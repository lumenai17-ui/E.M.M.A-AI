package com.beemovil.workflow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkflowScheduler — Manages periodic workflow execution using AlarmManager + WorkManager.
 *
 * Flow:
 * 1. User configures schedule in WorkflowEditor
 * 2. WorkflowScheduler.schedule() sets AlarmManager alarm
 * 3. At trigger time: WorkflowSchedulerReceiver fires
 * 4. Receiver enqueues WorkflowSchedulerWorker via WorkManager
 * 5. Worker starts ForegroundService to execute workflow
 * 6. On completion: notification with result
 *
 * Supported triggers:
 * - Time-based: daily, weekly, custom days
 * - Event-based: boot, wifi connect, low battery (via separate receivers)
 */
object WorkflowScheduler {

    private const val TAG = "WorkflowScheduler"
    private const val ACTION_WORKFLOW_ALARM = "com.beemovil.WORKFLOW_ALARM"
    private const val EXTRA_WORKFLOW_ID = "workflow_id"

    /**
     * Schedule a workflow for periodic execution.
     */
    fun schedule(context: Context, workflowId: String, config: ScheduleConfig) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, WorkflowSchedulerReceiver::class.java).apply {
            action = ACTION_WORKFLOW_ALARM
            putExtra(EXTRA_WORKFLOW_ID, workflowId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            workflowId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate next trigger time
        val triggerTime = calculateNextTrigger(config)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                        pendingIntent
                    )
                } else {
                    // Fallback to inexact
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                    pendingIntent
                )
            }

            Log.i(TAG, "Scheduled workflow '$workflowId' for ${java.util.Date(triggerTime)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm: ${e.message}", e)
            // Fallback to WorkManager periodic
            scheduleWithWorkManager(context, workflowId, config)
        }
    }

    /**
     * Cancel a scheduled workflow.
     */
    fun cancel(context: Context, workflowId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WorkflowSchedulerReceiver::class.java).apply {
            action = ACTION_WORKFLOW_ALARM
            putExtra(EXTRA_WORKFLOW_ID, workflowId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            workflowId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        // Also cancel WorkManager
        WorkManager.getInstance(context).cancelUniqueWork("workflow_$workflowId")

        Log.i(TAG, "Cancelled workflow '$workflowId'")
    }

    /**
     * Re-schedule all enabled workflows. Called on boot and app start.
     */
    fun rescheduleAll(context: Context) {
        val db = CustomWorkflowDB(context)
        val scheduled = db.getScheduledWorkflows()
        Log.i(TAG, "Rescheduling ${scheduled.size} workflows")
        scheduled.forEach { record ->
            record.schedule?.let { config ->
                schedule(context, record.id, config)
            }
        }
    }

    /**
     * Fallback: use WorkManager for periodic execution.
     */
    private fun scheduleWithWorkManager(context: Context, workflowId: String, config: ScheduleConfig) {
        val constraints = Constraints.Builder().apply {
            if (config.requireWifi) {
                setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
        }.build()

        val intervalMs = when (config.frequency) {
            ScheduleFrequency.DAILY -> 24L * 60 * 60 * 1000
            ScheduleFrequency.WEEKLY -> 7L * 24 * 60 * 60 * 1000
            else -> 24L * 60 * 60 * 1000
        }

        val workRequest = PeriodicWorkRequestBuilder<WorkflowSchedulerWorker>(
            intervalMs, TimeUnit.MILLISECONDS
        ).setConstraints(constraints)
            .setInputData(workDataOf("workflow_id" to workflowId))
            .addTag("workflow_scheduled")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "workflow_$workflowId",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.i(TAG, "Fallback: Scheduled via WorkManager: '$workflowId'")
    }

    /**
     * Calculate the next trigger time based on schedule config.
     */
    private fun calculateNextTrigger(config: ScheduleConfig): Long {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.hour)
            set(Calendar.MINUTE, config.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the time has already passed today, move to next valid day
        if (trigger.before(now) || trigger == now) {
            trigger.add(Calendar.DAY_OF_MONTH, 1)
        }

        // For weekly/custom: find the next valid day
        if (config.frequency == ScheduleFrequency.WEEKLY || config.frequency == ScheduleFrequency.CUSTOM) {
            if (config.daysOfWeek.isNotEmpty()) {
                var attempts = 0
                while (attempts < 8) {
                    // Convert Calendar day (1=Sun..7=Sat) to our format (1=Mon..7=Sun)
                    val calDay = trigger.get(Calendar.DAY_OF_WEEK)
                    val ourDay = when (calDay) {
                        Calendar.MONDAY -> 1
                        Calendar.TUESDAY -> 2
                        Calendar.WEDNESDAY -> 3
                        Calendar.THURSDAY -> 4
                        Calendar.FRIDAY -> 5
                        Calendar.SATURDAY -> 6
                        Calendar.SUNDAY -> 7
                        else -> 1
                    }
                    if (ourDay in config.daysOfWeek) break
                    trigger.add(Calendar.DAY_OF_MONTH, 1)
                    attempts++
                }
            }
        }

        return trigger.timeInMillis
    }
}

/**
 * BroadcastReceiver for scheduled workflow alarms.
 * Also handles BOOT_COMPLETED to reschedule all workflows.
 */
class WorkflowSchedulerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("WorkflowScheduler", "Receiver triggered: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Reschedule all workflows after reboot
                WorkflowScheduler.rescheduleAll(context)
            }
            "com.beemovil.WORKFLOW_ALARM" -> {
                val workflowId = intent.getStringExtra("workflow_id") ?: return
                Log.i("WorkflowScheduler", "Alarm fired for workflow: $workflowId")

                // Enqueue one-time work to execute the workflow
                val workRequest = OneTimeWorkRequestBuilder<WorkflowSchedulerWorker>()
                    .setInputData(workDataOf("workflow_id" to workflowId))
                    .addTag("workflow_execution")
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)

                // Reschedule for next occurrence
                val db = CustomWorkflowDB(context)
                val record = db.getWorkflow(workflowId)
                if (record != null && record.isScheduled) {
                    record.schedule?.let { config ->
                        WorkflowScheduler.schedule(context, workflowId, config)
                    }
                }
            }
        }
    }
}

/**
 * BroadcastReceiver for WiFi connectivity trigger.
 */
class WifiTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.net.wifi.STATE_CHANGE" ||
            intent.action == "android.net.conn.CONNECTIVITY_CHANGE") {

            val db = CustomWorkflowDB(context)
            val scheduled = db.getScheduledWorkflows()
            scheduled.filter { it.schedule?.triggerOnWifiConnect == true }.forEach { record ->
                Log.i("WorkflowScheduler", "WiFi trigger: executing '${record.name}'")
                val workRequest = OneTimeWorkRequestBuilder<WorkflowSchedulerWorker>()
                    .setInputData(workDataOf("workflow_id" to record.id))
                    .addTag("workflow_wifi_trigger")
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }
}

/**
 * BroadcastReceiver for low battery trigger.
 */
class BatteryTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BATTERY_LOW) {
            val db = CustomWorkflowDB(context)
            val scheduled = db.getScheduledWorkflows()
            scheduled.filter { it.schedule?.triggerOnLowBattery == true }.forEach { record ->
                Log.i("WorkflowScheduler", "Battery trigger: executing '${record.name}'")
                val workRequest = OneTimeWorkRequestBuilder<WorkflowSchedulerWorker>()
                    .setInputData(workDataOf("workflow_id" to record.id))
                    .addTag("workflow_battery_trigger")
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }
}

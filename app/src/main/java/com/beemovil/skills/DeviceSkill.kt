package com.beemovil.skills

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject

class DeviceSkill(private val context: Context) : BeeSkill {
    override val name = "device_info"
    override val description = "Get Android device information: model, battery level, storage space, RAM usage"
    override val parametersSchema = JSONObject("""{"type":"object","properties":{},"required":[]}""")

    override fun execute(params: JSONObject): JSONObject {
        val result = JSONObject()
        result.put("manufacturer", Build.MANUFACTURER)
        result.put("model", Build.MODEL)
        result.put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        result.put("android_version", Build.VERSION.RELEASE)
        result.put("sdk_version", Build.VERSION.SDK_INT)

        // Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            result.put("battery_level", if (level >= 0 && scale > 0) (level * 100 / scale) else -1)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            result.put("battery_charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }

        // Storage
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            result.put("storage_total_gb", String.format("%.1f", stat.totalBytes / 1_073_741_824.0))
            result.put("storage_free_gb", String.format("%.1f", stat.availableBytes / 1_073_741_824.0))
        } catch (_: Exception) {}

        // RAM
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            result.put("ram_total_mb", memInfo.totalMem / 1_048_576)
            result.put("ram_free_mb", memInfo.availMem / 1_048_576)
        } catch (_: Exception) {}

        return result
    }
}

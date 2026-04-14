package com.beemovil.google

import android.util.Log
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import java.util.Date

/**
 * GoogleCalendarService — Read/Write Google Calendar events.
 * 
 * All methods are SYNCHRONOUS — call from Dispatchers.IO.
 * Access token provided by GoogleAuthManager.
 */
class GoogleCalendarService(private val accessToken: String) {

    companion object {
        private const val TAG = "GoogleCalendar"
        const val SCOPE = CalendarScopes.CALENDAR
        const val SCOPE_READONLY = CalendarScopes.CALENDAR_READONLY
    }

    data class CalendarEvent(
        val id: String,
        val title: String,
        val description: String,
        val location: String,
        val startTime: Long,
        val endTime: Long,
        val isAllDay: Boolean,
        val htmlLink: String?
    )

    private val calendarService: Calendar by lazy {
        val credentials = GoogleCredentials.create(AccessToken(accessToken, Date(System.currentTimeMillis() + 3600_000)))
        Calendar.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(credentials)
        )
            .setApplicationName("BeeMovil")
            .build()
    }

    // ═══════════════════════════════════════
    // LIST EVENTS
    // ═══════════════════════════════════════

    /**
     * List upcoming events from primary calendar.
     * @param maxResults Number of events to retrieve
     * @param daysAhead How many days into the future to look
     */
    fun listUpcomingEvents(maxResults: Int = 20, daysAhead: Int = 30): List<CalendarEvent> {
        return try {
            val now = DateTime(System.currentTimeMillis())
            val future = DateTime(System.currentTimeMillis() + daysAhead.toLong() * 86400_000L)

            val events = calendarService.events().list("primary")
                .setTimeMin(now)
                .setTimeMax(future)
                .setMaxResults(maxResults)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setFields("items(id,summary,description,location,start,end,htmlLink)")
                .execute()

            events.items?.map { parseEvent(it) } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "List events error: ${e.message}", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════
    // CREATE EVENT
    // ═══════════════════════════════════════

    /**
     * Create a new calendar event.
     * @return Event ID or null on error
     */
    fun createEvent(
        title: String,
        description: String = "",
        location: String = "",
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean = false
    ): String? {
        return try {
            val event = Event().apply {
                summary = title
                this.description = description
                this.location = location

                if (allDay) {
                    start = EventDateTime().setDate(DateTime(true, startMillis, 0))
                    end = EventDateTime().setDate(DateTime(true, endMillis, 0))
                } else {
                    start = EventDateTime().setDateTime(DateTime(startMillis))
                    end = EventDateTime().setDateTime(DateTime(endMillis))
                }
            }

            val created = calendarService.events()
                .insert("primary", event)
                .setFields("id,htmlLink")
                .execute()

            Log.i(TAG, "Event created: ${created.id}")
            created.id
        } catch (e: Exception) {
            Log.e(TAG, "Create event error: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════
    // DELETE EVENT
    // ═══════════════════════════════════════

    fun deleteEvent(eventId: String): Boolean {
        return try {
            calendarService.events().delete("primary", eventId).execute()
            Log.i(TAG, "Deleted event: $eventId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Delete event error: ${e.message}", e)
            false
        }
    }

    // ═══════════════════════════════════════
    // SEARCH
    // ═══════════════════════════════════════

    fun searchEvents(query: String, maxResults: Int = 10): List<CalendarEvent> {
        return try {
            val events = calendarService.events().list("primary")
                .setQ(query)
                .setMaxResults(maxResults)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setFields("items(id,summary,description,location,start,end,htmlLink)")
                .execute()

            events.items?.map { parseEvent(it) } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Search events error: ${e.message}", e)
            emptyList()
        }
    }

    // ═══════════════════════════════════════
    // FORMAT FOR LLM
    // ═══════════════════════════════════════

    fun formatEventsForLlm(events: List<CalendarEvent>): String {
        if (events.isEmpty()) return "No tienes eventos próximos."
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        return buildString {
            appendLine("📅 Tus próximos ${events.size} eventos:")
            events.forEachIndexed { i, ev ->
                appendLine("${i + 1}. **${ev.title}** — ${sdf.format(Date(ev.startTime))}")
                if (ev.location.isNotBlank()) appendLine("   📍 ${ev.location}")
                if (ev.description.isNotBlank()) appendLine("   ${ev.description.take(80)}")
            }
        }
    }

    private fun parseEvent(event: Event): CalendarEvent {
        val startMs = event.start?.dateTime?.value
            ?: event.start?.date?.value ?: 0L
        val endMs = event.end?.dateTime?.value
            ?: event.end?.date?.value ?: 0L
        val isAllDay = event.start?.dateTime == null

        return CalendarEvent(
            id = event.id ?: "",
            title = event.summary ?: "(Sin título)",
            description = event.description ?: "",
            location = event.location ?: "",
            startTime = startMs,
            endTime = endMs,
            isAllDay = isAllDay,
            htmlLink = event.htmlLink
        )
    }
}

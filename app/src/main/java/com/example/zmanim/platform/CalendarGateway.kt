package com.example.zmanim.platform

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.zmanim.domain.*
import java.security.MessageDigest
import java.time.*

data class DeviceCalendar(val id: Long, val name: String, val account: String, val writable: Boolean, val color: Int)

class CalendarGateway(private val context: Context) {
    private val resolver = context.contentResolver
    fun canRead() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun canWrite() = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun calendars(): List<DeviceCalendar> {
        if (!canRead()) return emptyList()
        return resolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf("_id", "calendar_displayName", "account_name", "calendar_access_level", "calendar_color"),
            null, null, "calendar_displayName COLLATE NOCASE")?.use { c ->
            buildList { while (c.moveToNext()) add(DeviceCalendar(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR, c.getInt(4))) }
        }.orEmpty()
    }
    fun instances(from: Instant, until: Instant, selected: Set<Long>): List<Occurrence> {
        if (!canRead() || selected.isEmpty()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from.toEpochMilli()); ContentUris.appendId(it, until.toEpochMilli())
        }.build()
        return resolver.query(uri, arrayOf("event_id", "title", "begin", "end", "allDay", "eventTimezone", "calendar_id"),
            "calendar_id IN (${selected.joinToString(",") { "?" }})", selected.map(Long::toString).toTypedArray(), "begin ASC")?.use { c ->
            buildList { while (c.moveToNext()) {
                val start = Instant.ofEpochMilli(c.getLong(2)); val end = Instant.ofEpochMilli(c.getLong(3))
                val zone = runCatching { ZoneId.of(c.getString(5)) }.getOrDefault(ZoneId.systemDefault())
                add(Occurrence("device:${c.getLong(0)}:$start", "device:${c.getLong(0)}", start.atZone(if (c.getInt(4) != 0) ZoneOffset.UTC else zone).toLocalDate(),
                    c.getString(1) ?: "Untitled event", start, end, c.getInt(4) != 0, zone.id,
                    sourceCalendarId = c.getLong(6), providerId = c.getLong(0)))
            } }
        }.orEmpty()
    }
    fun viewIntent(providerId: Long) = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId))
    fun invitationIntent(event: Event): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.Events.TITLE, event.title)
        putExtra(CalendarContract.Events.DESCRIPTION, event.description)
        putExtra(CalendarContract.Events.EVENT_LOCATION, event.venue)
        putExtra(Intent.EXTRA_EMAIL, event.guests)
        val start = LocalDateTime.of(LocalDate.parse(event.anchorDate), LocalTime.parse(event.localTime)).atZone(ZoneId.of(event.zoneId)).toInstant()
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start.plusSeconds(event.durationMinutes * 60).toEpochMilli())
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.timeKind == EventTimeKind.ALL_DAY)
        nativeRule(event)?.let { putExtra(CalendarContract.Events.RRULE, it) }
    }
    private fun nativeRule(event: Event): String? {
        val r = event.recurrence
        if (r.frequency == Frequency.NONE) return null
        return buildString {
            append("FREQ=${r.frequency.name};INTERVAL=${r.interval}")
            if (r.weekdays.isNotEmpty()) append(";BYDAY=" + r.weekdays.sorted().joinToString(",") { listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[it - 1] })
            r.count?.let { append(";COUNT=$it") }
            r.until?.let { append(";UNTIL=" + LocalDate.parse(it).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + "T235959Z") }
        }
    }
    fun saveOrdinary(event: Event, calendarId: Long, existingId: Long? = null): Long {
        check(canWrite()) { "Calendar write access is required." }
        require(calendars().any { it.id == calendarId && it.writable }) { "Choose a writable calendar." }
        require(event.calendar == CalendarSystem.GREGORIAN && event.timeKind != EventTimeKind.ZMAN) { "Special recurrence must be saved in the app and exported as derived occurrences." }
        val zone = ZoneId.of(event.zoneId)
        val date = LocalDate.parse(event.anchorDate)
        val allDay = event.timeKind == EventTimeKind.ALL_DAY
        val start = if (allDay) date.atStartOfDay(ZoneOffset.UTC).toInstant() else
            OccurrenceEngine().occurrence(event, date, AppData()).start ?: error("This event time does not exist under its clock-change policy.")
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title); put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.venue)
            put(CalendarContract.Events.DTSTART, start.toEpochMilli())
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else event.zoneId)
            val rule = nativeRule(event)
            if (rule != null) { put(CalendarContract.Events.RRULE, rule); put(CalendarContract.Events.DURATION, if (allDay) "P1D" else "PT${event.durationMinutes}M"); putNull(CalendarContract.Events.DTEND) }
            else { putNull(CalendarContract.Events.RRULE); putNull(CalendarContract.Events.DURATION); put(CalendarContract.Events.DTEND, start.plusSeconds(if (allDay) 86400 else event.durationMinutes * 60).toEpochMilli()) }
        }
        val id = if (existingId == null) ContentUris.parseId(checkNotNull(resolver.insert(CalendarContract.Events.CONTENT_URI, values)))
        else { resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId), values, null, null); existingId }
        replaceProviderReminders(id, event.reminders)
        return id
    }
    fun ordinaryEvent(id: Long): Pair<Event, Long>? {
        if (!canRead()) return null
        return resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
            arrayOf("title", "description", "eventLocation", "dtstart", "dtend", "duration", "allDay", "eventTimezone", "calendar_id", "rrule"), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val zone = runCatching { ZoneId.of(c.getString(7)) }.getOrDefault(ZoneId.systemDefault())
            val start = Instant.ofEpochMilli(c.getLong(3)).atZone(if (c.getInt(6) != 0) ZoneOffset.UTC else zone)
            // Advanced external recurrence must be edited in its owning calendar app to avoid losing semantics.
            if (!c.getString(9).isNullOrBlank()) return@use null
            Event(title = c.getString(0).orEmpty(), description = c.getString(1).orEmpty(), venue = c.getString(2).orEmpty(),
                anchorDate = start.toLocalDate().toString(), localTime = start.toLocalTime().toString(),
                durationMinutes = if (c.isNull(4)) 60 else Duration.between(start.toInstant(), Instant.ofEpochMilli(c.getLong(4))).toMinutes().coerceAtLeast(1),
                zoneId = zone.id, timeKind = if (c.getInt(6) != 0) EventTimeKind.ALL_DAY else EventTimeKind.FIXED) to c.getLong(8)
        }
    }
    fun deleteOrdinary(id: Long) { check(canWrite()); resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) }
    private fun values(event: Event, occurrence: Occurrence, calendarId: Long) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId); put(CalendarContract.Events.TITLE, event.title)
        put(CalendarContract.Events.DESCRIPTION, event.description); put(CalendarContract.Events.EVENT_LOCATION, event.venue)
        put(CalendarContract.Events.DTSTART, if (occurrence.allDay) occurrence.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else occurrence.start!!.toEpochMilli())
        put(CalendarContract.Events.DTEND, if (occurrence.allDay) occurrence.date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else occurrence.end!!.toEpochMilli())
        put(CalendarContract.Events.ALL_DAY, if (occurrence.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (occurrence.allDay) "UTC" else event.zoneId)
    }
    private val managedColumns = arrayOf("calendar_id", "title", "description", "eventLocation", "dtstart", "dtend", "allDay", "eventTimezone")
    private val fingerprintColumns get() = managedColumns + arrayOf("rrule", "duration", "eventStatus", "deleted")
    private fun fingerprint(id: Long): String? = resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), fingerprintColumns, null, null, null)?.use { c ->
        if (!c.moveToFirst()) null else {
            val core = fingerprintColumns.indices.joinToString("\u0000") { c.getString(it).orEmpty() }
            val reminders = resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf("minutes", "method"), "event_id = ?", arrayOf(id.toString()), "minutes, method")?.use { rows ->
                buildList { while (rows.moveToNext()) add("${rows.getInt(0)}:${rows.getInt(1)}") }.joinToString(";")
            }.orEmpty()
            val guests = resolver.query(CalendarContract.Attendees.CONTENT_URI, arrayOf("attendeeEmail", "attendeeStatus", "attendeeRelationship"), "event_id = ?", arrayOf(id.toString()), "attendeeEmail")?.use { rows ->
                buildList { while (rows.moveToNext()) add("${rows.getString(0)}:${rows.getInt(1)}:${rows.getInt(2)}") }.joinToString(";")
            }.orEmpty()
            MessageDigest.getInstance("SHA-256").digest("$core\u0000$reminders\u0000$guests".toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
    private fun replaceProviderReminders(id: Long, reminders: List<ReminderRule>) {
        resolver.delete(CalendarContract.Reminders.CONTENT_URI, "event_id = ?", arrayOf(id.toString()))
        reminders.forEach { r -> resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, id); put(CalendarContract.Reminders.MINUTES, r.minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }) }
    }
    fun reconcile(data: AppData, today: LocalDate): List<ExportLink> {
        if (!canRead() || !canWrite()) return data.exports
        val engine = OccurrenceEngine()
        val writable = calendars().filter { it.writable }.map { it.id }.toSet()
        val desired = data.events.filter { it.exportCalendarId != null }.flatMap { e ->
            engine.expand(e, today, today.plusYears(1).plusDays(1), data).filter { it.start != null && it.end != null }.map { it.key to (e to it) }
        }.toMap()
        val links = data.exports.associateBy { it.id }.toMutableMap()
        for ((id, link) in links.toMap()) {
            if (link.state != "MANAGED") continue
            val actual = fingerprint(link.providerId)
            if (actual != link.fingerprint) { links[id] = link.copy(state = "CONFLICT", reason = if (actual == null) "Deleted in the device calendar" else "Edited in the device calendar"); continue }
            val pair = desired[id]
            // Past exported occurrences are history, not a rolling cache to delete.
            if (pair == null && LocalDate.parse(link.occurrenceDate) < today && data.events.any { it.id == link.eventId && it.exportCalendarId == link.calendarId }) continue
            if (pair == null || pair.first.exportCalendarId != link.calendarId) {
                resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, link.providerId), null, null); links.remove(id)
            }
        }
        for ((id, pair) in desired) {
            val (event, occurrence) = pair
            val link = links[id]
            if (link != null && link.state != "MANAGED") continue
            val calendarId = event.exportCalendarId!!
            if (calendarId !in writable) continue
            val values = values(event, occurrence, calendarId)
            val providerId = if (link == null) ContentUris.parseId(checkNotNull(resolver.insert(CalendarContract.Events.CONTENT_URI, values))) else {
                // Avoid writes when nothing changed, to prevent observer/sync feedback loops.
                val currentValues = resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, link.providerId), managedColumns, null, null, null)?.use { c ->
                    if (!c.moveToFirst()) false else managedColumns.indices.all { c.getString(it).orEmpty() == values.getAsString(managedColumns[it]).orEmpty() }
                } ?: false
                if (!currentValues) resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, link.providerId), values, null, null)
                link.providerId
            }
            val reminders = if (event.remindersEnabled && event.reminderOwner == ReminderOwner.CALENDAR) event.reminders else emptyList()
            val currentReminders = resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf("minutes"), "event_id = ?", arrayOf(providerId.toString()), null)?.use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } }.orEmpty()
            if (currentReminders.sorted() != reminders.map { it.minutesBefore }.sorted()) replaceProviderReminders(providerId, reminders)
            links[id] = ExportLink(id, event.id, occurrence.date.toString(), calendarId, providerId, fingerprint(providerId)!!)
        }
        return links.values.toList()
    }
    fun restore(link: ExportLink) {
        check(canWrite())
        if (fingerprint(link.providerId) != null) resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, link.providerId), null, null)
    }
}

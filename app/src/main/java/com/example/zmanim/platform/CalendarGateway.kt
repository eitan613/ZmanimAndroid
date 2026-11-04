package com.example.zmanim.platform

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
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
    /** Reload the exact recurring instance; never substitutes the series anchor. */
    fun instance(providerId: Long, beginMillis: Long): Occurrence? {
        if (!canRead()) return null
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, beginMillis - 1000); ContentUris.appendId(it, beginMillis + 1000)
        }.build()
        return resolver.query(uri, arrayOf("event_id", "title", "begin", "end", "allDay", "eventTimezone", "calendar_id"),
            "event_id = ? AND begin = ?", arrayOf(providerId.toString(), beginMillis.toString()), null)?.use { c ->
            if (!c.moveToFirst()) null else {
                val start = Instant.ofEpochMilli(c.getLong(2))
                val zone = runCatching { ZoneId.of(c.getString(5)) }.getOrDefault(ZoneId.systemDefault())
                Occurrence("device:$providerId:$start", "device:$providerId", start.atZone(if (c.getInt(4) != 0) ZoneOffset.UTC else zone).toLocalDate(),
                    c.getString(1).orEmpty(), start, Instant.ofEpochMilli(c.getLong(3)), c.getInt(4) != 0, zone.id,
                    sourceCalendarId = c.getLong(6), providerId = providerId)
            }
        }
    }
    /** Editable fields for the selected instance, retaining arbitrary external series rules. */
    fun instanceEvent(occurrence: Occurrence): Event? {
        if (!canRead()) return null
        val current = instance(occurrence.providerId!!, occurrence.start!!.toEpochMilli()) ?: return null
        return resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, current.providerId!!),
            arrayOf("description", "eventLocation"), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val zone = if (current.allDay) ZoneOffset.UTC else ZoneId.of(current.zoneId)
            val start = current.start!!.atZone(zone)
            val end = current.end!!.atZone(zone)
            Event(id = current.key, title = current.title, description = cursor.getString(0).orEmpty(), venue = cursor.getString(1).orEmpty(),
                anchorDate = start.toLocalDate().toString(), localTime = start.toLocalTime().toString(), zoneId = current.zoneId,
                timeKind = if (current.allDay) EventTimeKind.ALL_DAY else EventTimeKind.FIXED,
                durationMinutes = Duration.between(start.toLocalDateTime(), end.toLocalDateTime()).toMinutes(),
                reminders = instanceReminders(current.providerId), reminderOwner = ReminderOwner.CALENDAR)
        }
    }

    private fun instanceReminders(providerId: Long): List<ReminderRule> =
        resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf("_id", "minutes"),
            "event_id = ? AND minutes >= 0 AND method IN (?, ?)",
            arrayOf(providerId.toString(), CalendarContract.Reminders.METHOD_ALERT.toString(), CalendarContract.Reminders.METHOD_DEFAULT.toString()), "minutes DESC")?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(ReminderRule(id = "provider-reminder:${cursor.getLong(0)}", minutesBefore = cursor.getInt(1))) }
        }.orEmpty()

    /** Update an ordinary event, or create an exception for exactly one recurring instance. */
    fun updateInstance(occurrence: Occurrence, original: Event, edited: Event): Long {
        check(canWrite()) { "Calendar write access is required." }
        require(calendars().any { it.id == occurrence.sourceCalendarId && it.writable }) { "Choose a writable calendar." }
        require(edited.title.isNotBlank()) { "Enter an event title." }
        require(edited.durationMinutes > 0) { "Enter a positive duration." }
        require(edited.reminders.all { it.minutesBefore in 0..525600 }) { "Reminder offsets must be whole minutes from 0 to 525600." }
        val providerId = occurrence.providerId!!
        check(instance(providerId, occurrence.start!!.toEpochMilli()) != null) { "This occurrence is no longer available." }
        val date = LocalDate.parse(edited.anchorDate)
        val allDay = edited.timeKind == EventTimeKind.ALL_DAY
        val timesUnchanged = edited.anchorDate == original.anchorDate && edited.localTime == original.localTime &&
            edited.durationMinutes == original.durationMinutes && edited.timeKind == original.timeKind
        val (start, end) = if (timesUnchanged) occurrence.start to occurrence.end!! else if (allDay) {
            require(edited.durationMinutes % 1440 == 0L) { "All-day duration must be whole days." }
            date.atStartOfDay(ZoneOffset.UTC).toInstant() to date.plusDays(edited.durationMinutes / 1440).atStartOfDay(ZoneOffset.UTC).toInstant()
        } else {
            val local = LocalDateTime.of(date, LocalTime.parse(edited.localTime))
            val intervals = localIntervalSegments(local, local.plusMinutes(edited.durationMinutes), ZoneId.of(edited.zoneId))
            require(intervals.size == 1) { "This time crosses a clock change. Choose another time." }
            intervals.single()
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, edited.title); put(CalendarContract.Events.DESCRIPTION, edited.description)
            put(CalendarContract.Events.EVENT_LOCATION, edited.venue)
            put(CalendarContract.Events.DTSTART, start.toEpochMilli()); put(CalendarContract.Events.DTEND, end.toEpochMilli())
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else edited.zoneId)
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, providerId)
        val recurring = resolver.query(uri, arrayOf("rrule", "rdate", "_sync_id"), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            val repeats = !cursor.getString(0).isNullOrBlank() || !cursor.getString(1).isNullOrBlank()
            if (repeats) {
                // Android's unsynced-series exception path drops cached sibling instances.
                // Reject before writing; synced RRULE events use the supported exception path.
                check(!cursor.getString(0).isNullOrBlank() && !cursor.getString(2).isNullOrBlank()) {
                    "This calendar does not support editing a single occurrence here."
                }
            }
            repeats
        } ?: false
        val savedId = if (recurring) {
            // CalendarProvider derives an exception's DTEND from DURATION and copies
            // the original all-day flag, attendees, and reminders itself.
            values.remove(CalendarContract.Events.DTEND)
            values.put(CalendarContract.Events.DURATION, if (allDay) "P${Duration.between(start, end).toDays()}D" else "PT${Duration.between(start, end).seconds}S")
            values.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, occurrence.start.toEpochMilli())
            val id = ContentUris.parseId(checkNotNull(resolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, providerId), values)))
            check(id > 0) { "This recurrence rule cannot be edited here yet." }
            id
        } else {
            check(resolver.update(uri, values, null, null) == 1) { "This event is no longer available." }
            providerId
        }
        if (edited.reminders != original.reminders) {
            // Keep other delivery methods (for example email reminders) intact.
            resolver.delete(CalendarContract.Reminders.CONTENT_URI, "event_id = ? AND method IN (?, ?)",
                arrayOf(savedId.toString(), CalendarContract.Reminders.METHOD_ALERT.toString(), CalendarContract.Reminders.METHOD_DEFAULT.toString()))
            edited.reminders.filter { it.enabled }.forEach { rule ->
                resolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, savedId)
                    put(CalendarContract.Reminders.MINUTES, rule.minutesBefore)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                })
            }
        }
        return savedId
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
        if (!allDay) {
            val segments = OccurrenceEngine().expand(event, date, date.plusDays(1), AppData())
            require(segments.size == 1) { "Save this clock-change rule in the app and export its actual segments." }
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title); put(CalendarContract.Events.DESCRIPTION, event.description)
            put(CalendarContract.Events.EVENT_LOCATION, event.venue)
            put(CalendarContract.Events.DTSTART, start.toEpochMilli())
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else event.zoneId)
            val rule = nativeRule(event)
            if (rule != null) { put(CalendarContract.Events.RRULE, rule); put(CalendarContract.Events.DURATION, if (allDay) "P1D" else "PT${event.durationMinutes}M"); putNull(CalendarContract.Events.DTEND) }
            else {
                putNull(CalendarContract.Events.RRULE); putNull(CalendarContract.Events.DURATION)
                val end = if (allDay) start.plusSeconds(86400) else OccurrenceEngine().occurrence(event, date, AppData()).end ?: error("The local interval is skipped by a clock change.")
                put(CalendarContract.Events.DTEND, end.toEpochMilli())
            }
        }
        val id = if (existingId == null) ContentUris.parseId(checkNotNull(resolver.insert(CalendarContract.Events.CONTENT_URI, values)))
        else { resolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingId), values, null, null); existingId }
        replaceProviderReminders(id, if (event.remindersEnabled) event.reminders.filter { it.enabled } else emptyList())
        return id
    }
    fun ordinaryEvent(id: Long): Pair<Event, Long>? {
        if (!canRead()) return null
        return resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
            arrayOf("title", "description", "eventLocation", "dtstart", "dtend", "duration", "allDay", "eventTimezone", "calendar_id", "rrule", "rdate"), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val zone = runCatching { ZoneId.of(c.getString(7)) }.getOrDefault(ZoneId.systemDefault())
            val start = Instant.ofEpochMilli(c.getLong(3)).atZone(if (c.getInt(6) != 0) ZoneOffset.UTC else zone)
            // Recurring events use the selected-instance editor to retain their original rules.
            if (!c.getString(9).isNullOrBlank() || !c.getString(10).isNullOrBlank()) return@use null
            val providerReminders = resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf("_id", "minutes", "method"), "event_id = ?", arrayOf(id.toString()), null)?.use remindersRead@ { reminders ->
                buildList<ReminderRule> {
                    while (reminders.moveToNext()) {
                        if (reminders.getInt(1) < 0 || reminders.getInt(2) !in setOf(CalendarContract.Reminders.METHOD_ALERT, CalendarContract.Reminders.METHOD_DEFAULT)) return@remindersRead null
                        add(ReminderRule(id = "provider-reminder:${reminders.getLong(0)}", minutesBefore = reminders.getInt(1)))
                    }
                }
            } ?: return@use null
            val end = if (c.isNull(4)) start.toInstant().plusSeconds(3600) else Instant.ofEpochMilli(c.getLong(4))
            val wallDuration = Duration.between(start.toLocalDateTime(), end.atZone(zone).toLocalDateTime()).toMinutes()
            if (c.getInt(6) == 0 && (wallDuration <= 0 || localIntervalSegments(start.toLocalDateTime(), start.toLocalDateTime().plusMinutes(wallDuration), zone) != listOf(start.toInstant() to end))) return@use null
            Event(title = c.getString(0).orEmpty(), description = c.getString(1).orEmpty(), venue = c.getString(2).orEmpty(),
                anchorDate = start.toLocalDate().toString(), localTime = start.toLocalTime().toString(),
                durationMinutes = if (c.getInt(6) != 0) 1440 else wallDuration,
                zoneId = zone.id, timeKind = if (c.getInt(6) != 0) EventTimeKind.ALL_DAY else EventTimeKind.FIXED,
                reminders = providerReminders, reminderOwner = ReminderOwner.CALENDAR) to c.getLong(8)
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
            val reminders = if (event.remindersEnabled && event.reminderDeliveryOwner() == ReminderOwner.CALENDAR) event.reminders.filter { it.enabled } else emptyList()
            val currentReminders = resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf("minutes"), "event_id = ?", arrayOf(providerId.toString()), null)?.use { c -> buildList { while (c.moveToNext()) add(c.getInt(0)) } }.orEmpty()
            if (currentReminders.sorted() != reminders.map { it.minutesBefore }.sorted()) replaceProviderReminders(providerId, reminders)
            links[id] = ExportLink(id, event.id, occurrence.date.toString(), calendarId, providerId, fingerprint(providerId)!!)
        }
        return links.values.toList()
    }
    /** Adopt an edited provider record when its rule needs app-managed occurrence export. */
    fun adopt(event: Event, occurrence: Occurrence, calendarId: Long, providerId: Long): ExportLink =
        ExportLink(occurrence.key, event.id, occurrence.date.toString(), calendarId, providerId,
            fingerprint(providerId) ?: error("This device-calendar event is no longer available."))

    fun restore(link: ExportLink) {
        check(canWrite())
        if (fingerprint(link.providerId) != null) resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, link.providerId), null, null)
    }
}

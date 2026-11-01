package com.example.zmanim.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import com.example.zmanim.domain.*
import java.time.*
import kotlinx.coroutines.*

@Composable fun EventEditor(s: ScreenData, model: AppViewModel, original: Event?, occurrence: Occurrence?,
    nativeId: Long?, nativeCalendar: Long?, onDismiss: () -> Unit) {
    val scopedId = rememberSaveable(original?.id, occurrence?.key) { newId() }
    val initial = remember(original?.id, occurrence?.key) {
        when {
            original == null -> Event(title = "", anchorDate = s.date.toString(), zoneId = s.data.place()?.zoneId ?: ZoneId.systemDefault().id,
                location = s.data.settings.location, profileId = s.data.settings.activeProfileId)
            occurrence?.key == "single" -> original.copy(id = scopedId, anchorDate = occurrence.date.toString(), recurrence = Recurrence(), excludedDates = emptySet(), parentId = original.id)
            occurrence?.key == "following" -> original.copy(id = scopedId, anchorDate = occurrence.date.toString(), recurrence = original.recurrence.copy(count = original.recurrence.count?.let { count ->
                val before = OccurrenceEngine().dates(original.copy(excludedDates = emptySet()), LocalDate.parse(original.anchorDate), occurrence.date).size
                (count - before).coerceAtLeast(1)
            }), excludedDates = original.excludedDates.filter { it >= occurrence.date.toString() }.toSet(), parentId = original.id)
            else -> original
        }
    }
    var draft by rememberDraft("event-${initial.id}", initial)
    var duration by rememberSaveable { mutableStateOf(initial.durationMinutes.toString()) }
    var offset by rememberSaveable { mutableStateOf(initial.offsetMinutes.toString()) }
    var destination by rememberSaveable { mutableStateOf(nativeCalendar ?: initial.exportCalendarId) }
    var goTo by remember { mutableStateOf(false) }
    val eligible = draft.calendar == CalendarSystem.GREGORIAN && draft.timeKind != EventTimeKind.ZMAN && draft.recurrence.frequency == Frequency.NONE
    var deleteNative by remember { mutableStateOf(false) }
    val context = LocalContext.current
    EditorDialog(if (original == null || nativeId == null && s.data.events.none { it.id == original.id }) tr("New event", "אירוע חדש") else tr("Edit event", "עריכת אירוע"), draft != initial || duration != initial.durationMinutes.toString() || destination != (nativeCalendar ?: initial.exportCalendarId), onDismiss, {
        val event = draft.copy(durationMinutes = duration.toLongOrNull() ?: error("Enter a whole duration in minutes."),
            offsetMinutes = offset.toDoubleOrNull() ?: error("Enter a valid offset."), exportCalendarId = destination)
        OccurrenceEngine().validate(event, model.repository.data)
        if (destination != null && eligible && occurrence == null && (original == null || s.data.events.none { it.id == original.id } || nativeId != null)) {
            val providerId = withContext(Dispatchers.IO) { model.app.calendars.saveOrdinary(event, destination!!, nativeId) }
            if (event.guests.isNotBlank()) context.startActivity(Intent(Intent.ACTION_EDIT,
                android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, providerId)).putExtra(Intent.EXTRA_EMAIL, event.guests))
            model.refresh()
        } else when (occurrence?.key) {
            "single" -> model.repository.replaceOccurrence(original!!, occurrence.date, event)
            "following" -> model.repository.splitSeries(original!!, occurrence.date, event)
            else -> model.repository.saveEvent(event)
        }
    }) {
        Field(tr("Title", "כותרת"), draft.title, { draft = draft.copy(title = it) })
        Choice(tr("Recurrence calendar", "לוח לחזרה"), draft.calendar, CalendarSystem.entries, { enumName(it) }, { draft = draft.copy(calendar = it, recurrence = draft.recurrence.copy(missingDay = null, ordinaryAdar = null, leapAdar = null)) })
        OutlinedButton(onClick = { goTo = true }, modifier = Modifier.fillMaxWidth()) {
            Column { Text(draft.anchorDate); Text(runCatching { CalendarEngine().dateLabel(LocalDate.parse(draft.anchorDate), LocalHebrew.current) }.getOrDefault("")) }
        }
        Choice(tr("Time", "זמן"), draft.timeKind, EventTimeKind.entries, { enumName(it) }, { draft = draft.copy(timeKind = it) })
        if (draft.timeKind == EventTimeKind.ALL_DAY && draft.calendar == CalendarSystem.HEBREW) {
            if (s.data.settings.hebrewDayBoundary == null) Choice(tr("App-wide Hebrew all-day boundary", "גבול יום עברי לכל האפליקציה"), s.data.settings.hebrewDayBoundary, listOf(null) + HebrewDayBoundary.entries,
                { enumName(it) }, { value -> model.settings { it.copy(hebrewDayBoundary = value) } })
            else Text(tr("All-day boundary: ", "גבול יום שלם: ") + enumName(s.data.settings.hebrewDayBoundary) + tr(". Change it in Settings to preview the effect on all events.", ". ניתן לשנות בהגדרות ולצפות בהשפעה על כל האירועים."))
            Text(tr("This setting applies to every Hebrew all-day event.", "הגדרה זו חלה על כל האירועים העבריים של יום שלם."))
        }
        if (draft.timeKind == EventTimeKind.FIXED) {
            Field(tr("Time (HH:MM, 24-hour input)", "שעה (HH:MM בתבנית 24 שעות)"), draft.localTime, { draft = draft.copy(localTime = it) }, hint = tr("The calendar display follows your 12/24-hour preference.", "תצוגת לוח השנה תשתמש בהעדפת 12/24 השעות שלך."))
            if (draft.calendar == CalendarSystem.HEBREW) Toggle(tr("Place this time on the previous civil evening", "זמן זה חל בערב הלועזי הקודם"), draft.previousEvening, { draft = draft.copy(previousEvening = it) })
        }
        if (draft.timeKind == EventTimeKind.ZMAN) {
            Choice(tr("Calculation profile", "פרופיל חישוב"), draft.profileId, listOf(null) + s.data.profiles.map { it.id },
                { id -> s.data.profiles.find { it.id == id }?.name ?: tr("Choose profile", "בחירת פרופיל") }, { draft = draft.copy(profileId = it, methodId = null) })
            val p = s.data.profile(draft.profileId)
            val ids = p?.let { it.selected.filter { id -> MethodCatalog.byId[id]?.isDuration != true } +
                MethodCatalog.methods.filterNot { method -> method.isDuration }.map { method -> method.id } + it.custom.map { z -> z.id } }?.distinct().orEmpty()
            Choice(tr("Calculation", "חישוב"), draft.methodId, listOf(null) + ids, { id -> if (id == null) tr("Choose calculation", "בחירת חישוב") else methodLabel(id, p, LocalLabelHebrew.current) }, { draft = draft.copy(methodId = it) })
            Field(tr("Minutes offset (+ after, − before)", "הפרש בדקות (+ אחרי, − לפני)"), offset, { offset = it; it.toDoubleOrNull()?.let { n -> draft = draft.copy(offsetMinutes = n) } }, numeric = true)
        }
        if (draft.timeKind != EventTimeKind.ALL_DAY) Field(tr("Duration (minutes)", "משך (דקות)"), duration, { duration = it }, numeric = true)
        Field(tr("Event timezone", "אזור הזמן של האירוע"), draft.zoneId, { draft = draft.copy(zoneId = it) })
        if (draft.timeKind == EventTimeKind.ZMAN || draft.timeKind == EventTimeKind.ALL_DAY && draft.calendar == CalendarSystem.HEBREW)
            LocationChoice(tr("Calculation location", "מקום החישוב"), draft.location, s.data) { draft = draft.copy(location = it) }
        Section(tr("Repeat", "חזרה")) {
            Choice(tr("Frequency", "תדירות"), draft.recurrence.frequency, (if (occurrence?.key == "single") listOf(Frequency.NONE) else Frequency.entries), { enumName(it) }, { draft = draft.copy(recurrence = draft.recurrence.copy(frequency = it)) })
            if (draft.recurrence.frequency != Frequency.NONE) {
                var interval by rememberSaveable { mutableStateOf(initial.recurrence.interval.toString()) }
                Field(tr("Every … periods", "כל … תקופות"), interval, { interval = it; draft = draft.copy(recurrence = draft.recurrence.copy(interval = it.toIntOrNull() ?: 0)) }, numeric = true)
                if (draft.recurrence.frequency in setOf(Frequency.DAILY, Frequency.WEEKLY)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..7).forEach { day -> FilterChip(selected = day in draft.recurrence.weekdays,
                            onClick = { draft = draft.copy(recurrence = draft.recurrence.copy(weekdays = if (day in draft.recurrence.weekdays) draft.recurrence.weekdays - day else draft.recurrence.weekdays + day)) },
                            label = { Text(DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, LocalProductLocale.current)) }) }
                    }
                    Text(tr("No weekdays selected: every day for daily repeats, or the original weekday for weekly repeats.", "ללא בחירת ימים: כל יום בחזרה יומית, או היום המקורי בשבוע בחזרה שבועית."), style = MaterialTheme.typography.bodySmall)
                }
                var until by rememberSaveable { mutableStateOf(initial.recurrence.until.orEmpty()) }
                var count by rememberSaveable { mutableStateOf(initial.recurrence.count?.toString().orEmpty()) }
                Field(tr("End date (YYYY-MM-DD, optional)", "תאריך סיום (YYYY-MM-DD, רשות)"), until, { until = it; draft = draft.copy(recurrence = draft.recurrence.copy(until = it.takeIf(String::isNotBlank))) })
                Field(tr("Maximum occurrences (optional)", "מספר מופעים מרבי (רשות)"), count, { count = it; draft = draft.copy(recurrence = draft.recurrence.copy(count = if (it.isBlank()) null else it.toIntOrNull() ?: 0)) }, numeric = true)
            }
            if (draft.recurrence.frequency in setOf(Frequency.MONTHLY, Frequency.YEARLY)) {
                Choice(tr("When the original day is absent", "כאשר היום המקורי אינו קיים"), draft.recurrence.missingDay, listOf(null) + MissingDayPolicy.entries, { enumName(it) }, { draft = draft.copy(recurrence = draft.recurrence.copy(missingDay = it)) })
                if (draft.calendar == CalendarSystem.HEBREW && draft.recurrence.frequency == Frequency.YEARLY) {
                    val jd = runCatching { com.kosherjava.zmanim.hebrewcalendar.JewishDate(LocalDate.parse(draft.anchorDate)) }.getOrNull()
                    if (jd?.jewishMonth == 12 && !jd.isJewishLeapYear)
                        Choice(tr("Ordinary Adar in a leap year", "אדר רגיל בשנה מעוברת"), draft.recurrence.ordinaryAdar, listOf(null) + AdarPolicy.entries, { enumName(it) }, { draft = draft.copy(recurrence = draft.recurrence.copy(ordinaryAdar = it)) })
                    if (jd?.jewishMonth in setOf(12, 13) && jd?.isJewishLeapYear == true)
                        Choice(tr("Leap-year Adar in an ordinary year", "אדר של שנה מעוברת בשנה רגילה"), draft.recurrence.leapAdar, listOf(null) + LeapAdarPolicy.entries, { enumName(it) }, { draft = draft.copy(recurrence = draft.recurrence.copy(leapAdar = it)) })
                }
            }
        }
        Section(tr("Clock changes", "שינויי שעון")) {
            Choice(tr("If the wall time does not exist", "אם השעה אינה קיימת"), draft.gapPolicy, DstGapPolicy.entries, { enumName(it) }, { draft = draft.copy(gapPolicy = it) })
            Choice(tr("If the wall time occurs twice", "אם השעה מופיעה פעמיים"), draft.overlapPolicy, DstOverlapPolicy.entries, { enumName(it) }, { draft = draft.copy(overlapPolicy = it) })
        }
        Section(tr("Details and reminders", "פרטים ותזכורות")) {
            Field(tr("Location / venue", "מקום האירוע"), draft.venue, { draft = draft.copy(venue = it) })
            Field(tr("Description", "תיאור"), draft.description, { draft = draft.copy(description = it) }, multiline = true)
            Field(tr("Guest email addresses", "כתובות דוא״ל של אורחים"), draft.guests, { draft = draft.copy(guests = it) })
            Text(tr("Guest invitations are completed in your device calendar app. Saving local guest details does not send invitations.", "הזמנות לאורחים מתבצעות באפליקציית לוח השנה של המכשיר. שמירת פרטי אורחים כאן אינה שולחת הזמנות."), style = MaterialTheme.typography.bodySmall)
            Toggle(tr("Enable this event’s reminders", "הפעלת תזכורות לאירוע זה"), draft.remindersEnabled, { draft = draft.copy(remindersEnabled = it) })
            ReminderOffsets(draft.reminders) { draft = draft.copy(reminders = it) }
            Text(localizeMessage(model.app.reminders.status(s.data.settings), LocalHebrew.current), style = MaterialTheme.typography.bodySmall)
            val calendars = s.calendars.filter { it.writable }
            Choice(tr("Calendar destination", "לוח שנה לשמירה"), destination, listOf(null) + calendars.map { it.id },
                { id -> calendars.find { it.id == id }?.let { "${it.name} · ${it.account}" } ?: tr("Personal events in this app", "אירועים אישיים באפליקציה") }, { destination = it })
            if (destination != null && !eligible) {
                Notice(tr("This app keeps the original rule and exports individual occurrences for the next year. External changes are flagged for review.", "האפליקציה שומרת את הכלל המקורי ומייצאת מופעים לשנה הקרובה. שינויים חיצוניים יסומנו לבדיקה."))
                Choice(tr("Reminder owner", "אחראי לתזכורות"), draft.reminderOwner, ReminderOwner.entries, { enumName(it) }, { draft = draft.copy(reminderOwner = it) })
            }
        }
        if (nativeId != null) TextButton(onClick = { deleteNative = true }) { Text(tr("Delete device-calendar event", "מחיקת אירוע מלוח המכשיר"), color = MaterialTheme.colorScheme.error) }
        Section(tr("Occurrence preview", "תצוגה מקדימה של מופעים")) {
            val preview by produceState<List<Occurrence>>(emptyList(), draft, s.data.settings.hebrewDayBoundary) {
                value = withContext(Dispatchers.Default) { runCatching {
                    val start = LocalDate.parse(draft.anchorDate)
                    OccurrenceEngine().expand(draft, maxOf(start, s.date), maxOf(start, s.date).plusYears(6), s.data).take(6)
                }.getOrDefault(emptyList()) }
            }
            if (preview.isEmpty()) Text(tr("No occurrences in the next six years, or the rule is incomplete.", "אין מופעים בשש השנים הקרובות, או שהכלל טרם הושלם."))
            preview.forEach { o -> Text("${o.date} · " + (o.start?.atZone(ZoneId.of(o.zoneId))?.toString() ?: o.unavailableReason ?: "Unavailable"), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (deleteNative && nativeId != null) ConfirmDialog(tr("Delete this device-calendar event?", "למחוק אירוע זה מלוח המכשיר?"),
        tr("The calendar account may sync this deletion to your other devices.", "המחיקה עשויה להסתנכרן למכשירים אחרים דרך חשבון לוח השנה."), tr("Delete", "מחיקה"), { deleteNative = false }) {
        model.act { withContext(Dispatchers.IO) { model.app.calendars.deleteOrdinary(nativeId) }; model.refresh(); deleteNative = false; onDismiss() }
    }
    if (goTo) GoToDialog(runCatching { LocalDate.parse(draft.anchorDate) }.getOrDefault(s.date), { draft = draft.copy(anchorDate = it.toString()); goTo = false }, { goTo = false })
}

@Composable fun EventDetails(s: ScreenData, model: AppViewModel, occurrence: Occurrence, onDismiss: () -> Unit, onEdit: (String) -> Unit) {
    val event = s.data.events.find { it.id == occurrence.eventId } ?: return
    var deleting by remember { mutableStateOf(false) }
    var deleteScope by remember { mutableStateOf("single") }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(event.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${occurrence.date} · ${event.zoneId}")
            occurrence.start?.let { Text(it.atZone(ZoneId.of(event.zoneId)).toString()) }
            occurrence.end?.let { Text("→ " + it.atZone(ZoneId.of(event.zoneId))) }
            occurrence.unavailableReason?.let { Notice(it, true) }
            if (event.venue.isNotBlank()) Text(event.venue)
            if (event.description.isNotBlank()) Text(event.description)
            if (event.guests.isNotBlank()) Text(tr("Guests: ", "אורחים: ") + event.guests)
            if (event.recurrence.frequency != Frequency.NONE) {
                TextButton(onClick = { onEdit("single") }) { Text(tr("Edit this occurrence", "עריכת מופע זה")) }
                TextButton(onClick = { onEdit("following") }) { Text(tr("Edit this and following", "עריכת מופע זה והבאים")) }
            }
            TextButton(onClick = { onEdit("series") }) { Text(if (event.recurrence.frequency == Frequency.NONE) tr("Edit event", "עריכת אירוע") else tr("Edit entire series", "עריכת כל הסדרה")) }
            if (event.guests.isNotBlank()) TextButton(onClick = {
                val link = s.data.exports.find { it.id == occurrence.key }
                val intent = if (link != null) Intent(Intent.ACTION_EDIT, android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, link.providerId)).putExtra(Intent.EXTRA_EMAIL, event.guests)
                else model.app.calendars.invitationIntent(event.copy(anchorDate = occurrence.date.toString(), recurrence = Recurrence()))
                if (link == null) {
                    occurrence.start?.let { intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.toEpochMilli()) }
                    occurrence.end?.let { intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, it.toEpochMilli()) }
                    intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, occurrence.allDay)
                    intent.putExtra(android.provider.CalendarContract.Events.EVENT_TIMEZONE, occurrence.zoneId)
                }
                model.act { context.startActivity(intent) }
            }) { Text(tr("Open guest invitations in calendar", "פתיחת הזמנות בלוח השנה")) }
            if (event.recurrence.frequency != Frequency.NONE) Choice(tr("Delete scope", "טווח מחיקה"), deleteScope, listOf("single", "following", "series"),
                { scope -> when (scope) { "single" -> tr("This occurrence", "מופע זה"); "following" -> tr("This and following", "מופע זה והבאים"); else -> tr("Entire series", "כל הסדרה") } }, { deleteScope = it })
            TextButton(onClick = { deleting = true }) { Text(tr("Delete", "מחיקה"), color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "סגירה")) } })
    if (deleting) ConfirmDialog(tr("Delete ${event.title}?", "למחוק את ${event.title}?"),
        tr("The chosen occurrences and their reminders will be removed. Managed exports are reconciled afterward.", "המופעים שנבחרו והתזכורות שלהם יוסרו. הייצוא המנוהל יעודכן לאחר מכן."),
        tr("Delete", "מחיקה"), { deleting = false }) {
        deleting = false
        model.act {
            when {
                event.recurrence.frequency == Frequency.NONE || deleteScope == "series" -> model.repository.deleteEvent(event.id)
                deleteScope == "single" -> model.repository.excludeOccurrence(event, occurrence.date)
                else -> if (occurrence.date <= LocalDate.parse(event.anchorDate)) model.repository.deleteEvent(event.id) else model.repository.saveEvent(event.copy(recurrence = event.recurrence.copy(until = occurrence.date.minusDays(1).toString())))
            }
            onDismiss()
        }
    }
}

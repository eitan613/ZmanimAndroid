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
    var timePicker by remember { mutableStateOf(false) }
    var untilPicker by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    val eligible = draft.calendar == CalendarSystem.GREGORIAN && draft.timeKind != EventTimeKind.ZMAN && draft.recurrence.frequency == Frequency.NONE &&
        (draft.timeKind == EventTimeKind.ALL_DAY || runCatching {
            val start = LocalDateTime.of(LocalDate.parse(draft.anchorDate), LocalTime.parse(draft.localTime))
            val segments = localIntervalSegments(start, start.plusMinutes(duration.toLongOrNull() ?: 0), ZoneId.of(draft.zoneId))
            segments.size == 1 && Duration.between(segments[0].first, segments[0].second).toMinutes() == duration.toLongOrNull()
        }.getOrDefault(false))
    var deleteNative by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val locale = LocalProductLocale.current
    EditorDialog(if (original == null || nativeId == null && s.data.events.none { it.id == original.id }) tr("New event", "אירוע חדש") else tr("Edit event", "עריכת אירוע"), draft != initial || duration != initial.durationMinutes.toString() || destination != (nativeCalendar ?: initial.exportCalendarId), onDismiss, {
        val event = draft.copy(durationMinutes = duration.toLongOrNull() ?: error("Enter a whole duration in minutes."),
            offsetMinutes = offset.toDoubleOrNull() ?: error("Enter a valid offset."), exportCalendarId = destination,
            profileId = draft.profileId.takeIf { draft.timeKind == EventTimeKind.ZMAN }, methodId = draft.methodId.takeIf { draft.timeKind == EventTimeKind.ZMAN })
        OccurrenceEngine().validate(event, model.repository.data)
        if (destination != null && eligible && occurrence == null && (original == null || s.data.events.none { it.id == original.id } || nativeId != null)) {
            val providerId = withContext(Dispatchers.IO) { model.app.calendars.saveOrdinary(event, destination!!, nativeId) }
            if (event.guests.isNotBlank()) context.startActivity(Intent(Intent.ACTION_EDIT,
                android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, providerId)).putExtra(Intent.EXTRA_EMAIL, event.guests))
            model.refresh()
        } else if (nativeId != null && destination != null) {
            val o = OccurrenceEngine().occurrence(event, LocalDate.parse(event.anchorDate), model.repository.data)
            val link = withContext(Dispatchers.IO) { model.app.calendars.adopt(event, o, destination!!, nativeId) }
            model.repository.update { it.copy(events = it.events.filterNot { e -> e.id == event.id } + event,
                exports = it.exports.filterNot { e -> e.id == link.id } + link) }
        } else when (occurrence?.key) {
            "single" -> model.repository.replaceOccurrence(original!!, occurrence.date, event)
            "following" -> model.repository.splitSeries(original!!, occurrence.date, event)
            else -> model.repository.saveEvent(event)
        }
    }) {
        Field(tr("Title", "כותרת"), draft.title, { draft = draft.copy(title = it) })
        Choice(tr("Date calendar", "לוח להזנת תאריך"), draft.calendar, CalendarSystem.entries, { enumName(it) }, { draft = draft.copy(calendar = it) })
        OutlinedButton(onClick = { goTo = true }, modifier = Modifier.fillMaxWidth()) {
            Column { Text(runCatching { LocalDate.parse(draft.anchorDate).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM uuuu", locale)) }.getOrDefault(draft.anchorDate)); Text(runCatching { CalendarEngine().dateLabel(LocalDate.parse(draft.anchorDate), LocalHebrew.current) }.getOrDefault("")) }
        }
        Choice(tr("Time", "זמן"), draft.timeKind, EventTimeKind.entries, { enumName(it) }, { draft = draft.copy(timeKind = it) })
        if (draft.timeKind == EventTimeKind.ALL_DAY && draft.calendar == CalendarSystem.HEBREW) {
            if (s.data.settings.hebrewDayBoundary == null) Choice(tr("App-wide Hebrew all-day boundary", "גבול יום עברי לכל האפליקציה"), s.data.settings.hebrewDayBoundary, listOf(null) + HebrewDayBoundary.entries,
                { enumName(it) }, { value -> model.settings { it.copy(hebrewDayBoundary = value) } })
            else Text(tr("All-day boundary: ", "גבול יום שלם: ") + enumName(s.data.settings.hebrewDayBoundary) + tr(". Change it in Settings to preview the effect on all events.", ". ניתן לשנות בהגדרות ולצפות בהשפעה על כל האירועים."))
            Text(tr("This setting applies to every Hebrew all-day event.", "הגדרה זו חלה על כל האירועים העבריים של יום שלם."))
        }
        if (draft.timeKind == EventTimeKind.FIXED) {
            OutlinedButton(onClick = { timePicker = true }, modifier = Modifier.fillMaxWidth()) {
                val local = runCatching { LocalTime.parse(draft.localTime) }.getOrDefault(LocalTime.of(9, 0))
                Text(local.format(java.time.format.DateTimeFormatter.ofPattern(if (s.data.settings.use24Hour ?: DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", LocalProductLocale.current)))
            }
            if (draft.calendar == CalendarSystem.HEBREW) Toggle(tr("Place this time on the previous civil evening", "זמן זה חל בערב הלועזי הקודם"), draft.previousEvening, { draft = draft.copy(previousEvening = it) })
        }
        if (draft.timeKind == EventTimeKind.ZMAN) {
            Text(tr("Calculation profile: ", "פרופיל חישוב: ") + (s.data.profile(draft.profileId)?.name ?: tr("Choose under Advanced", "בחירה באפשרויות מתקדמות")))
            val p = s.data.profile(draft.profileId)
            val ids = p?.let { it.selected.filter { id -> MethodCatalog.byId[id]?.isDuration != true } +
                MethodCatalog.methods.filterNot { method -> method.isDuration }.map { method -> method.id } + it.custom.map { z -> z.id } }?.distinct().orEmpty()
            Choice(tr("Calculation", "חישוב"), draft.methodId, listOf(null) + ids, { id -> if (id == null) tr("Choose calculation", "בחירת חישוב") else methodLabel(id, p, LocalLabelHebrew.current) }, { draft = draft.copy(methodId = it) })
            draft.methodId?.let { Text(methodExplanation(it, p), style = MaterialTheme.typography.bodySmall) }
            Field(tr("Minutes offset (+ after, − before)", "הפרש בדקות (+ אחרי, − לפני)"), offset, { offset = it; it.toDoubleOrNull()?.let { n -> draft = draft.copy(offsetMinutes = n) } }, numeric = true)
        }
        if (draft.timeKind != EventTimeKind.ALL_DAY) Field(tr("Duration (minutes)", "משך (דקות)"), duration, { duration = it }, numeric = true)
        TextButton(onClick = { advanced = !advanced }) { Text(tr("Advanced", "אפשרויות מתקדמות")) }
        if (advanced) Section(tr("Timezone and calculation context", "אזור זמן ופרטי חישוב")) {
        Field(tr("Event timezone", "אזור הזמן של האירוע"), draft.zoneId, { draft = draft.copy(zoneId = it) })
        if (draft.timeKind == EventTimeKind.ZMAN) Choice(tr("Calculation profile", "פרופיל חישוב"), draft.profileId, s.data.profiles.map { it.id },
            { id -> s.data.profile(id)?.name.orEmpty() }, { draft = draft.copy(profileId = it, methodId = null) })
        if (draft.timeKind == EventTimeKind.ZMAN || draft.timeKind == EventTimeKind.ALL_DAY && draft.calendar == CalendarSystem.HEBREW)
            LocationChoice(tr("Calculation location", "מקום החישוב"), draft.location, s.data) { draft = draft.copy(location = it) }
        }
        Section(tr("Repeat", "חזרה")) {
            Choice(tr("Frequency", "תדירות"), draft.recurrence.frequency, (if (occurrence?.key == "single") listOf(Frequency.NONE) else Frequency.entries), { enumName(it) }, { draft = draft.copy(recurrence = draft.recurrence.copy(frequency = it)) })
            if (draft.recurrence.frequency != Frequency.NONE) {
                Choice(tr("Recurrence calendar", "לוח לחזרה"), draft.repeatCalendar, CalendarSystem.entries, { enumName(it) },
                    { draft = draft.copy(repeatCalendar = it, recurrence = draft.recurrence.copy(missingDay = null, ordinaryAdar = null, leapAdar = null)) })
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
                OutlinedButton(onClick = { untilPicker = true }) { Text(tr("End date: ", "תאריך סיום: ") + (draft.recurrence.until ?: tr("No end date", "ללא תאריך סיום"))) }
                if (draft.recurrence.until != null) TextButton(onClick = { draft = draft.copy(recurrence = draft.recurrence.copy(until = null)) }) { Text(tr("Remove end date", "הסרת תאריך סיום")) }
                Field(tr("Maximum occurrences (optional)", "מספר מופעים מרבי (רשות)"), count, { count = it; draft = draft.copy(recurrence = draft.recurrence.copy(count = if (it.isBlank()) null else it.toIntOrNull() ?: 0)) }, numeric = true)
            }
            if (draft.recurrence.frequency in setOf(Frequency.MONTHLY, Frequency.YEARLY)) {
                Choice(tr("When the original day is absent", "כאשר היום המקורי אינו קיים"), draft.recurrence.missingDay, listOf(null) + MissingDayPolicy.entries, { enumName(it) }, { draft = draft.copy(recurrence = draft.recurrence.copy(missingDay = it)) })
                if (draft.repeatCalendar == CalendarSystem.HEBREW && draft.recurrence.frequency == Frequency.YEARLY) {
                    val jd = runCatching { com.kosherjava.zmanim.hebrewcalendar.JewishDate(LocalDate.parse(draft.anchorDate)) }.getOrNull()
                    if (jd?.jewishMonth == 12 && !jd.isJewishLeapYear)
                        Choice(tr("Ordinary Adar in a leap year", "אדר רגיל בשנה מעוברת"), draft.recurrence.ordinaryAdar, listOf(null) + AdarPolicy.entries, { enumName(it) }, { draft = draft.copy(recurrence = draft.recurrence.copy(ordinaryAdar = it)) })
                    if (jd?.jewishMonth in setOf(12, 13) && jd?.isJewishLeapYear == true)
                        Choice(tr("Leap-year Adar in an ordinary year", "אדר של שנה מעוברת בשנה רגילה"), draft.recurrence.leapAdar, listOf(null) + LeapAdarPolicy.entries, { enumName(it) }, { draft = draft.copy(recurrence = draft.recurrence.copy(leapAdar = it)) })
                }
            }
        }
        TextButton(onClick = { details = !details }) { Text(tr("Details, reminders and Save to", "פרטים, תזכורות ושמירה")) }
        if (details) Section(tr("Details and reminders", "פרטים ותזכורות")) {
            Field(tr("Location / venue", "מקום האירוע"), draft.venue, { draft = draft.copy(venue = it) })
            Field(tr("Description", "תיאור"), draft.description, { draft = draft.copy(description = it) }, multiline = true)
            if (advanced || draft.guests.isNotBlank()) {
            Field(tr("Guest email addresses", "כתובות דוא״ל של אורחים"), draft.guests, { draft = draft.copy(guests = it) })
            Text(tr("Guest invitations are completed in your device calendar app. Saving local guest details does not send invitations.", "הזמנות לאורחים מתבצעות באפליקציית לוח השנה של המכשיר. שמירת פרטי אורחים כאן אינה שולחת הזמנות."), style = MaterialTheme.typography.bodySmall)
            }
            Toggle(tr("Enable this event’s reminders", "הפעלת תזכורות לאירוע זה"), draft.remindersEnabled, { draft = draft.copy(remindersEnabled = it) })
            ReminderOffsets(draft.reminders) { draft = draft.copy(reminders = it) }
            Text(localizeMessage(model.app.reminders.status(s.data.settings), LocalHebrew.current), style = MaterialTheme.typography.bodySmall)
            val calendars = s.calendars.filter { it.writable }
            if (nativeId != null) {
                Text(tr("Save to: ", "שמירה ב־") + (calendars.find { it.id == nativeCalendar }?.name ?: tr("Original device calendar", "לוח המכשיר המקורי")))
            } else if (calendars.isEmpty()) {
                Text(tr("Saved in this app", "נשמר באפליקציה"))
                Text(tr("Local events need no calendar permission. Connect device calendars in Settings → Data to choose another destination.", "אירועים מקומיים אינם דורשים הרשאת לוח שנה. לחיבור לוחות מכשיר: הגדרות ← נתונים."), style = MaterialTheme.typography.bodySmall)
            } else Choice(tr("Save to", "שמירה ב־"), destination, listOf(null) + calendars.map { it.id },
                { id -> calendars.find { it.id == id }?.let { "${it.name} · ${it.account}" } ?: tr("Personal events in this app", "אירועים אישיים באפליקציה") }, { destination = it })
            if (destination != null && !eligible) {
                Notice(tr("This app keeps the original rule and exports individual occurrences for the next year. External changes are flagged for review.", "האפליקציה שומרת את הכלל המקורי ומייצאת מופעים לשנה הקרובה. שינויים חיצוניים יסומנו לבדיקה."))
                Choice(tr("Reminder owner", "אחראי לתזכורות"), draft.reminderOwner, ReminderOwner.entries, { enumName(it) }, { draft = draft.copy(reminderOwner = it) })
            }
        }
        if (nativeId != null) TextButton(onClick = { deleteNative = true }) { Text(tr("Delete device-calendar event", "מחיקת אירוע מלוח המכשיר"), color = MaterialTheme.colorScheme.error) }
        Section(tr("Occurrence preview", "תצוגה מקדימה של מופעים")) {
            val previewDraft = draft.copy(durationMinutes = duration.toLongOrNull() ?: 0, offsetMinutes = offset.toDoubleOrNull() ?: Double.NaN)
            val preview by produceState<Pair<String?, List<Occurrence>>>(null to emptyList(), previewDraft, s.data) {
                value = withContext(Dispatchers.Default) {
                    try {
                        OccurrenceEngine().validate(previewDraft, s.data)
                        val start = LocalDate.parse(previewDraft.anchorDate)
                        null to OccurrenceEngine().expand(previewDraft, start, start.plusYears(6), s.data).take(6)
                    } catch (e: Exception) { (e.message ?: "Complete the event rule to preview occurrences.") to emptyList() }
                }
            }
            if (preview.first != null) Text(tr("Complete this draft: ", "להשלמת הטיוטה: ") + localizeMessage(preview.first!!, LocalHebrew.current))
            else if (preview.second.isEmpty()) Text(tr("This valid rule has no occurrence in the next six years. A clock change can skip an entire local interval.", "לכלל תקין זה אין מופע בשש השנים הבאות. שינוי שעון עשוי לדלג על כל המרווח המקומי."))
            preview.second.forEach { o -> Text(TimeFormatting.occurrence(o, s.data.settings, DateFormat.is24HourFormat(context), LocalProductLocale.current), style = MaterialTheme.typography.bodySmall) }
            HelpLink("events")
        }
    }
    if (deleteNative && nativeId != null) ConfirmDialog(tr("Delete this device-calendar event?", "למחוק אירוע זה מלוח המכשיר?"),
        tr("The calendar account may sync this deletion to your other devices.", "המחיקה עשויה להסתנכרן למכשירים אחרים דרך חשבון לוח השנה."), tr("Delete", "מחיקה"), { deleteNative = false }) {
        model.act { withContext(Dispatchers.IO) { model.app.calendars.deleteOrdinary(nativeId) }; model.refresh(); deleteNative = false; onDismiss() }
    }
    if (timePicker) TimeSelectionDialog(runCatching { LocalTime.parse(draft.localTime) }.getOrDefault(LocalTime.of(9, 0)), s.data.settings.use24Hour ?: DateFormat.is24HourFormat(context), { draft = draft.copy(localTime = it.toString()); timePicker = false }, { timePicker = false })
    if (untilPicker) GoToDialog(runCatching { LocalDate.parse(draft.recurrence.until ?: draft.anchorDate) }.getOrDefault(s.date), { draft = draft.copy(recurrence = draft.recurrence.copy(until = it.toString())); untilPicker = false }, { untilPicker = false }, draft.repeatCalendar)
    if (goTo) GoToDialog(runCatching { LocalDate.parse(draft.anchorDate) }.getOrDefault(s.date), { draft = draft.copy(anchorDate = it.toString()); goTo = false }, { goTo = false }, draft.calendar)
}

@Composable fun EventDetails(s: ScreenData, model: AppViewModel, occurrence: Occurrence, onDismiss: () -> Unit, onEdit: (String) -> Unit) {
    val event = s.data.events.find { it.id == occurrence.eventId } ?: return
    var deleting by remember { mutableStateOf(false) }
    var deleteScope by remember { mutableStateOf("single") }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(event.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(TimeFormatting.occurrence(occurrence, s.data.settings, DateFormat.is24HourFormat(context), LocalProductLocale.current))
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

/** Opens directly from a widget, including arbitrary recurring device-calendar instances. */
@Composable fun ProviderEventEditor(s: ScreenData, model: AppViewModel, occurrence: Occurrence, onDismiss: () -> Unit) {
    val loaded by produceState<Pair<Boolean, Event?>>(false to null, occurrence.key) {
        value = true to withContext(Dispatchers.IO) { model.app.calendars.instanceEvent(occurrence) }
    }
    val initial = loaded.second
    if (initial == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Edit event", "עריכת אירוע")) },
            text = { if (loaded.first) Text(tr("This occurrence is no longer available.", "מופע זה אינו זמין עוד.")) else CircularProgressIndicator() },
            confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "סגירה")) } })
        return
    }
    var draft by rememberDraft("provider-instance-${occurrence.key}", initial)
    val writable = model.app.calendars.canWrite() && s.calendars.any { it.id == occurrence.sourceCalendarId && it.writable }
    var datePicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    val initialDuration = (if (initial.timeKind == EventTimeKind.ALL_DAY) initial.durationMinutes / 1440 else initial.durationMinutes).toString()
    var duration by rememberSaveable(occurrence.key) { mutableStateOf(initialDuration) }
    val context = LocalContext.current
    EditorDialog(tr("Edit event", "עריכת אירוע"), writable && (draft != initial || duration != initialDuration), onDismiss, {
        if (writable) {
            val units = duration.toLongOrNull() ?: error("Enter a whole duration.")
            val edited = draft.copy(durationMinutes = if (draft.timeKind == EventTimeKind.ALL_DAY) Math.multiplyExact(units, 1440L) else units)
            withContext(Dispatchers.IO) { model.app.calendars.updateInstance(occurrence, initial, edited) }
            model.refresh()
        }
    }, saveLabel = if (writable) null else tr("Close", "סגירה")) {
        Text(tr("Changes apply to this occurrence in its original calendar.", "השינויים יחולו על מופע זה בלוח השנה המקורי."), style = MaterialTheme.typography.bodySmall)
        if (!writable) Notice(tr("This calendar is read-only or calendar write access is missing.", "לוח זה לקריאה בלבד או שחסרה הרשאת כתיבה ללוח השנה."))
        Field(tr("Title", "כותרת"), draft.title, { draft = draft.copy(title = it) }, enabled = writable)
        OutlinedButton(onClick = { datePicker = true }, enabled = writable, modifier = Modifier.fillMaxWidth()) {
            Text(LocalDate.parse(draft.anchorDate).format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy", LocalProductLocale.current)))
        }
        if (writable) Toggle(tr("All day", "כל היום"), draft.timeKind == EventTimeKind.ALL_DAY, {
            draft = draft.copy(timeKind = if (it) EventTimeKind.ALL_DAY else EventTimeKind.FIXED)
            duration = if (it) "1" else "60"
        })
        if (draft.timeKind == EventTimeKind.FIXED) OutlinedButton(onClick = { timePicker = true }, enabled = writable, modifier = Modifier.fillMaxWidth()) {
            Text(LocalTime.parse(draft.localTime).format(java.time.format.DateTimeFormatter.ofPattern(if (s.data.settings.use24Hour ?: DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", LocalProductLocale.current)))
        }
        Field(if (draft.timeKind == EventTimeKind.ALL_DAY) tr("Duration (days)", "משך (ימים)") else tr("Duration (minutes)", "משך (דקות)"), duration, { duration = it }, numeric = true, enabled = writable)
        Field(tr("Location / venue", "מקום האירוע"), draft.venue, { draft = draft.copy(venue = it) }, enabled = writable)
        Field(tr("Description", "תיאור"), draft.description, { draft = draft.copy(description = it) }, multiline = true, enabled = writable)
    }
    if (datePicker) GoToDialog(LocalDate.parse(draft.anchorDate), { draft = draft.copy(anchorDate = it.toString()); datePicker = false }, { datePicker = false }, CalendarSystem.GREGORIAN)
    if (timePicker) TimeSelectionDialog(LocalTime.parse(draft.localTime), s.data.settings.use24Hour ?: DateFormat.is24HourFormat(context),
        { draft = draft.copy(localTime = it.toString()); timePicker = false }, { timePicker = false })
}

@Composable fun ProviderEventDetails(s: ScreenData, model: AppViewModel, initial: Occurrence, onDismiss: () -> Unit, onEdit: (Event, Long) -> Unit) {
    var current by remember { mutableStateOf<Occurrence?>(null) }
    var ordinary by remember { mutableStateOf<Pair<Event, Long>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(initial.key) {
        try {
            withContext(Dispatchers.IO) {
                current = model.app.calendars.instance(initial.providerId!!, initial.start!!.toEpochMilli())
                ordinary = model.app.calendars.ordinaryEvent(initial.providerId)
            }
        } catch (e: Exception) { error = e.message }
        loaded = true
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(current?.title ?: initial.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!loaded) CircularProgressIndicator()
            error?.let { Notice(it, true) }
            current?.let { o ->
                Text(TimeFormatting.occurrence(o, s.data.settings, DateFormat.is24HourFormat(context), LocalProductLocale.current))
                ordinary?.first?.venue?.takeIf { it.isNotBlank() }?.let { Text(it) }
                ordinary?.first?.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                val editable = ordinary?.takeIf { pair -> s.calendars.any { it.id == pair.second && it.writable } }
                if (editable != null) TextButton(onClick = { onEdit(editable.first, editable.second) }) { Text(tr("Edit event", "עריכת אירוע")) }
                else Text(tr("Read-only details. Recurring or unsupported rules are edited in their device calendar.", "פרטים לקריאה בלבד. כללים חוזרים או לא נתמכים נערכים בלוח המכשיר."))
                TextButton(onClick = {
                    context.startActivity(model.app.calendars.viewIntent(o.providerId!!)
                        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, o.start!!.toEpochMilli())
                        .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, o.end!!.toEpochMilli()))
                }) { Text(tr("Open in device calendar", "פתיחה בלוח המכשיר")) }
            }
            if (loaded && current == null && error == null) Text(tr("This occurrence was deleted or calendar access is missing.", "מופע זה נמחק או שחסרה הרשאת לוח שנה."))
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "סגירה")) } })
}

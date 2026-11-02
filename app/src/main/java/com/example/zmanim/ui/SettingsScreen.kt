package com.example.zmanim.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.zmanim.domain.*
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun SettingsScreen(s: ScreenData, model: AppViewModel,
    permission: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onProfiles: () -> Unit, onLocations: () -> Unit, onBackup: () -> Unit, onBack: () -> Unit) {
    val settings = s.data.settings
    val context = LocalContext.current
    var boundary by remember { mutableStateOf<HebrewDayBoundary?>(null) }
    var showBackgroundExplanation by remember { mutableStateOf(false) }
    var approximate by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<ExportLink?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().widthIn(max = 840.dp).padding(horizontal = 12.dp)) {
        AppHeader(tr("Settings", "הגדרות"), onBack = onBack)
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            listOf(tr("Display", "תצוגה"), tr("Calendar", "לוח שנה"), tr("Zmanim", "זמנים"), tr("Permissions", "הרשאות"), tr("Data", "נתונים")).forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        key(tab) {
            LazyColumn(Modifier.testTag("settingsList").fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (tab == 0) item { Section(tr("Appearance and language", "מראה ושפה")) {
                    Choice(tr("Appearance", "מראה"), settings.appearance, Appearance.entries, { enumName(it) }, { value -> model.settings { it.copy(appearance = value) } })
                    Choice(tr("Interface language", "שפת הממשק"), settings.language, Language.entries, { enumName(it) }, { value -> model.settings { it.copy(language = value) } })
                    Choice(tr("Calendar and zmanim labels", "תוויות לוח שנה וזמנים"), settings.labels, LabelLanguage.entries, { enumName(it) }, { value -> model.settings { it.copy(labels = value) } })
                    Choice(tr("Open app into", "תצוגה בפתיחת האפליקציה"), settings.openingView, OpeningView.entries, { enumName(it) }, { value -> model.settings { it.copy(openingView = value) } })
                } }
                if (tab == 1) item { Section(tr("Calendar", "לוח שנה")) {
                    Choice(tr("Primary calendar", "לוח ראשי"), settings.primaryCalendar, CalendarSystem.entries, { enumName(it) }, { value -> model.settings { it.copy(primaryCalendar = value) } })
                    Choice(tr("Default calendar view", "תצוגת לוח ברירת מחדל"), settings.calendarView, CalendarView.entries, { enumName(it) }, { value -> model.settings { it.copy(calendarView = value) } })
                    Choice(tr("Holiday and parsha schedule", "סדר חגים ופרשות"), settings.inIsrael ?: false, listOf(true, false),
                        { value -> when(value) { true -> tr("Israel", "ישראל"); false -> tr("Diaspora", "חו״ל") } }, { value -> model.settings { it.copy(inIsrael = value, scheduleInitialized = true) } })
                    Choice(tr("Week starts on", "היום הראשון בשבוע"), settings.mondayFirst, listOf(false, true), { if (it) tr("Monday", "שני") else tr("Sunday", "ראשון") }, { value -> model.settings { it.copy(mondayFirst = value) } })
                    Toggle(tr("Days progress right to left", "הימים מתקדמים מימין לשמאל"), settings.reverseGrid, { value -> model.settings { it.copy(reverseGrid = value) } }, tr("Independent of interface and label language.", "ללא תלות בשפת הממשק או התוויות."))
                    Toggle(tr("Swipe changes years", "החלקה משנה שנים"), settings.swapScrollAxes, { value -> model.settings { it.copy(swapScrollAxes = value) } },
                        tr("Off: swipe changes one month. On: swipe changes one year.", "כבוי: החלקה משנה חודש. פעיל: החלקה משנה שנה."))
                    Choice(tr("Double-tap a day", "לחיצה כפולה על יום"), settings.doubleTap, DoubleTapAction.entries, { enumName(it) }, { value -> model.settings { it.copy(doubleTap = value) } })
                    Toggle(tr("Show holidays", "הצגת חגים"), settings.showHolidays, { value -> model.settings { it.copy(showHolidays = value) } })
                    Toggle(tr("Show modern holidays", "הצגת מועדים מודרניים"), settings.modernHolidays, { value -> model.settings { it.copy(modernHolidays = value) } })
                    Toggle(tr("Show parsha and special parsha", "הצגת פרשה ופרשה מיוחדת"), settings.showParsha, { value -> model.settings { it.copy(showParsha = value) } })
                    Toggle(tr("Show Omer in month view", "הצגת ספירת העומר בחודש"), settings.showOmer, { value -> model.settings { it.copy(showOmer = value) } })
                    Toggle(tr("Color special days", "צביעת ימים מיוחדים"), settings.colorSpecialDays, { value -> model.settings { it.copy(colorSpecialDays = value) } })
                    if (settings.colorSpecialDays) {
                        specialDayDefaults.keys.forEach { category ->
                            ColorSetting(category, settings.specialDayColor(category)) { color -> model.settings { it.copy(specialDayColors = it.specialDayColors + (category to color)) } }
                        }
                        TextButton(onClick = { model.settings { it.copy(specialDayColors = emptyMap(), holidayTint = specialDayDefaults.getValue("holiday"), shabbatTint = specialDayDefaults.getValue("shabbat")) } }) { Text(tr("Reset colors", "איפוס צבעים")) }
                    }
                    Choice(tr("Hebrew all-day event boundary", "גבולות יום שלם לאירוע עברי"), settings.hebrewDayBoundary, (if (settings.hebrewDayBoundary == null) listOf(null) else emptyList()) + HebrewDayBoundary.entries, { enumName(it) }, { value ->
                        if (value != null && value != settings.hebrewDayBoundary && s.data.events.any { it.calendar == CalendarSystem.HEBREW && it.timeKind == EventTimeKind.ALL_DAY }) boundary = value
                        else model.settings { it.copy(hebrewDayBoundary = value) }
                    })
                } }
                if (tab == 2) item { Section(tr("Zmanim and time", "זמנים ותבנית שעה")) {
                    OutlinedButton(onClick = onProfiles, modifier = Modifier.fillMaxWidth()) { Text(tr("Manage profiles and custom zmanim", "ניהול פרופילים וזמנים אישיים")) }
                    OutlinedButton(onClick = onLocations, modifier = Modifier.fillMaxWidth()) { Text(tr("Manage locations", "ניהול מקומות")) }
                    Choice(tr("Time format", "תבנית שעה"), settings.use24Hour, listOf(null, true, false),
                        { value -> when(value) { true -> tr("24-hour", "24 שעות"); false -> tr("12-hour", "12 שעות"); null -> tr("System", "לפי המערכת") } }, { value -> model.settings { it.copy(use24Hour = value) } })
                    Toggle(tr("Show seconds", "הצגת שניות"), settings.showSeconds, { value -> model.settings { it.copy(showSeconds = value) } })
                    Choice(tr("Minute rounding", "עיגול דקות"), settings.rounding, Rounding.entries, { enumName(it) }, { value -> model.settings { it.copy(rounding = value) } })
                    Text(tr("+ means displayed later than the exact time; − means earlier. Reminders use the exact instant.", "+ מסמן תצוגה מאוחרת מהזמן המדויק; − מסמן מוקדמת. התזכורות משתמשות בזמן המדויק."), style = MaterialTheme.typography.bodySmall)
                    Toggle(tr("Show time remaining", "הצגת הזמן שנותר"), settings.showCountdown, { value -> model.settings { it.copy(showCountdown = value) } })
                } }
                if (tab == 3) item { Section(tr("Notifications and alarms", "התראות ותזכורות")) {
                    Text(localizeMessage(model.app.reminders.status(settings), LocalHebrew.current))
                    Toggle(tr("Event notifications", "התראות אירועים"), settings.eventNotifications, { value -> model.settings { it.copy(eventNotifications = value) } })
                    Toggle(tr("Selected zmanim reminders", "תזכורות לזמנים שנבחרו"), settings.zmanNotifications, { value -> model.settings { it.copy(zmanNotifications = value) } })
                    Button(onClick = { if (Build.VERSION.SDK_INT >= 33) permission.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) else context.startActivity(model.app.reminders.notificationSettingsIntent()) }) { Text(tr("Notification access", "הרשאת התראות")) }
                    if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = { context.startActivity(model.app.reminders.exactAccessIntent()) }) { Text(tr("Precise alarm access", "הרשאת תזכורות מדויקות")) }
                    Toggle(tr("Allow approximate reminders", "אפשר תזכורות משוערות"), settings.approximateAlarmsAccepted, { value ->
                        if (value) approximate = true else model.settings { it.copy(approximateAlarmsAccepted = false) }
                    })
                    Toggle(tr("Show Hebrew date notification", "הצגת תאריך עברי בהתראות"), settings.dateNotification, { value -> model.settings { it.copy(dateNotification = value) } })
                    Toggle(tr("Notification date advances at sunset", "התאריך בהתראה מתחלף בשקיעה"), settings.dateNotificationSunset, { value -> model.settings { it.copy(dateNotificationSunset = value) } })
                    Text(tr("The full Hebrew date appears in the notification. Android controls whether the day-number icon is visible in the status bar.", "התאריך העברי המלא מוצג בהתראה. Android קובע אם סמל מספר היום יופיע בשורת המצב."), style = MaterialTheme.typography.bodySmall)
                    model.app.maintenanceError?.let { Notice(it, true) }
                } }
                if (tab == 3) item { Section(tr("Location access", "הרשאת מיקום")) {
                    Text(if (model.app.location.hasAccess()) tr("Location access granted", "ניתנה הרשאת מיקום") else tr("Location access not granted", "לא ניתנה הרשאת מיקום"))
                    Button(onClick = onLocations) { Text(tr("Use current location", "שימוש במיקום הנוכחי")) }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text(tr("Android app permissions", "הרשאות האפליקציה ב־Android")) }
                } }
                if (tab == 3) item { Section(tr("Background location", "מיקום ברקע")) {
                    Toggle(tr("Update location while the app is closed", "עדכון מיקום כשהאפליקציה סגורה"), settings.backgroundLocation, { value ->
                        if (value && (!model.app.location.hasAccess() || !model.app.location.hasBackgroundAccess())) showBackgroundExplanation = true
                        else model.settings { it.copy(backgroundLocation = value) }
                    }, tr("Optional travel-aware recalculation. Android may limit update frequency; the last fix time stays visible.", "חישוב מחדש בנסיעות, לפי בחירה. Android עשוי להגביל עדכונים; מועד המיקום האחרון נשאר גלוי."))
                    if (settings.backgroundLocation && !model.app.location.hasBackgroundAccess()) Notice(tr("Background location is configured but access is missing.", "הוגדר מיקום ברקע אך חסרה הרשאה."))
                } }
                if (tab == 4) item { Section(tr("Device calendars", "לוחות שנה במכשיר")) {
                    Button(onClick = { permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }) { Text(tr("Connect device calendars", "חיבור לוחות השנה במכשיר")) }
                    Toggle(tr("Show device calendar events", "הצגת אירועים מלוחות המכשיר"), settings.showDeviceEvents, { value -> model.settings { it.copy(showDeviceEvents = value) } })
                    s.calendars.forEach { calendar -> Toggle("${calendar.name} · ${calendar.account}", calendar.id in settings.visibleCalendarIds,
                        { value -> model.settings { it.copy(visibleCalendarIds = if (value) it.visibleCalendarIds + calendar.id else it.visibleCalendarIds - calendar.id) } },
                        if (calendar.writable) tr("Writable", "ניתן לעריכה") else tr("Read only", "לקריאה בלבד")) }
                    if (s.calendars.isEmpty()) Text(tr("No calendars available, or calendar access has not been granted.", "אין לוחות זמינים, או שטרם ניתנה הרשאת לוח שנה."))
                    val conflicts = s.data.exports.filter { it.state == "CONFLICT" }
                    if (conflicts.isNotEmpty()) Text(tr("External changes need review", "שינויים חיצוניים דורשים בדיקה"), style = MaterialTheme.typography.titleMedium)
                    conflicts.forEach { link -> TextButton(onClick = { conflict = link }) { Text("${s.data.events.find { it.id == link.eventId }?.title ?: "Removed event"} · ${link.occurrenceDate}: ${link.reason}") } }
                    TextButton(onClick = { model.refresh() }) { Text(tr("Refresh calendars and schedules", "רענון לוחות ותזכורות")) }
                } }
                if (tab == 4) item { Section(tr("Backup and sharing", "גיבוי ושיתוף")) {
                    Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text(tr("Export, share, or import", "ייצוא, שיתוף או ייבוא")) }
                    Text(tr("Everything works offline. Device-calendar accounts sync through Android. Manual backups let you choose which private data to include.", "הנתונים האישיים והחישובים זמינים ללא אינטרנט. חשבונות לוח שנה מסתנכרנים דרך Android. בגיבוי ידני ניתן לבחור אילו נתונים פרטיים לכלול."))
                } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    if (approximate) AlertDialog(onDismissRequest = { approximate = false }, title = { Text(tr("Allow late reminders?", "לאפשר תזכורות שעשויות לאחר?")) },
        text = { Text(tr("Without precise-alarm access, Android can delay these reminders. They will be marked approximate, never precise.", "ללא הרשאת תזכורות מדויקות, Android עשוי לעכב תזכורות אלו. הן יסומנו כמשוערות.")) },
        confirmButton = { TextButton(onClick = { model.settings { it.copy(approximateAlarmsAccepted = true) }; approximate = false }) { Text(tr("Allow approximate", "אפשר תזכורות משוערות")) } },
        dismissButton = { TextButton(onClick = { approximate = false }) { Text(tr("Cancel", "ביטול")) } })
    if (showBackgroundExplanation) AlertDialog(onDismissRequest = { showBackgroundExplanation = false }, title = { Text(tr("Travel updates in the background", "עדכוני נסיעה ברקע")) },
        text = { Text(tr("Background location updates the place used by follow-location reminders and widgets when you travel. First allow foreground location, then enable “Allow all the time” in Android settings. Saved places work without this access. Return here and enable this setting after granting access.", "מיקום ברקע מעדכן תזכורות ויישומונים שעוקבים אחר המיקום בנסיעות. תחילה יש לאפשר מיקום בזמן שימוש, ואז לבחור ״כל הזמן״ בהגדרות Android. מקומות שמורים אינם זקוקים להרשאה זו. לאחר מתן ההרשאה יש לחזור ולהפעיל הגדרה זו.")) },
        confirmButton = { TextButton(onClick = {
            showBackgroundExplanation = false
            if (!model.app.location.hasAccess()) permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            else if (Build.VERSION.SDK_INT == 29) permission.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
        }) { Text(tr("Open permissions", "פתיחת הרשאות")) } }, dismissButton = { TextButton(onClick = { showBackgroundExplanation = false }) { Text(tr("Cancel", "ביטול")) } })
    boundary?.let { selected ->
        val affected = s.data.events.filter { it.calendar == CalendarSystem.HEBREW && it.timeKind == EventTimeKind.ALL_DAY }
        AlertDialog(onDismissRequest = { boundary = null }, title = { Text(tr("Change all Hebrew all-day events?", "לשנות את כל האירועים העבריים של יום שלם?")) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("${affected.size} event rules, their reminders, and future managed exports will use the new boundary.", "${affected.size} כללי אירועים, התזכורות שלהם והייצוא העתידי ישתמשו בגבול החדש."))
                affected.take(3).forEach { event ->
                    val nextData = s.data.copy(settings = settings.copy(hebrewDayBoundary = selected))
                    val preview = OccurrenceEngine().expand(event, s.date, s.date.plusYears(1), nextData).firstOrNull()
                    Text("${event.title}: ${preview?.start ?: preview?.unavailableReason ?: "No occurrence"} → ${preview?.end ?: ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }, confirmButton = { TextButton(onClick = { model.settings { it.copy(hebrewDayBoundary = selected) }; boundary = null }) { Text(tr("Apply to all", "החלה על הכול")) } },
            dismissButton = { TextButton(onClick = { boundary = null }) { Text(tr("Cancel", "ביטול")) } })
    }
    conflict?.let { link -> AlertDialog(onDismissRequest = { conflict = null }, title = { Text(tr("External calendar change", "שינוי בלוח שנה חיצוני")) },
        text = { Column {
            Text("${link.occurrenceDate}: ${link.reason}")
            TextButton(onClick = {
                model.act {
                    withContext(Dispatchers.IO) { model.app.calendars.restore(link) }
                    model.repository.update { it.copy(exports = it.exports.filterNot { e -> e.id == link.id }) }
                    conflict = null
                }
            }) { Text(tr("Restore occurrence from app", "שחזור המופע מהאפליקציה")) }
            TextButton(onClick = { model.act {
                model.repository.update { it.copy(exports = it.exports.map { e -> if (e.id == link.id) e.copy(state = "DETACHED", reason = "Keep external change") else e }) }; conflict = null
            } }) { Text(tr("Keep external change / suppress export", "שמירת השינוי החיצוני / ביטול הייצוא למופע")) }
            TextButton(onClick = { model.act {
                model.repository.update { it.copy(events = it.events.map { e -> if (e.id == link.eventId) e.copy(exportCalendarId = null) else e },
                    exports = it.exports.filterNot { e -> e.eventId == link.eventId }) }; conflict = null
            } }) { Text(tr("Stop exporting this series; keep calendar records", "הפסקת ייצוא הסדרה ושמירת רשומות לוח השנה")) }
        } }, confirmButton = { TextButton(onClick = { conflict = null }) { Text(tr("Close", "סגירה")) } }) }
}

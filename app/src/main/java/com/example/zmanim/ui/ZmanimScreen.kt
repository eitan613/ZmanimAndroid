package com.example.zmanim.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.example.zmanim.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class TimelineRow(val key: String, val date: LocalDate, val zman: CalculatedZman? = null)
internal fun timelineRows(days: List<DayZmanim>, zone: ZoneId): List<TimelineRow> {
    val values = days.flatMap { day -> day.values.filter { it.value != CalculationValue.NotApplicable }.map { z ->
        TimelineRow("${day.date}:${z.id}", (z.value as? CalculationValue.Time)?.instant?.atZone(zone)?.toLocalDate() ?: day.date, z)
    } }
    return (days.map { it.date } + values.map { it.date }).distinct().sorted().flatMap { date ->
        listOf(TimelineRow("day:$date", date)) + values.filter { it.date == date }.sortedWith(compareBy(
            { (it.zman?.value as? CalculationValue.Time)?.instant ?: Instant.MAX }, { it.key }))
    }
}

@Composable fun ZmanimScreen(s: ScreenData, model: AppViewModel, onProfiles: () -> Unit, onMethods: () -> Unit,
    onLocations: () -> Unit, onCustom: () -> Unit, onGoTo: () -> Unit, onSettings: () -> Unit,
    onHelp: () -> Unit = {}, onReminders: () -> Unit = {}, onProfileSettings: () -> Unit = {}) {
    val place = s.data.place(); val profile = s.data.profile(); val settings = s.data.settings
    val context = LocalContext.current; val locale = LocalProductLocale.current
    val state = rememberLazyListState()
    var reminderMethod by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<CalculatedZman?>(null) }
    var jump by rememberSaveable { mutableIntStateOf(1) }
    var handledJump by remember { mutableIntStateOf(0) }
    var positionedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val zone = place?.zone() ?: ZoneId.systemDefault()
    val rows = remember(s.timeline, zone) { timelineRows(s.timeline, zone) }
    val next = rows.firstOrNull { (it.zman?.value as? CalculationValue.Time)?.instant?.let { time -> time >= s.now } == true }
    LaunchedEffect(jump, s.date, rows) {
        if ((jump != handledJump || positionedDate != s.date.toString()) && rows.any { it.key == "day:${s.date}" }) {
            val today = s.date == LocalDate.now(zone)
            val target = (if (today) rows.indexOfFirst { it.key == next?.key } else -1).takeIf { it >= 0 }
                ?: rows.indexOfFirst { it.key == "day:${s.date}" }.coerceAtLeast(0)
            state.scrollToItem(target)
            withFrameNanos { }
            val info = state.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == target }
            val offset = if (today) -((info.viewportEndOffset - info.viewportStartOffset - (item?.size ?: 0)) / 2).coerceAtLeast(0) else 0
            state.scrollToItem(target, offset)
            handledJump = jump
            positionedDate = s.date.toString()
        }
    }
    LaunchedEffect(state, s.timeline.firstOrNull()?.date, s.timeline.lastOrNull()?.date, rows.size) {
        snapshotFlow { Triple(state.firstVisibleItemIndex, state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, state.isScrollInProgress) }
            .distinctUntilChanged().collect { (first, last, scrolling) ->
                if (rows.isNotEmpty()) {
                    if (scrolling && first <= 1) s.timeline.firstOrNull()?.date?.let { model.extendTimeline(true, it) }
                    else if (last >= rows.lastIndex - 2 && (scrolling || !state.canScrollForward)) s.timeline.lastOrNull()?.date?.let { model.extendTimeline(false, it) }
                }
            }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        AppHeader(tr("Zmanim", "זמנים"), "${profile?.name ?: tr("No profile", "ללא פרופיל")} · ${place?.name ?: tr("Choose a location", "בחירת מקום")}", listOf(
            tr("Profiles", "פרופילים") to onProfiles, tr("Active profile settings", "הגדרות הפרופיל הפעיל") to onProfileSettings,
            tr("Active profile reminders", "תזכורות הפרופיל הפעיל") to onReminders, tr("Help", "עזרה") to onHelp, tr("Choose zmanim", "בחירת זמנים") to onMethods,
            tr("Custom zmanim", "זמנים אישיים") to onCustom, tr("Location", "מיקום") to onLocations,
            tr("Back to now", "חזרה לעכשיו") to { model.today(); jump++ }, tr("Go to date", "מעבר לתאריך") to onGoTo,
            tr("Settings", "הגדרות") to onSettings))
        if (place == null) Notice(tr("Choose a location to calculate zmanim.", "יש לבחור מקום לחישוב זמנים."), action = {
            TextButton(onClick = onLocations) { Text(tr("Choose location", "בחירת מקום")) }
        })
        if (place?.isStale(s.now) == true) Notice(tr("Your device location is over 24 hours old. Refresh it in Location.", "מיקום המכשיר ישן מ־24 שעות. יש לרענן אותו במקומות."))
        if (profile == null || profile.selected.isEmpty()) Notice(tr("Choose which zmanim you want to show. No calculation methods are selected for you.", "יש לבחור אילו זמנים להציג. לא נבחרו שיטות חישוב מראש."), action = {
            TextButton(onClick = onMethods) { Text(tr("Choose zmanim", "בחירת זמנים")) }
        })
        else LazyColumn(state = state, modifier = Modifier.weight(1f).testTag("zmanimTimeline"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it.key }, contentType = { if (it.zman == null) "heading" else "zman" }) { row ->
                val z = row.zman
                if (z == null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().testTag("dayHeader:${row.date}").padding(top = 12.dp, bottom = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(CalendarEngine().dateLabel(row.date, settings.labels == LabelLanguage.HEBREW), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth(), textAlign = if (settings.labels == LabelLanguage.HEBREW) TextAlign.Right else TextAlign.Start)
                            Text(row.date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", locale)), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    val time = (z.value as? CalculationValue.Time)?.instant
                    val isNext = row.key == next?.key
                    if (isNext) Text(tr("Next zman", "הזמן הבא"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Surface(onClick = { detail = z }, color = if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().then(if (isNext) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier).testTag(if (isNext) "nextZman" else "zman:${row.key}")) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(methodLabel(z.id, profile, settings.labels == LabelLanguage.HEBREW), Modifier.fillMaxWidth(),
                                    textAlign = if (settings.labels == LabelLanguage.HEBREW) TextAlign.Right else TextAlign.Start,
                                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                                    color = if (time != null && time < s.now) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                if (settings.showCountdown && time != null && time >= s.now) Text(TimeFormatting.countdown(s.now, time), style = MaterialTheme.typography.labelMedium)
                                if (time != null && time < s.now) Text(tr("Passed", "עבר"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (z.value is CalculationValue.Unavailable) Text(localizeMessage(z.value.reason, LocalHebrew.current), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (MethodCatalog.byId[z.id]?.isDuration != true) IconButton(onClick = { reminderMethod = z.id }) { Icon(Icons.Default.Notifications, tr("Reminders", "תזכורות")) }
                            if (time != null) Text("\u2066${TimeFormatting.format(time, zone, settings, DateFormat.is24HourFormat(context), locale)}\u2069", style = MaterialTheme.typography.titleMedium)
                            if (z.value is CalculationValue.Length) Text(TimeFormatting.duration(z.value.duration), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
    reminderMethod?.let { id -> ProfileRemindersDialog(s, model, id) { reminderMethod = null } }
    detail?.let { z -> ZmanDetails(z, profile, settings, { detail = null }, onProfileSettings, onLocations) }
}

@Composable private fun ZmanDetails(z: CalculatedZman, profile: Profile?, settings: AppSettings, onDismiss: () -> Unit, onSetup: () -> Unit, onLocation: () -> Unit) {
    var technical by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(methodLabel(z.id, profile, settings.labels == LabelLanguage.HEBREW)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when(val value = z.value) {
                is CalculationValue.Time -> TimeFormatting.format(value.instant, z.details.place, settings, DateFormat.is24HourFormat(context), LocalProductLocale.current)
                is CalculationValue.Length -> TimeFormatting.duration(value.duration)
                is CalculationValue.Unavailable -> localizeMessage(value.reason, LocalHebrew.current)
                CalculationValue.NotApplicable -> tr("Does not apply on this date.", "לא חל בתאריך זה.")
            }, style = MaterialTheme.typography.titleLarge)
            Text(methodExplanation(z.id, profile))
            TextButton(onClick = { onDismiss(); onSetup() }) { Text(tr("Profile settings and reminders", "הגדרות פרופיל ותזכורות")) }
            Text("${z.details.date} · ${z.details.place.name}")
            TextButton(onClick = { onDismiss(); onLocation() }) { Text(tr("Location details / height", "פרטי מקום / גובה")) }
            if (!settings.showSeconds) Text(tr("+ means the displayed minute is rounded later; − means earlier. Reminders use the exact time.", "+ מציין עיגול הדקה מאוחר יותר; − מציין מוקדם יותר. התזכורות משתמשות בזמן המדויק."), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { technical = !technical }) { Text(tr("Calculation details", "פרטי החישוב")) }
            if (technical) {
                Text(z.details.formula.replace("profile elevation policy", if (z.details.options.useElevation) "using location elevation" else "at sea level"))
                Text("${z.details.place.zoneId} · ${z.details.effectiveElevation}")
                Text("\u2066${z.details.place.latitude}, ${z.details.place.longitude}\u2069")
                val method = MethodCatalog.byId[z.id]
                method?.let { Text(tr("Original name: ", "שם מקורי: ") + it.label); Text("KosherJava 3.0.0 · ${it.sourceMethod}") }
                if (method?.supportsHalfDays == true) Text(if (z.details.options.astronomicalHalfDays)
                    tr("Temporal hours are calculated separately for the morning and afternoon.", "שעות זמניות מחושבות בנפרד לבוקר ולאחר הצהריים.")
                    else tr("A temporal hour is one twelfth of the selected day boundaries.", "שעה זמנית היא חלק אחד משנים עשר של היום לפי הגבולות שנבחרו."))
            }
            if (z.value is CalculationValue.Unavailable) TextButton(onClick = { onDismiss(); onSetup() }) { Text(tr("Edit profile settings", "עריכת הגדרות הפרופיל")) }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "סגירה")) } })
}

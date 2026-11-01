@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.zmanim.ui

import android.text.format.DateFormat
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.zmanim.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

@Composable fun CalendarScreen(s: ScreenData, model: AppViewModel, onNewEvent: () -> Unit, onZmanim: () -> Unit,
    onEvent: (Occurrence) -> Unit, onGoTo: () -> Unit) {
    val settings = s.data.settings
    var search by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val locale = if (LocalHebrew.current) Locale.forLanguageTag("he") else Locale.ENGLISH
    LazyColumn(Modifier.testTag("calendarList").fillMaxSize().widthIn(max = 840.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { PageTitle(tr("Calendar", "לוח שנה"), CalendarEngine().dateLabel(s.date, settings.labels == LabelLanguage.HEBREW)) }
                Box {
                    TextButton(onClick = { menu = true }) { Text(tr("Menu ▾", "תפריט ▾")) }
                    DropdownMenu(menu, { menu = false }) {
                        listOf(tr("Today", "היום") to { model.today() }, tr("Go to date", "מעבר לתאריך") to onGoTo,
                            tr("New event", "אירוע חדש") to onNewEvent, tr("Search events", "חיפוש אירועים") to { searching = !searching }).forEach { (label, action) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; action() })
                        }
                    }
                }
            }
        }
        item { DateNavigation(s, model, onGoTo) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarView.entries.forEach { view -> FilterChip(selected = settings.calendarView == view, onClick = { model.settings { it.copy(calendarView = view) } }, label = { Text(enumName(view)) }) }
        } }
        if (settings.inIsrael == null) item { Notice(tr("Choose Israel or diaspora in Settings to show holidays and parsha.", "יש לבחור ישראל או חו״ל בהגדרות כדי להציג חגים ופרשות.")) }
        s.error?.let { error -> item { Notice(error, true) } }
        if (searching) item {
            Field(tr("Search event titles and descriptions", "חיפוש כותרות ותיאורים"), search, { search = it })
            if (search.isNotEmpty()) TextButton(onClick = { search = "" }) { Text(tr("Clear search", "ניקוי החיפוש")) }
        }
        if (!searching) {
            if (settings.calendarView == CalendarView.MONTH) item {
                MonthGrid(s, model, onNewEvent, onZmanim, locale)
            }
            if (settings.calendarView != CalendarView.MONTH) item {
                val first = if (settings.calendarView == CalendarView.DAY) s.date else {
                    val day = if (settings.mondayFirst) 1 else 7
                    s.date.minusDays(((s.date.dayOfWeek.value - day + 7) % 7).toLong())
                }
                val days = if (settings.calendarView == CalendarView.DAY) listOf(first) else (0L..6L).map(first::plusDays)
                Schedule(days, s, onEvent)
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (searching) tr("Search results", "תוצאות החיפוש") else s.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onNewEvent) { Text(tr("+ Event", "+ אירוע")) }
        } }
        val events = if (searching) s.data.events.filter { it.title.contains(search, true) || it.description.contains(search, true) }
            .map { OccurrenceEngine().occurrence(it, LocalDate.parse(it.anchorDate), s.data) }
        else s.occurrences.filter { occurrence ->
            if (occurrence.allDay || occurrence.start == null) occurrence.date == s.date
            else {
                val zone = runCatching { ZoneId.of(occurrence.zoneId) }.getOrDefault(ZoneId.systemDefault())
                val from = s.date.atStartOfDay(zone).toInstant(); val end = s.date.plusDays(1).atStartOfDay(zone).toInstant()
                occurrence.start < end && (occurrence.end ?: occurrence.start) > from
            }
        }
        if (events.isEmpty()) item { Text(if (searching) tr("No matching events.", "אין אירועים תואמים.") else tr("No events for this day.", "אין אירועים ביום זה."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(events, key = { it.key }) { o -> OccurrenceCard(o, settings, { onEvent(o) }) }
        if (!searching) item { OutlinedButton(onClick = onZmanim, modifier = Modifier.fillMaxWidth()) { Text(tr("Zmanim for this day", "זמני היום הנבחר")) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable fun DateNavigation(s: ScreenData, model: AppViewModel, onGoTo: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { model.shift(-1) }) { Text(tr("Previous", "הקודם")) }
        TextButton(onClick = { model.today() }) { Text(tr("Today", "היום")) }
        TextButton(onClick = onGoTo) { Text(s.date.toString()) }
        TextButton(onClick = { model.shift(1) }) { Text(tr("Next", "הבא")) }
    }
}

@Composable private fun MonthGrid(s: ScreenData, model: AppViewModel, onNewEvent: () -> Unit, onZmanim: () -> Unit, locale: Locale) {
    val settings = s.data.settings
    val engine = remember { CalendarEngine() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.month.primary.label, style = MaterialTheme.typography.headlineMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            s.month.secondary.forEach { month -> Text(month.label,
                style = if (month.selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
                color = if (month.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        var x by remember { mutableFloatStateOf(0f) }; var y by remember { mutableFloatStateOf(0f) }
        val currentShift by rememberUpdatedState<(Int, Boolean) -> Unit> { amount, years -> model.shift(amount, years) }
        val swap = settings.swapScrollAxes
        CompositionLocalProvider(LocalLayoutDirection provides if (settings.reverseGrid) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(Modifier.pointerInput(swap) {
                detectDragGestures(onDragStart = { x = 0f; y = 0f }, onDragEnd = {
                    if (max(abs(x), abs(y)) > 80f) {
                        val horizontal = abs(x) > abs(y)
                        val distance = if (horizontal) x else y
                        currentShift(if (distance < 0) 1 else -1, horizontal == swap)
                    }
                }) { change, drag -> change.consume(); x += drag.x; y += drag.y }
            }) {
                val first = if (settings.mondayFirst) 1 else 7
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { offset -> Text(DayOfWeek.of((first - 1 + offset) % 7 + 1).getDisplayName(java.time.format.TextStyle.NARROW, locale),
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
                }
                s.month.days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            val selected = day.date == s.date
                            val inMonth = day.date >= s.month.primary.start && day.date < s.month.primary.endExclusive
                            val count = s.occurrences.count { it.date == day.date }
                            val alpha = if (inMonth) 1f else 0.48f
                            val countLabel = tr("$count events", "$count אירועים")
                            val bg = when {
                                selected -> MaterialTheme.colorScheme.primaryContainer
                                settings.colorSpecialDays && day.special -> androidx.compose.ui.graphics.Color((if (day.date.dayOfWeek == DayOfWeek.SATURDAY) settings.shabbatTint else settings.holidayTint).toInt()).copy(alpha = 0.20f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                            Column(Modifier.weight(1f).heightIn(min = 86.dp).background(bg, RoundedCornerShape(8.dp))
                                .combinedClickable(onClick = { model.select(day.date) }, onDoubleClick = {
                                    model.select(day.date)
                                    when (settings.doubleTap) {
                                        DoubleTapAction.ZMANIM -> onZmanim(); DoubleTapAction.NEW_EVENT -> onNewEvent()
                                        DoubleTapAction.DAY -> model.settings { it.copy(calendarView = CalendarView.DAY) }
                                    }
                                }).semantics { contentDescription = "${day.date}, ${day.hebrewDate}, ${day.holiday}, $countLabel"; this.selected = selected }
                                .padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val primary = if (settings.primaryCalendar == CalendarSystem.HEBREW) {
                                    if (settings.labels == LabelLanguage.HEBREW) engine.formatter(true).formatHebrewNumber(day.hebrewDay) else day.hebrewDay.toString()
                                } else day.date.dayOfMonth.toString()
                                Text(primary, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                Text(if (settings.primaryCalendar == CalendarSystem.HEBREW) day.date.dayOfMonth.toString() else day.hebrewDay.toString(), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
                                listOf(day.holiday, day.parsha, day.specialParsha, day.omer).filter { it.isNotEmpty() }.forEach { label ->
                                    Text(label, maxLines = 2, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                                }
                                if (count > 0) Text("• $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        val chosen = engine.day(s.date, settings)
        listOf(chosen.holiday, chosen.parsha, chosen.specialParsha, chosen.omer).filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { model.shift(-1, true) }) { Text(tr("Previous year", "השנה הקודמת")) }
            TextButton(onClick = { model.shift(1, true) }) { Text(tr("Next year", "השנה הבאה")) }
        }
    }
}

@Composable fun OccurrenceCard(o: Occurrence, settings: AppSettings, onClick: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalProductLocale.current
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(o.title, style = MaterialTheme.typography.titleMedium)
            Text(when {
                o.unavailableReason != null -> tr("Unavailable: ", "לא זמין: ") + localizeMessage(o.unavailableReason, LocalHebrew.current)
                o.allDay -> tr("All day", "כל היום")
                o.start != null -> TimeFormatting.format(o.start, ZoneId.of(o.zoneId), settings, DateFormat.is24HourFormat(context), locale) +
                    (o.end?.let { " – " + TimeFormatting.format(it, ZoneId.of(o.zoneId), settings, DateFormat.is24HourFormat(context), locale) } ?: "") + " · ${o.zoneId}"
                else -> tr("Unavailable", "לא זמין")
            }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private data class Positioned(val event: Occurrence, val start: Float, val length: Float, val column: Int, var columns: Int = 1)
private fun positions(events: List<Occurrence>, start: Instant, end: Instant): List<Positioned> {
    val sorted = events.filter { !it.allDay && it.start != null && it.end != null && it.start < end && it.end > start }.sortedBy { it.start }
    val result = mutableListOf<Positioned>(); val active = mutableListOf<Positioned>(); val cluster = mutableListOf<Positioned>()
    var maxColumns = 1
    fun finishCluster() { cluster.forEach { it.columns = maxColumns }; cluster.clear(); maxColumns = 1 }
    for (o in sorted) {
        val from = Duration.between(start, maxOf(o.start!!, start)).toMinutes().toFloat()
        val to = Duration.between(start, minOf(o.end!!, end)).toMinutes().toFloat()
        active.removeAll { it.start + it.length <= from }
        if (active.isEmpty()) finishCluster()
        val column = generateSequence(0) { it + 1 }.first { c -> active.none { it.column == c } }
        val p = Positioned(o, from, (to - from).coerceAtLeast(15f), column)
        active += p; cluster += p; result += p; maxColumns = max(maxColumns, column + 1)
    }
    finishCluster(); return result
}

@Composable private fun Schedule(days: List<LocalDate>, s: ScreenData, onEvent: (Occurrence) -> Unit) {
    val zone = s.data.place()?.zone() ?: ZoneId.systemDefault()
    val is24 = s.data.settings.use24Hour ?: DateFormat.is24HourFormat(LocalContext.current)
    val width = if (days.size == 1) 320.dp else 180.dp
    Column(Modifier.testTag("hourSchedule")) {
        val allDay = s.occurrences.filter { it.allDay && it.date in days }
        allDay.forEach { o -> TextButton(onClick = { onEvent(o) }) { Text("${o.date}: ${o.title}") } }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            days.forEach { date ->
                val start = date.atStartOfDay(zone).toInstant(); val end = date.plusDays(1).atStartOfDay(zone).toInstant()
                val hours = Duration.between(start, end).toHours().toInt()
                Column(Modifier.width(width).padding(end = 8.dp)) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.titleSmall)
                    BoxWithConstraints(Modifier.fillMaxWidth().height((hours * 64).dp)) {
                        for (hour in 0..hours) {
                            Row(Modifier.fillMaxWidth().offset(y = (hour * 64).dp)) {
                                Text(start.plusSeconds(hour * 3600L).atZone(zone).format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h a")),
                                    modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall)
                                HorizontalDivider(Modifier.padding(top = 8.dp))
                            }
                        }
                        val usable = maxWidth - 48.dp
                        positions(s.occurrences, start, end).forEach { p ->
                            Surface(onClick = { onEvent(p.event) }, modifier = Modifier.offset(x = 48.dp + usable / p.columns * p.column,
                                y = (p.start / 60f * 64).dp).width(usable / p.columns).heightIn(min = 32.dp).height((p.length / 60f * 64).dp),
                                color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                                Text(p.event.title, Modifier.padding(4.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

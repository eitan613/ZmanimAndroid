@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.zmanim.ui

import android.text.format.DateFormat
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.*
import com.example.zmanim.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

@Composable fun CalendarScreen(s: ScreenData, model: AppViewModel, onNewEvent: (EventStart) -> Unit, onZmanim: () -> Unit,
    onEvent: (Occurrence) -> Unit, onGoTo: () -> Unit, onSettings: () -> Unit, onLocations: () -> Unit = {}, onHelp: () -> Unit = {})  {
    val settings = s.data.settings
    var search by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val locale = LocalProductLocale.current
    val listState = key(settings.calendarView, settings.primaryCalendar) { rememberLazyListState() }
    BoxWithConstraints(Modifier.fillMaxSize().widthIn(max = 840.dp)) {
        val cellHeight = ((maxHeight - 130.dp) / s.month.days.chunked(7).size).coerceIn((104 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp, (180 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            AppHeader(s.month.primary.label, s.month.secondary.joinToString(" / ") { it.label } + " · " + (s.data.place()?.name ?: tr("Set app location", "הגדרת מיקום האפליקציה")), listOf(
                tr("Today", "היום") to { model.today() }, tr("Go to date", "מעבר לתאריך") to onGoTo,
                tr("New event", "אירוע חדש") to { onNewEvent(EventStart(s.date)) },
                tr("Search events", "חיפוש אירועים") to { searching = !searching },
                tr("App location", "מיקום האפליקציה") to onLocations, tr("Help", "עזרה") to onHelp,
                tr("Settings", "הגדרות") to onSettings))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (settings.calendarView == CalendarView.MONTH || searching) 12.dp else 0.dp)) {
                s.error?.let { error -> item { Notice(error, true) } }
                if (searching) item {
                    Field(tr("Search event titles and descriptions", "חיפוש כותרות ותיאורים"), search, { search = it })
                    if (search.isNotEmpty()) TextButton(onClick = { search = "" }) { Text(tr("Clear search", "ניקוי החיפוש")) }
                }
                if (!searching) {
                    if (settings.calendarView == CalendarView.MONTH) item {
                        MonthGrid(s, model, { date -> onNewEvent(EventStart(date)) }, onZmanim, onEvent, locale, cellHeight)
                    }
                    if (settings.calendarView != CalendarView.MONTH) {
                        val first = if (settings.calendarView == CalendarView.DAY) s.date else {
                            val day = if (settings.mondayFirst) 1 else 7
                            s.date.minusDays(((s.date.dayOfWeek.value - day + 7) % 7).toLong())
                        }
                        val days = if (settings.calendarView == CalendarView.DAY) listOf(first) else (0L..6L).map(first::plusDays)
                        scheduleItems(days, s, model, onEvent, onNewEvent, locale)
                    }
                }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (searching) tr("Search results", "תוצאות החיפוש") else s.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onNewEvent(EventStart(s.date)) }) { Text(tr("+ Event", "+ אירוע")) }
                } }
                val events = if (searching) s.data.events.filter { it.title.contains(search, true) || it.description.contains(search, true) }
                    .map { OccurrenceEngine().occurrence(it, LocalDate.parse(it.anchorDate), s.data) }
                else s.occurrences.filter { it.overlapsDay(s.date, s.data.place()?.zone() ?: ZoneId.systemDefault()) }
                if (events.isEmpty()) item { Text(if (searching) tr("No matching events.", "אין אירועים תואמים.") else tr("No events for this day.", "אין אירועים ביום זה."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(events, key = { it.key }) { o -> OccurrenceCard(o, settings, { onEvent(o) }) }
                if (!searching) item { OutlinedButton(onClick = onZmanim, modifier = Modifier.fillMaxWidth()) { Text(tr("Zmanim for this day", "זמני היום הנבחר")) } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable private fun MonthGrid(s: ScreenData, model: AppViewModel, onNewEvent: (LocalDate) -> Unit, onZmanim: () -> Unit, onEvent: (Occurrence) -> Unit, locale: Locale, cellHeight: Dp) {
    val settings = s.data.settings
    val engine = remember { CalendarEngine() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var x by remember { mutableFloatStateOf(0f) }
        var y by remember { mutableFloatStateOf(0f) }
        val currentShift by rememberUpdatedState<(Int, Boolean) -> Unit> { amount, years -> model.shift(amount, years) }
        val swap = settings.swapScrollAxes
        CompositionLocalProvider(LocalLayoutDirection provides if (settings.calendarRtl()) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Column(Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Previous period") { model.shift(-1); true },
                    CustomAccessibilityAction("Next period") { model.shift(1); true },
                    CustomAccessibilityAction("Previous year") { model.shift(-1, true); true },
                    CustomAccessibilityAction("Next year") { model.shift(1, true); true })
            }.pointerInput(swap, settings.calendarRtl()) {
                detectDragGestures(onDragStart = { x = 0f; y = 0f }, onDragEnd = {
                    if (max(abs(x), abs(y)) > 80f) {
                        val horizontal = abs(x) > abs(y)
                        val forward = if (horizontal) (x < 0) != settings.calendarRtl() else y < 0
                        currentShift(if (forward) 1 else -1, if (horizontal) swap else !swap)
                    }
                }) { change, drag -> change.consume(); x += drag.x; y += drag.y }
            }) {
                val first = if (settings.mondayFirst) 1 else 7
                Row(Modifier.fillMaxWidth()) {
                    (0..6).forEach { offset -> Text(weekdayLabel(DayOfWeek.of((first - 1 + offset) % 7 + 1), settings.labels == LabelLanguage.HEBREW, locale),
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
                }
                s.month.days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            val selected = day.date == s.date
                            val inMonth = day.date >= s.month.primary.start && day.date < s.month.primary.endExclusive
                            val dayEvents = s.occurrences.filter { it.overlapsDay(day.date, s.data.place()?.zone() ?: ZoneId.systemDefault()) }
                            val count = dayEvents.size
                            val alpha = if (inMonth) 1f else 0.48f
                            val countLabel = tr("$count events", "$count אירועים")
                            val base = MaterialTheme.colorScheme.surface
                            val category = day.category ?: if (day.candleLighting) "candles" else null
                            val tint = category?.let { Color(settings.specialDayColor(it).toInt()) }
                            val bg = if (settings.colorSpecialDays && tint != null && !effectiveDark(settings)) tint else base
                            val onCell = (if (bg.luminance() < 0.3f) Color.White else Color(0xFF172C3D)).copy(alpha = alpha)
                            val today = day.date == LocalDate.now(s.data.place()?.zone() ?: ZoneId.systemDefault())
                            Column(Modifier.weight(1f).height(cellHeight).background(bg)
                                .border(if (selected || today) 2.dp else 0.5.dp,
                                    if (selected || today) { if (bg.luminance() < 0.3f) Color.White else MaterialTheme.colorScheme.primary } else MaterialTheme.colorScheme.outlineVariant)
                                .combinedClickable(onClick = { model.select(day.date) }, onDoubleClick = {
                                    model.select(day.date)
                                    when (settings.doubleTap) {
                                        DoubleTapAction.ZMANIM -> onZmanim(); DoubleTapAction.NEW_EVENT -> onNewEvent(day.date)
                                        DoubleTapAction.DAY -> model.settings { it.copy(calendarView = CalendarView.DAY) }
                                    }
                                }).semantics { contentDescription = "${day.date}, ${day.hebrewDate}, ${day.holiday}, $countLabel"; this.selected = selected }
                                .padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val primary = if (settings.primaryCalendar == CalendarSystem.HEBREW) {
                                    engine.formatter(true).formatHebrewNumber(day.hebrewDay).replace("׳", "").replace("״", "")
                                } else day.date.dayOfMonth.toString()
                                CalendarCellDates(day.date.toString(), if (today) "$primary •" else primary,
                                    if (settings.primaryCalendar == CalendarSystem.HEBREW) day.date.dayOfMonth.toString()
                                    else engine.formatter(true).formatHebrewNumber(day.hebrewDay).replace("׳", "").replace("״", ""),
                                    onCell.copy(alpha = alpha), selected)
                                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    listOf(day.holiday, day.parsha, day.specialParsha, day.omer).filter { it.isNotEmpty() }.flatMap { it.split(" · ") }.forEach { label ->
                                        CalendarCellLabel(label, onCell.copy(alpha = alpha * 0.9f))
                                    }
                                    if (count > 0) {
                                        if (settings.showEventBasics) {
                                            val event = dayEvents.first()
                                            val clock = event.start?.let { TimeFormatting.format(it, ZoneId.of(event.zoneId), settings.copy(showSeconds = false), DateFormat.is24HourFormat(LocalContext.current), locale) }
                                            val openEvent = { model.select(day.date); onEvent(event) }
                                            Text((if (event.allDay) "" else clock?.let { "\u2066$it\u2069 " }.orEmpty()) + event.title,
                                                modifier = Modifier.fillMaxWidth().combinedClickable(onClick = openEvent, onDoubleClick = openEvent),
                                                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = onCell)
                                            if (count > 1) Text(tr("+${count - 1} more", "+${count - 1} נוספים"), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = onCell)
                                        } else Text("• $count", style = MaterialTheme.typography.labelSmall, color = onCell)
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }
        val chosen = engine.day(s.date, settings)
        listOf(chosen.holiday, chosen.parsha, chosen.specialParsha, chosen.omer).filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }

    }
}

/** Keep both date numbers intact when accessibility text no longer fits side by side. */
@Composable private fun CalendarCellDates(date: String, primary: String, secondary: String, color: Color, selected: Boolean) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val primaryStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    val secondaryStyle = MaterialTheme.typography.labelSmall
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val primaryWidth = measurer.measure(primary, primaryStyle).size.width.coerceAtLeast(1)
        val secondaryWidth = measurer.measure(secondary, secondaryStyle).size.width
        val fittedPrimary = primaryStyle.copy(fontSize = primaryStyle.fontSize * (constraints.maxWidth.toFloat() / primaryWidth).coerceAtMost(1f))
        if (primaryWidth + secondaryWidth > constraints.maxWidth) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text(primary, style = fittedPrimary, color = color, maxLines = 1, softWrap = false)
                Text(secondary, modifier = Modifier.align(Alignment.Start), style = secondaryStyle, color = color.copy(alpha = color.alpha * 0.75f), maxLines = 1, softWrap = false)
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(primary, style = fittedPrimary, color = color, maxLines = 1, softWrap = false)
            Text(secondary, style = secondaryStyle, color = color.copy(alpha = color.alpha * 0.75f), maxLines = 1, softWrap = false)
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

private fun androidx.compose.foundation.lazy.LazyListScope.scheduleItems(
    days: List<LocalDate>, s: ScreenData, model: AppViewModel, onEvent: (Occurrence) -> Unit,
    onNewEvent: (EventStart) -> Unit, locale: Locale,
) {
    val zone = s.data.place()?.zone() ?: ZoneId.systemDefault()
    val maxHours = days.maxOf { Duration.between(it.atStartOfDay(zone), it.plusDays(1).atStartOfDay(zone)).toHours().toInt() }
    stickyHeader(key = "scheduleHeader") {
        CompositionLocalProvider(LocalLayoutDirection provides if (s.data.settings.calendarRtl()) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Spacer(Modifier.width(38.dp))
                days.forEach { date ->
                    val jd = com.kosherjava.zmanim.hebrewcalendar.JewishDate(date)
                    Column(Modifier.weight(1f).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.format(DateTimeFormatter.ofPattern("EE", if (s.data.settings.labels == LabelLanguage.HEBREW) Locale.forLanguageTag("he") else locale)), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        Text("${date.dayOfMonth} · ${CalendarEngine().formatter(true).formatHebrewNumber(jd.jewishDayOfMonth)}", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    item(key = "allDay") {
        s.occurrences.filter { it.allDay && days.any { day -> it.overlapsDay(day, zone) } }.forEach { o ->
            TextButton(onClick = { onEvent(o) }) { Text("${o.date}: ${o.title}") }
        }
    }
    items(maxHours, key = { "hour:$it" }) { hour ->
        val is24 = s.data.settings.use24Hour ?: DateFormat.is24HourFormat(LocalContext.current)
        CompositionLocalProvider(LocalLayoutDirection provides if (s.data.settings.calendarRtl()) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().height(64.dp).semantics {
                customActions = listOf(CustomAccessibilityAction("Previous period") { model.shift(-1); true }, CustomAccessibilityAction("Next period") { model.shift(1); true })
            }.pointerInput(s.data.settings.calendarRtl()) {
                var dx = 0f
                detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                    if (abs(dx) > 80f) model.shift(if ((dx < 0) != s.data.settings.calendarRtl()) 1 else -1)
                }) { change, amount -> change.consume(); dx += amount }
            }) {
                Text("\u2066" + (if (days.size == 1) days.first().atStartOfDay(zone).toInstant().plusSeconds(hour * 3600L).atZone(zone).toLocalTime() else LocalTime.MIDNIGHT.plusHours(hour.toLong())).format(DateTimeFormatter.ofPattern(if (is24) "HH" else "h a", locale)) + "\u2069",
                    Modifier.width(38.dp), style = MaterialTheme.typography.labelSmall)
                days.forEach { date ->
                    val start = date.atStartOfDay(zone).toInstant()
                    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
                    val slot = start.plusSeconds(hour * 3600L)
                    val enabled = slot < end
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        .semantics { contentDescription = "$date ${slot.atZone(zone).toLocalTime()}" }
                        .combinedClickable(enabled = enabled, onClick = { model.select(date) }, onDoubleClick = { onNewEvent(EventStart.at(slot, zone)) },
                            onLongClickLabel = "Create event", onLongClick = { onNewEvent(EventStart.at(slot, zone)) })) {
                        if (enabled && slot.atZone(zone).hour != hour % 24) Text(slot.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.labelSmall)
                        positions(s.occurrences, start, end).filter { it.start < (hour + 1) * 60 && it.start + it.length > hour * 60 }.forEach { p ->
                            val top = (p.start - hour * 60).coerceAtLeast(0f)
                            val length = (minOf(p.start + p.length, (hour + 1) * 60f) - maxOf(p.start, hour * 60f)).coerceAtLeast(1f)
                            Surface(onClick = { onEvent(p.event) }, modifier = Modifier.offset(x = maxWidth / p.columns * p.column, y = (top / 60f * 64).dp)
                                .width(maxWidth / p.columns).height((length / 60f * 64).dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(p.event.title, Modifier.padding(2.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Wrap at measured word boundaries, including Hebrew; never split a built-in label mid-word. */
@Composable private fun CalendarCellLabel(text: String, color: Color) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val compact = text.replace("ראש חודש ", "ר״ח ").replace("פרשת ", "")
        val singleLine = compact.startsWith("ר״ח ") || compact.startsWith("Rosh Chodesh ")
        val words = compact.split(Regex("\\s+"))
        val lines = remember(text, width, style, density.fontScale) {
            if (singleLine) listOf(compact) else buildList {
                var line = ""
                for (word in words) {
                    val candidate = if (line.isBlank()) word else "$line $word"
                    if (line.isNotBlank() && measurer.measure(candidate, style).size.width > width) { add(line); line = word }
                    else line = candidate
                }
                if (line.isNotBlank()) add(line)
            }
        }
        Column {
            lines.forEach { line ->
                val measured = measurer.measure(line, style).size.width.coerceAtLeast(1)
                val scale = (width / measured).coerceAtMost(1f)
                Text(line, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, softWrap = false, maxLines = 1, style = style.copy(fontSize = style.fontSize * scale), color = color)
            }
        }
    }
}

@Composable internal fun effectiveDark(settings: AppSettings): Boolean = when (settings.appearance) {
    Appearance.DARK -> true
    Appearance.LIGHT -> false
    Appearance.SYSTEM -> isSystemInDarkTheme()
}

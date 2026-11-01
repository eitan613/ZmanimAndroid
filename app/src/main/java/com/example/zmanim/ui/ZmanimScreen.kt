package com.example.zmanim.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import com.example.zmanim.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable fun ZmanimScreen(s: ScreenData, model: AppViewModel, onProfiles: () -> Unit, onMethods: () -> Unit, onLocations: () -> Unit) {
    val place = s.data.place(); val profile = s.data.profile(); val settings = s.data.settings
    val context = LocalContext.current; val locale = LocalProductLocale.current
    val state = rememberLazyListState()
    var detail by remember { mutableStateOf<CalculatedZman?>(null) }
    var jumpToNow by rememberSaveable { mutableStateOf(s.date == LocalDate.now(place?.zone() ?: ZoneId.systemDefault())) }
    val timed = remember(s.timeline) { s.timeline.flatMap { d -> d.values.map { d.date to it } }.filter { it.second.value is CalculationValue.Time }
        .sortedBy { (it.second.value as CalculationValue.Time).instant } }
    val next = timed.firstOrNull { (it.second.value as CalculationValue.Time).instant >= s.now }
    val nowIndex = timed.indexOf(next).takeIf { it >= 0 } ?: timed.size
    LaunchedEffect(timed, jumpToNow) {
        if (jumpToNow && s.date == LocalDate.now(place?.zone() ?: ZoneId.systemDefault()) && timed.isNotEmpty()) { state.scrollToItem((nowIndex + 3).coerceAtMost(timed.size + 2)); jumpToNow = false }
    }
    LaunchedEffect(state, s.timeline.lastOrNull()?.date) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { index ->
            if (timed.isNotEmpty() && index >= state.layoutInfo.totalItemsCount - 2 && state.layoutInfo.totalItemsCount > 5 && state.isScrollInProgress) model.extendTimeline(false)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        PageTitle(tr("Zmanim", "זמנים"), "${profile?.name ?: tr("No profile", "ללא פרופיל")} · ${place?.name ?: tr("Choose a location", "בחירת מקום")}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onProfiles) { Text(tr("Profiles", "פרופילים")) }
            TextButton(onClick = onMethods) { Text(tr("Choose zmanim", "בחירת זמנים")) }
            TextButton(onClick = onLocations) { Text(tr("Location", "מיקום")) }
            TextButton(onClick = { model.today(); jumpToNow = true }) { Text(tr("Back to now", "חזרה לעכשיו")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { model.select(s.date.minusDays(1)) }) { Text(tr("Previous day", "היום הקודם")) }
            Text(s.date.toString(), Modifier.padding(top = 12.dp))
            TextButton(onClick = { model.select(s.date.plusDays(1)) }) { Text(tr("Next day", "היום הבא")) }
        }
        if (place == null) Notice(tr("Choose a saved location or follow your device location to calculate zmanim.", "יש לבחור מקום שמור או את מיקום המכשיר כדי לחשב זמנים."))
        else {
            Text("\u2066${place.latitude}, ${place.longitude} · ${place.zoneId}\u2069", style = MaterialTheme.typography.bodySmall)
            place.obtainedAt?.let { Text(tr("Location obtained: ", "המיקום התקבל: ") + Instant.ofEpochMilli(it).atZone(place.zone()).format(DateTimeFormatter.ofPattern("d MMM HH:mm")), style = MaterialTheme.typography.bodySmall) }
            place.accuracyMeters?.let { Text(tr("Location accuracy: ±${it.toInt()} m", "דיוק המיקום: ±${it.toInt()} מטר"), style = MaterialTheme.typography.bodySmall) }
            if (place.isStale(s.now)) Notice(tr("This device location needs refreshing: it is over 24 hours old or the clock changed. Refresh it or choose a saved place.", "יש לרענן את מיקום המכשיר: חלפו יותר מ־24 שעות או שהשעון השתנה. ניתן גם לבחור מקום שמור."))
        }
        if (profile == null || profile.selected.isEmpty()) Notice(tr("Choose which zmanim you want to show. No calculation methods are selected for you.", "יש לבחור אילו זמנים להציג. לא נבחרו שיטות חישוב מראש."))
        else LazyColumn(state = state, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "earlier") { TextButton(onClick = { model.extendTimeline(true) }) { Text(tr("Load earlier days", "טעינת ימים קודמים")) } }
            item(key = "duration-heading") {
                val durations = s.timeline.find { it.date == s.date }?.values?.filter { it.value is CalculationValue.Length }.orEmpty()
                durations.forEach { z -> TextButton(onClick = { detail = z }) { Text("${methodLabel(z.id, profile, settings.labels == LabelLanguage.HEBREW)}: ${TimeFormatting.duration((z.value as CalculationValue.Length).duration)}") } }
            }
            if (timed.isEmpty()) item(key = "no-timed-results") { Text(tr("No timed calculations are available for these days.", "אין חישובי זמן זמינים בימים אלו.")) }
            timed.forEachIndexed { index, (sourceDate, z) ->
                val instant = (z.value as CalculationValue.Time).instant
                val localDate = instant.atZone(place!!.zone()).toLocalDate()
                item(key = "$sourceDate:${z.id}") {
                    Column {
                        if (index == 0 || (timed[index - 1].second.value as CalculationValue.Time).instant.atZone(place.zone()).toLocalDate() != localDate)
                            Text(CalendarEngine().dateLabel(localDate, settings.labels == LabelLanguage.HEBREW) + " · $localDate", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
                        if (index == nowIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary)
                            Text(tr("Now", "עכשיו"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                        Surface(onClick = { detail = z }, color = if (z == next?.second) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(vertical = 14.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(methodLabel(z.id, profile, settings.labels == LabelLanguage.HEBREW), color = if (instant < s.now) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                    if (z == next?.second && settings.showCountdown) Text(TimeFormatting.countdown(s.now, instant), style = MaterialTheme.typography.bodySmall)
                                }
                                Text("\u2066" + TimeFormatting.format(instant, place, settings, DateFormat.is24HourFormat(context), locale) + "\u2069", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            val unavailable = s.timeline.flatMap { d -> d.values.filter { it.value is CalculationValue.Unavailable }.map { d.date to it } }
            items(unavailable, key = { "unavailable:${it.first}:${it.second.id}" }) { (date, z) ->
                TextButton(onClick = { detail = z }) { Text("$date · ${methodLabel(z.id, profile, settings.labels == LabelLanguage.HEBREW)} · " + tr("Unavailable", "לא זמין")) }
            }
            item(key = "later") { TextButton(onClick = { model.extendTimeline(false) }) { Text(tr("Load later days", "טעינת ימים נוספים")) } }
        }
    }
    detail?.let { z ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(methodLabel(z.id, profile, settings.labels == LabelLanguage.HEBREW)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(z.details.formula)
                Text("${z.details.date} · ${z.details.place.name}")
                Text("\u2066${z.details.place.latitude}, ${z.details.place.longitude}\u2069")
                Text("${z.details.place.zoneId} · ${z.details.effectiveElevation}")
                Text(tr("Astronomical midday: ", "חצות אסטרונומי: ") + z.details.options.astronomicalChatzos)
                Text(tr("Half-day temporal hours where supported: ", "שעות זמניות לפי חצאי יום בשיטות התומכות: ") + z.details.options.astronomicalHalfDays)
                z.details.options.candleLightingMinutes?.let { Text(tr("Candle-lighting offset: ", "הפרש הדלקת נרות: ") + "$it min") }
                z.details.options.ateretTorahMinutes?.let { Text(tr("Ateret Torah offset: ", "הפרש עטרת תורה: ") + "$it min") }
                Text(when (val value = z.value) {
                    is CalculationValue.Time -> value.instant.atZone(z.details.place.zone()).toString()
                    is CalculationValue.Length -> value.duration.toString()
                    is CalculationValue.Unavailable -> localizeMessage(value.reason, LocalHebrew.current)
                })
                Text(tr("Minute rounding changes only the display. Reminders use the exact instant.", "עיגול הדקות משפיע רק על התצוגה. תזכורות מבוססות על הזמן המדויק."), style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(onClick = { detail = null }) { Text(tr("Close", "סגירה")) } })
    }
}

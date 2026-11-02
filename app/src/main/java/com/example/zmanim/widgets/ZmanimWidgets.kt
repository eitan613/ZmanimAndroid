package com.example.zmanim.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.zmanim.ui.theme.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.launch
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.appwidget.lazy.LazyColumn as WidgetLazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.text.*
import com.example.zmanim.R
import com.example.zmanim.MainActivity
import com.example.zmanim.ZmanimApplication
import com.example.zmanim.domain.*
import java.time.*
import java.util.Locale

private data class WidgetLine(val title: String, val subtitle: String, val intent: android.content.Intent, val targetMillis: Long? = null)

open class CalendarWidget(private val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(when (kind) {
        WidgetKind.DATE -> setOf(androidx.compose.ui.unit.DpSize(120.dp, 64.dp), androidx.compose.ui.unit.DpSize(240.dp, 120.dp))
        WidgetKind.COMBINED -> setOf(androidx.compose.ui.unit.DpSize(240.dp, 180.dp), androidx.compose.ui.unit.DpSize(300.dp, 240.dp))
        else -> setOf(androidx.compose.ui.unit.DpSize(120.dp, 120.dp), androidx.compose.ui.unit.DpSize(240.dp, 180.dp), androidx.compose.ui.unit.DpSize(300.dp, 240.dp))
    })
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ZmanimApplication
        val data = app.awaitData()
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = data.widgets.find { it.id == widgetId.toString() } ?: WidgetConfig(widgetId.toString(), kind)
        val settings = data.settings
        val productLocale = settings.productLocale()
        val isHebrewUi = settings.language == Language.HEBREW || settings.language == Language.SYSTEM && context.resources.configuration.locales[0].language in setOf("he", "iw")
        fun text(en: String, he: String) = if (isHebrewUi) he else en
        val place = data.place(config.location)
        val now = Instant.now()
        val today = LocalDate.now(place?.zone() ?: ZoneId.systemDefault())
        val hebrew = settings.labels == LabelLanguage.HEBREW
        val openCalendar = Intent(context, MainActivity::class.java).putExtra("destination", "calendar")
        val openZmanim = Intent(context, MainActivity::class.java).putExtra("destination", "zmanim")
        val rows: List<WidgetLine> = when (kind) {
            WidgetKind.DATE -> {
                val (date, error) = if (config.calendar == CalendarSystem.HEBREW) CalendarEngine().currentHebrewDate(now, place, config.sunsetRollover) else today to null
                listOf(WidgetLine(date?.let { if (config.calendar == CalendarSystem.HEBREW) CalendarEngine().dateLabel(it, hebrew) else it.toString() } ?: "Unavailable",
                    error ?: today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, productLocale), openCalendar))
            }
            WidgetKind.COMBINED -> {
                val (date, error) = CalendarEngine().currentHebrewDate(now, place, config.sunsetRollover)
                listOf(WidgetLine("${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, productLocale)} · $today",
                    date?.let { CalendarEngine().dateLabel(it, hebrew) } ?: error.orEmpty(), openCalendar),
                    WidgetLine(CalendarEngine().upcomingParsha(today, settings), CalendarEngine().daf(today, hebrew), openCalendar))
            }
            WidgetKind.EVENTS -> {
                val events = data.events.flatMap { OccurrenceEngine().expand(it, today.minusDays(1), today.plusYears(1), data) }
                    .filter { it.start != null && (it.start >= now || it.end?.let { end -> end > now } == true) }.toMutableList()
                if (settings.showDeviceEvents) events += runCatching { app.calendars.instances(now, now.plus(Duration.ofDays(365)), settings.visibleCalendarIds) }.getOrDefault(emptyList())
                    .filter { o -> data.exports.none { it.providerId == o.providerId && it.state == "MANAGED" } }
                events.sortedBy { it.start }.take(config.itemCount.coerceIn(1, 10)).map { o ->
                    val intent = if (config.tap == WidgetTap.EVENT && o.providerId != null) app.calendars.viewIntent(o.providerId)
                    else openCalendar.clone() as Intent
                    if (config.tap == WidgetTap.EVENT && o.providerId == null) intent.putExtra("eventId", o.eventId).putExtra("date", o.date.toString())
                    WidgetLine(o.title, if (o.allDay) "${o.date} · ${text("All day", "כל היום")}" else "${o.date} · ${TimeFormatting.format(o.start!!, ZoneId.of(o.zoneId), settings, android.text.format.DateFormat.is24HourFormat(context), productLocale)}", intent)
                }
            }
            WidgetKind.ZMANIM -> {
                val profile = data.profile(config.profileId ?: settings.activeProfileId)
                if (profile == null || place == null) emptyList() else (0L..7L).flatMap { offset ->
                    CalculationEngine().calculate(CalculationQuery(today.plusDays(offset), place, profile, inIsrael = settings.inIsrael ?: false)).filter { (MethodCatalog.byId[it.id]?.group ?: "custom") !in profile.hiddenGroups }
                }.filter { it.value is CalculationValue.Time && it.value.instant >= now }.sortedBy { (it.value as CalculationValue.Time).instant }
                    .take(config.itemCount.coerceIn(1, 10)).map { z ->
                        val instant = (z.value as CalculationValue.Time).instant
                        WidgetLine(com.example.zmanim.ui.methodLabel(z.id, profile, hebrew), TimeFormatting.format(instant, place, settings, android.text.format.DateFormat.is24HourFormat(context), productLocale), openZmanim, instant.toEpochMilli())
                    }
            }
        }
        val dark = settings.appearance == Appearance.DARK || settings.appearance == Appearance.SYSTEM &&
            context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val background = if (dark) Night else Paper
        val primary = if (dark) Sky else InkBlue
        val foreground = if (dark) Mist else DeepInk
        val muted = if (dark) PaleBlue else Slate
        provideContent {
            val size = LocalSize.current
            val maxRows = ((size.height.value - if (size.height < 100.dp) 24f else 64f) / 44f).toInt().coerceIn(1, 8)
            Column(GlanceModifier.fillMaxSize().background(background).padding(if (size.height < 100.dp) 8.dp else 12.dp)) {
                if (size.height >= 100.dp) Text(if (kind == WidgetKind.ZMANIM) text("Zmanim", "זמנים") else if (kind == WidgetKind.EVENTS) text("Upcoming events", "אירועים קרובים") else text("Calendar", "לוח שנה"),
                    style = TextStyle(color = ColorProvider(primary), fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity(if (kind == WidgetKind.ZMANIM) openZmanim else openCalendar)))
                if (rows.isEmpty()) Text(if (kind == WidgetKind.ZMANIM) text("Choose a profile, calculations, and location.", "יש לבחור פרופיל, חישובים ומקום.") else text("No upcoming events.", "אין אירועים קרובים."), style = TextStyle(fontSize = 12.sp))
                @Composable fun Line(row: WidgetLine, first: Boolean) {
                    Column(GlanceModifier.fillMaxWidth().padding(vertical = if (size.height < 100.dp) 2.dp else 6.dp).clickable(actionStartActivity(row.intent))) {
                        Text(row.title, style = TextStyle(color = ColorProvider(foreground), fontSize = 14.sp), maxLines = if (size.height < 100.dp) 1 else 2)
                        Text(row.subtitle, style = TextStyle(color = ColorProvider(muted), fontSize = 11.sp), maxLines = if (size.height < 100.dp) 1 else 2)
                        if (first && config.countdown && row.targetMillis != null && size.height >= 120.dp) {
                            AndroidRemoteViews(android.widget.RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                                setChronometer(R.id.widget_countdown, android.os.SystemClock.elapsedRealtime() + row.targetMillis - System.currentTimeMillis(), null, true)
                                setChronometerCountDown(R.id.widget_countdown, true)
                                setTextColor(R.id.widget_countdown, primary.toArgb())
                            })
                        }
                    }
                }
                if (rows.size > maxRows && kind in setOf(WidgetKind.EVENTS, WidgetKind.ZMANIM)) {
                    WidgetLazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        items(rows) { row -> Line(row, row == rows.firstOrNull()) }
                    }
                } else rows.take(maxRows).forEachIndexed { index, row -> Line(row, index == 0) }
                if (place?.isStale(now) == true) Text(text("Refresh device location", "יש לרענן את מיקום המכשיר"), style = TextStyle(fontSize = 10.sp))
                Text((place?.name?.let { "$it · " } ?: "") + text("Updated ", "עודכן ") + now.atZone(place?.zone() ?: ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), style = TextStyle(color = ColorProvider(muted), fontSize = if (size.height < 100.dp) 8.sp else 10.sp), maxLines = 1)
            }
        }
    }
}
class EventsWidget : CalendarWidget(WidgetKind.EVENTS)
class ZmanimWidget : CalendarWidget(WidgetKind.ZMANIM)
class DateWidget : CalendarWidget(WidgetKind.DATE)
class CombinedWidget : CalendarWidget(WidgetKind.COMBINED)

abstract class BaseWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val app = context.applicationContext as ZmanimApplication
        app.scope.launchWidgetDelete(app, appWidgetIds)
    }
}
private fun kotlinx.coroutines.CoroutineScope.launchWidgetDelete(app: ZmanimApplication, ids: IntArray) = launch {
    app.awaitData(); app.repository.update { it.copy(widgets = it.widgets.filterNot { w -> w.id.toIntOrNull()?.let { it in ids } == true }) }
}
class EventsWidgetReceiver : BaseWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = EventsWidget() }
class ZmanimWidgetReceiver : BaseWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ZmanimWidget() }
class DateWidgetReceiver : BaseWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DateWidget() }
class CombinedWidgetReceiver : BaseWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CombinedWidget() }

object WidgetUpdates {
    suspend fun refresh(context: Context, data: AppData) {
        EventsWidget().updateAll(context); ZmanimWidget().updateAll(context); DateWidget().updateAll(context); CombinedWidget().updateAll(context)
        val now = Instant.now()
        val boundaries = mutableListOf<Instant>()
        data.widgets.forEach { config ->
            val place = data.place(config.location) ?: return@forEach
            val date = LocalDate.now(place.zone())
            boundaries += date.plusDays(1).atStartOfDay(place.zone()).toInstant()
            if (config.sunsetRollover) CalculationEngine().seaLevelSunset(date, place)?.takeIf { it > now }?.let { boundaries += it }
            if (config.kind == WidgetKind.ZMANIM) data.profile(config.profileId ?: data.settings.activeProfileId)?.let { p ->
                CalculationEngine().calculate(CalculationQuery(date, place, p, inIsrael = data.settings.inIsrael ?: false)).mapNotNull { (it.value as? CalculationValue.Time)?.instant }.filter { it > now }.minOrNull()?.let { boundaries += it }
            }
        }
        if (data.settings.dateNotification) {
            data.place()?.let { place ->
                val date = LocalDate.now(place.zone()); boundaries += date.plusDays(1).atStartOfDay(place.zone()).toInstant()
                if (data.settings.dateNotificationSunset) CalculationEngine().seaLevelSunset(date, place)?.takeIf { it > now }?.let { boundaries += it }
            }
        }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 300, Intent(context, WidgetBoundaryReceiver::class.java).setAction("com.example.zmanim.WIDGET_BOUNDARY"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.cancel(pi)
        boundaries.minOrNull()?.let {
            try {
                if (android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pi)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pi)
            } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pi) }
        }
    }
}
class WidgetBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync(); val app = context.applicationContext as ZmanimApplication
        app.runBackground(pending) { WidgetUpdates.refresh(context, it.repository.data); it.reminders.updateDateNotification(it.repository.data) }
    }
}

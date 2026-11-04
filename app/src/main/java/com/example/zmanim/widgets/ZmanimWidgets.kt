package com.example.zmanim.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import androidx.glance.*
import kotlinx.coroutines.launch
import androidx.glance.appwidget.*
import com.example.zmanim.MainActivity
import com.example.zmanim.ZmanimApplication
import com.example.zmanim.domain.*
import java.time.*

internal data class WidgetLine(val title: String, val subtitle: String, val intent: android.content.Intent, val targetMillis: Long? = null)

open class CalendarWidget(private val kind: WidgetKind, private val dateCalendar: CalendarSystem = CalendarSystem.HEBREW) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ZmanimApplication
        val data = app.awaitData()
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = data.widgets.find { it.id == widgetId.toString() } ?: WidgetConfig(widgetId.toString(), kind, calendar = dateCalendar)
        if (data.widgets.none { it.id == widgetId.toString() }) app.repository.update { current ->
            if (current.widgets.any { it.id == config.id }) current else current.copy(widgets = current.widgets + config)
        }
        val settings = data.settings
        val productLocale = settings.productLocale()
        val isHebrewUi = settings.language == Language.HEBREW || settings.language == Language.SYSTEM && context.resources.configuration.locales[0].language in setOf("he", "iw")
        fun text(en: String, he: String) = if (isHebrewUi) he else en
        val place = data.widgetPlace(config)
        val now = Instant.now()
        val today = LocalDate.now(place?.zone() ?: ZoneId.systemDefault())
        val hebrew = settings.labels == LabelLanguage.HEBREW
        val openCalendar = widgetLaunch(context, widgetId, "calendar", today)
        val openZmanim = widgetLaunch(context, widgetId, if (place == null) "location" else "zmanim", today)
        val rows: List<WidgetLine> = when (kind) {
            WidgetKind.DATE -> emptyList()
            WidgetKind.COMBINED -> app.learning.rows(today, config.learning, settings).map { row ->
                WidgetLine(row.label, row.value, if (config.openingView == OpeningView.ZMANIM) openZmanim else openCalendar)
            }
            WidgetKind.EVENTS -> {
                val events = data.events.flatMap { OccurrenceEngine().expand(it, today.minusDays(it.durationMinutes / 1440 + 2), today.plusYears(1), data) }
                    .filter { it.start != null && (it.start >= now || it.end?.let { end -> end > now } == true) }.toMutableList()
                if (settings.showDeviceEvents) events += runCatching { app.calendars.instances(now, now.plus(Duration.ofDays(365)), settings.visibleCalendarIds) }.getOrDefault(emptyList())
                    .filter { o -> data.exports.none { it.providerId == o.providerId && it.state == "MANAGED" } }
                events.sortedBy { it.start }.take(config.itemCount.coerceIn(1, 10)).map { o ->
                    val intent = widgetLaunch(context, widgetId, "calendar", o.date, o.key)
                    if (config.tap == WidgetTap.EVENT) {
                        if (o.providerId != null) intent.putExtra("providerId", o.providerId).putExtra("instanceStart", o.start!!.toEpochMilli())
                        else intent.putExtra("eventId", o.eventId).putExtra("instanceStart", o.start!!.toEpochMilli())
                    }
                    WidgetLine(o.title, if (o.allDay) "${o.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", productLocale))} · ${text("All day", "כל היום")}" else "${o.date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", productLocale))} · ${TimeFormatting.format(o.start!!, ZoneId.of(o.zoneId), settings, android.text.format.DateFormat.is24HourFormat(context), productLocale)}", intent)
                }
            }
            WidgetKind.ZMANIM -> {
                val profile = data.profile(config.profileId ?: settings.activeProfileId)
                if (profile == null || place == null) emptyList() else (0L..7L).flatMap { offset ->
                    CalculationEngine().calculate(CalculationQuery(today.plusDays(offset), place, profile, inIsrael = settings.inIsrael ?: false)).filter { (MethodCatalog.byId[it.id]?.group ?: "custom") !in profile.hiddenGroups }
                }.filter { it.value is CalculationValue.Time && it.value.instant >= now }.sortedBy { (it.value as CalculationValue.Time).instant }
                    .take(config.itemCount.coerceIn(1, 10)).map { z ->
                        val instant = (z.value as CalculationValue.Time).instant
                        WidgetLine(com.example.zmanim.ui.methodLabel(z.id, profile, hebrew),
                            (if (instant.atZone(place.zone()).toLocalDate() == today) "" else instant.atZone(place.zone()).format(java.time.format.DateTimeFormatter.ofPattern("EEE", productLocale)) + " · ") +
                            TimeFormatting.format(instant, place, settings, android.text.format.DateFormat.is24HourFormat(context), productLocale), openZmanim, instant.toEpochMilli())
                    }
            }
        }
        provideContent { WidgetContent(kind, config, data, place, now, rows, openCalendar, openZmanim, context) }
    }
}
class EventsWidget : CalendarWidget(WidgetKind.EVENTS)
class ZmanimWidget : CalendarWidget(WidgetKind.ZMANIM)
class DateWidget : CalendarWidget(WidgetKind.DATE)
class GregorianDateWidget : CalendarWidget(WidgetKind.DATE, CalendarSystem.GREGORIAN)
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
class GregorianDateWidgetReceiver : BaseWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GregorianDateWidget() }
class CombinedWidgetReceiver : BaseWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CombinedWidget() }

object WidgetUpdates {
    suspend fun refresh(context: Context, data: AppData) {
        EventsWidget().updateAll(context); ZmanimWidget().updateAll(context); DateWidget().updateAll(context); GregorianDateWidget().updateAll(context); CombinedWidget().updateAll(context)
        val now = Instant.now()
        val boundaries = mutableListOf<Instant>()
        data.widgets.forEach { config ->
            val place = data.widgetPlace(config)
            val zone = place?.zone() ?: ZoneId.systemDefault()
            val date = LocalDate.now(zone)
            boundaries += date.plusDays(1).atStartOfDay(zone).toInstant()
            if (place != null && config.sunsetRollover && !(config.kind == WidgetKind.DATE && config.calendar == CalendarSystem.GREGORIAN)) CalculationEngine().seaLevelSunset(date, place)?.takeIf { it > now }?.let { boundaries += it }
            if (config.kind == WidgetKind.ZMANIM && place != null) data.profile(config.profileId ?: data.settings.activeProfileId)?.let { p ->
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

internal fun widgetLaunch(context: Context, id: Int, destination: String, date: LocalDate, identity: String = destination): Intent =
    Intent(context, MainActivity::class.java)
        .setData(android.net.Uri.parse("zmanim://widget/$id/${android.net.Uri.encode(identity)}"))
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("destination", destination).putExtra("date", date.toString()).putExtra("widgetId", id)

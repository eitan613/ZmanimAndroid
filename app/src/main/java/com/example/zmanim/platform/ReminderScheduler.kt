package com.example.zmanim.platform

import android.Manifest
import android.annotation.SuppressLint
import com.example.zmanim.R
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.zmanim.MainActivity
import com.example.zmanim.ZmanimApplication
import com.example.zmanim.domain.*
import java.time.*

class ReminderScheduler(private val context: Context, private val clock: Clock = Clock.systemUTC()) {
    data class Due(val id: String, val at: Instant, val title: String, val text: String, val eventId: String? = null, val date: String? = null, val rule: ReminderRule = ReminderRule(), val contextKey: String = "")
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)
    fun preciseAccess() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun notificationAccess(channelId: String = "reminders"): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled() && context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    fun status(settings: AppSettings): String = when {
        !notificationAccess() -> "Inactive: notifications are blocked."
        preciseAccess() -> "Precise reminders available."
        settings.approximateAlarmsAccepted -> "Approximate reminders: Android may deliver them late."
        else -> "Inactive: allow precise alarms or accept approximate reminders."
    }
    fun exactAccessIntent() = Intent(if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM else Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
    fun notificationSettingsIntent() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    private fun pendingIntent() = PendingIntent.getBroadcast(context, 100, Intent(context, ReminderReceiver::class.java).setAction("com.example.zmanim.REMINDER"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun upcoming(data: AppData, from: Instant = clock.instant(), days: Long = 8): List<Due> {
        val results = mutableListOf<Due>()
        if (data.settings.eventNotifications) for (event in data.events.filter { it.remindersEnabled && it.reminders.isNotEmpty() && (it.exportCalendarId == null || it.reminderDeliveryOwner() == ReminderOwner.APP) }) {
            val local = from.atZone(ZoneId.of(event.zoneId)).toLocalDate()
            val lead = event.reminders.maxOf { it.minutesBefore }.coerceAtLeast(0) / 1440 + 2L
            for (o in OccurrenceEngine().expand(event, local.minusDays(2), local.plusDays(days + lead), data)) {
                val start = o.start ?: continue
                for (r in event.reminders.filter { it.enabled }) {
                    val at = start.minusSeconds(r.minutesBefore * 60L)
                    if (at >= from && at <= from.plus(Duration.ofDays(days))) results += Due("${o.key}:${r.id}", at, event.title,
                        TimeFormatting.occurrence(o, data.settings, android.text.format.DateFormat.is24HourFormat(context), data.settings.productLocale()), event.id, o.date.toString(), r)
                }
            }
        }
        val p = data.profile()
        val place = data.place()
        if (data.settings.zmanNotifications && p != null && p.remindersEnabled && place != null) {
            val ids = p.reminders.filter { it.methodId in p.selected && (MethodCatalog.byId[it.methodId]?.group ?: "custom") !in p.hiddenGroups }.map { it.methodId }
            val candidateDates = p.reminders.filter { it.methodId in ids }.flatMap { zr -> zr.reminders.filter { it.enabled }.flatMap { r ->
                val first = from.plusSeconds(r.minutesBefore * 60L).atZone(place.zone()).toLocalDate().minusDays(1)
                (0L..days + 2).map(first::plusDays)
            } }.distinct().sorted()
            for (date in candidateDates) {
                val values = CalculationEngine().calculate(CalculationQuery(date, place, p, ids, data.settings.inIsrael ?: false))
                for (value in values) {
                    val instant = (value.value as? CalculationValue.Time)?.instant ?: continue
                    for (r in p.reminders.find { it.methodId == value.id }?.reminders.orEmpty().filter { it.enabled }) {
                        val at = instant.minusSeconds(r.minutesBefore * 60L)
                        if (at >= from && at <= from.plus(Duration.ofDays(days))) results += Due("${p.id}:${value.id}:$date:${r.id}", at, bidiZmanLabel(methodDisplayName(value.id, p, data.settings.labels == LabelLanguage.HEBREW)),
                            "${place.name} · ${TimeFormatting.format(instant, place, data.settings, android.text.format.DateFormat.is24HourFormat(context), data.settings.productLocale())}", rule = r, contextKey = "$place:${p.options}")
                    }
                }
            }
        }
        return results.sortedBy { it.at }
    }
    @Synchronized fun reconcile(data: AppData) {
        alarms.cancel(pendingIntent())
        val planned = prefs.getLong("scheduled_at", Long.MIN_VALUE)
        val plannedIds = prefs.getStringSet("scheduled_ids", emptySet()).orEmpty()
        if (plannedIds.isNotEmpty() && planned <= clock.millis() && planned >= clock.millis() - 86_400_000) {
            deliver(data)
            return
        }
        prefs.edit().remove("scheduled_at").remove("scheduled_ids").apply()
        val candidates = upcoming(data)
        ZmanAlarmController(context).reconcile(data)
        val next = candidates.firstOrNull { !prefs.contains(it.id) && canDeliver(it, data.settings) } ?: return
        prefs.edit().putLong("scheduled_at", next.at.toEpochMilli())
            .putStringSet("scheduled_ids", candidates.filter { it.at == next.at && canDeliver(it, data.settings) }.map { it.id }.toSet()).apply()
        try {
            if (next.rule.mode == ReminderMode.ALARM) {
                val show = PendingIntent.getActivity(context, 101, Intent(context, MainActivity::class.java)
                    .putExtra("destination", if (next.eventId == null) "zmanim" else "event")
                    .putExtra("eventId", next.eventId).putExtra("date", next.date), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(next.at.toEpochMilli(), show), pendingIntent())
            } else if (preciseAccess()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at.toEpochMilli(), pendingIntent())
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at.toEpochMilli(), pendingIntent())
        } catch (_: SecurityException) { /* Permission may be revoked between check and scheduling. */ }
    }
    private fun canDeliver(due: Due, settings: AppSettings): Boolean = if (due.rule.mode == ReminderMode.ALARM)
        preciseAccess() && notificationAccess("zman-alarms")
        else notificationAccess() && (preciseAccess() || settings.approximateAlarmsAccepted)

    @SuppressLint("MissingPermission") // notificationAccess() checks the runtime grant and notification switch.
    @Synchronized fun deliver(data: AppData) {
        val now = clock.instant()
        val scheduledAt = prefs.getLong("scheduled_at", Long.MIN_VALUE)
        val scheduledIds = prefs.getStringSet("scheduled_ids", emptySet()).orEmpty().toSet()
        prefs.edit().remove("scheduled_at").remove("scheduled_ids").apply()
        val from = maxOf(Instant.ofEpochMilli(scheduledAt), now.minus(Duration.ofDays(1)))
        run {
            for (due in upcoming(data, from, 2).filter { it.id in scheduledIds && it.at.toEpochMilli() == scheduledAt && it.at <= now && !prefs.contains(it.id) && canDeliver(it, data.settings) }) {
                if (due.rule.mode == ReminderMode.ALARM) {
                    if (Duration.between(due.at, now).toMinutes() <= 5) ZmanAlarmController(context).ring(due)
                    prefs.edit().putLong(due.id, now.toEpochMilli()).apply()
                    continue
                }
                // Recomputed inputs ensure an edited/canceled reminder cannot survive as a stale alarm payload.
                val open = Intent(context, MainActivity::class.java).apply {
                    putExtra("destination", if (due.eventId == null) "zmanim" else "calendar")
                    due.eventId?.let { putExtra("eventId", it) }
                    due.date?.let { putExtra("date", it) }
                }
                val pi = PendingIntent.getActivity(context, due.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = NotificationCompat.Builder(context, "reminders").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(due.title).setContentText(due.text).setStyle(NotificationCompat.BigTextStyle().bigText(due.text))
                    .setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
                NotificationManagerCompat.from(context).notify(due.id.hashCode(), notification)
                prefs.edit().putLong(due.id, now.toEpochMilli()).apply()
            }
        }
        val editor = prefs.edit().putLong("last_dispatch", now.toEpochMilli())
        prefs.all.filter { it.key !in setOf("last_dispatch", "scheduled_at", "scheduled_ids") && (it.value as? Long ?: Long.MAX_VALUE) < now.minus(Duration.ofDays(32)).toEpochMilli() }.keys.forEach(editor::remove)
        editor.apply()
        reconcile(data)
    }
    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reminders", "Event and zmanim reminders", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel("zman-alarms", "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Event and zmanim alarms with Dismiss and Snooze controls"; setSound(null, null); enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel("hebrew-date", "Current Hebrew date", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
    }
    @SuppressLint("MissingPermission") // notificationAccess() checks the runtime grant and notification switch.
    fun updateDateNotification(data: AppData) {
        val manager = NotificationManagerCompat.from(context)
        if (!data.settings.dateNotification || !notificationAccess("hebrew-date")) { manager.cancel(42); return }
        val place = data.place()
        val (date, reason) = CalendarEngine().currentHebrewDate(clock.instant(), place, data.settings.dateNotificationSunset)
        val text = date?.let { CalendarEngine().dateLabel(it, true) } ?: reason!!
        val day = date?.let { com.kosherjava.zmanim.hebrewcalendar.JewishDate(it).jewishDayOfMonth }
        val icons = intArrayOf(R.drawable.hebrew_day_1, R.drawable.hebrew_day_2, R.drawable.hebrew_day_3, R.drawable.hebrew_day_4, R.drawable.hebrew_day_5, R.drawable.hebrew_day_6, R.drawable.hebrew_day_7, R.drawable.hebrew_day_8, R.drawable.hebrew_day_9, R.drawable.hebrew_day_10, R.drawable.hebrew_day_11, R.drawable.hebrew_day_12, R.drawable.hebrew_day_13, R.drawable.hebrew_day_14, R.drawable.hebrew_day_15, R.drawable.hebrew_day_16, R.drawable.hebrew_day_17, R.drawable.hebrew_day_18, R.drawable.hebrew_day_19, R.drawable.hebrew_day_20, R.drawable.hebrew_day_21, R.drawable.hebrew_day_22, R.drawable.hebrew_day_23, R.drawable.hebrew_day_24, R.drawable.hebrew_day_25, R.drawable.hebrew_day_26, R.drawable.hebrew_day_27, R.drawable.hebrew_day_28, R.drawable.hebrew_day_29, R.drawable.hebrew_day_30)
        val iconId = day?.let { icons[it - 1] } ?: android.R.drawable.ic_menu_today
        val open = PendingIntent.getActivity(context, 42, Intent(context, MainActivity::class.java).putExtra("destination", "today"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(42, NotificationCompat.Builder(context, "hebrew-date").setSmallIcon(iconId)
            .setContentTitle(text).setContentText(place?.name ?: "Set app location").setSubText("Hebrew date · תאריך עברי")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\n" +
                (date?.let { it.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM uuuu", data.settings.productLocale())) } ?: reason.orEmpty()) +
                "\n" + (place?.let { "${it.name} · ${it.zoneId}" } ?: "Set app location")))
            .setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(true).setSilent(true).build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as ZmanimApplication).runBackground(pending) { app -> app.reminders.deliver(app.repository.data) }
    }
}
class SystemChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val controller = ZmanAlarmController(context)
            controller.entries().filter { it.snoozedUntil == null }.forEach { controller.remove(it.id) }
        }
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
            (context.applicationContext as ZmanimApplication).requestMaintenance()
    }
}

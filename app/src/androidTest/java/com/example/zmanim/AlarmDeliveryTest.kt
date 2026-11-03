package com.example.zmanim

import android.app.Notification
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.domain.*
import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDeliveryTest {
    @Test fun exactAlarmPostsOneNotificationAndDoesNotRedeliver() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val app = context.applicationContext as ZmanimApplication
            val original = app.awaitData()
            assumeTrue("Allow notifications and precise alarms for the delivery test.", app.reminders.notificationAccess() && app.reminders.preciseAccess())
            val title = "Alarm integration ${newId()}"
            val at = Instant.now().plusSeconds(8).atZone(ZoneOffset.UTC).withNano(0)
            val event = Event(title = title, anchorDate = at.toLocalDate().toString(), localTime = at.toLocalTime().toString(), zoneId = "UTC", reminders = listOf(ReminderRule()))
            val manager = context.getSystemService(NotificationManager::class.java)
            try {
                app.repository.update { AppData(settings = AppSettings(onboardingComplete = true, migratedPrototype = true), events = listOf(event)) }
                app.reminders.reconcile(app.repository.data)
                val posted = withTimeout(25_000) {
                    var found = manager.activeNotifications.firstOrNull { it.notification.extras.getString(Notification.EXTRA_TITLE) == title }
                    while (found == null) { delay(200); found = manager.activeNotifications.firstOrNull { it.notification.extras.getString(Notification.EXTRA_TITLE) == title } }
                    found
                }
                val postedAt = posted.postTime
                app.reminders.deliver(app.repository.data)
                assertEquals(postedAt, manager.activeNotifications.first { it.id == posted.id }.postTime)
            } finally {
                manager.activeNotifications.filter { it.notification.extras.getString(Notification.EXTRA_TITLE) == title }.forEach { manager.cancel(it.id) }
                app.repository.update { original }; app.reminders.reconcile(original)
            }
        }
    }
    @Test fun dateNotificationAlwaysUsesHebrewLettersAndLetterIcons() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ZmanimApplication
        val original = app.awaitData()
        assumeTrue("Allow Hebrew date notifications for this test.", app.reminders.notificationAccess("hebrew-date"))
        val place = Place(id = "notification-place", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            for (day in listOf(1, 15, 16, 22, 30)) {
                val civil = com.kosherjava.zmanim.hebrewcalendar.JewishDate(5787, 1, day).localDate
                val now = civil.atTime(12, 0).atZone(place.zone()).toInstant()
                val scheduler = com.example.zmanim.platform.ReminderScheduler(context, Clock.fixed(now, place.zone()))
                val data = AppData(settings = AppSettings(dateNotification = true, dateNotificationSunset = false,
                    labels = LabelLanguage.TRANSLITERATED, location = LocationBinding(savedId = place.id)), places = listOf(place))
                scheduler.updateDateNotification(data)
                val expected = CalendarEngine().dateLabel(civil, true)
                val notification = withTimeout(5000) {
                    var current = manager.activeNotifications.firstOrNull { it.id == 42 && it.notification.extras.getString(Notification.EXTRA_TITLE) == expected }
                    while (current == null) { delay(50); current = manager.activeNotifications.firstOrNull { it.id == 42 && it.notification.extras.getString(Notification.EXTRA_TITLE) == expected } }
                    current.notification
                }
                assertEquals(expected, notification.extras.getString(Notification.EXTRA_TITLE))
                assertFalse(expected.any { it.isDigit() })
                val icon = context.resources.getIdentifier("hebrew_day_$day", "drawable", context.packageName)
                assertEquals(icon, notification.smallIcon.resId)
            }
            val bitmap = android.graphics.Bitmap.createBitmap(480, 256, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(20, 43, 61))
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 12f; isAntiAlias = true }
            for (day in 1..30) {
                val x = ((day - 1) % 8) * 60; val y = ((day - 1) / 8) * 64
                val icon = context.resources.getDrawable(context.resources.getIdentifier("hebrew_day_$day", "drawable", context.packageName), context.theme)
                icon.setBounds(x + 14, y + 4, x + 46, y + 36); icon.draw(canvas)
                canvas.drawText(day.toString(), x + 23f, y + 54f, paint)
            }
            java.io.File(context.cacheDir, "qa").mkdirs()
            java.io.File(context.cacheDir, "qa/hebrew-status-icons.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { manager.cancel(42); app.reminders.updateDateNotification(original) }
    }

}

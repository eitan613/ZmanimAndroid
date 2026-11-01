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
}

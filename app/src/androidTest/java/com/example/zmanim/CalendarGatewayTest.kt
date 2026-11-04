package com.example.zmanim

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.domain.*
import com.example.zmanim.platform.CalendarGateway
import com.example.zmanim.platform.ReminderScheduler
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarGatewayTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val gateway get() = CalendarGateway(context)
    private var calendarId = -1L
    private fun syncUri() = CalendarContract.Calendars.CONTENT_URI.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "zmanim-integration-tests")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
    @Before fun createCalendar() {
        Assume.assumeTrue("Grant READ_CALENDAR and WRITE_CALENDAR for provider integration tests.", gateway.canRead() && gateway.canWrite())
        calendarId = ContentUris.parseId(context.contentResolver.insert(syncUri(), ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "zmanim-integration-tests")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Zmanim integration test")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Zmanim integration test")
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "zmanim-integration-tests")
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, "Asia/Jerusalem")
            put(CalendarContract.Calendars.SYNC_EVENTS, 1); put(CalendarContract.Calendars.VISIBLE, 1)
        })!!)
    }
    @After fun cleanup() { if (calendarId >= 0) context.contentResolver.delete(syncUri(), "_id = ?", arrayOf(calendarId.toString())) }
    @Test fun derivedExportsAreIdempotentAndExternalEditsArePreserved() {
        val today = LocalDate.now(ZoneId.of("Asia/Jerusalem"))
        val place = Place(id = "p", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        val profile = Profile(id = "profile")
        val event = Event(title = "Relative event", anchorDate = today.toString(), zoneId = place.zoneId,
            timeKind = EventTimeKind.ZMAN, profileId = profile.id, methodId = "sea-level-sunset", offsetMinutes = -15.0,
            location = LocationBinding(savedId = place.id), recurrence = Recurrence(Frequency.DAILY, count = 3), exportCalendarId = calendarId)
        val data = AppData(profiles = listOf(profile), places = listOf(place), events = listOf(event))
        val first = gateway.reconcile(data, today)
        assertEquals(3, first.size)
        val second = gateway.reconcile(data.copy(exports = first), today)
        assertEquals(first.map { it.providerId }.toSet(), second.map { it.providerId }.toSet())
        val edited = first.first()
        context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, edited.providerId),
            ContentValues().apply { put(CalendarContract.Events.TITLE, "Changed outside the app") }, null, null)
        val third = gateway.reconcile(data.copy(exports = second), today)
        assertEquals("CONFLICT", third.first { it.id == edited.id }.state)
        context.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, edited.providerId), arrayOf("title"), null, null, null)!!.use {
            assertTrue(it.moveToFirst()); assertEquals("Changed outside the app", it.getString(0))
        }
    }
    @Test fun ordinaryEventsSupportCreateEditDeleteAndDstOverlapSelection() {
        val event = Event(title = "Ordinary", anchorDate = "2026-11-02", localTime = "01:30", zoneId = "America/New_York", reminders = listOf(ReminderRule(minutesBefore = 10)))
        val id = gateway.saveOrdinary(event, calendarId)
        val loaded = gateway.ordinaryEvent(id)!!
        assertEquals("01:30", LocalTime.parse(loaded.first.localTime).toString())
        assertEquals(10, loaded.first.reminders.single().minutesBefore)
        assertEquals(ReminderOwner.CALENDAR, loaded.first.reminderOwner)
        context.contentResolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), arrayOf("dtstart"), null, null, null)!!.use {
            assertTrue(it.moveToFirst()); assertEquals(Instant.parse("2026-11-02T06:30:00Z").toEpochMilli(), it.getLong(0))
        }
        assertEquals(id, gateway.saveOrdinary(event.copy(title = "Edited"), calendarId, id))
        assertEquals("Edited", gateway.ordinaryEvent(id)!!.first.title)
        gateway.deleteOrdinary(id)
        assertNull(gateway.ordinaryEvent(id))
    }
    @Test fun convertingAProviderEventToAManagedRuleAdoptsItsRecordInsteadOfDuplicatingIt() {
        val today = LocalDate.now(ZoneId.of("Asia/Jerusalem"))
        val existing = Event(title = "Adopted event", anchorDate = today.toString(), zoneId = "Asia/Jerusalem")
        val id = gateway.saveOrdinary(existing, calendarId)
        val place = Place(id = "adopt-place", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        val profile = Profile(id = "adopt-profile")
        val managed = existing.copy(timeKind = EventTimeKind.ZMAN, methodId = "sea-level-sunset", profileId = profile.id,
            recurrence = Recurrence(Frequency.DAILY, count = 2), exportCalendarId = calendarId)
        val data = AppData(settings = AppSettings(location = LocationBinding(savedId = place.id)),
            profiles = listOf(profile), places = listOf(place), events = listOf(managed))
        val occurrence = OccurrenceEngine().occurrence(managed, today, data)
        val adopted = gateway.adopt(managed, occurrence, calendarId, id)
        val links = gateway.reconcile(data.copy(exports = listOf(adopted)), today)
        assertEquals(2, links.size)
        assertEquals(id, links.first { it.id == occurrence.key }.providerId)
        val visible = gateway.instances(today.atStartOfDay(place.zone()).toInstant(), today.plusDays(3).atStartOfDay(place.zone()).toInstant(), setOf(calendarId))
        assertEquals(2, visible.size)
        assertEquals(occurrence.start!!.toEpochMilli(), visible.first { it.providerId == id }.start!!.toEpochMilli())
    }

    @Test fun reminderSchedulesIgnoreDisplayRounding() {
        val now = Instant.parse("2026-11-01T00:00:00Z")
        val scheduler = ReminderScheduler(context, Clock.fixed(now, ZoneOffset.UTC))
        val place = Place(id = "p", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        val reminder = ReminderRule(minutesBefore = 10)
        val profile = Profile(id = "profile", selected = listOf("sea-level-sunset"), reminders = listOf(ZmanReminder("sea-level-sunset", listOf(reminder))))
        val data = AppData(settings = AppSettings(activeProfileId = profile.id, location = LocationBinding(savedId = place.id)), profiles = listOf(profile), places = listOf(place))
        val exact = scheduler.upcoming(data).map { it.at }
        val rounded = scheduler.upcoming(data.copy(settings = data.settings.copy(rounding = Rounding.UP))).map { it.at }
        assertEquals(exact, rounded)
        assertEquals(CalculationEngine().seaLevelSunset(LocalDate.of(2026, 11, 1), place)!!.minusSeconds(600), exact.first())
    }
}

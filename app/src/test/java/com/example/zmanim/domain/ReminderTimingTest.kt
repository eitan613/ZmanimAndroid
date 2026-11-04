package com.example.zmanim.domain

import org.junit.Assert.*
import org.junit.Test

class ReminderTimingTest {
    private fun event(reminders: List<ReminderRule>) = Event(title = "Meeting", anchorDate = "2026-11-04", zoneId = "Asia/Jerusalem",
        exportCalendarId = 1, reminderOwner = ReminderOwner.CALENDAR, reminders = reminders)

    @Test fun ringingAlarmsAlwaysUseAppDeliveryIncludingCalendarExports() {
        val alarm = ReminderRule(mode = ReminderMode.ALARM)
        val notification = ReminderRule(mode = ReminderMode.NOTIFICATION)
        assertEquals(ReminderOwner.APP, event(listOf(alarm, notification)).reminderDeliveryOwner())
        assertEquals(ReminderOwner.APP, event(listOf(alarm.copy(enabled = false))).reminderDeliveryOwner())
        assertEquals(ReminderOwner.CALENDAR, event(listOf(notification)).reminderDeliveryOwner())
        assertFalse(event(listOf(notification)).hasAlarmReminders())
    }

    @Test fun eventAlarmsValidateSnoozeBeforeSaving() {
        for (snooze in listOf(0, 121, -1)) {
            val invalid = event(listOf(ReminderRule(mode = ReminderMode.ALARM, snoozeMinutes = snooze)))
            val result = runCatching { OccurrenceEngine().validate(invalid, AppData()) }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Snooze must be between 1 and 120 minutes.", result.exceptionOrNull()?.message)
        }
        for (snooze in listOf(1, 5, 120)) {
            OccurrenceEngine().validate(event(listOf(ReminderRule(mode = ReminderMode.ALARM, snoozeMinutes = snooze))), AppData())
        }
    }

    @Test fun amountsConvertToTheMinutesUsedByTheScheduler() {
        assertEquals(10, reminderMinutes("10", ReminderUnit.MINUTES))
        assertEquals(120, reminderMinutes("2", ReminderUnit.HOURS))
        assertEquals(4320, reminderMinutes("3", ReminderUnit.DAYS))
        assertEquals(20160, reminderMinutes("2", ReminderUnit.WEEKS))
    }

    @Test fun invalidAndOverflowingAmountsCannotBecomeValidReminders() {
        for (amount in listOf("", "0", "-1", "1.5", "abc", "99999999999999999999999")) {
            assertNull(reminderMinutes(amount, ReminderUnit.WEEKS))
        }
        assertEquals(525600, reminderMinutes("365", ReminderUnit.DAYS))
        assertNull(reminderMinutes("366", ReminderUnit.DAYS))
        assertNull(reminderMinutes(Long.MAX_VALUE.toString(), ReminderUnit.WEEKS))
    }

    @Test fun existingMinuteOffsetsUseWholeUnitsWithoutChangingTheirMeaning() {
        for (minutes in listOf(1, 10, 60, 90, 1440, 2880, 10080, 20160, 525600)) {
            val unit = ReminderUnit.forMinutes(minutes)
            assertEquals(minutes, reminderMinutes((minutes / unit.minutes).toString(), unit))
        }
        assertEquals(ReminderUnit.WEEKS, ReminderUnit.forMinutes(10080))
        assertEquals(ReminderUnit.MINUTES, ReminderUnit.forMinutes(90))
        assertEquals(ReminderUnit.MINUTES, ReminderUnit.forMinutes(0))
    }
}

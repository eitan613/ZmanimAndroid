package com.example.zmanim.calculation

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZmanimCalculatorTest {
    private val calculator = ZmanimCalculator()

    @Test
    fun roshHashanahHasTheExpectedHebrewDate() {
        val day = calculator.calculate(LocalDate.of(2026, 9, 12), SavedLocation.Jerusalem)
        assertEquals("1 Tishrei, 5787", day.transliteratedDate)
        assertTrue(day.hebrewDate.contains("תשרי"))
    }

    @Test
    fun polarSummerKeepsMissingSolarEventsUnavailable() {
        val tromso = SavedLocation("Tromsø", 69.6492, 18.9553, ZoneId.of("Europe/Oslo"))
        val day = calculator.calculate(LocalDate.of(2026, 6, 21), tromso)
        assertNull(day.sunrise)
        assertNull(day.sunset)
    }

    @Test
    fun locationTimezoneDeterminesSolarDayAcrossDstChange() {
        val location = SavedLocation.Jerusalem
        val before = calculator.calculate(LocalDate.of(2026, 3, 26), location)
        val after = calculator.calculate(LocalDate.of(2026, 3, 27), location)
        assertEquals(ZoneOffset.ofHours(2), before.sunrise!!.atZone(location.zoneId).offset)
        assertEquals(ZoneOffset.ofHours(3), after.sunrise!!.atZone(location.zoneId).offset)
        assertEquals(before.date, before.sunrise.atZone(location.zoneId).toLocalDate())
        assertEquals(after.date, after.sunrise.atZone(location.zoneId).toLocalDate())
        assertTrue(before.sunrise.isBefore(before.sunset))
    }

    @Test
    fun phoneTimezoneDoesNotChangeTheSelectedLocationResults() {
        val original = TimeZone.getDefault()
        val date = LocalDate.of(2026, 11, 1)
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            val first = calculator.calculate(date, SavedLocation.Jerusalem)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(first, calculator.calculate(date, SavedLocation.Jerusalem))
            assertNotEquals(first.sunrise, calculator.calculate(date.plusDays(1), SavedLocation.Jerusalem).sunrise)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}

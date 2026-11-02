package com.example.zmanim.domain

import com.example.zmanim.data.*
import com.example.zmanim.ui.*
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RedesignTest {
    private val place = Place(id = "jerusalem", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
    private val engine = CalculationEngine()
    private val chametz = "sof-zman-achilas-chametz-gra"
    private fun value(date: LocalDate, id: String, profile: Profile = Profile(), israel: Boolean = false) =
        engine.calculate(CalculationQuery(date, place, profile, listOf(id), israel)).single().value

    @Test fun seasonalResultsAndTheirCustomDependenciesAreNotFailures() {
        val custom = CustomZman(id = "before-chametz", name = "Before chametz", kind = FormulaKind.FIXED_MINUTES, base = chametz, amount = -10.0)
        val p = Profile(custom = listOf(custom))
        val ordinary = LocalDate.of(2026, 11, 2)
        assertEquals(CalculationValue.NotApplicable, value(ordinary, chametz))
        assertEquals(CalculationValue.NotApplicable, value(ordinary, custom.id, p))
        val erev = JewishDate(5787, JewishDate.NISSAN, 14).localDate
        assertTrue(value(erev, chametz) is CalculationValue.Time)
        assertTrue(value(erev, custom.id, p) is CalculationValue.Time)
        assertEquals(CalculationValue.NotApplicable, value(erev.plusDays(1), chametz))
    }
    @Test fun candleLightingUsesOffsetOrExplicitNightfallAndRespectsSchedule() {
        val p = Profile(options = CalculationOptions(candleLightingMinutes = 40.0, candleLightingNightfallId = "tzais72-minutes"))
        val friday = LocalDate.of(2026, 11, 6)
        val sunset = (value(friday, "sea-level-sunset") as CalculationValue.Time).instant
        assertEquals(sunset.minusSeconds(2400), (value(friday, "candle-lighting", p) as CalculationValue.Time).instant)
        assertEquals(CalculationValue.NotApplicable, value(LocalDate.of(2026, 11, 2), "candle-lighting", p))
        val firstRh = JewishDate(5787, JewishDate.TISHREI, 1).localDate
        assertEquals(value(firstRh, "tzais72-minutes", p), value(firstRh, "candle-lighting", p))
        assertTrue(value(firstRh, "candle-lighting") is CalculationValue.Unavailable)
        val firstShavuos = (5787..5798).map { JewishDate(it, JewishDate.SIVAN, 6).localDate }.first { it.dayOfWeek != DayOfWeek.FRIDAY }
        assertNotEquals(DayOfWeek.FRIDAY, firstShavuos.dayOfWeek)
        assertEquals(CalculationValue.NotApplicable, value(firstShavuos, "candle-lighting", p, israel = true))
        assertEquals(value(firstShavuos, "tzais72-minutes", p), value(firstShavuos, "candle-lighting", p, israel = false))
    }
    @Test fun holidayNightfallCannotDependOnCandleLightingOrUseADuration() {
        val custom = CustomZman(id = "loop", name = "Loop", kind = FormulaKind.FIXED_MINUTES, base = "candle-lighting")
        assertThrows(IllegalArgumentException::class.java) { engine.validateProfile(Profile(custom = listOf(custom), options = CalculationOptions(candleLightingNightfallId = custom.id))) }
        assertThrows(IllegalArgumentException::class.java) { engine.validateProfile(Profile(options = CalculationOptions(candleLightingNightfallId = "shaah-zmanis-gra"))) }
    }
    @Test fun irrelevantSeasonalOccurrencesAreSkippedWithoutDeletingRules() {
        val p = Profile(id = "p")
        val erev = JewishDate(5787, JewishDate.NISSAN, 14).localDate
        val event = Event(title = "Chametz reminder", anchorDate = erev.minusDays(1).toString(), timeKind = EventTimeKind.ZMAN,
            profileId = p.id, methodId = chametz, zoneId = place.zoneId, location = LocationBinding(savedId = place.id), recurrence = Recurrence(Frequency.DAILY))
        val data = AppData(profiles = listOf(p), places = listOf(place), events = listOf(event))
        assertEquals(listOf(erev), OccurrenceEngine().expand(event, erev.minusDays(1), erev.plusDays(2), data).map { it.date })
        assertEquals(1, data.events.size)
    }
    @Test fun scheduleIsInitializedOnceAndManualChoiceWins() {
        val initial = AppData(settings = AppSettings(location = LocationBinding(savedId = place.id)), places = listOf(place))
        assertNull(initializeSchedule(AppData()).settings.inIsrael)
        val israel = initializeSchedule(initial)
        assertEquals(true, israel.settings.inIsrael)
        val abroad = israel.copy(places = listOf(place.copy(zoneId = "America/New_York")))
        assertEquals(true, initializeSchedule(abroad).settings.inIsrael)
        assertEquals(false, initializeSchedule(initial.copy(settings = initial.settings.copy(inIsrael = false))).settings.inIsrael)
    }
    @Test fun categoryColorsFollowPhpPrecedence() {
        val day = JewishDate(5787, JewishDate.TISHREI, 1).localDate
        val model = CalendarEngine().day(day, AppSettings(inIsrael = true))
        assertEquals(if (day.dayOfWeek == DayOfWeek.SATURDAY) "shabbat" else "holiday", model.category)
        assertEquals(0xFFF7E7CCL, AppSettings().specialDayColor("holiday"))
        assertEquals(0xFF112233L, AppSettings(specialDayColors = mapOf("holiday" to 0xFF112233L)).specialDayColor("holiday"))
    }
    @Test fun timelineOrdersActualLocalTimesAndOmitsNotApplicable() {
        val date = LocalDate.of(2026, 11, 2)
        val p = Profile(selected = listOf("sea-level-sunrise", chametz, "shaah-zmanis-gra"))
        val rows = timelineRows((0L..1L).map { DayZmanim(date.plusDays(it), engine.calculate(CalculationQuery(date.plusDays(it), place, p))) }, place.zone())
        assertEquals(2, rows.count { it.zman == null })
        assertFalse(rows.any { it.zman?.id == chametz })
        assertEquals(2, rows.count { it.zman?.value is CalculationValue.Length })
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }
    @Test fun newOptionsRoundTripWithoutChangingOldProfiles() {
        val p = Profile(options = CalculationOptions(candleLightingNightfallId = "tzais72-minutes"))
        val data = AppData(settings = AppSettings(specialDayColors = mapOf("fast" to 0xFFAABBCCL)), profiles = listOf(p))
        val backup = BackupService()
        val restored = backup.merge(backup.preview(backup.export(data, BackupSelection()), AppData()), AppData(), ConflictAction.REPLACE, true)
        assertEquals(p.options, restored.profiles.single().options)
        assertEquals(data.settings.specialDayColors, restored.settings.specialDayColors)
    }
    @Test fun repeatedHourTapPreservesItsActualInstantInTheEventEditor() {
        val zone = ZoneId.of("America/New_York")
        val later = Instant.parse("2026-11-01T06:00:00Z")
        val start = EventStart.at(later, zone)
        assertEquals(LocalTime.of(1, 0), start.time)
        assertEquals(DstOverlapPolicy.LATER, start.overlapPolicy)
        val event = Event(title = "Repeated hour", anchorDate = start.date.toString(), localTime = start.time.toString(), zoneId = zone.id, overlapPolicy = start.overlapPolicy)
        assertEquals(later, OccurrenceEngine().occurrence(event, start.date, AppData()).start)
    }

    @Test fun candleDetailsShowTheEffectiveOffsetAndHolidayNightfallInputs() {
        val elevated = place.copy(elevation = 800.0)
        val p = Profile(selected = listOf("candle-lighting"), options = CalculationOptions(useElevation = true,
            candleLightingMinutes = 40.0, candleLightingNightfallId = "tzais72-minutes"))
        val friday = engine.calculate(CalculationQuery(LocalDate.of(2026, 11, 6), elevated, p)).single()
        assertEquals("40.0 minutes before sea-level sunset", friday.details.formula)
        assertEquals("Elevation does not affect this method", friday.details.effectiveElevation)
        val holiday = engine.calculate(CalculationQuery(JewishDate(5787, JewishDate.TISHREI, 1).localDate, elevated, p)).single()
        assertTrue(holiday.details.formula.contains("Nightfall 72 minutes"))
        assertEquals("800.0 m", holiday.details.effectiveElevation)
    }

}

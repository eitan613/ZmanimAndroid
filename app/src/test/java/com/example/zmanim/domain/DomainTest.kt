package com.example.zmanim.domain

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.example.zmanim.data.BackupService
import com.example.zmanim.data.BackupSelection
import java.time.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private val place = Place(id = "jerusalem", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
    private val engine = CalculationEngine()
    private val occurrences = OccurrenceEngine(engine)
    private val data = AppData(places = listOf(place), settings = AppSettings(location = LocationBinding(savedId = place.id)))
    private fun event(date: String, calendar: CalendarSystem = CalendarSystem.GREGORIAN, recurrence: Recurrence = Recurrence()) =
        Event(title = "Test", anchorDate = date, zoneId = place.zoneId, calendar = calendar, recurrence = recurrence)
    @Test fun catalogIdsAreUniqueAndNoMethodsArePreselected() {
        assertEquals(191, MethodCatalog.methods.size)
        assertEquals(191, MethodCatalog.byId.size)
        assertTrue(Profile().selected.isEmpty())
        assertTrue(MethodCatalog.methods.none { it.label.contains("get_") || it.label.startsWith("get") })
    }
    @Test fun temporalHourIsADurationAndDoesNotBecomeATimedOccurrence() {
        val value = engine.calculate(CalculationQuery(LocalDate.of(2026, 11, 1), place, Profile(), listOf("shaah-zmanis-gra"))).single().value
        assertTrue(value is CalculationValue.Length)
    }
    @Test fun fixedOffsetsAndTemporalOffsetsUseExactInstants() {
        val custom = CustomZman(name = "Evening", kind = FormulaKind.FIXED_MINUTES, amount = 20.0)
        val temporal = CustomZman(name = "Temporal", kind = FormulaKind.TEMPORAL_HOURS, amount = 1.0, base = "sea-level-sunrise")
        val profile = Profile(custom = listOf(custom, temporal))
        val results = engine.calculate(CalculationQuery(LocalDate.of(2026, 11, 1), place, profile, listOf("sea-level-sunset", custom.id, "sea-level-sunrise", temporal.id)))
        val times = results.map { (it.value as CalculationValue.Time).instant }
        assertEquals(Duration.ofMinutes(20), Duration.between(times[0], times[1]))
        assertEquals(Duration.between(times[2], times[0]).toMillis() / 12.0, Duration.between(times[2], times[3]).toMillis().toDouble(), 1.0)
    }
    @Test fun cyclesAreRejectedAndUnavailableDependenciesPropagate() {
        val a = CustomZman(id = "a", name = "A", kind = FormulaKind.FIXED_MINUTES, base = "b")
        val b = a.copy(id = "b", name = "B", base = "a")
        assertThrows(IllegalArgumentException::class.java) { engine.validateProfile(Profile(custom = listOf(a, b))) }
        val polar = place.copy(latitude = 69.6492, longitude = 18.9553, zoneId = "Europe/Oslo")
        val custom = a.copy(base = "sea-level-sunset")
        val result = engine.calculate(CalculationQuery(LocalDate.of(2026, 6, 21), polar, Profile(custom = listOf(custom)), listOf(custom.id))).single()
        assertTrue(result.value is CalculationValue.Unavailable)
    }
    @Test fun negativeElevationIsNotSilentlyClamped() {
        val profile = Profile(options = CalculationOptions(useElevation = true))
        val result = engine.calculate(CalculationQuery(LocalDate.of(2026, 11, 1), place.copy(elevation = -400.0), profile, listOf("sunrise"))).single()
        assertTrue(result.value is CalculationValue.Unavailable)
    }
    @Test fun monthHeadersIncludeThreeOverlappingSecondaryMonths() {
        val settings = AppSettings(primaryCalendar = CalendarSystem.GREGORIAN)
        val found = (2020..2040).asSequence().flatMap { y -> (1..12).asSequence().map { m -> CalendarEngine().month(LocalDate.of(y, m, 15), settings, Locale.ENGLISH) } }.first { it.secondary.size == 3 }
        assertEquals(3, found.secondary.size)
        assertEquals(1, found.secondary.count { it.selected })
    }
    @Test fun sunsetRolloverDoesNotMoveCivilAppointments() {
        val day = LocalDate.of(2026, 11, 1)
        val sunset = engine.seaLevelSunset(day, place)!!
        assertEquals(day, CalendarEngine().currentHebrewDate(sunset.minusSeconds(1), place, true).first)
        assertEquals(day.plusDays(1), CalendarEngine().currentHebrewDate(sunset, place, true).first)
        val occurrence = occurrences.occurrence(event(day.toString()), day, data)
        assertEquals(day, occurrence.start!!.atZone(place.zone()).toLocalDate())
    }
    @Test fun ordinaryAdarPolicyCanProduceBothLeapYearAdars() {
        val anchor = JewishDate(5785, JewishDate.ADAR, 14).localDate
        val e = event(anchor.toString(), CalendarSystem.HEBREW, Recurrence(Frequency.YEARLY, missingDay = MissingDayPolicy.SKIP, ordinaryAdar = AdarPolicy.BOTH))
        val dates = occurrences.dates(e, JewishDate(5787, 7, 1).localDate, JewishDate(5788, 7, 1).localDate)
        assertEquals(listOf(12, 13), dates.map { JewishDate(it).jewishMonth })
        assertTrue(dates.all { JewishDate(it).jewishDayOfMonth == 14 })
    }
    @Test fun missingDayPolicyPreservesOriginalAnchor() {
        val e = event("2026-01-31", recurrence = Recurrence(Frequency.MONTHLY, missingDay = MissingDayPolicy.PREVIOUS))
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), occurrences.dates(e, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)).map { it.toString() })
    }
    @Test fun recurrenceCountIsAppliedBeforeExclusions() {
        val e = event("2026-01-01", recurrence = Recurrence(Frequency.DAILY, count = 3)).copy(excludedDates = setOf("2026-01-02"))
        assertEquals(listOf("2026-01-01", "2026-01-03"), occurrences.dates(e, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)).map { it.toString() })
    }
    @Test fun weekdayRelativeEventsRecalculateEachDate() {
        val p = Profile(id = "p")
        val d = data.copy(profiles = listOf(p))
        val e = event("2026-11-01", recurrence = Recurrence(Frequency.DAILY, weekdays = setOf(1, 2, 3, 4, 5))).copy(timeKind = EventTimeKind.ZMAN,
            profileId = p.id, methodId = "sea-level-sunset", offsetMinutes = -15.0, location = LocationBinding(savedId = place.id))
        val values = occurrences.expand(e, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), d)
        assertEquals(5, values.size)
        values.forEach { assertEquals(Duration.ofMinutes(15), Duration.between(it.start, engine.seaLevelSunset(it.date, place))) }
    }
    @Test fun allDayHebrewSunsetEventsCoverThePreviousEvening() {
        val d = data.copy(settings = data.settings.copy(hebrewDayBoundary = HebrewDayBoundary.SUNSET))
        val e = event("2026-11-01", CalendarSystem.HEBREW).copy(timeKind = EventTimeKind.ALL_DAY, location = LocationBinding(savedId = place.id))
        val result = occurrences.occurrence(e, LocalDate.of(2026, 11, 1), d)
        assertFalse(result.allDay)
        assertEquals(engine.seaLevelSunset(LocalDate.of(2026, 10, 31), place), result.start)
        assertEquals(engine.seaLevelSunset(LocalDate.of(2026, 11, 1), place), result.end)
    }
    @Test fun clockChangeIntervalsUseActualLocalSegments() {
        val e = event("2026-03-08").copy(zoneId = "America/New_York", localTime = "02:30", durationMinutes = 15)
        assertTrue(occurrences.expand(e, LocalDate.parse(e.anchorDate), LocalDate.parse(e.anchorDate).plusDays(1), data).isEmpty())
        val partial = e.copy(durationMinutes = 60)
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), occurrences.occurrence(partial, LocalDate.parse(e.anchorDate), data).start)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), occurrences.occurrence(partial, LocalDate.parse(e.anchorDate), data).end)
        val overlap = e.copy(anchorDate = "2026-11-01", localTime = "01:30")
        val segments = occurrences.expand(overlap, LocalDate.parse(overlap.anchorDate), LocalDate.parse(overlap.anchorDate).plusDays(1), data)
        assertEquals(2, segments.size)
        assertEquals(Duration.ofHours(1), Duration.between(segments[0].start, segments[1].start))
    }
    @Test fun roundingIsPresentationOnlyAndMarksItsDirection() {
        val instant = Instant.parse("2026-11-01T08:00:40Z")
        assertTrue(TimeFormatting.format(instant, place, AppSettings(), true, Locale.ENGLISH).endsWith(" +"))
        assertTrue(TimeFormatting.format(instant, place, AppSettings(rounding = Rounding.DOWN), true, Locale.ENGLISH).endsWith(" −"))
        assertEquals(Instant.parse("2026-11-01T08:00:40Z"), instant)
    }
    @Test fun calculationSharingExcludesPrivateDataAndImportsWithoutActivatingReminders() {
        val original = data.copy(profiles = listOf(Profile(id = "p", selected = listOf("sea-level-sunset"))), events = listOf(event("2026-11-01")))
        val service = BackupService()
        val text = service.export(original, BackupSelection())
        val preview = service.preview(text, AppData())
        assertTrue(preview.document.places.isEmpty()); assertTrue(preview.document.events.isEmpty())
        val merged = service.merge(preview, AppData(), ConflictAction.KEEP, true)
        assertFalse(merged.profiles.single().remindersEnabled)
    }
    @Test fun missingHebrewDayThirtyDoesNotChangeTheOriginalRecurrenceAnchor() {
        val anchor = JewishDate(5784, 12, 30).localDate
        val rule = Recurrence(Frequency.YEARLY, missingDay = MissingDayPolicy.NEXT, leapAdar = LeapAdarPolicy.ADAR)
        val e = event(anchor.toString(), CalendarSystem.HEBREW, rule)
        val dates = occurrences.dates(e, JewishDate(5785, 7, 1).localDate, JewishDate(5788, 7, 1).localDate)
        assertEquals(1, JewishDate(dates.first()).jewishMonth)
        assertEquals(1, JewishDate(dates.first()).jewishDayOfMonth)
        val leap = dates.map(::JewishDate).first { it.jewishYear == 5787 }
        assertEquals(12, leap.jewishMonth); assertEquals(30, leap.jewishDayOfMonth)
    }
    @Test fun februaryTwentyNineCanSkipWithoutDriftingToFebruaryTwentyEight() {
        val e = event("2024-02-29", recurrence = Recurrence(Frequency.YEARLY, missingDay = MissingDayPolicy.SKIP))
        assertEquals(listOf("2024-02-29", "2028-02-29"), occurrences.dates(e, LocalDate.of(2024, 1, 1), LocalDate.of(2029, 1, 1)).map { it.toString() })
    }
    @Test fun degreeCalculationsDoNotRequireAnElevationAndAreNotFixedOffsets() {
        val z = CustomZman(name = "Angle", kind = FormulaKind.SOLAR_ANGLE, amount = 18.0)
        val p = Profile(options = CalculationOptions(useElevation = true), custom = listOf(z))
        fun distance(date: LocalDate): Duration {
            val values = engine.calculate(CalculationQuery(date, place, p, listOf(z.id, "sea-level-sunset")))
            return Duration.between((values[1].value as CalculationValue.Time).instant, (values[0].value as CalculationValue.Time).instant)
        }
        assertNotEquals(distance(LocalDate.of(2026, 1, 1)), distance(LocalDate.of(2026, 7, 1)))
        assertTrue(engine.calculate(CalculationQuery(LocalDate.of(2026, 1, 1), place, p, listOf("alos18-degrees"))).single().value is CalculationValue.Time)
    }
    @Test fun israelAndDiasporaHolidayAndParshaSchedulesStayExplicit() {
        val date = JewishDate(5786, JewishDate.SIVAN, 7).localDate
        val israel = CalendarEngine().day(date, AppSettings(inIsrael = true))
        val diaspora = CalendarEngine().day(date, AppSettings(inIsrael = false))
        assertNotEquals(israel.holiday, diaspora.holiday)
        assertNotEquals(israel.parsha, diaspora.parsha)
        val unset = CalendarEngine().day(date, AppSettings())
        assertEquals(diaspora.holiday, unset.holiday); assertEquals(diaspora.parsha, unset.parsha)
    }
    @Test fun namedShemaMethodsExplainTheirDayBoundaries() {
        val methods = MethodCatalog.methods.filter { it.group == "shema" && it.id.contains("mga") && !it.id.contains("fixed") }
        assertTrue(methods.isNotEmpty())
        assertTrue(methods.all { it.label.contains("Day:") })
    }
    @Test fun importCopiesRemapProfileAndLocationReferencesWithoutEnablingReminders() {
        val profile = Profile(id = "p")
        val event = event("2026-11-01").copy(profileId = profile.id, location = LocationBinding(savedId = place.id), reminders = listOf(ReminderRule(minutesBefore = 5)))
        val existing = data.copy(profiles = listOf(profile), events = listOf(event))
        val service = BackupService()
        val preview = service.preview(service.export(existing, BackupSelection(events = true, places = true)), existing)
        val merged = service.merge(preview, existing, ConflictAction.COPY, false)
        val imported = merged.events.first { it.id != event.id }
        assertNotEquals(profile.id, imported.profileId); assertNotEquals(place.id, imported.location.savedId)
        assertEquals(5, imported.reminders.single().minutesBefore); assertFalse(imported.remindersEnabled)
    }

    @Test fun extremeCustomOffsetsAreUnavailableRatherThanSilentlyClamped() {
        val formula = CustomZman(name = "Too far", kind = FormulaKind.FIXED_MINUTES, amount = 1e100)
        val result = engine.calculate(CalculationQuery(LocalDate.of(2026, 11, 1), place, Profile(custom = listOf(formula)), listOf(formula.id))).single()
        assertTrue(result.value is CalculationValue.Unavailable)
    }

}

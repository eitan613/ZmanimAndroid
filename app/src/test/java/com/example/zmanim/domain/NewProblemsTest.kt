package com.example.zmanim.domain

import com.example.zmanim.data.*
import com.kosherjava.zmanim.ComprehensiveZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class NewProblemsTest {
    private val day = LocalDate.of(2026, 11, 6)
    private val place = Place(id = "p", name = "Hill", latitude = 31.8645075, longitude = 35.1447088, zoneId = "Asia/Jerusalem", elevation = 770.0)
    @Test fun timezoneResolutionRequiresAnExactZoneIdNotAnOffset() {
        assertEquals("Asia/Jerusalem", resolveDeviceZone(listOf("Asia/Hebron", "Asia/Jerusalem"), "Asia/Jerusalem"))
        assertNull(resolveDeviceZone(listOf("Asia/Hebron", "Asia/Jerusalem"), "Europe/Athens"))
        assertNull(resolveDeviceZone(emptyList(), "Asia/Jerusalem"))
        assertEquals("Europe/London", resolveDeviceZone(listOf("Europe/London"), "Asia/Jerusalem"))
    }
    @Test fun manualTimezoneChoiceIsLocalToTheAcquiredRegion() {
        val previous = place.copy(zoneUserSelected = true)
        val nearby = place.copy(zoneId = "Asia/Hebron", zoneConfirmed = false, zoneCandidates = listOf("Asia/Jerusalem", "Asia/Hebron"))
        assertEquals("Asia/Jerusalem", retainNearbyZoneChoice(nearby, previous).zoneId)
        val travelled = nearby.copy(latitude = 40.7, longitude = -74.0, zoneId = "America/New_York", zoneCandidates = listOf("America/New_York"))
        assertEquals("America/New_York", retainNearbyZoneChoice(travelled, previous).zoneId)
    }
    @Test fun cityAliasesHandleMarksApostrophesAndRepeatedVowels() {
        val aliases = normalizeCityQuery("Givat Zeev,גִּבְעַת זְאֵב")
        for (query in listOf("Givat Zev", "Givat Zeev", "Giv'at Ze'ev", "Giv’at Ze’ev", "גבעת זאב"))
            assertTrue(query, cityMatchRank(normalizeCityQuery(query), "Givat Zeev", aliases) < 5)
        assertEquals(0, cityMatchRank("jerusalem", "Jerusalem", "Jerusalem"))
        assertTrue(cityMatchRank("jerusalem", "Jerusalem", "Jerusalem") < cityMatchRank("jerusalem", "New Jerusalem", "New Jerusalem"))
    }
    @Test fun explicitElevationDoesNotDependOnProfileToggleAndSeaLevelStaysIndependent() {
        for (enabled in listOf(false, true)) {
            val profile = Profile(options = CalculationOptions(useElevation = enabled))
            val actual = CalculationEngine().calculate(CalculationQuery(day, place, profile,
                listOf("sunrise", "sunset", "sea-level-sunrise", "sea-level-sunset", "sof-zman-shma-gra", "alos18-degrees"))).associate { it.id to it.value }
            val library = ComprehensiveZmanimCalendar(GeoLocation(place.name, place.latitude, place.longitude, 770.0, place.zone())).apply { localDate = day; isUseElevation = enabled }
            assertEquals(CalculationValue.Time(library.sunrise!!), actual["sunrise"])
            assertEquals(CalculationValue.Time(library.sunset!!), actual["sunset"])
            assertEquals(CalculationValue.Time(library.seaLevelSunrise!!), actual["sea-level-sunrise"])
            assertEquals(CalculationValue.Time(library.seaLevelSunset!!), actual["sea-level-sunset"])
            assertEquals(CalculationValue.Time(library.sofZmanShmaGRA!!), actual["sof-zman-shma-gra"])
            assertEquals(CalculationValue.Time(library.alos18Degrees!!), actual["alos18-degrees"])
            assertNotEquals(actual["sunrise"], actual["sea-level-sunrise"])
        }
    }
    @Test fun missingNegativeAndZeroHeightsStayDistinctThroughDependencies() {
        val custom = CustomZman(id = "custom", name = "Adjusted + 5", kind = FormulaKind.FIXED_MINUTES, base = "sunrise", amount = 5.0)
        val p = Profile(custom = listOf(custom))
        for (height in listOf(null, -400.0)) {
            val values = CalculationEngine().calculate(CalculationQuery(day, place.copy(elevation = height), p,
                listOf("sunrise", custom.id, "sea-level-sunrise", "alos18-degrees"))).map { it.value }
            assertTrue(values[0] is CalculationValue.Unavailable); assertTrue(values[1] is CalculationValue.Unavailable)
            assertTrue(values[2] is CalculationValue.Time); assertTrue(values[3] is CalculationValue.Time)
        }
        val zero = CalculationEngine().calculate(CalculationQuery(day, place.copy(elevation = 0.0), p, listOf("sunrise", "sea-level-sunrise")))
        assertEquals(zero[0].value, zero[1].value)
    }
    @Test fun ateretDependenciesIncludeUnselectedEventMethodsAndNestedFormulas() {
        val custom = CustomZman(id = "custom", name = "After Ateret", kind = FormulaKind.FIXED_MINUTES, base = "tzais-ateret-torah")
        val p = Profile(id = "profile", custom = listOf(custom))
        assertFalse(p.usesAteretTorah(AppData()))
        val event = Event(title = "Ateret event", anchorDate = day.toString(), zoneId = place.zoneId, timeKind = EventTimeKind.ZMAN, profileId = p.id, methodId = custom.id)
        assertTrue(p.usesAteretTorah(AppData(events = listOf(event))))
        assertTrue(p.copy(selected = listOf(custom.id)).usesAteretTorah(AppData()))
    }
    @Test fun appLocationAndExplicitEventLocationRemainIndependent() {
        val other = place.copy(id = "other", latitude = 40.7, longitude = -74.0, zoneId = "America/New_York")
        val data = AppData(settings = AppSettings(location = LocationBinding(savedId = place.id)), places = listOf(place, other))
        assertEquals(place, data.place(LocationBinding()))
        val travelled = data.copy(settings = data.settings.copy(location = LocationBinding(savedId = other.id)))
        assertEquals(other, travelled.place(LocationBinding()))
        assertEquals(place, travelled.place(LocationBinding(savedId = place.id)))
        assertNull(data.copy(places = listOf(place.copy(zoneConfirmed = false))).place())
    }
    @Test fun holidayInitializationDoesNotInferFromTimezoneOrGps() {
        val data = AppData(settings = AppSettings(location = LocationBinding(savedId = place.id)), places = listOf(place))
        assertNull(initializeSchedule(data).settings.inIsrael)
        assertTrue(initializeSchedule(data.copy(places = listOf(place.copy(holidayInIsrael = true)))).settings.inIsrael == true)
        assertFalse(initializeSchedule(data.copy(places = listOf(place.copy(holidayInIsrael = false)))).settings.inIsrael!!)
    }
    @Test fun namesAndAlarmSettingsRoundTripWithoutChangingCalculations() {
        val p = Profile(id = "profile", selected = listOf("sea-level-sunrise"), displayNames = mapOf("sea-level-sunrise" to "My sunrise · הנץ"),
            reminders = listOf(ZmanReminder("sea-level-sunrise", listOf(ReminderRule(mode = ReminderMode.ALARM, snoozeMinutes = 9, vibrate = false, sound = "content://test/sound")))))
        val data = AppData(profiles = listOf(p))
        val service = BackupService()
        val restored = service.merge(service.preview(service.export(data, BackupSelection()), AppData()), AppData(), ConflictAction.REPLACE, false).profiles.single()
        assertEquals(p.displayNames, restored.displayNames); assertEquals(p.reminders, restored.reminders)
        assertEquals("My sunrise · הנץ", methodDisplayName("sea-level-sunrise", p, true))
        assertEquals(CalculationEngine().calculate(CalculationQuery(day, place, p)).single().value,
            CalculationEngine().calculate(CalculationQuery(day, place, p.copy(displayNames = emptyMap()))).single().value)
    }
    @Test fun automaticDirectionAndControlledSaturdayLabelsIgnoreInterfaceLanguage() {
        assertFalse(AppSettings(primaryCalendar = CalendarSystem.GREGORIAN).calendarRtl())
        assertTrue(AppSettings(primaryCalendar = CalendarSystem.HEBREW).calendarRtl())
        assertFalse(AppSettings(calendarDirection = CalendarDirection.LTR).calendarRtl())
        assertTrue(AppSettings(calendarDirection = CalendarDirection.RTL, primaryCalendar = CalendarSystem.GREGORIAN).calendarRtl())
        assertEquals("שבת", weekdayLabel(DayOfWeek.SATURDAY, true, Locale.ENGLISH))
        assertTrue(methodDisplayName("temporal-hour", null, true).contains("שעה זמנית"))
        assertTrue(methodDisplayName("tzais-geonim8-point5-degrees", null, true).contains("גאונים"))
    }
    @Test fun localIntervalsCoverSkippedRepeatedSpanningAndHalfHourTransitions() {
        val ny = ZoneId.of("America/New_York")
        fun segments(start: String, minutes: Long, zone: ZoneId = ny) = LocalDateTime.parse(start).let { localIntervalSegments(it, it.plusMinutes(minutes), zone) }
        assertTrue(segments("2026-03-08T02:15", 20).isEmpty())
        assertEquals(listOf(Instant.parse("2026-03-08T07:00:00Z") to Instant.parse("2026-03-08T07:30:00Z")), segments("2026-03-08T02:30",60))
        assertEquals(Duration.ofHours(1), segments("2026-03-08T01:30",120).single().let { Duration.between(it.first,it.second) })
        assertEquals(2, segments("2026-11-01T01:15",15).size)
        assertEquals(Duration.ofHours(4), segments("2026-11-01T00:30",180).single().let { Duration.between(it.first,it.second) })
        assertEquals(Duration.ofMinutes(90), segments("2026-10-04T01:30",120,ZoneId.of("Australia/Lord_Howe")).single().let { Duration.between(it.first,it.second) })
        assertTrue(segments("2011-12-30T00:00",1440,ZoneId.of("Pacific/Apia")).isEmpty())
    }
    @Test fun countdownsIncludeSecondsHoursAndDaysIndependentlyOfClockSettings() {
        val now = Instant.EPOCH
        assertEquals("1h 1m 1s", TimeFormatting.countdown(now,now.plusSeconds(3661)))
        assertEquals("1d 1h 1m 1s", TimeFormatting.countdown(now,now.plusSeconds(90061)))
        assertEquals("0m 0s", TimeFormatting.countdown(now,now.minusSeconds(1)))
    }
}

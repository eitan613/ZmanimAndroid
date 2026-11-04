package com.example.zmanim.widgets

import com.example.zmanim.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class WidgetRolloverTest {
    private val place = Place(id = "jerusalem", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
    private val profile = Profile(id = "profile", selected = listOf("sea-level-sunrise", "sea-level-sunset"))
    private val config = WidgetConfig("1", WidgetKind.ZMANIM, sunsetRollover = false)
    private val data = AppData(profiles = listOf(profile), places = listOf(place), settings = AppSettings(activeProfileId = profile.id, location = LocationBinding(savedId = place.id)))
    private val morning = LocalDate.of(2026, 11, 4).atStartOfDay(place.zone()).toInstant()

    @Test fun passedRowDisappearsSecondBecomesFirstAndBottomIsRefilled() {
        val before = upcomingWidgetZmanim(data, profile, place, morning).take(3)
        assertEquals(3, before.size)
        val boundary = (before.first().value as CalculationValue.Time).instant
        val after = upcomingWidgetZmanim(data, profile, place, boundary).take(3)
        assertEquals(3, after.size)
        assertEquals(before.drop(1), after.take(2))
        assertTrue(after.all { (it.value as CalculationValue.Time).instant > boundary })
        assertTrue((after.last().value as CalculationValue.Time).instant > (before.last().value as CalculationValue.Time).instant)
    }

    @Test fun refreshBoundaryTracksTheNextRemainingZman() {
        val rows = upcomingWidgetZmanim(data, profile, place, morning)
        val first = (rows[0].value as CalculationValue.Time).instant
        val second = (rows[1].value as CalculationValue.Time).instant
        assertEquals(first, nextWidgetBoundary(data, config, morning))
        assertEquals(second, nextWidgetBoundary(data, config, first))
    }

    @Test fun lastZmanRollsIntoTomorrowAndMidnightKeepsRefreshScheduled() {
        val todayRows = upcomingWidgetZmanim(data, profile, place, morning).filter { (it.value as CalculationValue.Time).instant.atZone(place.zone()).toLocalDate() == morning.atZone(place.zone()).toLocalDate() }
        val last = (todayRows.last().value as CalculationValue.Time).instant
        val tomorrow = morning.atZone(place.zone()).toLocalDate().plusDays(1)
        assertEquals(tomorrow, (upcomingWidgetZmanim(data, profile, place, last).first().value as CalculationValue.Time).instant.atZone(place.zone()).toLocalDate())
        assertEquals(tomorrow.atStartOfDay(place.zone()).toInstant(), nextWidgetBoundary(data, config, last))
    }

    @Test fun hiddenGroupsDoNotAppearOrTriggerRollover() {
        val filtered = profile.copy(hiddenGroups = setOf("sunrise"))
        val hiddenData = data.copy(profiles = listOf(filtered))
        val rows = upcomingWidgetZmanim(hiddenData, filtered, place, morning)
        assertTrue(rows.none { it.id == "sea-level-sunrise" })
        assertEquals((rows.first().value as CalculationValue.Time).instant, nextWidgetBoundary(hiddenData, config, morning))
    }
}

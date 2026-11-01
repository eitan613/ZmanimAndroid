package com.example.zmanim

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zmanim.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZmanimScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun seed() { runBlocking {
        val app = compose.activity.application as ZmanimApplication
        app.awaitData()
        val place = Place(id = "test-place", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        val p = Profile(id = "test-profile")
        app.repository.update { AppData(settings = AppSettings(onboardingComplete = true, migratedPrototype = true,
            inIsrael = true, activeProfileId = p.id, language = Language.ENGLISH, location = LocationBinding(savedId = place.id)),
            profiles = listOf(p), places = listOf(place)) }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Menu ▾").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("Your calendar and zmanim").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun pickerStartsEmptyAndPersistsAnExplicitMethod() {
        compose.onNodeWithText("Zmanim", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Choose which zmanim you want to show. No calculation methods are selected for you.").assertIsDisplayed()
        compose.onNodeWithText("Choose zmanim").performClick()
        compose.onNodeWithText("Alos · dawn").performClick()
        compose.onNodeWithText("Sunrise", substring = false).performClick()
        compose.onNodeWithText("Sunrise · sea level").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) {
            val app = compose.activity.application as ZmanimApplication
            app.repository.data.profile()?.selected == listOf("sea-level-sunrise")
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        val app = compose.activity.application as ZmanimApplication
        org.junit.Assert.assertEquals(listOf("sea-level-sunrise"), app.repository.data.profile()!!.selected)
    }
    @Test fun localEventCanBeCreatedAndDeleted() {
        compose.onNodeWithText("Menu ▾").performClick()
        compose.onNodeWithText("New event").performClick()
        compose.onNodeWithText("Title", substring = false).performTextInput("Dentist appointment")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { (compose.activity.application as ZmanimApplication).repository.data.events.any { it.title == "Dentist appointment" } }
        compose.onNodeWithTag("calendarList").performScrollToNode(hasText("Dentist appointment"))
        compose.onNodeWithText("Dentist appointment").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.onNodeWithTag("confirmationAction").performClick()
        compose.waitUntil(10000) { (compose.activity.application as ZmanimApplication).repository.data.events.isEmpty() }
    }
    @Test fun civilDateNavigationHasVisibleAlternativesToGestures() {
        compose.onNodeWithText("Next", substring = false).performClick()
        compose.onNodeWithText("Today", substring = false).performClick()
        compose.onNodeWithText("Menu ▾").performClick()
        compose.onNode(hasText("Go to date", substring = false) and hasClickAction()).performClick()
        compose.onNodeWithText("Date (YYYY-MM-DD)").performTextReplacement("2026-09-12")
        compose.onNode(hasText("Go to date", substring = false) and hasClickAction()).performClick()
        compose.onNodeWithText("1 Tishrei, 5787").assertIsDisplayed()
    }
    @Test fun dayAndWeekViewsRenderOverlappingEventsAndRemainNavigable() {
        val app = compose.activity.application as ZmanimApplication
        runBlocking {
            val date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jerusalem")).toString()
            app.repository.update { data -> data.copy(events = listOf(
                Event(title = "Overlap one", anchorDate = date, localTime = "11:00", zoneId = "Asia/Jerusalem"),
                Event(title = "Overlap two", anchorDate = date, localTime = "11:30", zoneId = "Asia/Jerusalem"))) }
        }
        compose.onNodeWithText("Week", substring = false).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("hourSchedule").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("hourSchedule").assertExists()
        compose.onNodeWithText("Day", substring = false).performClick()
        compose.onNodeWithTag("hourSchedule").assertExists()
        compose.onNodeWithText("Month", substring = false).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("hourSchedule").fetchSemanticsNodes().isEmpty() }
    }

}

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
            inIsrael = true, activeProfileId = p.id, language = Language.ENGLISH, labels = LabelLanguage.TRANSLITERATED, primaryCalendar = CalendarSystem.GREGORIAN, location = LocationBinding(savedId = place.id)),
            profiles = listOf(p), places = listOf(place)) }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("overflowMenu").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun pickerStartsEmptyAndPersistsAnExplicitMethod() {
        compose.onNodeWithText("Zmanim", useUnmergedTree = false).performClick()
        compose.onNodeWithText("Choose which zmanim you want to show. No calculation methods are selected for you.").assertIsDisplayed()
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithTag("menu:Choose zmanim").performClick()
        compose.onNodeWithText("Alos · dawn").performClick()
        compose.onNodeWithText("Sunrise", substring = false).performClick()
        compose.onNodeWithText("Sunrise · sea level").performClick()
        compose.onAllNodesWithText("Save").assertCountEquals(0)
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
        compose.onNodeWithTag("overflowMenu").performClick()
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
    @Test fun calendarNavigationUsesMenuAndKeepsBottomDestinationsReachable() {
        compose.onAllNodesWithText("Next", substring = false).assertCountEquals(0)
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithText("Today", substring = false).performClick()
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNode(hasText("Go to date", substring = false) and hasClickAction()).performClick()
        choosePickerDate("2026-09-12")
        compose.onNodeWithText("Hebrew", substring = false).performClick()
        compose.onNodeWithText("Zmanim", substring = false).performClick()
        compose.onNodeWithText("Gregorian", substring = false).performClick()
        compose.onNodeWithTag("overflowMenu").assertIsDisplayed()
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
        compose.onNodeWithText("Hebrew", substring = false).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("hourSchedule").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun weekFitsSevenDaysAndDoubleTapPrefillsDateAndTime() {
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithText("Go to date").performClick()
        choosePickerDate("2026-11-02")
        compose.onNodeWithText("Week").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("hourSchedule").fetchSemanticsNodes().isNotEmpty() }
        val header = compose.onNodeWithTag("hourSchedule").fetchSemanticsNode().boundsInRoot
        (1..7).forEach { day ->
            val bounds = compose.onNodeWithTag("weekday:2026-11-0$day").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue(bounds.left >= header.left && bounds.right <= header.right)
            org.junit.Assert.assertTrue(bounds.width > 0)
        }
        compose.onNodeWithTag("calendarList").performScrollToNode(hasTestTag("slot:2026-11-02:10"))
        compose.onNodeWithTag("slot:2026-11-02:10").performTouchInput { doubleClick() }
        compose.onNodeWithText("10:00 AM").assertExists()
        compose.onNodeWithText("Title", substring = false).performTextInput("Tapped hour")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { (compose.activity.application as ZmanimApplication).repository.data.events.any { it.title == "Tapped hour" } }
        val saved = (compose.activity.application as ZmanimApplication).repository.data.events.single { it.title == "Tapped hour" }
        org.junit.Assert.assertEquals("2026-11-02", saved.anchorDate)
        org.junit.Assert.assertEquals("10:00", saved.localTime)
    }

    @Test fun freshCalendarDoesNotBlockOnOnboardingOrPermissions() {
        val app = compose.activity.application as ZmanimApplication
        runBlocking { app.repository.update { AppData() } }
        compose.waitForIdle()
        compose.onAllNodesWithText("Your calendar and zmanim").assertCountEquals(0)
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithTag("hourSchedule").assertExists()
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Permissions").performClick()
        compose.onNodeWithTag("settingsList").assertIsDisplayed()
    }

    @Test fun customZmanCanBeCreatedDirectlyAndIsSelectedOnSave() {
        compose.onNodeWithText("Zmanim", substring = false).performClick()
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithTag("menu:Custom zmanim").performClick()
        compose.onNodeWithText("Create custom zman").performClick()
        compose.onNodeWithText("Name", substring = false).performTextInput("After sunset")
        compose.onNodeWithText("Minutes (+ after, − before)").performTextReplacement("12")
        compose.onNodeWithText("Save").performClick()
        val app = compose.activity.application as ZmanimApplication
        compose.waitUntil(10000) { app.repository.data.profile()?.custom?.any { it.name == "After sunset" } == true }
        val profile = app.repository.data.profile()!!
        org.junit.Assert.assertTrue(profile.custom.single { it.name == "After sunset" }.id in profile.selected)
    }

    @Test fun backToNowCentersNextZmanAndTimelineLoadsBothDirections() {
        val app = compose.activity.application as ZmanimApplication
        runBlocking { app.repository.updateProfile("test-profile") { it.copy(selected = listOf("sea-level-sunrise", "chatzos-hayom", "sea-level-sunset", "tzais72-minutes")) } }
        compose.onNode(hasText("Zmanim", substring = false) and hasClickAction()).performClick()
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[com.example.zmanim.ui.AppViewModel::class.java]
        compose.waitUntil(10000) { compose.onAllNodesWithTag("nextZman").fetchSemanticsNodes().isNotEmpty() }
        val timeline = compose.onNodeWithTag("zmanimTimeline")
        val before = model.screen.value!!.timeline.first().date
        timeline.performScrollToIndex(0)
        timeline.performTouchInput { swipeDown() }
        compose.waitUntil(10000) { model.screen.value!!.timeline.first().date < before }
        val last = model.screen.value!!.timeline.last().date
        timeline.performScrollToIndex(com.example.zmanim.ui.timelineRows(model.screen.value!!.timeline, app.repository.data.place()!!.zone()).lastIndex)
        timeline.performTouchInput { swipeUp() }
        compose.waitUntil(10000) { model.screen.value!!.timeline.last().date > last }
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithTag("menu:Back to now").performClick()
        compose.waitUntil(10000) {
            val item = compose.onAllNodesWithTag("nextZman").fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            val viewport = timeline.fetchSemanticsNode().boundsInRoot
            item != null && kotlin.math.abs(item.center.y - viewport.center.y) < viewport.height * 0.18f
        }
    }

    @Test fun currentLocationContinuesAfterPermissionAndCreatesASnapshot() {
        val app = compose.activity.application as ZmanimApplication
        compose.onNodeWithText("Zmanim", substring = false).performClick()
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithTag("menu:Location").performClick()
        compose.onNodeWithText("Use current location snapshot", substring = false).performClick()
        if (!app.location.hasAccess()) {
            val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            compose.waitUntil(10000) {
                automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Precise")?.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                val button = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("While using the app")?.firstOrNull()
                button?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                app.location.hasAccess()
            }
        }
        compose.waitUntil(35000) { app.repository.data.devicePlace != null }
        val current = app.repository.data
        org.junit.Assert.assertEquals(LocationMode.SAVED, current.settings.location.mode)
        org.junit.Assert.assertEquals("Current location", current.place()!!.name)
        org.junit.Assert.assertEquals(current.devicePlace!!.zoneId, current.place()!!.zoneId)
        org.junit.Assert.assertEquals(current.devicePlace!!.latitude, current.place()!!.latitude, 0.0)
        org.junit.Assert.assertEquals(current.devicePlace!!.longitude, current.place()!!.longitude, 0.0)
        org.junit.Assert.assertNotEquals(current.devicePlace!!.id, current.place()!!.id)
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[com.example.zmanim.ui.AppViewModel::class.java]
        compose.waitUntil(5000) { !model.locationBusy.value }
        org.junit.Assert.assertTrue(model.useDeviceLocation(false))
        compose.waitUntil(10000) { !model.locationBusy.value }
        org.junit.Assert.assertEquals(1, app.repository.data.places.count { it.id == com.example.zmanim.data.AppRepository.SNAPSHOT_ID })
        org.junit.Assert.assertTrue(app.repository.data.places.any { it.id == "test-place" })
        org.junit.Assert.assertTrue(model.useDeviceLocation(true))
        compose.waitUntil(10000) { app.repository.data.settings.location.mode == LocationMode.FOLLOW }
        model.stopFollowing()
        compose.waitUntil(10000) { app.repository.data.settings.location.mode == LocationMode.SAVED }
        org.junit.Assert.assertEquals(1, app.repository.data.places.count { it.id == com.example.zmanim.data.AppRepository.SNAPSHOT_ID })
    }

    @Test fun goToDateInZmanimShowsTheRequestedDay() {
        val app = compose.activity.application as ZmanimApplication
        runBlocking { app.repository.updateProfile("test-profile") { it.copy(selected = listOf("sea-level-sunrise", "sea-level-sunset")) } }
        compose.onNode(hasText("Zmanim", substring = false) and hasClickAction()).performClick()
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithTag("menu:Go to date").performClick()
        choosePickerDate("2027-01-03")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("dayHeader:2027-01-03").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dayHeader:2027-01-03").assertIsDisplayed()
    }

    @Test fun candleDetailsExplainTheChosenOffsetWithoutInternalBooleans() {
        val app = compose.activity.application as ZmanimApplication
        runBlocking { app.repository.updateProfile("test-profile") { it.copy(selected = listOf("candle-lighting"), options = CalculationOptions(candleLightingMinutes = 40.0)) } }
        compose.onNode(hasText("Zmanim", substring = false) and hasClickAction()).performClick()
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithTag("menu:Go to date").performClick()
        choosePickerDate("2026-11-06")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("dayHeader:2026-11-06").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Candle Lighting", substring = false).onFirst().performClick()
        compose.onNodeWithText("Calculation details").performClick()
        compose.onNodeWithText("40.0 minutes before sea-level sunset").assertIsDisplayed()
        compose.onAllNodesWithText("Astronomical midday: true").assertCountEquals(0)
        compose.onAllNodesWithText("Half-day temporal hours where supported: false").assertCountEquals(0)
    }

    private fun choosePickerDate(iso: String) {
        val target = java.time.LocalDate.parse(iso)
        val app = compose.activity.application as ZmanimApplication
        val model = androidx.lifecycle.ViewModelProvider(compose.activity)[com.example.zmanim.ui.AppViewModel::class.java]
        val initial = model.screen.value!!.date
        compose.onNodeWithTag("pickerCalendar:GREGORIAN").performClick()
        val years = target.year - initial.year
        repeat(kotlin.math.abs(years)) { compose.onNodeWithText(if (years > 0) "+ Year" else "− Year").performScrollTo().performClick() }
        val months = target.monthValue - initial.monthValue
        repeat(kotlin.math.abs(months)) { compose.onNodeWithText(if (months > 0) "+ Month" else "− Month").performScrollTo().performClick() }
        compose.onNodeWithTag("pickerDay:$iso").performScrollTo().performClick()
        compose.onNode(hasText("Choose date") and hasClickAction()).performClick()
    }

}

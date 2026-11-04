package com.example.zmanim

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.domain.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350) // Let Android window animations finish before capturing hardware layers.
        val dir = File(compose.activity.filesDir, "qa").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun englishLightAndHebrewDarkSupportLargeTextAndReachableActions() {
        val app = compose.activity.application as ZmanimApplication
        runBlocking {
            app.awaitData()
            val place = Place(id = "appearance-place", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
            app.repository.update { AppData(settings = AppSettings(onboardingComplete = true, migratedPrototype = true, inIsrael = true,
                language = Language.ENGLISH, appearance = Appearance.LIGHT, location = LocationBinding(savedId = place.id)), places = listOf(place)) }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("overflowMenu").fetchSemanticsNodes().isNotEmpty() }
        screenshot("calendar-light.png")
        val originalFontScale = compose.activity.resources.configuration.fontScale
        try {
            runBlocking { app.repository.settings { it.copy(language = Language.HEBREW, labels = LabelLanguage.HEBREW, appearance = Appearance.DARK, calendarDirection = CalendarDirection.RTL) } }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("overflowMenu").fetchSemanticsNodes().isNotEmpty() }
            val model = androidx.lifecycle.ViewModelProvider(compose.activity)[com.example.zmanim.ui.AppViewModel::class.java]
            compose.runOnIdle { model.select(java.time.LocalDate.of(2026, 9, 12)) }
            compose.waitUntil(10000) { model.screen.value?.date == java.time.LocalDate.of(2026, 9, 12) }
            screenshot("calendar-hebrew-dark.png")
            shell("settings put system font_scale 1.7")
            compose.waitUntil(10000) { compose.activity.resources.configuration.fontScale >= 1.69f && compose.onAllNodesWithTag("overflowMenu").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            val dateLayout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag("secondaryDate:2026-09-11", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(dateLayout) }
            org.junit.Assert.assertEquals("Secondary date digits stay on one line at large font size", 1, dateLayout.single().lineCount)
            screenshot("calendar-hebrew-large.png")
            compose.onNodeWithTag("overflowMenu").performClick()
            compose.onNodeWithText("הגדרות").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("settingsList").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("נתונים").performScrollTo().performClick()
            compose.onNodeWithTag("settingsList").performScrollToNode(hasText("ייצוא, שיתוף או ייבוא"))
            compose.onNodeWithText("ייצוא, שיתוף או ייבוא").performClick()
            compose.onNodeWithText("ייצוא הנתונים שנבחרו").performScrollTo().assertIsDisplayed()
            screenshot("backup-hebrew-large.png")
        } catch (e: Throwable) {
            screenshot("appearance-failure.png")
            File(compose.activity.filesDir, "qa/appearance-tree.txt").writeText(compose.onRoot().printToString())
            throw e
        } finally { shell("settings put system font_scale $originalFontScale") }
    }
    @Test fun calendarZmanimAndSetupScreensRenderAtCompactAndStandardSizes() {
        val app = compose.activity.application as ZmanimApplication
        runBlocking {
            app.awaitData()
            val place = Place(id = "visual-place", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
            val profile = Profile(id = "visual-profile", name = "Daily", selected = listOf("sea-level-sunrise", "chatzos-hayom", "sea-level-sunset", "tzais72-minutes"))
            app.repository.update { AppData(settings = AppSettings(appearance = Appearance.LIGHT, activeProfileId = profile.id, inIsrael = true,
                location = LocationBinding(savedId = place.id)), profiles = listOf(profile), places = listOf(place)) }
        }
        fun destination(label: String) = compose.onNode(hasText(label, substring = false) and hasClickAction()).performClick()
        fun menu(label: String) { compose.onNodeWithTag("overflowMenu").performClick(); compose.onNodeWithTag("menu:$label").performClick() }
        try {
            for (size in listOf("standard", "compact")) {
                if (size == "compact") shell("wm size 900x1600")
                destination("Hebrew"); screenshot("$size-month.png")
                destination("Gregorian"); screenshot("$size-gregorian.png")
                destination("Week")
                compose.waitUntil(10000) { compose.onAllNodesWithTag("hourSchedule").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("hourSchedule").assertIsDisplayed(); screenshot("$size-week.png")
                destination("Day"); screenshot("$size-day.png")
                destination("Zmanim"); screenshot("$size-zmanim.png")
                menu("Choose zmanim"); screenshot("$size-picker.png")
                compose.onNodeWithText("Done").performClick()
                menu("Location"); screenshot("$size-location.png")
                compose.onNodeWithText("Done").performClick()
                menu("Settings"); screenshot("$size-settings.png")
                compose.onNodeWithContentDescription("Back").performClick()
            }
        } finally { shell("wm size reset") }
    }

}

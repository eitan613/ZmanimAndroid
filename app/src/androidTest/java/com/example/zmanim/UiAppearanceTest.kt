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
        val dir = File(compose.activity.cacheDir, "qa").apply { mkdirs() }
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
        compose.waitUntil(10000) { compose.onAllNodesWithText("Menu ▾").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("Your calendar and zmanim").fetchSemanticsNodes().isEmpty() }
        screenshot("calendar-light.png")
        val originalFontScale = compose.activity.resources.configuration.fontScale
        try {
            runBlocking { app.repository.settings { it.copy(language = Language.HEBREW, labels = LabelLanguage.HEBREW, appearance = Appearance.DARK, reverseGrid = true) } }
            compose.waitUntil(10000) { compose.onAllNodesWithText("תפריט ▾").fetchSemanticsNodes().isNotEmpty() }
            screenshot("calendar-hebrew-dark.png")
            shell("settings put system font_scale 1.7")
            compose.waitUntil(10000) { compose.activity.resources.configuration.fontScale >= 1.69f && compose.onAllNodesWithText("תפריט ▾").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            compose.onNode(hasText("הגדרות") and hasClickAction()).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithTag("settingsList").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("settingsList").performScrollToNode(hasText("ייצוא, שיתוף או ייבוא"))
            compose.onNodeWithText("ייצוא, שיתוף או ייבוא").performClick()
            compose.onNodeWithText("ייצוא הנתונים שנבחרו").performScrollTo().assertIsDisplayed()
            screenshot("backup-hebrew-large.png")
        } catch (e: Throwable) {
            screenshot("appearance-failure.png")
            File(compose.activity.cacheDir, "qa/appearance-tree.txt").writeText(compose.onRoot().printToString())
            throw e
        } finally { shell("settings put system font_scale $originalFontScale") }
    }
}

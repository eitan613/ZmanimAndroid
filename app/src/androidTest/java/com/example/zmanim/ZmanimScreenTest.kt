package com.example.zmanim

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zmanim.calculation.SavedLocation
import com.example.zmanim.data.ZmanimPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Before
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZmanimScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearSelections() {
        runBlocking { ZmanimPreferences(compose.activity).saveSelection(emptySet()) }
    }

    @Test
    fun pickerHasNoDefaultsAndPersistsTheExplicitSelection() {
        val emptyText = compose.activity.getString(R.string.no_zmanim_selected)
        compose.waitUntil(5_000) { compose.onAllNodesWithText(emptyText).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sunriseTime").assertDoesNotExist()
        compose.onNodeWithTag("sunsetTime").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.choose_zmanim)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.sunrise)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.done)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("sunriseTime").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sunsetTime").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("sunriseTime").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sunriseTime").assertIsDisplayed()
        compose.onNodeWithTag("sunsetTime").assertDoesNotExist()
    }

    @Test
    fun dateNavigationAndTodayUpdateBothCalendars() {
        val today = LocalDate.now(SavedLocation.Jerusalem.zoneId)
        val locale = compose.activity.resources.configuration.locales[0]
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        compose.onNodeWithTag("civilDate").assertTextEquals(formatter.format(today))
        compose.onNodeWithTag("hebrewDate").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.next_day)).performClick()
        compose.onNodeWithTag("civilDate").assertTextEquals(formatter.format(today.plusDays(1)))
        compose.onNodeWithText(compose.activity.getString(R.string.previous_day)).performClick()
        compose.onNodeWithTag("civilDate").assertTextEquals(formatter.format(today))
        compose.onNodeWithText(compose.activity.getString(R.string.previous_day)).performClick()
        compose.onNodeWithTag("civilDate").assertTextEquals(formatter.format(today.minusDays(1)))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("civilDate").assertTextEquals(formatter.format(today.minusDays(1)))
        compose.onNodeWithText(compose.activity.getString(R.string.today)).performClick()
        compose.onNodeWithTag("civilDate").assertTextEquals(formatter.format(today))
    }
}

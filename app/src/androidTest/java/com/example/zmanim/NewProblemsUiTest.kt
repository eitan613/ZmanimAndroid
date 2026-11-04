package com.example.zmanim

import android.content.Intent
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
class NewProblemsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun seed() = runBlocking {
        val app=compose.activity.application as ZmanimApplication
        app.awaitData()
        val place=Place(id="ui-place",name="Jerusalem",latitude=31.778,longitude=35.235,zoneId="Asia/Jerusalem")
        val profile=Profile(id="ui-profile",name="Active example",selected=listOf("sea-level-sunrise","sea-level-sunset"))
        app.repository.update { AppData(settings=AppSettings(language=Language.ENGLISH,labels=LabelLanguage.HEBREW,inIsrael=true,activeProfileId=profile.id,location=LocationBinding(savedId=place.id)),places=listOf(place),profiles=listOf(profile)) }
    }
    private fun menu(label:String) {
        compose.waitUntil(10000) { compose.onAllNodesWithTag("overflowMenu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("overflowMenu").performClick()
        compose.onNodeWithTag("menu:$label").performClick()
    }
    @Test fun settingsHasNoBottomBarAndCalendarLaunchConsumesDateOnce() {
        compose.onNode(hasText("Zmanim") and hasClickAction()).performClick()
        menu("Settings")
        compose.onAllNodes(hasText("Gregorian") and hasClickAction()).assertCountEquals(0)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("zmanimTimeline").assertExists()
        menu("Settings")
        compose.runOnUiThread {
            compose.activity.startActivity(Intent(compose.activity.intent).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("destination","calendar").putExtra("date","2026-09-12"))
        }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("monthGrid").fetchSemanticsNodes().isNotEmpty() }
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[com.example.zmanim.ui.AppViewModel::class.java]
        org.junit.Assert.assertEquals("2026-09-12",model.screen.value!!.date.toString())
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("monthGrid").assertExists()
        org.junit.Assert.assertEquals("2026-09-12",model.screen.value!!.date.toString())
    }
    @Test fun citySearchKeepsAResultVisibleWithTheKeyboardOpen() {
        menu("App location")
        compose.onNodeWithText("Find a city offline").performScrollTo().performClick()
        compose.onNodeWithText("City name (2 or more letters)").performTextInput("Givat Zev")
        compose.waitUntil(25000) { compose.onAllNodesWithText("Givat Zeev, IL").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Givat Zeev, IL").assertIsDisplayed().performClick()
        compose.onNodeWithText("IANA timezone").performScrollTo().assertTextContains("Asia/Jerusalem")
    }
    @Test fun offlineHelpTopicsAndActiveProfileRemindersAreReachable() {
        menu("Help")
        compose.onNodeWithText("Profiles and names").performClick()
        compose.onNodeWithText("A profile saves",substring=true).assertExists()
        compose.onNodeWithText("Back",substring=false).performClick()
        compose.onNodeWithText("About → Licenses & data sources").performScrollTo().performClick()
        compose.onNodeWithText("License texts and source metadata below remain available offline.").performScrollTo().assertExists()
    }
}

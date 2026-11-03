package com.example.zmanim

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.zmanim.domain.*
import com.example.zmanim.ui.AppViewModel
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarGesturesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun bothSwipeAxesNavigateMonthsAndYearsAndCanBeSwapped() {
        val app = compose.activity.application as ZmanimApplication
        runBlocking { app.awaitData(); app.repository.update { AppData(settings = AppSettings(primaryCalendar = CalendarSystem.GREGORIAN)) } }
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        val initial = LocalDate.of(2026, 11, 2)
        fun expect(date: LocalDate) {
            compose.waitUntil(10000) { model.screen.value?.date == date }
            compose.waitForIdle()
            assertEquals(date, model.screen.value!!.date)
        }
        for (rtl in listOf(false, true)) for (swapped in listOf(false, true)) {
            runBlocking { app.repository.settings { it.copy(reverseGrid = rtl, swapScrollAxes = swapped) } }
            compose.runOnIdle { model.select(initial) }; expect(initial)
            compose.onNodeWithTag("monthGrid").performTouchInput { swipeUp(durationMillis = 350) }
            expect(if (swapped) initial.plusMonths(1) else initial.plusYears(1))
            compose.onNodeWithTag("monthGrid").performTouchInput { swipeDown(durationMillis = 350) }
            expect(initial)
            compose.onNodeWithTag("monthGrid").performTouchInput { if (rtl) swipeRight(durationMillis = 350) else swipeLeft(durationMillis = 350) }
            expect(if (swapped) initial.plusYears(1) else initial.plusMonths(1))
            compose.onNodeWithTag("monthGrid").performTouchInput { if (rtl) swipeLeft(durationMillis = 350) else swipeRight(durationMillis = 350) }
            expect(initial)
        }
    }
}

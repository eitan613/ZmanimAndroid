package com.example.zmanim

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.activity.ComponentActivity
import com.example.zmanim.domain.*
import com.example.zmanim.widgets.WidgetContent
import com.example.zmanim.widgets.WidgetLine
import com.example.zmanim.widgets.dateWidgetWeekday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.*

/** Safe on personal phones: preserve existing events, preferences, and launcher widgets. */
@OptIn(ExperimentalGlanceApi::class)
class WidgetFeedbackTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun dateBadgesFitPhoneCellsAndUseFullWeekdaysAndExplicitAlignment() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val place = Place(name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        assertEquals("יום רביעי", dateWidgetWeekday(DayOfWeek.WEDNESDAY, true))
        assertEquals("Wednesday", dateWidgetWeekday(DayOfWeek.WEDNESDAY, false))
        for (language in listOf(Language.ENGLISH, Language.HEBREW)) for (calendar in CalendarSystem.entries) {
            val data = AppData(settings = AppSettings(language = language, labels = LabelLanguage.HEBREW))
            val config = WidgetConfig("isolated-preview", WidgetKind.DATE, calendar = calendar, sunsetRollover = false)
            val widget = object : GlanceAppWidget() {
                override suspend fun provideGlance(context: Context, id: GlanceId) {
                    provideContent { WidgetContent(WidgetKind.DATE, config, data, place, Instant.now(), emptyList(), Intent(), Intent(), context) }
                }
            }
            // Samsung One UI allocations plus short and tall launcher cells.
            for (size in listOf(DpSize(67.dp, 71.dp), DpSize(110.dp, 56.dp), DpSize(156.dp, 71.dp), DpSize(180.dp, 72.dp), DpSize(120.dp, 180.dp))) {
                val remote = widget.compose(context, size = size)
                withContext(Dispatchers.Main) {
                    val frame = FrameLayout(context)
                    frame.addView(remote.apply(context, frame), FrameLayout.LayoutParams(-1, -1))
                    val density = context.resources.displayMetrics.density
                    val width = (size.width.value * density).toInt()
                    val height = (size.height.value * density).toInt()
                    frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    frame.layout(0, 0, width, height)
                    val texts = descendants(frame).filterIsInstance<TextView>().filter { it.text.isNotBlank() }
                    val weekday = dateWidgetWeekday(LocalDate.now(place.zone()).dayOfWeek, calendar == CalendarSystem.HEBREW)
                    assertTrue("Full weekday on $calendar $size", texts.any { it.text.toString() == weekday })
                    if (size.width >= 150.dp && calendar == CalendarSystem.HEBREW) {
                        assertTrue(texts.any { it.text.toString().trim('\u2066', '\u2069').endsWith(LocalDate.now(place.zone()).year.toString()) })
                        for (text in texts.filter { it.textSize < texts.maxOf { it.textSize } }) {
                            assertEquals("Hebrew details are physically right aligned even with English controls", Gravity.RIGHT, text.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
                        }
                    }
                    for (text in texts) {
                        val label = "$language $calendar $size ${text.text}"
                        val layout = text.layout ?: error("No text layout: $label")
                        assertEquals("One line: $label", 1, layout.lineCount)
                        assertEquals("No ellipsis: $label", 0, layout.getEllipsisCount(0))
                        assertTrue("No horizontal clipping: $label", layout.getLineWidth(0) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight + 1)
                        assertTrue("No vertical clipping: $label (line ${layout.getLineBottom(0)}, padding ${text.compoundPaddingTop + text.compoundPaddingBottom}, height ${text.height})", layout.getLineBottom(0) + text.compoundPaddingTop + text.compoundPaddingBottom <= text.height)
                        val rect = android.graphics.Rect(0, 0, text.width, text.height)
                        frame.offsetDescendantRectToMyCoords(text, rect)
                        assertTrue("Within widget: $label $rect", rect.top >= 0 && rect.bottom <= height && rect.left >= 0 && rect.right <= width)
                    }
                    val directory = File(context.filesDir, "qa/widget-feedback").apply { mkdirs() }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    frame.draw(Canvas(bitmap))
                    File(directory, "$language-$calendar-${size.width.value.toInt()}x${size.height.value.toInt()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
    }

    @Test fun absentCalendarRowsAreOmittedAndActiveRowsRemain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val schedules = com.example.zmanim.data.LearningSchedules(context)
        for (labels in LabelLanguage.entries) {
            val settings = AppSettings(labels = labels)
            assertTrue(schedules.rows(LocalDate.of(2026, 11, 4), listOf("holiday", "rosh-chodesh", "omer"), settings).isEmpty())
            assertEquals(1, schedules.rows(LocalDate.of(2026, 11, 10), listOf("rosh-chodesh"), settings).size)
            assertEquals(1, schedules.rows(LocalDate.of(2026, 4, 10), listOf("omer"), settings).size)
            assertEquals(1, schedules.rows(LocalDate.of(2026, 4, 2), listOf("holiday"), settings).size)
        }
    }

    @Test fun hebrewLearningRowsAlignAndScrollFromTheDateHeaderAfterRefresh(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val place = Place(name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        val data = AppData(settings = AppSettings(language = Language.ENGLISH, labels = LabelLanguage.HEBREW))
        val open = Intent(context, MainActivity::class.java).putExtra("destination", "calendar")
        val rows = listOf(WidgetLine("הפרשה הקרובה", "חיי שרה", open), WidgetLine("דף יומי", "חולין מ״ז", open)) +
            (1..10).map { WidgetLine("לימוד $it", "שורת לימוד יומי $it", open) }
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) {
                provideContent { WidgetContent(WidgetKind.COMBINED, WidgetConfig("isolated-learning", WidgetKind.COMBINED), data,
                    place, Instant.now(), rows, open, open, context) }
            }
        }
        val host = AppWidgetHost(context, 73923)
        val widgetId = host.allocateAppWidgetId()
        val manager = AppWidgetManager.getInstance(context)
        val provider = manager.getInstalledProvidersForPackage(context.packageName, null)
            .single { it.provider.className.endsWith(".CombinedWidgetReceiver") }
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        try { assertTrue(manager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)) }
        finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        try {
            val glanceId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIdBy(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))!!
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var view: android.appwidget.AppWidgetHostView
                scenario.onActivity { activity ->
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    view = host.createView(activity, widgetId, provider)
                }
                for (size in listOf(DpSize(300.dp, 240.dp), DpSize(180.dp, 110.dp))) {
                    for (refresh in 0..1) {
                        val remote = widget.compose(context, id = glanceId, size = size)
                        scenario.onActivity { activity ->
                            val density = activity.resources.displayMetrics.density
                            activity.setContentView(view, ViewGroup.LayoutParams((size.width.value * density).toInt(), (size.height.value * density).toInt()))
                            view.updateAppWidget(remote)
                        }
                        lateinit var list: android.widget.ListView
                        kotlinx.coroutines.withTimeout(10000) {
                            while (true) {
                                var ready = false
                                scenario.onActivity {
                                    val nativeList = descendants(view).filterIsInstance<android.widget.ListView>().singleOrNull()
                                    if (nativeList != null && nativeList.adapter?.count == rows.size + 1 && nativeList.childCount > 0) {
                                        list = nativeList; ready = true
                                    }
                                }
                                if (ready) break
                                kotlinx.coroutines.delay(100)
                            }
                        }
                        scenario.onActivity { list.setSelection(0) }
                        instrumentation.waitForIdleSync()
                        var before = 0 to 0
                        lateinit var header: TextView
                        scenario.onActivity {
                            before = list.firstVisiblePosition to list.getChildAt(0).top
                            val texts = descendants(view).filterIsInstance<TextView>().filter { it.text.isNotBlank() }
                            header = texts.first()
                            for (text in texts) assertEquals("Hebrew text: ${text.text}", Gravity.RIGHT, text.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
                        }
                        val rect = android.graphics.Rect()
                        scenario.onActivity {
                            rect.set(0, 0, header.width, header.height)
                            view.offsetDescendantRectToMyCoords(header, rect)
                        }
                        val down = android.os.SystemClock.uptimeMillis()
                        for (step in 0..12) {
                            val fraction = step / 12f
                            val y = rect.exactCenterY() * (1 - fraction) + fraction
                            val action = when (step) { 0 -> android.view.MotionEvent.ACTION_DOWN; 12 -> android.view.MotionEvent.ACTION_UP; else -> android.view.MotionEvent.ACTION_MOVE }
                            val event = android.view.MotionEvent.obtain(down, down + step * 20L, action, rect.exactCenterX(), y, 0)
                            scenario.onActivity { view.dispatchTouchEvent(event) }
                            event.recycle()
                            android.os.SystemClock.sleep(20)
                        }
                        kotlinx.coroutines.withTimeout(3000) {
                            while (true) {
                                var moved = false
                                scenario.onActivity {
                                    moved = list.firstVisiblePosition > before.first || list.getChildAt(0).top < before.second - 2
                                }
                                if (moved) break
                                kotlinx.coroutines.delay(50)
                            }
                        }
                        scenario.onActivity { assertTrue("Learning list still has more content: $size refresh=$refresh", list.canScrollVertically(1)) }
                    }
                }
            }
        } finally { host.stopListening(); host.deleteAppWidgetId(widgetId) }
    }
}

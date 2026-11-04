package com.example.zmanim

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.example.zmanim.widgets.widgetLaunch
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Uses existing calendar access and opens an existing event without editing it. */
class WidgetPhoneNavigationTest {
    @Test fun eventTapOpensTheEventEditorInsideZmanim() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as ZmanimApplication
        val now = Instant.now()
        val occurrence = withContext(Dispatchers.IO) {
            val selected = app.awaitData().settings.visibleCalendarIds
            runCatching { app.calendars.instances(now, now.plus(Duration.ofDays(30)), selected) }
                .getOrDefault(emptyList()).firstOrNull { it.providerId != null && it.start != null }
        }
        assumeTrue("Requires an existing readable device-calendar event", occurrence != null)
        val event = occurrence!!
        val launch = widgetLaunch(context, 0, "event", event.date, event.key)
            .putExtra("providerId", event.providerId!!).putExtra("instanceStart", event.start!!.toEpochMilli())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(launch).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            fun labels(node: android.view.accessibility.AccessibilityNodeInfo?): List<String> = if (node == null) emptyList() else
                listOfNotNull(node.text?.toString(), node.contentDescription?.toString()) +
                    (0 until node.childCount).flatMap { labels(node.getChild(it)) }
            try { withTimeout(20000) {
                while (true) {
                    val root = instrumentation.uiAutomation.rootInActiveWindow
                    // Compose exposes virtual accessibility children; native text-search RPCs
                    // do not reliably search their contents on Samsung.
                    val visible = labels(root)
                    if (root?.packageName?.toString() == context.packageName &&
                        visible.any { it.contains(event.title) } && visible.any { it in setOf("Cancel", "ביטול") }) break
                    delay(200)
                }
            } } finally {
                val directory = java.io.File(context.filesDir, "qa").apply { mkdirs() }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(directory, "event-editor.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                }

            }
        }
    }

    @Test fun bothCompactDateWidgetsAreAdvertisedAsOneCellAndHaveConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val providers = AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null)
        for (name in listOf("CompactDateWidgetReceiver", "CompactGregorianDateWidgetReceiver")) {
            val provider = providers.single { it.provider.className.endsWith(".$name") }
            assertNotNull("Opening screen can be chosen", provider.configure)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                assertEquals(1, provider.targetCellWidth)
                assertEquals(1, provider.targetCellHeight)
            }
        }
    }
}

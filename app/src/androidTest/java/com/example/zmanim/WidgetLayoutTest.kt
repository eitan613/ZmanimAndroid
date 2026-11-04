package com.example.zmanim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.domain.*
import com.example.zmanim.widgets.*
import java.io.File
import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetLayoutTest {
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun widgetsUseActualAllocatedSizeAndInheritAppLocation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ZmanimApplication
        val original = app.awaitData()
        val place = Place(id = "layout-place", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        val profile = Profile(id = "layout-profile", selected = listOf("sea-level-sunrise", "sea-level-sunset"))
        val later = Instant.now().plusSeconds(7200).atZone(place.zone())
        val event = Event(title = "Team meeting", anchorDate = later.toLocalDate().toString(), localTime = later.toLocalTime().toString(), zoneId = place.zoneId)
        try {
            for (dark in listOf(false, true)) {
                app.repository.update { AppData(settings = AppSettings(appearance = if (dark) Appearance.DARK else Appearance.LIGHT,
                    inIsrael = true, activeProfileId = profile.id, location = LocationBinding(savedId = place.id), showDeviceEvents = false),
                    profiles = listOf(profile), places = listOf(place), events = listOf(event)) }
                for ((kind, widget) in listOf(WidgetKind.DATE to DateWidget(), WidgetKind.DATE to GregorianDateWidget(), WidgetKind.COMBINED to CombinedWidget(), WidgetKind.EVENTS to EventsWidget(), WidgetKind.ZMANIM to ZmanimWidget())) {
                    val gregorianDate = widget is GregorianDateWidget
                    val numeric = 1_100_000 + kind.ordinal + if (gregorianDate) 100 else 0
                    val id = GlanceAppWidgetManager(context).getGlanceIdBy(android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, numeric))!!
                    // An empty location binding means the active app location, including for existing widgets.
                    app.repository.update { it.copy(widgets = it.widgets + WidgetConfig(numeric.toString(), kind, itemCount = 1, countdown = true, calendar = if (gregorianDate) CalendarSystem.GREGORIAN else CalendarSystem.HEBREW)) }
                    val sizes = when(kind) {
                        WidgetKind.DATE -> listOf(DpSize(110.dp, 56.dp), DpSize(110.dp, 110.dp), DpSize(180.dp, 72.dp), DpSize(120.dp, 180.dp))
                        WidgetKind.COMBINED -> listOf(DpSize(180.dp, 110.dp), DpSize(300.dp, 180.dp), DpSize(300.dp, 300.dp))
                        else -> listOf(DpSize(180.dp, 110.dp), DpSize(300.dp, 240.dp))
                    }
                    var smallDateSize: Float? = null
                    for (size in sizes) {
                        val remote = widget.compose(context, id = id, size = size)
                        withContext(Dispatchers.Main) {
                            val frame = FrameLayout(context)
                            val view = remote.apply(context, frame)
                            frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            val density = context.resources.displayMetrics.density
                            val w = (size.width.value * density).toInt(); val h = (size.height.value * density).toInt()
                            frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); frame.layout(0, 0, w, h)
                            val textViews = descendants(frame).filterIsInstance<TextView>()
                            val text = textViews.joinToString(" ") { it.text.toString() }
                            assertFalse("Widget must inherit the selected app location: $kind", text.contains("Choose location"))
                            assertFalse(text.contains("Choose a location for the current Hebrew date"))
                            if (kind == WidgetKind.DATE) {
                                val largest = textViews.maxOf { it.textSize }
                                textViews.maxBy { it.textSize }.layout?.let { layout ->
                                    assertEquals("Primary date must not become an ellipsis: $size", 0, layout.getEllipsisCount(0))
                                }
                                if (smallDateSize == null) smallDateSize = largest else if (size.height >= 140.dp) assertTrue("Date type scales up when the widget is made taller", largest > smallDateSize!!)
                            }
                            if (kind == WidgetKind.COMBINED) assertTrue("Learning rows use a native scrolling collection", descendants(frame).any { it is android.widget.AdapterView<*> })
                            if (kind == WidgetKind.EVENTS) assertTrue(text.contains("Team meeting"))
                            if (kind == WidgetKind.ZMANIM && size.height >= 160.dp) assertTrue(text.contains("Jerusalem"))
                            for (textView in textViews.filter { it.visibility == View.VISIBLE && it.text.isNotBlank() }) {
                                if (kind == WidgetKind.DATE) textView.layout?.let { layout ->
                                    assertTrue("Date text lines must fit without vertical clipping: $size ${textView.text}",
                                        layout.getLineBottom(layout.lineCount - 1) + textView.compoundPaddingTop + textView.compoundPaddingBottom <= textView.height)
                                }
                                val rect = android.graphics.Rect(0, 0, textView.width, textView.height)
                                frame.offsetDescendantRectToMyCoords(textView, rect)
                                assertTrue("Text must stay inside the widget: $kind $size ${textView.text} $rect", rect.top >= 0 && rect.bottom <= h && rect.left >= 0 && rect.right <= w)
                            }
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            frame.draw(Canvas(bitmap))
                            File(context.filesDir, "qa/widgets").mkdirs()
                            File(context.filesDir, "qa/widgets/${kind.name.lowercase()}${if (gregorianDate) "-gregorian" else ""}-${size.width.value.toInt()}x${size.height.value.toInt()}-${if (dark) "dark" else "light"}.png")
                                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        }
                    }
                }
            }
        } finally { app.repository.update { original } }
    }
}

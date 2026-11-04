package com.example.zmanim

import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.domain.*
import com.example.zmanim.widgets.*
import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @Test fun allWidgetFamiliesRenderAndAllZmanRowsUseTheScrollingCollection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as ZmanimApplication
        val original = app.awaitData()
        val place = Place(id = "widget-test-place", name = "Jerusalem", latitude = 31.778, longitude = 35.235, zoneId = "Asia/Jerusalem")
        val profile = Profile(id = "widget-test-profile", selected = listOf("sea-level-sunrise"))
        val later = Instant.now().plusSeconds(7200).atZone(place.zone())
        val event = Event(title = "Widget event", anchorDate = later.toLocalDate().toString(), localTime = later.toLocalTime().toString(), zoneId = place.zoneId)
        try {
            app.repository.update { AppData(settings = AppSettings(onboardingComplete = true, migratedPrototype = true, inIsrael = true,
                activeProfileId = profile.id, location = LocationBinding(savedId = place.id), showDeviceEvents = false), profiles = listOf(profile), places = listOf(place), events = listOf(event)) }
            for ((kind, widget) in listOf(WidgetKind.EVENTS to EventsWidget(), WidgetKind.ZMANIM to ZmanimWidget(), WidgetKind.DATE to DateWidget(), WidgetKind.COMBINED to CombinedWidget())) {
                val numeric = 1_000_000 + kind.ordinal
                val id = GlanceAppWidgetManager(context).getGlanceIdBy(android.content.Intent().putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, numeric))!!
                app.repository.update { it.copy(widgets = it.widgets + WidgetConfig(numeric.toString(), kind, profileId = profile.id, countdown = true)) }
                val remote = widget.compose(context, id = id, size = DpSize(300.dp, 240.dp))
                val rendered = withContext(Dispatchers.Main) { remote.apply(context, android.widget.FrameLayout(context)) }
                fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                val views = descendants(rendered)
                val text = views.filterIsInstance<TextView>().joinToString(" ") { it.text.toString() }
                assertTrue("Widget must render text: $kind", text.isNotBlank())
                if (kind == WidgetKind.EVENTS) assertTrue(text.contains("Widget event"))
                if (kind == WidgetKind.ZMANIM) {
                    assertTrue(views.any { it is android.widget.AdapterView<*> })
                    assertFalse("No countdown/next row is pinned outside the collection", views.any { it is Chronometer })
                }
                if (kind == WidgetKind.EVENTS) {
                    app.repository.update { data -> data.copy(events = (0 until 10).map { index -> event.copy(id = "widget-event-$index", title = "Widget event $index") },
                        widgets = data.widgets.map { if (it.id == numeric.toString()) it.copy(itemCount = 10) else it }) }
                    val scrolling = widget.compose(context, id = id, size = DpSize(300.dp, 240.dp))
                    val list = withContext(Dispatchers.Main) { scrolling.apply(context, android.widget.FrameLayout(context)) }
                    assertTrue("Overflowing widget events use a native scrollable collection", descendants(list).any { it is android.widget.AdapterView<*> })
                }
            }
        } finally { app.repository.update { original } }
    }
}

package com.example.zmanim

import android.appwidget.*
import android.content.ComponentName
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.domain.*
import com.example.zmanim.widgets.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.*

@OptIn(ExperimentalGlanceApi::class)
class WidgetHostTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun descendants(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    @Test fun realHostScrollsAllZmanRowsAndEachHasANativeCountdown() = runBlocking {
        val activity=compose.activity
        val app=activity.application as ZmanimApplication
        val old=app.awaitData()
        val host=AppWidgetHost(activity,73921)
        val manager=AppWidgetManager.getInstance(activity)
        val id=host.allocateAppWidgetId()
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        try { assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(activity,ZmanimWidgetReceiver::class.java))) }
        finally { automation.dropShellPermissionIdentity() }
        try {
            val place=Place(id="host-place",name="Jerusalem",latitude=31.778,longitude=35.235,zoneId="Asia/Jerusalem")
            val p=Profile(id="host-profile",selected=listOf("sea-level-sunrise","sea-level-sunset"))
            app.repository.update { AppData(settings=AppSettings(activeProfileId=p.id,location=LocationBinding(savedId=place.id)),places=listOf(place),profiles=listOf(p),widgets=listOf(WidgetConfig(id.toString(),WidgetKind.ZMANIM,itemCount=4,countdown=true))) }
            lateinit var view:AppWidgetHostView
            compose.runOnUiThread {
                host.startListening()
                view=host.createView(activity,id,manager.getAppWidgetInfo(id))
                view.updateAppWidgetSize(null,300,240,300,240)
                activity.setContent { AndroidView(factory={view},modifier=Modifier.size(300.dp,240.dp)) }
            }
            compose.waitForIdle()
            val glance=GlanceAppWidgetManager(activity).getGlanceIdBy(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id))!!
            val remote = ZmanimWidget().compose(activity, id = glance, size = DpSize(300.dp, 240.dp))
            compose.runOnUiThread { view.updateAppWidget(remote) }
            compose.waitForIdle()
            withTimeout(30000) {
                while(withContext(Dispatchers.Main) { descendants(view).filterIsInstance<Chronometer>().size }<2) {
                    compose.waitForIdle()
                    delay(200)
                }
            }
            withContext(Dispatchers.Main) {
                assertTrue(descendants(view).any { it is AdapterView<*> })
                val clocks=descendants(view).filterIsInstance<Chronometer>()
                assertTrue(clocks.size>=2)
                assertTrue(clocks.all { it.isCountDown })
                assertTrue(clocks.map { it.base }.distinct().size>=2)
            }
        } catch (e: Throwable) {
            val root = compose.activity.window.decorView
            withContext(Dispatchers.Main) {
                java.io.File(activity.cacheDir, "widget-host-tree.txt").writeText(descendants(root).joinToString("\n") { v ->
                    "${v.javaClass.name} ${v.width}x${v.height} " + (if (v is TextView) v.text else if (v is AdapterView<*>) "count=${v.adapter?.count}" else "")
                })
            }
            throw e
        } finally {
            compose.runOnUiThread { host.stopListening();host.deleteAppWidgetId(id) }
            app.repository.update { old }
        }
    }
}

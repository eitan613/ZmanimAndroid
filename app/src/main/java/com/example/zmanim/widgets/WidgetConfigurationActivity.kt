package com.example.zmanim.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zmanim.ZmanimApplication
import com.example.zmanim.data.RepositoryState
import com.example.zmanim.domain.*
import com.example.zmanim.ui.*
import com.example.zmanim.ui.theme.ZmanimTheme

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val app = application as ZmanimApplication
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className.orEmpty()
        val kind = when { "Events" in provider -> WidgetKind.EVENTS; "Zmanim" in provider -> WidgetKind.ZMANIM; "Combined" in provider -> WidgetKind.COMBINED; else -> WidgetKind.DATE }
        setContent {
            val state by app.repository.state.collectAsStateWithLifecycle()
            ZmanimTheme {
                val data = (state as? RepositoryState.Ready)?.data
                if (data == null) CircularProgressIndicator() else {
                    CompositionLocalProvider(LocalHebrew provides (data.settings.language == Language.HEBREW)) {
                        val initial = remember { data.widgets.find { it.id == id.toString() } ?: WidgetConfig(id.toString(), kind,
                            location = data.settings.location, profileId = data.settings.activeProfileId) }
                        var config by rememberDraft("widget-$id", initial)
                        EditorDialog(tr("Configure widget", "הגדרת יישומון"), config != initial, { finish() }, {
                            app.repository.update { it.copy(widgets = it.widgets.filterNot { w -> w.id == config.id } + config) }
                            WidgetUpdates.refresh(this@WidgetConfigurationActivity, app.repository.data)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                        }) {
                            if (kind in setOf(WidgetKind.EVENTS, WidgetKind.ZMANIM)) Choice(tr("Number of items", "מספר פריטים"), config.itemCount,
                                (1..10).toList(), { it.toString() }, { config = config.copy(itemCount = it) })
                            LocationChoice(tr("Widget location", "מיקום היישומון"), config.location, data) { config = config.copy(location = it) }
                            if (kind == WidgetKind.ZMANIM) {
                                Choice(tr("Profile", "פרופיל"), config.profileId, listOf(null) + data.profiles.map { it.id },
                                    { value -> data.profiles.find { it.id == value }?.name ?: tr("Active app profile", "הפרופיל הפעיל באפליקציה") }, { config = config.copy(profileId = it) })
                                Toggle(tr("Countdown to next zman", "ספירה לאחור לזמן הבא"), config.countdown, { config = config.copy(countdown = it) })
                            }
                            if (kind == WidgetKind.EVENTS) Choice(tr("Tap action", "פעולת לחיצה"), config.tap, WidgetTap.entries, { enumName(it) }, { config = config.copy(tap = it) })
                            if (kind == WidgetKind.DATE) Choice(tr("Calendar", "לוח"), config.calendar, CalendarSystem.entries, { enumName(it) }, { config = config.copy(calendar = it) })
                            if (kind in setOf(WidgetKind.DATE, WidgetKind.COMBINED)) Toggle(tr("Hebrew date advances at sunset", "התאריך העברי מתחלף בשקיעה"), config.sunsetRollover, { config = config.copy(sunsetRollover = it) })
                            Text(tr("Android may delay widget updates. Precise-alarm access improves boundary updates; the widget shows its last refresh time.", "Android עשוי לעכב עדכוני יישומון. הרשאת שעון מדויק משפרת עדכונים בגבולות הזמן; היישומון מציג את מועד הרענון האחרון."))
                        }
                    }
                }
            }
        }
    }
}

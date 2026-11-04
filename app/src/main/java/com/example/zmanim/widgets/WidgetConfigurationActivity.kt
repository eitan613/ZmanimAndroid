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
            val data = (state as? RepositoryState.Ready)?.data
            ZmanimTheme(darkTheme = data?.settings?.let { effectiveDark(it) } ?: androidx.compose.foundation.isSystemInDarkTheme()) {
                if (data == null) CircularProgressIndicator() else {
                    CompositionLocalProvider(LocalHebrew provides (data.settings.productLocale().language in setOf("he", "iw")),
                        LocalLabelHebrew provides (data.settings.labels == LabelLanguage.HEBREW),
                        LocalProductLocale provides data.settings.productLocale(),
                        androidx.compose.ui.platform.LocalLayoutDirection provides if (data.settings.productLocale().language in setOf("he", "iw")) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                        val initial = remember { data.widgets.find { it.id == id.toString() } ?: WidgetConfig(id.toString(), kind, calendar = if ("Gregorian" in provider) CalendarSystem.GREGORIAN else CalendarSystem.HEBREW, profileId = null) }
                        var config by rememberDraft("widget-$id", initial)
                        EditorDialog(tr("Configure widget", "הגדרת יישומון"), config != initial, { finish() }, {
                            app.repository.update { it.copy(widgets = it.widgets.filterNot { w -> w.id == config.id } + config) }
                            WidgetUpdates.refresh(this@WidgetConfigurationActivity, app.repository.data)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                        }) {
                            if (kind in setOf(WidgetKind.EVENTS, WidgetKind.ZMANIM)) Choice(tr("Number of items", "מספר פריטים"), config.itemCount,
                                (1..10).toList(), { it.toString() }, { config = config.copy(itemCount = it) })
                            Text(when (kind) {
                                WidgetKind.DATE -> tr("A compact date badge. Fits 1 × 1 or 2 × 1 cells; resize to show a larger date.", "תג תאריך קומפקטי. מתאים ל־1 × 1 או 2 × 1 משבצות וניתן להגדלה.")
                                WidgetKind.COMBINED -> tr("Both dates, the upcoming parsha, and Daf Yomi. Start at 3 × 2 cells.", "שני התאריכים, הפרשה הקרובה ודף יומי. מתחיל בגודל 3 × 2 משבצות.")
                                WidgetKind.EVENTS -> tr("Your upcoming events, with scrolling when needed.", "האירועים הקרובים שלך, עם גלילה לפי הצורך.")
                                WidgetKind.ZMANIM -> tr("Your selected zmanim, with an optional countdown.", "הזמנים שבחרת, עם ספירה לאחור לפי בחירה.")
                            })
                            if (kind != WidgetKind.EVENTS) Text(tr("Uses app location: ", "מיקום האפליקציה: ") + (data.place()?.name ?: tr("Set app location", "יש להגדיר מיקום")))
                            if (data.place() == null && kind != WidgetKind.EVENTS) TextButton(onClick = {
                                startActivity(Intent(this@WidgetConfigurationActivity, com.example.zmanim.MainActivity::class.java).putExtra("destination", "location"))
                            }) { Text(tr("Set app location", "הגדרת מיקום האפליקציה")) }
                            if (kind == WidgetKind.ZMANIM) {
                                Choice(tr("Profile", "פרופיל"), config.profileId, listOf(null) + data.profiles.map { it.id },
                                    { value -> data.profiles.find { it.id == value }?.name ?: tr("Active app profile", "הפרופיל הפעיל באפליקציה") }, { config = config.copy(profileId = it) })
                                Toggle(tr("Countdowns for all upcoming zmanim", "ספירה לאחור לכל הזמנים הקרובים"), config.countdown, { config = config.copy(countdown = it) })
                            }
                            if (kind == WidgetKind.EVENTS) Choice(tr("Tap action", "פעולת לחיצה"), config.tap, WidgetTap.entries, { enumName(it) }, { config = config.copy(tap = it) })
                            if (kind in setOf(WidgetKind.DATE, WidgetKind.COMBINED)) Choice(tr("Open", "פתיחה"), config.openingView, OpeningView.entries, { enumName(it) }, { config = config.copy(openingView = it) })
                            if (kind == WidgetKind.DATE) Choice(tr("Calendar", "לוח"), config.calendar, CalendarSystem.entries, { enumName(it) }, { config = config.copy(calendar = it) })
                            if (kind == WidgetKind.COMBINED) {
                                Text(tr("Daily learning uses the local civil date; date-badge sunset rollover does not advance these cycles.", "הלימוד היומי לפי התאריך האזרחי המקומי; החלפת תאריך בשקיעה אינה מקדמת את מחזורי הלימוד."))
                                config.learning.forEachIndexed { index, key ->
                                    val provider = app.learning.providers.find { it.id == key }
                                    Text(provider?.let { if (LocalHebrew.current) it.he else it.en } ?: key)
                                    androidx.compose.foundation.layout.Row {
                                        TextButton(enabled = index > 0, onClick = {
                                            val order = config.learning.toMutableList(); order[index] = order[index - 1].also { order[index - 1] = order[index] }; config = config.copy(learning = order)
                                        }) { Text(tr("Move up", "למעלה")) }
                                        TextButton(onClick = { config = config.copy(learning = config.learning - key) }) { Text(tr("Remove", "הסרה")) }
                                    }
                                }
                                app.learning.providers.filter { it.id !in config.learning }.forEach { provider ->
                                    TextButton(onClick = { config = config.copy(learning = config.learning + provider.id) }) { Text("+ " + if (LocalHebrew.current) provider.he else provider.en) }
                                    Text(provider.cycle, style = MaterialTheme.typography.bodySmall)
                                    if (provider.validFrom != null) Text("${provider.validFrom}–${provider.validThrough} · ${provider.version.ifBlank { "Hebcal ${app.learning.version}" }}", style = MaterialTheme.typography.bodySmall)
                                }
                                HelpLink("widgets")
                            }
                            if (kind in setOf(WidgetKind.DATE, WidgetKind.COMBINED)) Toggle(tr("Hebrew date advances at sunset", "התאריך העברי מתחלף בשקיעה"), config.sunsetRollover, { config = config.copy(sunsetRollover = it) })

                        }
                    }
                }
            }
        }
    }
}

package com.example.zmanim.widgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.example.zmanim.R
import com.example.zmanim.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun AppData.widgetPlace(config: WidgetConfig): Place? =
    place()

@Composable internal fun WidgetContent(kind: WidgetKind, config: WidgetConfig, data: AppData, place: Place?,
    now: Instant, rows: List<WidgetLine>, openCalendar: Intent, openZmanim: Intent, context: Context) {
    val size = LocalSize.current
    val dark = data.settings.appearance == Appearance.DARK || data.settings.appearance == Appearance.SYSTEM &&
        context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
    val hebrewUi = data.settings.language == Language.HEBREW || data.settings.language == Language.SYSTEM &&
        context.resources.configuration.locales[0].language in setOf("he", "iw")
    fun text(en: String, he: String) = if (hebrewUi) he else en
    val locale = data.settings.productLocale()
    val ink = if (dark) Color(0xFFF5F5F0) else Color(0xFF172F42)
    val muted = if (dark) Color(0xFFC4D4DE) else Color(0xFF4C6271)
    val accent = when(kind) { WidgetKind.EVENTS -> if (dark) Color(0xFFE7B5D0) else Color(0xFF90466C); else -> if (dark) Color(0xFF8AD8CD) else Color(0xFF20786C) }
    val background = when(kind) {
        WidgetKind.DATE -> if (dark) R.drawable.widget_date_background_dark else R.drawable.widget_date_background
        WidgetKind.COMBINED -> if (dark) R.drawable.widget_overview_background_dark else R.drawable.widget_overview_background
        WidgetKind.EVENTS -> if (dark) R.drawable.widget_events_background_dark else R.drawable.widget_events_background
        WidgetKind.ZMANIM -> if (dark) R.drawable.widget_zmanim_background_dark else R.drawable.widget_zmanim_background
    }
    val today = LocalDate.now(place?.zone() ?: ZoneId.systemDefault())
    val (hebrewDate, dateError) = if (kind in setOf(WidgetKind.DATE, WidgetKind.COMBINED))
        CalendarEngine().currentHebrewDate(now, place, config.sunsetRollover) else null to null
    val padding = if (kind == WidgetKind.DATE) { if (size.height < 80.dp) 6.dp else if (size.height < 120.dp) 8.dp else 12.dp } else if (size.height < 90.dp) 10.dp else 14.dp
    val root = GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(background)).padding(padding)
    if (kind == WidgetKind.DATE) {
        val date = if (config.calendar == CalendarSystem.HEBREW) hebrewDate else today
        val jewish = date?.let { com.kosherjava.zmanim.hebrewcalendar.JewishDate(it) }
        val formatter = CalendarEngine().formatter(data.settings.labels == LabelLanguage.HEBREW)
        val day = date?.let { if (config.calendar == CalendarSystem.HEBREW) CalendarEngine().formatter(true).formatHebrewNumber(jewish!!.jewishDayOfMonth).replace("׳", "").replace("״", "") else it.dayOfMonth.toString() } ?: "—"
        val hebrew = config.calendar == CalendarSystem.HEBREW
        val hebrewLabels = data.settings.labels == LabelLanguage.HEBREW
        val monthName = if (date == null) text("Choose location", "בחירת מקום") else if (hebrew)
            formatter.formatMonth(jewish!!) else date.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
        val year = if (hebrew && jewish != null) {
            if (hebrewLabels) formatter.formatHebrewNumber(jewish.jewishYear) else jewish.jewishYear.toString()
        } else date?.year?.toString().orEmpty()
        val month = "$monthName $year"
        val compact = size.width < 150.dp
        val onDate = Color(0xFFF7FCFC)
        val dateMuted = Color(0xFFCAE3E2)
        val weekday = dateWidgetWeekday((date ?: today).dayOfWeek, hebrew && hebrewLabels)
        val secondary = if (hebrew) "\u2066${today.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}\u2069"
            else hebrewDate?.let { CalendarEngine().dateLabel(it, hebrewLabels) } ?: text("Set app location", "הגדרת מיקום האפליקציה")
        val open = if (place == null && (hebrew || config.sunsetRollover))
            Intent(openCalendar).putExtra("destination", "location") else if (config.openingView == OpeningView.ZMANIM) openZmanim else openCalendar
        val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val contentWidth = (size.width.value - padding.value * 2).coerceAtLeast(1f)
        fun fitted(value: String, base: Float, width: Float, bold: Boolean = false): androidx.compose.ui.unit.TextUnit {
            val font = base / fontScale
            val paint = android.text.TextPaint().apply {
                typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                textSize = font * context.resources.displayMetrics.scaledDensity
            }
            return (font * ((width - 2f).coerceAtLeast(1f) * context.resources.displayMetrics.density / paint.measureText(value).coerceAtLeast(1f)).coerceAtMost(1f)).sp
        }
        if (compact) {
            // Keep the original stacked badge in a single launcher cell, including short cells.
            // TextView includes font padding on Samsung; reserve that space for all four lines.
            val height = ((size.height.value - padding.value * 2 - 4f) / 1.2f).coerceAtLeast(1f)
            Column(root.clickable(actionStartActivity(open)), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(weekday, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = ColorProvider(dateMuted), fontSize = fitted(weekday, (height * .14f).coerceAtMost(11f), contentWidth), textAlign = TextAlign.Center), maxLines = 1)
                Text(day, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = ColorProvider(Color(0xFFF8D996)), fontSize = fitted(day, (height * .38f).coerceAtMost(44f), contentWidth, true), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                Text(monthName, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = ColorProvider(onDate), fontSize = fitted(monthName, (height * .16f).coerceAtMost(14f), contentWidth, true), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                Text(year, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = ColorProvider(dateMuted), fontSize = fitted(year, (height * .14f).coerceAtMost(11f), contentWidth), textAlign = TextAlign.Center), maxLines = 1)
            }
        } else {
            val dayWidth = (contentWidth * .30f).coerceAtMost(64f)
            val detailWidth = contentWidth - dayWidth
            val align = if (hebrew) TextAlign.Right else TextAlign.Left
            val short = size.height.value / fontScale < 100f
            Row(root.clickable(actionStartActivity(open)), verticalAlignment = Alignment.CenterVertically) {
                Text(day, modifier = GlanceModifier.width(dayWidth.dp),
                    style = TextStyle(color = ColorProvider(Color(0xFFF8D996)), fontSize = fitted(day, if (short) 32f else 46f, dayWidth, true), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text(weekday, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = ColorProvider(dateMuted), fontSize = fitted(weekday, if (short) 11f else 13f, detailWidth), textAlign = align), maxLines = 1)
                    Text(month, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = ColorProvider(onDate), fontSize = fitted(month, if (short) 12f else 16f, detailWidth, true), fontWeight = FontWeight.Bold, textAlign = align), maxLines = 1)
                    Text(secondary, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(color = ColorProvider(dateMuted), fontSize = fitted(secondary, if (short) 10f else 12f, detailWidth), textAlign = align), maxLines = 1)
                }
            }
        }
    } else if (kind == WidgetKind.COMBINED) {
        val compact = size.height < 140.dp
        val align = if (data.settings.labels == LabelLanguage.HEBREW) TextAlign.Right else TextAlign.Left
        val open = if (config.openingView == OpeningView.ZMANIM) openZmanim else openCalendar
        // One native collection owns the entire surface so swipes over the dates
        // and learning rows both scroll. Tap actions belong to individual items.
        LazyColumn(root) {
            item(itemId = 0L) {
                Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open))) {
                    Text(hebrewDate?.let { CalendarEngine().dateLabel(it, data.settings.labels == LabelLanguage.HEBREW) }
                        ?: text("Choose location for Hebrew date", "בחירת מקום לתאריך העברי"), modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(textAlign = align, color = ColorProvider(ink), fontSize = if (compact) 16.sp else if (size.width >= 260.dp) 25.sp else 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(today.format(DateTimeFormatter.ofPattern(if (compact) "EEE · d MMM yyyy" else "EEEE · d MMM yyyy", locale)), modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(textAlign = align, color = ColorProvider(muted), fontSize = if (compact) 10.sp else 12.sp), maxLines = 1)
                    Spacer(GlanceModifier.height(if (compact) 4.dp else 10.dp))
                }
            }
            if (config.learning.isEmpty()) item(itemId = 1L) {
                Text(text("Choose learning rows in widget settings", "בחירת שורות לימוד בהגדרות היישומון"), modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open)), style = TextStyle(textAlign = align, color = ColorProvider(muted), fontSize = 12.sp))
            }
            items(rows) { row ->
                Column(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(actionStartActivity(row.intent))) {
                    Text(row.title, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(textAlign = align, color = ColorProvider(accent), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(row.subtitle, modifier = GlanceModifier.fillMaxWidth(), style = TextStyle(textAlign = align, color = ColorProvider(ink), fontSize = if (compact) 12.sp else 15.sp), maxLines = 2)
                }
            }
            if (dateError != null && !compact) item(itemId = 2L) {
                Text(text("Set app location for sunset updates", "יש לבחור מיקום לאפליקציה לעדכון בשקיעה"), modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open)), style = TextStyle(textAlign = align, color = ColorProvider(muted), fontSize = 10.sp), maxLines = 1)
            }
        }
    } else {
        Column(root) {
            Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(if (kind == WidgetKind.ZMANIM) openZmanim else openCalendar)), verticalAlignment = Alignment.CenterVertically) {
                Text(if (kind == WidgetKind.ZMANIM) text("Zmanim", "זמנים") else text("Upcoming events", "אירועים קרובים"), modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = ColorProvider(accent), fontWeight = FontWeight.Bold, fontSize = if (size.width >= 240.dp) 18.sp else 15.sp), maxLines = 1)
                Text(today.format(DateTimeFormatter.ofPattern("d MMM", locale)), style = TextStyle(color = ColorProvider(muted), fontSize = 11.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(6.dp))
            if (rows.isEmpty()) Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity(if (kind == WidgetKind.ZMANIM) openZmanim else openCalendar)), verticalAlignment = Alignment.CenterVertically) {
                Text(if (kind == WidgetKind.ZMANIM) text("Choose your zmanim", "בחירת הזמנים שלך") else text("Your day is clear", "אין אירועים קרובים"), style = TextStyle(color = ColorProvider(ink), fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                Text(if (kind == WidgetKind.ZMANIM) text("Open the app to choose a profile and location", "יש לפתוח את האפליקציה לבחירת פרופיל ומקום") else text("Open calendar", "פתיחת לוח השנה"), style = TextStyle(color = ColorProvider(accent), fontSize = 12.sp), maxLines = 1)
            }
            val compactList = size.height < 150.dp
            @Composable fun Line(row: WidgetLine, first: Boolean) {
                val card = if (kind == WidgetKind.EVENTS) { if (dark) R.drawable.widget_event_card_dark else R.drawable.widget_event_card }
                    else if (dark) R.drawable.widget_zman_card_dark else R.drawable.widget_zman_card
                Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).background(ImageProvider(card)).padding(horizontal = 10.dp, vertical = if (compactList) 4.dp else 7.dp).clickable(actionStartActivity(row.intent))) {
                    Text((if (first && kind == WidgetKind.ZMANIM) "▶ " else "") + row.title, style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        textAlign = if (kind == WidgetKind.ZMANIM && data.settings.labels == LabelLanguage.HEBREW) TextAlign.End else TextAlign.Start), maxLines = if (compactList) 1 else 2)
                    Text("\u2066${row.subtitle}\u2069", style = TextStyle(color = ColorProvider(accent), fontSize = if (first && kind == WidgetKind.ZMANIM && !compactList) 17.sp else 12.sp, fontWeight = if (first) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
                    if (config.countdown && row.targetMillis != null && row.targetMillis > now.toEpochMilli()) AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                        setChronometer(R.id.widget_countdown, android.os.SystemClock.elapsedRealtime() + row.targetMillis - System.currentTimeMillis(), null, true)
                        setChronometerCountDown(R.id.widget_countdown, true); setTextColor(R.id.widget_countdown, accent.toArgb())
                    })
                }
            }
            val rowHeight = (if (compactList) 48f else if (config.countdown && kind == WidgetKind.ZMANIM) 70f else 60f) * context.resources.configuration.fontScale.coerceAtLeast(1f)
            val maxRows = ((size.height.value - 65f) / rowHeight).toInt().coerceIn(1, 5)
            if (rows.size > maxRows || kind == WidgetKind.ZMANIM && rows.isNotEmpty()) LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(rows) { row -> Line(row, row == rows.firstOrNull()) }
            } else rows.forEachIndexed { index, row -> Line(row, index == 0) }
            if (kind == WidgetKind.ZMANIM && size.height >= 160.dp) Text((place?.name?.let { "$it · " } ?: "") +
                text("Updated ", "עודכן ") + now.atZone(place?.zone() ?: ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(color = ColorProvider(muted), fontSize = 10.sp), maxLines = 1)
            if (place?.isStale(now) == true && kind == WidgetKind.ZMANIM) Text(text("Refresh device location", "יש לרענן את מיקום המכשיר"), style = TextStyle(color = ColorProvider(accent), fontSize = 10.sp), maxLines = 1)
        }
    }
}

/** Date badges use full weekday names independently of the abbreviated calendar-grid headers. */
internal fun dateWidgetWeekday(day: DayOfWeek, hebrew: Boolean): String = if (hebrew)
    listOf("יום שני", "יום שלישי", "יום רביעי", "יום חמישי", "יום שישי", "שבת", "יום ראשון")[day.value - 1]
else day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)

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
        val month = if (date == null) text("Choose location", "בחירת מקום") else if (config.calendar == CalendarSystem.HEBREW)
            "${formatter.formatMonth(jewish!!)} ${if (data.settings.labels == LabelLanguage.HEBREW) formatter.formatHebrewNumber(jewish.jewishYear) else jewish.jewishYear}" else date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        val compact = size.width < 150.dp
        val onDate = Color(0xFFF7FCFC)
        val dateMuted = Color(0xFFCAE3E2)
        val weekday = weekdayLabel(today.dayOfWeek, data.settings.labels == LabelLanguage.HEBREW, locale)
        val secondary = if (config.calendar == CalendarSystem.HEBREW) today.format(DateTimeFormatter.ofPattern("dd/MM", locale))
            else hebrewDate?.let { CalendarEngine().dateLabel(it, data.settings.labels == LabelLanguage.HEBREW) } ?: text("Set app location", "הגדרת מיקום האפליקציה")
        val open = if (place == null && (config.calendar == CalendarSystem.HEBREW || config.sunsetRollover))
            Intent(openCalendar).putExtra("destination", "location") else if (config.openingView == OpeningView.ZMANIM) openZmanim else openCalendar
        val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val short = size.height.value / fontScale < 100f
        if (short) {
            val dayWidth = (size.width.value * 0.40f).coerceAtMost(64f)
            val baseDayFont = 32f / fontScale
            val dayPaint = android.text.TextPaint().apply {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textSize = baseDayFont * context.resources.displayMetrics.scaledDensity
            }
            val dayFont = baseDayFont * ((dayWidth - 2f) * context.resources.displayMetrics.density / dayPaint.measureText(day).coerceAtLeast(1f)).coerceAtMost(1f)
            // A short launcher cell has room for columns, not three stacked date lines.
            Row(root.clickable(actionStartActivity(open)), verticalAlignment = Alignment.CenterVertically) {
                Text(day, modifier = GlanceModifier.width(dayWidth.dp),
                    style = TextStyle(color = ColorProvider(Color(0xFFF8D996)), fontSize = dayFont.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
                Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text(weekday, style = TextStyle(color = ColorProvider(dateMuted), fontSize = (9f / fontScale).sp), maxLines = 1)
                    val shortMonth = if (date != null && config.calendar == CalendarSystem.GREGORIAN) date.format(DateTimeFormatter.ofPattern("MMM yyyy", locale)) else month
                    Text(shortMonth, style = TextStyle(color = ColorProvider(onDate), fontSize = ((if (compact) 8f else 11f) / fontScale).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(secondary, style = TextStyle(color = ColorProvider(dateMuted), fontSize = (9f / fontScale).sp), maxLines = 1)
                }
            }
        } else Column(root.clickable(actionStartActivity(open)), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(weekday + if (!compact && config.calendar == CalendarSystem.HEBREW) " · ${today.year}" else "", style = TextStyle(color = ColorProvider(dateMuted), fontSize = 11.sp), maxLines = 1)
            if (compact) {
                Text(day, style = TextStyle(color = ColorProvider(Color(0xFFF8D996)), fontSize = if (size.height < 140.dp) 32.sp else 40.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(month, style = TextStyle(color = ColorProvider(onDate), fontSize = 11.sp, textAlign = TextAlign.Center), maxLines = 2)
                Text(secondary, style = TextStyle(color = ColorProvider(dateMuted), fontSize = 9.sp), maxLines = 1)
            } else Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(month, style = TextStyle(color = ColorProvider(onDate), fontSize = if (size.width >= 220.dp) 16.sp else 12.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                    Text(secondary, style = TextStyle(color = ColorProvider(dateMuted), fontSize = 11.sp), maxLines = 2)
                }
                Text(day, modifier = GlanceModifier.width(60.dp), style = TextStyle(color = ColorProvider(Color(0xFFF8D996)), fontSize = 46.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
            }
        }
    } else if (kind == WidgetKind.COMBINED) {
        val compact = size.height < 140.dp
        Column(root.clickable(actionStartActivity(openCalendar))) {
            Text(hebrewDate?.let { CalendarEngine().dateLabel(it, data.settings.labels == LabelLanguage.HEBREW) }
                ?: text("Choose location for Hebrew date", "בחירת מקום לתאריך העברי"), style = TextStyle(color = ColorProvider(ink), fontSize = if (compact) 16.sp else if (size.width >= 260.dp) 25.sp else 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(today.format(DateTimeFormatter.ofPattern(if (compact) "EEE · d MMM yyyy" else "EEEE · d MMM yyyy", locale)), style = TextStyle(color = ColorProvider(muted), fontSize = if (compact) 10.sp else 12.sp), maxLines = 1)
            Spacer(GlanceModifier.height(if (compact) 4.dp else 10.dp))
            if (rows.isEmpty()) Text(text("Choose learning rows in widget settings", "בחירת שורות לימוד בהגדרות היישומון"), style = TextStyle(color = ColorProvider(muted), fontSize = 12.sp))
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(rows) { row ->
                    Column(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(row.title, style = TextStyle(color = ColorProvider(accent), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(row.subtitle, style = TextStyle(color = ColorProvider(ink), fontSize = if (compact) 12.sp else 15.sp), maxLines = 2)
                    }
                }
            }
            if (dateError != null && !compact) Text(text("Set app location for sunset updates", "יש לבחור מיקום לאפליקציה לעדכון בשקיעה"), style = TextStyle(color = ColorProvider(muted), fontSize = 10.sp), maxLines = 1)
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

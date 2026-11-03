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
    place(if (config.location == LocationBinding()) settings.location else config.location)

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
    val root = GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(background)).padding(if (size.height < 90.dp) 10.dp else 14.dp)
    if (kind == WidgetKind.DATE) {
        val date = if (config.calendar == CalendarSystem.HEBREW) hebrewDate else today
        val jewish = date?.let { com.kosherjava.zmanim.hebrewcalendar.JewishDate(it) }
        val formatter = CalendarEngine().formatter(data.settings.labels == LabelLanguage.HEBREW)
        val day = date?.let { if (config.calendar == CalendarSystem.HEBREW) CalendarEngine().formatter(true).formatHebrewNumber(jewish!!.jewishDayOfMonth).replace("׳", "").replace("״", "") else it.dayOfMonth.toString() } ?: "—"
        val month = if (date == null) text("Choose location", "בחירת מקום") else if (config.calendar == CalendarSystem.HEBREW)
            "${formatter.formatMonth(jewish!!)} ${if (data.settings.labels == LabelLanguage.HEBREW) formatter.formatHebrewNumber(jewish.jewishYear) else jewish.jewishYear}" else date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        val tiny = size.width < 150.dp && size.height < 90.dp
        val monthTitle = if (tiny && date != null) {
            if (config.calendar == CalendarSystem.HEBREW) formatter.formatMonth(jewish!!) else date.format(DateTimeFormatter.ofPattern("MMMM", locale))
        } else month
        val dateSubtitle = if (tiny && date != null) {
            if (config.calendar == CalendarSystem.HEBREW) {
                if (data.settings.labels == LabelLanguage.HEBREW) formatter.formatHebrewNumber(jewish!!.jewishYear) else jewish!!.jewishYear.toString()
            } else date.year.toString()
        } else today.format(DateTimeFormatter.ofPattern(if (size.width >= 200.dp) "EEEE · d MMM" else "EEE · d/M", locale))
        val tall = size.width < 170.dp && size.height >= 110.dp
        val onDate = Color(0xFFF7FCFC)
        val dateMuted = Color(0xFFCAE3E2)
        Column(root.clickable(actionStartActivity(openCalendar)), verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (tall) Alignment.CenterHorizontally else Alignment.Start) {
            if (tall) {
                Text(today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale), style = TextStyle(color = ColorProvider(dateMuted), fontSize = 12.sp), maxLines = 1)
                Text(day, style = TextStyle(color = ColorProvider(Color(0xFFF8D996)), fontSize = (size.height.value * 0.32f).coerceIn(36f, 64f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(month, style = TextStyle(color = ColorProvider(onDate), fontSize = 14.sp, textAlign = TextAlign.Center), maxLines = 2)
                if (size.height >= 170.dp) Text(today.format(DateTimeFormatter.ofPattern("d MMM", locale)), style = TextStyle(color = ColorProvider(dateMuted), fontSize = 12.sp), maxLines = 1)
            } else Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(monthTitle, style = TextStyle(color = ColorProvider(onDate), fontSize = if (size.width >= 220.dp) 17.sp else 12.sp, fontWeight = FontWeight.Bold), maxLines = if (size.height < 70.dp) 1 else 2)
                    Text(dateSubtitle, style = TextStyle(color = ColorProvider(dateMuted), fontSize = 11.sp), maxLines = 1)
                }
                Text(day, modifier = GlanceModifier.width(if (size.width < 160.dp) 40.dp else 60.dp),
                    style = TextStyle(color = ColorProvider(Color(0xFFF8D996)), fontSize = if (size.height >= 100.dp) 48.sp else if (size.height < 70.dp) 28.sp else 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
            }
            if (date == null && size.height >= 110.dp) Text(text("Set a location for the sunset date", "יש לבחור מקום לתאריך לפי שקיעה"), style = TextStyle(color = ColorProvider(dateMuted), fontSize = 10.sp), maxLines = 2)
        }
    } else if (kind == WidgetKind.COMBINED) {
        val compact = size.height < 140.dp
        Column(root.clickable(actionStartActivity(openCalendar))) {
            Text(hebrewDate?.let { CalendarEngine().dateLabel(it, data.settings.labels == LabelLanguage.HEBREW) }
                ?: text("Choose location for Hebrew date", "בחירת מקום לתאריך העברי"), style = TextStyle(color = ColorProvider(ink), fontSize = if (compact) 16.sp else if (size.width >= 260.dp) 25.sp else 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(today.format(DateTimeFormatter.ofPattern(if (compact) "EEE · d MMM yyyy" else "EEEE · d MMM yyyy", locale)), style = TextStyle(color = ColorProvider(muted), fontSize = if (compact) 10.sp else 12.sp), maxLines = 1)
            Spacer(GlanceModifier.height(if (compact) 4.dp else 10.dp))
            val calendarDate = hebrewDate ?: today
            val parsha = CalendarEngine().upcomingParsha(calendarDate, data.settings)
            val daf = CalendarEngine().daf(calendarDate, data.settings.labels == LabelLanguage.HEBREW)
            val card = if (dark) R.drawable.widget_info_card_dark else R.drawable.widget_info_card
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                @Composable fun Info(label: String, value: String, color: Color) {
                    Column(GlanceModifier.fillMaxSize().background(ImageProvider(card)).padding(if (compact) 5.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = TextStyle(color = ColorProvider(color), fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(value.ifBlank { "—" }, style = TextStyle(color = ColorProvider(ink), fontSize = if (compact) 12.sp else if (size.height >= 200.dp) 19.sp else 15.sp, fontWeight = FontWeight.Bold), maxLines = if (compact) 1 else 2)
                    }
                }
                Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Info(text("PARSHA", "פרשה"), parsha, if (dark) Color(0xFFF8D996) else Color(0xFF936C20)) }
                Spacer(GlanceModifier.width(6.dp))
                Box(GlanceModifier.defaultWeight().fillMaxHeight()) { Info(text("DAF YOMI", "דף יומי"), daf, accent) }
            }
            if (dateError != null && !compact) Text(text("Set the widget location for sunset updates", "יש לבחור מקום ליישומון לעדכון בשקיעה"), style = TextStyle(color = ColorProvider(muted), fontSize = 10.sp), maxLines = 1)
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
                    Text(row.title, style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        textAlign = if (kind == WidgetKind.ZMANIM && data.settings.labels == LabelLanguage.HEBREW) TextAlign.End else TextAlign.Start), maxLines = if (compactList) 1 else 2)
                    Text("\u2066${row.subtitle}\u2069", style = TextStyle(color = ColorProvider(accent), fontSize = if (first && kind == WidgetKind.ZMANIM && !compactList) 17.sp else 12.sp, fontWeight = if (first) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
                    if (first && config.countdown && row.targetMillis != null && size.height >= 160.dp) AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_countdown).apply {
                        setChronometer(R.id.widget_countdown, android.os.SystemClock.elapsedRealtime() + row.targetMillis - System.currentTimeMillis(), null, true)
                        setChronometerCountDown(R.id.widget_countdown, true); setTextColor(R.id.widget_countdown, accent.toArgb())
                    })
                }
            }
            val rowHeight = (if (compactList) 48f else if (config.countdown && kind == WidgetKind.ZMANIM) 70f else 60f) * context.resources.configuration.fontScale.coerceAtLeast(1f)
            val maxRows = ((size.height.value - 65f) / rowHeight).toInt().coerceIn(1, 5)
            if (kind == WidgetKind.ZMANIM && rows.isNotEmpty()) {
                // Keep the next zman and its native countdown visible while later rows scroll.
                Line(rows.first(), true)
                val later = rows.drop(1)
                if (later.isNotEmpty() && size.height >= 160.dp) {
                    if (later.size > (maxRows - 1).coerceAtLeast(0)) LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) { items(later) { row -> Line(row, false) } }
                    else later.forEach { Line(it, false) }
                }
            } else if (rows.size > maxRows) LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) { items(rows) { row -> Line(row, row == rows.firstOrNull()) } }
            else rows.forEachIndexed { index, row -> Line(row, index == 0) }
            if (kind == WidgetKind.ZMANIM && size.height >= 160.dp) Text((place?.name?.let { "$it · " } ?: "") +
                text("Updated ", "עודכן ") + now.atZone(place?.zone() ?: ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(color = ColorProvider(muted), fontSize = 10.sp), maxLines = 1)
            if (place?.isStale(now) == true && kind == WidgetKind.ZMANIM) Text(text("Refresh device location", "יש לרענן את מיקום המכשיר"), style = TextStyle(color = ColorProvider(accent), fontSize = 10.sp), maxLines = 1)
        }
    }
}

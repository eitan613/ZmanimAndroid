package com.example.zmanim.domain

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

// Gregorian LocalDate is an unambiguous index; it never implies a sunset rollover.
data class CalendarDay(val date: LocalDate, val hebrewDay: Int, val hebrewDate: String,
    val holiday: String, val parsha: String, val specialParsha: String, val omer: String, val special: Boolean,
    val category: String? = null, val candleLighting: Boolean = false)
data class MonthHeader(val label: String, val start: LocalDate, val endExclusive: LocalDate, val selected: Boolean)
data class MonthModel(val primary: MonthHeader, val secondary: List<MonthHeader>, val days: List<CalendarDay>)

class CalendarEngine(private val calculations: CalculationEngine = CalculationEngine()) {
    fun formatter(hebrew: Boolean) = HebrewDateFormatter().apply { isHebrewFormat = hebrew }
    fun dateLabel(date: LocalDate, hebrew: Boolean): String = formatter(hebrew).format(JewishDate(date))
    fun day(date: LocalDate, settings: AppSettings): CalendarDay {
        val jc = JewishCalendar(date).apply {
            inIsrael = settings.inIsrael ?: false
            isUseModernHolidays = settings.modernHolidays
        }
        val f = formatter(settings.labels == LabelLanguage.HEBREW)
        return CalendarDay(date, jc.jewishDayOfMonth, f.format(jc),
            if (settings.showHolidays) listOf(f.formatYomTov(jc), f.formatRoshChodesh(jc)).filter { it.isNotBlank() }.distinct().joinToString(" · ") else "",
            if (settings.showParsha && date.dayOfWeek == DayOfWeek.SATURDAY) f.formatParshah(jc) else "",
            if (settings.showParsha) f.formatSpecialParshah(jc) else "",
            if (settings.showOmer && jc.dayOfOmer > 0) f.formatOmer(jc) else "",
            date.dayOfWeek == DayOfWeek.SATURDAY || jc.isYomTov,
            when {
                date.dayOfWeek == DayOfWeek.SATURDAY -> "shabbat"
                jc.isAssurBemelacha -> "holiday"
                jc.isCholHamoed -> "chol"
                jc.isTaanis -> "fast"
                jc.isChanukah || jc.isPurim -> "festive"
                else -> null
            }, jc.hasCandleLighting())
    }
    fun monthBounds(date: LocalDate, system: CalendarSystem): Pair<LocalDate, LocalDate> = when (system) {
        CalendarSystem.GREGORIAN -> date.withDayOfMonth(1).let { it to it.plusMonths(1) }
        CalendarSystem.HEBREW -> JewishDate(date).let { jd ->
            val first = JewishDate(jd.jewishYear, jd.jewishMonth, 1)
            first.localDate to first.localDate.plusDays(first.daysInJewishMonth.toLong())
        }
    }
    fun monthLabel(date: LocalDate, system: CalendarSystem, hebrew: Boolean, locale: Locale): String = when (system) {
        CalendarSystem.GREGORIAN -> DateTimeFormatter.ofPattern("LLLL uuuu", locale).format(date)
        CalendarSystem.HEBREW -> JewishDate(date).let { "${formatter(hebrew).formatMonth(it)} ${if (hebrew) formatter(true).formatHebrewNumber(it.jewishYear) else it.jewishYear}" }
    }
    fun month(date: LocalDate, settings: AppSettings, locale: Locale): MonthModel {
        val (start, end) = monthBounds(date, settings.primaryCalendar)
        val other = if (settings.primaryCalendar == CalendarSystem.HEBREW) CalendarSystem.GREGORIAN else CalendarSystem.HEBREW
        val secondary = mutableListOf<MonthHeader>()
        var cursor = start
        while (cursor < end) {
            val (a, b) = monthBounds(cursor, other)
            secondary += MonthHeader(monthLabel(cursor, other, settings.labels == LabelLanguage.HEBREW, locale), a, b, date >= a && date < b)
            cursor = b
        }
        val firstWeekday = if (settings.mondayFirst) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        val gridStart = start.minusDays(((start.dayOfWeek.value - firstWeekday.value + 7) % 7).toLong())
        val length = (((end.toEpochDay() - gridStart.toEpochDay() + 6) / 7) * 7).toInt()
        return MonthModel(MonthHeader(monthLabel(date, settings.primaryCalendar, settings.labels == LabelLanguage.HEBREW, locale), start, end, true),
            secondary, (0 until length).map { day(gridStart.plusDays(it.toLong()), settings) })
    }
    fun shift(date: LocalDate, system: CalendarSystem, amount: Int, years: Boolean = false): LocalDate = when (system) {
        CalendarSystem.GREGORIAN -> if (years) date.plusYears(amount.toLong()) else date.plusMonths(amount.toLong())
        CalendarSystem.HEBREW -> JewishDate(date).apply {
            if (years) {
                if (amount >= 0) plusYears(amount, false) else minusYears(-amount, false)
            } else { if (amount >= 0) plusMonths(amount) else minusMonths(-amount) }
        }.localDate
    }
    fun currentHebrewDate(now: Instant, place: Place?, sunset: Boolean): Pair<LocalDate?, String?> {
        if (place == null) return null to "Choose a location for the current Hebrew date."
        val civil = now.atZone(place.zone()).toLocalDate()
        if (!sunset) return civil to null
        val boundary = calculations.seaLevelSunset(civil, place)
            ?: return null to "Sunset is unavailable at this location today. Choose another location or midnight rollover."
        return (if (now >= boundary) civil.plusDays(1) else civil) to null
    }
    fun upcomingParsha(date: LocalDate, settings: AppSettings): String {
        val jc = JewishCalendar(date).apply { inIsrael = settings.inIsrael ?: false }
        return formatter(settings.labels == LabelLanguage.HEBREW).formatParshah(jc.upcomingParshah)
    }
    fun daf(date: LocalDate, hebrew: Boolean): String = runCatching {
        JewishCalendar(date).dafYomiBavli?.let { formatter(hebrew).formatDafYomiBavli(it) } ?: "Unavailable"
    }.getOrDefault("Unavailable")
}

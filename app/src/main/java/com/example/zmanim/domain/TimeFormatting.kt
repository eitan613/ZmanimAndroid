package com.example.zmanim.domain

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

object TimeFormatting {
    fun format(instant: Instant, place: Place, settings: AppSettings, system24Hour: Boolean, locale: Locale): String =
        format(instant, place.zone(), settings, system24Hour, locale)
    fun format(instant: Instant, zone: ZoneId, settings: AppSettings, system24Hour: Boolean, locale: Locale): String {
        val is24 = settings.use24Hour ?: system24Hour
        val pattern = if (is24) (if (settings.showSeconds) "HH:mm:ss" else "HH:mm") else (if (settings.showSeconds) "h:mm:ss a" else "h:mm a")
        val exact = instant.toEpochMilli()
        val rounded = if (settings.showSeconds) exact else when (settings.rounding) {
            Rounding.DOWN -> floor(exact / 60000.0).toLong() * 60000
            Rounding.UP -> ceil(exact / 60000.0).toLong() * 60000
            Rounding.NEAREST -> floor(exact / 60000.0 + 0.5).toLong() * 60000
        }
        val sign = if (settings.showSeconds || rounded == exact) "" else if (rounded > exact) " +" else " −"
        return DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(Instant.ofEpochMilli(rounded)) + sign
    }
    fun occurrence(o: Occurrence, settings: AppSettings, system24Hour: Boolean, locale: Locale): String {
        o.unavailableReason?.let { return it }
        val date = o.date.format(DateTimeFormatter.ofPattern("EEE, d MMM uuuu", locale))
        val he = locale.language in setOf("he", "iw")
        if (o.allDay) return "$date · ${if (he) "כל היום" else "All day"}"
        val zone = ZoneId.of(o.zoneId)
        val start = o.start ?: return if (he) "החישוב אינו זמין" else "Calculation unavailable"
        val end = o.end ?: return "$date · ${format(start, zone, settings, system24Hour, locale)}"
        fun label(i: Instant) = (if (i.atZone(zone).toLocalDate() != o.date) i.atZone(zone).format(DateTimeFormatter.ofPattern("d MMM · ", locale)) else "") + format(i, zone, settings, system24Hour, locale)
        return "$date · \u2066${label(start)} – ${label(end)}\u2069 · ${zone.id.replace('_', ' ')}"
    }
    fun duration(duration: Duration): String = "%d:%02d".format(duration.toMinutes(), duration.seconds % 60)
    fun countdown(now: Instant, target: Instant): String {
        val seconds = Duration.between(now, target).seconds.coerceAtLeast(0)
        return if (seconds >= 86400) "${seconds / 86400}d ${(seconds % 86400) / 3600}h ${(seconds % 3600) / 60}m ${seconds % 60}s" else if (seconds >= 3600) "${seconds / 3600}h ${(seconds % 3600) / 60}m ${seconds % 60}s" else "${seconds / 60}m ${seconds % 60}s"
    }
}

fun AppSettings.productLocale(system: Locale = Locale.getDefault()): Locale = when (language) {
    Language.HEBREW -> Locale.forLanguageTag("he")
    Language.ENGLISH -> Locale.ENGLISH
    Language.SYSTEM -> if (system.language in setOf("he", "iw", "en")) system else Locale.ENGLISH
}

fun weekdayLabel(day: DayOfWeek, hebrew: Boolean, locale: Locale): String = if (hebrew)
    listOf("ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "שבת", "א׳")[day.value - 1]
    else day.getDisplayName(java.time.format.TextStyle.SHORT, locale)

fun Occurrence.overlapsDay(day: LocalDate, zone: ZoneId): Boolean {
    if (allDay) {
        val through = end?.atZone(if (providerId != null) ZoneOffset.UTC else ZoneId.of(zoneId))?.toLocalDate() ?: date.plusDays(1)
        return day >= date && day < through
    }
    if (start == null) return date == day
    return start < day.plusDays(1).atStartOfDay(zone).toInstant() && (end ?: start) > day.atStartOfDay(zone).toInstant()
}

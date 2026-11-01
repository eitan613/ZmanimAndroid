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
    fun duration(duration: Duration): String = "%d:%02d".format(duration.toMinutes(), duration.seconds % 60)
    fun countdown(now: Instant, target: Instant): String {
        val seconds = Duration.between(now, target).seconds.coerceAtLeast(0)
        return if (seconds >= 3600) "${seconds / 3600}h ${(seconds % 3600) / 60}m" else "${seconds / 60}m ${seconds % 60}s"
    }
}

fun AppSettings.productLocale(system: Locale = Locale.getDefault()): Locale = when (language) {
    Language.HEBREW -> Locale.forLanguageTag("he")
    Language.ENGLISH -> Locale.ENGLISH
    Language.SYSTEM -> if (system.language in setOf("he", "iw", "en")) system else Locale.ENGLISH
}

package com.example.zmanim.calculation

import com.kosherjava.zmanim.AstronomicalCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.kosherjava.zmanim.util.GeoLocation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: ZoneId,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }

    companion object {
        val Jerusalem = SavedLocation(
            name = "Jerusalem",
            latitude = 31.778,
            longitude = 35.235,
            zoneId = ZoneId.of("Asia/Jerusalem"),
        )
    }
}

data class SolarDay(
    val date: LocalDate,
    val location: SavedLocation,
    val hebrewDate: String,
    val transliteratedDate: String,
    val sunrise: Instant?,
    val sunset: Instant?,
)

/** A date-based preview. Current Hebrew-date sunset rollover is a separate feature. */
class ZmanimCalculator {
    fun calculate(date: LocalDate, location: SavedLocation): SolarDay {
        // Each request owns its mutable KosherJava objects; none are shared across callers.
        val calendar = AstronomicalCalendar(
            GeoLocation(location.name, location.latitude, location.longitude, location.zoneId),
        ).apply { localDate = date }
        val jewishDate = JewishDate(date)
        return SolarDay(
            date = date,
            location = location,
            hebrewDate = HebrewDateFormatter().apply { isHebrewFormat = true }.format(jewishDate),
            transliteratedDate = HebrewDateFormatter().format(jewishDate),
            sunrise = calendar.seaLevelSunrise,
            sunset = calendar.seaLevelSunset,
        )
    }
}

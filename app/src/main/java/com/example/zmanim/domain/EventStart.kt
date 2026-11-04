package com.example.zmanim.domain

import java.time.*

/** Calendar input carries a local date/time; automatic interval rules handle repeated hours. */
data class EventStart(val date: LocalDate, val time: LocalTime? = null) {
    companion object {
        fun at(instant: Instant, zone: ZoneId): EventStart = instant.atZone(zone).let { EventStart(it.toLocalDate(), it.toLocalTime()) }
    }
}

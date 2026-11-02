package com.example.zmanim.domain

import java.time.*

/** Explicit event-editor input, including which occurrence of a repeated wall hour was tapped. */
data class EventStart(val date: LocalDate, val time: LocalTime? = null, val overlapPolicy: DstOverlapPolicy = DstOverlapPolicy.EARLIER) {
    companion object {
        fun at(instant: Instant, zone: ZoneId): EventStart {
            val local = instant.atZone(zone)
            val offsets = zone.rules.getValidOffsets(local.toLocalDateTime())
            return EventStart(local.toLocalDate(), local.toLocalTime(),
                if (offsets.size == 2 && local.offset == offsets.last()) DstOverlapPolicy.LATER else DstOverlapPolicy.EARLIER)
        }
    }
}

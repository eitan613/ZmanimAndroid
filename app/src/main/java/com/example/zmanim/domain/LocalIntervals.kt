package com.example.zmanim.domain

import java.time.*

/** Preimage of a half-open wall-clock interval under the zone's piecewise constant offsets. */
fun localIntervalSegments(start: LocalDateTime, end: LocalDateTime, zone: ZoneId): List<Pair<Instant, Instant>> {
    require(end > start)
    val first = start.toInstant(ZoneOffset.MAX)
    val last = end.toInstant(ZoneOffset.MIN)
    var cursor = first
    val result = mutableListOf<Pair<Instant, Instant>>()
    while (cursor < last) {
        val offset = zone.rules.getOffset(cursor)
        val next = zone.rules.nextTransition(cursor)?.instant?.let { minOf(it, last) } ?: last
        val a = maxOf(cursor, start.toInstant(offset))
        val b = minOf(next, end.toInstant(offset))
        if (a < b) {
            if (result.lastOrNull()?.second == a) result[result.lastIndex] = result.last().first to b
            else result += a to b
        }
        cursor = next
    }
    return result
}

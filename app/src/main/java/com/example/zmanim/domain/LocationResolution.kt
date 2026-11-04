package com.example.zmanim.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

fun resolveDeviceZone(candidates: List<String>, deviceZone: String): String? = when {
    candidates.size == 1 -> candidates.single()
    deviceZone in candidates -> deviceZone
    else -> null
}

fun retainNearbyZoneChoice(fix: Place, previous: Place?): Place {
    if (previous == null) return fix
    val withHeight = if (fix.latitude == previous.latitude && fix.longitude == previous.longitude) fix.copy(
        elevation = previous.elevation, elevationSource = previous.elevationSource,
        elevationLatitude = previous.elevationLatitude, elevationLongitude = previous.elevationLongitude,
        elevationDataset = previous.elevationDataset, elevationObtainedAt = previous.elevationObtainedAt) else fix
    if (!previous.zoneUserSelected || !previous.zoneConfirmed) return withHeight
    val lat = Math.toRadians((fix.latitude + previous.latitude) / 2)
    val distance = hypot(fix.latitude - previous.latitude, (fix.longitude - previous.longitude) * cos(lat)) * 111_200
    return if (distance < 5_000 && (previous.zoneId in fix.zoneCandidates || fix.zoneCandidates.isEmpty()))
        withHeight.copy(zoneId = previous.zoneId, zoneConfirmed = true, zoneUserSelected = true) else withHeight
}

private val cityMarks = Regex("[\\p{M}'’‘`´\u05f3\u05f4]")
private val citySeparators = Regex("[^\\p{L}\\p{N},]+")
private val citySpaces = Regex(" +")
private val repeatedCityVowels = Regex("([aeiou])\\1+")
fun looseCityQuery(value: String): String = value.replace(repeatedCityVowels, "$1").replace(" ", "")

fun normalizeCityQuery(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(cityMarks, "").lowercase(Locale.ROOT)
    .replace(citySeparators, " ").trim().replace(citySpaces, " ")

fun cityMatchRank(query: String, name: String, aliases: String): Int {
    val names = (listOf(normalizeCityQuery(name)) + aliases.split(',').map(::normalizeCityQuery)).distinct()
    fun loose(value: String) = looseCityQuery(value)
    val q = loose(query)
    return when {
        query in names -> 0
        names.any { it.startsWith(query) } -> 1
        names.any { loose(it) == q || loose(it).startsWith(q) } -> 2
        names.any { it.contains(query) || loose(it).contains(q) } -> 3
        else -> 5
    }
}

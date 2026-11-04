package com.example.zmanim.data

import android.content.Context
import com.example.zmanim.domain.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.serialization.json.*
import java.util.Locale
import java.util.zip.GZIPInputStream

class OfflinePlaces(private val context: Context) {
    data class City(val id: String, val name: String, val country: String, val latitude: Double,
        val longitude: Double, val zone: String, val elevation: Double?, val aliases: String) {
        fun place() = Place(name = "$name, $country", latitude = latitude, longitude = longitude,
            zoneId = zone, elevation = elevation, localityId = id, displayCountry = country, holidayInIsrael = when (country) { "IL" -> true; "PS" -> false; else -> false }, elevationSource = if (elevation != null) "GeoNames city elevation; confirm before use" else null)
    }
    private val corrections by lazy {
        appJson.parseToJsonElement(context.assets.open("locality-corrections.json").bufferedReader().use { it.readText() }).jsonObject
    }
    private fun corrected(city: City): City {
        // GeoNames erroneously assigns Betar Illit's Hebrew alias to nearby Battir.
        val cleaned = if (city.id == "284375") city.copy(aliases = normalizeCityQuery("Battir,בתיר")) else city
        val correction = corrections[city.id]?.jsonObject ?: return cleaned
        if (kotlin.math.abs(city.latitude - correction.getValue("sourceLatitude").jsonPrimitive.double) > 0.03 ||
            kotlin.math.abs(city.longitude - correction.getValue("sourceLongitude").jsonPrimitive.double) > 0.03) return cleaned
        return cleaned.copy(country = correction.getValue("country").jsonPrimitive.content,
            zone = correction.getValue("zone").jsonPrimitive.content,
            aliases = cleaned.aliases + "," + normalizeCityQuery(correction.getValue("aliases").jsonPrimitive.content))
    }
    private val cities: List<City> by lazy {
        GZIPInputStream(context.assets.open("cities.tsv.gzip")).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 8) null else City(f[0], f[1], f[2], f[3].toDouble(), f[4].toDouble(), f[5], f[6].toDoubleOrNull(), normalizeCityQuery(f[7])).let(::corrected)
            }.toList()
        }
    }
    private data class IndexedCity(val city: City, val names: List<String>, val loose: List<String>)
    private val searchIndex by lazy {
        cities.map { city ->
            val names = (city.aliases.split(',') + normalizeCityQuery(city.name)).filter { it.isNotBlank() }.distinct()
            IndexedCity(city, names, names.map(::looseCityQuery))
        }
    }
    fun search(query: String, limit: Int = 30): List<City> {
        val q = normalizeCityQuery(query)
        if (q.length < 2) return emptyList()
        val loose = looseCityQuery(q)
        return searchIndex.asSequence().mapNotNull { entry ->
            val rank = when {
                q in entry.names -> 0
                entry.names.any { it.startsWith(q) } -> 1
                entry.loose.any { it == loose || it.startsWith(loose) } -> 2
                entry.names.any { it.contains(q) } || entry.loose.any { it.contains(loose) } -> 3
                else -> return@mapNotNull null
            }
            entry.city to rank
        }.sortedWith(compareBy({ it.second }, { it.first.name }, { it.first.country })).take(limit).map { it.first }.toList()
    }
    private data class Ring(val offset: Long, val count: Int)
    private data class Polygon(val zone: String, val bounds: List<Double>, val rings: List<Ring>)
    private val polygons: List<Polygon> by lazy {
        val json = appJson.parseToJsonElement(context.assets.open("timezone-index.json").bufferedReader().use { it.readText() }).jsonArray
        json.map { element ->
            val obj = element.jsonObject
            Polygon(obj.getValue("zone").jsonPrimitive.content,
                obj.getValue("bounds").jsonArray.map { it.jsonPrimitive.double },
                obj.getValue("rings").jsonArray.map { Ring(it.jsonArray[0].jsonPrimitive.long, it.jsonArray[1].jsonPrimitive.int) })
        }
    }
    // Map the point file on demand. The OS pages only the polygons near a requested coordinate;
    // keeping the entire global coastline as FloatArrays can exhaust a phone's heap.
    private val points: MappedByteBuffer by lazy {
        val version = appJson.parseToJsonElement(context.assets.open("place-data.json").bufferedReader().use { it.readText() })
            .jsonObject.getValue("timezones").jsonObject.getValue("version").jsonPrimitive.content
        val expected = polygons.maxOf { p -> p.rings.maxOf { it.offset + it.count * 8L } }
        val file = File(context.cacheDir, "timezone-points-$version.bin")
        if (!file.exists() || file.length() != expected) {
            val temporary = File.createTempFile("timezone-points-", ".tmp", context.cacheDir)
            try {
                GZIPInputStream(context.assets.open("timezone-points.bin.gzip")).use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.length() == expected) { "The offline timezone dataset is incomplete." }
                check(temporary.renameTo(file)) { "The offline timezone cache could not be prepared." }
            } finally { temporary.delete() }
        }
        RandomAccessFile(file, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
    }
    // Multiple matches stay explicit: overlapping boundaries cannot be silently resolved.
    fun timezones(latitude: Double, longitude: Double): List<String> = polygons.asSequence().filter {
        longitude >= it.bounds[0] && longitude <= it.bounds[2] && latitude >= it.bounds[1] && latitude <= it.bounds[3]
    }.filter { polygon ->
        inside(longitude, latitude, polygon.rings.first()) && polygon.rings.drop(1).none { inside(longitude, latitude, it) }
    }.map { it.zone }.distinct().toList()

    private fun inside(x: Double, y: Double, ring: Ring): Boolean {
        val buffer = points
        var result = false
        var j = ring.count - 1
        for (i in 0 until ring.count) {
            val a = (ring.offset + i * 8L).toInt(); val b = (ring.offset + j * 8L).toInt()
            val xi = buffer.getFloat(a).toDouble(); val yi = buffer.getFloat(a + 4).toDouble()
            val xj = buffer.getFloat(b).toDouble(); val yj = buffer.getFloat(b + 4).toDouble()
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) result = !result
            j = i
        }
        return result
    }
}

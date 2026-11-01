package com.example.zmanim.data

import android.content.Context
import com.example.zmanim.domain.Place
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
            zoneId = zone, elevation = elevation, elevationSource = if (elevation != null) "GeoNames city elevation; confirm before use" else null)
    }
    private val cities: List<City> by lazy {
        GZIPInputStream(context.assets.open("cities.tsv.gzip")).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 8) null else City(f[0], f[1], f[2], f[3].toDouble(), f[4].toDouble(), f[5], f[6].toDoubleOrNull(), f[7].lowercase(Locale.ROOT))
            }.toList()
        }
    }
    fun search(query: String, limit: Int = 30): List<City> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.length < 2) return emptyList()
        return cities.asSequence().filter { it.aliases.contains(q) || it.name.contains(q, true) }
            .sortedBy { if (it.name.startsWith(q, true)) 0 else 1 }.take(limit).toList()
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

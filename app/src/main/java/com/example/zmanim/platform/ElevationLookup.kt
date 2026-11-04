package com.example.zmanim.platform

import android.content.Context
import com.example.zmanim.data.appJson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Optional, user-initiated terrain-height query. Never uses GPS ellipsoid altitude. */
class ElevationLookup(context: Context) {
    @Serializable data class Result(val latitude: Double, val longitude: Double, val meters: Double,
        val dataset: String, val obtainedAt: Long)
    private val cache = context.getSharedPreferences("terrain-elevation", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var lastRequest = 0L
    suspend fun lookup(latitude: Double, longitude: Double): Result = withContext(Dispatchers.IO) {
        require(latitude.isFinite() && latitude in -60.0..60.0 && longitude.isFinite() && longitude in -180.0..180.0) {
            "SRTM elevation covers latitudes 60°S–60°N. Enter a height manually outside this area."
        }
        val key = "$latitude,$longitude"
        mutex.withLock {
            cache.getString(key, null)?.let { return@withContext appJson.decodeFromString<Result>(it) }
            delay((1100 - (android.os.SystemClock.elapsedRealtime() - lastRequest)).coerceAtLeast(0))
            lastRequest = android.os.SystemClock.elapsedRealtime()
            val connection = URL("https://api.opentopodata.org/v1/srtm90m?locations=$key").openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 10_000; connection.readTimeout = 10_000
                check(connection.responseCode != 429) { "The elevation service is busy or rate limited. Try later or enter a height manually." }
                check(connection.responseCode == 200) { "Elevation lookup failed. Keep manual height or try again later." }
                val json = appJson.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject
                check(json["status"]?.jsonPrimitive?.content == "OK") { "Elevation lookup is unavailable. Enter a height manually." }
                val result = json.getValue("results").jsonArray.first().jsonObject
                val meters = result["elevation"]?.jsonPrimitive?.doubleOrNull
                    ?: error("No terrain height is available for these coordinates. Enter a height manually.")
                check(meters.isFinite()) { "The service returned an invalid height." }
                Result(latitude, longitude, meters, result.getValue("dataset").jsonPrimitive.content, System.currentTimeMillis()).also {
                    cache.edit().putString(key, appJson.encodeToString(it)).apply()
                }
            } finally { connection.disconnect() }
        }
    }
}

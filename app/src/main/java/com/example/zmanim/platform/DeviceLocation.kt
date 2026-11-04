package com.example.zmanim.platform

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import androidx.core.content.ContextCompat
import com.example.zmanim.ZmanimApplication
import com.example.zmanim.data.AppRepository
import com.example.zmanim.data.OfflinePlaces
import com.example.zmanim.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeviceLocation(private val context: Context, private val places: OfflinePlaces) {
    private val manager = context.getSystemService(LocationManager::class.java)
    fun hasAccess() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackgroundAccess() = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasFineAccess() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun providers(): List<String> = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter {
        (it != LocationManager.GPS_PROVIDER || hasFineAccess()) && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
    }
    @SuppressLint("MissingPermission") // hasAccess() checks runtime access; a revoked grant is reported to the caller.
    suspend fun refresh(): Place = withContext(Dispatchers.IO) {
        check(hasAccess()) { "Allow location access or choose a saved place." }
        val providers = providers()
        check(providers.isNotEmpty()) { "Turn on device location or choose a saved place." }
        val cached = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .minByOrNull { (android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos).takeIf { age -> age >= 0 } ?: Long.MAX_VALUE }
        val location = if (cached != null && (android.os.SystemClock.elapsedRealtimeNanos() - cached.elapsedRealtimeNanos) in 0..900_000_000_000L) cached
        else withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { continuation ->
                val registrationLock = Any()
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        synchronized(registrationLock) {
                            try { manager.removeUpdates(this) } catch (_: SecurityException) { }
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }
                    @Deprecated("Platform callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation {
                    synchronized(registrationLock) { try { manager.removeUpdates(listener) } catch (_: SecurityException) { } }
                }
                try {
                    synchronized(registrationLock) {
                        if (continuation.isActive) providers.forEach { provider -> manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }
                    }
                } catch (e: SecurityException) {
                    try { manager.removeUpdates(listener) } catch (_: SecurityException) { }
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        } ?: cached ?: error("No location fix was obtained. Try again outdoors or choose a saved place.")
        toPlace(location)
    }
    fun toPlace(location: Location): Place {
        val zones = places.timezones(location.latitude, location.longitude)
        val resolved = resolveDeviceZone(zones, java.time.ZoneId.systemDefault().id)
        return Place(id = AppRepository.DEVICE_ID, name = "Device location", latitude = location.latitude,
            longitude = location.longitude, zoneId = resolved ?: java.time.ZoneId.systemDefault().id, zoneCandidates = zones, zoneConfirmed = resolved != null, obtainedAt = location.time,
            accuracyMeters = location.accuracy).also { it.validate() }
    }
    private fun backgroundIntent() = PendingIntent.getBroadcast(context, 200,
        Intent(context, LocationUpdateReceiver::class.java).setAction("com.example.zmanim.LOCATION"),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
    @SuppressLint("MissingPermission") // Both foreground and background grants are checked below.
    fun configureBackground(enabled: Boolean) {
        runCatching { manager.removeUpdates(backgroundIntent()) }
        if (!enabled || !hasAccess() || !hasBackgroundAccess()) return
        providers().forEach { provider -> runCatching { manager.requestLocationUpdates(provider, 15 * 60 * 1000L, 1000f, backgroundIntent()) } }
    }
}
class LocationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION") val location = intent.getParcelableExtra<Location>(LocationManager.KEY_LOCATION_CHANGED) ?: return
        val app = context.applicationContext as ZmanimApplication
        val pending = goAsync()
        app.runBackground(pending) {
            val place = retainNearbyZoneChoice(app.location.toPlace(location), app.repository.data.devicePlace)
            app.repository.update { it.copy(devicePlace = place) }
        }
    }
}

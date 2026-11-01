package com.example.zmanim

import android.app.Application
import android.content.BroadcastReceiver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.work.*
import com.example.zmanim.data.*
import com.example.zmanim.domain.*
import com.example.zmanim.platform.*
import com.example.zmanim.widgets.WidgetUpdates
import java.time.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

class ZmanimApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: AppRepository; private set
    lateinit var places: OfflinePlaces; private set
    lateinit var calendars: CalendarGateway; private set
    lateinit var reminders: ReminderScheduler; private set
    lateinit var location: DeviceLocation; private set
    private var maintenanceRequest: Job? = null
    private var calendarObserved = false
    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { requestMaintenance() }
    }
    @Volatile var maintenanceError: String? = null
    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        places = OfflinePlaces(this)
        calendars = CalendarGateway(this)
        reminders = ReminderScheduler(this)
        location = DeviceLocation(this, places)
        reminders.createChannels()
        repository.onChanged = { requestMaintenance() }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily-maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(6, TimeUnit.HOURS).build())
        ensureCalendarObserver()
        requestMaintenance()
    }
    @Synchronized fun ensureCalendarObserver() {
        if (calendarObserved && !calendars.canRead()) {
            contentResolver.unregisterContentObserver(calendarObserver); calendarObserved = false
        }
        if (!calendarObserved && calendars.canRead()) {
            try { contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver); calendarObserved = true }
            catch (_: SecurityException) { calendarObserved = false }
        }
    }
    suspend fun awaitData(): AppData = repository.state.filterIsInstance<RepositoryState.Ready>().first().data
    fun requestMaintenance() {
        maintenanceRequest?.cancel()
        maintenanceRequest = scope.launch {
            delay(500)
            WorkManager.getInstance(this@ZmanimApplication).enqueueUniqueWork("reconcile", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<MaintenanceWorker>().setInitialDelay(1, TimeUnit.SECONDS).build())
        }
    }
    fun runBackground(pending: BroadcastReceiver.PendingResult, action: suspend (ZmanimApplication) -> Unit) {
        scope.launch {
            try { withTimeout(25_000) { awaitData(); action(this@ZmanimApplication) } }
            catch (e: Exception) { maintenanceError = e.message ?: "Background update failed. Open the app to retry." }
            finally { pending.finish() }
        }
    }
    suspend fun maintain() {
        ensureCalendarObserver()
        var data = awaitData()
        try {
            val links = calendars.reconcile(data, LocalDate.now())
            if (links != data.exports) { repository.update { it.copy(exports = links) }; data = repository.data }
            reminders.reconcile(data)
            reminders.updateDateNotification(data)
            location.configureBackground(data.settings.backgroundLocation)
            WidgetUpdates.refresh(this, data)
            maintenanceError = null
        } catch (e: Exception) { maintenanceError = e.message; throw e }
    }
}
class MaintenanceWorker(context: android.content.Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as ZmanimApplication).maintain(); Result.success()
    } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
}

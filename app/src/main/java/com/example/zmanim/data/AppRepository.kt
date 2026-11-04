package com.example.zmanim.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.zmanim.data.db.*
import com.example.zmanim.domain.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.appSettingsStore by preferencesDataStore("calendar_settings")
val appJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
sealed interface RepositoryState {
    data object Loading : RepositoryState
    data class Ready(val data: AppData) : RepositoryState
    data class Failed(val message: String) : RepositoryState
}

class AppRepository(private val context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "zmanim.db").addMigrations(MIGRATION_1_2).build()
    private val store = context.appSettingsStore
    private val key = stringPreferencesKey("settings_v1")
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<RepositoryState>(RepositoryState.Loading)
    val state: StateFlow<RepositoryState> = _state.asStateFlow()
    val data: AppData get() = (_state.value as? RepositoryState.Ready)?.data ?: error("Data is not loaded yet.")
    var onChanged: (() -> Unit)? = null

    init { reload() }
    fun reload() { scope.launch { mutex.withLock { load() }; onChanged?.invoke() } }
    private suspend fun load() {
        try {
            val dao = db.dao()
            dao.pendingSettings()?.let { pending ->
                appJson.decodeFromString<AppSettings>(pending.payload)
                store.edit { it[key] = pending.payload }
                dao.clearSettings()
            }
            val storedSettings = store.data.first()[key]
            var settings = storedSettings?.let { appJson.decodeFromString<AppSettings>(it) } ?: AppSettings()
            var profiles = dao.profiles().map { appJson.decodeFromString<Profile>(it.payload) }
            if (!settings.migratedPrototype) {
                val legacy = ZmanimPreferences(context).selectedMethods.first().map { it.id }
                if (legacy.isNotEmpty() && profiles.isEmpty()) {
                    val profile = Profile(name = "My zmanim", selected = legacy)
                    dao.putProfileRow(ProfileRow(profile.id, profile.name, appJson.encodeToString(profile)))
                    profiles = listOf(profile)
                    settings = settings.copy(activeProfileId = profile.id)
                }
                settings = settings.copy(migratedPrototype = true)
                store.edit { it[key] = appJson.encodeToString(settings) }
            }
            // Explicit legacy schedules are already established; never infer over them.
            if (!settings.scheduleInitialized && settings.inIsrael != null) settings = settings.copy(scheduleInitialized = true)
            val places = dao.places().map { appJson.decodeFromString<Place>(it.payload) }
            var loaded = AppData(settings, profiles, places.filter { it.id != DEVICE_ID },
                dao.events().map { appJson.decodeFromString<Event>(it.payload) },
                dao.widgets().map { appJson.decodeFromString<WidgetConfig>(it.payload) },
                dao.exports().map { appJson.decodeFromString<ExportLink>(it.payload) }, places.find { it.id == DEVICE_ID })
            loaded = initializeSchedule(loaded)
            if (storedSettings != appJson.encodeToString(loaded.settings)) store.edit { it[key] = appJson.encodeToString(loaded.settings) }
            _state.value = RepositoryState.Ready(loaded)
        } catch (e: Exception) { _state.value = RepositoryState.Failed(e.message ?: "Your data could not be loaded. Retry to keep it intact.") }
    }

    suspend fun update(change: (AppData) -> AppData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val old = data
            var next = change(old)
            next = initializeSchedule(next)
            if (old == next) return@withContext
            val dao = db.dao()
            db.runInTransaction {
                fun <T> sync(before: List<T>, after: List<T>, id: (T) -> String, put: (T) -> Unit, delete: (String) -> Unit) {
                    val oldById = before.associateBy(id)
                    val newIds = after.map(id).toSet()
                    before.filter { id(it) !in newIds }.forEach { delete(id(it)) }
                    after.filter { oldById[id(it)] != it }.forEach(put)
                }
                sync(old.profiles, next.profiles, { it.id }, { dao.putProfileRow(ProfileRow(it.id, it.name, appJson.encodeToString(it))) }, dao::deleteProfileRow)
                sync(old.places + listOfNotNull(old.devicePlace), next.places + listOfNotNull(next.devicePlace), { it.id }, { dao.putPlaceRow(PlaceRow(it.id, it.name, appJson.encodeToString(it))) }, dao::deletePlaceRow)
                sync(old.events, next.events, { it.id }, { dao.putEventRow(EventRow(it.id, it.title, appJson.encodeToString(it))) }, dao::deleteEventRow)
                sync(old.widgets, next.widgets, { it.id }, { dao.putWidgetRow(WidgetRow(it.id, it.kind.name, appJson.encodeToString(it))) }, dao::deleteWidgetRow)
                sync(old.exports, next.exports, { it.id }, { dao.putExportRow(ExportRow(it.id, it.eventId, appJson.encodeToString(it))) }, dao::deleteExportRow)
                if (old.settings != next.settings) dao.putSettings(SettingsJournalRow(1, appJson.encodeToString(next.settings)))
            }
            // A Room journal makes a committed import recoverable if the process dies between
            // its record transaction and the DataStore preference write.
            withContext(NonCancellable) {
                try {
                    if (old.settings != next.settings) { store.edit { it[key] = appJson.encodeToString(next.settings) }; dao.clearSettings() }
                    _state.value = RepositoryState.Ready(next)
                } catch (e: Exception) { load(); throw e }
            }
        }
        onChanged?.invoke()
    }
    suspend fun settings(change: (AppSettings) -> AppSettings) = update { it.copy(settings = change(it.settings)) }
    suspend fun saveProfile(profile: Profile) {
        CalculationEngine().validateProfile(profile)
        update { d ->
            require(d.events.filter { it.profileId == profile.id && it.timeKind == EventTimeKind.ZMAN }.all { event ->
                event.methodId in MethodCatalog.byId || profile.custom.any { it.id == event.methodId }
            }) { "This calculation is used by an event. Replace its event binding first." }
            d.copy(profiles = d.profiles.filterNot { it.id == profile.id } + profile,
            settings = if (d.settings.activeProfileId == null) d.settings.copy(activeProfileId = profile.id) else d.settings) }
    }
    suspend fun updateProfile(id: String, change: (Profile) -> Profile) = update { data ->
        val current = data.profile(id) ?: error("This profile no longer exists.")
        val updated = change(current)
        CalculationEngine().validateProfile(updated)
        require(data.events.filter { it.profileId == id && it.timeKind == EventTimeKind.ZMAN }.all { event ->
            event.methodId in MethodCatalog.byId || updated.custom.any { it.id == event.methodId }
        }) { "This calculation is used by an event. Replace its event binding first." }
        data.copy(profiles = data.profiles.map { if (it.id == id) updated else it })
    }
    suspend fun deleteProfile(id: String) = update { d ->
        require(d.events.none { it.profileId == id }) { "This profile is used by events. Change those events before deleting it." }
        require(d.widgets.none { it.profileId == id }) { "This profile is used by a widget. Reconfigure that widget first." }
        val remaining = d.profiles.filterNot { it.id == id }
        d.copy(profiles = remaining, settings = d.settings.copy(activeProfileId = if (d.settings.activeProfileId == id) remaining.firstOrNull()?.id else d.settings.activeProfileId))
    }
    suspend fun duplicateProfile(profile: Profile) {
        val ids = profile.custom.associate { it.id to newId() }
        fun mapped(id: String) = ids[id] ?: id
        saveProfile(profile.copy(id = newId(), name = "${profile.name} copy", selected = profile.selected.map(::mapped),
            custom = profile.custom.map { it.copy(id = mapped(it.id), base = mapped(it.base), dayStart = mapped(it.dayStart), dayEnd = mapped(it.dayEnd)) },
            options = profile.options.copy(candleLightingNightfallId = profile.options.candleLightingNightfallId?.let(::mapped)),
            displayNames = profile.displayNames.mapKeys { mapped(it.key) },
            reminders = profile.reminders.map { it.copy(methodId = mapped(it.methodId), reminders = it.reminders.map { r -> r.copy(id = newId()) }) }))
    }
    suspend fun savePlace(place: Place) { place.validate(); update { it.copy(places = it.places.filterNot { p -> p.id == place.id } + place) } }
    suspend fun deletePlace(id: String) = update { d ->
        require(d.settings.location.savedId != id && d.events.none { it.location.savedId == id }) { "This location is in use. Change its bindings before deleting it." }
        d.copy(places = d.places.filterNot { it.id == id })
    }
    suspend fun saveEvent(event: Event) { OccurrenceEngine().validate(event, data); update { it.copy(events = it.events.filterNot { e -> e.id == event.id } + event) } }
    suspend fun deleteEvent(id: String) = update { data ->
        val removed = mutableSetOf(id)
        var added: Boolean
        do { added = false; data.events.forEach { event -> if (event.parentId in removed && removed.add(event.id)) added = true } } while (added)
        data.copy(events = data.events.filterNot { it.id in removed })
    }
    suspend fun excludeOccurrence(event: Event, date: LocalDate) = saveEvent(event.copy(excludedDates = event.excludedDates + date.toString()))
    suspend fun replaceOccurrence(original: Event, date: LocalDate, replacement: Event) {
        OccurrenceEngine().validate(replacement, data)
        update { d -> d.copy(events = d.events.filterNot { it.id == original.id || it.id == replacement.id } +
            original.copy(excludedDates = original.excludedDates + date.toString()) + replacement.copy(parentId = original.id, originalOccurrenceDate = date.toString(), recurrence = Recurrence())) }
    }
    suspend fun splitSeries(original: Event, date: LocalDate, replacement: Event) {
        OccurrenceEngine().validate(replacement, data)
        update { data ->
            val cut = date.toString()
            val futureExceptions = original.excludedDates.filter { it >= cut }.toSet()
            val replacementId = if (date <= LocalDate.parse(original.anchorDate)) original.id else replacement.id
            val retained = data.events.filterNot { it.id == original.id }.map { child ->
                if (child.parentId == original.id && (child.originalOccurrenceDate ?: child.anchorDate) >= cut)
                    child.copy(parentId = replacementId) else child
            }
            val previous = if (date <= LocalDate.parse(original.anchorDate)) emptyList() else listOf(original.copy(
                recurrence = original.recurrence.copy(until = date.minusDays(1).toString()), excludedDates = original.excludedDates - futureExceptions))
            data.copy(events = retained + previous + replacement.copy(id = replacementId,
                parentId = if (replacementId == original.id) original.parentId else original.id,
                excludedDates = replacement.excludedDates + futureExceptions))
        }
    }
    companion object {
        const val DEVICE_ID = "device-location"
        const val SNAPSHOT_ID = "current-location-snapshot"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS settings_journal (id INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}

/** Only reviewed locality metadata initializes holiday rules. A timezone is not a locality. */
internal fun initializeSchedule(data: AppData): AppData {
    if (data.settings.scheduleInitialized) return data
    if (data.settings.inIsrael != null) return data.copy(settings = data.settings.copy(scheduleInitialized = true))
    val place = data.place() ?: return data
    return data.copy(settings = data.settings.copy(inIsrael = place.holidayInIsrael ?: return data, scheduleInitialized = true))
}

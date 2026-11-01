package com.example.zmanim.data

import com.example.zmanim.domain.*
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class BackupDocument(val format: String = "zmanim-backup", val version: Int = 1,
    val createdAt: String = Instant.now().toString(), val settings: AppSettings? = null,
    val profiles: List<Profile> = emptyList(), val places: List<Place> = emptyList(), val events: List<Event> = emptyList())
data class BackupSelection(val settings: Boolean = true, val profiles: Boolean = true,
    val places: Boolean = false, val events: Boolean = false)
data class ImportPreview(val document: BackupDocument, val profileConflicts: Int, val placeConflicts: Int, val eventConflicts: Int)

class BackupService {
    fun export(data: AppData, selection: BackupSelection): String {
        // Events include their calculation dependencies, even if the profile checkbox is off.
        val profiles = if (selection.profiles) data.profiles else data.profiles.filter { p -> selection.events && data.events.any { it.profileId == p.id } }
        val settings = if (selection.settings) data.settings.copy(
            location = if (selection.places) data.settings.location else LocationBinding(),
            activeProfileId = data.settings.activeProfileId.takeIf { id -> profiles.any { it.id == id } },
            visibleCalendarIds = emptySet(), backgroundLocation = false, dateNotification = false,
            approximateAlarmsAccepted = false, eventNotifications = false, zmanNotifications = false) else null
        return appJson.encodeToString(BackupDocument(settings = settings, profiles = profiles,
            places = if (selection.places) data.places else emptyList(),
            events = if (selection.events) data.events.map { it.copy(exportCalendarId = null,
                location = if (selection.places) it.location else LocationBinding()) } else emptyList()))
    }
    fun preview(text: String, existing: AppData): ImportPreview {
        require(text.toByteArray().size <= 20_000_000) { "Backup files must be smaller than 20 MB." }
        val doc = appJson.decodeFromString<BackupDocument>(text)
        require(doc.format == "zmanim-backup" && doc.version == 1) { "This backup format or version is not supported." }
        require(doc.profiles.size <= 1000 && doc.places.size <= 10000 && doc.events.size <= 100000) { "This backup has too many records." }
        require(doc.profiles.map { it.id }.distinct().size == doc.profiles.size &&
            doc.places.map { it.id }.distinct().size == doc.places.size && doc.events.map { it.id }.distinct().size == doc.events.size) { "The backup contains duplicate IDs." }
        doc.profiles.forEach { CalculationEngine().validateProfile(it) }
        doc.places.forEach { it.validate() }
        val prospective = existing.copy(settings = doc.settings ?: existing.settings,
            profiles = existing.profiles.filter { p -> doc.profiles.none { it.id == p.id } } + doc.profiles,
            places = existing.places.filter { p -> doc.places.none { it.id == p.id } } + doc.places)
        doc.events.forEach { OccurrenceEngine().validate(it, prospective, allowUnresolvedContext = true) }
        return ImportPreview(doc, doc.profiles.count { p -> existing.profiles.any { it.id == p.id } },
            doc.places.count { p -> existing.places.any { it.id == p.id } }, doc.events.count { p -> existing.events.any { it.id == p.id } })
    }
    fun merge(preview: ImportPreview, existing: AppData, action: ConflictAction, applySettings: Boolean): AppData {
        val doc = preview.document
        val profileIds = doc.profiles.associate { it.id to if (action == ConflictAction.COPY && existing.profiles.any { p -> p.id == it.id }) newId() else it.id }
        val placeIds = doc.places.associate { it.id to if (action == ConflictAction.COPY && existing.places.any { p -> p.id == it.id }) newId() else it.id }
        val eventIds = doc.events.associate { it.id to if (action == ConflictAction.COPY && existing.events.any { p -> p.id == it.id }) newId() else it.id }
        fun binding(b: LocationBinding) = if (b.mode == LocationMode.FOLLOW) LocationBinding(LocationMode.FOLLOW)
            else b.copy(savedId = placeIds[b.savedId] ?: b.savedId)
        fun <T> merge(old: List<T>, imported: List<T>, id: (T) -> String): List<T> = when (action) {
            ConflictAction.KEEP -> old + imported.filter { item -> old.none { id(it) == id(item) } }
            else -> old.filter { item -> imported.none { id(it) == id(item) } } + imported
        }
        val profiles = doc.profiles.map { it.copy(id = profileIds.getValue(it.id), remindersEnabled = false) }
        val places = doc.places.map { it.copy(id = placeIds.getValue(it.id)) }
        val events = doc.events.map { it.copy(id = eventIds.getValue(it.id), profileId = profileIds[it.profileId] ?: it.profileId,
            parentId = eventIds[it.parentId] ?: it.parentId, location = binding(it.location), exportCalendarId = null, remindersEnabled = false) }
        val settings = if (applySettings && doc.settings != null) doc.settings.copy(location = binding(doc.settings.location),
            activeProfileId = profileIds[doc.settings.activeProfileId] ?: doc.settings.activeProfileId,
            visibleCalendarIds = existing.settings.visibleCalendarIds, backgroundLocation = existing.settings.backgroundLocation,
            approximateAlarmsAccepted = existing.settings.approximateAlarmsAccepted,
            dateNotification = existing.settings.dateNotification, onboardingComplete = existing.settings.onboardingComplete,
            migratedPrototype = true, eventNotifications = false, zmanNotifications = false) else existing.settings
        val result = existing.copy(settings = settings, profiles = merge(existing.profiles, profiles) { it.id },
            places = merge(existing.places, places) { it.id }, events = merge(existing.events, events) { it.id })
        result.events.forEach { OccurrenceEngine().validate(it, result, allowUnresolvedContext = true) }
        return result
    }
}

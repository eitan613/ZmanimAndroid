package com.example.zmanim.domain

import java.time.*
import java.util.UUID
import kotlinx.serialization.Serializable

fun newId(): String = UUID.randomUUID().toString()

@Serializable enum class CalendarSystem { GREGORIAN, HEBREW }
@Serializable enum class CalendarView { MONTH, WEEK, DAY }
@Serializable enum class Appearance { SYSTEM, LIGHT, DARK }
@Serializable enum class Language { SYSTEM, ENGLISH, HEBREW }
@Serializable enum class LabelLanguage { TRANSLITERATED, HEBREW }
@Serializable enum class OpeningView { CALENDAR, ZMANIM }
@Serializable enum class DoubleTapAction { ZMANIM, NEW_EVENT, DAY }
@Serializable enum class HebrewDayBoundary { CIVIL, SUNSET }
@Serializable enum class Rounding { DOWN, NEAREST, UP }
@Serializable enum class LocationMode { APP, SAVED, FOLLOW }
@Serializable enum class CalendarDirection { AUTOMATIC, LTR, RTL }
@Serializable enum class ReminderMode { NOTIFICATION, ALARM }
@Serializable enum class Frequency { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }
@Serializable enum class MissingDayPolicy { PREVIOUS, NEXT, SKIP }
@Serializable enum class AdarPolicy { ADAR_I, ADAR_II, BOTH, SKIP }
@Serializable enum class LeapAdarPolicy { ADAR, SKIP }
@Serializable enum class EventTimeKind { ALL_DAY, FIXED, ZMAN }
@Serializable enum class FormulaKind { FIXED_MINUTES, SOLAR_ANGLE, TEMPORAL_HOURS }
@Serializable enum class SolarSide { MORNING, EVENING }
@Serializable enum class ReminderOwner { APP, CALENDAR }
@Serializable enum class ConflictAction { KEEP, REPLACE, COPY }
@Serializable enum class WidgetKind { EVENTS, ZMANIM, DATE, COMBINED }
@Serializable enum class WidgetTap { CALENDAR, EVENT }

@Serializable
data class Place(
    val id: String = newId(), val name: String, val latitude: Double, val longitude: Double,
    val zoneId: String, val elevation: Double? = null, val elevationSource: String? = null,
    val obtainedAt: Long? = null, val accuracyMeters: Float? = null,
    val localityId: String? = null, val displayCountry: String? = null, val holidayInIsrael: Boolean? = null,
    val zoneCandidates: List<String> = emptyList(), val zoneConfirmed: Boolean = true, val zoneUserSelected: Boolean = false,
    val elevationLatitude: Double? = null, val elevationLongitude: Double? = null,
    val elevationDataset: String? = null, val elevationObtainedAt: Long? = null,
) {
    fun zone(): ZoneId = ZoneId.of(zoneId)
    fun validate() {
        require(name.isNotBlank()) { "Enter a place name." }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between −90 and 90." }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between −180 and 180." }
        zone()
        require(elevation == null || elevation.isFinite()) { "Enter a finite elevation." }
    }
    fun isStale(now: Instant): Boolean = id == "device-location" && obtainedAt != null && now.toEpochMilli() - obtainedAt !in 0L..86_400_000L
}

@Serializable
data class LocationBinding(val mode: LocationMode = LocationMode.APP, val savedId: String? = null)

@Serializable
data class CalculationOptions(
    val useElevation: Boolean = false,
    val astronomicalChatzos: Boolean = true,
    val astronomicalHalfDays: Boolean = false,
    val candleLightingMinutes: Double? = null,
    val ateretTorahMinutes: Double? = null,
    val candleLightingNightfallId: String? = null,
)

@Serializable
data class CustomZman(
    val id: String = newId(), val name: String, val hebrewName: String = "",
    val kind: FormulaKind, val base: String = "sea-level-sunset", val amount: Double = 0.0,
    val side: SolarSide = SolarSide.EVENING,
    val dayStart: String = "sea-level-sunrise", val dayEnd: String = "sea-level-sunset",
) {
    fun references(): Set<String> = when (kind) {
        FormulaKind.SOLAR_ANGLE -> emptySet()
        FormulaKind.FIXED_MINUTES -> setOf(base)
        FormulaKind.TEMPORAL_HOURS -> setOf(base, dayStart, dayEnd)
    }
}

@Serializable
data class ReminderRule(val id: String = newId(), val minutesBefore: Int = 0,
    val mode: ReminderMode = ReminderMode.NOTIFICATION, val enabled: Boolean = true,
    val sound: String? = null, val vibrate: Boolean = true, val snoozeMinutes: Int = 5)

@Serializable
data class ZmanReminder(val methodId: String, val reminders: List<ReminderRule>)

@Serializable
data class Profile(
    val id: String = newId(), val name: String = "My zmanim",
    val selected: List<String> = emptyList(), val hiddenGroups: Set<String> = emptySet(),
    val custom: List<CustomZman> = emptyList(), val options: CalculationOptions = CalculationOptions(),
    val reminders: List<ZmanReminder> = emptyList(), val remindersEnabled: Boolean = true,
    val displayNames: Map<String, String> = emptyMap(),
)

@Serializable
data class Recurrence(
    val frequency: Frequency = Frequency.NONE, val interval: Int = 1,
    val until: String? = null, val count: Int? = null, val weekdays: Set<Int> = emptySet(),
    val missingDay: MissingDayPolicy? = null, val ordinaryAdar: AdarPolicy? = null,
    val leapAdar: LeapAdarPolicy? = null,
)

@Serializable
data class Event(
    val id: String = newId(), val title: String, val description: String = "",
    val venue: String = "", val guests: String = "", val calendar: CalendarSystem = CalendarSystem.GREGORIAN,
    // The original civil date preserves the exact Hebrew source date, including Adar identity.
    val anchorDate: String, val repeatCalendar: CalendarSystem = calendar, val timeKind: EventTimeKind = EventTimeKind.FIXED,
    val localTime: String = "09:00", val previousEvening: Boolean = false,
    val durationMinutes: Long = 60, val zoneId: String,
    val recurrence: Recurrence = Recurrence(), val location: LocationBinding = LocationBinding(),
    val profileId: String? = null, val methodId: String? = null, val offsetMinutes: Double = 0.0,
    val reminders: List<ReminderRule> = emptyList(), val remindersEnabled: Boolean = true,
    val excludedDates: Set<String> = emptySet(),
    val exportCalendarId: Long? = null, val reminderOwner: ReminderOwner = ReminderOwner.APP,
    val parentId: String? = null, val originalOccurrenceDate: String? = null,
)

@Serializable
data class AppSettings(
    val appearance: Appearance = Appearance.SYSTEM, val language: Language = Language.ENGLISH,
    val labels: LabelLanguage = LabelLanguage.HEBREW,
    val openingView: OpeningView = OpeningView.CALENDAR,
    val primaryCalendar: CalendarSystem = CalendarSystem.HEBREW,
    val calendarView: CalendarView = CalendarView.MONTH,
    val mondayFirst: Boolean = false, val calendarDirection: CalendarDirection = CalendarDirection.AUTOMATIC,
    val swapScrollAxes: Boolean = false, val doubleTap: DoubleTapAction = DoubleTapAction.ZMANIM,
    val showHolidays: Boolean = true, val modernHolidays: Boolean = false,
    val showParsha: Boolean = true, val showOmer: Boolean = false, val colorSpecialDays: Boolean = true,
    val holidayTint: Long = 0xFFF7E7CC, val shabbatTint: Long = 0xFFF7E7CC,
    val specialDayColors: Map<String, Long> = emptyMap(),
    val scheduleInitialized: Boolean = false,
    val showEventBasics: Boolean = true, val showDeviceEvents: Boolean = true, val inIsrael: Boolean? = null,
    val hebrewDayBoundary: HebrewDayBoundary? = null,
    val use24Hour: Boolean? = null, val showSeconds: Boolean = false,
    val rounding: Rounding = Rounding.NEAREST, val showCountdown: Boolean = true,
    val activeProfileId: String? = null, val location: LocationBinding = LocationBinding(LocationMode.SAVED),
    val eventNotifications: Boolean = true, val zmanNotifications: Boolean = true,
    val approximateAlarmsAccepted: Boolean = false,
    val backgroundLocation: Boolean = false, val dateNotification: Boolean = false,
    val dateNotificationSunset: Boolean = true,
    val visibleCalendarIds: Set<Long> = emptySet(),
    val onboardingComplete: Boolean = false, val migratedPrototype: Boolean = false,
)

@Serializable
data class WidgetConfig(
    val id: String, val kind: WidgetKind, val itemCount: Int = 3,
    val profileId: String? = null,
    val calendar: CalendarSystem = CalendarSystem.HEBREW, val sunsetRollover: Boolean = true,
    val tap: WidgetTap = WidgetTap.CALENDAR, val countdown: Boolean = false,
    val openingView: OpeningView = OpeningView.CALENDAR,
    val learning: List<String> = listOf("parsha", "bavli"),
)

@Serializable
data class ExportLink(
    val id: String, val eventId: String, val occurrenceDate: String,
    val calendarId: Long, val providerId: Long, val fingerprint: String,
    val state: String = "MANAGED", val reason: String = "",
)

@Serializable
data class AppData(
    val settings: AppSettings = AppSettings(), val profiles: List<Profile> = emptyList(),
    val places: List<Place> = emptyList(), val events: List<Event> = emptyList(),
    val widgets: List<WidgetConfig> = emptyList(), val exports: List<ExportLink> = emptyList(),
    val devicePlace: Place? = null,
) {
    fun profile(id: String? = settings.activeProfileId): Profile? = profiles.find { it.id == id }
    fun place(binding: LocationBinding = settings.location): Place? {
        val effective = if (binding.mode == LocationMode.APP && binding.savedId == null) settings.location else binding
        val result = if (effective.savedId != null) places.find { it.id == effective.savedId }
            else if (effective.mode == LocationMode.FOLLOW) devicePlace else null
        return result?.takeIf { it.zoneConfirmed }
    }
}

data class CalculationDetails(
    val methodId: String, val formula: String, val date: LocalDate, val place: Place,
    val effectiveElevation: String, val options: CalculationOptions,
)
sealed interface CalculationValue {
    data object NotApplicable : CalculationValue
    data class Time(val instant: Instant) : CalculationValue
    data class Length(val duration: Duration) : CalculationValue
    data class Unavailable(val reason: String) : CalculationValue
}
data class CalculatedZman(val id: String, val label: String, val value: CalculationValue, val details: CalculationDetails)
data class Occurrence(
    val key: String, val eventId: String, val date: LocalDate, val title: String,
    val start: Instant?, val end: Instant?, val allDay: Boolean, val zoneId: String,
    val unavailableReason: String? = null, val sourceCalendarId: Long? = null, val providerId: Long? = null,
    val applicable: Boolean = true,
)

@Serializable
data class OccurrenceSnapshot(val key: String, val eventId: String, val date: String, val title: String,
    val start: String?, val end: String?, val allDay: Boolean, val zone: String, val error: String?,
    val calendarId: Long?, val providerId: Long?) {
    fun restore() = Occurrence(key, eventId, LocalDate.parse(date), title, start?.let(Instant::parse), end?.let(Instant::parse), allDay, zone, error, calendarId, providerId)
    companion object {
        fun from(o: Occurrence) = OccurrenceSnapshot(o.key, o.eventId, o.date.toString(), o.title, o.start?.toString(), o.end?.toString(), o.allDay, o.zoneId, o.unavailableReason, o.sourceCalendarId, o.providerId)
    }
}

fun AppSettings.calendarRtl(system: CalendarSystem = primaryCalendar): Boolean = when (calendarDirection) {
    CalendarDirection.AUTOMATIC -> system == CalendarSystem.HEBREW
    CalendarDirection.LTR -> false
    CalendarDirection.RTL -> true
}

fun Profile.methodDependencies(roots: Collection<String>): Set<String> {
    val result = mutableSetOf<String>()
    fun visit(id: String) {
        if (!result.add(id)) return
        custom.find { it.id == id }?.references()?.forEach(::visit)
        if (MethodCatalog.byId[id]?.needsCandleOffset == true) options.candleLightingNightfallId?.let(::visit)
    }
    roots.forEach(::visit)
    return result
}

fun Profile.usesAteretTorah(data: AppData): Boolean = methodDependencies(selected +
    reminders.filter { it.reminders.isNotEmpty() }.map { it.methodId } +
    data.events.filter { it.profileId == id && it.timeKind == EventTimeKind.ZMAN }.mapNotNull { it.methodId } +
    listOfNotNull(options.candleLightingNightfallId)).any { MethodCatalog.byId[it]?.needsAteretOffset == true }

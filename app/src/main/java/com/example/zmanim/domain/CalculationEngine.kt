package com.example.zmanim.domain

import com.kosherjava.zmanim.ComprehensiveZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.*
import kotlin.math.*

class ZmanMethod(
    val id: String, val group: String, val label: String, val isDuration: Boolean,
    val needsAteretOffset: Boolean = false, val needsCandleOffset: Boolean = false, val usesElevation: Boolean = false, val supportsHalfDays: Boolean = false,
    val explicitElevation: Boolean = false, val sourceMethod: String = "", val sourceDefinition: String = "", val hebrewLabel: String = "", val definition: String = "",
    val evaluate: (ComprehensiveZmanimCalendar) -> Any?,
)

data class ZmanGroup(val id: String, val english: String, val hebrew: String)
val zmanGroups = listOf(
    ZmanGroup("dawn", "Alos · dawn", "עלות השחר"), ZmanGroup("misheyakir", "Misheyakir", "משיכיר"),
    ZmanGroup("sunrise", "Sunrise", "הנץ החמה"), ZmanGroup("shema", "Latest Shema", "סוף זמן קריאת שמע"),
    ZmanGroup("prayer", "Latest teffilah", "סוף זמן תפילה"), ZmanGroup("chatzos", "Chatzos", "חצות"),
    ZmanGroup("mincha-gedolah", "Mincha gedolah", "מנחה גדולה"), ZmanGroup("mincha-ketanah", "Mincha ketanah", "מנחה קטנה"),
    ZmanGroup("plag", "Plag", "פלג המנחה"), ZmanGroup("sunset", "Sunset", "שקיעה"),
    ZmanGroup("nightfall", "Tzeis · nightfall", "צאת הכוכבים"), ZmanGroup("duration", "Sha’ah zmanis · durations", "שעה זמנית"),
    ZmanGroup("twilight", "Other twilight calculations", "בין השמשות"), ZmanGroup("candles", "Candle lighting", "הדלקת נרות"),
    ZmanGroup("chametz", "Chametz deadlines", "זמני חמץ"), ZmanGroup("lunar", "Lunar calculations", "זמני הלבנה"),
    ZmanGroup("custom", "Custom zmanim", "זמנים אישיים"),
)

data class CalculationQuery(val date: LocalDate, val place: Place, val profile: Profile, val methodIds: List<String> = profile.selected, val inIsrael: Boolean = false)

class CalculationEngine {
    fun calculate(query: CalculationQuery): List<CalculatedZman> {
        val p = query.profile
        val place = query.place
        val elevationError = when {
            place.elevation == null || (place.elevationLatitude != null && (place.elevationLatitude != place.latitude || place.elevationLongitude != place.longitude)) -> "This method requires location height. Edit the location, choose a sea-level method, or disable elevation for other supported zmanim."
            place.elevation < 0 -> "The calculation library does not support below-sea-level adjustment. Choose a sea-level method or disable elevation for other supported zmanim."
            else -> null
        }
        val calendar = ComprehensiveZmanimCalendar(GeoLocation(place.name, place.latitude, place.longitude,
            place.elevation?.takeIf { it >= 0.0 } ?: 0.0, place.zone())).apply {
            localDate = query.date
            isUseElevation = p.options.useElevation
            isUseAstronomicalChatzos = p.options.astronomicalChatzos
            isUseAstronomicalChatzosForOtherZmanim = p.options.astronomicalHalfDays
            p.options.candleLightingMinutes?.let { candleLightingOffset = it }
            p.options.ateretTorahMinutes?.let { ateretTorahSunsetOffset = it }
        }
        val jewishDay = JewishCalendar(query.date).apply { inIsrael = query.inIsrael }
        val memo = mutableMapOf<String, CalculationValue>()
        val visiting = mutableSetOf<String>()
        fun evaluate(id: String): CalculationValue {
            memo[id]?.let { return it }
            if (!visiting.add(id)) return CalculationValue.Unavailable("This formula contains a dependency cycle.")
            val method = MethodCatalog.byId[id]
            val custom = p.custom.find { it.id == id }
            val result = try {
                if (method?.group == "chametz" && !(jewishDay.jewishMonth == JewishCalendar.NISSAN && jewishDay.jewishDayOfMonth == 14)) {
                    CalculationValue.NotApplicable
                } else if (method?.needsCandleOffset == true && !jewishDay.hasCandleLighting()) {
                    CalculationValue.NotApplicable
                } else if (method?.needsCandleOffset == true && query.date.dayOfWeek != DayOfWeek.FRIDAY && jewishDay.isAssurBemelacha) {
                    p.options.candleLightingNightfallId?.let { evaluate(it) }
                        ?: CalculationValue.Unavailable("Choose a nightfall method for holiday candle lighting in this profile.")
                } else if (elevationError != null && (method?.explicitElevation == true || p.options.useElevation && method?.usesElevation == true)) {
                    CalculationValue.Unavailable(elevationError)
                } else if (method != null) {
                    when {
                        method.needsCandleOffset && p.options.candleLightingMinutes == null -> CalculationValue.Unavailable("Choose a candle-lighting offset in this profile.")
                        method.needsAteretOffset && p.options.ateretTorahMinutes == null -> CalculationValue.Unavailable("Choose the Ateret Torah sunset offset in this profile.")
                        else -> when (val value = method.evaluate(calendar)) {
                            is Instant -> CalculationValue.Time(value)
                            is Duration -> CalculationValue.Length(value)
                            else -> if (method.group == "lunar" || method.id.startsWith("polar-")) CalculationValue.NotApplicable else CalculationValue.Unavailable("The required solar or lunar event is not available for this date and location.")
                        }
                    }
                } else if (custom != null && custom.references().any { evaluate(it) == CalculationValue.NotApplicable }) {
                    CalculationValue.NotApplicable
                } else if (custom != null) {
                    fun instant(ref: String): Instant = when (val value = evaluate(ref)) {
                        is CalculationValue.Time -> value.instant
                        is CalculationValue.Unavailable -> throw IllegalArgumentException("Dependency unavailable: ${value.reason}")
                        else -> throw IllegalArgumentException("A duration cannot be used as a time anchor.")
                    }
                    val value = when (custom.kind) {
                        FormulaKind.FIXED_MINUTES -> instant(custom.base).plusMillis(checkedOffsetMillis(custom.amount * 60_000))
                        FormulaKind.SOLAR_ANGLE -> {
                            require(custom.amount.isFinite() && custom.amount > 0 && custom.amount < 90) { "Solar angle must be between 0° and 90°." }
                            (if (custom.side == SolarSide.MORNING) calendar.getSunriseOffsetByDegrees(90 + custom.amount)
                            else calendar.getSunsetOffsetByDegrees(90 + custom.amount))
                                ?: throw IllegalArgumentException("The sun does not reach this angle on this date.")
                        }
                        FormulaKind.TEMPORAL_HOURS -> {
                            val start = instant(custom.dayStart)
                            val end = instant(custom.dayEnd)
                            require(end > start) { "The temporal day must end after it starts." }
                            instant(custom.base).plusMillis(checkedOffsetMillis(Duration.between(start, end).toMillis() / 12.0 * custom.amount))
                        }
                    }
                    CalculationValue.Time(value)
                } else CalculationValue.Unavailable("The selected calculation no longer exists.")
            } catch (e: IllegalArgumentException) {
                CalculationValue.Unavailable(e.message ?: "This calculation is unavailable.")
            } catch (e: ArithmeticException) {
                CalculationValue.Unavailable("The formula exceeds the supported time range.")
            }
            visiting.remove(id)
            memo[id] = result
            return result
        }
        fun usesElevation(id: String, seen: Set<String> = emptySet()): Boolean {
            if (id in seen) return false
            MethodCatalog.byId[id]?.let { return it.usesElevation }
            return p.custom.find { it.id == id }?.references()?.any { usesElevation(it, seen + id) } == true
        }
        fun referenceDescription(id: String): String = p.custom.find { it.id == id }?.let { formulaText(it, p.custom) }
            ?: MethodCatalog.byId[id]?.let { effectiveMethodDescription(it, p) } ?: "Missing calculation"
        return query.methodIds.distinct().map { id ->
            val custom = p.custom.find { it.id == id }
            val method = MethodCatalog.byId[id]
            val label = p.displayNames[id] ?: custom?.name ?: method?.let { effectiveMethodDescription(it, p) } ?: "Missing calculation"
            val holidayNightfall = method?.needsCandleOffset == true && query.date.dayOfWeek != DayOfWeek.FRIDAY && jewishDay.isAssurBemelacha
            val effectiveId = if (holidayNightfall) p.options.candleLightingNightfallId ?: id else id
            val formula = when {
                custom != null -> formulaText(custom, p.custom)
                holidayNightfall -> p.options.candleLightingNightfallId?.let { "Holiday candle lighting · ${referenceDescription(it)}" }
                    ?: "Choose a nightfall method for holiday candle lighting in this profile."
                method?.needsCandleOffset == true -> p.options.candleLightingMinutes?.let { "$it minutes before sea-level sunset" }
                    ?: "Choose a candle-lighting offset in this profile."
                method?.needsAteretOffset == true -> "$label · Nightfall: ${p.options.ateretTorahMinutes?.let { "$it minutes after sunset" } ?: "choose an offset"}"
                else -> label
            }
            val elevation = when {
                !p.options.useElevation && !p.methodDependencies(listOf(effectiveId)).any { MethodCatalog.byId[it]?.explicitElevation == true } -> "Sea level"
                !usesElevation(effectiveId) -> "Elevation does not affect this method"
                place.elevation == null -> "Elevation not provided"
                else -> "${place.elevation} m"
            }
            CalculatedZman(id, label, evaluate(id), CalculationDetails(id, formula, query.date, place, elevation, p.options))
        }
    }

    fun validateProfile(profile: Profile) {
        require(profile.displayNames.all { (id, name) -> name.isNotBlank() && (id in MethodCatalog.byId || profile.custom.any { it.id == id }) }) { "Display names must refer to an existing calculation and cannot be blank." }
        require(profile.reminders.map { it.methodId }.distinct().size == profile.reminders.size) { "Each calculation must have one reminder collection." }
        val rules = profile.reminders.flatMap { it.reminders }
        require(rules.map { it.id }.distinct().size == rules.size) { "Reminder IDs must be unique." }
        require(profile.selected.distinct().size == profile.selected.size) { "Selected calculations must be unique." }
        require(profile.reminders.flatMap { it.reminders }.all { it.snoozeMinutes in 1..120 }) { "Snooze must be between 1 and 120 minutes." }
        require(profile.name.isNotBlank()) { "Enter a profile name." }
        require(profile.custom.map { it.id }.distinct().size == profile.custom.size) { "Custom calculation IDs must be unique." }
        require(profile.reminders.flatMap { it.reminders }.all { it.minutesBefore in 0..525600 }) { "Reminder offsets must be whole minutes from 0 to 525600." }
        require(profile.custom.none { it.id.isBlank() || it.id in MethodCatalog.byId }) { "Custom calculation IDs must not shadow built-in calculations." }
        val definitions = profile.custom.associateBy { it.id }
        require(profile.reminders.all { it.methodId in MethodCatalog.byId || it.methodId in definitions }) { "A reminder refers to a missing calculation." }
        val visited = mutableSetOf<String>()
        val active = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in visited) return
            require(active.add(id)) { "Custom formulas cannot contain a dependency cycle." }
            val definition = definitions[id]
            if (definition != null) {
                require(definition.name.isNotBlank() && definition.amount.isFinite()) { "Enter a name and a finite amount." }
                if (definition.kind == FormulaKind.SOLAR_ANGLE) require(definition.amount > 0 && definition.amount < 90) { "Angle must be between 0° and 90°." }
                definition.references().forEach { ref ->
                    require(ref in definitions || ref in MethodCatalog.byId) { "A formula refers to a missing calculation." }
                    require(MethodCatalog.byId[ref]?.isDuration != true) { "Choose a time, not a duration, for formula boundaries." }
                    visit(ref)
                }
            }
            active.remove(id); visited.add(id)
        }
        pNightfallValidation(profile)
        definitions.keys.forEach(::visit)
        // A nightfall formula may not depend back on candle lighting.
        fun dependsOnCandles(id: String, seen: Set<String> = emptySet()): Boolean =
            id == "candle-lighting" || id !in seen && definitions[id]?.references()?.any { dependsOnCandles(it, seen + id) } == true
        require(profile.options.candleLightingNightfallId?.let { !dependsOnCandles(it) } != false) { "Holiday nightfall cannot depend on candle lighting." }
        require(profile.selected.all { it in definitions || it in MethodCatalog.byId }) { "A selected calculation does not exist." }
        listOfNotNull(profile.options.candleLightingMinutes, profile.options.ateretTorahMinutes).forEach {
            require(it.isFinite() && it >= 0 && it <= 1440) { "Offsets must be between 0 and 1440 minutes." }
        }
    }

    fun seaLevelSunset(date: LocalDate, place: Place): Instant? =
        (calculate(CalculationQuery(date, place, Profile(), listOf("sea-level-sunset"))).first().value as? CalculationValue.Time)?.instant
}

fun formulaText(custom: CustomZman, definitions: List<CustomZman> = emptyList()): String {
    fun name(id: String) = MethodCatalog.byId[id]?.label ?: definitions.find { it.id == id }?.name ?: "Custom calculation"
    return when (custom.kind) {
        FormulaKind.FIXED_MINUTES -> "${name(custom.base)} ${if (custom.amount < 0) "−" else "+"} ${abs(custom.amount)} minutes"
        FormulaKind.SOLAR_ANGLE -> "Sun ${custom.amount}° below the horizon · ${custom.side.name.lowercase()}"
        FormulaKind.TEMPORAL_HOURS -> "${name(custom.base)} + ${custom.amount} × ( ${name(custom.dayEnd)} − ${name(custom.dayStart)} ) / 12"
    }
}

fun effectiveMethodDescription(method: ZmanMethod, profile: Profile?): String {
    if (profile?.options?.astronomicalHalfDays != true || !method.supportsHalfDays) return method.label
    val boundaries = method.label.substringAfter(" · Day: ", "").split(" → ")
    val noon = if (profile.options.astronomicalChatzos) "Astronomical solar transit" else "Midpoint of sunrise and sunset"
    val name = method.label.substringBefore(" · Day:")
    return when {
        boundaries.size < 2 -> "$name · Profile half-day temporal-hour policy"
        method.group in setOf("shema", "prayer") -> "$name · Morning (six temporal hours): ${boundaries[0]} → $noon"
        method.group in setOf("mincha-gedolah", "mincha-ketanah", "plag") -> "$name · Afternoon (six temporal hours): $noon → ${boundaries[1]}"
        else -> method.label
    }
}

internal fun checkedOffsetMillis(value: Double): Long {
    require(value.isFinite() && value >= Long.MIN_VALUE.toDouble() && value < Long.MAX_VALUE.toDouble()) {
        "The formula exceeds the supported time range."
    }
    return value.roundToLong()
}

private fun pNightfallValidation(profile: Profile) {
    val id = profile.options.candleLightingNightfallId ?: return
    require(MethodCatalog.byId[id]?.group == "nightfall" || profile.custom.any { it.id == id }) {
        "Choose a nightfall method or custom time for holiday candle lighting."
    }
}

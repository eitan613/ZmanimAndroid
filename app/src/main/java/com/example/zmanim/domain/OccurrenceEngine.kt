package com.example.zmanim.domain

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.*
import java.time.temporal.ChronoUnit

class OccurrenceEngine(private val calculations: CalculationEngine = CalculationEngine()) {
    fun validate(event: Event, data: AppData, allowUnresolvedContext: Boolean = false) {
        require(event.title.isNotBlank()) { "Enter an event title." }
        val anchor = LocalDate.parse(event.anchorDate)
        event.originalOccurrenceDate?.let(LocalDate::parse)
        ZoneId.of(event.zoneId)
        LocalTime.parse(event.localTime)
        require(event.durationMinutes in 1..525600) { "Duration must be between 1 minute and 1 year." }
        require(event.offsetMinutes.isFinite()) { "Enter a finite offset." }
        checkedOffsetMillis(event.offsetMinutes * 60000)
        require(event.reminders.all { it.minutesBefore in 0..525600 }) { "Reminder offsets must be whole minutes from 0 to 525600." }
        require(event.reminders.filter { it.mode == ReminderMode.ALARM }.all { it.snoozeMinutes in 1..120 }) { "Snooze must be between 1 and 120 minutes." }
        val r = event.recurrence
        require(r.interval in 1..1000) { "Recurrence interval must be between 1 and 1000." }
        require(r.count == null || r.count in 1..100000) { "Occurrence count must be positive and at most 100,000." }
        require(r.until == null || LocalDate.parse(r.until) >= anchor) { "The end date must not precede the first occurrence." }
        require(r.weekdays.all { it in 1..7 }) { "Choose valid weekdays." }
        if (r.frequency in setOf(Frequency.MONTHLY, Frequency.YEARLY)) {
            require(r.missingDay != null) { "Choose what happens when the day does not exist." }
            if (event.repeatCalendar == CalendarSystem.HEBREW && r.frequency == Frequency.YEARLY) {
                val jd = JewishDate(anchor)
                if (jd.jewishMonth == JewishDate.ADAR && !jd.isJewishLeapYear)
                    require(r.ordinaryAdar != null) { "Choose which Adar to use in leap years." }
                if (jd.jewishMonth == JewishDate.ADAR_II || (jd.jewishMonth == JewishDate.ADAR && jd.isJewishLeapYear))
                    require(r.leapAdar != null) { "Choose what happens in ordinary years." }
            }
        }
        if (event.timeKind == EventTimeKind.ALL_DAY && event.calendar == CalendarSystem.HEBREW) {
            require(allowUnresolvedContext || data.settings.hebrewDayBoundary != null) { "Choose the app-wide Hebrew all-day boundary in Settings first." }
            if (data.settings.hebrewDayBoundary == HebrewDayBoundary.SUNSET)
                require(allowUnresolvedContext || data.place(event.location) != null) { "Choose a location for sunset boundaries." }
        }
        if (event.timeKind == EventTimeKind.ZMAN) {
            val profile = data.profile(event.profileId)
            require(profile != null && event.methodId != null) { "Choose a profile and calculation." }
            require(event.methodId in profile.selected || profile.custom.any { it.id == event.methodId } || event.methodId in MethodCatalog.byId) { "The event calculation no longer exists." }
            require(MethodCatalog.byId[event.methodId]?.isDuration != true) { "Choose a timed calculation, not a duration." }
            require(allowUnresolvedContext || data.place(event.location) != null) { "Choose a location for the event calculation." }
        }
    }

    fun dates(event: Event, from: LocalDate, untilExclusive: LocalDate): List<LocalDate> {
        require(untilExclusive >= from && ChronoUnit.DAYS.between(from, untilExclusive) <= 36600) { "Request at most 100 years at a time." }
        val anchor = LocalDate.parse(event.anchorDate)
        val r = event.recurrence
        val limit = r.until?.let(LocalDate::parse)?.plusDays(1)?.let { minOf(it, untilExclusive) } ?: untilExclusive
        if (anchor >= limit) return emptyList()
        val candidates = sequence {
            when (r.frequency) {
                Frequency.NONE -> yield(anchor)
                Frequency.DAILY, Frequency.WEEKLY -> {
                    // Recurrence count is evaluated before EXDATE, as in calendar recurrence rules.
                    var d = anchor
                    while (d < limit) {
                        val days = ChronoUnit.DAYS.between(anchor, d)
                        val weekdays = r.weekdays.ifEmpty { if (r.frequency == Frequency.WEEKLY) setOf(anchor.dayOfWeek.value) else (1..7).toSet() }
                        val weekStart = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
                        val weeks = ChronoUnit.DAYS.between(weekStart, d) / 7
                        if ((if (r.frequency == Frequency.DAILY) days % r.interval == 0L else weeks % r.interval == 0L) && d.dayOfWeek.value in weekdays) yield(d)
                        d = d.plusDays(1)
                    }
                }
                Frequency.MONTHLY -> {
                    val hebrew = JewishDate(anchor)
                    var index = 0
                    while (true) {
                        val first = if (event.repeatCalendar == CalendarSystem.GREGORIAN) anchor.withDayOfMonth(1).plusMonths(index.toLong())
                        else JewishDate(hebrew.jewishYear, hebrew.jewishMonth, 1).apply { plusMonths(index) }.localDate
                        if (first >= limit) break
                        val next = if (event.repeatCalendar == CalendarSystem.GREGORIAN) first.plusMonths(1)
                        else first.plusDays(JewishDate(first).daysInJewishMonth.toLong())
                        val targetDay = if (event.repeatCalendar == CalendarSystem.GREGORIAN) anchor.dayOfMonth else hebrew.jewishDayOfMonth
                        resolveDay(first, next, targetDay, r.missingDay)?.let { if (it >= anchor) yield(it) }
                        index += r.interval
                    }
                }
                Frequency.YEARLY -> {
                    val original = JewishDate(anchor)
                    var index = 0
                    while (true) {
                        val year = if (event.repeatCalendar == CalendarSystem.GREGORIAN) anchor.year + index else original.jewishYear + index
                        val yearStart = if (event.repeatCalendar == CalendarSystem.GREGORIAN) LocalDate.of(year, 1, 1) else JewishDate(year, JewishDate.TISHREI, 1).localDate
                        if (yearStart >= limit) break
                        if (event.repeatCalendar == CalendarSystem.GREGORIAN) {
                            val first = LocalDate.of(year, anchor.month, 1)
                            resolveDay(first, first.plusMonths(1), anchor.dayOfMonth, r.missingDay)?.let { if (it >= anchor) yield(it) }
                        } else {
                            val leap = JewishDate.isJewishLeapYear(year)
                            val months: List<Int> = when {
                                index == 0 -> listOf(original.jewishMonth)
                                original.jewishMonth == JewishDate.ADAR && !original.isJewishLeapYear && leap -> when (r.ordinaryAdar) {
                                    AdarPolicy.ADAR_I -> listOf(12); AdarPolicy.ADAR_II -> listOf(13)
                                    AdarPolicy.BOTH -> listOf(12, 13); else -> emptyList()
                                }
                                original.jewishMonth in setOf(12, 13) && original.isJewishLeapYear && !leap -> if (r.leapAdar == LeapAdarPolicy.ADAR) listOf(12) else emptyList()
                                else -> listOf(original.jewishMonth)
                            }
                            for (month in months) {
                                val first = JewishDate(year, month, 1)
                                resolveDay(first.localDate, first.localDate.plusDays(first.daysInJewishMonth.toLong()), original.jewishDayOfMonth, r.missingDay)?.let { if (it >= anchor) yield(it) }
                            }
                        }
                        index += r.interval
                    }
                }
            }
        }
        return candidates.distinct().filter { it < limit }.take(r.count ?: Int.MAX_VALUE)
            .filter { it >= from && it.toString() !in event.excludedDates }.toList()
    }

    private fun resolveDay(first: LocalDate, next: LocalDate, day: Int, policy: MissingDayPolicy?): LocalDate? {
        val length = ChronoUnit.DAYS.between(first, next).toInt()
        if (day <= length) return first.plusDays(day - 1L)
        return when (policy) { MissingDayPolicy.PREVIOUS -> next.minusDays(1); MissingDayPolicy.NEXT -> next; else -> null }
    }

    fun expand(event: Event, from: LocalDate, untilExclusive: LocalDate, data: AppData): List<Occurrence> =
        dates(event, from, untilExclusive).flatMap { date ->
            if (event.timeKind == EventTimeKind.FIXED) fixedOccurrences(event, date) else listOf(occurrence(event, date, data))
        }.filter { it.applicable }

    private fun fixedOccurrences(event: Event, date: LocalDate): List<Occurrence> {
        val local = LocalDateTime.of(if (event.previousEvening && event.calendar == CalendarSystem.HEBREW) date.minusDays(1) else date, LocalTime.parse(event.localTime))
        return localIntervalSegments(local, local.plusMinutes(event.durationMinutes), ZoneId.of(event.zoneId)).mapIndexed { index, (start, end) ->
            Occurrence("${event.id}:$date" + if (index == 0) "" else ":${start.epochSecond}", event.id, date, event.title, start, end, false, event.zoneId)
        }
    }

    fun occurrence(event: Event, date: LocalDate, data: AppData): Occurrence {
        if (event.timeKind == EventTimeKind.FIXED) return fixedOccurrences(event, date).firstOrNull()
            ?: Occurrence("${event.id}:$date", event.id, date, event.title, null, null, false, event.zoneId,
                "The clock change skips this entire local interval.", applicable = false)
        val zone = ZoneId.of(event.zoneId)
        var error: String? = null
        var allDay = false
        var applicable = true
        val start: Instant? = when (event.timeKind) {
            EventTimeKind.ALL_DAY -> {
                if (event.calendar == CalendarSystem.HEBREW && data.settings.hebrewDayBoundary == HebrewDayBoundary.SUNSET) {
                    val place = data.place(event.location)
                    if (place == null) { error = "The event location is unavailable."; null }
                    else calculations.seaLevelSunset(date.minusDays(1), place).also { if (it == null) error = "The opening sunset is unavailable." }
                } else if (event.calendar == CalendarSystem.HEBREW && data.settings.hebrewDayBoundary == null) {
                    error = "Choose the Hebrew all-day boundary in Settings."; null
                } else { allDay = true; date.atStartOfDay(zone).toInstant() }
            }
            EventTimeKind.FIXED -> error("Handled above")
            EventTimeKind.ZMAN -> {
                val place = data.place(event.location)
                val profile = data.profile(event.profileId)
                if (place == null || profile == null || event.methodId == null) { error = "The event calculation or location is missing."; null }
                else when (val value = calculations.calculate(CalculationQuery(date, place, profile, listOf(event.methodId), data.settings.inIsrael ?: false)).first().value) {
                    is CalculationValue.Time -> try { value.instant.plusMillis(checkedOffsetMillis(event.offsetMinutes * 60000)) }
                    catch (e: IllegalArgumentException) { error = e.message; null }
                    catch (e: ArithmeticException) { error = "The formula exceeds the supported time range."; null }
                    CalculationValue.NotApplicable -> { applicable = false; null }
                    is CalculationValue.Unavailable -> { error = value.reason; null }
                    else -> { error = "A duration is not a timed occurrence."; null }
                }
            }
        }
        val end = if (start == null) null else if (allDay) date.plusDays(1).atStartOfDay(zone).toInstant()
        else if (event.timeKind == EventTimeKind.ALL_DAY) data.place(event.location)?.let { calculations.seaLevelSunset(date, it) }.also { if (it == null) error = "The closing sunset is unavailable." }
        else start.plus(Duration.ofMinutes(event.durationMinutes))
        return Occurrence("${event.id}:$date", event.id, date, event.title, if (error == null) start else null,
            if (error == null) end else null, allDay, event.zoneId, error, applicable = applicable)
    }
}

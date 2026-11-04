package com.example.zmanim.domain

/** UI units use elapsed time, matching the scheduler and Android calendar provider. */
enum class ReminderUnit(val minutes: Int) {
    MINUTES(1), HOURS(60), DAYS(1440), WEEKS(10080);

    companion object {
        fun forMinutes(minutes: Int): ReminderUnit =
            entries.lastOrNull { minutes > 0 && minutes % it.minutes == 0 } ?: MINUTES
    }
}

fun reminderMinutes(amount: String, unit: ReminderUnit): Int? =
    amount.toLongOrNull()?.takeIf { it in 1..(525600L / unit.minutes) }
        ?.let { (it * unit.minutes).toInt() }

fun Event.hasAlarmReminders(): Boolean = reminders.any { it.mode == ReminderMode.ALARM }

/** Calendar provider reminders cannot store ringing alarm settings. */
fun Event.reminderDeliveryOwner(): ReminderOwner = if (hasAlarmReminders()) ReminderOwner.APP else reminderOwner

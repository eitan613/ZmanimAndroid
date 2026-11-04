package com.example.zmanim.ui

import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.zmanim.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ReminderOffsets(initial: List<ReminderRule>, allowAlarms: Boolean = false, onChange: (List<ReminderRule>) -> Unit) {
    initial.forEachIndexed { index, rule ->
        key(rule.id) {
            var unit by rememberSaveable(rule.id) { mutableStateOf(ReminderUnit.forMinutes(rule.minutesBefore)) }
            var amount by rememberSaveable(rule.id) { mutableStateOf((rule.minutesBefore / unit.minutes).coerceAtLeast(1).toString()) }
            val atTime = rule.minutesBefore == 0
            fun update(updated: ReminderRule) = onChange(initial.map { if (it.id == rule.id) updated else it })
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(tr("Reminder ${index + 1}", "תזכורת ${index + 1}"), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { onChange(initial.filterNot { it.id == rule.id }) }) {
                            Icon(Icons.Default.Delete, tr("Remove reminder ${index + 1}", "הסרת תזכורת ${index + 1}"))
                        }
                    }
                    Choice(tr("When", "מתי"), atTime, listOf(false, true),
                        { if (it) tr("At the time", "בזמן המדויק") else tr("Before", "לפני") },
                        { at -> update(rule.copy(minutesBefore = if (at) 0 else reminderMinutes(amount, unit) ?: -1)) })
                    if (!atTime) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                            Field(tr("Amount", "כמות"), amount, { value ->
                                amount = value
                                update(rule.copy(minutesBefore = reminderMinutes(value, unit) ?: -1))
                            }, modifier = Modifier.weight(1f), numeric = true)
                            Choice(tr("Units", "יחידות"), unit, ReminderUnit.entries, { value -> when (value) {
                                ReminderUnit.MINUTES -> tr("Minutes", "דקות")
                                ReminderUnit.HOURS -> tr("Hours", "שעות")
                                ReminderUnit.DAYS -> tr("Days", "ימים")
                                ReminderUnit.WEEKS -> tr("Weeks", "שבועות")
                            } }, { value -> unit = value; update(rule.copy(minutesBefore = reminderMinutes(amount, value) ?: -1)) }, Modifier.weight(1.2f))
                        }
                        if (reminderMinutes(amount, unit) == null) Text(tr("Enter a whole amount between 1 and ${525600 / unit.minutes}.", "יש להזין כמות שלמה בין 1 ל־${525600 / unit.minutes}."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    // Older saved reminders may be paused. New rows are active until removed.
                    if (!rule.enabled) TextButton(onClick = { update(rule.copy(enabled = true)) }) { Text(tr("Paused · enable reminder", "מושהית · הפעלת תזכורת")) }
                    if (allowAlarms) {
                        Choice(tr("Delivery", "אופן התזכורת"), rule.mode, ReminderMode.entries,
                            { if (it == ReminderMode.ALARM) tr("Alarm · rings until dismissed", "שעון מעורר · מצלצל עד לעצירה") else tr("Notification", "התראה") },
                            { update(rule.copy(mode = it)) })
                        if (rule.mode == ReminderMode.ALARM) AlarmOptions(rule) { update(it) }
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = { onChange(initial + ReminderRule(minutesBefore = 10)) }) { Text(tr("Add reminder", "הוספת תזכורת")) }
    if (initial.isEmpty()) Text(tr("No reminders. Add one to get notified.", "אין תזכורות. הוסיפו תזכורת לקבלת התראה."), style = MaterialTheme.typography.bodySmall)
}

@Composable fun EventAlarmAccess(model: AppViewModel) {
    val context = LocalContext.current
    val scheduler = model.app.reminders
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    if (!scheduler.preciseAccess()) {
        Notice(tr("Alarms need precise alarm access to ring at the selected time.", "שעונים מעוררים דורשים הרשאת תזמון מדויק כדי לצלצל בזמן שנבחר."))
        TextButton(onClick = { context.startActivity(scheduler.exactAccessIntent()) }) { Text(tr("Allow precise alarms", "מתן הרשאת תזמון מדויק")) }
    }
    if (!scheduler.notificationAccess("zman-alarms")) {
        Notice(tr("Allow alarm notifications so Dismiss and Snooze are available.", "יש לאפשר התראות שעון מעורר כדי להשתמש בעצירה ובנודניק."))
        TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "zman-alarms"))
        }) { Text(tr("Allow alarm notifications", "מתן הרשאת התראות שעון")) }
    }
    if (scheduler.preciseAccess() && scheduler.notificationAccess("zman-alarms")) Text(tr("Alarm permissions are ready. Alarms ring until dismissed or snoozed.", "הרשאות השעון המעורר מוכנות. השעונים מצלצלים עד לעצירה או להפעלת נודניק."), style = MaterialTheme.typography.bodySmall)
}

@Composable fun AlarmOptions(rule: ReminderRule, onChange: (ReminderRule) -> Unit) {
    val context = LocalContext.current
    val sound = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION") val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            onChange(rule.copy(sound = uri?.toString()))
        }
    }
    val soundName = remember(rule.sound) { runCatching { RingtoneManager.getRingtone(context, rule.sound?.let(android.net.Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))?.getTitle(context) }.getOrNull() }
    OutlinedButton(onClick = { sound.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, rule.sound?.let(android.net.Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
    }) }) { Text(tr("Alarm sound: ", "צליל שעון: ") + (soundName ?: tr("Default alarm", "שעון ברירת מחדל"))) }
    Toggle(tr("Vibrate", "רטט"), rule.vibrate, { onChange(rule.copy(vibrate = it)) })
    var snooze by rememberSaveable(rule.id) { mutableStateOf(rule.snoozeMinutes.toString()) }
    Field(tr("Snooze minutes (1–120)", "דקות לנודניק (1–120)"), snooze, { snooze = it; onChange(rule.copy(snoozeMinutes = it.toIntOrNull() ?: 0)) }, numeric = true)
}

@Composable fun ProfileRemindersDialog(s: ScreenData, model: AppViewModel, initialMethod: String? = null, onDismiss: () -> Unit) {
    val profile = s.data.profile()
    if (profile == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Create a profile first", "יש ליצור פרופיל תחילה")) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close", "סגירה")) } })
        return
    }
    var reminders by rememberDraft("reminders-${profile.id}", profile.reminders)
    var enabled by rememberSaveable(profile.id) { mutableStateOf(profile.remindersEnabled) }
    var selected by rememberSaveable(profile.id) { mutableStateOf(initialMethod) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    val due by produceState<List<com.example.zmanim.platform.ReminderScheduler.Due>>(emptyList(), profile, reminders, enabled, s.data.settings, s.data.place()) {
        value = withContext(Dispatchers.Default) {
            val p = profile.copy(reminders = reminders, remindersEnabled = enabled)
            model.app.reminders.upcoming(s.data.copy(profiles = s.data.profiles.map { if (it.id == p.id) p else it }))
        }
    }
    EditorDialog(tr("Active profile reminders", "תזכורות הפרופיל הפעיל"), reminders != profile.reminders || enabled != profile.remindersEnabled, onDismiss, {
        model.repository.updateProfile(profile.id) { it.copy(reminders = reminders, remindersEnabled = enabled) }
    }) {
        Text(tr("Active profile: ", "פרופיל פעיל: ") + profile.name, style = MaterialTheme.typography.titleMedium)
        Toggle(tr("Enable profile reminders", "הפעלת תזכורות הפרופיל"), enabled, { enabled = it })
        Text(tr("Global reminder switch: ", "מתג תזכורות כללי: ") + if (s.data.settings.zmanNotifications) tr("On", "פעיל") else tr("Off — enable in Settings", "כבוי — ניתן להפעיל בהגדרות"))
        Text(localizeMessage(model.app.reminders.status(s.data.settings), LocalHebrew.current))
        Text(tr("Alarm notification channel: ", "ערוץ התראות שעון: ") + if (model.app.reminders.notificationAccess("zman-alarms")) tr("Allowed", "מורשה") else tr("Blocked", "חסום"))
        TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "zman-alarms")) }) { Text(tr("Alarm channel settings", "הגדרות ערוץ שעון")) }
        Text(tr("Real alarm precision: ", "דיוק שעון מעורר: ") + if (model.app.reminders.preciseAccess()) tr("Allowed", "מורשה") else tr("Blocked — exact access required", "חסום — נדרשת הרשאה מדויקת"))
        TextButton(onClick = { if (Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(model.app.reminders.notificationSettingsIntent()) }) { Text(tr("Notification access", "הרשאת התראות")) }
        TextButton(onClick = { context.startActivity(model.app.reminders.exactAccessIntent()) }) { Text(tr("Precise alarm access", "הרשאת שעון מדויק")) }
        if (Build.VERSION.SDK_INT >= 34 && !context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()) {
            TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${context.packageName}"))) }) { Text(tr("Optional full-screen alarm access", "הרשאת שעון במסך מלא (רשות)")) }
        }
        Text(tr("Hidden groups suppress both notifications and real alarms. Deselected or unavailable methods do not deliver. Alarm mode requires exact access; approximate acceptance applies only to notifications.", "קבוצות מוסתרות מבטלות התראות ושעונים מעוררים. שיטות שלא נבחרו או שאינן זמינות לא ישלחו תזכורות. שעון מעורר דורש הרשאה מדויקת; הסכמה לקירוב חלה רק על התראות."))
        val ids = (profile.selected + reminders.map { it.methodId }).distinct().filter { MethodCatalog.byId[it]?.isDuration != true }
        if (ids.isEmpty()) Text(tr("Choose a timed zman in this profile first.", "יש לבחור תחילה זמן בפרופיל."))
        ids.forEach { id ->
            TextButton(onClick = { selected = if (selected == id) null else id }) { Text(methodLabel(id, profile, LocalLabelHebrew.current) + " · " + reminders.find { it.methodId == id }?.reminders.orEmpty().size) }
            if (selected == id) {
                val suppressed = id !in profile.selected || (MethodCatalog.byId[id]?.group ?: "custom") in profile.hiddenGroups
                if (suppressed) Notice(tr("Suppressed: select this method and show its group to deliver reminders.", "מושתק: יש לבחור שיטה זו ולהציג את הקבוצה שלה כדי לשלוח תזכורות."))
                ReminderOffsets(reminders.find { it.methodId == id }?.reminders.orEmpty(), allowAlarms = true) { rules -> reminders = reminders.filterNot { it.methodId == id } + ZmanReminder(id, rules) }
            }
        }
        val next = due.firstOrNull()
        Text(tr("Next configured delivery: ", "התזכורת המוגדרת הבאה: ") + (next?.let { it.title + " · " + TimeFormatting.format(it.at, s.data.place()?.zone() ?: java.time.ZoneId.systemDefault(), s.data.settings, android.text.format.DateFormat.is24HourFormat(context), LocalProductLocale.current) } ?: tr("None in the next eight days", "אין בשמונת הימים הבאים")))
        HelpLink("reminders")
    }
}

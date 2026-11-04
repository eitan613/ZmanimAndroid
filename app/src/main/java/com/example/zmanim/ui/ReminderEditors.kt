package com.example.zmanim.ui

import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.zmanim.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

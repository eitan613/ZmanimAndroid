package com.example.zmanim.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zmanim.domain.*
import kotlinx.coroutines.launch

val LocalHebrew = staticCompositionLocalOf { false }
val LocalLabelHebrew = staticCompositionLocalOf { false }
val LocalProductLocale = staticCompositionLocalOf { java.util.Locale.ENGLISH }
@Composable fun tr(english: String, hebrew: String): String = if (LocalHebrew.current) hebrew else english

@Composable fun PageTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
@Composable fun Field(label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, numeric: Boolean = false, multiline: Boolean = false, hint: String? = null, enabled: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), enabled = enabled, singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr),
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
        supportingText = hint?.let { { Text(it) } })
}
@Composable fun <T> Choice(label: String, selected: T, options: List<T>, name: @Composable (T) -> String,
    onChange: (T) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(name(selected), modifier = Modifier.weight(1f)); Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 380.dp)) {
                options.forEach { option -> DropdownMenuItem(text = { Text(name(option)) }, onClick = { onChange(option); expanded = false }) }
            }
        }
    }
}
@Composable fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, detail: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
@Composable fun Notice(message: String, error: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(localizeMessage(message, LocalHebrew.current)); action?.invoke() }
    }
}
@Composable fun ConfirmDialog(title: String, message: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirmationAction")) { Text(action, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "ביטול")) } })
}
@Composable fun EditorDialog(title: String, dirty: Boolean, onDismiss: () -> Unit, onSave: suspend () -> Unit,
    saveLabel: String? = null, live: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close = { if (!saving) { if (dirty) discard = true else onDismiss() } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler(onBack = close)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = close, enabled = !saving) { Text(if (live) tr("Back", "חזרה") else tr("Cancel", "ביטול")) }
                    TextButton(onClick = {
                        if (!saving) scope.launch {
                            saving = true; error = null
                            try { onSave(); onDismiss() } catch (e: Exception) { error = e.message ?: "Save failed. Try again." } finally { saving = false }
                        }
                    }, enabled = !saving) { Text(if (saving) tr("Saving…", "שומר…") else saveLabel ?: tr("Save", "שמירה")) }
                }
                Column(Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    error?.let { Notice(it, true) }
                    content()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (discard) ConfirmDialog(tr("Discard changes?", "לבטל את השינויים?"), tr("Your unsaved changes will be lost.", "השינויים שלא נשמרו יאבדו."),
        tr("Discard", "ביטול השינויים"), { discard = false }) { discard = false; onDismiss() }
}
@Composable fun enumName(value: Enum<*>?): String {
    if (value == null) return tr("Choose…", "בחירה…")
    val en = value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    val he = mapOf("SYSTEM" to "לפי המערכת", "LIGHT" to "בהיר", "DARK" to "כהה", "ENGLISH" to "אנגלית", "HEBREW" to "עברית",
        "GREGORIAN" to "לועזי", "TRANSLITERATED" to "תעתיק", "CALENDAR" to "לוח שנה", "ZMANIM" to "זמנים", "MONTH" to "חודש", "WEEK" to "שבוע", "DAY" to "יום",
        "CIVIL" to "מחצות לחצות", "SUNSET" to "משקיעה לשקיעה", "DOWN" to "כלפי מטה", "NEAREST" to "הדקה הקרובה", "UP" to "כלפי מעלה",
        "NONE" to "ללא חזרה", "DAILY" to "יומי", "WEEKLY" to "שבועי", "MONTHLY" to "חודשי", "YEARLY" to "שנתי", "PREVIOUS" to "היום הקודם", "NEXT" to "היום הבא", "SKIP" to "דילוג",
        "ALL_DAY" to "כל היום", "FIXED" to "שעה קבועה", "ZMAN" to "יחסית לזמן", "ADAR_I" to "אדר א׳", "ADAR_II" to "אדר ב׳", "BOTH" to "שניהם", "ADAR" to "אדר",
        "SAVED" to "מקום שמור", "FOLLOW" to "המיקום שלי", "FIXED_MINUTES" to "דקות קבועות", "SOLAR_ANGLE" to "זווית השמש", "TEMPORAL_HOURS" to "שעות זמניות",
        "MORNING" to "בוקר", "EVENING" to "ערב", "APP" to "באפליקציה", "EVENT" to "אירוע", "EVENTS" to "אירועים", "DATE" to "תאריך", "COMBINED" to "משולב",
        "KEEP" to "שמירת הקיים", "REPLACE" to "החלפה", "COPY" to "ייבוא כעותק", "NEW_EVENT" to "אירוע חדש", "EARLIER" to "המופע המוקדם", "LATER" to "המופע המאוחר", "SHIFT_FORWARD" to "הזזה קדימה")
    return if (LocalHebrew.current) he[value.name] ?: en else when (value) {
        HebrewDayBoundary.CIVIL -> "Civil day · midnight to midnight"
        HebrewDayBoundary.SUNSET -> "Sunset to sunset"
        else -> en
    }
}
fun methodLabel(id: String, profile: Profile?, hebrew: Boolean): String = bidiZmanLabel(methodDisplayName(id, profile, hebrew))

@Composable fun AppHeader(title: String, subtitle: String? = null, actions: List<Pair<String, () -> Unit>> = emptyList(), onBack: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "חזרה"))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (actions.isNotEmpty()) Box {
            IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("overflowMenu")) {
                Icon(androidx.compose.material.icons.Icons.Default.MoreVert, tr("Menu", "תפריט"))
            }
            DropdownMenu(expanded, { expanded = false }) {
                actions.forEach { (label, action) -> DropdownMenuItem(text = { Text(label) }, modifier = Modifier.testTag("menu:$label"), onClick = { expanded = false; action() }) }
            }
        }
    }
}

@Composable fun MethodChoice(label: String, checked: Boolean, onChange: (Boolean) -> Unit, onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.weight(1f).padding(horizontal = 8.dp),
            textAlign = if (LocalLabelHebrew.current) androidx.compose.ui.text.style.TextAlign.Right else androidx.compose.ui.text.style.TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content))
        IconButton(onClick = onInfo) { Icon(androidx.compose.material.icons.Icons.Default.Info, tr("How this is calculated", "אופן החישוב")) }
    }
}

package com.example.zmanim.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.zmanim.data.*
import com.example.zmanim.domain.*
import kotlinx.coroutines.*

@Composable fun BackupDialog(s: ScreenData, model: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(BackupSelection()) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var action by remember { mutableStateOf(ConflictAction.KEEP) }
    var applySettings by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val text = pendingExport ?: error("No export is ready.")
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: error("The selected file cannot be written.") }
                status = "Backup exported."; error = null
            } catch (e: Exception) { error = e.message } finally { busy = false; pendingExport = null }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val imported = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val result = StringBuilder(); val buffer = CharArray(8192)
                        while (true) { val n = reader.read(buffer); if (n < 0) break; require(result.length + n <= 20_000_000) { "Backup files must be smaller than 20 MB." }; result.append(buffer, 0, n) }
                        result.toString()
                    } ?: error("The selected file cannot be read.")
                    BackupService().preview(text, model.repository.data)
                }
                preview = imported; error = null; status = null
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }
    EditorDialog(tr("Backup and sharing", "גיבוי ושיתוף"), false, onDismiss, {}, tr("Done", "סיום")) {
        Text(tr("Choose the data to export. Calculation-only sharing excludes private locations and events unless you select them.", "בחרו אילו נתונים לייצא. שיתוף חישובים אינו כולל מקומות ואירועים פרטיים אלא אם נבחרו במפורש."))
        Toggle(tr("App settings", "הגדרות האפליקציה"), selection.settings, { selection = selection.copy(settings = it) })
        Toggle(tr("Profiles and custom calculations", "פרופילים וחישובים אישיים"), selection.profiles, { selection = selection.copy(profiles = it) })
        Toggle(tr("Saved locations (private)", "מקומות שמורים (פרטי)"), selection.places, { selection = selection.copy(places = it) })
        Toggle(tr("Personal events and guest details (private)", "אירועים אישיים ופרטי אורחים (פרטי)"), selection.events, { selection = selection.copy(events = it) })
        Button(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                try { pendingExport = withContext(Dispatchers.Default) { BackupService().export(s.data, selection) }; create.launch("zmanim-${java.time.LocalDate.now()}.json") }
                catch (e: Exception) { error = e.message } finally { busy = false }
            }
        }) { Text(tr("Export selected data", "ייצוא הנתונים שנבחרו")) }
        OutlinedButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            try {
                val uri = withContext(Dispatchers.IO) {
                    val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                    val file = java.io.File(dir, "zmanim-share-${System.currentTimeMillis()}.json")
                    file.writeText(BackupService().export(s.data, selection))
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json")
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(android.content.Intent.createChooser(intent, "Share selected Zmanim data"))
            } catch (e: Exception) { error = e.message } finally { busy = false }
        } }) { Text(tr("Share selected data", "שיתוף הנתונים שנבחרו")) }
        HorizontalDivider()
        OutlinedButton(enabled = !busy, onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text(tr("Open backup to import", "פתיחת גיבוי לייבוא")) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Notice(it, true) }
        status?.let { Notice(it) }
        preview?.let { p ->
            Section(tr("Import preview", "תצוגה מקדימה לייבוא")) {
                Text("${p.document.profiles.size} profiles · ${p.document.places.size} locations · ${p.document.events.size} events")
                Text("Conflicting IDs: ${p.profileConflicts} profiles, ${p.placeConflicts} locations, ${p.eventConflicts} events")
                Choice(tr("For conflicting records", "לרשומות עם מזהים קיימים"), action, ConflictAction.entries, { enumName(it) }, { action = it })
                if (p.document.settings != null) Toggle(tr("Also import app settings", "ייבוא גם של הגדרות האפליקציה"), applySettings, { applySettings = it })
                Notice(tr("Permissions, widgets, device-calendar links, and active alarms are not restored. Imported reminders stay inactive until you enable them. Rebind any omitted locations before using their calculations.", "הרשאות, יישומונים, קישורים ללוחות המכשיר ותזכורות פעילות אינם משוחזרים. תזכורות שיובאו נשארות לא פעילות עד להפעלתן. יש לשייך מחדש מקומות שלא נכללו לפני שימוש בחישובים."))
                Button(enabled = !busy, onClick = { scope.launch {
                    busy = true
                    try {
                        model.repository.update { existing -> BackupService().merge(p, existing, action, applySettings) }
                        preview = null; status = "Import completed. Review locations and reminder activation."; error = null
                    } catch (e: Exception) { error = e.message } finally { busy = false }
                } }) { Text(tr("Import with these choices", "ייבוא לפי בחירות אלו")) }
            }
        }
    }
}

package com.example.zmanim.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zmanim.data.appJson
import com.example.zmanim.domain.*
import kotlinx.serialization.encodeToString

@Composable inline fun <reified T> rememberDraft(key: String, initial: T): MutableState<T> =
    rememberSaveable(key, saver = Saver<MutableState<T>, String>(save = { appJson.encodeToString(it.value) }, restore = { mutableStateOf(appJson.decodeFromString<T>(it)) })) { mutableStateOf(initial) }

@Composable fun ProfilesDialog(s: ScreenData, model: AppViewModel, onDismiss: () -> Unit) {
    var editing by rememberDraft<Profile?>("profile-open", null)
    var deletion by remember { mutableStateOf<Profile?>(null) }
    EditorDialog(tr("Saved zmanim profiles", "פרופילי זמנים שמורים"), false, onDismiss, {}, tr("Done", "סיום")) {
        Text(tr("Profiles save your calculations and reminders. Location and display settings stay separate.", "פרופילים שומרים שיטות חישוב ותזכורות. המיקום והעדפות התצוגה נפרדים."))
        s.data.profiles.forEach { p ->
            Section(p.name) {
                Text(tr("${p.selected.size} selected calculations", "${p.selected.size} חישובים נבחרים"))
                if (p.id == s.data.settings.activeProfileId) Text(tr("Active profile", "הפרופיל הפעיל"), color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { model.settings { it.copy(activeProfileId = p.id) } }) { Text(tr("Use profile", "בחירת פרופיל")) }
                    TextButton(onClick = { editing = p }) { Text(tr("Edit", "עריכה")) }
                    TextButton(onClick = { model.act { model.repository.duplicateProfile(p) } }) { Text(tr("Duplicate", "שכפול")) }
                    TextButton(onClick = { deletion = p }) { Text(tr("Delete", "מחיקה"), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Button(onClick = { editing = Profile() }) { Text(tr("Create profile", "יצירת פרופיל")) }
        Notice(tr("Switching profiles switches profile reminders. Event calculations keep their own profile reference.", "החלפת פרופיל משנה את תזכורות הפרופיל. חישובי אירועים נשארים משויכים לפרופיל שהוגדר בהם."))
    }
    editing?.let { ProfileEditor(it, s, model) { editing = null } }
    deletion?.let { p -> ConfirmDialog(tr("Delete ${p.name}?", "למחוק את ${p.name}?"),
        tr("This removes the profile and its zmanim reminders. Profiles used by events or widgets cannot be deleted.", "הפרופיל ותזכורות הזמנים שלו יימחקו. לא ניתן למחוק פרופיל שמשמש אירועים או יישומונים."),
        tr("Delete profile", "מחיקת הפרופיל"), { deletion = null }) { deletion = null; model.act { model.repository.deleteProfile(p.id) } } }
}

@Composable fun MethodPicker(profile: Profile, model: AppViewModel, onDismiss: () -> Unit) {
    var draft by rememberDraft("methods-${profile.id}", profile)
    var search by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("dawn") }
    val hebrew = LocalLabelHebrew.current
    EditorDialog(tr("Choose zmanim", "בחירת זמנים"), draft != profile, onDismiss, { model.repository.saveProfile(draft) }) {
        Text(tr("Select individual calculations. No methods are selected automatically.", "יש לבחור כל שיטת חישוב בנפרד. לא נבחרות שיטות אוטומטית."))
        Field(tr("Search calculations", "חיפוש חישובים"), search, { search = it })
        if (search.isNotEmpty()) TextButton(onClick = { search = "" }) { Text(tr("Clear search", "ניקוי החיפוש")) }
        Choice(tr("Group", "קבוצה"), group, zmanGroups.map { it.id }, { id -> zmanGroups.first { it.id == id }.let { if (hebrew) it.hebrew else it.english } }, { group = it })
        val methods = MethodCatalog.methods.filter { (search.isNotBlank() || it.group == group) && methodLabel(it.id, draft, hebrew).contains(search, true) }
        if (group == "custom" && search.isBlank()) draft.custom.forEach { z ->
            Toggle(z.name, z.id in draft.selected, { checked -> draft = draft.copy(selected = if (checked) draft.selected + z.id else draft.selected - z.id) }, formulaText(z))
        }
        methods.forEach { method -> Toggle(methodLabel(method.id, draft, hebrew), method.id in draft.selected,
            { checked -> draft = draft.copy(selected = if (checked) draft.selected + method.id else draft.selected - method.id) }) }
        if (methods.isEmpty() && group != "custom") Text(tr("No matching calculations.", "אין חישובים תואמים."))
    }
}

@Composable private fun ProfileEditor(initial: Profile, s: ScreenData, model: AppViewModel, onDismiss: () -> Unit) {
    var draft by rememberDraft("profile-${initial.id}", initial)
    var choosing by remember { mutableStateOf(false) }
    var formula by rememberDraft<CustomZman?>("formula-open", null)
    var deleteFormula by remember { mutableStateOf<CustomZman?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var candle by rememberSaveable { mutableStateOf(initial.options.candleLightingMinutes?.toString().orEmpty()) }
    var ateret by rememberSaveable { mutableStateOf(initial.options.ateretTorahMinutes?.toString().orEmpty()) }
    EditorDialog(tr("Edit profile", "עריכת פרופיל"), draft != initial, onDismiss, {
        require(candle.isBlank() || candle.toDoubleOrNull() != null) { "Enter a valid candle-lighting offset." }
        require(ateret.isBlank() || ateret.toDoubleOrNull() != null) { "Enter a valid Ateret Torah offset." }
        model.repository.saveProfile(draft)
    }) {
        Field(tr("Profile name", "שם הפרופיל"), draft.name, { draft = draft.copy(name = it) })
        Toggle(tr("Use elevation", "התחשבות בגובה"), draft.options.useElevation, { draft = draft.copy(options = draft.options.copy(useElevation = it)) })
        Toggle(tr("Astronomical chatzos", "חצות אסטרונומי"), draft.options.astronomicalChatzos, { draft = draft.copy(options = draft.options.copy(astronomicalChatzos = it)) })
        Toggle(tr("Use astronomical half-days for supported methods", "חצאי יום אסטרונומיים בשיטות התומכות בכך"), draft.options.astronomicalHalfDays,
            { draft = draft.copy(options = draft.options.copy(astronomicalHalfDays = it)) })
        Field(tr("Candle lighting: minutes before sunset", "הדלקת נרות: דקות לפני השקיעה"), candle, {
            candle = it; draft = draft.copy(options = draft.options.copy(candleLightingMinutes = it.toDoubleOrNull()))
        }, numeric = true, hint = tr("Required only for candle-lighting calculations; no offset is assumed.", "נדרש רק לחישוב הדלקת נרות; לא נקבע מרווח מראש."))
        Field(tr("Ateret Torah: minutes after sunset", "עטרת תורה: דקות אחרי השקיעה"), ateret, {
            ateret = it; draft = draft.copy(options = draft.options.copy(ateretTorahMinutes = it.toDoubleOrNull()))
        }, numeric = true)
        Button(onClick = { choosing = true }) { Text(tr("Choose calculations", "בחירת חישובים")) }
        Section(tr("Display order and reminders", "סדר תצוגה ותזכורות")) {
            Toggle(tr("Enable reminders for this profile", "הפעלת תזכורות לפרופיל זה"), draft.remindersEnabled, { draft = draft.copy(remindersEnabled = it) })
            draft.selected.forEachIndexed { index, id ->
                Text(methodLabel(id, draft, LocalLabelHebrew.current), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = index > 0, onClick = { val list = draft.selected.toMutableList(); list[index] = list[index - 1].also { list[index - 1] = list[index] }; draft = draft.copy(selected = list) }) { Text(tr("Move up", "הזזה למעלה")) }
                    TextButton(enabled = index < draft.selected.lastIndex, onClick = { val list = draft.selected.toMutableList(); list[index] = list[index + 1].also { list[index + 1] = list[index] }; draft = draft.copy(selected = list) }) { Text(tr("Move down", "הזזה למטה")) }
                    TextButton(onClick = { draft = draft.copy(selected = draft.selected - id) }) { Text(tr("Remove", "הסרה")) }
                }
                if (MethodCatalog.byId[id]?.isDuration != true) ReminderOffsets(draft.reminders.find { it.methodId == id }?.reminders.orEmpty()) { offsets ->
                    draft = draft.copy(reminders = draft.reminders.filterNot { it.methodId == id } + ZmanReminder(id, offsets))
                }
            }
        }
        Section(tr("Visible groups", "קבוצות מוצגות")) {
            zmanGroups.forEach { group -> Toggle(if (LocalHebrew.current) group.hebrew else group.english, group.id !in draft.hiddenGroups,
                { visible -> draft = draft.copy(hiddenGroups = if (visible) draft.hiddenGroups - group.id else draft.hiddenGroups + group.id) }) }
        }
        Section(tr("Custom calculations", "חישובים אישיים")) {
            error?.let { Notice(it, true) }
            draft.custom.forEach { z ->
                Text(z.name); Text(formulaText(z), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { formula = z }) { Text(tr("Edit", "עריכה")) }
                    TextButton(onClick = { deleteFormula = z }) { Text(tr("Delete", "מחיקה")) }
                }
            }
            Button(onClick = { formula = CustomZman(name = "", kind = FormulaKind.FIXED_MINUTES) }) { Text(tr("Create custom zman", "יצירת זמן אישי")) }
        }
    }
    if (choosing) LocalMethodPicker(draft, { draft = it; choosing = false }, { choosing = false })
    formula?.let { z -> FormulaEditor(z, draft, s, { updated ->
        val candidate = draft.copy(custom = draft.custom.filterNot { it.id == updated.id } + updated)
        CalculationEngine().validateProfile(candidate)
        draft = candidate
    }, { formula = null }) }
    deleteFormula?.let { z ->
        val dependents = draft.custom.filter { z.id in it.references() }.map { it.name } + s.data.events.filter { it.profileId == draft.id && it.methodId == z.id }.map { it.title }
        ConfirmDialog(tr("Delete ${z.name}?", "למחוק את ${z.name}?"),
            if (dependents.isEmpty()) tr("This also removes its selection and reminders.", "הבחירה והתזכורות של זמן זה יוסרו גם הן.") else tr("Used by: ", "משמש את: ") + dependents.joinToString() + tr(". Replace these dependencies before deleting.", ". יש להחליף את התלויות לפני המחיקה."),
            tr("Delete", "מחיקה"), { deleteFormula = null }) {
            if (dependents.isEmpty()) draft = draft.copy(custom = draft.custom.filterNot { it.id == z.id }, selected = draft.selected - z.id, reminders = draft.reminders.filterNot { it.methodId == z.id })
            else error = "Replace dependent formulas and events before deleting this calculation."
            deleteFormula = null
        }
    }
}

@Composable private fun LocalMethodPicker(profile: Profile, onSave: (Profile) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberDraft("local-methods-${profile.id}", profile)
    var group by rememberSaveable { mutableStateOf("dawn") }
    EditorDialog(tr("Choose calculations", "בחירת חישובים"), draft != profile, onDismiss, { onSave(draft) }) {
        Choice(tr("Group", "קבוצה"), group, zmanGroups.map { it.id }, { id -> zmanGroups.first { it.id == id }.let { if (LocalHebrew.current) it.hebrew else it.english } }, { group = it })
        val ids = if (group == "custom") draft.custom.map { it.id } else MethodCatalog.methods.filter { it.group == group }.map { it.id }
        ids.forEach { id -> Toggle(methodLabel(id, draft, LocalLabelHebrew.current), id in draft.selected, { checked -> draft = draft.copy(selected = if (checked) draft.selected + id else draft.selected - id) }) }
    }
}

@Composable fun ReminderOffsets(initial: List<ReminderRule>, onChange: (List<ReminderRule>) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.joinToString(", ") { it.minutesBefore.toString() }) }
    var invalid by remember { mutableStateOf(false) }
    Field(tr("Reminder minutes before (comma separated)", "דקות לפני לתזכורת (מופרדות בפסיק)"), text, { value ->
        text = value
        val values = value.split(',').map(String::trim).filter(String::isNotEmpty)
        invalid = values.any { it.toIntOrNull() == null || (it.toIntOrNull() ?: -1) !in 0..525600 }
        if (invalid) onChange(listOf(ReminderRule(minutesBefore = -1)))
        else onChange(values.map(String::toInt).distinct().map { offset -> initial.find { it.minutesBefore == offset } ?: ReminderRule(minutesBefore = offset) })
    }, hint = if (invalid) tr("Enter whole minutes from 0 to 525600.", "יש להזין דקות שלמות בין 0 ל־525600.") else tr("Leave empty for no reminders. 0 means at the exact time.", "ריק פירושו ללא תזכורות. 0 הוא הזמן המדויק."))
}

@Composable private fun FormulaEditor(initial: CustomZman, profile: Profile, s: ScreenData, onSave: (CustomZman) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberDraft("formula-${initial.id}", initial)
    var amount by rememberSaveable { mutableStateOf(initial.amount.toString()) }
    val ids = MethodCatalog.methods.filterNot { it.isDuration }.map { it.id } + profile.custom.filterNot { it.id == draft.id }.map { it.id }
    EditorDialog(tr("Custom zman", "זמן אישי"), draft != initial || amount != initial.amount.toString(), onDismiss, {
        draft = draft.copy(amount = amount.toDoubleOrNull() ?: error("Enter a number for the offset or angle.")); onSave(draft)
    }) {
        Field(tr("Name", "שם"), draft.name, { draft = draft.copy(name = it) })
        Field(tr("Hebrew name (optional)", "שם בעברית (רשות)"), draft.hebrewName, { draft = draft.copy(hebrewName = it) })
        Choice(tr("Formula type", "סוג נוסחה"), draft.kind, FormulaKind.entries, { enumName(it) }, { draft = draft.copy(kind = it) })
        Field(when (draft.kind) { FormulaKind.FIXED_MINUTES -> tr("Minutes (+ after, − before)", "דקות (+ אחרי, − לפני)"); FormulaKind.SOLAR_ANGLE -> tr("Degrees below horizon", "מעלות מתחת לאופק"); FormulaKind.TEMPORAL_HOURS -> tr("Temporal hours (+ after, − before)", "שעות זמניות (+ אחרי, − לפני)") }, amount, { amount = it; it.toDoubleOrNull()?.let { n -> draft = draft.copy(amount = n) } }, numeric = true)
        if (draft.kind == FormulaKind.SOLAR_ANGLE) Choice(tr("Morning or evening", "בוקר או ערב"), draft.side, SolarSide.entries, { enumName(it) }, { draft = draft.copy(side = it) })
        else Choice(tr("Time anchor", "זמן בסיס"), draft.base, ids, { methodLabel(it, profile, LocalLabelHebrew.current) }, { draft = draft.copy(base = it) })
        if (draft.kind == FormulaKind.TEMPORAL_HOURS) {
            Choice(tr("Temporal day starts", "תחילת היום הזמני"), draft.dayStart, ids, { methodLabel(it, profile, LocalLabelHebrew.current) }, { draft = draft.copy(dayStart = it) })
            Choice(tr("Temporal day ends", "סיום היום הזמני"), draft.dayEnd, ids, { methodLabel(it, profile, LocalLabelHebrew.current) }, { draft = draft.copy(dayEnd = it) })
        }
        Text(formulaText(draft))
        val place = s.data.place()
        if (place != null) {
            val preview = runCatching { CalculationEngine().calculate(CalculationQuery(s.date, place, profile.copy(custom = profile.custom.filterNot { it.id == draft.id } + draft), listOf(draft.id))).first().value }.getOrNull()
            Text("${s.date} · ${place.name}: " + when (preview) {
                is CalculationValue.Time -> preview.instant.atZone(place.zone()).toString()
                is CalculationValue.Unavailable -> preview.reason
                else -> tr("Unavailable", "לא זמין")
            })
        }
    }
}

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

@Composable fun MethodPicker(profile: Profile, s: ScreenData, model: AppViewModel, onCustom: () -> Unit, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("dawn") }
    var pending by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var failures by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val hebrew = LocalLabelHebrew.current
    var sequence by remember { mutableIntStateOf(0) }
    var versions by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var committed by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(profile.selected, committed) {
        pending = pending.filter { (id, checked) -> versions[id] != committed[id] || (id in profile.selected) != checked }
    }
    fun choose(id: String, checked: Boolean) {
        val version = ++sequence
        versions = versions + (id to version)
        pending = pending + (id to checked)
        failures = failures - id
        model.queueProfileWrite {
            try {
                model.repository.updateProfile(profile.id) { current -> current.copy(selected =
                    if (checked) (current.selected + id).distinct() else current.selected - id) }
                committed = committed + (id to version)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (versions[id] == version) failures = failures + (id to checked)
                model.message.emit("Selection could not be saved. Open Choose zmanim to try again.")
            }
        }
    }
    EditorDialog(tr("Choose zmanim", "בחירת זמנים"), false, onDismiss, {}, tr("Done", "סיום"), live = true) {
        Text(tr("Changes save automatically.", "השינויים נשמרים אוטומטית."))
        if (failures.isNotEmpty()) Notice(tr("Some selections could not be saved.", "לא ניתן לשמור חלק מהבחירות."), true, action = {
            TextButton(onClick = { failures.toMap().forEach { (id, checked) -> choose(id, checked) } }) { Text(tr("Retry", "ניסיון חוזר")) }
        })
        Field(tr("Search calculations", "חיפוש חישובים"), search, { search = it })
        Choice(tr("Group", "קבוצה"), group, zmanGroups.map { it.id }, { id -> zmanGroups.first { it.id == id }.let { if (hebrew) it.hebrew else it.english } }, { group = it })
        if (group == "custom") Button(onClick = onCustom) { Text(tr("Create or edit custom zmanim", "יצירה ועריכה של זמנים אישיים")) }
        val ids = MethodCatalog.methods.filter { (search.isNotBlank() || it.group == group) &&
            (methodLabel(it.id, profile, hebrew).contains(search, true) || it.label.contains(search, true)) }.map { it.id } +
            profile.custom.filter { (search.isNotBlank() || group == "custom") && (it.name.contains(search, true) || it.hebrewName.contains(search, true)) }.map { it.id }
        ids.forEach { id ->
            var expanded by rememberSaveable(id) { mutableStateOf(false) }
            MethodChoice(methodLabel(id, profile, hebrew), pending[id] ?: (id in profile.selected), { checked -> choose(id, checked) }, { expanded = !expanded })
            if (expanded) { Text(methodExplanation(id, profile)); MethodCatalog.byId[id]?.let { Text(effectiveMethodDescription(it, profile).replace("profile elevation policy", if (profile.options.useElevation) "using location elevation" else "at sea level"), style = MaterialTheme.typography.bodySmall) } }
        }
        if (ids.isEmpty()) Text(tr("No matching calculations.", "אין חישובים תואמים."))
    }
}

@Composable fun CustomZmanDialog(profile: Profile, s: ScreenData, model: AppViewModel, onDismiss: () -> Unit) {
    var editing by rememberDraft<CustomZman?>("direct-formula", null)
    var deleting by remember { mutableStateOf<CustomZman?>(null) }
    EditorDialog(tr("Custom zmanim", "זמנים אישיים"), false, onDismiss, {}, tr("Done", "סיום"), live = true) {
        Text(tr("Build a time from a minute offset, a solar angle, or temporal hours.", "ניתן ליצור זמן בעזרת הפרש דקות, זווית שמש או שעות זמניות."))
        Button(onClick = { editing = CustomZman(name = "", kind = FormulaKind.FIXED_MINUTES) }) { Text(tr("Create custom zman", "יצירת זמן אישי")) }
        profile.custom.forEach { z ->
            Section(methodLabel(z.id, profile, LocalLabelHebrew.current)) {
                Text(formulaText(z, profile.custom))
                Row {
                    TextButton(onClick = { editing = z }) { Text(tr("Edit", "עריכה")) }
                    TextButton(onClick = { deleting = z }) { Text(tr("Delete", "מחיקה")) }
                }
            }
        }
    }
    editing?.let { z -> FormulaEditor(z, profile, s, { updated ->
        model.repository.updateProfile(profile.id) { latest -> latest.copy(
            custom = latest.custom.filterNot { it.id == updated.id } + updated,
            selected = (latest.selected + updated.id).distinct()) }
    }, { editing = null }) }
    deleting?.let { z ->
        val used = profile.custom.filter { z.id in it.references() }.map { it.name } +
            s.data.events.filter { it.profileId == profile.id && it.methodId == z.id }.map { it.title } +
            if (profile.options.candleLightingNightfallId == z.id) listOf(tr("Holiday candle lighting", "הדלקת נרות בחג")) else emptyList()
        ConfirmDialog(tr("Delete ${z.name}?", "למחוק את ${z.name}?"),
            if (used.isEmpty()) tr("Its selection and reminders will also be removed.", "גם הבחירה והתזכורות יוסרו.") else tr("Replace dependencies first: ", "יש להחליף תחילה את התלויות: ") + used.joinToString(),
            tr("Delete", "מחיקה"), { deleting = null }) {
            if (used.isEmpty()) model.act { model.repository.updateProfile(profile.id) { latest -> latest.copy(custom = latest.custom.filterNot { it.id == z.id }, selected = latest.selected - z.id, reminders = latest.reminders.filterNot { it.methodId == z.id }) } }
            deleting = null
        }
    }
}

@Composable private fun ProfileEditor(initial: Profile, s: ScreenData, model: AppViewModel, onDismiss: () -> Unit) {
    var draft by rememberDraft("profile-${initial.id}", initial)
    var choosing by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }
    var formula by rememberDraft<CustomZman?>("formula-open", null)
    var deleteFormula by remember { mutableStateOf<CustomZman?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectionBaseline by rememberDraft("selection-baseline-${initial.id}", initial.selected)
    val liveSelected = s.data.profile(initial.id)?.selected
    LaunchedEffect(liveSelected) {
        if (liveSelected != null) {
            if (draft.selected == selectionBaseline) draft = draft.copy(selected = liveSelected)
            selectionBaseline = liveSelected
        }
    }
    var candle by rememberSaveable { mutableStateOf(initial.options.candleLightingMinutes?.toString().orEmpty()) }
    var ateret by rememberSaveable { mutableStateOf(initial.options.ateretTorahMinutes?.toString().orEmpty()) }
    EditorDialog(tr("Edit profile", "עריכת פרופיל"), draft != initial, onDismiss, {
        require(candle.isBlank() || candle.toDoubleOrNull() != null) { "Enter a valid candle-lighting offset." }
        require(ateret.isBlank() || ateret.toDoubleOrNull() != null) { "Enter a valid Ateret Torah offset." }
        if (s.data.profiles.none { it.id == initial.id }) model.repository.saveProfile(draft)
        else model.repository.updateProfile(initial.id) { latest -> draft.copy(
            selected = if (draft.selected == selectionBaseline) latest.selected else
                (draft.selected.filter { it in latest.selected || it !in selectionBaseline } + latest.selected.filter { it !in selectionBaseline && it !in draft.selected }).distinct(),
            custom = if (draft.custom == initial.custom) latest.custom else draft.custom,
            reminders = if (draft.reminders == initial.reminders) latest.reminders else draft.reminders) }
    }) {
        Field(tr("Profile name", "שם הפרופיל"), draft.name, { draft = draft.copy(name = it) })
        Toggle(tr("Adjust supported times for location elevation", "התאמת זמנים נתמכים לגובה המקום"), draft.options.useElevation, { draft = draft.copy(options = draft.options.copy(useElevation = it)) }, tr("Uses the saved height of the location for methods that support elevation. Sea-level methods stay at sea level.", "משתמש בגובה השמור של המקום בשיטות התומכות בגובה. שיטות פני הים נשארות בגובה פני הים."))
        Toggle(tr("Use solar noon for chatzos", "חצות לפי מעבר השמש"), draft.options.astronomicalChatzos, { draft = draft.copy(options = draft.options.copy(astronomicalChatzos = it)) }, tr("On: the sun crossing the local meridian. Off: the midpoint between sunrise and sunset.", "פעיל: מעבר השמש במרידיאן המקומי. כבוי: אמצע הזמן שבין הזריחה לשקיעה."))
        Toggle(tr("Calculate morning and afternoon hours separately", "חישוב נפרד של שעות הבוקר ואחר הצהריים"), draft.options.astronomicalHalfDays,
            { draft = draft.copy(options = draft.options.copy(astronomicalHalfDays = it)) }, tr("For supported methods, divide each interval to/from chatzos into six hours instead of dividing the full day into twelve.", "בשיטות נתמכות, מחלק כל מרווח עד חצות או מחצות לשש שעות, במקום לחלק את כל היום לשתים עשרה."))
        Field(tr("Candle lighting: minutes before sunset", "הדלקת נרות: דקות לפני השקיעה"), candle, {
            candle = it; draft = draft.copy(options = draft.options.copy(candleLightingMinutes = it.toDoubleOrNull()))
        }, numeric = true, hint = tr("Required only for candle-lighting calculations; no offset is assumed.", "נדרש רק לחישוב הדלקת נרות; לא נקבע מרווח מראש."))
        Choice(tr("Holiday candle lighting: nightfall method", "הדלקת נרות בחג: שיטת צאת הכוכבים"), draft.options.candleLightingNightfallId,
            listOf<String?>(null) + MethodCatalog.methods.filter { it.group == "nightfall" }.map { it.id } + draft.custom.map { it.id },
            { id -> id?.let { methodLabel(it, draft, LocalLabelHebrew.current) } ?: tr("Choose when needed", "בחירה בעת הצורך") },
            { id -> draft = draft.copy(options = draft.options.copy(candleLightingNightfallId = id)) })
        Text(tr("Used for applicable holiday transitions after an already sacred day. Friday evenings still use your before-sunset offset.", "משמש במעברי חג המתאימים לאחר יום מקודש. בערבי שבת עדיין משמש המרווח שלפני השקיעה."))
        Field(tr("Ateret Torah: minutes after sunset", "עטרת תורה: דקות אחרי השקיעה"), ateret, {
            ateret = it; draft = draft.copy(options = draft.options.copy(ateretTorahMinutes = it.toDoubleOrNull()))
        }, numeric = true)
        Button(onClick = { model.act { if (model.repository.data.profile(initial.id) == null) model.repository.saveProfile(draft); choosing = true } }) { Text(tr("Choose calculations", "בחירת חישובים")) }
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
    if (choosing) MethodPicker(s.data.profile(initial.id) ?: draft, s, model, onCustom = { choosing = false; customDialog = true }) { choosing = false }
    if (customDialog) CustomZmanDialog(s.data.profile(initial.id) ?: draft, s, model) { customDialog = false }
    formula?.let { z -> FormulaEditor(z, draft, s, { updated ->
        val candidate = draft.copy(custom = draft.custom.filterNot { it.id == updated.id } + updated)
        CalculationEngine().validateProfile(candidate)
        draft = candidate.copy(selected = (candidate.selected + updated.id).distinct())
    }, { formula = null }) }
    deleteFormula?.let { z ->
        val dependents = draft.custom.filter { z.id in it.references() }.map { it.name } + s.data.events.filter { it.profileId == draft.id && it.methodId == z.id }.map { it.title } + if (draft.options.candleLightingNightfallId == z.id) listOf("Holiday candle lighting") else emptyList()
        ConfirmDialog(tr("Delete ${z.name}?", "למחוק את ${z.name}?"),
            if (dependents.isEmpty()) tr("This also removes its selection and reminders.", "הבחירה והתזכורות של זמן זה יוסרו גם הן.") else tr("Used by: ", "משמש את: ") + dependents.joinToString() + tr(". Replace these dependencies before deleting.", ". יש להחליף את התלויות לפני המחיקה."),
            tr("Delete", "מחיקה"), { deleteFormula = null }) {
            if (dependents.isEmpty()) draft = draft.copy(custom = draft.custom.filterNot { it.id == z.id }, selected = draft.selected - z.id, reminders = draft.reminders.filterNot { it.methodId == z.id })
            else error = "Replace dependent formulas and events before deleting this calculation."
            deleteFormula = null
        }
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

@Composable private fun FormulaEditor(initial: CustomZman, profile: Profile, s: ScreenData, onSave: suspend (CustomZman) -> Unit, onDismiss: () -> Unit) {
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
            val preview = runCatching { CalculationEngine().calculate(CalculationQuery(s.date, place, profile.copy(custom = profile.custom.filterNot { it.id == draft.id } + draft), listOf(draft.id), s.data.settings.inIsrael ?: false)).first().value }.getOrNull()
            Text("${s.date} · ${place.name}: " + when (preview) {
                is CalculationValue.Time -> TimeFormatting.format(preview.instant, place, s.data.settings, android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current), LocalProductLocale.current)
                is CalculationValue.Unavailable -> preview.reason
                CalculationValue.NotApplicable -> tr("Does not apply on this date", "לא חל בתאריך זה")
                else -> tr("Unavailable", "לא זמין")
            })
        }
    }
}

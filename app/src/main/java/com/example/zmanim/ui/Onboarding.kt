package com.example.zmanim.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zmanim.domain.*
import java.time.*
import java.time.format.TextStyle
import com.kosherjava.zmanim.hebrewcalendar.JewishDate

@Composable fun GoToDialog(initial: LocalDate, onDate: (LocalDate) -> Unit, onDismiss: () -> Unit,
    initialSystem: CalendarSystem = CalendarSystem.GREGORIAN) {
    var system by rememberSaveable { mutableStateOf(initialSystem) }
    var selected by rememberSaveable { mutableStateOf(initial.toString()) }
    var month by rememberSaveable { mutableStateOf(initial.toString()) }
    var jump by rememberSaveable { mutableStateOf(false) }
    var year by rememberSaveable { mutableStateOf("") }
    val engine = remember { CalendarEngine() }
    val date = LocalDate.parse(selected)
    val cursor = LocalDate.parse(month)
    val (first, end) = engine.monthBounds(cursor, system)
    EditorDialog(tr("Choose date", "בחירת תאריך"), false, onDismiss, { onDate(date) }, tr("Choose date", "בחירת תאריך")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarSystem.entries.forEach { mode -> FilterChip(selected = mode == system,
                onClick = { system = mode; month = selected }, label = { Text(enumName(mode)) }) }
        }
        Text(engine.monthLabel(cursor, system, LocalLabelHebrew.current, LocalProductLocale.current), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { month = engine.shift(cursor, system, -1, true).toString() }) { Text("− " + tr("Year", "שנה")) }
            TextButton(onClick = { month = engine.shift(cursor, system, -1).toString() }) { Text("− " + tr("Month", "חודש")) }
            TextButton(onClick = { month = engine.shift(cursor, system, 1).toString() }) { Text("+ " + tr("Month", "חודש")) }
            TextButton(onClick = { month = engine.shift(cursor, system, 1, true).toString() }) { Text("+ " + tr("Year", "שנה")) }
        }
        TextButton(onClick = { jump = !jump; year = (if (system == CalendarSystem.HEBREW) JewishDate(cursor).jewishYear else cursor.year).toString() }) { Text(tr("Jump to year", "מעבר לשנה")) }
        if (jump) {
            Field(tr("Year", "שנה"), year, { year = it }, numeric = true)
            TextButton(enabled = year.toIntOrNull() in 1..9999, onClick = {
                val y = year.toInt()
                month = if (system == CalendarSystem.GREGORIAN) first.withYear(y).toString() else {
                    val jd = JewishDate(first)
                    JewishDate(y, if (jd.jewishMonth == 13 && !JewishDate.isJewishLeapYear(y)) 12 else jd.jewishMonth, 1).localDate.toString()
                }
                jump = false
            }) { Text(tr("Go", "מעבר")) }
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides
            if (system == CalendarSystem.HEBREW) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Column {
                Row(Modifier.fillMaxWidth()) { (0..6).forEach { index ->
                    Text(weekdayLabel(DayOfWeek.of(if (index == 0) 7 else index), system == CalendarSystem.HEBREW, LocalProductLocale.current),
                        modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } }
                val offset = first.dayOfWeek.value % 7
                val length = java.time.temporal.ChronoUnit.DAYS.between(first, end).toInt()
                (0 until (offset + length + 6) / 7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) { (0..6).forEach { column ->
                        val index = week * 7 + column - offset
                        Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            if (index in 0 until length) {
                                val day = first.plusDays(index.toLong())
                                val label = if (system == CalendarSystem.HEBREW) engine.formatter(true).formatHebrewNumber(JewishDate(day).jewishDayOfMonth) else day.dayOfMonth.toString()
                                if (day == date) FilledTonalButton(onClick = { selected = day.toString() }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) { Text(label) }
                                else TextButton(onClick = { selected = day.toString() }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) { Text(label) }
                            }
                        }
                    } }
                }
            }
        }
        Text(date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMM uuuu", LocalProductLocale.current)))
        Text(engine.dateLabel(date, LocalLabelHebrew.current))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimeSelectionDialog(initial: LocalTime, use24Hour: Boolean, onTime: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, use24Hour)
    var input by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Choose time", "בחירת שעה")) }, text = {
        Column {
            if (input) TimeInput(state) else TimePicker(state)
            TextButton(onClick = { input = !input }) { Text(if (input) tr("Clock", "שעון") else tr("Type a time", "הקלדת שעה")) }
        }
    }, confirmButton = { TextButton(onClick = { onTime(LocalTime.of(state.hour, state.minute)) }) { Text(tr("Choose time", "בחירת שעה")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "ביטול")) } })
}

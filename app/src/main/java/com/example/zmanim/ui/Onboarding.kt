package com.example.zmanim.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zmanim.domain.*

@Composable fun Onboarding(s: ScreenData, model: AppViewModel, onMethods: () -> Unit, onLocation: () -> Unit) {
    var inIsrael by remember { mutableStateOf(s.data.settings.inIsrael) }
    var working by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = {}, title = { Text(tr("Your calendar and zmanim", "לוח השנה והזמנים שלך")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Choose the holiday and parsha schedule you follow. It stays fixed when you travel.", "בחרו את סדר החגים והפרשות שלכם. בחירה זו לא משתנה בנסיעות."))
            Choice(tr("Holiday and parsha schedule", "סדר חגים ופרשות"), inIsrael, listOf(null, true, false),
                { value -> when (value) { true -> tr("Israel", "ישראל"); false -> tr("Diaspora", "חו״ל"); null -> tr("Choose…", "בחירה…") } }, { inIsrael = it })
            Text(tr("You choose every zmanim method yourself. You can start with the calendar and configure zmanim later.", "כל שיטת חישוב נבחרת על ידכם. אפשר להתחיל בלוח השנה ולהגדיר זמנים בהמשך."))
        } }, confirmButton = { TextButton(enabled = inIsrael != null && !working, onClick = {
            working = true
            model.act {
                if (s.data.profiles.isEmpty()) model.repository.saveProfile(Profile())
                model.repository.settings { it.copy(inIsrael = inIsrael, onboardingComplete = true) }
                onMethods()
            }
        }) { Text(tr("Choose zmanim", "בחירת זמנים")) } }, dismissButton = {
            TextButton(enabled = inIsrael != null && !working, onClick = { working = true; model.settings { it.copy(inIsrael = inIsrael, onboardingComplete = true) } }) { Text(tr("Open calendar", "פתיחת לוח השנה")) }
        })
}

@Composable fun GoToDialog(initial: java.time.LocalDate, onDate: (java.time.LocalDate) -> Unit, onDismiss: () -> Unit) {
    var system by rememberSaveable { mutableStateOf(CalendarSystem.GREGORIAN) }
    var civil by rememberSaveable { mutableStateOf(initial.toString()) }
    val jd = remember(initial) { com.kosherjava.zmanim.hebrewcalendar.JewishDate(initial) }
    var year by rememberSaveable { mutableStateOf(jd.jewishYear.toString()) }
    var month by rememberSaveable { mutableStateOf(jd.jewishMonth) }
    var day by rememberSaveable { mutableStateOf(jd.jewishDayOfMonth.toString()) }
    EditorDialog(tr("Go to date", "מעבר לתאריך"), false, onDismiss, {
        val date = if (system == CalendarSystem.GREGORIAN) java.time.LocalDate.parse(civil) else {
            val y = year.toIntOrNull() ?: error("Enter a Hebrew year.")
            val d = day.toIntOrNull() ?: error("Enter a Hebrew day.")
            require(month != 13 || com.kosherjava.zmanim.hebrewcalendar.JewishDate.isJewishLeapYear(y)) { "Adar II is absent in this year." }
            val first = com.kosherjava.zmanim.hebrewcalendar.JewishDate(y, month, 1)
            require(d in 1..first.daysInJewishMonth) { "This day is absent in the selected month." }
            com.kosherjava.zmanim.hebrewcalendar.JewishDate(y, month, d).localDate
        }
        onDate(date)
    }, tr("Go to date", "מעבר לתאריך")) {
        Choice(tr("Calendar", "לוח"), system, CalendarSystem.entries, { enumName(it) }, { system = it })
        if (system == CalendarSystem.GREGORIAN) Field(tr("Date (YYYY-MM-DD)", "תאריך (YYYY-MM-DD)"), civil, { civil = it })
        else {
            Field(tr("Hebrew year", "שנה עברית"), year, { year = it }, numeric = true)
            Choice(tr("Hebrew month", "חודש עברי"), month, (1..13).toList(), { m ->
                val y = year.toIntOrNull()?.coerceIn(1, 9999) ?: jd.jewishYear
                CalendarEngine().formatter(LocalHebrew.current).formatMonth(com.kosherjava.zmanim.hebrewcalendar.JewishDate(if (m == 13 && !com.kosherjava.zmanim.hebrewcalendar.JewishDate.isJewishLeapYear(y)) 5784 else y, m, 1))
            }, { month = it })
            Field(tr("Hebrew day", "יום בחודש"), day, { day = it }, numeric = true)
        }
    }
}

package com.example.zmanim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.zmanim.calculation.SavedLocation
import com.example.zmanim.calculation.SolarDay
import com.example.zmanim.calculation.ZmanimCalculator
import com.example.zmanim.data.SolarMethod
import com.example.zmanim.data.ZmanimPreferences
import com.example.zmanim.ui.theme.ZmanimTheme
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ZmanimTheme { ZmanimApp() } }
    }
}

@Composable
fun ZmanimApp() {
    val location = SavedLocation.Jerusalem
    var epochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now(location.zoneId).toEpochDay())
    }
    val calculator = remember { ZmanimCalculator() }
    val day = remember(epochDay) { calculator.calculate(LocalDate.ofEpochDay(epochDay), location) }
    val context = LocalContext.current
    val preferences = remember(context) { ZmanimPreferences(context) }
    var retry by remember { mutableLongStateOf(0L) }
    val selection by produceState<SelectionState>(SelectionState.Loading, preferences, retry) {
        value = SelectionState.Loading
        try {
            preferences.selectedMethods.collect { value = SelectionState.Ready(it) }
        } catch (_: IOException) {
            value = SelectionState.Failed
        }
    }
    var choosing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ZmanimScreen(
        day = day,
        selection = selection,
        onChoose = { saveFailed = false; choosing = true },
        onRetry = { retry += 1 },
        onPrevious = { epochDay -= 1 },
        onToday = { epochDay = LocalDate.now(location.zoneId).toEpochDay() },
        onNext = { epochDay += 1 },
    )
    val ready = selection as? SelectionState.Ready
    if (choosing && ready != null) {
        MethodPicker(
            initial = ready.methods,
            saving = saving,
            saveFailed = saveFailed,
            onDismiss = { if (!saving) choosing = false },
            onSave = { methods ->
                scope.launch {
                    saving = true
                    saveFailed = false
                    try {
                        preferences.saveSelection(methods)
                        choosing = false
                    } catch (_: IOException) {
                        saveFailed = true
                    } finally {
                        saving = false
                    }
                }
            },
        )
    }
}

private sealed interface SelectionState {
    data object Loading : SelectionState
    data object Failed : SelectionState
    data class Ready(val methods: Set<SolarMethod>) : SelectionState
}

@Composable
private fun ZmanimScreen(
    day: SolarDay,
    selection: SelectionState,
    onChoose: () -> Unit,
    onRetry: () -> Unit,
    onPrevious: () -> Unit,
    onToday: () -> Unit,
    onNext: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    val timeFormatter = remember(locale, day.location.zoneId) {
        DateTimeFormatter.ofPattern("HH:mm:ss", locale).withZone(day.location.zoneId)
    }
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.calculation_preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        day.hebrewDate,
                        modifier = Modifier.testTag("hebrewDate"),
                        style = MaterialTheme.typography.displaySmall.copy(textDirection = TextDirection.Rtl),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(day.transliteratedDate, style = MaterialTheme.typography.titleMedium)
                    Text(
                        dateFormatter.format(day.date),
                        modifier = Modifier.testTag("civilDate"),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.date_context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DateNavigation(onPrevious, onToday, onNext)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(day.location.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.location_coordinates, day.location.latitude, day.location.longitude),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(day.location.zoneId.id, style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider()
                        when (selection) {
                            SelectionState.Loading -> CircularProgressIndicator()
                            SelectionState.Failed -> {
                                Text(stringResource(R.string.preferences_load_error))
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                            is SelectionState.Ready -> {
                                if (selection.methods.isEmpty()) {
                                    Text(stringResource(R.string.no_zmanim_selected))
                                }
                                if (SolarMethod.SUNRISE in selection.methods) {
                                    SolarTime(stringResource(R.string.sunrise), day.sunrise, timeFormatter, Modifier.testTag("sunriseTime"))
                                }
                                if (SolarMethod.SUNSET in selection.methods) {
                                    SolarTime(stringResource(R.string.sunset), day.sunset, timeFormatter, Modifier.testTag("sunsetTime"))
                                }
                                OutlinedButton(onClick = onChoose) { Text(stringResource(R.string.choose_zmanim)) }
                            }
                        }
                        Text(
                            stringResource(R.string.sea_level),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (day.sunrise == null || day.sunset == null) {
                    Text(stringResource(R.string.unavailable_reason), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DateNavigation(onPrevious: () -> Unit, onToday: () -> Unit, onNext: () -> Unit) {
    val actions = listOf(
        R.string.previous_day to onPrevious,
        R.string.today to onToday,
        R.string.next_day to onNext,
    )
    val button: @Composable (Int, () -> Unit, Modifier) -> Unit = { label, action, modifier ->
        TextButton(
            onClick = action,
            modifier = modifier.heightIn(min = 48.dp),
            colors = if (label == R.string.today) {
                ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else {
                ButtonDefaults.textButtonColors()
            },
        ) { Text(stringResource(label)) }
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            actions.forEach { (label, action) -> button(label, action, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            actions.forEach { (label, action) -> button(label, action, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SolarTime(label: String, instant: Instant?, formatter: DateTimeFormatter, modifier: Modifier = Modifier) {
    // Stacked text remains readable on narrow screens and with enlarged system fonts.
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            instant?.let(formatter::format) ?: stringResource(R.string.unavailable),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun MethodPicker(
    initial: Set<SolarMethod>,
    saving: Boolean,
    saveFailed: Boolean,
    onDismiss: () -> Unit,
    onSave: (Set<SolarMethod>) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_zmanim)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SolarMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = method in draft,
                            enabled = !saving,
                            role = Role.Checkbox,
                            onValueChange = { checked -> draft = if (checked) draft + method else draft - method },
                        ).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = method in draft, onCheckedChange = null, enabled = !saving)
                        Text(
                            stringResource(if (method == SolarMethod.SUNRISE) R.string.sunrise else R.string.sunset),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                Text(stringResource(R.string.sea_level), style = MaterialTheme.typography.bodySmall)
                if (saveFailed) {
                    Text(stringResource(R.string.preferences_save_error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, enabled = !saving) {
                Text(stringResource(if (saving) R.string.saving else R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ZmanimPreview() {
    ZmanimTheme {
        ZmanimScreen(
            ZmanimCalculator().calculate(LocalDate.of(2026, 11, 1), SavedLocation.Jerusalem),
            selection = SelectionState.Ready(SolarMethod.entries.toSet()),
            onChoose = {}, onRetry = {},
            onPrevious = {}, onToday = {}, onNext = {},
        )
    }
}

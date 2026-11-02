package com.example.zmanim.ui

import android.Manifest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zmanim.data.OfflinePlaces
import com.example.zmanim.domain.*
import kotlinx.coroutines.*
import java.time.ZoneId

@Composable fun LocationsDialog(s: ScreenData, model: AppViewModel,
    permission: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<OfflinePlaces.City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by rememberDraft<Place?>("place-open", null)
    var deletion by remember { mutableStateOf<Place?>(null) }
    val scope = rememberCoroutineScope()
    val locationBusy by model.locationBusy.collectAsStateWithLifecycle()
    val locationError by model.locationError.collectAsStateWithLifecycle()
    LaunchedEffect(query) {
        if (query.trim().length < 2) { cities = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        try { cities = withContext(Dispatchers.IO) { model.app.places.search(query) }; error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { if (currentCoroutineContext().isActive) searching = false }
    }
    EditorDialog(tr("Locations", "מקומות"), false, onDismiss, {}, tr("Done", "סיום"), live = true) {
        Text(tr("Active location: ", "המקום הפעיל: ") + (s.data.place()?.name ?: tr("None selected", "לא נבחר מקום")))
        fun locate(follow: Boolean) {
            if (!model.useDeviceLocation(follow)) permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
        Button(onClick = { locate(false) }, enabled = !locationBusy, modifier = Modifier.fillMaxWidth()) { Text(tr("Use current location", "שימוש במיקום הנוכחי")) }
        Text(tr("Use a snapshot of your current position, or follow your location as it changes.", "ניתן להשתמש בנקודת המיקום הנוכחית, או לעקוב אחר המיקום המשתנה."))
        Toggle(tr("Follow my location", "מעקב אחר המיקום שלי"), s.data.settings.location.mode == LocationMode.FOLLOW, { follow ->
            if (follow) locate(true)
            else model.act {
                val fix = model.repository.data.devicePlace
                if (fix != null) {
                    val snapshot = fix.copy(id = newId(), name = "Current location", obtainedAt = null, accuracyMeters = null)
                    model.repository.update { it.copy(places = it.places + snapshot, settings = it.settings.copy(location = LocationBinding(savedId = snapshot.id))) }
                } else model.repository.settings { it.copy(location = LocationBinding(savedId = s.data.places.firstOrNull()?.id)) }
            }
        }, enabled = !locationBusy)
        if (locationBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(tr("Finding your location…", "מאתר את המיקום…")) }
        locationError?.let { Notice(localizeMessage(it, LocalHebrew.current), true, action = {
            TextButton(onClick = { locate(s.data.settings.location.mode == LocationMode.FOLLOW) }) { Text(tr("Retry", "ניסיון חוזר")) }
        }) }
        if (s.data.settings.location.mode == LocationMode.FOLLOW) {
            s.data.devicePlace?.let { fix ->
                Text("\u2066${fix.latitude}, ${fix.longitude} · ${fix.zoneId}\u2069", style = MaterialTheme.typography.bodySmall)
                fix.obtainedAt?.let { Text(tr("Updated: ", "עודכן: ") + java.time.Instant.ofEpochMilli(it).atZone(fix.zone()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm"))) }
            }
            TextButton(onClick = { locate(true) }, enabled = !locationBusy) { Text(tr("Refresh device location", "רענון מיקום המכשיר")) }
        }
        error?.let { Notice(it, true) }
        Section(tr("Saved places", "מקומות שמורים")) {
            if (s.data.places.isEmpty()) Text(tr("No saved places. Search for a city or enter coordinates.", "אין מקומות שמורים. אפשר לחפש עיר או להזין קואורדינטות."))
            s.data.places.forEach { place ->
                Text((if (s.data.settings.location.savedId == place.id) "✓ " else "") + place.name, style = MaterialTheme.typography.titleMedium)
                Text("\u2066${place.latitude}, ${place.longitude} · ${place.zoneId}\u2069", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { model.settings { it.copy(location = LocationBinding(savedId = place.id)) } }) { Text(tr("Use this place", "בחירת מקום זה")) }
                    TextButton(onClick = { editing = place }) { Text(tr("Edit", "עריכה")) }
                    TextButton(onClick = { deletion = place }) { Text(tr("Delete", "מחיקה")) }
                }
            }
        }
        Section(tr("Find a city offline", "חיפוש עיר ללא אינטרנט")) {
            Field(tr("City name (2 or more letters)", "שם העיר (לפחות 2 אותיות)"), query, { query = it })
            if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(tr("Clear search", "ניקוי החיפוש")) }
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            cities.forEach { city -> TextButton(onClick = { editing = city.place() }) { Text("${city.name}, ${city.country} · ${city.zone}") } }
            if (!searching && query.length >= 2 && cities.isEmpty()) Text(tr("No matching cities. Enter coordinates manually.", "לא נמצאו ערים. ניתן להזין קואורדינטות ידנית."))
            OutlinedButton(onClick = { editing = Place(name = "", latitude = 0.0, longitude = 0.0, zoneId = ZoneId.systemDefault().id) }) { Text(tr("Enter a place manually", "הזנת מקום ידנית")) }
        }
        var attribution by remember { mutableStateOf(false) }
        TextButton(onClick = { attribution = !attribution }) { Text(tr("Location data credits", "מקורות נתוני המיקום")) }
        if (attribution) Text("GeoNames · CC BY 4.0; OpenStreetMap / timezone-boundary-builder · ODbL 1.0", style = MaterialTheme.typography.bodySmall)
    }
    editing?.let { PlaceEditor(it, model) { editing = null } }
    deletion?.let { place -> ConfirmDialog(tr("Delete ${place.name}?", "למחוק את ${place.name}?"),
        tr("Locations used by events, widgets, or the active calculation cannot be deleted until those bindings are changed.", "מקום שמשמש אירועים, יישומונים או חישוב פעיל לא יימחק עד להחלפת השיוכים."),
        tr("Delete", "מחיקה"), { deletion = null }) { deletion = null; model.act { model.repository.deletePlace(place.id) } } }
}

@Composable private fun PlaceEditor(initial: Place, model: AppViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var latitude by rememberSaveable { mutableStateOf(if (initial.name.isBlank()) "" else initial.latitude.toString()) }
    var longitude by rememberSaveable { mutableStateOf(if (initial.name.isBlank()) "" else initial.longitude.toString()) }
    var zone by rememberSaveable { mutableStateOf(if (initial.name.isBlank()) "" else initial.zoneId) }
    var elevation by rememberSaveable { mutableStateOf(initial.elevation?.toString().orEmpty()) }
    var lookup by remember { mutableStateOf<String?>(null) }
    var use by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    EditorDialog(tr("Saved location", "מקום שמור"), name != initial.name || latitude != initial.latitude.toString() || longitude != initial.longitude.toString() || zone != initial.zoneId || elevation != initial.elevation?.toString().orEmpty(),
        onDismiss, {
            val place = initial.copy(name = name.trim(), latitude = latitude.toDoubleOrNull() ?: error("Enter a valid latitude."),
                longitude = longitude.toDoubleOrNull() ?: error("Enter a valid longitude."), zoneId = zone.trim(),
                elevation = if (elevation.isBlank()) null else elevation.toDoubleOrNull() ?: error("Enter a valid elevation."),
                elevationSource = if (elevation == initial.elevation?.toString().orEmpty()) initial.elevationSource else "Entered by user")
            model.repository.savePlace(place)
            if (use) model.repository.settings { it.copy(location = LocationBinding(savedId = place.id)) }
        }) {
        Field(tr("Name", "שם"), name, { name = it })
        Field(tr("Latitude (−90 to 90)", "קו רוחב (מ־90− עד 90)"), latitude, { latitude = it }, numeric = true)
        Field(tr("Longitude (−180 to 180)", "קו אורך (מ־180− עד 180)"), longitude, { longitude = it }, numeric = true)
        Field(tr("IANA timezone", "אזור זמן IANA"), zone, { zone = it }, hint = "Asia/Jerusalem, America/New_York, Europe/London")
        OutlinedButton(enabled = !checking, onClick = { scope.launch {
            checking = true
            try {
                val zones = withContext(Dispatchers.IO) { model.app.places.timezones(latitude.toDouble(), longitude.toDouble()) }
                if (zones.size == 1) { zone = zones.single(); lookup = "Timezone found: $zone" }
                else lookup = "Confirm the timezone manually. Matches: ${zones.joinToString().ifBlank { "none" }}"
            } catch (e: Exception) { lookup = e.message } finally { checking = false }
        } }) { Text(tr("Find timezone offline", "איתור אזור זמן ללא אינטרנט")) }
        lookup?.let { Text(it) }
        Field(tr("Elevation in meters (optional)", "גובה במטרים (רשות)"), elevation, { elevation = it }, numeric = true)
        initial.elevationSource?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(tr("Elevation is used only when enabled in the selected profile. Below-sea-level adjustment is unsupported by the library.", "הגובה משמש רק כאשר הוא מופעל בפרופיל הנבחר. הספרייה אינה תומכת בתיקון לגובה מתחת לפני הים."))
        Toggle(tr("Use this place after saving", "בחירת מקום זה לאחר השמירה"), use, { use = it })
    }
}

@Composable fun LocationChoice(label: String, binding: LocationBinding, data: AppData, onChange: (LocationBinding) -> Unit) {
    val choices = listOf(LocationBinding(), LocationBinding(LocationMode.FOLLOW)) + data.places.map { LocationBinding(savedId = it.id) }
    Choice(label, binding, choices, { b -> when {
        b.mode == LocationMode.FOLLOW -> tr("Follow my location", "המיקום שלי")
        b.savedId == null -> tr("Choose a location", "בחירת מקום")
        else -> data.places.find { it.id == b.savedId }?.name ?: tr("Missing location", "מקום חסר")
    } }, onChange)
}

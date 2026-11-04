package com.example.zmanim.ui

import android.Manifest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zmanim.data.AppRepository
import com.example.zmanim.data.OfflinePlaces
import com.example.zmanim.domain.*
import kotlinx.coroutines.*
import java.time.ZoneId

@Composable fun LocationsDialog(s: ScreenData, model: AppViewModel,
    permission: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>, onDismiss: () -> Unit) {
    var editing by rememberDraft<Place?>("place-open", null)
    var deletion by remember { mutableStateOf<Place?>(null) }
    var search by rememberSaveable { mutableStateOf(false) }
    val busy by model.locationBusy.collectAsStateWithLifecycle()
    val error by model.locationError.collectAsStateWithLifecycle()
    val zoneChoice by model.zoneChoice.collectAsStateWithLifecycle()
    fun locate(follow: Boolean) {
        if (!model.useDeviceLocation(follow)) permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    EditorDialog(tr("App location", "מיקום האפליקציה"), false, onDismiss, {}, tr("Done", "סיום"), live = true) {
        Text(tr("Active location: ", "המקום הפעיל: ") + (s.data.place()?.name ?: tr("Choose a location", "בחירת מקום")))
        Text(tr("Calendar, zmanim, widgets and date notifications use this location. Events can use a separate saved place.", "לוח השנה, הזמנים, היישומונים והתראת התאריך משתמשים במקום זה. לאירועים ניתן לבחור מקום שמור נפרד."))
        HelpLink("locations")
        Button(onClick = { locate(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(tr("Use current location snapshot", "שמירת המיקום הנוכחי")) }
        Text(tr("Repeated snapshots update the same place. Independently named places stay separate.", "שמירה חוזרת מעדכנת את אותו המקום. מקומות ששמרתם בשמות נפרדים נשארים נפרדים."))
        Toggle(tr("Follow my location", "מעקב אחר המיקום שלי"), s.data.settings.location.mode == LocationMode.FOLLOW,
            { if (it) locate(true) else model.stopFollowing() }, enabled = !busy)
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(tr("Finding your location…", "מאתר את המיקום…")) }
        error?.let { Notice(localizeMessage(it, LocalHebrew.current), true, action = {
            TextButton(onClick = { locate(s.data.settings.location.mode == LocationMode.FOLLOW) }) { Text(tr("Retry", "ניסיון חוזר")) }
        }) }
        s.data.devicePlace?.let { fix ->
            if (s.data.settings.location.mode == LocationMode.FOLLOW || !fix.zoneConfirmed) {
                Text("\u2066${fix.latitude}, ${fix.longitude} · ${if (fix.zoneConfirmed) fix.zoneId else tr("Confirm timezone", "אישור אזור הזמן")}\u2069")
                FixInformation(fix)
                TextButton(onClick = { editing = fix }) { Text(tr("Change timezone / height", "שינוי אזור זמן / גובה")) }
                TextButton(onClick = { locate(true) }, enabled = !busy) { Text(tr("Refresh device location", "רענון מיקום המכשיר")) }
            }
        }
        Button(onClick = { search = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Find a city offline", "חיפוש עיר ללא אינטרנט")) }
        OutlinedButton(onClick = { editing = Place(name = "", latitude = 0.0, longitude = 0.0, zoneId = ZoneId.systemDefault().id) }) { Text(tr("Enter a place manually", "הזנת מקום ידנית")) }
        Section(tr("Saved places", "מקומות שמורים")) {
            if (s.data.places.isEmpty()) Text(tr("No saved places yet.", "אין עדיין מקומות שמורים."))
            s.data.places.forEach { place ->
                Text((if (s.data.settings.location.savedId == place.id && s.data.settings.location.mode != LocationMode.FOLLOW) "✓ " else "") + place.name, style = MaterialTheme.typography.titleMedium)
                Text("\u2066${place.latitude}, ${place.longitude} · ${place.zoneId}\u2069", style = MaterialTheme.typography.bodySmall)
                FixInformation(place)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { model.settings { it.copy(location = LocationBinding(savedId = place.id)) } }) { Text(tr("Use this place", "בחירת מקום זה")) }
                    TextButton(onClick = { editing = place }) { Text(tr("Edit", "עריכה")) }
                    TextButton(onClick = { deletion = place }) { Text(tr("Delete", "מחיקה")) }
                }
            }
        }
    }
    if (search) CitySearchDialog(model, { editing = it; search = false }) { search = false }
    zoneChoice?.let { fix -> ZoneConfirmation(fix, model::confirmDeviceZone, model::cancelZoneChoice) }
    editing?.let { PlaceEditor(it, model) { editing = null } }
    deletion?.let { place -> ConfirmDialog(tr("Delete ${place.name}?", "למחוק את ${place.name}?"),
        tr("Change active and event bindings before deleting a place.", "יש להחליף את המקום הפעיל ואת שיוכי האירועים לפני המחיקה."),
        tr("Delete", "מחיקה"), { deletion = null }) { deletion = null; model.act { model.repository.deletePlace(place.id) } } }
}

@Composable private fun FixInformation(place: Place) {
    place.obtainedAt?.let { Text(tr("Last fix: ", "מיקום אחרון: ") + java.time.Instant.ofEpochMilli(it).atZone(place.zone()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm")), style = MaterialTheme.typography.bodySmall) }
    place.accuracyMeters?.let { Text(tr("Accuracy: ±${it.toInt()} m", "דיוק: ±${it.toInt()} מ׳"), style = MaterialTheme.typography.bodySmall) }
}

@Composable private fun ZoneConfirmation(fix: Place, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var zone by rememberSaveable(fix.latitude, fix.longitude) { mutableStateOf(fix.zoneCandidates.firstOrNull().orEmpty()) }
    EditorDialog(tr("Confirm the location’s timezone", "אישור אזור הזמן של המקום"), false, onDismiss, { ZoneId.of(zone); onConfirm(zone) }) {
        Text(tr("Your coordinates were acquired and retained. Choose the civil timezone used here.", "הקואורדינטות התקבלו ונשמרו. יש לבחור את אזור הזמן האזרחי במקום זה."))
        Text("\u2066${fix.latitude}, ${fix.longitude}\u2069")
        if (fix.zoneCandidates.isNotEmpty()) Choice(tr("Timezone candidates", "אזורי זמן אפשריים"), zone, fix.zoneCandidates,
            { zoneName(it) }, { zone = it })
        Field(tr("IANA timezone (editable)", "אזור זמן IANA (ניתן לשינוי)"), zone, { zone = it }, hint = "Asia/Jerusalem, America/New_York")
    }
}
private fun zoneName(id: String): String = id.replace('_', ' ').replace("/", " · ") + " ($id)"

@Composable private fun CitySearchDialog(model: AppViewModel, onSelect: (Place) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cities by remember { mutableStateOf<List<OfflinePlaces.City>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        cities = emptyList(); error = null
        if (query.trim().length < 2) { busy = false; return@LaunchedEffect }
        busy = true
        try { delay(150); cities = withContext(Dispatchers.IO) { model.app.places.search(query) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { busy = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(tr("Back", "חזרה")) }
                Text(tr("Find a city offline", "חיפוש עיר ללא אינטרנט"), style = MaterialTheme.typography.titleLarge)
                Field(tr("City name (2 or more letters)", "שם עיר (לפחות שתי אותיות)"), query, { query = it })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Notice(it, true) }
                if (!busy && query.length >= 2 && cities.isEmpty()) Text(tr("No matching cities. Try another spelling or enter coordinates.", "לא נמצאו ערים. נסו איות אחר או הזינו קואורדינטות."))
                LazyColumn(Modifier.weight(1f)) {
                    items(cities, key = { it.id }) { city ->
                        TextButton(onClick = { onSelect(city.place()) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text("${city.name}, ${city.country}"); Text("${city.zone} · ${city.latitude}, ${city.longitude}", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                OutlinedButton(onClick = { onSelect(Place(name = query, latitude = 0.0, longitude = 0.0, zoneId = ZoneId.systemDefault().id)) }) { Text(tr("Enter manually", "הזנה ידנית")) }
            }
        }
    }
}

@Composable private fun PlaceEditor(initial: Place, model: AppViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var latitude by rememberSaveable { mutableStateOf(initial.latitude.toString()) }
    var longitude by rememberSaveable { mutableStateOf(initial.longitude.toString()) }
    var zone by rememberSaveable { mutableStateOf(initial.zoneId) }
    var elevation by rememberSaveable { mutableStateOf(initial.elevation?.toString().orEmpty()) }
    var terrain by remember { mutableStateOf<com.example.zmanim.platform.ElevationLookup.Result?>(null) }
    var lookup by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf(initial.zoneCandidates) }
    var israel by rememberSaveable { mutableStateOf(initial.holidayInIsrael) }
    var use by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val moved = latitude.toDoubleOrNull() != initial.latitude || longitude.toDoubleOrNull() != initial.longitude
    val matchedTerrain = terrain?.takeIf { it.latitude == latitude.toDoubleOrNull() && it.longitude == longitude.toDoubleOrNull() && it.meters.toString() == elevation }
    LaunchedEffect(latitude, longitude) { if (moved) { elevation = ""; terrain = null; israel = null } }
    EditorDialog(tr("Location details", "פרטי המקום"), name != initial.name || moved || zone != initial.zoneId || elevation != initial.elevation?.toString().orEmpty() || israel != initial.holidayInIsrael,
        onDismiss, {
            val place = initial.copy(name = name.trim(), latitude = latitude.toDoubleOrNull() ?: error("Enter a valid latitude."),
                longitude = longitude.toDoubleOrNull() ?: error("Enter a valid longitude."), zoneId = zone.trim(), zoneConfirmed = true, zoneUserSelected = true,
                holidayInIsrael = israel, localityId = if (moved) null else initial.localityId,
                elevation = if (elevation.isBlank()) null else elevation.toDoubleOrNull() ?: error("Enter a valid elevation."),
                elevationSource = when { matchedTerrain != null -> "Open Topo Data · SRTM · EGM96 mean sea level · meters"; !moved && elevation == initial.elevation?.toString().orEmpty() -> initial.elevationSource; else -> "Entered by user · meters above mean sea level" },
                elevationLatitude = matchedTerrain?.latitude ?: initial.elevationLatitude.takeIf { !moved && elevation == initial.elevation?.toString().orEmpty() },
                elevationLongitude = matchedTerrain?.longitude ?: initial.elevationLongitude.takeIf { !moved && elevation == initial.elevation?.toString().orEmpty() },
                elevationDataset = matchedTerrain?.dataset ?: initial.elevationDataset.takeIf { !moved && elevation == initial.elevation?.toString().orEmpty() },
                elevationObtainedAt = matchedTerrain?.obtainedAt ?: initial.elevationObtainedAt.takeIf { !moved && elevation == initial.elevation?.toString().orEmpty() })
            place.validate()
            if (place.id == AppRepository.DEVICE_ID) model.repository.update { it.copy(devicePlace = place) } else model.repository.savePlace(place)
            if (use) model.repository.settings { it.copy(location = if (place.id == AppRepository.DEVICE_ID) LocationBinding(LocationMode.FOLLOW) else LocationBinding(savedId = place.id)) }
        }) {
        Field(tr("Name", "שם"), name, { name = it })
        Field(tr("Latitude (−90 to 90)", "קו רוחב (מ־90− עד 90)"), latitude, { latitude = it }, numeric = true)
        Field(tr("Longitude (−180 to 180)", "קו אורך (מ־180− עד 180)"), longitude, { longitude = it }, numeric = true)
        Field(tr("IANA timezone", "אזור זמן IANA"), zone, { zone = it }, hint = "Asia/Jerusalem, America/New_York, Europe/London")
        if (candidates.isNotEmpty()) Choice(tr("Timezone candidates", "אזורי זמן אפשריים"), zone, candidates, { zoneName(it) }, { zone = it })
        OutlinedButton(enabled = !checking, onClick = { scope.launch {
            checking = true
            try {
                candidates = withContext(Dispatchers.IO) { model.app.places.timezones(latitude.toDouble(), longitude.toDouble()) }
                resolveDeviceZone(candidates, ZoneId.systemDefault().id)?.let { zone = it }
                lookup = if (candidates.isEmpty()) "No offline match. Enter the timezone manually." else "Confirm the timezone above."
            } catch (e: Exception) { lookup = e.message } finally { checking = false }
        } }) { Text(tr("Find timezone offline", "איתור אזור זמן ללא אינטרנט")) }
        Choice(tr("Holiday schedule default for this place", "ברירת מחדל לחגים במקום זה"), israel, listOf(null, true, false),
            { when (it) { null -> tr("Unconfirmed — choose in Calendar settings", "לא אושר — יש לבחור בהגדרות הלוח"); true -> tr("Israel", "ישראל"); false -> tr("Diaspora", "חו״ל") } }, { israel = it })
        Field(tr("Height above mean sea level in meters (optional)", "גובה מעל פני הים במטרים (רשות)"), elevation, { elevation = it; terrain = null }, numeric = true)
        if (!moved && initial.elevation != null && elevation == initial.elevation.toString()) {
            Text(initial.elevationSource ?: tr("Height source not recorded", "מקור הגובה לא נרשם"), style = MaterialTheme.typography.bodySmall)
            initial.elevationDataset?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            initial.elevationObtainedAt?.let { Text(tr("Height obtained: ", "הגובה התקבל: ") + java.time.Instant.ofEpochMilli(it).atZone(initial.zone()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu HH:mm")), style = MaterialTheme.typography.bodySmall) }
        }
        OutlinedButton(enabled = !checking, onClick = { scope.launch {
            checking = true
            try {
                val result = model.app.elevation.lookup(latitude.toDouble(), longitude.toDouble())
                terrain = result; elevation = result.meters.toString(); lookup = "${result.meters} m · ${result.dataset} · EGM96 mean sea level"
            } catch (e: Exception) { lookup = e.message ?: "Elevation lookup failed. Manual entry remains available." } finally { checking = false }
        } }) { Text(tr("Look up elevation", "איתור גובה")) }
        lookup?.let { Text(it) }
        Text(tr("Optional lookup sends these coordinates to Open Topo Data (SRTM90m, 60°S–60°N). Results are cached. Manual entry and calculations work offline. Zero means sea level; blank means unknown; negative height is retained but adjusted methods are unavailable.", "איתור לפי בחירה שולח קואורדינטות ל־Open Topo Data (SRTM90m, בין 60° דרום ל־60° צפון). התוצאות נשמרות. הזנה ידנית וחישובים זמינים ללא אינטרנט. אפס הוא פני הים, ריק הוא גובה לא ידוע, וגובה שלילי נשמר אך חישובים מותאמים אינם זמינים."))
        Text(tr("Explicit adjusted sunrise/sunset use this height. Other supported zmanim use the profile’s elevation setting. Terrain and horizon obstructions are not modeled.", "זריחה ושקיעה מותאמות משתמשות בגובה זה. זמנים נתמכים אחרים תלויים בהגדרת הגובה בפרופיל. חסימות נוף ואופק אינן נכללות בחישוב."))
        Toggle(tr("Use this place after saving", "בחירת מקום זה לאחר השמירה"), use, { use = it })
    }
}

@Composable fun LocationChoice(label: String, binding: LocationBinding, data: AppData, onChange: (LocationBinding) -> Unit) {
    val choices = listOf(LocationBinding(), LocationBinding(LocationMode.FOLLOW)) + data.places.map { LocationBinding(savedId = it.id) }
    Choice(label, binding, choices, { b -> when {
        b.savedId != null -> data.places.find { it.id == b.savedId }?.name ?: tr("Missing location", "מקום חסר")
        b.mode == LocationMode.FOLLOW -> tr("Follow my location", "המיקום שלי")
        else -> tr("App location", "מיקום האפליקציה") + (data.place()?.name?.let { " · $it" } ?: "")
    } }, onChange)
}

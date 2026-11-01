package com.example.zmanim

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.zmanim.data.RepositoryState
import com.example.zmanim.domain.*
import com.example.zmanim.ui.*
import com.example.zmanim.ui.theme.ZmanimTheme

class MainActivity : ComponentActivity() {
    private var launchDestination by mutableStateOf<String?>(null)
    private var launchEventId by mutableStateOf<String?>(null)
    private var launchDate by mutableStateOf<String?>(null)
    private var launchToken by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readLaunch(intent)
        setContent { ZmanimApp(launchDestination, launchEventId, launchDate, launchToken) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readLaunch(intent) }
    private fun readLaunch(intent: Intent) { launchDestination = intent.getStringExtra("destination"); launchEventId = intent.getStringExtra("eventId"); launchDate = intent.getStringExtra("date"); launchToken += 1 }
}

@Composable fun ZmanimApp(destination: String? = null, eventId: String? = null, launchDate: String? = null, launchToken: Int = 0, model: AppViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val screen by model.screen.collectAsStateWithLifecycle()
    val settings = (state as? RepositoryState.Ready)?.data?.settings ?: AppSettings()
    val configuration = LocalConfiguration.current
    val hebrew = settings.language == Language.HEBREW || settings.language == Language.SYSTEM && configuration.locales[0].language in setOf("he", "iw")
    val dark = when (settings.appearance) { Appearance.DARK -> true; Appearance.LIGHT -> false; Appearance.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme() }
    CompositionLocalProvider(LocalHebrew provides hebrew, LocalLabelHebrew provides (settings.labels == LabelLanguage.HEBREW), LocalProductLocale provides settings.productLocale(configuration.locales[0]), LocalLayoutDirection provides if (hebrew) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        ZmanimTheme(dark) {
            val activity = LocalContext.current as? android.app.Activity
            val view = LocalView.current
            SideEffect {
                activity?.window?.let { window ->
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
            }
            val nav = rememberNavController()
            val snackbar = remember { SnackbarHostState() }
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route ?: "calendar"
            var dialog by rememberSaveable { mutableStateOf<String?>(null) }
            var editing by rememberDraft<Event?>("editing-event", null)
            var occurrenceSnapshot by rememberDraft<OccurrenceSnapshot?>("editing-occurrence", null)
            val selectedOccurrence = occurrenceSnapshot?.restore()
            var nativeId by rememberSaveable { mutableStateOf<Long?>(null) }
            var nativeCalendar by rememberSaveable { mutableStateOf<Long?>(null) }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.refresh() }
            LaunchedEffect(model, hebrew) { model.message.collect { snackbar.showSnackbar(localizeMessage(it, hebrew)) } }
            var initialNavigationDone by rememberSaveable { mutableStateOf(false) }
            var handledLaunch by rememberSaveable { mutableStateOf("") }
            LaunchedEffect(destination, settings.onboardingComplete, screen != null, launchToken) {
                if (screen == null || (initialNavigationDone && destination == null)) return@LaunchedEffect
                val target = destination ?: if (settings.openingView == OpeningView.ZMANIM) "zmanim" else "calendar"
                if (target in listOf("calendar", "zmanim", "settings")) {
                    nav.navigate(target) { popUpTo(nav.graph.startDestinationId); launchSingleTop = true }
                    initialNavigationDone = true
                }
            }
            LaunchedEffect(eventId, screen != null, launchDate, launchToken) {
                val current = screen ?: return@LaunchedEffect
                val request = "$launchToken:$eventId:$launchDate"
                if (eventId != null && request != handledLaunch) current.data.events.find { it.id == eventId }?.let { event ->
                    val date = runCatching { java.time.LocalDate.parse(launchDate) }.getOrNull() ?: java.time.LocalDate.parse(event.anchorDate)
                    model.select(date); editing = event
                    occurrenceSnapshot = OccurrenceSnapshot.from(OccurrenceEngine().occurrence(event, date, current.data))
                    dialog = "event-detail"; handledLaunch = request
                }
            }
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { model.visible(true); model.refresh(); onPauseOrDispose { model.visible(false) } }
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                NavigationBar {
                    listOf(Triple("calendar", tr("Calendar", "לוח שנה"), Icons.Default.DateRange),
                        Triple("zmanim", tr("Zmanim", "זמנים"), Icons.AutoMirrored.Filled.List),
                        Triple("settings", tr("Settings", "הגדרות"), Icons.Default.Settings)).forEach { (path, label, icon) ->
                        NavigationBarItem(selected = route == path, enabled = screen != null, onClick = { nav.navigate(path) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
                    }
                }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (val current = state) {
                        is RepositoryState.Failed -> Column(Modifier.padding(24.dp)) {
                            Notice(current.message, true); TextButton(onClick = { model.repository.reload() }) { Text(tr("Retry", "ניסיון חוזר")) }
                        }
                        RepositoryState.Loading -> CircularProgressIndicator()
                        is RepositoryState.Ready -> if (screen == null) CircularProgressIndicator() else {
                            val s = screen!!
                            val openEvent: (Occurrence) -> Unit = { occurrence ->
                                if (occurrence.providerId != null) {
                                    model.act {
                                        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { model.app.calendars.ordinaryEvent(occurrence.providerId) }
                                        if (loaded == null || s.calendars.none { it.id == loaded.second && it.writable }) model.app.startActivity(model.app.calendars.viewIntent(occurrence.providerId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        else { editing = loaded.first; nativeId = occurrence.providerId; nativeCalendar = loaded.second; occurrenceSnapshot = null; dialog = "event" }
                                    }
                                } else { occurrenceSnapshot = OccurrenceSnapshot.from(occurrence); editing = s.data.events.find { it.id == occurrence.eventId }; nativeId = null; nativeCalendar = null; dialog = "event-detail" }
                            }
                            NavHost(nav, startDestination = "calendar") {
                                composable("calendar") { CalendarScreen(s, model, onNewEvent = { editing = Event(title = "", anchorDate = s.date.toString(), zoneId = s.data.place()?.zoneId ?: java.time.ZoneId.systemDefault().id,
                                        location = s.data.settings.location, profileId = s.data.settings.activeProfileId); nativeId = null; nativeCalendar = null; occurrenceSnapshot = null; dialog = "event" },
                                    onZmanim = { nav.navigate("zmanim") }, onEvent = openEvent, onGoTo = { dialog = "go-to" }) }
                                composable("zmanim") { ZmanimScreen(s, model, onProfiles = { dialog = "profiles" }, onMethods = { model.act { if (model.repository.data.profile() == null) model.repository.saveProfile(Profile()); dialog = "methods" } }, onLocations = { dialog = "locations" }) }
                                composable("settings") { SettingsScreen(s, model, permission, onProfiles = { dialog = "profiles" }, onLocations = { dialog = "locations" }, onBackup = { dialog = "backup" }) }
                            }
                            when (dialog) {
                                "event" -> EventEditor(s, model, editing, selectedOccurrence, nativeId, nativeCalendar, { dialog = null })
                                "event-detail" -> selectedOccurrence?.let { o -> EventDetails(s, model, o, onDismiss = { dialog = null }, onEdit = { scopeChoice ->
                                    dialog = "event"; nativeId = null; nativeCalendar = null
                                    if (scopeChoice == "series") occurrenceSnapshot = null
                                    else occurrenceSnapshot = OccurrenceSnapshot.from(o.copy(key = scopeChoice))
                                }) }
                                "profiles" -> ProfilesDialog(s, model) { dialog = null }
                                "methods" -> s.data.profile()?.let { MethodPicker(it, model) { dialog = null } }
                                "locations" -> LocationsDialog(s, model, permission) { dialog = null }
                                "go-to" -> GoToDialog(s.date, { model.select(it); dialog = null }, { dialog = null })
                                "backup" -> BackupDialog(s, model) { dialog = null }
                            }
                            if (!s.data.settings.onboardingComplete) Onboarding(s, model, onMethods = { model.act { if (model.repository.data.profile() == null) model.repository.saveProfile(Profile()); dialog = "methods" } }, onLocation = { dialog = "locations" })
                        }
                    }
                }
            }
        }
    }
}

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
    private var launchProvider by mutableStateOf<Long?>(null)
    private var launchInstance by mutableStateOf<Long?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchToken = savedInstanceState?.getInt("launch-token") ?: 0
        readLaunch(intent, savedInstanceState == null)
        setContent { ZmanimApp(launchDestination, launchEventId, launchDate, launchToken, launchProvider, launchInstance) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readLaunch(intent) }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("launch-token", launchToken); super.onSaveInstanceState(outState) }
    private fun readLaunch(intent: Intent, increment: Boolean = true) {
        launchDestination = intent.getStringExtra("destination")
        launchEventId = intent.getStringExtra("eventId")
        launchDate = intent.getStringExtra("date")
        launchProvider = if (intent.hasExtra("providerId")) intent.getLongExtra("providerId", 0) else null
        launchInstance = if (intent.hasExtra("instanceStart")) intent.getLongExtra("instanceStart", 0) else null
        if (increment) launchToken += 1
    }
}

@Composable fun ZmanimApp(destination: String? = null, eventId: String? = null, launchDate: String? = null, launchToken: Int = 0, providerId: Long? = null, instanceStart: Long? = null, model: AppViewModel = viewModel()) {
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
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.permissionsReturned(); model.refresh() }
            LaunchedEffect(model, hebrew) { model.message.collect { snackbar.showSnackbar(localizeMessage(it, hebrew)) } }
            var initialNavigationDone by rememberSaveable { mutableStateOf(false) }
            var handledLaunch by rememberSaveable { mutableStateOf("") }
            LaunchedEffect(screen != null, launchToken) {
                val current = screen ?: return@LaunchedEffect
                val request = "$launchToken:$destination:$eventId:$launchDate:$providerId:$instanceStart"
                if (handledLaunch == request) return@LaunchedEffect
                if (initialNavigationDone && destination == null && eventId == null && providerId == null && launchDate == null) {
                    handledLaunch = request; return@LaunchedEffect
                }
                val target = when (destination) {
                    "settings" -> "settings"
                    "zmanim" -> "zmanim"
                    "calendar", "location", "today" -> "calendar"
                    else -> if (settings.openingView == OpeningView.ZMANIM && eventId == null && providerId == null) "zmanim" else "calendar"
                }
                // A single launch effect decides destination and date before opening details.
                nav.navigate(target) { popUpTo(nav.graph.startDestinationId); launchSingleTop = true }
                if (destination == "today") model.today()
                runCatching { java.time.LocalDate.parse(launchDate) }.getOrNull()?.let(model::select)
                dialog = null; nativeId = null; nativeCalendar = null
                if (destination == "location") dialog = "locations"
                if (eventId != null) {
                    val event = current.data.events.find { it.id == eventId }
                    if (event == null) model.message.emit("This event was deleted.") else {
                        val date = runCatching { java.time.LocalDate.parse(launchDate) }.getOrNull() ?: java.time.LocalDate.parse(event.anchorDate)
                        val occurrences = OccurrenceEngine().expand(event, date, date.plusDays(1), current.data)
                        val occurrence = occurrences.find { it.start?.toEpochMilli() == instanceStart } ?: occurrences.firstOrNull()
                        if (occurrence == null) model.message.emit("This occurrence is no longer available.") else {
                            model.select(date); editing = event; occurrenceSnapshot = OccurrenceSnapshot.from(occurrence); dialog = "event-detail"
                        }
                    }
                } else if (providerId != null && instanceStart != null) {
                    val occurrence = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { model.app.calendars.instance(providerId, instanceStart) }
                    if (occurrence == null) model.message.emit("This device-calendar occurrence is no longer available.")
                    else { model.select(occurrence.date); occurrenceSnapshot = OccurrenceSnapshot.from(occurrence); dialog = "provider-detail" }
                }
                initialNavigationDone = true; handledLaunch = request
            }
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { model.visible(true); model.refresh(); onPauseOrDispose { model.visible(false) } }
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
                if (route in listOf("calendar", "zmanim")) NavigationBar {
                    val ready = screen
                    listOf(
                        Triple("gregorian", tr("Gregorian", "לועזי"), Icons.Default.DateRange),
                        Triple("hebrew", tr("Hebrew", "עברי"), Icons.Default.DateRange),
                        Triple("week", tr("Week", "שבוע"), Icons.Default.DateRange),
                        Triple("day", tr("Day", "יום"), Icons.Default.DateRange),
                        Triple("zmanim", tr("Zmanim", "זמנים"), Icons.AutoMirrored.Filled.List)
                    ).forEach { (path, label, icon) ->
                        val selected = route == "zmanim" && path == "zmanim" || route == "calendar" && when(path) {
                            "gregorian" -> settings.calendarView == CalendarView.MONTH && settings.primaryCalendar == CalendarSystem.GREGORIAN
                            "hebrew" -> settings.calendarView == CalendarView.MONTH && settings.primaryCalendar == CalendarSystem.HEBREW
                            "week" -> settings.calendarView == CalendarView.WEEK
                            "day" -> settings.calendarView == CalendarView.DAY
                            else -> false
                        }
                        NavigationBarItem(selected = selected, colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant), enabled = ready != null, onClick = {
                            if (path != "zmanim") model.settings { it.copy(
                                calendarView = when(path) { "week" -> CalendarView.WEEK; "day" -> CalendarView.DAY; else -> CalendarView.MONTH },
                                primaryCalendar = when(path) { "gregorian" -> CalendarSystem.GREGORIAN; "hebrew" -> CalendarSystem.HEBREW; else -> it.primaryCalendar }) }
                            nav.navigate(if (path == "zmanim") "zmanim" else "calendar") {
                                popUpTo(nav.graph.startDestinationId); launchSingleTop = true
                            }
                        }, icon = { if (path in listOf("hebrew", "week", "day")) Text(when(path) { "hebrew" -> "א"; "week" -> "7"; else -> "1" }, style = MaterialTheme.typography.titleLarge) else Icon(icon, null) }, label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) })
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
                                occurrenceSnapshot = OccurrenceSnapshot.from(occurrence)
                                nativeId = null; nativeCalendar = null
                                if (occurrence.providerId != null) dialog = "provider-detail"
                                else { editing = s.data.events.find { it.id == occurrence.eventId }; dialog = "event-detail" }
                            }
                            NavHost(nav, startDestination = "calendar") {
                                composable("calendar") { CalendarScreen(s, model, onNewEvent = { start -> editing = Event(title = "", anchorDate = start.date.toString(), localTime = (start.time ?: java.time.LocalTime.of(9, 0)).toString(), zoneId = s.data.place()?.zoneId ?: java.time.ZoneId.systemDefault().id,
                                        location = LocationBinding(), profileId = s.data.settings.activeProfileId); nativeId = null; nativeCalendar = null; occurrenceSnapshot = null; dialog = "event" },
                                    onZmanim = { nav.navigate("zmanim") }, onEvent = openEvent, onGoTo = { dialog = "go-to" }, onSettings = { nav.navigate("settings") }, onLocations = { dialog = "locations" }, onHelp = { dialog = "help" }) }
                                composable("zmanim") { ZmanimScreen(s, model, onProfiles = { dialog = "profiles" }, onMethods = { model.act { if (model.repository.data.profile() == null) model.repository.saveProfile(Profile()); dialog = "methods" } }, onLocations = { dialog = "locations" }, onCustom = { model.act { if (model.repository.data.profile() == null) model.repository.saveProfile(Profile()); dialog = "custom" } }, onGoTo = { dialog = "go-to" }, onSettings = { nav.navigate("settings") }, onHelp = { dialog = "help" }, onReminders = { dialog = "reminders" }, onProfileSettings = { dialog = "active-profile" }) }
                                composable("settings") { SettingsScreen(s, model, permission, onProfiles = { dialog = "profiles" }, onLocations = { dialog = "locations" }, onBackup = { dialog = "backup" }, onBack = { nav.popBackStack() }) }
                            }
                            when (dialog) {
                                "event" -> EventEditor(s, model, editing, selectedOccurrence, nativeId, nativeCalendar, { dialog = null })
                                "event-detail" -> selectedOccurrence?.let { o -> EventDetails(s, model, o, onDismiss = { dialog = null }, onEdit = { scopeChoice ->
                                    dialog = "event"; nativeId = null; nativeCalendar = null
                                    if (scopeChoice == "series") occurrenceSnapshot = null
                                    else occurrenceSnapshot = OccurrenceSnapshot.from(o.copy(key = scopeChoice))
                                }) }
                                "provider-detail" -> selectedOccurrence?.let { o -> ProviderEventDetails(s, model, o, { dialog = null }) { event, calendar ->
                                    editing = event; nativeId = o.providerId; nativeCalendar = calendar; occurrenceSnapshot = null; dialog = "event"
                                } }
                                "help" -> HelpDialog { dialog = null }
                                "reminders" -> ProfileRemindersDialog(s, model) { dialog = null }
                                "active-profile" -> s.data.profile()?.let { ProfileEditor(it, s, model) { dialog = null } }
                                "profiles" -> ProfilesDialog(s, model) { dialog = null }
                                "methods" -> s.data.profile()?.let { MethodPicker(it, s, model, onCustom = { dialog = "custom" }) { dialog = null } }
                                "custom" -> s.data.profile()?.let { CustomZmanDialog(it, s, model) { dialog = null } }
                                "locations" -> LocationsDialog(s, model, permission) { dialog = null }
                                "go-to" -> GoToDialog(s.date, { model.select(it); dialog = null }, { dialog = null }, settings.primaryCalendar)
                                "backup" -> BackupDialog(s, model) { dialog = null }
                            }

                        }
                    }
                }
            }
        }
    }
}

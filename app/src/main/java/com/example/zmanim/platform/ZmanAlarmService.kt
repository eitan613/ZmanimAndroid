package com.example.zmanim.platform

import android.app.*
import android.content.*
import android.media.*
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.zmanim.data.RepositoryState
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.zmanim.ZmanimApplication
import com.example.zmanim.data.appJson
import com.example.zmanim.domain.*
import com.example.zmanim.ui.theme.ZmanimTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Instant

@Serializable internal data class AlarmPayload(val id: String, val title: String, val text: String,
    val originalAt: Long, val rule: ReminderRule, val contextKey: String, val snoozedUntil: Long? = null)

/** Persisted ringing/snooze state is validated against recomputed rules before every recovery. */
@android.annotation.SuppressLint("ApplySharedPref") // Persist before alarm/service hand-off, including process death.
class ZmanAlarmController(private val context: Context) {
    private val prefs = context.getSharedPreferences("zman-alarm-state", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(AlarmManager::class.java)
    internal fun entries(): List<AlarmPayload> = prefs.all.values.mapNotNull { raw ->
        (raw as? String)?.let { runCatching { appJson.decodeFromString<AlarmPayload>(it) }.getOrNull() }
    }
    private fun save(payload: AlarmPayload) { prefs.edit().putString(payload.id, appJson.encodeToString(payload)).commit() }
    private fun snoozeIntent(id: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, SnoozedAlarmReceiver::class.java).setData(Uri.parse("zmanim://snooze/${Uri.encode(id)}")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    internal fun payload(id: String): AlarmPayload? = prefs.getString(id, null)?.let { appJson.decodeFromString(it) }
    internal fun remove(id: String) { manager.cancel(snoozeIntent(id)); prefs.edit().remove(id).commit() }
    fun ring(due: ReminderScheduler.Due) {
        val payload = AlarmPayload(due.id, due.title, due.text, due.at.toEpochMilli(), due.rule, due.contextKey)
        save(payload)
        start()
    }
    private fun start() = ContextCompat.startForegroundService(context, Intent(context, ZmanAlarmService::class.java))
    fun dismiss(id: String) { remove(id); context.startService(Intent(context, ZmanAlarmService::class.java).setAction("refresh")) }
    fun snooze(id: String) {
        val payload = payload(id) ?: return
        val reminders = (context.applicationContext as ZmanimApplication).reminders
        if (!reminders.preciseAccess()) { dismiss(id); return }
        val until = System.currentTimeMillis() + payload.rule.snoozeMinutes * 60_000L
        val updated = payload.copy(snoozedUntil = until)
        save(updated)
        try { manager.setAlarmClock(AlarmManager.AlarmClockInfo(until, alarmActivityIntent(context, id)), snoozeIntent(id)) }
        catch (_: SecurityException) { remove(id) }
        context.startService(Intent(context, ZmanAlarmService::class.java).setAction("refresh"))
    }
    internal fun resume(id: String) { payload(id)?.let { save(it.copy(snoozedUntil = null)); start() } }
    fun reconcile(data: AppData) {
        val entries = entries()
        if (entries.isEmpty()) return
        val reminders = (context.applicationContext as ZmanimApplication).reminders
        val valid = if (reminders.preciseAccess() && reminders.notificationAccess("zman-alarms"))
            reminders.upcoming(data, Instant.now().minusSeconds(86400), 10).associateBy { it.id } else emptyMap()
        entries.forEach { old ->
            val due = valid[old.id]
            if (due == null || due.rule != old.rule || due.contextKey != old.contextKey || due.at.toEpochMilli() != old.originalAt || due.title != old.title || due.text != old.text) remove(old.id)
            else if (old.snoozedUntil != null) {
                if (old.snoozedUntil < System.currentTimeMillis() - 5 * 60_000) {
                    // A reboot/time edit must not resurrect a stale snooze as a surprise alarm.
                    remove(old.id)
                } else if (old.snoozedUntil > System.currentTimeMillis()) runCatching { manager.setAlarmClock(AlarmManager.AlarmClockInfo(old.snoozedUntil, alarmActivityIntent(context, old.id)), snoozeIntent(old.id)) }
            }
        }
        ZmanAlarmService.current?.refresh()
    }
}

private fun alarmActivityIntent(context: Context, id: String) = PendingIntent.getActivity(context, 0,
    Intent(context, ZmanAlarmActivity::class.java).setData(Uri.parse("zmanim://alarm/${Uri.encode(id)}")).putExtra("alarmId", id),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

class ZmanAlarmService : Service() {
    companion object { @Volatile internal var current: ZmanAlarmService? = null }
    private var ringtone: Ringtone? = null
    internal fun isRinging(): Boolean = ringtone?.isPlaying == true || legacyPlayer?.isPlaying == true
    private var legacyPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var playing: String? = null
    private var audioFocus: AudioFocusRequest? = null
    private val posted = mutableSetOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate() { super.onCreate(); current = this }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = ZmanAlarmController(this)
        when (intent?.action) {
            "dismiss" -> intent.getStringExtra("alarmId")?.let { controller.remove(it) }
            "snooze" -> intent.getStringExtra("alarmId")?.let { controller.snooze(it) }
        }
        (application as ZmanimApplication).scope.launchAlarmRecovery(this)
        return START_STICKY
    }
    internal fun refresh() { handler.post { render() } }
    private fun render() {
        val controller = ZmanAlarmController(this)
        val active = controller.entries().filter { it.snoozedUntil == null }
        val notifications = getSystemService(NotificationManager::class.java)
        val ids = active.map { it.id.hashCode() }.toSet()
        (posted - ids).forEach { notifications.cancel(it) }; posted.retainAll(ids)
        if (active.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return }
        active.forEachIndexed { index, alarm ->
            val notification = notification(alarm)
            if (index == 0) startForeground(alarm.id.hashCode(), notification) else notifications.notify(alarm.id.hashCode(), notification)
            posted += alarm.id.hashCode()
        }
        val alarm = active.first()
        if (playing != alarm.id) {
            stopAudio(); playing = alarm.id
            val audio = getSystemService(AudioManager::class.java)
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            audioFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener { }.build().also { audio.requestAudioFocus(it) }
            val selected = alarm.rule.sound?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = runCatching { RingtoneManager.getRingtone(this, selected) }.getOrNull()
                ?: RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            if (Build.VERSION.SDK_INT >= 28) ringtone?.apply { audioAttributes = attributes; isLooping = true; play() }
            else {
                legacyPlayer = runCatching { MediaPlayer().apply { setAudioAttributes(attributes); setDataSource(this@ZmanAlarmService, selected); isLooping = true; prepare(); start() } }.getOrNull()
                    ?: MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null, attributes, 0)?.apply { isLooping = true; start() }
            }
            if (alarm.rule.vibrate) {
                @Suppress("DEPRECATION") val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vibrator = v; v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0), attributes)
            }
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:zman-alarm").apply { acquire(10 * 60_000L) }
        }
    }
    private fun notification(alarm: AlarmPayload): Notification {
        fun action(name: String) = PendingIntent.getService(this, 0,
            Intent(this, ZmanAlarmService::class.java).setAction(name).setData(Uri.parse("zmanim://alarm-action/$name/${Uri.encode(alarm.id)}")).putExtra("alarmId", alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val he = (application as ZmanimApplication).repository.data.settings.productLocale().language in setOf("he", "iw")
        val builder = NotificationCompat.Builder(this, "zman-alarms").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.title).setContentText(alarm.text).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(alarmActivityIntent(this, alarm.id))
            .addAction(0, if (he) "עצירה" else "Dismiss", action("dismiss"))
            .addAction(0, if (he) "נודניק ${alarm.rule.snoozeMinutes} דקות" else "Snooze ${alarm.rule.snoozeMinutes} min", action("snooze"))
        if (Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) builder.setFullScreenIntent(alarmActivityIntent(this, alarm.id), true)
        return builder.build()
    }
    private fun stopAudio() {
        ringtone?.stop(); ringtone = null; legacyPlayer?.release(); legacyPlayer = null; vibrator?.cancel(); vibrator = null
        audioFocus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }; audioFocus = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null; playing = null
    }
    override fun onDestroy() { stopAudio(); handler.removeCallbacksAndMessages(null); current = null; super.onDestroy() }
}

class SnoozedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment ?: return
        val pending = goAsync()
        (context.applicationContext as ZmanimApplication).runBackground(pending) { app ->
            ZmanAlarmController(context).reconcile(app.repository.data)
            ZmanAlarmController(context).resume(id)
        }
    }
}

class ZmanAlarmActivity : ComponentActivity() {
    private var alarmId by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        alarmId = intent.getStringExtra("alarmId")
        setContent {
            val repositoryState by (application as ZmanimApplication).repository.state.collectAsStateWithLifecycle()
            val settings = (repositoryState as? RepositoryState.Ready)?.data?.settings ?: AppSettings()
            ZmanimTheme(darkTheme = com.example.zmanim.ui.effectiveDark(settings)) {
                val controller = remember { ZmanAlarmController(this) }
                var entries by remember { mutableStateOf(controller.entries().filter { it.snoozedUntil == null }) }
                LaunchedEffect(Unit) { while (true) { entries = controller.entries().filter { it.snoozedUntil == null }; if (entries.isEmpty()) finish(); kotlinx.coroutines.delay(500) } }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), verticalArrangement = Arrangement.Center) {
                        val he = settings.productLocale().language in setOf("he", "iw")
                        entries.sortedBy { it.id != alarmId }.forEach { alarm ->
                            Text(alarm.title, style = MaterialTheme.typography.headlineLarge)
                            Text(alarm.text)
                            Button(onClick = { controller.dismiss(alarm.id) }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text(if (he) "עצירה" else "Dismiss") }
                            OutlinedButton(onClick = { controller.snooze(alarm.id) }, modifier = Modifier.fillMaxWidth()) { Text(if (he) "נודניק ${alarm.rule.snoozeMinutes} דקות" else "Snooze ${alarm.rule.snoozeMinutes} min") }
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); alarmId = intent.getStringExtra("alarmId") }
}

private fun kotlinx.coroutines.CoroutineScope.launchAlarmRecovery(service: ZmanAlarmService) = launch {
    val data = (service.application as ZmanimApplication).awaitData()
    ZmanAlarmController(service).reconcile(data)
    service.refresh()
}

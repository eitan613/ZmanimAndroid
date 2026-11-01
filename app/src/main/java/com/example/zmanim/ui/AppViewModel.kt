package com.example.zmanim.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.zmanim.ZmanimApplication
import com.example.zmanim.data.*
import com.example.zmanim.domain.*
import com.example.zmanim.platform.DeviceCalendar
import java.time.*
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DayZmanim(val date: LocalDate, val values: List<CalculatedZman>)
data class ScreenData(val data: AppData, val date: LocalDate, val now: Instant,
    val month: MonthModel, val occurrences: List<Occurrence>, val timeline: List<DayZmanim>,
    val calendars: List<DeviceCalendar>, val error: String? = null)

class AppViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val app = application as ZmanimApplication
    val repository get() = app.repository
    val selectedDay = saved.getStateFlow("selected-day", LocalDate.now().toEpochDay())
    private val now = MutableStateFlow(Instant.now())
    private val revision = MutableStateFlow(0L)
    private val timelineBounds = MutableStateFlow(-2L to 3L)
    private val _screen = MutableStateFlow<ScreenData?>(null)
    val screen = _screen.asStateFlow()
    val state = repository.state
    val message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private var ticker: Job? = null
    fun visible(visible: Boolean) {
        ticker?.cancel(); ticker = null
        if (visible) ticker = viewModelScope.launch {
            while (true) {
                now.value = Instant.now()
                if (saved.get<Boolean>("follow-today") != false) {
                    val zone = runCatching { repository.data.place()?.zone() }.getOrNull() ?: ZoneId.systemDefault()
                    val today = LocalDate.now(zone).toEpochDay()
                    if (today != selectedDay.value) { saved["selected-day"] = today; timelineBounds.value = -2L to 3L }
                }
                delay(1000)
            }
        }
    }
    init {
        viewModelScope.launch {
            // Recompute on data/day/window changes, not on every countdown tick.
            combine(repository.state, selectedDay, timelineBounds, revision) { state, epoch, bounds, _ -> Triple(state, epoch, bounds) }
                .collectLatest { (state, epoch, bounds) ->
                    val data = (state as? RepositoryState.Ready)?.data ?: return@collectLatest
                    val date = LocalDate.ofEpochDay(epoch)
                    val result = withContext(Dispatchers.Default) {
                        val engine = CalendarEngine()
                        val locale = data.settings.productLocale()
                        val month = engine.month(date, data.settings, locale)
                        val from = minOf(month.days.first().date, date.minusDays(7))
                        val end = maxOf(month.days.last().date.plusDays(1), date.plusDays(8))
                        val local = data.events.flatMap { OccurrenceEngine().expand(it, from, end, data) }
                        var error: String? = null
                        val device = try {
                            if (data.settings.showDeviceEvents) app.calendars.instances(from.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(86400),
                                end.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(86400), data.settings.visibleCalendarIds) else emptyList()
                        } catch (e: Exception) { error = e.message; emptyList() }
                        val managedProviderIds = data.exports.filter { it.state == "MANAGED" }.map { it.providerId }.toSet()
                        val place = data.place()
                        val profile = data.profile()
                        val timeline = if (place == null || profile == null || profile.selected.isEmpty()) emptyList() else
                            (bounds.first..bounds.second).map { offset ->
                                val day = date.plusDays(offset)
                                DayZmanim(day, CalculationEngine().calculate(CalculationQuery(day, place, profile)).filter { z ->
                                    val group = MethodCatalog.byId[z.id]?.group ?: "custom"
                                    group !in profile.hiddenGroups
                                })
                            }
                        ScreenData(data, date, now.value, month, (local + device.filter { it.providerId !in managedProviderIds }).sortedWith(compareBy({ it.date }, { it.start })),
                            timeline, runCatching { app.calendars.calendars() }.getOrDefault(emptyList()), error)
                    }
                    _screen.value = result
                }
        }
        viewModelScope.launch { now.collect { tick -> _screen.value = _screen.value?.copy(now = tick) } }
    }
    fun select(date: LocalDate) { saved["follow-today"] = false; saved["selected-day"] = date.toEpochDay(); timelineBounds.value = -2L to 3L }
    fun today() {
        select(LocalDate.now(runCatching { repository.data.place()?.zone() }.getOrNull() ?: ZoneId.systemDefault()))
        saved["follow-today"] = true
    }
    fun shift(amount: Int, years: Boolean = false) {
        val data = repository.data
        val date = LocalDate.ofEpochDay(selectedDay.value)
        select(if (!years && data.settings.calendarView != CalendarView.MONTH) date.plusDays(if (data.settings.calendarView == CalendarView.WEEK) amount * 7L else amount.toLong())
            else CalendarEngine().shift(date, data.settings.primaryCalendar, amount, years))
    }
    fun extendTimeline(before: Boolean) {
        val (a, b) = timelineBounds.value
        timelineBounds.value = if (before) (a - 7) to if (b - a >= 56) b - 7 else b
        else (if (b - a >= 56) a + 7 else a) to b + 7
    }
    fun refresh() { revision.value += 1; app.requestMaintenance() }
    fun act(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() } catch (e: Exception) { message.emit(e.message ?: "The change could not be saved. Try again.") }
    }
    fun settings(change: (AppSettings) -> AppSettings) = act { repository.settings(change) }
    suspend fun refreshLocation() {
        val place = app.location.refresh()
        repository.update { it.copy(devicePlace = place) }
    }
}

package com.example.zmanim

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.data.*
import com.example.zmanim.data.db.*
import com.example.zmanim.domain.*
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageAndPlacesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun roomTransactionsRollbackWithoutLosingExistingRecords() {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val p = Profile(id = "original", selected = listOf("sea-level-sunset"))
            db.dao().putProfileRow(ProfileRow(p.id, p.name, appJson.encodeToString(p)))
            try { db.runInTransaction {
                db.dao().deleteProfileRow(p.id)
                throw IllegalStateException("simulated failed import")
            } } catch (_: IllegalStateException) { }
            val restored = appJson.decodeFromString<Profile>(db.dao().profiles().single().payload)
            assertEquals(p, restored)
        } finally { db.close() }
    }
    @Test fun offlineCitiesAndCoordinateTimezonesWorkWithoutNetwork() {
        val places = OfflinePlaces(context)
        assertTrue(places.search("ירושלים").isNotEmpty())
        assertTrue(places.search("Jerusalem").isNotEmpty())
        assertEquals(listOf("Asia/Jerusalem"), places.timezones(31.778, 35.235))
        assertEquals(listOf("America/New_York"), places.timezones(40.7128, -74.0060))
        assertEquals(listOf("Pacific/Honolulu"), places.timezones(21.3099, -157.8581))
    }
    @Test fun splittingASeriesRetainsExceptionsAndRecursiveDeletionRemovesItsFamily() {
        kotlinx.coroutines.runBlocking {
            val app = context.applicationContext as ZmanimApplication
            val originalData = app.awaitData()
            try {
                val base = Event(id = "series-root", title = "Series", anchorDate = "2026-01-01", zoneId = "UTC", recurrence = Recurrence(Frequency.DAILY, count = 10))
                app.repository.update { it.copy(events = listOf(base), exports = emptyList()) }
                val exceptionDate = java.time.LocalDate.parse("2026-01-07")
                app.repository.replaceOccurrence(base, exceptionDate, base.copy(id = "exception", title = "Moved occurrence", anchorDate = "2026-01-08", recurrence = Recurrence()))
                val updated = app.repository.data.events.first { it.id == base.id }
                val split = base.copy(id = "following", anchorDate = "2026-01-05", recurrence = base.recurrence.copy(count = 6))
                app.repository.splitSeries(updated, java.time.LocalDate.parse("2026-01-05"), split)
                val current = app.repository.data
                assertEquals("following", current.events.first { it.id == "exception" }.parentId)
                assertTrue("2026-01-07" in current.events.first { it.id == "following" }.excludedDates)
                val expanded = current.events.flatMap { OccurrenceEngine().expand(it, java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-01-12"), current) }
                assertEquals(1, expanded.count { it.eventId == "exception" })
                assertFalse(expanded.any { it.eventId == "following" && it.date.toString() == "2026-01-07" })
                app.repository.deleteEvent(base.id)
                assertTrue(app.repository.data.events.isEmpty())
            } finally { app.repository.update { originalData } }
        }
    }

    @Test fun profileFieldUpdatesPreserveConcurrentSelections() = kotlinx.coroutines.runBlocking {
        val app = context.applicationContext as ZmanimApplication
        val before = app.awaitData()
        try {
            val profile = Profile(id = "atomic-profile")
            app.repository.saveProfile(profile)
            kotlinx.coroutines.coroutineScope {
                val jobs = listOf("sea-level-sunrise", "sea-level-sunset").map { id ->
                    async(kotlinx.coroutines.Dispatchers.Default) {
                        app.repository.updateProfile(profile.id) { it.copy(selected = it.selected + id) }
                    }
                }
                app.repository.updateProfile(profile.id) { it.copy(name = "Renamed") }
                jobs.forEach { it.await() }
            }
            val saved = app.repository.data.profile(profile.id)!!
            assertEquals("Renamed", saved.name)
            assertEquals(setOf("sea-level-sunrise", "sea-level-sunset"), saved.selected.toSet())
        } finally { app.repository.update { before } }
    }

}

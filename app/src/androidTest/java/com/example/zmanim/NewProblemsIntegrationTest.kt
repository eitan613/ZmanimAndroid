package com.example.zmanim

import android.app.NotificationManager
import android.content.Intent
import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.data.OfflinePlaces
import com.example.zmanim.domain.*
import com.example.zmanim.platform.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class NewProblemsIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun originalCoordinatesAndReviewedLocalitiesResolveOffline() {
        val places = OfflinePlaces(context)
        val zones = places.timezones(31.8645075,35.1447088)
        assertTrue(zones.containsAll(listOf("Asia/Jerusalem","Asia/Hebron")))
        val location = Location("test").apply { latitude=31.8645075; longitude=35.1447088; time=System.currentTimeMillis(); accuracy=10f }
        val acquired = DeviceLocation(context,places).toPlace(location)
        assertEquals(location.latitude,acquired.latitude,0.0)
        for (query in listOf("Givat Zev","Givat Zeev","Giv'at Ze'ev","גבעת זאב")) {
            val city=places.search(query).first()
            assertEquals("8199386",city.id);assertEquals("IL",city.country);assertEquals("Asia/Jerusalem",city.zone);assertEquals(true,city.place().holidayInIsrael)
        }
        for ((query,id) in listOf("Beit El" to "12156437","Efrata" to "283991","Ariel" to "8199394")) {
            val city=places.search(query).first { it.id==id }
            assertEquals("IL",city.country);assertEquals("Asia/Jerusalem",city.zone)
        }
        assertEquals("PS",places.search("Bethlehem").first { it.id=="284315" }.country)
        assertFalse(places.search("ביתר עילית").any { it.id=="284375" })
    }
    @Test fun profileDuplicationRemapsNamesCustomReferencesAndReminderIds() = runBlocking {
        val app=context.applicationContext as ZmanimApplication
        val original=app.awaitData()
        try {
            val a=CustomZman(id="custom-a",name="A",kind=FormulaKind.FIXED_MINUTES)
            val b=a.copy(id="custom-b",name="B",base=a.id)
            val p=Profile(id="original-profile",name="Original",custom=listOf(a,b),selected=listOf(a.id,b.id),displayNames=mapOf(a.id to "Renamed A"),
                reminders=listOf(ZmanReminder(b.id,listOf(ReminderRule(mode=ReminderMode.ALARM)))))
            app.repository.update { AppData(profiles=listOf(p),settings=AppSettings(activeProfileId=p.id)) }
            app.repository.duplicateProfile(p)
            val copy=app.repository.data.profiles.single { it.id!=p.id }
            assertEquals("Renamed A",copy.displayNames[copy.custom[0].id])
            assertEquals(copy.custom[0].id,copy.custom[1].base)
            assertEquals(copy.custom[1].id,copy.reminders.single().methodId)
            assertNotEquals(p.reminders.single().reminders.single().id,copy.reminders.single().reminders.single().id)
        } finally { app.repository.update { original } }
    }
    @Test fun suppressionAndRuleSwitchesApplyToNotificationsAndAlarms() {
        val app=context.applicationContext as ZmanimApplication
        val from=Instant.parse("2026-11-04T00:00:00Z")
        val scheduler=ReminderScheduler(context,Clock.fixed(from,ZoneOffset.UTC))
        val place=Place(id="p",name="Jerusalem",latitude=31.778,longitude=35.235,zoneId="Asia/Jerusalem")
        val p=Profile(id="profile",selected=listOf("sea-level-sunset"),reminders=listOf(ZmanReminder("sea-level-sunset",listOf(ReminderRule(),ReminderRule(mode=ReminderMode.ALARM)))))
        val data=AppData(settings=AppSettings(activeProfileId=p.id,location=LocationBinding(savedId=place.id)),places=listOf(place),profiles=listOf(p))
        assertEquals(setOf(ReminderMode.NOTIFICATION,ReminderMode.ALARM),scheduler.upcoming(data).map { it.rule.mode }.toSet())
        for (changed in listOf(p.copy(hiddenGroups=setOf("sunset")),p.copy(selected=emptyList()),p.copy(remindersEnabled=false),p.copy(reminders=p.reminders.map { it.copy(reminders=it.reminders.map { r->r.copy(enabled=false) }) })))
            assertTrue(scheduler.upcoming(data.copy(profiles=listOf(changed))).isEmpty())
        assertTrue(scheduler.upcoming(data.copy(settings=data.settings.copy(activeProfileId="other"))).isEmpty())
    }
    @Test fun realAlarmRingsAndSnoozesAndCancelsWhenItsGroupIsHidden() = runBlocking {
        val app=context.applicationContext as ZmanimApplication
        val original=app.awaitData()
        assumeTrue(app.reminders.preciseAccess() && app.reminders.notificationAccess("zman-alarms"))
        val controller=ZmanAlarmController(context)
        val place=Place(id="alarm-place",name="Jerusalem",latitude=31.778,longitude=35.235,zoneId="Asia/Jerusalem")
        val date=LocalDate.now(place.zone())
        val sunset=CalculationEngine().seaLevelSunset(date,place)!!
        val target=Instant.now().plusSeconds(10)
        val custom=CustomZman(id="alarm-test-zman",name="Real alarm test",kind=FormulaKind.FIXED_MINUTES,
            base="sea-level-sunset",amount=Duration.between(sunset,target).toMillis()/60_000.0)
        val rule=ReminderRule(mode=ReminderMode.ALARM,snoozeMinutes=1)
        val p=Profile(id="alarm-test-profile",selected=listOf(custom.id),custom=listOf(custom),reminders=listOf(ZmanReminder(custom.id,listOf(rule))))
        try {
            app.repository.update { AppData(settings=AppSettings(activeProfileId=p.id,location=LocationBinding(savedId=place.id)),places=listOf(place),profiles=listOf(p)) }
            app.reminders.reconcile(app.repository.data)
            withTimeout(30_000) { while (ZmanAlarmService.current?.isRinging()!=true) delay(100) }
            val ringing=controller.entries().single { it.title==custom.name }
            val notification=context.getSystemService(NotificationManager::class.java).activeNotifications.first { it.notification.extras.getString(android.app.Notification.EXTRA_TITLE)==custom.name }
            assertEquals(2,notification.notification.actions.size)
            controller.snooze(ringing.id)
            withTimeout(5000) { while (ZmanAlarmService.current?.isRinging()==true) delay(50) }
            assertTrue(controller.entries().single().snoozedUntil!!>System.currentTimeMillis())
            app.repository.updateProfile(p.id) { it.copy(hiddenGroups=setOf("custom")) }
            controller.reconcile(app.repository.data)
            assertTrue(controller.entries().isEmpty())
        } finally {
            controller.entries().forEach { controller.dismiss(it.id) }
            app.repository.update { original };app.reminders.reconcile(original)
        }
    }
    @Test fun learningTablesMatchIndependentReferenceDatesAndRespectFiniteCoverage() {
        val app=context.applicationContext as ZmanimApplication
        val settings=AppSettings(labels=LabelLanguage.TRANSLITERATED)
        val date=LocalDate.of(2026,11,4)
        val rows=app.learning.rows(date,listOf("bavli","mishna","rambam-one","rambam-three","tanya"),settings)
        assertTrue(rows[0].value.contains("47"));assertEquals("Oholot 15:4-5",rows[1].value)
        assertEquals("Forbidden Intercourse 16",rows[2].value)
        assertEquals("Ownerless Property and Gifts 7-9",rows[3].value)
        assertTrue(rows[4].value.contains("30"));assertTrue(rows[4].value.contains("paragraph 1"))
        assertTrue(app.learning.rows(LocalDate.of(2027,9,1),listOf("dirshu-halacha"),settings).single().value.startsWith("Unavailable"))
        val yomKippur=com.kosherjava.zmanim.hebrewcalendar.JewishDate(5787,7,10).localDate
        assertEquals("No learning today",app.learning.rows(yomKippur,listOf("yerushalmi-vilna"),settings).single().value)
        assertEquals("Yevamot 88",app.learning.rows(date,listOf("yerushalmi-oz"),settings).single().value)
        assertEquals("Niddah 11",app.learning.rows(LocalDate.of(2028,8,7),listOf("yerushalmi-oz"),settings).single().value)
        assertEquals("Berakhot 1",app.learning.rows(LocalDate.of(2028,8,8),listOf("yerushalmi-oz"),settings).single().value)
        assertTrue(app.learning.rows(LocalDate.of(2034,5,3),listOf("yerushalmi-oz"),settings).single().value.startsWith("Unavailable"))
        assertEquals("Ketubot 3b · Review Ketubot 3a",app.learning.rows(date,listOf("oraysa"),settings).single().value)
        assertEquals("Review Ketubot 2a, Ketubot 2b",app.learning.rows(LocalDate.of(2026,11,6),listOf("oraysa"),settings).single().value)
        assertTrue(app.learning.rows(LocalDate.of(2026,11,8),listOf("oraysa"),settings).single().value.startsWith("Unavailable"))
    }
}

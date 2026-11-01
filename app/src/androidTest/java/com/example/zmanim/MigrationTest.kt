package com.example.zmanim

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zmanim.data.AppRepository
import com.example.zmanim.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
    @Test fun migrationAddsRecoveryJournalAndPreservesProfiles() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO profiles (id, name, payload) VALUES ('retained', 'Retained profile', '{}')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 2, true, AppRepository.MIGRATION_1_2).use { db ->
            db.query("SELECT name FROM profiles WHERE id = 'retained'").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("Retained profile", cursor.getString(0))
            }
            db.execSQL("INSERT INTO settings_journal (id, payload) VALUES (1, '{}')")
        }
    }
}

package com.example.zmanim.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.zmanimDataStore by preferencesDataStore(name = "zmanim_preferences")

enum class SolarMethod(val id: String) {
    SUNRISE("sea-level-sunrise"),
    SUNSET("sea-level-sunset"),
}

class ZmanimPreferences(context: Context) {
    private val store = context.applicationContext.zmanimDataStore
    private val selectedKey = stringSetPreferencesKey("selected_zmanim")

    val selectedMethods: Flow<Set<SolarMethod>> = store.data.map { preferences ->
        val ids = preferences[selectedKey].orEmpty()
        SolarMethod.entries.filter { it.id in ids }.toSet()
    }

    suspend fun saveSelection(methods: Set<SolarMethod>) {
        store.edit { it[selectedKey] = methods.map { method -> method.id }.toSet() }
    }
}

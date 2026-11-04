package com.example.zmanim.data.db;
import androidx.room.*;
@Database(entities = {ProfileRow.class, PlaceRow.class, EventRow.class, WidgetRow.class, ExportRow.class, SettingsJournalRow.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AppDao dao();
}

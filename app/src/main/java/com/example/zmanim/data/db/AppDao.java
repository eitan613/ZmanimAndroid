package com.example.zmanim.data.db;
import androidx.room.*;
import java.util.List;
@Dao
public interface AppDao {
    @androidx.annotation.Nullable @Query("SELECT * FROM settings_journal WHERE id = 1") SettingsJournalRow pendingSettings();
    @Upsert void putSettings(SettingsJournalRow row);
    @Query("DELETE FROM settings_journal") void clearSettings();
    @Query("SELECT * FROM profiles") List<ProfileRow> profiles();
    @Upsert void putProfileRow(ProfileRow row);
    @Query("DELETE FROM profiles WHERE id = :id") void deleteProfileRow(String id);
    @Query("SELECT * FROM places") List<PlaceRow> places();
    @Upsert void putPlaceRow(PlaceRow row);
    @Query("DELETE FROM places WHERE id = :id") void deletePlaceRow(String id);
    @Query("SELECT * FROM events") List<EventRow> events();
    @Upsert void putEventRow(EventRow row);
    @Query("DELETE FROM events WHERE id = :id") void deleteEventRow(String id);
    @Query("SELECT * FROM widgets") List<WidgetRow> widgets();
    @Upsert void putWidgetRow(WidgetRow row);
    @Query("DELETE FROM widgets WHERE id = :id") void deleteWidgetRow(String id);
    @Query("SELECT * FROM exports") List<ExportRow> exports();
    @Upsert void putExportRow(ExportRow row);
    @Query("DELETE FROM exports WHERE id = :id") void deleteExportRow(String id);
}

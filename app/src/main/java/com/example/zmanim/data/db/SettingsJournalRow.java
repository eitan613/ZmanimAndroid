package com.example.zmanim.data.db;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "settings_journal")
public class SettingsJournalRow {
    @PrimaryKey public int id;
    @NonNull public String payload;
    public SettingsJournalRow(int id, @NonNull String payload) { this.id = id; this.payload = payload; }
}

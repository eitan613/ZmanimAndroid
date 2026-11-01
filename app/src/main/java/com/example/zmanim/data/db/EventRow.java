package com.example.zmanim.data.db;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
@Entity(tableName = "events", indices = {@Index("title")})
public class EventRow {
    @PrimaryKey @NonNull public String id;
    @NonNull public String title;
    @NonNull public String payload;
    public EventRow(@NonNull String id, @NonNull String title, @NonNull String payload) {
        this.id = id; this.title = title; this.payload = payload;
    }
}

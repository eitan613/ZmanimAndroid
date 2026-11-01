package com.example.zmanim.data.db;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
@Entity(tableName = "exports", indices = {@Index("eventId")})
public class ExportRow {
    @PrimaryKey @NonNull public String id;
    @NonNull public String eventId;
    @NonNull public String payload;
    public ExportRow(@NonNull String id, @NonNull String eventId, @NonNull String payload) {
        this.id = id; this.eventId = eventId; this.payload = payload;
    }
}

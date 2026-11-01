package com.example.zmanim.data.db;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
@Entity(tableName = "widgets", indices = {@Index("kind")})
public class WidgetRow {
    @PrimaryKey @NonNull public String id;
    @NonNull public String kind;
    @NonNull public String payload;
    public WidgetRow(@NonNull String id, @NonNull String kind, @NonNull String payload) {
        this.id = id; this.kind = kind; this.payload = payload;
    }
}

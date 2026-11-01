package com.example.zmanim.data.db;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Index;
@Entity(tableName = "places", indices = {@Index("name")})
public class PlaceRow {
    @PrimaryKey @NonNull public String id;
    @NonNull public String name;
    @NonNull public String payload;
    public PlaceRow(@NonNull String id, @NonNull String name, @NonNull String payload) {
        this.id = id; this.name = name; this.payload = payload;
    }
}

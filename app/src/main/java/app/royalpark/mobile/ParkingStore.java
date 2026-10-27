package app.royalpark.mobile;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local-only storage: no login, analytics or cloud connection. */
public final class ParkingStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "royalpark.db";
    private static final int DB_VERSION = 1;
    private final Context context;

    public ParkingStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE parking_spots ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "latitude REAL NOT NULL, longitude REAL NOT NULL,"
                + "note TEXT NOT NULL DEFAULT '', parked_at INTEGER NOT NULL,"
                + "source TEXT NOT NULL, timer_at INTEGER NOT NULL DEFAULT 0,"
                + "photo_uri TEXT NOT NULL DEFAULT '')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future schema changes will use additive migrations: never drop users' history.
        throw new IllegalStateException("Migration missing for version " + oldVersion);
    }

    public synchronized ParkingSpot save(double latitude, double longitude, String note, String source) {
        if (!ParkingLogic.validCoordinates(latitude, longitude)) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
        ContentValues row = new ContentValues();
        row.put("latitude", latitude);
        row.put("longitude", longitude);
        row.put("note", note == null ? "" : note);
        row.put("parked_at", System.currentTimeMillis());
        row.put("source", source);
        long id = getWritableDatabase().insertOrThrow("parking_spots", null, row);
        setActiveId(id);
        return getById(id);
    }

    public synchronized void setActiveId(long id) {
        context.getSharedPreferences("royalpark", Context.MODE_PRIVATE).edit()
                .putLong("active_spot", id).apply();
    }

    public synchronized ParkingSpot active() {
        long id = context.getSharedPreferences("royalpark", Context.MODE_PRIVATE)
                .getLong("active_spot", -1);
        return id < 0 ? null : getById(id);
    }

    public synchronized ParkingSpot getById(long id) {
        try (Cursor c = getReadableDatabase().query("parking_spots", null,
                "id=?", new String[]{Long.toString(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public synchronized List<ParkingSpot> history() {
        List<ParkingSpot> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("parking_spots", null,
                null, null, null, null, "parked_at DESC, id DESC")) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public synchronized void setNote(long id, String note) {
        ContentValues row = new ContentValues();
        row.put("note", note == null ? "" : note);
        getWritableDatabase().update("parking_spots", row, "id=?", new String[]{Long.toString(id)});
    }

    public synchronized void setTimer(long id, long timerAt) {
        ContentValues row = new ContentValues();
        row.put("timer_at", timerAt);
        getWritableDatabase().update("parking_spots", row, "id=?", new String[]{Long.toString(id)});
    }

    public synchronized void setPhoto(long id, String uri) {
        ContentValues row = new ContentValues();
        row.put("photo_uri", uri == null ? "" : uri);
        getWritableDatabase().update("parking_spots", row, "id=?", new String[]{Long.toString(id)});
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("parking_spots", "id=?", new String[]{Long.toString(id)});
        if (context.getSharedPreferences("royalpark", Context.MODE_PRIVATE)
                .getLong("active_spot", -1) == id) {
            context.getSharedPreferences("royalpark", Context.MODE_PRIVATE).edit()
                    .remove("active_spot").apply();
        }
    }

    private static ParkingSpot fromCursor(Cursor c) {
        return new ParkingSpot(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getDouble(c.getColumnIndexOrThrow("latitude")),
                c.getDouble(c.getColumnIndexOrThrow("longitude")),
                c.getString(c.getColumnIndexOrThrow("note")),
                c.getLong(c.getColumnIndexOrThrow("parked_at")),
                c.getString(c.getColumnIndexOrThrow("source")),
                c.getLong(c.getColumnIndexOrThrow("timer_at")),
                c.getString(c.getColumnIndexOrThrow("photo_uri")));
    }
}

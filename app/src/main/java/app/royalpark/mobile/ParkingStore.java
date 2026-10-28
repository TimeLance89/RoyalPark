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
    private static final int DB_VERSION = 2;
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
                + "photo_uri TEXT NOT NULL DEFAULT '',"
                + "garage_floor TEXT NOT NULL DEFAULT '',"
                + "garage_spot TEXT NOT NULL DEFAULT '')");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2 && newVersion >= 2) {
            db.execSQL("ALTER TABLE parking_spots ADD COLUMN garage_floor TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE parking_spots ADD COLUMN garage_spot TEXT NOT NULL DEFAULT ''");
        }
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

    public synchronized void setGarage(long id, String floor, String spot) {
        ContentValues values = new ContentValues();
        values.put("garage_floor", GarageLabels.clean(floor));
        values.put("garage_spot", GarageLabels.clean(spot));
        getWritableDatabase().update("parking_spots", values, "id=?",
                new String[]{Long.toString(id)});
    }

    public synchronized boolean contains(double lat, double lon, long parkedAt) {
        return contains(getReadableDatabase(), lat, lon, parkedAt);
    }

    private boolean contains(SQLiteDatabase db, double lat, double lon, long parkedAt) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM parking_spots "
                        + "WHERE latitude=? AND longitude=? AND parked_at=? LIMIT 1",
                new String[]{Double.toString(lat), Double.toString(lon), Long.toString(parkedAt)})) {
            return c.moveToFirst();
        }
    }

    /** Merge imported records transactionally, preserving all existing rows and active choice. */
    public synchronized int mergeBackup(List<ParkingArchive.Record> imported) {
        SQLiteDatabase db = getWritableDatabase();
        boolean activeExists = active() != null;
        long restoredActive = -1;
        int count = 0;
        db.beginTransaction();
        try {
            for (ParkingArchive.Record record : imported) {
                if (contains(db, record.latitude, record.longitude, record.parkedAt)) continue;
                ContentValues row = new ContentValues();
                row.put("latitude", record.latitude);
                row.put("longitude", record.longitude);
                row.put("note", record.note);
                row.put("source", record.source);
                row.put("parked_at", record.parkedAt);
                row.put("timer_at", 0); // Imported timers must be rearmed explicitly.
                row.put("photo_uri", record.photoUri);
                row.put("garage_floor", record.floor);
                row.put("garage_spot", record.space);
                long id = db.insertOrThrow("parking_spots", null, row);
                if (record.active && restoredActive < 0) restoredActive = id;
                count++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (!activeExists && restoredActive >= 0) setActiveId(restoredActive);
        return count;
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
                c.getString(c.getColumnIndexOrThrow("photo_uri")),
                c.getString(c.getColumnIndexOrThrow("garage_floor")),
                c.getString(c.getColumnIndexOrThrow("garage_spot")));
    }
}

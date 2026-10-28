package app.royalpark.mobile;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * RoyalPark's portable, user-initiated backup.
 * Uses Android's document picker, never requests broad storage access.
 * Archives contain precise GPS locations: keep them private.
 */
public final class ParkingArchive {
    private static final int MAX_RECORDS = 5000;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final long MAX_ARCHIVE_UNCOMPRESSED = 120L * 1024 * 1024;
    private static final int MAX_JSON = 2 * 1024 * 1024;
    private static final int MAX_ENTRIES = 5002;
    private static final String FORMAT = "royalpark-parking-backup";
    private ParkingArchive() {}

    public static final class Result {
        public final int entries;
        public final int photos;
        public final int skippedPhotos;
        Result(int entries, int photos, int skippedPhotos) {
            this.entries = entries;
            this.photos = photos;
            this.skippedPhotos = skippedPhotos;
        }
    }

    static final class Record {
        long originalId;
        double latitude, longitude;
        long parkedAt;
        String note, source, floor, space, photoEntry;
        String photoUri = "";
        boolean active;
    }

    public static Result exportTo(Context context, ParkingStore store, Uri destination)
            throws IOException, JSONException {
        List<ParkingSpot> history = store.history();
        if (history.size() > MAX_RECORDS) throw new IOException("Zu viele Parkplätze für eine Sicherung.");
        ParkingSpot active = store.active();
        JSONArray records = new JSONArray();
        int exportedPhotos = 0, skippedPhotos = 0;
        OutputStream sink = context.getContentResolver().openOutputStream(destination, "w");
        if (sink == null) throw new IOException("Zieldatei konnte nicht geöffnet werden.");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(sink))) {
            for (ParkingSpot spot : history) {
                JSONObject item = new JSONObject()
                        .put("id", spot.id)
                        .put("latitude", spot.latitude)
                        .put("longitude", spot.longitude)
                        .put("parkedAt", spot.parkedAt)
                        .put("source", spot.source)
                        .put("note", spot.note)
                        .put("floor", spot.floor)
                        .put("space", spot.space)
                        .put("active", active != null && active.id == spot.id);
                if (!spot.photoUri.isEmpty()) {
                    String extension = photoExtension(context, Uri.parse(spot.photoUri));
                    String name = "photos/" + spot.id + "." + extension;
                    try (InputStream photo = context.getContentResolver()
                            .openInputStream(Uri.parse(spot.photoUri))) {
                        if (photo != null) {
                            zip.putNextEntry(new ZipEntry(name));
                            writePhoto(photo, zip);
                            zip.closeEntry();
                            item.put("photo", name);
                            exportedPhotos++;
                        } else skippedPhotos++;
                    } catch (SecurityException e) {
                        skippedPhotos++;
                    } catch (java.io.FileNotFoundException e) {
                        skippedPhotos++;
                    }
                }
                records.put(item);
            }
            JSONObject manifest = new JSONObject()
                    .put("format", FORMAT).put("version", 1)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("records", records);
            byte[] bytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_JSON) throw new IOException("Sicherung zu groß.");
            zip.putNextEntry(new ZipEntry("parking.json"));
            zip.write(bytes);
            zip.closeEntry();
        }
        return new Result(history.size(), exportedPhotos, skippedPhotos);
    }

    private static String photoExtension(Context context, Uri uri) {
        String mime;
        try { mime = context.getContentResolver().getType(uri); }
        catch (SecurityException denied) { mime = null; }
        if ("image/png".equals(mime)) return "png";
        if ("image/webp".equals(mime)) return "webp";
        return "jpg";
    }

    private static void writePhoto(InputStream input, OutputStream output) throws IOException {
        byte[] chunk = new byte[8192];
        long count = 0;
        int n;
        while ((n = input.read(chunk)) != -1) {
            count += n;
            if (count > MAX_IMAGE_BYTES) throw new IOException("Ein Foto ist größer als 8 MB.");
            output.write(chunk, 0, n);
        }
    }

    public static Result restoreFrom(Context context, ParkingStore store, Uri source)
            throws IOException, JSONException {
        File stage = new File(context.getCacheDir(), "restore-" + UUID.randomUUID());
        if (!stage.mkdirs()) throw new IOException("Zwischenspeicher nicht verfügbar.");
        Map<String, File> photos = new HashMap<>();
        Set<String> seenEntries = new HashSet<>();
        String rawManifest = null;
        long total = 0;
        try {
            InputStream original = context.getContentResolver().openInputStream(source);
            if (original == null) throw new IOException("Sicherung konnte nicht geöffnet werden.");
            try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(original))) {
                ZipEntry entry;
                int entries = 0;
                while ((entry = input.getNextEntry()) != null) {
                    if (++entries > MAX_ENTRIES) throw new IOException("Zu viele ZIP-Einträge.");
                    if (entry.isDirectory() || !seenEntries.add(entry.getName()))
                        throw new IOException("Ungültiges Archiv.");
                    String name = entry.getName();
                    if ("parking.json".equals(name)) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        byte[] block = new byte[8192];
                        int len;
                        while ((len = input.read(block)) != -1) {
                            total += len;
                            if (output.size() + len > MAX_JSON || total > MAX_ARCHIVE_UNCOMPRESSED)
                                throw new IOException("Sicherung überschreitet Größenlimit.");
                            output.write(block, 0, len);
                        }
                        rawManifest = output.toString(StandardCharsets.UTF_8.name());
                    } else if (name.matches("photos/[1-9][0-9]{0,17}\\.(jpg|png|webp)")) {
                        File dest = new File(stage, name.substring("photos/".length()));
                        byte[] chunk = new byte[8192];
                        long photoSize = 0;
                        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                            int n;
                            while ((n = input.read(chunk)) != -1) {
                                photoSize += n;
                                total += n;
                                if (photoSize > MAX_IMAGE_BYTES || total > MAX_ARCHIVE_UNCOMPRESSED)
                                    throw new IOException("Sicherung überschreitet Größenlimit.");
                                out.write(chunk, 0, n);
                            }
                        }
                        photos.put(name, dest);
                    } else {
                        throw new IOException("Unbekannter ZIP-Eintrag: " + name);
                    }
                    input.closeEntry();
                }
            }
            if (rawManifest == null) throw new IOException("parking.json fehlt im Archiv.");
            JSONObject manifest = new JSONObject(rawManifest);
            if (!FORMAT.equals(manifest.optString("format")) || manifest.optInt("version") != 1)
                throw new IOException("Sicherungsformat oder Version nicht unterstützt.");
            JSONArray items = manifest.getJSONArray("records");
            if (items.length() > MAX_RECORDS) throw new IOException("Zu viele Parkplätze.");
            List<Record> imported = new ArrayList<>();
            Set<Long> identifiers = new HashSet<>();
            Set<String> usedPhotos = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject row = items.getJSONObject(i);
                Record r = new Record();
                r.originalId = row.getLong("id");
                r.latitude = row.getDouble("latitude");
                r.longitude = row.getDouble("longitude");
                r.parkedAt = row.getLong("parkedAt");
                r.note = row.getString("note");
                r.source = row.getString("source");
                r.floor = row.optString("floor", "");
                r.space = row.optString("space", "");
                r.active = row.optBoolean("active", false);
                r.photoEntry = row.optString("photo", "");
                if (r.originalId <= 0 || !identifiers.add(r.originalId)
                        || !ParkingLogic.validCoordinates(r.latitude, r.longitude)
                        || r.parkedAt <= 0 || r.parkedAt > System.currentTimeMillis() + 86_400_000L
                        || r.note.length() > 4000 || r.source.length() > 32
                        || r.floor.length() > 80 || r.space.length() > 80)
                    throw new IOException("Ungültiger Parkplatz im Archiv.");
                if (!r.photoEntry.isEmpty()) {
                    if (!r.photoEntry.matches("photos/" + r.originalId + "\\.(jpg|png|webp)")
                            || !photos.containsKey(r.photoEntry)
                            || !usedPhotos.add(r.photoEntry))
                        throw new IOException("Foto-Verweis ist ungültig.");
                }
                imported.add(r);
            }
            File root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (root == null) throw new IOException("Fotospeicher nicht verfügbar.");
            File dir = new File(root, "RoyalPark");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Fotospeicher nicht verfügbar.");
            List<File> created = new ArrayList<>();
            try {
                for (Record r : imported) {
                    if (!r.photoEntry.isEmpty() && !store.contains(r.latitude, r.longitude, r.parkedAt)) {
                        String suffix = r.photoEntry.substring(r.photoEntry.lastIndexOf('.'));
                        File image = new File(dir, "restored-" + UUID.randomUUID() + suffix);
                        try (InputStream in = new FileInputStream(photos.get(r.photoEntry));
                             OutputStream out = new FileOutputStream(image)) {
                            writePhoto(in, out);
                        }
                        created.add(image);
                        r.photoUri = FileProvider.getUriForFile(context,
                                context.getPackageName() + ".fileprovider", image).toString();
                    }
                }
                int newRows = store.mergeBackup(imported);
                // If existing records matched, associated photo duplicates can be deleted after merge.
                return new Result(newRows, created.size(), 0);
            } catch (Exception ex) {
                for (File file : created) file.delete();
                if (ex instanceof IOException) throw (IOException) ex;
                throw new IOException("Wiederherstellung fehlgeschlagen.", ex);
            }
        } finally {
            File[] children = stage.listFiles();
            if (children != null) for (File item : children) item.delete();
            stage.delete();
        }
    }
}

package app.royalpark.mobile;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.views.MapView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Fixed allowlist of licensed ODbL Mapsforge state extracts.
 * Only user-initiated downloads: NO downloads from tile.openstreetmap.org.
 */
public final class RegionMaps {
    public static final String LICENSE = "© OpenStreetMap-Mitwirkende · ODbL";
    private static final String MIRROR =
            "https://ftp-stud.hs-esslingen.de/Mirrors/download.mapsforge.org/maps/v5/europe/germany/";
    private static final long MAX_BYTES = 650L * 1024L * 1024L;
    private static final String KEY_REGION = "vector_region";
    private static final String KEY_ENABLED = "vector_enabled";

    public static final class Region {
        public final String code, label, size;
        Region(String code, String label, String size) {
            this.code = code; this.label = label; this.size = size;
        }
        public String display() { return label + " · ca. " + size; }
    }

    public static final Region[] AVAILABLE = {
        new Region("bremen", "Bremen / Bremerhaven", "13 MB"),
        new Region("niedersachsen", "Niedersachsen", "350 MB"),
        new Region("hamburg", "Hamburg", "28 MB"),
        new Region("schleswig-holstein", "Schleswig-Holstein", "110 MB"),
        new Region("berlin", "Berlin", "52 MB"),
        new Region("brandenburg", "Brandenburg", "190 MB")
    };

    public interface Progress { void onUpdate(int percent); }
    private RegionMaps() {}

    public static Region find(String id) {
        if (id != null) for (Region region : AVAILABLE)
            if (region.code.equals(id)) return region;
        return null;
    }
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("royalpark", Context.MODE_PRIVATE);
    }
    private static File folder(Context context) {
        return new File(context.getFilesDir(), "regional-maps");
    }
    private static File location(Context context, Region region) {
        return new File(folder(context), region.code + ".map");
    }
    public static boolean downloaded(Context context, Region region) {
        return region != null && location(context, region).length() >= 1024;
    }
    public static Region current(Context context) {
        return find(prefs(context).getString(KEY_REGION, ""));
    }
    public static boolean enabled(Context context) {
        Region region = current(context);
        return prefs(context).getBoolean(KEY_ENABLED, false) && downloaded(context, region);
    }
    public static void enable(Context context, Region region) {
        if (!downloaded(context, region)) throw new IllegalArgumentException("Karte nicht installiert.");
        prefs(context).edit().putString(KEY_REGION, region.code).putBoolean(KEY_ENABLED, true).apply();
    }
    public static void disable(Context context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply();
    }
    public static void delete(Context context, Region region) {
        if (region == null) return;
        Region active = current(context);
        if (active != null && active.code.equals(region.code)) disable(context);
        location(context, region).delete();
    }

    public static void download(Context context, Region region, Progress progress) throws IOException {
        if (region == null) throw new IOException("Ungültige Region.");
        File base = folder(context);
        if (!base.isDirectory() && !base.mkdirs()) throw new IOException("Kartenordner nicht verfügbar.");
        File target = location(context, region);
        File temp = new File(base, region.code + ".download");
        HttpURLConnection connection = null;
        try {
            URL url = new URL(MIRROR + region.code + ".map");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "RoyalPark/0.8 (+https://github.com/TimeLance89/RoyalPark)");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK)
                throw new IOException("Download HTTP " + status + " – später erneut versuchen.");
            long advertised = connection.getContentLengthLong();
            if (advertised > MAX_BYTES) throw new IOException("Kartenpaket überschreitet 650 MB.");
            if (base.getUsableSpace() < Math.max(advertised > 0 ? advertised : 80L * 1024 * 1024, 16L * 1024 * 1024))
                throw new IOException("Nicht genügend freier Speicher.");
            long count = 0, announced = -1;
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] block = new byte[64 * 1024];
                int len;
                while ((len = input.read(block)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Download abgebrochen.");
                    count += len;
                    if (count > MAX_BYTES) throw new IOException("Kartenpaket zu groß.");
                    output.write(block, 0, len);
                    if (advertised > 0) {
                        long percent = Math.min(99, count * 100 / advertised);
                        if (percent != announced) { announced = percent; progress.onUpdate((int)percent); }
                    }
                }
            }
            if (advertised > 0 && count != advertised) throw new IOException("Download unvollständig.");
            validate(temp);
            if (!temp.renameTo(target)) {
                try (InputStream in = new FileInputStream(temp);
                     OutputStream out = new FileOutputStream(target)) {
                    byte[] bytes = new byte[64 * 1024]; int n;
                    while ((n = in.read(bytes)) > 0) out.write(bytes, 0, n);
                }
            }
            progress.onUpdate(100);
        } finally {
            if (connection != null) connection.disconnect();
            temp.delete();
        }
    }

    /** Magic header of a Mapsforge binary OSM .map file, not an HTTP error page. */
    private static void validate(File file) throws IOException {
        byte[] magic = "mapsforge binary OSM".getBytes(StandardCharsets.US_ASCII);
        if (file.length() < 4096) throw new IOException("Kartenpaket unvollständig.");
        byte[] actual = new byte[magic.length];
        try (InputStream stream = new FileInputStream(file)) {
            if (stream.read(actual) != magic.length) throw new IOException("Kartenpaket nicht lesbar.");
        }
        for (int i = 0; i < magic.length; i++)
            if (actual[i] != magic[i]) throw new IOException("Keine gültige Mapsforge-Karte.");
    }

    public static boolean offline(Context context) {
        if (enabled(context)) return true;
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) return true;
        android.net.Network n = cm.getActiveNetwork();
        if (n == null) return true;
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        return c == null || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public static boolean apply(Context context, MapView map) {
        Region region = current(context);
        if (region == null || !downloaded(context, region) || !offline(context)) return false;
        try {
            MapsForgeTileSource.createInstance((Application) context.getApplicationContext());
            MapsForgeTileSource source = MapsForgeTileSource.createFromFiles(
                    new File[]{location(context, region)});
            map.setTileProvider(new MapsForgeTileProvider(new SimpleRegisterReceiver(context), source, null));
            map.setTileSource(source);
            map.setUseDataConnection(false);
            return true;
        } catch (Exception ex) {
            // Bad vector archive must not crash map usage. Existing raster/compass fallback.
            return false;
        }
    }
}

package app.royalpark.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.osmdroid.tileprovider.modules.ArchiveFileFactory;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.views.MapView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** User-imported, licensed offline raster map archives. Never downloads map tiles in bulk. */
public final class OfflineMaps {
    private static final String NAME = "royalpark-offline";
    private static final String PREF_OFFLINE = "offline_maps_enabled";
    private static final String PREF_EXTENSION = "offline_maps_extension";
    private static final String PREF_SOURCE = "offline_maps_source";
    private static final long MAX_BYTES = 1024L * 1024L * 1024L;
    private OfflineMaps() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("royalpark", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return prefs(context).getBoolean(PREF_OFFLINE, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(PREF_OFFLINE, enabled).apply();
    }

    public static boolean hasArchive(Context context) {
        File file = installedFile(context);
        return file != null && file.isFile() && file.length() > 0;
    }

    public static String sourceName(Context context) {
        return prefs(context).getString(PREF_SOURCE, "");
    }

    private static File installedFile(Context context) {
        String extension = prefs(context).getString(PREF_EXTENSION, "");
        if (!isSupportedExtension(extension)) return null;
        return new File(context.getFilesDir(), NAME + "." + extension);
    }

    static boolean isSupportedExtension(String extension) {
        return "mbtiles".equals(extension) || "sqlite".equals(extension) || "zip".equals(extension);
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        String name = filename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return uri.getLastPathSegment();
    }

    public static String importArchive(Context context, Uri uri) throws IOException {
        String ext = extension(displayName(context, uri));
        if (!isSupportedExtension(ext))
            throw new IOException("Bitte .mbtiles, .sqlite oder .zip wählen.");
        File temp = new File(context.getFilesDir(), NAME + "-pending." + ext);
        File dest = new File(context.getFilesDir(), NAME + "." + ext);
        try {
            InputStream input = context.getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("Datei lässt sich nicht öffnen.");
            try (InputStream in = new BufferedInputStream(input);
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] block = new byte[65536];
                int len;
                long size = 0;
                while ((len = in.read(block)) != -1) {
                    size += len;
                    if (size > MAX_BYTES) throw new IOException("Maximal 1 GB je Kartenarchiv.");
                    out.write(block, 0, len);
                }
            }
            validateHeader(temp, ext);
            String source = inspect(temp);
            if (source == null || source.isEmpty()) throw new IOException("Kartenarchiv enthält keine verwendbare Raster-Kartenquelle.");
            if (!temp.renameTo(dest)) {
                try (InputStream in = new FileInputStream(temp);
                     OutputStream out = new FileOutputStream(dest)) {
                    byte[] block = new byte[65536];
                    int n;
                    while ((n = in.read(block)) != -1) out.write(block, 0, n);
                }
            }
            File previous = installedFile(context);
            prefs(context).edit()
                    .putString(PREF_EXTENSION, ext)
                    .putString(PREF_SOURCE, source)
                    .putBoolean(PREF_OFFLINE, true)
                    .apply();
            if (previous != null && !previous.equals(dest)) previous.delete();
            return source;
        } finally {
            temp.delete();
        }
    }

    private static void validateHeader(File file, String ext) throws IOException {
        if (file.length() < 64) throw new IOException("Kartenarchiv ist leer oder beschädigt.");
        byte[] bytes = new byte[16];
        try (InputStream in = new FileInputStream(file)) {
            if (in.read(bytes) != 16) throw new IOException("Datei nicht lesbar.");
        }
        if ("zip".equals(ext)) {
            if (bytes[0] != 'P' || bytes[1] != 'K')
                throw new IOException("Ungültige ZIP-Karten.");
        } else if (!"SQLite format 3\0".equals(new String(bytes, StandardCharsets.US_ASCII)))
            throw new IOException("Keine SQLite-Kartendatenbank.");
    }

    private static String inspect(File file) throws IOException {
        IArchiveFile archive = null;
        try {
            archive = ArchiveFileFactory.getArchiveFile(file);
            if (archive == null) throw new IOException("Kartenformat nicht unterstützt.");
            Set<String> sources = archive.getTileSources();
            if (sources == null || sources.isEmpty()) throw new IOException("Keine Rasterkarten in diesem Archiv.");
            for (String source : sources) {
                if (source != null && !source.isBlank()) return source;
            }
            throw new IOException("Kartenquelle ohne Namen.");
        } catch (RuntimeException error) {
            throw new IOException("Kartenarchiv nicht lesbar.", error);
        } finally {
            if (archive != null) archive.close();
        }
    }

    public static void remove(Context context) {
        File file = installedFile(context);
        if (file != null) file.delete();
        prefs(context).edit().remove(PREF_EXTENSION).remove(PREF_SOURCE)
                .putBoolean(PREF_OFFLINE, false).apply();
    }

    private static boolean connected(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null) return false;
        android.net.Network network = connectivity.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * Configures online raster tiles or a STRICT archive-only provider.
     * An explicit offline switch never requests the Internet for tiles.
     * When offline due to network loss, a legal imported archive is used automatically.
     */
    public static boolean apply(Context context, MapView map) {
        if (RegionMaps.apply(context, map)) return true;
        boolean offline = enabled(context) || !connected(context);
        File archiveFile = installedFile(context);
        if (offline && archiveFile != null && archiveFile.length() > 0) {
            OfflineTileProvider provider = new OfflineTileProvider(
                    new SimpleRegisterReceiver(context), new File[]{archiveFile});
            if (provider.getArchives().length > 0) {
                map.setTileProvider(provider);
                String source = sourceName(context);
                if (source.isEmpty()) {
                    Set<String> available = provider.getArchives()[0].getTileSources();
                    if (available != null && !available.isEmpty()) source = available.iterator().next();
                }
                if (!source.isEmpty()) map.setTileSource(FileBasedTileSource.getSource(source));
                map.setUseDataConnection(false);
                return true;
            }
            provider.detach();
        }
        // Without an archive, osmdroid can still show its previously viewed, unexpired tiles.
        if (offline) map.setUseDataConnection(false);
        return offline;
    }
}

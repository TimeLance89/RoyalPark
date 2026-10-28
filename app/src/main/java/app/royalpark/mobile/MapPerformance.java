package app.royalpark.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

/**
 * Performance settings shared by both the parking map and walking navigation.
 * Keep the cache modest: 64 256px ARGB tile bitmaps are at most ~16MiB.
 * Online tiles follow osmdroid's normal on-demand caching; no OSM prefetch.
 */
public final class MapPerformance {
    private MapPerformance() {}

    public static void configure(Context context, SharedPreferences preferences) {
        IConfigurationProvider config = Configuration.getInstance();
        config.load(context, preferences);
        config.setUserAgentValue("RoyalPark/1.3 (app.royalpark.mobile)");
        // osmdroid defaults to 9 in-memory tiles, often less than a visible screen.
        config.setCacheMapTileCount((short) 64);
        // Keep only one extra ring for adjacent tiles during map panning.
        config.setCacheMapTileOvershoot((short) 1);
        config.setAnimationSpeedDefault(380);
        config.setAnimationSpeedShort(220);
        config.setMapViewHardwareAccelerated(true);
        // Do not increase public OpenStreetMap downloader concurrency.
    }

    public static boolean shouldZoom(double current, double target) {
        return !Double.isFinite(current) || !Double.isFinite(target)
                || Math.abs(current - target) >= 0.15;
    }

    /** Key excludes GPS location so sensor updates cannot reconstruct the map. */
    public static String renderKey(ParkingSpot p, String provider, boolean precise) {
        String mode = provider == null ? "" : provider;
        if (p == null) return "none|" + mode + "|" + precise;
        return p.id + "|" + p.latitude + "|" + p.longitude + "|" + p.note + "|"
                + p.parkedAt + "|" + p.source + "|" + p.timerAt + "|"
                + p.photoUri + "|" + p.floor + "|" + p.space + "|" + mode
                + "|" + precise;
    }
}

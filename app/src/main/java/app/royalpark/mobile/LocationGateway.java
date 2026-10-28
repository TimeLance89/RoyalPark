package app.royalpark.mobile;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches a fresh fix without quietly reusing a potentially obsolete last-known position. */
public final class LocationGateway {
    public interface Callback { void onResult(Location location, String error); }
    private LocationGateway() {}

    public static boolean hasPrecisePermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Best recent fix from Android's provider cache; it is never written as a parking location
     * and is only suitable for centering the map. Invalid or inaccurate fixes are rejected.
     */
    public static Location recent(Context context, long maxAgeMillis, float maxAccuracyMeters) {
        if (!hasPermission(context)) return null;
        LocationManager manager = context.getSystemService(LocationManager.class);
        if (manager == null) return null;
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        Location candidate = null;
        String[] providers = fine
                ? new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}
                : new String[]{LocationManager.NETWORK_PROVIDER};
        long now = SystemClock.elapsedRealtimeNanos();
        for (String provider : providers) {
            try {
                Location location = manager.getLastKnownLocation(provider);
                if (location == null || !location.hasAccuracy()
                        || location.getAccuracy() > maxAccuracyMeters
                        || !Double.isFinite(location.getLatitude())
                        || !Double.isFinite(location.getLongitude())) continue;
                long age = (now - location.getElapsedRealtimeNanos()) / 1_000_000L;
                if (age < 0 || age > maxAgeMillis) continue;
                if (candidate == null || location.getAccuracy() < candidate.getAccuracy())
                    candidate = location;
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        return candidate;
    }

    public static void once(Context context, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        if (!hasPermission(context)) {
            main.post(() -> callback.onResult(null, "Standortberechtigung fehlt"));
            return;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            main.post(() -> callback.onResult(null, "Standortdienst nicht verfügbar"));
            return;
        }
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean gps;
        boolean network;
        try {
            gps = fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            network = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            gps = false;
            network = false;
        }
        if (!gps && !network) {
            main.post(() -> callback.onResult(null, "Bitte Standortdienste aktivieren"));
            return;
        }
        String primary = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
        AtomicBoolean completed = new AtomicBoolean(false);
        CancellationSignal gpsSignal = new CancellationSignal();
        CancellationSignal networkSignal = new CancellationSignal();
        LocationListener[] legacy = new LocationListener[2];
        java.util.function.Consumer<Location> result = location -> {
            if (location != null && completed.compareAndSet(false, true)) {
                callback.onResult(location, null);
                gpsSignal.cancel();
                networkSignal.cancel();
                for (LocationListener listener : legacy) if (listener != null) manager.removeUpdates(listener);
            }
        };
        try {
            request(manager, context, primary, gpsSignal, result, legacy, 0);
            // Indoor GPS may be slow; request a fresh network fix after a short grace period.
            if (gps && network) main.postDelayed(() -> {
                if (!completed.get()) {
                    try {
                        request(manager, context, LocationManager.NETWORK_PROVIDER,
                                networkSignal, result, legacy, 1);
                    } catch (Exception ignored) { }
                }
            }, 1_200L);
            main.postDelayed(() -> {
                if (completed.compareAndSet(false, true)) {
                    gpsSignal.cancel();
                    networkSignal.cancel();
                    for (LocationListener listener : legacy) if (listener != null) {
                        try { manager.removeUpdates(listener); } catch (Exception ignored) { }
                    }
                    callback.onResult(null, "Noch kein genauer Standort gefunden. Unter freiem Himmel erneut versuchen.");
                }
            }, 12_000L);
        } catch (SecurityException ex) {
            if (completed.compareAndSet(false, true)) callback.onResult(null, "Keine Standortberechtigung");
        } catch (Exception ex) {
            if (completed.compareAndSet(false, true)) callback.onResult(null, "Ortung fehlgeschlagen");
        }
    }

    private static void request(LocationManager manager, Context context, String provider,
                                CancellationSignal cancellation, java.util.function.Consumer<Location> result,
                                LocationListener[] legacy, int index) {
        if (Build.VERSION.SDK_INT >= 30) {
            manager.getCurrentLocation(provider, cancellation, context.getMainExecutor(), result::accept);
        } else {
            legacy[index] = new LocationListener() {
                @Override public void onLocationChanged(Location location) { result.accept(location); }
            };
            manager.requestSingleUpdate(provider, legacy[index], Looper.getMainLooper());
        }
    }
}

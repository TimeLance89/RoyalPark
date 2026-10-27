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

import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches a fresh fix without quietly reusing a potentially obsolete last-known position. */
public final class LocationGateway {
    public interface Callback { void onResult(Location location, String error); }
    private LocationGateway() {}

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
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
            // An indoor location may have no GPS fix. Fall back to fresh network positioning.
            if (gps && network) main.postDelayed(() -> {
                if (!completed.get()) {
                    try {
                        request(manager, context, LocationManager.NETWORK_PROVIDER,
                                networkSignal, result, legacy, 1);
                    } catch (Exception ignored) { }
                }
            }, 8_000L);
            main.postDelayed(() -> {
                if (completed.compareAndSet(false, true)) {
                    gpsSignal.cancel();
                    networkSignal.cancel();
                    for (LocationListener listener : legacy) if (listener != null) {
                        try { manager.removeUpdates(listener); } catch (Exception ignored) { }
                    }
                    callback.onResult(null, "Zeitüberschreitung bei der Ortung. Bitte erneut versuchen.");
                }
            }, 20_000L);
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

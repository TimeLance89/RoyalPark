package app.royalpark.mobile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import btools.routingapp.IBRouterService;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** Offline foot route calculated by the separate BRouter app through its public AIDL service.
 * Requires the app and downloaded .rd5 segments. No route calculation hits the Internet.
 */
public final class OfflineWalkingRouter {
    private static final int MAX_XML = 3_000_000;
    private static final int MAX_POINTS = 15000;
    public interface Callback {
        void onResult(WalkingRouteService.Route route, String error);
    }
    private OfflineWalkingRouter() {}

    public static boolean isInstalled(Context context) {
        try {
            return context.getPackageManager().getLaunchIntentForPackage("btools.routingapp") != null;
        } catch (Exception error) { return false; }
    }

    public static boolean calculate(Activity activity, ExecutorService worker,
            double startLat, double startLon, double destinationLat, double destinationLon,
            Callback callback) {
        if (!ParkingLogic.validCoordinates(startLat, startLon) ||
                !ParkingLogic.validCoordinates(destinationLat, destinationLon)) {
            callback.onResult(null, "Standort ungültig.");
            return false;
        }
        if (!isInstalled(activity)) {
            callback.onResult(null, "BRouter nicht installiert. In Einstellungen einrichten.");
            return false;
        }
        final ServiceConnection[] connection = new ServiceConnection[1];
        connection[0] = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                IBRouterService router = IBRouterService.Stub.asInterface(binder);
                worker.execute(() -> {
                    WalkingRouteService.Route result = null;
                    String error = null;
                    try {
                        Bundle request = new Bundle();
                        request.putDoubleArray("lats", new double[]{startLat, destinationLat});
                        request.putDoubleArray("lons", new double[]{startLon, destinationLon});
                        request.putString("v", "foot");
                        request.putString("fast", "0");
                        request.putString("trackFormat", "gpx");
                        request.putString("maxRunningTime", "30");
                        String response = router.getTrackFromParams(request);
                        if (response == null || !response.trim().startsWith("<"))
                            throw new IllegalStateException(response == null
                                ? "BRouter hat keine Route geliefert. Routendaten (.rd5) fehlen möglicherweise."
                                : response.substring(0, Math.min(180, response.length())));
                        result = parseGpx(response);
                    } catch (Exception ex) {
                        error = ex.getMessage() == null ? "Offline-Route fehlgeschlagen" : ex.getMessage();
                    }
                    WalkingRouteService.Route route = result;
                    String reason = error;
                    activity.runOnUiThread(() -> {
                        try { activity.unbindService(connection[0]); } catch (Exception ignored) {}
                        if (!activity.isFinishing() && !activity.isDestroyed())
                            callback.onResult(route, reason);
                    });
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                if (!activity.isFinishing()) callback.onResult(null, "BRouter-Verbindung getrennt.");
            }
        };
        Intent intent = new Intent().setClassName("btools.routingapp", "btools.routingapp.BRouterService");
        try {
            if (!activity.bindService(intent, connection[0], Context.BIND_AUTO_CREATE)) {
                callback.onResult(null, "BRouter-Dienst nicht erreichbar.");
                return false;
            }
            return true;
        } catch (Exception error) {
            callback.onResult(null, "BRouter-Dienst konnte nicht gestartet werden.");
            return false;
        }
    }

    static WalkingRouteService.Route parseGpx(String xml) throws Exception {
        if (xml == null || xml.length() > MAX_XML || !xml.trim().startsWith("<"))
            throw new IllegalArgumentException("Ungültige GPX-Route.");
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new StringReader(xml));
        List<RouteGeometry.Point> path = new ArrayList<>();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event != XmlPullParser.START_TAG || !"trkpt".equals(parser.getName())) continue;
            if (path.size() >= MAX_POINTS) throw new IllegalArgumentException("GPX-Route zu lang.");
            double lat = Double.parseDouble(parser.getAttributeValue(null, "lat"));
            double lon = Double.parseDouble(parser.getAttributeValue(null, "lon"));
            path.add(new RouteGeometry.Point(lat, lon));
        }
        if (path.size() < 2) throw new IllegalArgumentException("BRouter lieferte keine Fußwegkoordinaten.");
        double meters = 0;
        for (int i=1; i<path.size(); i++) {
            RouteGeometry.Point a = path.get(i-1), b = path.get(i);
            meters += NavigationMath.distanceMeters(a.lat,a.lon,b.lat,b.lon);
        }
        // Estimated walking time only. BRouter GPX does not include turn-by-turn text.
        return new WalkingRouteService.Route(Collections.unmodifiableList(path),
                Collections.emptyList(), meters/1000, meters / 1.30);
    }
}

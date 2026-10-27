package app.royalpark.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.Arrays;
import java.util.Locale;

/** Live bearing guidance. The line is straight-line guidance, NOT a calculated pedestrian route. */
public final class NavigateActivity extends Activity implements LocationListener, SensorEventListener {
    private static final int INK = Color.rgb(239, 248, 250);
    private static final int BG = Color.rgb(16, 21, 30);
    private static final int GREEN = Color.rgb(86, 215, 193);
    private static final int MUTED = Color.rgb(160, 181, 195);
    private MapView map;
    private Polyline directionLine;
    private Marker meMarker;
    private LocationManager locationManager;
    private SensorManager sensors;
    private Sensor rotationSensor;
    private TextView arrow;
    private TextView distanceLabel;
    private TextView infoLabel;
    private double targetLat;
    private double targetLon;
    private float heading;
    private Location current;
    private boolean cameraCentered = false;

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }

    private TextView text(String str, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(str);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        targetLat = getIntent().getDoubleExtra("latitude", Double.NaN);
        targetLon = getIntent().getDoubleExtra("longitude", Double.NaN);
        if (!ParkingLogic.validCoordinates(targetLat, targetLon)) { finish(); return; }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("RoyalPark/0.2 (app.royalpark.mobile)");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(12), dp(18), dp(12));
        TextView back = text("‹  Zurück", 16, GREEN, true);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView headingTitle = text("  Zum Parkplatz", 19, INK, true);
        top.addView(headingTitle, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top);

        arrow = text("▲", 66, GREEN, true);
        root.addView(arrow, new LinearLayout.LayoutParams(-1, dp(90)));
        distanceLabel = text("Position wird ermittelt …", 28, INK, true);
        root.addView(distanceLabel);
        infoLabel = text("Pfeil zeigt die Luftlinienrichtung – keine Straßenroute.", 13, MUTED, false);
        infoLabel.setPadding(dp(22), dp(10), dp(22), dp(18));
        root.addView(infoLabel);

        FrameLayout mapFrame = new FrameLayout(this);
        map = new MapView(this);
        map.setTileSource(new XYTileSource("OSM", 0, 19, 256, ".png", new String[]{"https://tile.openstreetmap.org/"}));
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.0);
        map.getController().setCenter(new GeoPoint(targetLat, targetLon));
        Marker car = new Marker(map);
        car.setPosition(new GeoPoint(targetLat, targetLon));
        car.setTitle("Mein Auto");
        car.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(car);
        directionLine = new Polyline(map);
        directionLine.setColor(GREEN);
        directionLine.setWidth(dp(4));
        map.getOverlays().add(directionLine);
        meMarker = new Marker(map);
        meMarker.setTitle("Mein Standort");
        meMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(meMarker);
        mapFrame.addView(map, new FrameLayout.LayoutParams(-1, -1));
        TextView credit = text("© OpenStreetMap-Mitwirkende", 11, BG, false);
        credit.setPadding(dp(6), dp(3), dp(6), dp(3));
        credit.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams credits = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.RIGHT);
        credits.setMargins(0, 0, dp(6), dp(6));
        mapFrame.addView(credit, credits);
        credit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.openstreetmap.org/copyright"))));
        root.addView(mapFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView external = text("↗  Straßenroute in Karten-App öffnen", 16, BG, true);
        external.setPadding(dp(12), dp(16), dp(12), dp(16));
        external.setBackgroundColor(GREEN);
        external.setOnClickListener(v -> {
            String url = String.format(Locale.US,
                    "https://www.google.com/maps/dir/?api=1&destination=%.6f%%2C%.6f&travelmode=walking",
                    targetLat, targetLon);
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception ex) { Toast.makeText(this, "Keine Karten-App gefunden", Toast.LENGTH_LONG).show(); }
        });
        root.addView(external);
        locationManager = getSystemService(LocationManager.class);
        sensors = getSystemService(SensorManager.class);
        if (sensors != null) rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    @Override protected void onResume() {
        super.onResume();
        if (map == null) return;
        map.onResume();
        if (sensors != null && rotationSensor != null)
            sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        if (locationManager == null || !LocationGateway.hasPermission(this)) {
            infoLabel.setText("Für Live-Navigation Standortzugriff in den Einstellungen erlauben.");
            return;
        }
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 1f, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 5f, this);
            LocationGateway.once(this, (location, error) -> {
                if (location != null) onLocationChanged(location);
            });
        } catch (SecurityException ignored) {
            infoLabel.setText("Standortberechtigung fehlt.");
        }
    }

    @Override public void onLocationChanged(Location location) {
        current = location;
        updateGuidance();
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        float[] matrix = new float[9];
        float[] orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, e.values);
        SensorManager.getOrientation(matrix, orientation);
        heading = (float) Math.toDegrees(orientation[0]);
        if (current != null) {
            GeomagneticField magnetic = new GeomagneticField(
                    (float) current.getLatitude(), (float) current.getLongitude(),
                    (float) current.getAltitude(), System.currentTimeMillis());
            heading += magnetic.getDeclination();
        }
        updateArrowOnly();
    }

    private void updateArrowOnly() {
        if (current == null || arrow == null) return;
        float bearing = NavigationMath.bearingDegrees(
                current.getLatitude(), current.getLongitude(), targetLat, targetLon);
        arrow.setRotation(NavigationMath.relativeDirection(bearing, heading));
    }

    private void updateGuidance() {
        if (current == null || map == null) return;
        double distance = NavigationMath.distanceMeters(
                current.getLatitude(), current.getLongitude(), targetLat, targetLon);
        distanceLabel.setText(NavigationMath.formatDistance(distance));
        infoLabel.setText(distance < 20 ? "Du bist am Parkplatz angekommen."
                : "Luftlinie zum Auto · GPS ±" + Math.round(current.getAccuracy()) + " m");
        updateArrowOnly();
        GeoPoint me = new GeoPoint(current.getLatitude(), current.getLongitude());
        meMarker.setPosition(me);
        directionLine.setPoints(Arrays.asList(me, new GeoPoint(targetLat, targetLon)));
        if (!cameraCentered) {
            cameraCentered = true;
            map.zoomToBoundingBox(new org.osmdroid.util.BoundingBox(
                    Math.max(targetLat, me.getLatitude()) + 0.001,
                    Math.max(targetLon, me.getLongitude()) + 0.001,
                    Math.min(targetLat, me.getLatitude()) - 0.001,
                    Math.min(targetLon, me.getLongitude()) - 0.001), true, dp(48));
        }
        map.invalidate();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override protected void onPause() {
        if (locationManager != null) locationManager.removeUpdates(this);
        if (sensors != null) sensors.unregisterListener(this);
        if (map != null) map.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (map != null) { map.onDetach(); map = null; }
        super.onDestroy();
    }
}

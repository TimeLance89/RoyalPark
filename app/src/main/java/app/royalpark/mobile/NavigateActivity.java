package app.royalpark.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Map navigation with optional user-triggered pedestrian route from Valhalla.
 * No coordinates are sent to the routing service without tapping "Berechnen" and confirming.
 * The compass + straight line remain available when offline.
 */
public final class NavigateActivity extends Activity
        implements LocationListener, SensorEventListener {
    private static final int BG = Color.rgb(247, 249, 251);
    private static final int INK = Color.rgb(30, 48, 62);
    private static final int TEAL = Color.rgb(29, 146, 171);
    private static final int MUTED = Color.rgb(104, 122, 137);
    private static final int GRAY = Color.rgb(140, 153, 167);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private MapView map;
    private Marker myMarker;
    private Polyline pointerLine;
    private Polyline routeLine;
    private LocationManager locations;
    private SensorManager sensors;
    private Sensor rotationSensor;
    private TextView arrow;
    private TextView distanceText;
    private TextView stepText;
    private TextView routeDetails;
    private TextView routeButton;
    private TextView speechButton;
    private WalkingRouteService.Route route;
    private double targetLat;
    private double targetLon;
    private float heading;
    private Location current;
    private boolean cameraCentered;
    private boolean loading;
    private boolean voiceEnabled;
    private boolean speechReady;
    private TextToSpeech speaker;
    private int routeProgressIndex;
    private int lastSpokenStep = -1;
    private long lastRequestMs;

    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        result.setGravity(Gravity.CENTER);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return result;
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private TextView action(String value, boolean primary) {
        TextView view = label(value, 14, primary ? Color.WHITE : TEAL, true);
        view.setBackgroundColor(primary ? TEAL : Color.WHITE);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        return view;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        targetLat = getIntent().getDoubleExtra("latitude", Double.NaN);
        targetLon = getIntent().getDoubleExtra("longitude", Double.NaN);
        if (!ParkingLogic.validCoordinates(targetLat, targetLon)) { finish(); return; }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("RoyalPark/0.5 (app.royalpark.mobile)");

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(BG);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = label("‹  Zurück", 16, TEAL, true);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(98), dp(44)));
        TextView title = label("Zum Parkplatz", 20, INK, true);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(top);

        LinearLayout compass = new LinearLayout(this);
        compass.setGravity(Gravity.CENTER_VERTICAL);
        compass.setPadding(dp(12), dp(4), dp(12), dp(4));
        arrow = label("▲", 59, TEAL, true);
        compass.addView(arrow, new LinearLayout.LayoutParams(dp(95), dp(90)));
        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        distanceText = label("Standort wird ermittelt …", 22, INK, true);
        detail.addView(distanceText);
        routeDetails = label("Kompass zeigt die Luftlinie.", 12, MUTED, false);
        detail.addView(routeDetails);
        compass.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(compass);

        stepText = label("Fußwegroute optional berechnen – oder dem Richtungspfeil folgen.", 14, INK, false);
        stepText.setPadding(dp(18), dp(7), dp(18), dp(12));
        root.addView(stepText);

        FrameLayout mapFrame = new FrameLayout(this);
        map = new MapView(this);
        map.setTileSource(new XYTileSource("OSM", 0, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.org/"}));
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.5);
        map.getController().setCenter(new GeoPoint(targetLat, targetLon));
        Marker carMarker = new Marker(map);
        carMarker.setTitle("Mein Auto");
        carMarker.setPosition(new GeoPoint(targetLat, targetLon));
        carMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(carMarker);
        pointerLine = new Polyline(map);
        pointerLine.setColor(GRAY);
        pointerLine.setWidth(dp(3));
        map.getOverlays().add(pointerLine);
        routeLine = new Polyline(map);
        routeLine.setColor(TEAL);
        routeLine.setWidth(dp(6));
        map.getOverlays().add(routeLine);
        myMarker = new Marker(map);
        myMarker.setTitle("Dein Standort");
        myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(myMarker);
        mapFrame.addView(map, new FrameLayout.LayoutParams(-1, -1));
        TextView credit = label("© OpenStreetMap-Mitwirkende", 10, INK, false);
        credit.setPadding(dp(6), dp(3), dp(6), dp(3));
        credit.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams credits = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.RIGHT);
        credits.setMargins(0, 0, dp(5), dp(5));
        mapFrame.addView(credit, credits);
        credit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.openstreetmap.org/copyright"))));
        root.addView(mapFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        routeButton = action("⌁  Fußwegroute berechnen", true);
        routeButton.setOnClickListener(v -> askBeforeRouting());
        root.addView(routeButton, new LinearLayout.LayoutParams(-1, dp(49)));

        LinearLayout commands = new LinearLayout(this);
        speechButton = action("🔇  Ansagen: Aus", false);
        speechButton.setOnClickListener(v -> toggleSpeech());
        commands.addView(speechButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView external = action("↗  Externe Karte", false);
        external.setOnClickListener(v -> openExternal());
        commands.addView(external, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(commands);

        locations = getSystemService(LocationManager.class);
        sensors = getSystemService(SensorManager.class);
        if (sensors != null) rotationSensor =
                sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    private void askBeforeRouting() {
        if (loading) return;
        if (current == null) {
            toast("Zuerst muss dein Standort bestimmt werden.");
            return;
        }
        if (System.currentTimeMillis() - lastRequestMs < 30_000L) {
            toast("Bitte vor der nächsten Routenberechnung etwas warten.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Fußwegroute online berechnen?")
                .setMessage("Die aktuelle GPS-Position und die Position deines Autos werden " +
                        "einmalig an den öffentlichen FOSSGIS-Valhalla-Routingdienst übertragen. " +
                        "Der Dienst hat Nutzungslimits. Ohne Zustimmung bleibt die Kompassnavigation verfügbar.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Route berechnen", (d, which) -> loadRoute())
                .show();
    }

    private void loadRoute() {
        if (current == null || loading) return;
        final double startLat = current.getLatitude();
        final double startLon = current.getLongitude();
        lastRequestMs = System.currentTimeMillis();
        loading = true;
        routeButton.setText("Route wird berechnet …");
        stepText.setText("Online-Fußwegroute wird angefragt …");
        worker.execute(() -> {
            WalkingRouteService.Route response = null;
            String error = null;
            try {
                response = WalkingRouteService.calculate(startLat, startLon, targetLat, targetLon);
            } catch (Exception exception) {
                error = exception.getMessage();
            }
            final WalkingRouteService.Route result = response;
            final String failure = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                routeButton.setText("⟳  Fußwegroute aktualisieren");
                if (result == null) {
                    stepText.setText("Routing nicht verfügbar – Kompassnavigation bleibt aktiv.");
                    routeDetails.setText(failure == null ? "Verbindung fehlgeschlagen" : failure);
                    return;
                }
                route = result;
                routeProgressIndex = 0;
                lastSpokenStep = -1;
                List<GeoPoint> path = new ArrayList<>();
                for (RouteGeometry.Point p : result.coordinates)
                    path.add(new GeoPoint(p.lat, p.lon));
                routeLine.setPoints(path);
                pointerLine.setPoints(new ArrayList<>());
                routeDetails.setText(String.format(Locale.GERMANY,
                        "Fußweg: %.1f km · ca. %d Min · FOSSGIS/Valhalla",
                        result.lengthKm, Math.round(result.durationSeconds / 60)));
                if (!path.isEmpty()) {
                    double north = -90, south = 90, west = 180, east = -180;
                    for (GeoPoint p : path) {
                        north = Math.max(north, p.getLatitude());
                        south = Math.min(south, p.getLatitude());
                        east = Math.max(east, p.getLongitude());
                        west = Math.min(west, p.getLongitude());
                    }
                    map.zoomToBoundingBox(new BoundingBox(
                            Math.min(90, north + 0.001),
                            Math.min(180, east + 0.001),
                            Math.max(-90, south - 0.001),
                            Math.max(-180, west - 0.001)), true, dp(40));
                    cameraCentered = true;
                }
                updateGuidance();
                map.invalidate();
            });
        });
    }

    private void toggleSpeech() {
        voiceEnabled = !voiceEnabled;
        speechButton.setText(voiceEnabled ? "🔊  Ansagen: An" : "🔇  Ansagen: Aus");
        if (!voiceEnabled) {
            if (speaker != null) speaker.stop();
            return;
        }
        if (speaker == null) {
            speaker = new TextToSpeech(this, status -> {
                speechReady = status == TextToSpeech.SUCCESS
                        && speaker != null
                        && speaker.setLanguage(Locale.GERMAN) >= TextToSpeech.LANG_AVAILABLE;
                if (!speechReady) toast("Deutsche Sprachausgabe nicht verfügbar.");
                else speak("Ansagen eingeschaltet.");
            });
        } else if (speechReady) {
            speak("Ansagen eingeschaltet.");
        }
    }

    private void speak(String phrase) {
        if (voiceEnabled && speechReady && speaker != null)
            speaker.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "royalpark-step");
    }

    private void openExternal() {
        String url = String.format(Locale.US,
                "https://www.google.com/maps/dir/?api=1&destination=%.6f%%2C%.6f&travelmode=walking",
                targetLat, targetLon);
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ex) { toast("Keine Karten-App verfügbar."); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (map == null) return;
        map.onResume();
        if (sensors != null && rotationSensor != null)
            sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        if (locations == null || !LocationGateway.hasPermission(this)) {
            stepText.setText("Für die Navigation Standortberechtigung in den Einstellungen erlauben.");
            return;
        }
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1500, 1f, this);
            }
            if (locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 5f, this);
            LocationGateway.once(this, (fix, error) -> {
                if (fix != null && map != null) onLocationChanged(fix);
            });
        } catch (SecurityException ex) {
            stepText.setText("Standortberechtigung fehlt.");
        }
    }

    @Override public void onLocationChanged(Location location) {
        current = location;
        updateGuidance();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        float[] matrix = new float[9], orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);
        heading = (float) Math.toDegrees(orientation[0]);
        if (current != null) {
            GeomagneticField magnetic = new GeomagneticField(
                    (float) current.getLatitude(), (float) current.getLongitude(),
                    (float) current.getAltitude(), System.currentTimeMillis());
            heading += magnetic.getDeclination();
        }
        updateArrow();
    }

    private void updateArrow() {
        if (current == null || arrow == null) return;
        float bearing = NavigationMath.bearingDegrees(current.getLatitude(),
                current.getLongitude(), targetLat, targetLon);
        arrow.setRotation(NavigationMath.relativeDirection(bearing, heading));
    }

    private void updateGuidance() {
        if (current == null || map == null) return;
        double distance = NavigationMath.distanceMeters(current.getLatitude(),
                current.getLongitude(), targetLat, targetLon);
        distanceText.setText(NavigationMath.formatDistance(distance));
        updateArrow();
        GeoPoint me = new GeoPoint(current.getLatitude(), current.getLongitude());
        myMarker.setPosition(me);

        if (route == null) {
            pointerLine.setPoints(Arrays.asList(me, new GeoPoint(targetLat, targetLon)));
            if (distance < 20) stepText.setText("Du bist beim Auto angekommen.");
            else stepText.setText("Kompass-Richtung: Luftlinie (keine Straßenroute).");
            if (!cameraCentered) {
                cameraCentered = true;
                map.zoomToBoundingBox(new BoundingBox(
                        Math.max(targetLat, me.getLatitude()) + 0.001,
                        Math.max(targetLon, me.getLongitude()) + 0.001,
                        Math.min(targetLat, me.getLatitude()) - 0.001,
                        Math.min(targetLon, me.getLongitude()) - 0.001), true, dp(40));
            }
        } else {
            updateRouteInstruction();
        }
        map.invalidate();
    }

    private void updateRouteInstruction() {
        if (route == null || current == null) return;
        int nearest = RouteGeometry.nearestIndex(route.coordinates,
                current.getLatitude(), current.getLongitude());
        RouteGeometry.Point nearPoint = route.coordinates.get(nearest);
        double offRoute = NavigationMath.distanceMeters(current.getLatitude(), current.getLongitude(),
                nearPoint.lat, nearPoint.lon);
        if (offRoute > 110) {
            stepText.setText("Du bist von der Route abgewichen. Route manuell aktualisieren.");
            return;
        }
        routeProgressIndex = Math.max(routeProgressIndex, nearest);
        int stepNumber = Math.max(0, route.steps.size() - 1);
        for (int i = 0; i < route.steps.size(); i++) {
            if (route.steps.get(i).shapeIndex > routeProgressIndex + 1) {
                stepNumber = i;
                break;
            }
        }
        if (route.steps.isEmpty()) {
            stepText.setText("Fußweg eingezeichnet. Folge der türkisen Route.");
            return;
        }
        WalkingRouteService.Step step = route.steps.get(stepNumber);
        stepText.setText(step.instruction);
        RouteGeometry.Point stepPoint = route.coordinates.get(step.shapeIndex);
        double nextDistance = NavigationMath.distanceMeters(current.getLatitude(),
                current.getLongitude(), stepPoint.lat, stepPoint.lon);
        if (voiceEnabled && nextDistance < 65 && stepNumber != lastSpokenStep) {
            lastSpokenStep = stepNumber;
            speak(step.instruction);
        }
        if (NavigationMath.distanceMeters(current.getLatitude(), current.getLongitude(),
                targetLat, targetLon) < 18) stepText.setText("Du bist beim Auto angekommen.");
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override protected void onPause() {
        if (locations != null) locations.removeUpdates(this);
        if (sensors != null) sensors.unregisterListener(this);
        if (speaker != null) speaker.stop();
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        if (speaker != null) { speaker.stop(); speaker.shutdown(); speaker = null; }
        if (map != null) { map.onDetach(); map = null; }
        super.onDestroy();
    }
}

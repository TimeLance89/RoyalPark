package app.royalpark.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
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
import android.os.Build;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.text.TextUtils;
import android.widget.ImageButton;
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
    private static final int BG = Color.rgb(247, 250, 251);
    private static final int INK = Color.rgb(22, 45, 56);
    private static final int TEAL = Color.rgb(0, 139, 151);
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
    private TextView offlineRouteButton;
    private boolean offlineRouteActive;
    private TextView speechButton;
    private TextView recalculateButton;
    private TextView modeText;
    private TextView offlineModeButton;
    private TextView onlineModeButton;
    private TextView moreButton;
    private LinearLayout secondaryControls;
    private boolean selectedOffline;
    private boolean controlsExpanded;
    private boolean parkingApproximate;
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
    private long lastCompassFrameMs;
    private long declinationCheckedMs;
    private float cachedMagneticDeclination;

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

    private GradientDrawable background(int color, int radius, int borderColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (borderColor != Color.TRANSPARENT) d.setStroke(dp(1), borderColor);
        return d;
    }
    private TextView action(String value, boolean primary) {
        TextView view = label(value, 14, primary ? Color.WHITE : INK, true);
        view.setBackground(background(primary ? TEAL : 0xFFF1F6F7, 17, Color.TRANSPARENT));
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
        view.setMinHeight(dp(46));
        return view;
    }
    private TextView smallAction(String title, Runnable click) {
        TextView view = action(title, false);
        view.setTextSize(13);
        view.setOnClickListener(v -> click.run());
        return view;
    }
    private void addRowItem(LinearLayout row, View child, float weight, int marginLeft) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), weight);
        lp.leftMargin = dp(marginLeft);
        row.addView(child, lp);
    }
    private void updateMode() {
        if (modeText == null) return;
        modeText.setText("WELCHE ROUTE?");
        if (recalculateButton != null && !loading)
            recalculateButton.setText(NavigationDockText.routeAction(selectedOffline, route != null));
        if (offlineModeButton != null)
            offlineModeButton.setBackground(background(selectedOffline ? 0xFFE1F3F3 : 0xFFF5F7F8,
                    15, selectedOffline ? 0xFFB4DEDF : Color.TRANSPARENT));
        if (onlineModeButton != null)
            onlineModeButton.setBackground(background(!selectedOffline ? 0xFFE1F3F3 : 0xFFF5F7F8,
                    15, !selectedOffline ? 0xFFB4DEDF : Color.TRANSPARENT));
    }
    private void recalculate() {
        if (selectedOffline) calculateOffline();
        else askBeforeRouting();
    }
    private void setSheetExpanded(boolean expanded) {
        controlsExpanded = expanded;
        if (secondaryControls != null)
            secondaryControls.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (moreButton != null) {
            moreButton.setText(expanded ? "Weniger" : "Mehr");
            moreButton.setContentDescription(expanded
                    ? "Zusätzliche Navigationsaktionen schließen"
                    : "Zusätzliche Navigationsaktionen öffnen");
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        targetLat = getIntent().getDoubleExtra("latitude", Double.NaN);
        targetLon = getIntent().getDoubleExtra("longitude", Double.NaN);
        if (!ParkingLogic.validCoordinates(targetLat, targetLon)) { finish(); return; }
        parkingApproximate = getIntent().getBooleanExtra("parking_approximate", false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        MapPerformance.configure(this, getPreferences(MODE_PRIVATE));

        // Map-first navigation: all controls are overlays on the SAME osmdroid MapView.
        // Map tile provider and overlays remain connected to online maps, Mapsforge,
        // imported raster maps and BRouter/Valhalla route geometries.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(root);

        FrameLayout mapFrame = new FrameLayout(this);
        map = new MapView(this);
        map.setTileSource(new XYTileSource("OSM", 0, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.org/"}));
        OfflineMaps.apply(this, map);
        map.setMultiTouchControls(true);
        map.setFlingEnabled(true);
        map.getController().setZoom(18);
        map.getController().setCenter(new GeoPoint(targetLat, targetLon));
        Marker carMarker = new Marker(map);
        carMarker.setTitle("Hier steht dein Auto");
        carMarker.setIcon(getDrawable(R.drawable.ic_nav_car));
        carMarker.setPosition(new GeoPoint(targetLat, targetLon));
        carMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(carMarker);
        pointerLine = new Polyline(map);
        pointerLine.setColor(0xAA7199A2);
        pointerLine.setWidth(dp(2));
        map.getOverlays().add(pointerLine);
        routeLine = new Polyline(map);
        routeLine.setColor(TEAL);
        routeLine.setWidth(dp(5));
        map.getOverlays().add(routeLine);
        myMarker = new Marker(map);
        myMarker.setTitle("Mein aktueller Standort");
        myMarker.setIcon(getDrawable(R.drawable.ic_nav_location_dot));
        myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(myMarker);
        mapFrame.addView(map, new FrameLayout.LayoutParams(-1, -1));
        root.addView(mapFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        // Small floating toolbar. The large old white activity header is gone.
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(5), dp(2), dp(5), dp(2));
        toolbar.setBackground(background(0xF7FFFFFF, 20, 0xFFE1E9EC));
        toolbar.setElevation(dp(4));
        TextView back = label("‹", 30, TEAL, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Navigation verlassen");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(47), dp(43)));
        TextView title = label("Zum Parkplatz", 17, INK, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1));
        TextView menu = label("⋯", 25, TEAL, true);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Navigationseinstellungen öffnen");
        menu.setOnClickListener(v -> setSheetExpanded(!controlsExpanded));
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(43), dp(43)));
        FrameLayout.LayoutParams toolbarLp =
                new FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP);
        toolbarLp.setMargins(dp(12), dp(9), dp(12), 0);
        mapFrame.addView(toolbar, toolbarLp);

        // One readable floating direction indicator; no giant white top panel.
        LinearLayout guidance = new LinearLayout(this);
        guidance.setGravity(Gravity.CENTER_VERTICAL);
        guidance.setPadding(dp(12), dp(8), dp(14), dp(8));
        guidance.setBackground(background(0xF9FFFFFF, 19, 0xFFE1E9EC));
        guidance.setElevation(dp(4));
        arrow = label("▲", 26, TEAL, true);
        arrow.setGravity(Gravity.CENTER);
        guidance.addView(arrow, new LinearLayout.LayoutParams(dp(43), dp(55)));
        LinearLayout guidanceWords = new LinearLayout(this);
        guidanceWords.setOrientation(LinearLayout.VERTICAL);
        distanceText = label("Standort suchen", 23, INK, true);
        distanceText.setGravity(Gravity.CENTER_VERTICAL);
        guidanceWords.addView(distanceText);
        routeDetails = label("Zum gespeicherten Auto", 12, MUTED, false);
        routeDetails.setGravity(Gravity.CENTER_VERTICAL);
        guidanceWords.addView(routeDetails);
        stepText = label("", 12, MUTED, false);
        stepText.setGravity(Gravity.CENTER_VERTICAL);
        stepText.setSingleLine(true);
        stepText.setEllipsize(TextUtils.TruncateAt.END);
        stepText.setVisibility(View.GONE);
        guidanceWords.addView(stepText);
        guidance.addView(guidanceWords, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout.LayoutParams guideLp =
                new FrameLayout.LayoutParams(Math.min(dp(270),
                        getResources().getDisplayMetrics().widthPixels - dp(28)),
                        -2, Gravity.TOP | Gravity.LEFT);
        guideLp.setMargins(dp(12), dp(65), 0, 0);
        mapFrame.addView(guidance, guideLp);

        // One location recenter action; two-finger gestures zoom the map.
        TextView follow = smallAction("⌖", () -> {
            if (current == null) toast("Dein Standort wird noch ermittelt.");
            else {
                map.getController().animateTo(new GeoPoint(
                        current.getLatitude(), current.getLongitude()));
                if (map.getZoomLevelDouble() < 17.5)
                    map.getController().setZoom(18);
            }
        });
        follow.setTextSize(24);
        follow.setGravity(Gravity.CENTER);
        follow.setContentDescription("Meinen Standort auf der Karte zentrieren");
        follow.setElevation(dp(4));
        follow.setBackground(background(0xF9FFFFFF, 19, 0xFFE1E9EC));
        FrameLayout.LayoutParams followLp =
                new FrameLayout.LayoutParams(dp(48), dp(48),
                        Gravity.RIGHT | Gravity.BOTTOM);
        followLp.rightMargin = dp(12);
        mapFrame.addView(follow, followLp);

        // Bottom dock has one row by default, not a stack of permanent panels.
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(12), dp(8), dp(12), dp(12));
        sheet.setBackground(background(0xFCFFFFFF, 23, 0xFFE0E9EA));
        sheet.setElevation(dp(8));
        View grip = new View(this);
        grip.setBackground(background(0xFFD3DEE0, 3, Color.TRANSPARENT));
        LinearLayout.LayoutParams gripLp = new LinearLayout.LayoutParams(
                dp(35), dp(4));
        gripLp.gravity = Gravity.CENTER_HORIZONTAL;
        gripLp.bottomMargin = dp(9);
        sheet.addView(grip, gripLp);
        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setGravity(Gravity.CENTER_VERTICAL);
        recalculateButton = action("↻  Route", true);
        recalculateButton.setContentDescription("Fußweg berechnen oder neu berechnen");
        recalculateButton.setOnClickListener(v -> recalculate());
        addRowItem(quickActions, recalculateButton, 1.48f, 0);
        speechButton = smallAction("Ansagen aus", this::toggleSpeech);
        speechButton.setContentDescription("Sprachausgabe ein- oder ausschalten");
        addRowItem(quickActions, speechButton, 1.28f, 7);
        moreButton = smallAction("Mehr", () -> setSheetExpanded(!controlsExpanded));
        moreButton.setContentDescription("Weitere Navigationsaktionen anzeigen");
        addRowItem(quickActions, moreButton, 0.75f, 7);
        sheet.addView(quickActions);

        secondaryControls = new LinearLayout(this);
        secondaryControls.setOrientation(LinearLayout.VERTICAL);
        modeText = label("ROUTE WÄHLEN", 11, MUTED, true);
        modeText.setPadding(dp(4), dp(13), 0, dp(6));
        secondaryControls.addView(modeText);
        LinearLayout modes = new LinearLayout(this);
        offlineModeButton = smallAction("Offline · BRouter", () -> {
            selectedOffline = true;
            updateMode();
        });
        offlineModeButton.setContentDescription("Offline-Fußwegrouting auswählen");
        onlineModeButton = smallAction("Online", () -> {
            selectedOffline = false;
            updateMode();
        });
        onlineModeButton.setContentDescription("Online-Fußwegrouting auswählen");
        addRowItem(modes, offlineModeButton, 1.5f, 0);
        addRowItem(modes, onlineModeButton, 1f, 7);
        secondaryControls.addView(modes);
        LinearLayout extras = new LinearLayout(this);
        TextView external = smallAction("↗  Externe Karte", this::openExternal);
        addRowItem(extras, external, 1f, 0);
        TextView end = smallAction("Navigation beenden", this::finish);
        end.setTextColor(0xFFB44C55);
        addRowItem(extras, end, 1.12f, 7);
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(-1, -2);
        exLp.topMargin = dp(8);
        secondaryControls.addView(extras, exLp);
        LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(-1, -2);
        sheet.addView(secondaryControls, moreLp);
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM);
        sheetLp.setMargins(dp(10), 0, dp(10), dp(10));
        mapFrame.addView(sheet, sheetLp);
        selectedOffline = OfflineWalkingRouter.isInstalled(this);
        updateMode();
        setSheetExpanded(false);

        // Never put mandatory OSM attribution underneath an overlay.
        TextView credit = label("© OpenStreetMap-Mitwirkende", 10, MUTED, false);
        credit.setPadding(dp(7), dp(3), dp(7), dp(3));
        credit.setBackground(background(0xE7FFFFFF, 8, Color.TRANSPARENT));
        credit.setContentDescription("OpenStreetMap-Lizenz öffnen");
        credit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.openstreetmap.org/copyright"))));
        FrameLayout.LayoutParams creditLp =
                new FrameLayout.LayoutParams(-2, -2, Gravity.LEFT | Gravity.BOTTOM);
        creditLp.leftMargin = dp(10);
        mapFrame.addView(credit, creditLp);

        Runnable positionOverlays = () -> {
            int dockHeight = sheet.getHeight();
            if (dockHeight == 0) return;
            FrameLayout.LayoutParams p =
                    (FrameLayout.LayoutParams) follow.getLayoutParams();
            p.bottomMargin = dockHeight + dp(24);
            follow.setLayoutParams(p);
            FrameLayout.LayoutParams c =
                    (FrameLayout.LayoutParams) credit.getLayoutParams();
            c.bottomMargin = dockHeight + dp(21);
            credit.setLayoutParams(c);
        };
        sheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((b - t) != (ob - ot)) positionOverlays.run();
        });
        mapFrame.post(positionOverlays);

        offlineRouteButton = offlineModeButton;
        routeButton = onlineModeButton;


        locations = getSystemService(LocationManager.class);
        sensors = getSystemService(SensorManager.class);
        if (sensors != null) rotationSensor =
                sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    private void askBeforeRouting() {
        if (loading) return;
        if (OfflineMaps.enabled(this) || RegionMaps.enabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Offline-Kartenmodus")
                    .setMessage("Für eine Online-Route muss die Karte selbst nicht online sein. Die Routendaten werden allerdings an Valhalla übertragen. Wenn du vollständig offline bleiben möchtest, nutze die BRouter-Schaltfläche.")
                    .setPositiveButton("Verstanden", (dialog, which) -> confirmOnlineRoute()).show();
            return;
        }
        confirmOnlineRoute();
    }

    private void confirmOnlineRoute() {
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

    private void calculateOffline() {
        if (loading) return;
        if (current == null) { toast("Standort wird noch ermittelt."); return; }
        if (!OfflineWalkingRouter.isInstalled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("BRouter erforderlich")
                    .setMessage("Offline-Routen werden lokal durch die kostenlose BRouter-App berechnet. Installiere BRouter und lade dort zunächst die passenden .rd5-Routingsegmente herunter. Die Regionskarte allein reicht nicht aus.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("BRouter öffnen / installieren", (d, w) -> {
                        Intent launch = getPackageManager().getLaunchIntentForPackage("btools.routingapp");
                        if (launch != null) startActivity(launch);
                        else startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://f-droid.org/packages/btools.routingapp/")));
                    }).show();
            return;
        }
        loading = true;
        recalculateButton.setText("Berechne …");
        stepText.setText("BRouter berechnet die Strecke direkt auf diesem Gerät.");
        boolean started = OfflineWalkingRouter.calculate(this, worker, current.getLatitude(),
                current.getLongitude(), targetLat, targetLon, (result, error) -> {
                    if (isFinishing() || isDestroyed()) return;
                    loading = false;
                    recalculateButton.setText("↻  Route");
                    if (result != null) displayRoute(result, true);
                    else {
                        stepText.setText("Keine Offline-Route – Kompass funktioniert weiterhin.");
                        routeDetails.setText(error == null ? "BRouter-Daten prüfen." : error);
                    }
                });
        if (!started) {
            loading = false;
            recalculateButton.setText("↻  Route");
        }
    }

    private void loadRoute() {
        if (current == null || loading) return;
        final double startLat = current.getLatitude();
        final double startLon = current.getLongitude();
        lastRequestMs = System.currentTimeMillis();
        loading = true;
        recalculateButton.setText("Berechne …");
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
                    recalculateButton.setText("↻  Route");
                if (result == null) {
                    stepText.setText("Online-Routing nicht verfügbar – Kompass bleibt aktiv.");
                    routeDetails.setText(failure == null ? "Verbindung fehlgeschlagen" : failure);
                    return;
                }
                displayRoute(result, false);
            });
        });
    }

    private void displayRoute(WalkingRouteService.Route result, boolean offline) {
        route = result;
        recalculateButton.setText(NavigationDockText.routeAction(offline, true));
        offlineRouteActive = offline;
        selectedOffline = offline;
        updateMode();
        routeProgressIndex = 0;
        lastSpokenStep = -1;
        List<GeoPoint> path = new ArrayList<>();
        for (RouteGeometry.Point p : result.coordinates) path.add(new GeoPoint(p.lat, p.lon));
        routeLine.setPoints(path);
        pointerLine.setPoints(new ArrayList<>());
        routeDetails.setText(NavigationPresentation.walkingDuration(result.durationSeconds));
        if (offline) {
            stepText.setText("Folge der markierten Route");
            stepText.setVisibility(View.VISIBLE);
        }
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
    }

    private void toggleSpeech() {
        voiceEnabled = !voiceEnabled;
        speechButton.setText(voiceEnabled ? "Ansagen an" : "Ansagen aus");
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
        // Compass rendering at ~12 fps is smooth while avoiding hundreds of
        // matrix conversions and TextView rotations per second on mid-range phones.
        long now = SystemClock.elapsedRealtime();
        if (now - lastCompassFrameMs < 80L) return;
        lastCompassFrameMs = now;
        float[] matrix = new float[9], orientation = new float[3];
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, orientation);
        if (current != null && (declinationCheckedMs == 0
                || now - declinationCheckedMs > 60_000L)) {
            GeomagneticField magnetic = new GeomagneticField(
                    (float) current.getLatitude(), (float) current.getLongitude(),
                    (float) current.getAltitude(), System.currentTimeMillis());
            cachedMagneticDeclination = magnetic.getDeclination();
            declinationCheckedMs = now;
        }
        heading = (float) Math.toDegrees(orientation[0]) + cachedMagneticDeclination;
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
        float accuracy = current.hasAccuracy() ? current.getAccuracy() : Float.NaN;
        boolean precise = LocationGateway.hasPrecisePermission(this);
        distanceText.setText(NavigationPresentation.distanceText(distance, accuracy));
        if (route == null) {
            routeDetails.setText(NavigationPresentation.locationHint(
                    distance, accuracy, precise, parkingApproximate));
            stepText.setVisibility(View.GONE);
        } else {
            routeDetails.setText(NavigationPresentation.walkingDuration(route.durationSeconds));
            stepText.setVisibility(View.VISIBLE);
        }
        updateArrow();
        GeoPoint me = new GeoPoint(current.getLatitude(), current.getLongitude());
        myMarker.setPosition(me);

        if (route == null) {
            pointerLine.setPoints(Arrays.asList(me, new GeoPoint(targetLat, targetLon)));
            // A coarse GPS fix cannot prove that the user reached the car.
            stepText.setText("");
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
            stepText.setText("Abseits der Route · neu berechnen");
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
            stepText.setText("Folge der eingezeichneten Route");
            return;
        }
        WalkingRouteService.Step step = route.steps.get(stepNumber);
        stepText.setText(NavigationPresentation.briefInstruction(step.instruction));
        RouteGeometry.Point stepPoint = route.coordinates.get(step.shapeIndex);
        double nextDistance = NavigationMath.distanceMeters(current.getLatitude(),
                current.getLongitude(), stepPoint.lat, stepPoint.lon);
        if (voiceEnabled && nextDistance < 65 && stepNumber != lastSpokenStep) {
            lastSpokenStep = stepNumber;
            speak(step.instruction);
        }
        if (NavigationPresentation.closeToCar(
                NavigationMath.distanceMeters(current.getLatitude(), current.getLongitude(),
                        targetLat, targetLon),
                current.hasAccuracy() ? current.getAccuracy() : Float.NaN,
                LocationGateway.hasPrecisePermission(this), parkingApproximate))
            stepText.setText("Auto wahrscheinlich in der Nähe");
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

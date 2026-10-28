package app.royalpark.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import androidx.core.content.FileProvider;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;
import java.io.File;
import java.io.IOException;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Original, clean-room implementation of a car parking helper.
 * It contains none of the third-party application's source code, assets or identifiers.
 */
public final class MainActivity extends Activity {
    // Light map-first visual language, guided by screenshots of reference version 1.1.15.
    private static final int BG = Color.rgb(247, 250, 251);
    private static final int CARD = Color.WHITE;
    private static final int CARD_BRIGHT = Color.rgb(233, 246, 246);
    private static final int ACCENT = Color.rgb(0, 150, 157);
    private static final int ACCENT_DARK = Color.rgb(0, 118, 128);
    private static final int WHITE = Color.rgb(21, 45, 54);
    private static final int MUTED = Color.rgb(104, 124, 132);
    private static final int REQ_LOCATION = 100;
    private static final int REQ_BT = 101;
    private static final int REQ_NOTIFICATIONS = 102;
    private static final int REQ_PHOTO = 103;
    private static final int REQ_CAMERA = 104;
    private static final int REQ_MOTION = 105;
    private static final int REQ_BACKUP_CREATE = 106;
    private static final int REQ_BACKUP_IMPORT = 107;
    private static final int REQ_OFFLINE_MAP = 108;
    private static final int TAB_MAP = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_SETTINGS = 2;

    private ParkingStore store;
    private SharedPreferences prefs;
    private LinearLayout mainRoot;
    private FrameLayout body;
    private LinearLayout bottomBar;
    private LinearLayout headerView;
    private MapView map;
    private FrameLayout mapScreen;
    private View subPage;
    private String lastMapRenderKey;
    private long lastDisplayedParkingId = -1;
    private TextView locationStatus;
    private TextView parkingDistanceLine;
    private ImageButton locationButton;
    private Marker myLocationMarker;
    private boolean locatingNow;
    /** Keep the map usable by collapsing secondary actions until explicitly requested. */
    private boolean parkingDetailsExpanded = false;
    private boolean regionDownloadRunning;
    private Location deviceLocation;
    private Runnable afterPermission;
    private long photoSpotId = -1;
    private Uri pendingCameraUri;
    private String pendingCameraFile;
    private int currentTab = TAB_MAP;
    private final ExecutorService backupExecutor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store = new ParkingStore(this);
        if (state != null) {
            photoSpotId = state.getLong("photoSpotId", -1);
            pendingCameraFile = state.getString("pendingCameraFile");
            String uri = state.getString("pendingCameraUri");
            if (uri != null) pendingCameraUri = Uri.parse(uri);
        }
        prefs = getSharedPreferences("royalpark", MODE_PRIVATE);
        // No tracking SDK and no analytics. Map tiles are fetched only for visible viewport.
        MapPerformance.configure(this, getPreferences(MODE_PRIVATE));
        ParkingAlarmReceiver.createChannel(this);
        mainRoot = new LinearLayout(this);
        mainRoot.setOrientation(LinearLayout.VERTICAL);
        mainRoot.setBackgroundColor(BG);
        setContentView(mainRoot);
        if (Build.VERSION.SDK_INT >= 35) {
            mainRoot.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                mainRoot.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        header();
        body = new FrameLayout(this);
        mainRoot.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        bottomBar = new LinearLayout(this);
        bottomBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bottomBar.setBackgroundColor(CARD);
        mainRoot.addView(bottomBar);
        showTab(TAB_MAP);
        // Onboarding happens once and never blocks an existing user's data migration.
        if (state == null && !prefs.getBoolean("onboarding_complete", false)) {
            mainRoot.post(() -> {
                if (!isFinishing() && !isDestroyed())
                    startActivity(new Intent(this, OnboardingActivity.class));
            });
        }
        handleWidgetAction(getIntent());
    }

    private void handleWidgetAction(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ParkingWidget.QUICK_SAVE.equals(action)) {
            intent.setAction(null);
            mainRoot.post(this::saveCurrent);
        } else if (ParkingTimerWidget.OPEN_TIMER.equals(action)) {
            intent.setAction(null);
            mainRoot.post(() -> {
                ParkingSpot spot = store.active();
                if (spot == null) toast("Bitte zuerst einen Parkplatz speichern.");
                else timer(spot);
            });
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetAction(intent);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("photoSpotId", photoSpotId);
        out.putString("pendingCameraFile", pendingCameraFile);
        if (pendingCameraUri != null) out.putString("pendingCameraUri", pendingCameraUri.toString());
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable surface(int color, int stroke, int radius) {
        GradientDrawable drawable = shape(color, radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout column() {
        LinearLayout linear = new LinearLayout(this);
        linear.setOrientation(LinearLayout.VERTICAL);
        return linear;
    }

    private LinearLayout row() {
        LinearLayout linear = new LinearLayout(this);
        linear.setOrientation(LinearLayout.HORIZONTAL);
        linear.setGravity(Gravity.CENTER_VERTICAL);
        return linear;
    }

    private LinearLayout card() {
        LinearLayout view = column();
        view.setBackground(surface(CARD, Color.rgb(223, 233, 236), 22));
        view.setElevation(dp(2));
        view.setPadding(dp(18), dp(17), dp(18), dp(17));
        return view;
    }

    private void margin(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(child, params);
    }

    private void line(LinearLayout parent, String value, int size, int color, boolean bold, int top) {
        margin(parent, text(value, size, color, bold), top);
    }

    private TextView action(String caption, boolean primary, Runnable task) {
        TextView button = text(caption, 15, primary ? Color.WHITE : ACCENT_DARK, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setMinHeight(dp(50));
        button.setBackground(shape(primary ? ACCENT_DARK : CARD_BRIGHT, 17));
        button.setOnClickListener(v -> task.run());
        return button;
    }

    private void actionLine(LinearLayout parent, String title, boolean primary, Runnable task) {
        margin(parent, action(title, primary, task), 9);
    }

    private void header() {
        headerView = row();
        headerView.setBackgroundColor(CARD);
        headerView.setPadding(dp(14), dp(9), dp(14), dp(9));
        TextView back = text("‹", 34, ACCENT_DARK, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showTab(TAB_MAP));
        headerView.addView(back, new LinearLayout.LayoutParams(dp(40), dp(43)));
        TextView heading = text("royalpark", 21, WHITE, true);
        heading.setGravity(Gravity.CENTER);
        headerView.addView(heading, new LinearLayout.LayoutParams(0, dp(43), 1));
        TextView map = text("⌖", 25, ACCENT_DARK, true);
        map.setGravity(Gravity.CENTER);
        map.setOnClickListener(v -> showTab(TAB_MAP));
        headerView.addView(map, new LinearLayout.LayoutParams(dp(40), dp(43)));
        mainRoot.addView(headerView);
    }

    /**
     * Keep the map view and provider alive while changing tabs. Previously every
     * onResume() and every tab change destroyed the entire map, discarding its
     * in-memory tile cache and re-opening the Mapsforge rendering pipeline.
     */
    private void showTab(int tab) {
        currentTab = tab;
        boolean showMap = tab == TAB_MAP;
        headerView.setVisibility(showMap ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(showMap ? View.GONE : View.VISIBLE);
        if (showMap) {
            if (subPage != null) {
                body.removeView(subPage);
                subPage = null;
            }
            String key = mapRenderKey();
            ParkingSpot active = store.active();
            long spotId = active == null ? -1 : active.id;
            if (map != null && mapScreen != null && key.equals(lastMapRenderKey)) {
                mapScreen.setVisibility(View.VISIBLE);
                map.onResume();
                return;
            }
            // Rebuild only when the stored parking or the map data source
            // actually changed. Preserve camera position when editing a spot.
            GeoPoint previousCenter = null;
            double previousZoom = 0;
            if (map != null && spotId == lastDisplayedParkingId
                    && map.getMapCenter() != null) {
                previousCenter = new GeoPoint(map.getMapCenter().getLatitude(), map.getMapCenter().getLongitude());
                previousZoom = map.getZoomLevelDouble();
            }
            if (map != null) {
                map.onPause();
                map.onDetach();
                map = null;
            }
            body.removeAllViews();
            mapScreen = null;
            parkingDistanceLine = null;
            showFullMap();
            if (map != null && previousCenter != null) {
                map.getController().setZoom(previousZoom);
                map.getController().setCenter(previousCenter);
            }
            lastMapRenderKey = key;
            lastDisplayedParkingId = spotId;
            if (map != null) map.onResume();
            return;
        }
        if (mapScreen != null && map != null) {
            mapScreen.setVisibility(View.GONE);
            map.onPause();
        }
        if (subPage != null) {
            body.removeView(subPage);
            subPage = null;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(16), dp(8), dp(16), dp(26));
        scroll.addView(content);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        subPage = scroll;
        if (tab == TAB_HISTORY) showHistory(content);
        else showSettings(content);
        bottomBar.removeAllViews();
        nav("⌖", "Karte", TAB_MAP);
        nav("◷", "Verlauf", TAB_HISTORY);
        nav("⚙", "Mehr", TAB_SETTINGS);
    }

    private String mapRenderKey() {
        ParkingSpot active = store.active();
        String region = RegionMaps.current(this) == null ? ""
                : RegionMaps.current(this).code;
        String providerState = region + "|" + RegionMaps.enabled(this)
                + "|" + RegionMaps.offline(this)
                + "|" + OfflineMaps.enabled(this)
                + "|" + OfflineMaps.hasArchive(this)
                + "|" + OfflineMaps.sourceName(this);
        return MapPerformance.renderKey(active, providerState,
                LocationGateway.hasPrecisePermission(this));
    }

    private void nav(String symbol, String label, int tab) {
        TextView item = text(symbol + "\n" + label, 13,
                currentTab == tab ? ACCENT_DARK : MUTED, currentTab == tab);
        item.setGravity(Gravity.CENTER);
        item.setLineSpacing(dp(3), 1f);
        item.setPadding(dp(2), dp(8), dp(2), dp(8));
        item.setOnClickListener(v -> showTab(tab));
        bottomBar.addView(item, new LinearLayout.LayoutParams(0, dp(58), 1));
    }

    private TextView floatingButton(String symbol, int textSize, Runnable action) {
        TextView v = text(symbol, textSize, ACCENT_DARK, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(shape(Color.WHITE, 40));
        v.setElevation(dp(7));
        v.setOnClickListener(view -> action.run());
        return v;
    }

    private FrameLayout.LayoutParams floatingAt(int w, int h, int gravity,
            int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(w), dp(h), gravity);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private TextView quickAction(String icon, String label, Runnable click) {
        TextView result = text(icon + "\n" + label, 12, WHITE, false);
        result.setGravity(Gravity.CENTER);
        result.setLineSpacing(dp(6), 1f);
        result.setBackground(shape(CARD_BRIGHT, 13));
        result.setOnClickListener(v -> click.run());
        return result;
    }

    private void threeActions(LinearLayout sheet, TextView one, TextView two, TextView three) {
        LinearLayout row = row();
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(70), 1);
        p1.rightMargin = dp(7);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(70), 1);
        p2.setMargins(dp(2), 0, dp(2), 0);
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(70), 1);
        p3.leftMargin = dp(7);
        row.addView(one, p1);
        row.addView(two, p2);
        row.addView(three, p3);
        margin(sheet, row, 11);
    }

    /** Full-screen, map-first layout with the reference app's light bottom-sheet structure. */
    private void showFullMap() {
        ParkingSpot active = store.active();
        FrameLayout canvas = new FrameLayout(this);
        canvas.setBackgroundColor(BG);
        mapScreen = canvas;
        body.addView(canvas, new FrameLayout.LayoutParams(-1, -1));

        map = new MapView(this);
        map.setTileSource(new XYTileSource("OSM", 0, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.org/"}));
        boolean offline = OfflineMaps.apply(this, map);
        map.setMultiTouchControls(true);
        map.setFlingEnabled(true);
        // The first map view focuses closely on the actual parked car, never on Germany.
        if (deviceLocation == null && LocationGateway.hasPermission(this)) {
            deviceLocation = LocationGateway.recent(this, 120_000L, 250f);
        }
        map.getController().setZoom(active != null ? MapFraming.CAR_ZOOM
                : deviceLocation != null ? MapFraming.myPositionZoom(
                        deviceLocation.hasAccuracy() ? deviceLocation.getAccuracy() : Float.NaN)
                : 5.5);
        GeoPoint destination = active != null
                ? new GeoPoint(active.latitude, active.longitude)
                : deviceLocation != null
                ? new GeoPoint(deviceLocation.getLatitude(), deviceLocation.getLongitude())
                : new GeoPoint(51.1657, 10.4515);
        map.getController().setCenter(destination);
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint point) { return false; }
            @Override public boolean longPressHelper(GeoPoint point) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Parkplatz hier speichern?")
                        .setMessage("Den markierten Standort übernehmen?")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Speichern", (dialog, which) -> {
                            ParkingSpot spot = store.save(point.getLatitude(), point.getLongitude(), "", "MAP");
                            AlarmScheduler.cancel(MainActivity.this);
                            ParkingWidget.updateAll(MainActivity.this);
                            showTab(TAB_MAP);
                            toast("Parkplatz gespeichert · Notiz optional über die Parkplatzkarte.");
                        }).show();
                return true;
            }
        }));
        myLocationMarker = new Marker(map);
        myLocationMarker.setTitle("Mein Standort");
        myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        if (deviceLocation != null) {
            myLocationMarker.setPosition(new GeoPoint(
                    deviceLocation.getLatitude(), deviceLocation.getLongitude()));
            map.getOverlays().add(myLocationMarker);
        }
        if (active != null) {
            Marker parked = new Marker(map);
            parked.setPosition(new GeoPoint(active.latitude, active.longitude));
            parked.setTitle("Mein Parkplatz");
            parked.setSnippet(active.note);
            parked.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(parked);
        }
        canvas.addView(map, new FrameLayout.LayoutParams(-1, -1));

        // A calm, single top bar rather than scattered large circles.
        TextView brand = text("  ◉  royalpark  ", 17, WHITE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setBackground(surface(Color.WHITE, Color.rgb(227, 236, 237), 21));
        brand.setElevation(dp(4));
        brand.setOnClickListener(v -> showTab(TAB_SETTINGS));
        FrameLayout.LayoutParams brandParams = floatingAt(144, 44,
                Gravity.TOP | Gravity.LEFT, 14, 14, 0, 0);
        canvas.addView(brand, brandParams);

        TextView layers = text(offline ? "◈  Offline" : "▱  Karte", 14, ACCENT_DARK, true);
        layers.setGravity(Gravity.CENTER);
        layers.setBackground(surface(Color.WHITE, Color.rgb(227, 236, 237), 21));
        layers.setElevation(dp(4));
        layers.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Kartenansicht")
                    .setItems(new String[]{"Onlinekarte (Standard)",
                                    "Kartenregion herunterladen",
                                    "Geladene Region offline verwenden",
                                    "Offline-Rasterkarte importieren",
                                    "Karteneinstellungen öffnen"}, (d, which) -> {
                        if (which == 0) {
                            RegionMaps.disable(this);
                            OfflineMaps.setEnabled(this, false);
                            showTab(TAB_MAP);
                        } else if (which == 1) chooseRegion();
                        else if (which == 2) {
                            RegionMaps.Region region = RegionMaps.current(this);
                            if (RegionMaps.downloaded(this, region)) {
                                RegionMaps.enable(this, region);
                                showTab(TAB_MAP);
                            } else error("Bitte zuerst eine Region herunterladen.");
                        } else if (which == 3) importOfflineMap();
                        else showTab(TAB_SETTINGS);
                    }).show();
        });
        canvas.addView(layers, floatingAt(110, 44,
                Gravity.TOP | Gravity.RIGHT, 0, 14, 14, 0));

        TextView history = text("◷", 22, WHITE, true);
        history.setGravity(Gravity.CENTER);
        history.setContentDescription("Parkverlauf öffnen");
        history.setBackground(surface(Color.WHITE, Color.rgb(227, 236, 237), 18));
        history.setElevation(dp(3));
        history.setOnClickListener(v -> showTab(TAB_HISTORY));
        canvas.addView(history, floatingAt(43, 41,
                Gravity.TOP | Gravity.LEFT, 14, 67, 0, 0));

        if (deviceLocation != null) {
            boolean precise = LocationGateway.hasPrecisePermission(this);
            String gpsCaption = precise && deviceLocation.hasAccuracy()
                    ? "⌖  GPS ±" + Math.round(deviceLocation.getAccuracy()) + " m"
                    : "⌖  Ungefährer Standort";
            TextView gps = text(gpsCaption, 12, ACCENT_DARK, true);
            gps.setGravity(Gravity.CENTER);
            gps.setPadding(dp(10), 0, dp(10), 0);
            gps.setBackground(surface(Color.WHITE, Color.rgb(227, 236, 237), 16));
            gps.setElevation(dp(3));
            FrameLayout.LayoutParams gpsParams = new FrameLayout.LayoutParams(
                    -2, dp(32), Gravity.TOP | Gravity.LEFT);
            gpsParams.setMargins(dp(65), dp(71), 0, 0);
            canvas.addView(gps, gpsParams);
        }

        // Smart map controls: one horizontal cluster, not a tall rail that covers the map.
        LinearLayout cameraBar = row();
        cameraBar.setGravity(Gravity.CENTER_VERTICAL);
        cameraBar.setBackground(surface(Color.WHITE, Color.rgb(223, 233, 236), 18));
        cameraBar.setElevation(dp(5));
        cameraBar.setPadding(dp(4), dp(2), dp(4), dp(2));
        locationButton = new ImageButton(this);
        locationButton.setImageResource(R.drawable.ic_my_location);
        locationButton.setColorFilter(ACCENT_DARK);
        locationButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        locationButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        locationButton.setContentDescription("Mein Standort");
        locationButton.setBackground(shape(Color.TRANSPARENT, 14));
        locationButton.setOnClickListener(v -> showMyLocation());
        cameraBar.addView(locationButton, new LinearLayout.LayoutParams(dp(45), dp(43)));
        if (active != null) {
            View cut1 = new View(this);
            cut1.setBackgroundColor(0xFFE4EFEF);
            cameraBar.addView(cut1, new LinearLayout.LayoutParams(dp(1), dp(24)));
            TextView focusCar = text("Auto", 13, ACCENT_DARK, true);
            focusCar.setGravity(Gravity.CENTER);
            focusCar.setContentDescription("Parkplatz nah anzeigen");
            focusCar.setOnClickListener(v -> focusParked());
            cameraBar.addView(focusCar, new LinearLayout.LayoutParams(dp(47), dp(43)));
            View cut2 = new View(this);
            cut2.setBackgroundColor(0xFFE4EFEF);
            cameraBar.addView(cut2, new LinearLayout.LayoutParams(dp(1), dp(24)));
            TextView focusBoth = text("Beide", 13, ACCENT_DARK, true);
            focusBoth.setGravity(Gravity.CENTER);
            focusBoth.setContentDescription("Parkplatz und eigene Position anzeigen");
            focusBoth.setOnClickListener(v -> focusBoth());
            cameraBar.addView(focusBoth, new LinearLayout.LayoutParams(dp(55), dp(43)));
        }
        FrameLayout.LayoutParams cameraParams = new FrameLayout.LayoutParams(
                -2, dp(48), Gravity.TOP | Gravity.RIGHT);
        cameraParams.setMargins(0, dp(67), dp(14), 0);
        canvas.addView(cameraBar, cameraParams);
        locationStatus = text("", 12, WHITE, true);
        locationStatus.setPadding(dp(12), dp(7), dp(12), dp(7));
        locationStatus.setBackground(shape(Color.WHITE, 14));
        locationStatus.setElevation(dp(4));
        locationStatus.setVisibility(View.GONE);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                -2, -2, Gravity.TOP | Gravity.RIGHT);
        statusParams.setMargins(0, dp(121), dp(14), 0);
        canvas.addView(locationStatus, statusParams);

        LinearLayout sheet = card();
        sheet.setBackground(shape(Color.WHITE, 26));
        sheet.setElevation(dp(12));
        sheet.setPadding(dp(18), dp(13), dp(18), dp(16));
        View handle = new View(this);
        handle.setBackground(shape(0xFFD9E7E8, 3));
        LinearLayout.LayoutParams grip = new LinearLayout.LayoutParams(dp(37), dp(4));
        grip.gravity = Gravity.CENTER_HORIZONTAL;
        grip.bottomMargin = dp(10);
        sheet.addView(handle, grip);

        if (active == null) {
            line(sheet, "PARKPLATZ", 11, ACCENT_DARK, true, 0);
            line(sheet, "Wo steht dein Auto?", 22, WHITE, true, 6);
            line(sheet, "Einmal speichern. Später einfach wiederfinden.", 13, MUTED, false, 5);
            if (LocationGateway.hasPermission(this) &&
                    !LocationGateway.hasPrecisePermission(this)) {
                TextView precise = text("⌖  Standort nur ungefähr · verbessern", 12, ACCENT_DARK, true);
                precise.setOnClickListener(v -> openLocationPermissionSettings());
                margin(sheet, precise, 8);
            }
            actionLine(sheet, "＋  Parkplatz speichern", true, this::saveCurrent);
            TextView manually = text("Auf Karte markieren  ·  lange gedrückt halten", 13, ACCENT_DARK, false);
            manually.setGravity(Gravity.CENTER);
            manually.setOnClickListener(v -> Toast.makeText(this,
                    "Halte deinen Finger auf den gewünschten Parkplatz in der Karte.",
                    Toast.LENGTH_SHORT).show());
            margin(sheet, manually, 8);
        } else {
            LinearLayout headingRow = row();
            LinearLayout heading = column();
            line(heading, "DEIN PARKPLATZ", 11, ACCENT_DARK, true, 0);
            line(heading, "Dein Auto steht hier", 21, WHITE, true, 4);
            line(heading, ParkingPresentation.ageText(
                    active.parkedAt, System.currentTimeMillis()),
                    12, MUTED, false, 4);
            headingRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
            TextView more = text("···", 24, MUTED, true);
            more.setGravity(Gravity.CENTER);
            more.setContentDescription("Parkplatzaktionen");
            more.setOnClickListener(v -> showParkingMore(active));
            headingRow.addView(more, new LinearLayout.LayoutParams(dp(43), dp(44)));
            sheet.addView(headingRow);

            parkingDistanceLine = text("⌖  Entfernung zum Auto ermitteln", 13, ACCENT_DARK, true);
            parkingDistanceLine.setOnClickListener(v -> focusBoth());
            margin(sheet, parkingDistanceLine, 8);
            updateParkingDistance();
            if (ParkingAccuracy.SOURCE_APPROXIMATE.equals(active.source)) {
                TextView approximate = text("⌖  Nur ungefähre Position gespeichert", 12, MUTED, false);
                margin(sheet, approximate, 7);
                approximate.setOnClickListener(v -> openLocationPermissionSettings());
            }
            String garage = GarageLabels.summary(active.floor, active.space);
            if (!garage.isEmpty()) line(sheet, garage, 13, MUTED, false, 5);
            if (!active.note.isEmpty()) line(sheet, active.note, 13, MUTED, false, 5);
            if (active.timerAt > 0) line(sheet, "Parkuhr bis " + format(active.timerAt),
                    13, ACCENT_DARK, true, 5);

            actionLine(sheet, "➤  Route zu meinem Auto", true, () -> navigate(active));
            // Secondary actions are available on demand; the map remains front and center.
            LinearLayout extras = column();
            LinearLayout shortcuts = row();
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(58), 1);
            shortcuts.addView(quickAction("◷", "Parkuhr", () -> timer(active)), p);
            LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(58), 1);
            p2.leftMargin = dp(8);
            shortcuts.addView(quickAction("✎", "Notiz", () -> editNote(active)), p2);
            LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, dp(58), 1);
            p3.leftMargin = dp(8);
            shortcuts.addView(quickAction("▣", "Foto", () -> photo(active)), p3);
            margin(extras, shortcuts, 4);
            TextView moreOptions = text("Parkhaus · Teilen · Verlauf  →", 13, ACCENT_DARK, true);
            moreOptions.setGravity(Gravity.CENTER);
            moreOptions.setMinHeight(dp(44));
            moreOptions.setContentDescription("Weitere Optionen zu diesem Parkplatz");
            moreOptions.setOnClickListener(v -> showParkingMore(active));
            margin(extras, moreOptions, 6);

            // Deletion is intentionally visible in the expanded details, not hidden
            // in a second dialog behind the overflow menu. Confirmation is required.
            TextView deleteSpot = text("⌫  Parkplatz löschen", 13, Color.rgb(173, 57, 63), true);
            deleteSpot.setGravity(Gravity.CENTER);
            deleteSpot.setMinHeight(dp(44));
            deleteSpot.setContentDescription("Gespeicherten Parkplatz dauerhaft löschen");
            deleteSpot.setOnClickListener(v -> confirmDeleteParking(active, TAB_MAP));
            margin(extras, deleteSpot, 2);
            extras.setVisibility(parkingDetailsExpanded ? View.VISIBLE : View.GONE);
            TextView expand = text(parkingDetailsExpanded ? "Weniger anzeigen  ⌃"
                    : "Weitere Aktionen  ⌄", 13, ACCENT_DARK, true);
            expand.setGravity(Gravity.CENTER);
            expand.setMinHeight(dp(38));
            expand.setContentDescription("Zusätzliche Parkplatzaktionen ein- oder ausblenden");
            expand.setOnClickListener(v -> {
                parkingDetailsExpanded = !parkingDetailsExpanded;
                extras.setVisibility(parkingDetailsExpanded ? View.VISIBLE : View.GONE);
                expand.setText(parkingDetailsExpanded ? "Weniger anzeigen  ⌃"
                        : "Weitere Aktionen  ⌄");
            });
            margin(sheet, expand, 6);
            margin(sheet, extras, 0);
        }
        ScrollView sheetScroll = new ScrollView(this);
        sheetScroll.setVerticalScrollBarEnabled(false);
        sheetScroll.setFillViewport(false);
        sheetScroll.addView(sheet);
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sheetParams.setMargins(dp(12), 0, dp(12), dp(12));
        canvas.addView(sheetScroll, sheetParams);

        // The OpenStreetMap attribution is mandatory, but must never cover the
        // parking card. Anchor it dynamically just ABOVE the card's real height.
        // The card expands/collapses and varies with font size, so fixed 318dp
        // margins are incorrect (particularly on small phones like HyperOS).
        TextView attribution = text("© OpenStreetMap-Mitwirkende", 10, MUTED, false);
        attribution.setGravity(Gravity.CENTER);
        attribution.setContentDescription("Kartenquelle OpenStreetMap – Lizenzinformationen öffnen");
        attribution.setPadding(dp(7), dp(3), dp(7), dp(3));
        attribution.setBackground(shape(Color.argb(200, 255, 255, 255), 8));
        attribution.setOnClickListener(v -> openUrl("https://www.openstreetmap.org/copyright"));
        FrameLayout.LayoutParams attributionParams = new FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.LEFT);
        attributionParams.leftMargin = dp(14);
        canvas.addView(attribution, attributionParams);
        Runnable repositionAttribution = () -> {
            if (canvas.getHeight() <= 0) return;
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) attribution.getLayoutParams();
            int aboveSheet = sheetScroll.getHeight() + dp(22);
            // In very small windows the sheet may cover most of the map:
            // move the attribution into the remaining upper map area instead.
            if (canvas.getHeight() - aboveSheet < dp(178)) {
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.topMargin = dp(157);
                params.bottomMargin = 0;
            } else {
                params.gravity = Gravity.BOTTOM | Gravity.LEFT;
                params.bottomMargin = aboveSheet;
                params.topMargin = 0;
            }
            attribution.setLayoutParams(params);
        };
        sheetScroll.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        repositionAttribution.run());
        canvas.post(repositionAttribution);
        canvas.post(this::showInitialPosition);
    }

    private void showParkingMore(ParkingSpot spot) {
        new AlertDialog.Builder(this).setTitle("Dein Parkplatz")
                .setItems(new String[]{
                        "Neuen Parkplatz speichern",
                        "Parkhausangaben bearbeiten",
                        "Foto verwalten",
                        "Standort teilen",
                        "Parkverlauf öffnen",
                        "Parkplatz löschen"
                }, (dialog, which) -> {
                    switch (which) {
                        case 0: saveCurrent(); break;
                        case 1: editGarage(spot); break;
                        case 2: photo(spot); break;
                        case 3: share(spot); break;
                        case 4: showTab(TAB_HISTORY); break;
                        case 5: confirmDeleteParking(spot, TAB_MAP); break;
                    }
                }).show();
    }

    private void confirmDeleteParking(ParkingSpot selected, int nextTab) {
        if (selected == null || store.getById(selected.id) == null) {
            toast("Dieser Parkplatz ist bereits gelöscht.");
            showTab(nextTab);
            return;
        }
        ParkingSpot current = store.active();
        boolean isCurrent = current != null && current.id == selected.id;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Parkplatz löschen?")
                .setMessage(isCurrent
                        ? "Dieser Parkplatz wird dauerhaft aus deiner Liste entfernt. "
                                + "Eine laufende Parkuhr wird beendet. "
                                + "Andere gespeicherte Parkplätze bleiben erhalten."
                        : "Dieser Parkplatz wird dauerhaft aus deinem Verlauf entfernt. "
                                + "Andere gespeicherte Parkplätze bleiben erhalten.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (d, which) -> {
                    // Check again at confirmation: automation can change the active spot.
                    ParkingSpot latest = store.getById(selected.id);
                    if (latest == null) {
                        toast("Parkplatz ist bereits gelöscht.");
                        showTab(nextTab);
                        return;
                    }
                    ParkingSpot activeNow = store.active();
                    if (activeNow != null && activeNow.id == selected.id)
                        AlarmScheduler.cancel(this);
                    store.delete(selected.id);
                    ParkingWidget.updateAll(this);
                    parkingDetailsExpanded = false;
                    showTab(nextTab);
                    toast("Parkplatz gelöscht");
                })
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(Color.rgb(173, 57, 63)));
        dialog.show();
    }

    private void showInitialPosition() {
        if (currentTab != TAB_MAP || deviceLocation != null || store.active() != null) return;
        if (!LocationGateway.hasPermission(this)) return;
        Location cached = LocationGateway.recent(this, 120_000L, 250f);
        if (cached != null) {
            jumpToLocation(cached, false);
            return;
        }
        // The opening screen should not block on a GPS fix.
        // The explicit location button starts a fresh fix on demand.
    }

    private void showHistory(LinearLayout content) {
        line(content, "Deine Parkplätze", 26, WHITE, true, 10);
        List<ParkingSpot> history = store.history();
        line(content, history.isEmpty() ? "Hier findest du später deine gespeicherten Orte."
                : history.size() + (history.size() == 1 ? " Parkplatz" : " Parkplätze")
                        + " · nur auf diesem Gerät", 13, MUTED, false, 5);
        if (history.isEmpty()) {
            LinearLayout empty = card();
            line(empty, "Noch kein Parkplatz gespeichert", 18, WHITE, true, 0);
            line(empty, "Speichere deinen Standort, sobald du geparkt hast.", 14, MUTED, false, 8);
            actionLine(empty, "Zur Karte", true, () -> showTab(TAB_MAP));
            margin(content, empty, 18);
            return;
        }
        ParkingSpot active = store.active();
        for (ParkingSpot spot : history) {
            LinearLayout item = card();
            boolean current = active != null && active.id == spot.id;
            line(item, current ? "●  AKTUELLES FAHRZEUG" : "PARKPLATZ",
                    11, current ? ACCENT_DARK : MUTED, true, 0);
            line(item, format(spot.parkedAt), 18, WHITE, true, 5);
            String location = GarageLabels.summary(spot.floor, spot.space);
            if (!location.isEmpty()) line(item, location, 13, MUTED, false, 5);
            if (!spot.note.isEmpty()) line(item, spot.note, 13, MUTED, false, 4);
            if (ParkingAccuracy.SOURCE_APPROXIMATE.equals(spot.source))
                line(item, "⌖  Ungefähre Position", 12, MUTED, false, 5);
            LinearLayout buttons = row();
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(47), 1);
            TextView route = action("➤  Route", true, () -> navigate(spot));
            route.setTextSize(13);
            buttons.addView(route, left);
            LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(47), 1);
            right.leftMargin = dp(9);
            TextView more = action("···  Aktionen", false, () -> showHistoryActions(spot, current));
            more.setTextSize(13);
            buttons.addView(more, right);
            margin(item, buttons, 12);
            margin(content, item, 12);
        }
    }

    private void showHistoryActions(ParkingSpot spot, boolean current) {
        String[] options = {
                current ? "Aktueller Parkplatz" : "Als aktuellen Parkplatz wählen",
                "Parkhaus / Stellplatz",
                "Foto verwalten",
                "Parkplatz teilen",
                "Parkplatz löschen"
        };
        new AlertDialog.Builder(this).setTitle("Parkplatzaktionen")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (!current) {
                                store.setActiveId(spot.id);
                                ParkingWidget.updateAll(this);
                                showTab(TAB_HISTORY);
                            }
                            break;
                        case 1: editGarage(spot); break;
                        case 2: photo(spot); break;
                        case 3: share(spot); break;
                        case 4:
                            confirmDeleteParking(spot, TAB_HISTORY);
                            break;
                    }
                }).show();
    }

    private String installedVersion() {
        try {
            android.content.pm.PackageInfo packageInfo =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName == null ? "unbekannt" : packageInfo.versionName;
        } catch (Exception exception) {
            return "unbekannt";
        }
    }

    private void showSettings(LinearLayout content) {
        line(content, "Einstellungen", 24, WHITE, true, 6);
        line(content, "Alles lokal. Alle Funktionen in deiner Hand.", 14, MUTED, false, 8);
        LinearLayout intro = card();
        line(intro, "ERSTE SCHRITTE", 11, ACCENT_DARK, true, 0);
        line(intro, "RoyalPark kennenlernen", 18, WHITE, true, 7);
        line(intro, "Die kurze Einführung kannst du jederzeit erneut ansehen.", 13, MUTED, false, 6);
        actionLine(intro, "Einführung erneut starten", false, () -> {
            prefs.edit().putBoolean("onboarding_complete", false).apply();
            startActivity(new Intent(this, OnboardingActivity.class));
        });
        margin(content, intro, 13);
        LinearLayout bluetooth = card();
        line(bluetooth, "BLUETOOTH-AUTOMATIK", 12, ACCENT, true, 0);
        line(bluetooth, "Parkplatz automatisch merken", 19, WHITE, true, 8);
        line(bluetooth, "Wenn sich dein Handy von einem ausgewählten, gekoppelten Auto-Bluetoothgerät trennt, wird der aktuelle Standort gespeichert.",
                14, MUTED, false, 7);
        line(bluetooth, "Ausgewähltes Gerät: " + prefs.getString("auto_name", "Keines"),
                14, WHITE, false, 13);
        actionLine(bluetooth, "Bluetoothgerät auswählen", false, this::chooseBluetooth);
        boolean enabled = prefs.getBoolean("auto_enabled", false);
        actionLine(bluetooth, enabled ? "■  Überwachung stoppen" : "▶  Überwachung starten", true,
                enabled ? this::stopAuto : this::startAuto);
        line(bluetooth, "Erfordert Standort- und gegebenenfalls Bluetooth-Berechtigung sowie eine sichtbare Dienstmeldung. Nach Neustart erneut einschalten.",
                12, MUTED, false, 10);
        margin(content, bluetooth, 18);
        LinearLayout movement = card();
        line(movement, "BEWEGUNGSERKENNUNG", 12, ACCENT_DARK, true, 0);
        line(movement, "Beim Aussteigen automatisch speichern", 18, WHITE, true, 8);
        line(movement, "Erkennt über Google Play-Dienste den Wechsel von Autofahrt zu Fußweg. Kann irrtümlich auslösen und benötigt eine aktive Überwachung.",
                14, MUTED, false, 8);
        boolean motion = prefs.getBoolean("motion_enabled", false);
        actionLine(movement, motion ? "✓  Aktiv – ausschalten" : "○  Aktivitätserkennung einschalten",
                false, this::toggleMotion);
        if (motion) {
            line(movement, prefs.getString("motion_status", "Überwachung noch nicht gestartet"),
                    12, MUTED, false, 8);
        }
        line(movement, "Du kannst Bluetooth und Bewegung gemeinsam verwenden. Doppelte Erkennungen werden unterdrückt.",
                12, MUTED, false, 10);
        margin(content, movement, 16);
        LinearLayout backup = card();
        line(backup, "DATENSICHERUNG", 12, ACCENT_DARK, true, 0);
        line(backup, "Parkplätze und Fotos sichern", 18, WHITE, true, 8);
        line(backup, "Die ZIP-Sicherung speichert Positionen, Notizen, Parkhausangaben und erreichbare Fotos. Du wählst den Speicherort selbst. Die Datei ist nicht verschlüsselt und enthält genaue Standortdaten.",
                13, MUTED, false, 9);
        actionLine(backup, "⬇  Sicherung erstellen (.zip)", false, this::exportBackup);
        actionLine(backup, "⬆  Sicherung wiederherstellen", false, this::importBackup);
        line(backup, "Beim Import bleiben vorhandene Parkplätze erhalten. Alte Parkuhren werden aus Sicherheitsgründen nicht neu aktiviert.",
                12, MUTED, false, 8);
        margin(content, backup, 16);
        LinearLayout offlineCard = card();
        line(offlineCard, "OFFLINE-KARTEN", 12, ACCENT_DARK, true, 0);
        line(offlineCard, "Region direkt herunterladen", 18, WHITE, true, 7);
        line(offlineCard, "Vektorkarten aus lizenzierter OpenStreetMap-Kartendatenquelle. Bremen/Bremerhaven, Niedersachsen und weitere Bundesländer direkt installieren.",
                13, MUTED, false, 8);
        RegionMaps.Region currentRegion = RegionMaps.current(this);
        line(offlineCard, currentRegion != null && RegionMaps.downloaded(this, currentRegion)
                ? "Installiert: " + currentRegion.label
                : "Noch keine Vektorkartenregion gewählt.", 13, MUTED, false, 7);
        actionLine(offlineCard, "↓  Deutschland-Region auswählen", true, this::chooseRegion);
        if (currentRegion != null && RegionMaps.downloaded(this, currentRegion)) {
            actionLine(offlineCard, RegionMaps.enabled(this)
                    ? "✓  Regionskarte ist offline aktiviert"
                    : "○  Regionskarte offline aktivieren", false, () -> {
                RegionMaps.enable(this, currentRegion);
                OfflineMaps.setEnabled(this, false);
                showTab(TAB_SETTINGS);
            });
            actionLine(offlineCard, "Regionskarte löschen", false, () ->
                    new AlertDialog.Builder(this)
                            .setTitle("Regionskarte löschen?")
                            .setMessage("Die Karte wird entfernt, Parkplätze bleiben erhalten.")
                            .setNegativeButton("Abbrechen", null)
                            .setPositiveButton("Löschen", (dialog, which) -> {
                                RegionMaps.delete(this, currentRegion);
                                showTab(TAB_SETTINGS);
                            }).show());
        }
        line(offlineCard, "Kartenquelle: Mapsforge / OpenStreetMap-Mitwirkende (ODbL). Routingdaten sind getrennt.",
                12, MUTED, false, 10);
        line(offlineCard, "EXISTIERENDE RASTERKARTEN", 12, ACCENT_DARK, true, 14);
        line(offlineCard, "Karte ohne Internet anzeigen", 18, WHITE, true, 7);
        line(offlineCard, "Installiere ein lizenziertes Rasterkarten-Archiv (.mbtiles, .sqlite, .zip). RoyalPark lädt keine Kartengebiete vom öffentlichen OSM-Server herunter.",
                13, MUTED, false, 8);
        boolean installed = OfflineMaps.hasArchive(this);
        line(offlineCard, installed
                ? "Vorhandene Kartenquelle: " + OfflineMaps.sourceName(this)
                : "Noch keine Offlinekarte importiert.", 13, MUTED, false, 7);
        actionLine(offlineCard, "↓  Offline-Kartenpaket importieren", false, this::importOfflineMap);
        actionLine(offlineCard, OfflineMaps.enabled(this) ? "✓  Offline-Modus ausschalten"
                : "○  Offline-Modus einschalten", false, () -> {
            if (!OfflineMaps.enabled(this) && !OfflineMaps.hasArchive(this)) {
                error("Bitte zuerst ein Offline-Kartenpaket importieren.");
                return;
            }
            OfflineMaps.setEnabled(this, !OfflineMaps.enabled(this));
            showTab(TAB_SETTINGS);
        });
        if (installed) actionLine(offlineCard, "Offline-Kartenpaket entfernen", false,
                () -> new AlertDialog.Builder(this)
                        .setTitle("Offlinekarte entfernen?")
                        .setMessage("Nur das importierte Kartenpaket wird gelöscht, deine Parkplätze bleiben erhalten.")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Entfernen", (d, w) -> {
                            OfflineMaps.remove(this);
                            showTab(TAB_SETTINGS);
                        }).show());
        line(offlineCard, "Eine Mapsforge-Karte funktioniert ohne Netz. Für Offline-Fußwegrouten benötigt RoyalPark zusätzlich BRouter mit heruntergeladenen .rd5-Segmenten.",
                12, MUTED, false, 10);
        actionLine(offlineCard, "Offline-Fußwegrouting (BRouter) einrichten", false,
                this::setupBRouter);
        margin(content, offlineCard, 16);
        LinearLayout privacy = card();
        line(privacy, "DATENSCHUTZ", 12, ACCENT, true, 0);
        line(privacy, "Kein Konto. Kein Tracking. Keine Werbung.", 17, WHITE, true, 7);
        line(privacy, "Parkplätze werden lokal gespeichert. Online-Karten und optionale Valhalla-Fußwegrouten rufen externe Dienste auf. Installierte Offlinekarten sowie BRouter-Routen lassen sich ohne Netz nutzen.",
                14, MUTED, false, 8);
        actionLine(privacy, "Benachrichtigungen erlauben", false, this::askNotifications);
        actionLine(privacy, "App-Berechtigungen öffnen", false, () -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        margin(content, privacy, 16);
        line(content, "RoyalPark · Version " + installedVersion(), 12, MUTED, false, 25);
    }

    private void ensureLocation(Runnable action) {
        if (LocationGateway.hasPermission(this)) action.run();
        else {
            afterPermission = action;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
    }

    private void saveCurrent() {
        ensureLocation(() -> {
            toast("Parkplatz wird gespeichert …");
            LocationGateway.once(this, (location, error) -> {
                if (isFinishing() || isDestroyed()) return;
                if (location == null) { error(error); return; }
                boolean approximate = ParkingAccuracy.approximate(
                        LocationGateway.hasPrecisePermission(this), location.hasAccuracy(),
                        location.hasAccuracy() ? location.getAccuracy() : Float.NaN);
                store.save(location.getLatitude(), location.getLongitude(), "",
                        approximate ? ParkingAccuracy.SOURCE_APPROXIMATE : "MANUAL");
                AlarmScheduler.cancel(this);
                ParkingWidget.updateAll(this);
                deviceLocation = location;
                showTab(TAB_MAP);
                toast(approximate
                        ? "Parkplatz ungefähr gespeichert. Du kannst ihn auf der Karte korrigieren."
                        : "Parkplatz gespeichert");
            });
        });
    }

    private void openLocationPermissionSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); }
        catch (Exception ignored) { toast("Präzise Ortung unter Android-App-Berechtigungen erlauben."); }
    }

    private void showMyLocation() {
        ensureLocation(() -> {
            if (locatingNow) {
                if (deviceLocation != null) jumpToLocation(deviceLocation, false);
                return;
            }
            locatingNow = true;
            if (locationButton != null) locationButton.setAlpha(0.55f);
            setLocationStatus("Suche Standort …");
            Location cached = LocationGateway.recent(this, 120_000L, 250f);
            if (cached != null) {
                jumpToLocation(cached, true);
                setLocationStatus("Letzte Position …");
            } else if (deviceLocation != null) {
                // Don't display an old remembered position as if it were fresh.
                setLocationStatus("GPS wird aktualisiert …");
            }
            LocationGateway.once(this, (location, error) -> {
                locatingNow = false;
                if (locationButton != null) locationButton.setAlpha(1f);
                if (isFinishing() || isDestroyed()) return;
                if (location == null) {
                    if (cached == null) error(error);
                    setLocationStatus(cached == null ? "Kein GPS-Fix" : "Nur letzte Position");
                    return;
                }
                jumpToLocation(location, true);
                String accuracy = location.hasAccuracy()
                        ? "±" + Math.round(location.getAccuracy()) + " m"
                        : "ermittelt";
                setLocationStatus("Standort " + accuracy);
            });
        });
    }

    private void jumpToLocation(Location location, boolean animate) {
        updateMyMarker(location);
        if (map == null || currentTab != TAB_MAP) return;
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        double targetZoom = MapFraming.myPositionZoom(
                location.hasAccuracy() ? location.getAccuracy() : Float.NaN);
        // Avoid re-decoding the entire new zoom level on every GPS refresh.
        if (MapPerformance.shouldZoom(map.getZoomLevelDouble(), targetZoom))
            map.getController().setZoom(targetZoom);
        if (animate) map.getController().animateTo(point);
        else map.getController().setCenter(point);
        map.invalidate();
    }

    private void updateMyMarker(Location location) {
        if (location == null) return;
        deviceLocation = location;
        updateParkingDistance();
        if (map == null || currentTab != TAB_MAP) return;
        if (myLocationMarker == null) {
            myLocationMarker = new Marker(map);
            myLocationMarker.setTitle("Mein Standort");
            myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        }
        myLocationMarker.setPosition(new GeoPoint(location.getLatitude(), location.getLongitude()));
        if (!map.getOverlays().contains(myLocationMarker))
            map.getOverlays().add(myLocationMarker);
        map.invalidate();
    }

    private void updateParkingDistance() {
        if (parkingDistanceLine == null) return;
        ParkingSpot spot = store == null ? null : store.active();
        if (spot == null || deviceLocation == null) {
            parkingDistanceLine.setText("⌖  Entfernung zum Auto anzeigen");
            return;
        }
        long ageMs = (android.os.SystemClock.elapsedRealtimeNanos()
                - deviceLocation.getElapsedRealtimeNanos()) / 1_000_000L;
        if (ageMs < 0 || ageMs > 120_000L) {
            parkingDistanceLine.setText("⌖  Standort aktualisieren, um die Entfernung zu sehen");
            return;
        }
        double meters = NavigationMath.distanceMeters(
                spot.latitude, spot.longitude,
                deviceLocation.getLatitude(), deviceLocation.getLongitude());
        parkingDistanceLine.setText("⌖  " + ParkingPresentation.distanceText(
                meters, deviceLocation.hasAccuracy() ? deviceLocation.getAccuracy() : Float.NaN,
                LocationGateway.hasPrecisePermission(this),
                ParkingAccuracy.SOURCE_APPROXIMATE.equals(spot.source)) + " · beide anzeigen");
    }

    private void focusParked() {
        ParkingSpot spot = store.active();
        if (spot == null) {
            toast("Noch kein Parkplatz gespeichert.");
            return;
        }
        if (currentTab != TAB_MAP) {
            showTab(TAB_MAP);
        }
        if (map == null) return;
        map.getController().setZoom(MapFraming.CAR_ZOOM);
        map.getController().animateTo(new GeoPoint(spot.latitude, spot.longitude));
        setLocationStatus("Parkplatz im Fokus");
    }

    private void focusBoth() {
        ParkingSpot spot = store.active();
        if (spot == null) {
            showMyLocation();
            return;
        }
        ensureLocation(() -> {
            Location recent = LocationGateway.recent(this, 120_000L, 250f);
            if (recent != null) {
                focusBothWith(spot, recent);
                return;
            }
            setLocationStatus("Ermittle deinen Standort …");
            LocationGateway.once(this, (location, error) -> {
                if (isFinishing() || isDestroyed()) return;
                if (location == null) { error(error); return; }
                focusBothWith(spot, location);
            });
        });
    }

    private void focusBothWith(ParkingSpot spot, Location location) {
        if (currentTab != TAB_MAP) showTab(TAB_MAP);
        updateMyMarker(location);
        if (map == null) return;
        MapView currentMap = map;
        GeoPoint parked = new GeoPoint(spot.latitude, spot.longitude);
        GeoPoint here = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (MapFraming.nearby(spot.latitude, spot.longitude,
                location.getLatitude(), location.getLongitude())) {
            currentMap.getController().setZoom(MapFraming.MAX_BOTH_ZOOM);
            currentMap.getController().animateTo(parked);
        } else {
            MapFraming.Frame bounds = MapFraming.aroundBoth(
                    spot.latitude, spot.longitude, location.getLatitude(), location.getLongitude());
            currentMap.post(() -> {
                if (currentTab == TAB_MAP && map == currentMap) {
                    currentMap.zoomToBoundingBox(new BoundingBox(
                            bounds.north, bounds.east, bounds.south, bounds.west),
                            true, dp(72), MapFraming.MAX_BOTH_ZOOM, 450L);
                }
            });
        }
        double meters = NavigationMath.distanceMeters(
                spot.latitude, spot.longitude, here.getLatitude(), here.getLongitude());
        setLocationStatus("Auto: " + NavigationMath.formatDistance(meters));
    }

    private void setLocationStatus(String message) {
        if (locationStatus == null) return;
        locationStatus.setText(message);
        locationStatus.setVisibility(View.VISIBLE);
    }

    private void chooseRegion() {
        RegionMaps.Region[] regions = RegionMaps.AVAILABLE;
        String[] labels = new String[regions.length];
        for (int i=0; i<regions.length; i++) {
            labels[i] = regions[i].display() + (RegionMaps.downloaded(this, regions[i]) ? " · installiert" : "");
        }
        // Android AlertDialog cannot show a message and an item list together reliably:
        // setMessage() masks setItems() on some themes (observed on Xiaomi/HyperOS).
        new AlertDialog.Builder(this)
                .setTitle("Offline-Kartenregion")
                .setItems(labels, (dialog, which) -> {
                    RegionMaps.Region region = regions[which];
                    if (RegionMaps.downloaded(this, region)) {
                        RegionMaps.enable(this, region);
                        OfflineMaps.setEnabled(this, false);
                        showTab(TAB_MAP);
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle(region.label + " herunterladen?")
                            .setMessage("Download: ca. " + region.size + ". Eine Internetverbindung und entsprechender freier Speicher sind erforderlich. Kartenlizenz: OpenStreetMap ODbL.")
                            .setNegativeButton("Abbrechen", null)
                            .setPositiveButton("Herunterladen", (d, w) -> downloadRegion(region))
                            .show();
                })
                .setNegativeButton("Schließen", null)
                .show();
    }

    private void downloadRegion(RegionMaps.Region region) {
        if (regionDownloadRunning) { toast("Es läuft bereits ein Download."); return; }
        regionDownloadRunning = true;
        android.widget.ProgressBar progress = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setPadding(dp(20), dp(18), dp(20), dp(18));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Lade " + region.label)
                .setMessage("Karte wird auf dem Gerät gespeichert …")
                .setView(progress)
                .setCancelable(false)
                .create();
        dialog.show();
        backupExecutor.execute(() -> {
            try {
                RegionMaps.download(getApplicationContext(), region, percent ->
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) progress.setProgress(percent);
                        }));
                runOnUiThread(() -> {
                    regionDownloadRunning = false;
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    RegionMaps.enable(this, region);
                    OfflineMaps.setEnabled(this, false);
                    showTab(TAB_MAP);
                    toast(region.label + " offline verfügbar.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    regionDownloadRunning = false;
                    if (isFinishing() || isDestroyed()) return;
                    dialog.dismiss();
                    new AlertDialog.Builder(this)
                            .setTitle("Download fehlgeschlagen")
                            .setMessage(error.getMessage())
                            .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private void setupBRouter() {
        boolean installed = OfflineWalkingRouter.isInstalled(this);
        new AlertDialog.Builder(this)
                .setTitle("Offline-Fußwegrouting")
                .setMessage("RoyalPark kann BRouter auf diesem Smartphone als lokalen Routingdienst verwenden. " +
                        "Dazu werden die kostenlose BRouter-App und deren regionale Routendaten (.rd5) benötigt. " +
                        "Diese Routendaten müssen derzeit im BRouter-Downloadmanager geladen werden. " +
                        "Die RoyalPark-Kartenregion allein enthält keinen vollständigen Routinggraphen. " +
                        (installed ? "\n\nBRouter ist installiert – im Downloadmanager passende Region auswählen." :
                                "\n\nBRouter ist noch nicht installiert."))
                .setNegativeButton("Schließen", null)
                .setPositiveButton(installed ? "BRouter öffnen" : "BRouter finden", (d, w) -> {
                    try {
                        Intent launch = getPackageManager().getLaunchIntentForPackage("btools.routingapp");
                        if (launch != null) startActivity(launch);
                        else openUrl("https://f-droid.org/packages/btools.routingapp/");
                    } catch (Exception ex) { error("BRouter konnte nicht geöffnet werden."); }
                }).show();
    }

    private void importOfflineMap() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent, REQ_OFFLINE_MAP); }
        catch (Exception ex) { error("Dateiauswahl nicht verfügbar."); }
    }

    private void installOfflineMap(Uri uri) {
        toast("Offlinekarte wird geprüft und importiert …");
        backupExecutor.execute(() -> {
            try {
                String source = OfflineMaps.importArchive(getApplicationContext(), uri);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showTab(TAB_MAP);
                    new AlertDialog.Builder(this)
                            .setTitle("Offlinekarte installiert")
                            .setMessage("Kartenquelle: " + source
                                    + "\nDie Karte wird ohne Netzverbindung angezeigt, sofern das Archiv Kacheln für diese Region und Zoomstufe enthält. Online-Routing funktioniert dabei nicht.")
                            .setPositiveButton("OK", null).show();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        new AlertDialog.Builder(this)
                                .setTitle("Kartenimport fehlgeschlagen")
                                .setMessage(failure.getMessage())
                                .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private void editNote(ParkingSpot spot) {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setText(spot.note);
        input.setHint("Etage, Reihe, Parkhaus …");
        LinearLayout wrap = column();
        wrap.setPadding(dp(20), dp(6), dp(20), 0);
        wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Notiz zum Parkplatz")
                .setView(wrap).setNegativeButton("Abbrechen", null)
                .setPositiveButton("Speichern", (d, which) -> {
                    store.setNote(spot.id, input.getText().toString().trim());
                    ParkingWidget.updateAll(this);
                    showTab(currentTab);
                }).show();
    }

    private void editGarage(ParkingSpot spot) {
        LinearLayout layout = column();
        layout.setPadding(dp(18), dp(8), dp(18), dp(4));
        TextView floorLabel = text("Ebene / Etage", 14, MUTED, true);
        layout.addView(floorLabel);
        EditText floor = new EditText(this);
        floor.setSingleLine(true);
        floor.setHint("z. B. -2 oder P3");
        floor.setText(spot.floor);
        layout.addView(floor);
        TextView spaceLabel = text("Stellplatz / Bereich", 14, MUTED, true);
        layout.addView(spaceLabel);
        EditText space = new EditText(this);
        space.setSingleLine(true);
        space.setHint("z. B. B-17");
        space.setText(spot.space);
        layout.addView(space);
        new AlertDialog.Builder(this)
                .setTitle("Parkhausangaben")
                .setView(layout)
                .setNegativeButton("Abbrechen", null)
                .setNeutralButton("Leeren", (d, w) -> {
                    store.setGarage(spot.id, "", "");
                    showTab(currentTab);
                })
                .setPositiveButton("Speichern", (d, w) -> {
                    store.setGarage(spot.id, floor.getText().toString(), space.getText().toString());
                    showTab(currentTab);
                }).show();
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "RoyalPark-Backup-" + System.currentTimeMillis() + ".zip");
        try { startActivityForResult(intent, REQ_BACKUP_CREATE); }
        catch (Exception error) { error("Kein Dokumenten-Manager gefunden."); }
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent, REQ_BACKUP_IMPORT); }
        catch (Exception error) { error("Kein Dokumenten-Manager gefunden."); }
    }

    private void exportTo(Uri destination) {
        toast("Sicherung wird erstellt …");
        backupExecutor.execute(() -> {
            try (ParkingStore db = new ParkingStore(getApplicationContext())) {
                ParkingArchive.Result result = ParkingArchive.exportTo(
                        getApplicationContext(), db, destination);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    new AlertDialog.Builder(this)
                            .setTitle("Sicherung erstellt")
                            .setMessage(result.entries + " Parkplätze und " + result.photos
                                    + " Fotos gespeichert." + (result.skippedPhotos > 0
                                    ? "\n" + result.skippedPhotos + " Fotos waren nicht mehr zugänglich und fehlen im Archiv." : "")
                                    + "\n\nBewahre die ZIP-Datei sicher auf. Sie enthält genaue Standortdaten.")
                            .setPositiveButton("OK", null).show();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        error("Sicherung fehlgeschlagen: " + failure.getMessage());
                });
            }
        });
    }

    private void restoreFrom(Uri source) {
        new AlertDialog.Builder(this)
                .setTitle("Sicherung wiederherstellen?")
                .setMessage("Die Daten dieser ZIP-Sicherung werden mit deinen vorhandenen Parkplätzen zusammengeführt. Bereits vorhandene Einträge bleiben erhalten, Duplikate werden übersprungen. Parkuhren müssen anschließend erneut eingestellt werden.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Importieren", (d, w) -> {
                    toast("Sicherung wird geprüft und importiert …");
                    backupExecutor.execute(() -> {
                        try (ParkingStore db = new ParkingStore(getApplicationContext())) {
                            ParkingArchive.Result result = ParkingArchive.restoreFrom(
                                    getApplicationContext(), db, source);
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                ParkingWidget.updateAll(this);
                                showTab(TAB_HISTORY);
                                new AlertDialog.Builder(this)
                                        .setTitle("Import abgeschlossen")
                                        .setMessage(result.entries + " neue Parkplätze importiert. "
                                                + result.photos + " Bilder aus der Sicherung gelesen.")
                                        .setPositiveButton("OK", null).show();
                            });
                        } catch (Exception failure) {
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed())
                                    error("Import fehlgeschlagen: " + failure.getMessage());
                            });
                        }
                    });
                }).show();
    }

    private void timer(ParkingSpot spot) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle("Benachrichtigungen erforderlich")
                    .setMessage("Ohne Benachrichtigungsberechtigung kann RoyalPark dich nicht zuverlässig an die Parkzeit erinnern. Erlaube sie und öffne danach die Parkuhr erneut.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Berechtigung anfordern", (d, w) -> askNotifications()).show();
            return;
        }
        String[] choices = {"15 Minuten", "30 Minuten", "60 Minuten", "90 Minuten",
                "2 Stunden", "3 Stunden", "Eigene Dauer …", "Timer ausschalten"};
        final long[] durations = {15, 30, 60, 90, 120, 180};
        new AlertDialog.Builder(this).setTitle("Parkuhr einstellen")
                .setItems(choices, (dialog, which) -> {
                    if (which == 6) { customTimer(spot); return; }
                    setTimerMinutes(spot, which == 7 ? 0 : durations[which]);
                }).show();
    }

    private void customTimer(ParkingSpot spot) {
        EditText minutes = new EditText(this);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setHint("Minuten (1–1440)");
        minutes.setSingleLine(true);
        LinearLayout wrap = column();
        wrap.setPadding(dp(20), dp(4), dp(20), 0);
        wrap.addView(minutes);
        new AlertDialog.Builder(this).setTitle("Eigene Parkdauer")
                .setView(wrap)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Einstellen", (dialog, which) -> {
                    try {
                        long value = Long.parseLong(minutes.getText().toString().trim());
                        if (value < 1 || value > 1440) {
                            error("Bitte 1 bis 1440 Minuten eingeben.");
                        } else setTimerMinutes(spot, value);
                    } catch (NumberFormatException ex) { error("Bitte eine gültige Zahl eingeben."); }
                }).show();
    }

    private void setTimerMinutes(ParkingSpot spot, long minutes) {
        long at = minutes == 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        store.setTimer(spot.id, at);
        AlarmScheduler.schedule(this, spot.id, at);
        ParkingWidget.updateAll(this);
        toast(at == 0 ? "Timer beendet" : "Erinnerung eingestellt (ungefähre Uhrzeit)");
        showTab(currentTab);
    }

    private void photo(ParkingSpot spot) {
        photoSpotId = spot.id;
        boolean hasPhoto = spot.photoUri != null && !spot.photoUri.isEmpty();
        String[] actions = hasPhoto ? new String[]{
                "Foto anzeigen", "Neues Foto aufnehmen", "Aus Galerie auswählen", "Foto entfernen"
        } : new String[]{"Foto aufnehmen", "Aus Galerie auswählen"};
        new AlertDialog.Builder(this).setTitle("Parkplatzfoto")
                .setItems(actions, (dialog, which) -> {
                    if (!hasPhoto) {
                        if (which == 0) takePhoto(); else choosePhoto();
                    } else if (which == 0) {
                        try {
                            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(spot.photoUri));
                            open.setDataAndType(Uri.parse(spot.photoUri), "image/*");
                            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(open);
                        } catch (Exception e) { error("Das Foto konnte nicht geöffnet werden."); }
                    } else if (which == 1) takePhoto();
                    else if (which == 2) choosePhoto();
                    else new AlertDialog.Builder(this)
                            .setTitle("Parkplatzfoto entfernen?")
                            .setMessage("Die Verknüpfung wird gelöscht. Originalfotos in deiner Galerie bleiben erhalten.")
                            .setNegativeButton("Abbrechen", null)
                            .setPositiveButton("Entfernen", (d, w) -> {
                                store.setPhoto(spot.id, "");
                                ParkingWidget.updateAll(this);
                                showTab(currentTab);
                            }).show();
                }).show();
    }

    private void choosePhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PHOTO);
    }

    private void takePhoto() {
        try {
            File folder = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (folder == null) { error("Fotospeicher nicht verfügbar."); return; }
            File file = File.createTempFile("royalpark_", ".jpg", folder);
            pendingCameraFile = file.getAbsolutePath();
            pendingCameraUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (intent.resolveActivity(getPackageManager()) == null) {
                file.delete();
                pendingCameraFile = null;
                pendingCameraUri = null;
                error("Keine Kamera-App installiert.");
                return;
            }
            startActivityForResult(intent, REQ_CAMERA);
        } catch (IOException | IllegalArgumentException ex) {
            error("Kamera konnte nicht geöffnet werden.");
        }
    }

    private void navigate(ParkingSpot spot) {
        ensureLocation(() -> {
            Intent intent = new Intent(this, NavigateActivity.class);
            intent.putExtra("latitude", spot.latitude);
            intent.putExtra("longitude", spot.longitude);
            startActivity(intent);
        });
    }

    private void share(ParkingSpot spot) {
        String link = ParkingLogic.osmLink(spot.latitude, spot.longitude);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "Mein Parkplatz: " + link);
        startActivity(Intent.createChooser(intent, "Parkplatz teilen"));
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception ex) { error("Kein Browser oder Kartenprogramm gefunden"); }
    }

    private void chooseBluetooth() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            afterPermission = this::chooseBluetooth;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            error("Bitte Bluetooth einschalten und das Autoradio koppeln.");
            return;
        }
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded.isEmpty()) { error("Keine gekoppelten Bluetoothgeräte gefunden."); return; }
            List<BluetoothDevice> devices = new ArrayList<>(bonded);
            String[] labels = new String[devices.size()];
            for (int i = 0; i < devices.size(); i++) {
                String name = devices.get(i).getName();
                labels[i] = (name == null ? "Unbenanntes Gerät" : name) + " (" + devices.get(i).getAddress() + ")";
            }
            new AlertDialog.Builder(this).setTitle("Auto-Bluetooth auswählen")
                    .setItems(labels, (d, index) -> {
                        BluetoothDevice device = devices.get(index);
                        prefs.edit().putString("auto_mac", device.getAddress())
                                .putString("auto_name", device.getName() == null ? device.getAddress() : device.getName())
                                .apply();
                        if (prefs.getBoolean("auto_enabled", false)) stopAuto();
                        showTab(TAB_SETTINGS);
                    }).show();
        } catch (SecurityException ex) {
            error("Bluetoothberechtigung fehlt");
        }
    }

    private void toggleMotion() {
        boolean currentlyEnabled = prefs.getBoolean("motion_enabled", false);
        if (currentlyEnabled) {
            prefs.edit().putBoolean("motion_enabled", false)
                    .putString("motion_status", "").apply();
            if (prefs.getBoolean("auto_enabled", false)) stopAuto();
            showTab(TAB_SETTINGS);
            return;
        }
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
            afterPermission = () -> {
                prefs.edit().putBoolean("motion_enabled", true).apply();
                if (prefs.getBoolean("auto_enabled", false)) stopAuto();
                showTab(TAB_SETTINGS);
                toast("Bewegungserkennung aktiviert. Überwachung noch starten.");
            };
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ_MOTION);
            return;
        }
        prefs.edit().putBoolean("motion_enabled", true).apply();
        if (prefs.getBoolean("auto_enabled", false)) stopAuto();
        showTab(TAB_SETTINGS);
        toast("Bewegungserkennung ausgewählt. Überwachung noch starten.");
    }

    private void startAuto() {
        if (prefs.getString("auto_mac", "").isEmpty() &&
                !prefs.getBoolean("motion_enabled", false)) {
            error("Bluetoothgerät auswählen oder Bewegungserkennung aktivieren.");
            return;
        }
        ensureLocation(() -> {
            if (!prefs.getString("auto_mac", "").isEmpty()
                    && Build.VERSION.SDK_INT >= 31
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                afterPermission = this::startAuto;
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
                return;
            }
            if (prefs.getBoolean("motion_enabled", false)
                    && Build.VERSION.SDK_INT >= 29
                    && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
                afterPermission = this::startAuto;
                requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ_MOTION);
                return;
            }
            try {
                prefs.edit().putBoolean("auto_enabled", true).apply();
                startForegroundService(new Intent(this, AutoParkService.class));
                showTab(TAB_SETTINGS);
                toast("Überwachung eingeschaltet");
            } catch (Exception ex) {
                prefs.edit().putBoolean("auto_enabled", false).apply();
                error("Dienst konnte nicht gestartet werden: " + ex.getClass().getSimpleName());
            }
        });
    }

    private void stopAuto() {
        prefs.edit().putBoolean("auto_enabled", false).apply();
        stopService(new Intent(this, AutoParkService.class));
        showTab(TAB_SETTINGS);
        toast("Überwachung ausgeschaltet");
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        } else toast("Benachrichtigungen sind bereits erlaubt");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = false;
        for (int result : results) if (result == PackageManager.PERMISSION_GRANTED) granted = true;
        if ((requestCode == REQ_LOCATION || requestCode == REQ_BT || requestCode == REQ_MOTION) && granted) {
            Runnable next = afterPermission;
            afterPermission = null;
            if (next != null) next.run();
        } else if ((requestCode == REQ_LOCATION || requestCode == REQ_BT || requestCode == REQ_MOTION) && !granted) {
            afterPermission = null;
            error("Berechtigung nicht erteilt.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OFFLINE_MAP) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                installOfflineMap(data.getData());
            return;
        }
        if (requestCode == REQ_BACKUP_CREATE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                exportTo(data.getData());
            return;
        }
        if (requestCode == REQ_BACKUP_IMPORT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                restoreFrom(data.getData());
            return;
        }
        if (requestCode == REQ_PHOTO) {
            if (resultCode == RESULT_OK && data != null
                    && data.getData() != null && photoSpotId > 0) {
                Uri uri = data.getData();
                try { getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (SecurityException ignored) { }
                store.setPhoto(photoSpotId, uri.toString());
                showTab(currentTab);
            }
            photoSpotId = -1;
        } else if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && pendingCameraUri != null && photoSpotId > 0) {
                store.setPhoto(photoSpotId, pendingCameraUri.toString());
                showTab(currentTab);
            } else if (pendingCameraFile != null) {
                new File(pendingCameraFile).delete();
            }
            pendingCameraUri = null;
            pendingCameraFile = null;
            photoSpotId = -1;
        }
    }

    private String format(long milliseconds) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT,
                Locale.GERMANY).format(new Date(milliseconds));
    }

    private void error(String message) { toast(message == null ? "Unbekannter Fehler" : message); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override protected void onResume() {
        super.onResume();
        if (body != null && store != null) {
            // showTab() reuses the existing MapView on the map tab and refreshes
            // just the selected non-map page when returning from Android settings.
            showTab(currentTab);
        }
    }

    @Override protected void onPause() {
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        backupExecutor.shutdown();
        if (map != null) {
            map.onDetach();
            map = null;
        }
        mapScreen = null;
        subPage = null;
        if (store != null) store.close();
        super.onDestroy();
    }
}

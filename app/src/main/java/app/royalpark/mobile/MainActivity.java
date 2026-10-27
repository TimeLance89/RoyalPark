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
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Original, clean-room implementation of a car parking helper.
 * It contains none of the third-party application's source code, assets or identifiers.
 */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 21, 30);
    private static final int CARD = Color.rgb(27, 36, 48);
    private static final int CARD_BRIGHT = Color.rgb(35, 48, 62);
    private static final int ACCENT = Color.rgb(86, 215, 193);
    private static final int WHITE = Color.rgb(244, 249, 251);
    private static final int MUTED = Color.rgb(162, 179, 190);
    private static final int REQ_LOCATION = 100;
    private static final int REQ_BT = 101;
    private static final int REQ_NOTIFICATIONS = 102;
    private static final int REQ_PHOTO = 103;
    private static final int REQ_CAMERA = 104;
    private static final int TAB_MAP = 0;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_SETTINGS = 2;

    private ParkingStore store;
    private SharedPreferences prefs;
    private LinearLayout mainRoot;
    private FrameLayout body;
    private LinearLayout bottomBar;
    private MapView map;
    private Location deviceLocation;
    private Runnable afterPermission;
    private long photoSpotId = -1;
    private Uri pendingCameraUri;
    private String pendingCameraFile;
    private int currentTab = TAB_MAP;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        store = new ParkingStore(this);
        if (state != null) {
            photoSpotId = state.getLong("photoSpotId", -1);
            pendingCameraFile = state.getString("pendingCameraFile");
            String uri = state.getString("pendingCameraUri");
            if (uri != null) pendingCameraUri = Uri.parse(uri);
        }
        prefs = getSharedPreferences("royalpark", MODE_PRIVATE);
        // No tracking SDK and no analytics. Map tiles are fetched only for visible viewport.
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("RoyalPark/0.1 (app.royalpark.mobile)");
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
        handleQuickSave(getIntent());
    }

    private void handleQuickSave(Intent intent) {
        if (intent != null && ParkingWidget.QUICK_SAVE.equals(intent.getAction())) {
            intent.setAction(null);
            mainRoot.post(this::saveCurrent);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleQuickSave(intent);
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
        view.setBackground(shape(CARD, 20));
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
        TextView button = text(caption, 15, primary ? BG : WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(13), dp(16), dp(13));
        button.setBackground(shape(primary ? ACCENT : CARD_BRIGHT, 14));
        button.setOnClickListener(v -> task.run());
        return button;
    }

    private void actionLine(LinearLayout parent, String title, boolean primary, Runnable task) {
        margin(parent, action(title, primary, task), 12);
    }

    private void header() {
        LinearLayout header = row();
        header.setPadding(dp(20), dp(15), dp(20), dp(16));
        LinearLayout texts = column();
        texts.addView(text("ROYALPARK", 13, ACCENT, true));
        TextView heading = text("Dein Auto. Dein Weg.", 22, WHITE, true);
        margin(texts, heading, 3);
        header.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView insignia = text("P", 22, ACCENT, true);
        insignia.setGravity(Gravity.CENTER);
        insignia.setBackground(shape(CARD_BRIGHT, 16));
        header.addView(insignia, new LinearLayout.LayoutParams(dp(48), dp(48)));
        mainRoot.addView(header);
    }

    private void showTab(int tab) {
        currentTab = tab;
        if (map != null) {
            map.onPause();
            map.onDetach();
            map = null;
        }
        body.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(16), dp(6), dp(16), dp(20));
        scroll.addView(content);
        body.addView(scroll);
        if (tab == TAB_MAP) showMap(content);
        else if (tab == TAB_HISTORY) showHistory(content);
        else showSettings(content);
        bottomBar.removeAllViews();
        nav("⌖", "Karte", TAB_MAP);
        nav("◷", "Verlauf", TAB_HISTORY);
        nav("⚙", "Einstellungen", TAB_SETTINGS);
    }

    private void nav(String symbol, String label, int tab) {
        TextView item = text(symbol + "\n" + label, 13, currentTab == tab ? ACCENT : MUTED, currentTab == tab);
        item.setGravity(Gravity.CENTER);
        item.setLineSpacing(dp(3), 1f);
        item.setPadding(dp(2), dp(8), dp(2), dp(8));
        item.setOnClickListener(v -> showTab(tab));
        bottomBar.addView(item, new LinearLayout.LayoutParams(0, dp(58), 1));
    }

    private void showMap(LinearLayout content) {
        ParkingSpot active = store.active();
        LinearLayout status = card();
        line(status, active == null ? "NOCH KEIN PARKPLATZ" : "AKTUELLER PARKPLATZ", 12, ACCENT, true, 0);
        line(status, active == null ? "Wo steht dein Auto?" : "Dein Auto wartet hier.", 24, WHITE, true, 8);
        if (active == null) {
            line(status, "Speichere deinen Standort beim Aussteigen.", 14, MUTED, false, 8);
        } else {
            line(status, format(active.parkedAt), 14, MUTED, false, 7);
            line(status, String.format(Locale.GERMANY, "%.5f, %.5f", active.latitude, active.longitude),
                    13, MUTED, false, 5);
            if (!active.note.isEmpty()) line(status, "Notiz: " + active.note, 14, WHITE, false, 8);
            if (active.timerAt > 0) line(status, "Parkuhr: " + format(active.timerAt), 14, ACCENT, true, 8);
        }
        actionLine(status, "＋  Standort jetzt speichern", true, this::saveCurrent);
        if (deviceLocation != null && deviceLocation.hasAccuracy()) {
            line(status, "GPS-Genauigkeit: ±" + Math.round(deviceLocation.getAccuracy()) + " m",
                    12, MUTED, false, 8);
        }
        if (active != null) {
            actionLine(status, "↗  Zu meinem Auto navigieren", false, () -> navigate(active));
            LinearLayout tools = row();
            TextView note = action("Notiz", false, () -> editNote(active));
            TextView timer = action("Parkuhr", false, () -> timer(active));
            LinearLayout.LayoutParams halves = new LinearLayout.LayoutParams(0, -2, 1);
            halves.rightMargin = dp(5);
            LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, -2, 1);
            second.leftMargin = dp(5);
            tools.addView(note, halves);
            tools.addView(timer, second);
            margin(status, tools, 10);
            LinearLayout small = row();
            TextView photo = action("Foto", false, () -> photo(active));
            TextView share = action("Teilen", false, () -> share(active));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            p.rightMargin = dp(5);
            LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, -2, 1);
            q.leftMargin = dp(5);
            small.addView(photo, p);
            small.addView(share, q);
            margin(status, small, 10);
        }
        content.addView(status);
        line(content, "KARTE · Lange auf die Karte tippen, um einen Punkt zu speichern",
                12, MUTED, true, 20);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(shape(CARD_BRIGHT, 20));
        map = new MapView(this);
        map.setTileSource(new XYTileSource("OSM", 0, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.org/"}));
        map.setMultiTouchControls(true);
        // Allow gestures inside the scrollable dashboard without parent interception.
        map.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint point) { return false; }
            @Override public boolean longPressHelper(GeoPoint point) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Parkplatz hier speichern?")
                        .setMessage("Den Punkt " + String.format(Locale.GERMANY, "%.5f, %.5f",
                                point.getLatitude(), point.getLongitude()) + " als Parkplatz markieren?")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Speichern", (dialog, which) -> {
                            ParkingSpot spot = store.save(point.getLatitude(), point.getLongitude(), "", "MAP");
                            AlarmScheduler.cancel(MainActivity.this);
                            ParkingWidget.updateAll(MainActivity.this);
                            showTab(TAB_MAP);
                            editNote(spot);
                        }).show();
                return true;
            }
        }));
        map.getController().setZoom(active != null ? 16.7 : 5.7);
        map.getController().setCenter(active != null
                ? new GeoPoint(active.latitude, active.longitude)
                : deviceLocation != null
                ? new GeoPoint(deviceLocation.getLatitude(), deviceLocation.getLongitude())
                : new GeoPoint(51.1657, 10.4515));
        if (active != null) {
            Marker parked = new Marker(map);
            parked.setTitle("Mein Parkplatz");
            parked.setSnippet(active.note);
            parked.setPosition(new GeoPoint(active.latitude, active.longitude));
            parked.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(parked);
        }
        frame.addView(map, new FrameLayout.LayoutParams(-1, dp(310)));
        TextView attribution = text("© OpenStreetMap-Mitwirkende", 11, BG, false);
        attribution.setPadding(dp(8), dp(4), dp(8), dp(4));
        attribution.setBackground(shape(Color.argb(225, 255, 255, 255), 6));
        FrameLayout.LayoutParams attrParams = new FrameLayout.LayoutParams(-2, -2,
                Gravity.BOTTOM | Gravity.RIGHT);
        attrParams.setMargins(0, 0, dp(9), dp(9));
        frame.addView(attribution, attrParams);
        attribution.setOnClickListener(v -> openUrl("https://www.openstreetmap.org/copyright"));
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, dp(310));
        mapParams.topMargin = dp(9);
        content.addView(frame, mapParams);
        actionLine(content, "⌖  Meinen Standort auf Karte zeigen", false, this::showMyLocation);
        if (active != null && !active.photoUri.isEmpty()) {
            line(content, "PARKFOTO", 12, MUTED, true, 22);
            try {
                ImageView image = new ImageView(this);
                image.setImageURI(Uri.parse(active.photoUri));
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(-1, dp(170));
                photoParams.topMargin = dp(8);
                content.addView(image, photoParams);
            } catch (Exception ignored) {
                line(content, "Foto konnte nicht angezeigt werden.", 13, MUTED, false, 8);
            }
        }
    }

    private void showHistory(LinearLayout content) {
        line(content, "Deine Parkplätze", 24, WHITE, true, 6);
        line(content, "Lokal gespeichert – nur auf diesem Gerät.", 14, MUTED, false, 6);
        List<ParkingSpot> history = store.history();
        if (history.isEmpty()) {
            LinearLayout empty = card();
            line(empty, "Noch kein Eintrag", 20, WHITE, true, 0);
            line(empty, "Speichere deinen ersten Parkplatz über die Karte.", 14, MUTED, false, 9);
            margin(content, empty, 20);
            return;
        }
        ParkingSpot active = store.active();
        for (ParkingSpot spot : history) {
            LinearLayout item = card();
            boolean isActive = active != null && active.id == spot.id;
            line(item, (isActive ? "●  AKTUELL  ·  " : "") + ("BLUETOOTH".equals(spot.source) ? "Automatisch" : "Manuell"),
                    12, isActive ? ACCENT : MUTED, true, 0);
            line(item, format(spot.parkedAt), 17, WHITE, true, 7);
            line(item, String.format(Locale.GERMANY, "%.5f, %.5f", spot.latitude, spot.longitude),
                    13, MUTED, false, 6);
            if (!spot.note.isEmpty()) line(item, spot.note, 14, WHITE, false, 5);
            actionLine(item, "↗  Navigation", false, () -> navigate(spot));
            actionLine(item, isActive ? "Aktueller Parkplatz ✓" : "Als aktuellen Parkplatz wählen", false, () -> {
                store.setActiveId(spot.id);
                ParkingWidget.updateAll(this);
                showTab(TAB_HISTORY);
            });
            actionLine(item, "Eintrag löschen", false, () -> new AlertDialog.Builder(this)
                    .setTitle("Parkplatz löschen?")
                    .setMessage("Dieser Eintrag wird dauerhaft aus deinem Verlauf entfernt.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Löschen", (d, which) -> {
                        if (isActive) AlarmScheduler.cancel(this);
                        store.delete(spot.id);
                        ParkingWidget.updateAll(this);
                        showTab(TAB_HISTORY);
                    }).show());
            margin(content, item, 14);
        }
    }

    private void showSettings(LinearLayout content) {
        line(content, "Einstellungen", 24, WHITE, true, 6);
        line(content, "Alles lokal. Du entscheidest, wann Standort genutzt wird.", 14, MUTED, false, 8);
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
        line(bluetooth, "Erfordert Standort- und Bluetooth-Berechtigung sowie eine dauerhaft sichtbare Dienstmeldung. Nach einem Neustart bitte erneut aktivieren.",
                12, MUTED, false, 10);
        margin(content, bluetooth, 18);
        LinearLayout privacy = card();
        line(privacy, "DATENSCHUTZ", 12, ACCENT, true, 0);
        line(privacy, "Kein Konto. Kein Tracking. Keine Werbung.", 17, WHITE, true, 7);
        line(privacy, "Deine Parkplatzdaten bleiben in der lokalen SQLite-Datenbank. Für Kartenausschnitte werden Kacheln von OpenStreetMap geladen. Bei Auswahl einer Straßenroute wird eine externe Karten-App geöffnet.",
                14, MUTED, false, 8);
        actionLine(privacy, "Benachrichtigungen erlauben", false, this::askNotifications);
        actionLine(privacy, "App-Berechtigungen öffnen", false, () -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        margin(content, privacy, 16);
        line(content, "ROYALPARK 0.2.0  ·  Eigenständiger Neubau", 12, MUTED, false, 25);
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
            toast("Standort wird ermittelt …");
            LocationGateway.once(this, (location, error) -> {
                if (location == null) { error(error); return; }
                ParkingSpot spot = store.save(location.getLatitude(), location.getLongitude(), "", "MANUAL");
                AlarmScheduler.cancel(this);
                ParkingWidget.updateAll(this);
                deviceLocation = location;
                toast("Parkplatz gespeichert");
                showTab(TAB_MAP);
                editNote(spot);
            });
        });
    }

    private void showMyLocation() {
        ensureLocation(() -> LocationGateway.once(this, (location, error) -> {
            if (location == null) { error(error); return; }
            deviceLocation = location;
            if (map != null) {
                map.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));
                map.getController().setZoom(17.0);
            }
        }));
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
        new AlertDialog.Builder(this)
                .setTitle("Parkplatzfoto")
                .setItems(new String[]{"Foto aufnehmen", "Aus Galerie auswählen"}, (dialog, which) -> {
                    if (which == 0) takePhoto(); else choosePhoto();
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

    private void startAuto() {
        if (prefs.getString("auto_mac", "").isEmpty()) {
            error("Bitte zuerst dein Auto-Bluetoothgerät auswählen.");
            return;
        }
        ensureLocation(() -> {
            if (Build.VERSION.SDK_INT >= 31
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                afterPermission = this::startAuto;
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
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
        if ((requestCode == REQ_LOCATION || requestCode == REQ_BT) && granted) {
            Runnable next = afterPermission;
            afterPermission = null;
            if (next != null) next.run();
        } else if ((requestCode == REQ_LOCATION || requestCode == REQ_BT) && !granted) {
            afterPermission = null;
            error("Berechtigung nicht erteilt.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
        if (map != null) map.onResume();
        if (body != null && store != null) showTab(currentTab);
    }

    @Override protected void onPause() {
        if (map != null) map.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (map != null) {
            map.onDetach();
            map = null;
        }
        if (store != null) store.close();
        super.onDestroy();
    }
}

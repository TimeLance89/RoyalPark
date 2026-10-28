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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Original, clean-room implementation of a car parking helper.
 * It contains none of the third-party application's source code, assets or identifiers.
 */
public final class MainActivity extends Activity {
    // Light map-first visual language, guided by screenshots of reference version 1.1.15.
    private static final int BG = Color.rgb(247, 249, 251);
    private static final int CARD = Color.WHITE;
    private static final int CARD_BRIGHT = Color.rgb(235, 246, 250);
    private static final int ACCENT = Color.rgb(88, 196, 213);
    private static final int ACCENT_DARK = Color.rgb(29, 146, 171);
    private static final int WHITE = Color.rgb(30, 48, 62);
    private static final int MUTED = Color.rgb(104, 122, 137);
    private static final int REQ_LOCATION = 100;
    private static final int REQ_BT = 101;
    private static final int REQ_NOTIFICATIONS = 102;
    private static final int REQ_PHOTO = 103;
    private static final int REQ_CAMERA = 104;
    private static final int REQ_MOTION = 105;
    private static final int REQ_BACKUP_CREATE = 106;
    private static final int REQ_BACKUP_IMPORT = 107;
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
        view.setElevation(dp(3));
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
        button.setPadding(dp(16), dp(13), dp(16), dp(13));
        button.setBackground(shape(primary ? ACCENT : CARD_BRIGHT, 14));
        button.setOnClickListener(v -> task.run());
        return button;
    }

    private void actionLine(LinearLayout parent, String title, boolean primary, Runnable task) {
        margin(parent, action(title, primary, task), 12);
    }

    private void header() {
        headerView = row();
        headerView.setBackgroundColor(CARD);
        headerView.setPadding(dp(20), dp(13), dp(20), dp(13));
        TextView back = text("‹", 34, ACCENT_DARK, false);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showTab(TAB_MAP));
        headerView.addView(back, new LinearLayout.LayoutParams(dp(40), dp(43)));
        TextView heading = text("RoyalPark", 20, WHITE, true);
        heading.setGravity(Gravity.CENTER);
        headerView.addView(heading, new LinearLayout.LayoutParams(0, dp(43), 1));
        TextView map = text("⌖", 25, ACCENT_DARK, true);
        map.setGravity(Gravity.CENTER);
        map.setOnClickListener(v -> showTab(TAB_MAP));
        headerView.addView(map, new LinearLayout.LayoutParams(dp(40), dp(43)));
        mainRoot.addView(headerView);
    }

    private void showTab(int tab) {
        currentTab = tab;
        if (map != null) {
            map.onPause();
            map.onDetach();
            map = null;
        }
        body.removeAllViews();
        headerView.setVisibility(tab == TAB_MAP ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(tab == TAB_MAP ? View.GONE : View.VISIBLE);
        if (tab == TAB_MAP) {
            showFullMap();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = column();
        content.setPadding(dp(16), dp(8), dp(16), dp(26));
        scroll.addView(content);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (tab == TAB_HISTORY) showHistory(content);
        else showSettings(content);
        bottomBar.removeAllViews();
        nav("⌖", "Karte", TAB_MAP);
        nav("◷", "Verlauf", TAB_HISTORY);
        nav("⚙", "Einstellungen", TAB_SETTINGS);
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
        body.addView(canvas, new FrameLayout.LayoutParams(-1, -1));

        map = new MapView(this);
        map.setTileSource(new XYTileSource("OSM", 0, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.org/"}));
        map.setMultiTouchControls(true);
        map.getController().setZoom(active != null || deviceLocation != null ? 16.8 : 5.5);
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
                            editNote(spot);
                        }).show();
                return true;
            }
        }));
        if (active != null) {
            Marker parked = new Marker(map);
            parked.setPosition(new GeoPoint(active.latitude, active.longitude));
            parked.setTitle("Mein Parkplatz");
            parked.setSnippet(active.note);
            parked.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(parked);
        }
        canvas.addView(map, new FrameLayout.LayoutParams(-1, -1));

        TextView settings = floatingButton("⚙", 24, () -> showTab(TAB_SETTINGS));
        canvas.addView(settings, floatingAt(47, 47, Gravity.TOP | Gravity.LEFT, 17, 17, 0, 0));
        TextView layers = floatingButton("▱ Karte", 15, () -> {
            new AlertDialog.Builder(this)
                    .setTitle("Kartenansicht")
                    .setMessage("Standardkarte von OpenStreetMap. Halte die Karte gedrückt, um einen Parkplatz zu markieren.")
                    .setPositiveButton("OK", null).show();
        });
        canvas.addView(layers, floatingAt(98, 47, Gravity.TOP | Gravity.RIGHT, 0, 17, 17, 0));

        TextView history = floatingButton("◷", 25, () -> showTab(TAB_HISTORY));
        canvas.addView(history, floatingAt(47, 47, Gravity.TOP | Gravity.LEFT, 17, 76, 0, 0));

        if (deviceLocation != null && deviceLocation.hasAccuracy()) {
            TextView gps = text("GPS ±" + Math.round(deviceLocation.getAccuracy()) + " m", 12, ACCENT_DARK, true);
            gps.setPadding(dp(12), 0, dp(12), 0);
            gps.setGravity(Gravity.CENTER);
            gps.setBackground(shape(Color.WHITE, 18));
            gps.setElevation(dp(3));
            canvas.addView(gps, floatingAt(108, 32, Gravity.TOP | Gravity.LEFT, 17, 137, 0, 0));
        }

        LinearLayout sheet = card();
        sheet.setBackground(shape(Color.WHITE, 24));
        sheet.setElevation(dp(12));
        sheet.setPadding(dp(17), dp(14), dp(17), dp(17));
        if (active == null) {
            line(sheet, "MEIN PARKPLATZ", 12, ACCENT_DARK, true, 0);
            line(sheet, "Wo steht dein Auto?", 23, WHITE, true, 7);
            line(sheet, "Tippe auf Speichern, sobald du geparkt hast.", 13, MUTED, false, 7);
            actionLine(sheet, "＋  Parkplatz speichern", true, this::saveCurrent);
            actionLine(sheet, "⌖  Meinen Standort anzeigen", false, this::showMyLocation);
        } else {
            LinearLayout headline = row();
            LinearLayout heading = column();
            line(heading, "PARKPLATZ", 11, ACCENT_DARK, true, 0);
            line(heading, "Dein Auto steht hier", 21, WHITE, true, 5);
            line(heading, format(active.parkedAt), 12, MUTED, false, 5);
            headline.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
            TextView close = text("⋯", 28, MUTED, true);
            close.setGravity(Gravity.CENTER);
            close.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setItems(new String[]{"Parkplatz neu speichern", "Verlauf öffnen", "Parkhausangaben bearbeiten"}, (d, selected) -> {
                        if (selected == 0) saveCurrent();
                        else if (selected == 1) showTab(TAB_HISTORY);
                        else editGarage(active);
                    }).show());
            headline.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
            sheet.addView(headline);
            if (deviceLocation != null && deviceLocation.hasAccuracy()) {
                line(sheet, "Standortgenauigkeit  ±" + Math.round(deviceLocation.getAccuracy()) + " m",
                        12, ACCENT_DARK, false, 9);
            }
            String garage = GarageLabels.summary(active.floor, active.space);
            if (!garage.isEmpty()) line(sheet, "Parkhaus: " + garage, 13, ACCENT_DARK, true, 7);
            if (!active.note.isEmpty()) line(sheet, active.note, 13, MUTED, false, 7);
            if (active.timerAt > 0) line(sheet, "Parkuhr bis " + format(active.timerAt),
                    13, ACCENT_DARK, true, 7);
            threeActions(sheet,
                    quickAction("➤", "Route", () -> navigate(active)),
                    quickAction("◷", "Parkuhr", () -> timer(active)),
                    quickAction("▤", "Notiz", () -> editNote(active)));
            threeActions(sheet,
                    quickAction("▣", "Foto", () -> photo(active)),
                    quickAction("↗", "Teilen", () -> share(active)),
                    quickAction("▤", "Parkhaus", () -> editGarage(active)));
        }
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                -1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sheetParams.setMargins(dp(12), 0, dp(12), dp(14));
        canvas.addView(sheet, sheetParams);

        TextView myPosition = floatingButton("◎", 29, this::showMyLocation);
        canvas.addView(myPosition, floatingAt(51, 51, Gravity.RIGHT | Gravity.BOTTOM,
                0, 0, 18, active == null ? 247 : 304));

        // Provider attribution must remain accessible above the floating sheet.
        TextView attribution = text("© OpenStreetMap-Mitwirkende", 11, WHITE, false);
        attribution.setGravity(Gravity.CENTER);
        attribution.setPadding(dp(6), dp(3), dp(6), dp(3));
        attribution.setBackground(shape(Color.argb(220, 255, 255, 255), 5));
        attribution.setOnClickListener(v -> openUrl("https://www.openstreetmap.org/copyright"));
        canvas.addView(attribution, floatingAt(190, 27, Gravity.BOTTOM | Gravity.LEFT,
                12, 0, 0, active == null ? 260 : 318));
        canvas.post(this::showInitialPosition);
    }

    private void showInitialPosition() {
        if (currentTab != TAB_MAP || deviceLocation != null || store.active() != null) return;
        if (!LocationGateway.hasPermission(this)) return;
        LocationGateway.once(this, (location, error) -> {
            if (location != null && currentTab == TAB_MAP && map != null) {
                deviceLocation = location;
                map.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));
                map.getController().setZoom(16.5);
            }
        });
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
            line(item, (isActive ? "●  AKTUELL  ·  " : "") + (("BLUETOOTH".equals(spot.source) || "MOTION".equals(spot.source)) ? "Automatisch" : "Manuell"),
                    12, isActive ? ACCENT : MUTED, true, 0);
            line(item, format(spot.parkedAt), 17, WHITE, true, 7);
            line(item, String.format(Locale.GERMANY, "%.5f, %.5f", spot.latitude, spot.longitude),
                    13, MUTED, false, 6);
            String garage = GarageLabels.summary(spot.floor, spot.space);
            if (!garage.isEmpty()) line(item, "Parkhaus: " + garage, 14, ACCENT_DARK, true, 5);
            if (!spot.note.isEmpty()) line(item, spot.note, 14, WHITE, false, 5);
            actionLine(item, "↗  Navigation", false, () -> navigate(spot));
            actionLine(item, "▤  Parkhaus: Ebene und Stellplatz", false, () -> editGarage(spot));
            actionLine(item, spot.photoUri.isEmpty() ? "▣  Parkplatzfoto hinzufügen" : "▣  Parkplatzfoto verwalten",
                    false, () -> photo(spot));
            actionLine(item, "↗  Parkplatz teilen", false, () -> share(spot));
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
        line(content, "ROYALPARK 0.6.0  ·  Eigenständiger Neubau", 12, MUTED, false, 25);
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
        if (map != null) map.onResume();
        if (body != null && store != null) showTab(currentTab);
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
        if (store != null) store.close();
        super.onDestroy();
    }
}

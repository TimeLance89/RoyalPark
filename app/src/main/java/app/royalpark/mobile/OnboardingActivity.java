package app.royalpark.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Human-first, skippable onboarding. It requests only location permission on an explicit tap.
 * No tracking, background service, mandatory account, or forced automatic recognition.
 */
public final class OnboardingActivity extends Activity {
    private static final int BG = 0xFFF7FAFB;
    private static final int INK = 0xFF16313A;
    private static final int MUTED = 0xFF637C84;
    private static final int TEAL = 0xFF008E96;
    private static final int TINT = 0xFFE7F5F3;
    private static final int STROKE = 0xFFDFE9EC;
    private static final String PREF_DONE = "onboarding_complete";
    private static final String PREF_METHOD = "onboarding_preference";
    private static final int REQ_PERMISSION = 4421;
    private static final int[] ICONS = {0, 1, 2, 3};
    private final String[] headings = {
            "Dein Auto. Immer wiedergefunden.",
            "Damit du dein Auto wiederfindest.",
            "Wie möchtest du parken?",
            "Alles bereit für unterwegs."
    };
    private final String[] descriptions = {
            "Merke dir deinen Parkplatz mit einem Fingertipp. Den Rest erledigt RoyalPark – ohne Konto und ohne Werbung.",
            "Für eine genaue Parkposition braucht RoyalPark deinen Standort. Du entscheidest, wann du ihn freigibst.",
            "Du kannst jederzeit selbst speichern oder die automatische Erkennung später aktivieren.",
            "Karten, Erinnerungen und deine Parkplätze bleiben in deiner Hand."
    };
    private SharedPreferences prefs;
    private LinearLayout content;
    private LinearLayout bottom;
    private int page;
    private String chosenMethod = "manual";

    private int dp(float x) { return Math.round(getResources().getDisplayMetrics().density * x); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private GradientDrawable outlined(int color, int stroke, int radius) {
        GradientDrawable d = bg(color, radius);
        d.setStroke(dp(1), stroke);
        return d;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        parent.addView(child, lp);
    }
    private TextView button(String value, boolean primary, Runnable task) {
        TextView t = label(value, 16, primary ? Color.WHITE : TEAL, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        t.setMinHeight(dp(54));
        t.setBackground(outlined(primary ? TEAL : Color.WHITE,
                primary ? TEAL : STROKE, 18));
        t.setOnClickListener(v -> task.run());
        return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("royalpark", MODE_PRIVATE);
        if (prefs.getBoolean(PREF_DONE, false)) { finish(); return; }
        if (state != null) page = Math.max(0, Math.min(3, state.getInt("page", 0)));
        chosenMethod = prefs.getString(PREF_METHOD, "manual");
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(24), dp(14), dp(24), dp(6));
        TextView logo = label("◉  royalpark", 19, INK, true);
        toolbar.addView(logo, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView skip = label("Überspringen", 14, MUTED, false);
        skip.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        skip.setMinHeight(dp(44));
        skip.setOnClickListener(v -> complete(false));
        toolbar.addView(skip);
        root.addView(toolbar);

        LinearLayout progress = new LinearLayout(this);
        progress.setPadding(dp(24), dp(10), dp(24), dp(12));
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            dot.setBackground(bg(i <= page ? TEAL : STROKE, 9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(5), 1);
            if (i > 0) lp.leftMargin = dp(7);
            progress.addView(dot, lp);
        }
        root.addView(progress);

        ScrollView scrolling = new ScrollView(this);
        scrolling.setFillViewport(true);
        scrolling.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setPadding(dp(24), dp(6), dp(24), dp(16));
        content.setOrientation(LinearLayout.VERTICAL);
        scrolling.addView(content);
        root.addView(scrolling, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(24), dp(10), dp(24), dp(19));
        bottom.setBackgroundColor(BG);
        root.addView(bottom);

        hero();
        TextView step = label("SCHRITT " + (page + 1) + " VON 4", 12, TEAL, true);
        add(content, step, 26);
        add(content, label(headings[page], 30, INK, true), 9);
        add(content, label(descriptions[page], 15, MUTED, false), 11);
        if (page == 0) welcome();
        else if (page == 1) permissions();
        else if (page == 2) method();
        else finalPage();
        if (page > 0) add(bottom, button("Zurück", false, () -> {
            page--;
            render();
        }), 0);
        add(bottom, button(page == 3 ? "RoyalPark öffnen" : page == 1 ?
                "Weiter ohne Berechtigung" : "Weiter", true,
                () -> {
                    if (page == 3) complete(false);
                    else { page++; render(); }
                }), page > 0 ? 9 : 0);
    }

    private void hero() {
        FrameLayout illustration = new FrameLayout(this);
        illustration.setBackground(bg(TINT, 32));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(166));
        lp.topMargin = dp(4);
        content.addView(illustration, lp);

        View roadOne = new View(this);
        roadOne.setBackground(bg(0xFFD1EBE9, 22));
        roadOne.setRotation(-22f);
        FrameLayout.LayoutParams roadParams = new FrameLayout.LayoutParams(
                -1, dp(29), Gravity.CENTER);
        illustration.addView(roadOne, roadParams);
        View roadTwo = new View(this);
        roadTwo.setBackground(bg(0xFFFFFFFF, 22));
        roadTwo.setRotation(46f);
        FrameLayout.LayoutParams secondRoad = new FrameLayout.LayoutParams(
                -1, dp(17), Gravity.CENTER);
        illustration.addView(roadTwo, secondRoad);
        FrameLayout medallion = new FrameLayout(this);
        medallion.setBackground(bg(Color.WHITE, 27));
        medallion.setElevation(dp(5));
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(
                dp(92), dp(92), Gravity.CENTER);
        illustration.addView(medallion, center);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_royalpark);
        icon.setContentDescription("RoyalPark Parkplatz-Symbol");
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(75), dp(75), Gravity.CENTER);
        medallion.addView(icon, iconParams);
    }
    private void welcome() {
        benefit("Ohne Konto", "Alles bleibt lokal auf deinem Gerät.");
        benefit("Einfach parken", "Standort speichern, wiederfinden, fertig.");
        benefit("Auch offline", "Kartenpakete kannst du später herunterladen.");
    }
    private void benefit(String title, String caption) {
        LinearLayout tile = new LinearLayout(this);
        tile.setPadding(dp(16), dp(13), dp(16), dp(13));
        tile.setBackground(outlined(Color.WHITE, STROKE, 18));
        tile.setOrientation(LinearLayout.VERTICAL);
        add(tile, label(title, 16, INK, true), 0);
        add(tile, label(caption, 13, MUTED, false), 3);
        add(content, tile, 10);
    }
    private void permissions() {
        benefit("Genau statt ungefähr", "Für den Parkplatz ist eine möglichst genaue Position sinnvoll.");
        benefit("Nur wenn du es möchtest", "Du kannst RoyalPark auch ohne Freigabe erkunden.");
        boolean granted = LocationGateway.hasPermission(this);
        TextView cta = button(granted ? "✓ Standort ist erlaubt" :
                "Standortzugriff erlauben", true, () -> {
            if (granted) { page = 2; render(); return; }
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMISSION);
        });
        add(content, cta, 17);
        if (granted && !LocationGateway.hasPrecisePermission(this))
            add(content, label("Nur ungefähre Ortung aktiv – in Android kannst du später die präzise Ortung erlauben.",
                    13, MUTED, false), 8);
    }
    private void method() {
        methodTile("manual", "Manuell speichern", "Ein Fingertipp, sobald du geparkt hast. Empfohlen.");
        methodTile("bluetooth", "Bluetooth-Automatik", "Speichern, wenn die Verbindung zum Auto getrennt wird.");
        methodTile("motion", "Bewegungserkennung", "Möglichen Ausstieg erkennen – separat aktivierbar.");
        add(content, label("Automatische Erkennung schalten wir niemals ungefragt ein. "
                + "Nach dem Start kannst du sie in den Einstellungen einrichten.", 13, MUTED, false), 15);
    }
    private void methodTile(String id, String title, String description) {
        boolean checked = id.equals(chosenMethod);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(17), dp(15), dp(17), dp(15));
        tile.setBackground(outlined(checked ? TINT : Color.WHITE, checked ? TEAL : STROKE, 18));
        add(tile, label((checked ? "●  " : "○  ") + title, 16, INK, true), 0);
        add(tile, label(description, 13, MUTED, false), 5);
        tile.setOnClickListener(v -> {
            chosenMethod = id;
            prefs.edit().putString(PREF_METHOD, id).apply();
            render();
        });
        add(content, tile, 11);
    }
    private void finalPage() {
        benefit("Karte und Parkplatz", "Finde dein Auto auf einer übersichtlichen Karte.");
        benefit("Erinnerungen", "Stelle eine Parkuhr ein, wenn du sie brauchst.");
        benefit("Datensicherung", "Exportiere deine Parkplätze als ZIP.");
        add(content, label("Offline-Karten und automatische Erkennung kannst du später "
                + "in den Einstellungen aktivieren.", 13, MUTED, false), 15);
    }
    private void complete(boolean settings) {
        prefs.edit().putBoolean(PREF_DONE, true)
                .putString(PREF_METHOD, chosenMethod).apply();
        if (!"manual".equals(chosenMethod)) {
            Toast.makeText(this, "Automatik kannst du unter Einstellungen einrichten.",
                    Toast.LENGTH_LONG).show();
        }
        finish();
    }
    @Override public void onBackPressed() {
        if (page == 0) complete(false);
        else { page--; render(); }
    }
    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSION) return;
        if (LocationGateway.hasPermission(this)) {
            page = 2;
            render();
        } else {
            Toast.makeText(this, "Kein Problem – du kannst das später erlauben.",
                    Toast.LENGTH_LONG).show();
            render();
        }
    }
}

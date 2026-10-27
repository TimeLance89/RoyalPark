package app.royalpark.mobile;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Home-screen widget with current parking information and an explicit quick-save action. */
public final class ParkingWidget extends AppWidgetProvider {
    public static final String QUICK_SAVE = "app.royalpark.mobile.QUICK_SAVE";

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, ParkingWidget.class);
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids != null && ids.length > 0) render(context, manager, ids);
    }

    private static void render(Context context, AppWidgetManager manager, int[] ids) {
        ParkingSpot active;
        try (ParkingStore store = new ParkingStore(context)) { active = store.active(); }
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_parking);
            views.setTextViewText(R.id.widget_title, "ROYALPARK");
            views.setTextViewText(R.id.widget_subtitle, active == null
                    ? "Kein Parkplatz gespeichert"
                    : "Geparkt " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                        Locale.GERMANY).format(new Date(active.parkedAt)));
            Intent open = new Intent(context, MainActivity.class);
            views.setOnClickPendingIntent(R.id.widget_open,
                    PendingIntent.getActivity(context, id, open,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            Intent save = new Intent(context, MainActivity.class).setAction(QUICK_SAVE);
            views.setOnClickPendingIntent(R.id.widget_save,
                    PendingIntent.getActivity(context, id + 10000, save,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            manager.updateAppWidget(id, views);
        }
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, manager, ids);
    }
}

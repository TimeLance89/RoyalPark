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

/** Dedicated widget for viewing and changing the active parking timer. */
public final class ParkingTimerWidget extends AppWidgetProvider {
    public static final String OPEN_TIMER = "app.royalpark.mobile.OPEN_TIMER";
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ParkingTimerWidget.class));
        if (ids == null || ids.length == 0) return;
        ParkingSpot active;
        try (ParkingStore store = new ParkingStore(context)) { active = store.active(); }
        String label = active == null ? "Kein Parkplatz gewählt"
                : active.timerAt <= 0 ? "Keine Parkuhr eingestellt"
                : active.timerAt <= System.currentTimeMillis() ? "Parkzeit abgelaufen"
                : "Parkzeit bis " + DateFormat.getTimeInstance(DateFormat.SHORT,
                        Locale.GERMANY).format(new Date(active.timerAt));
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_timer);
            views.setTextViewText(R.id.timer_status, label);
            Intent action = new Intent(context, MainActivity.class).setAction(OPEN_TIMER);
            views.setOnClickPendingIntent(R.id.timer_button,
                    PendingIntent.getActivity(context, 30_000 + id, action,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id, views);
        }
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
    }
}

package app.royalpark.mobile;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Independent quick-save home-screen widget. User action always opens the app for GPS. */
public final class QuickSaveWidget extends AppWidgetProvider {
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, QuickSaveWidget.class));
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_save);
            Intent save = new Intent(context, MainActivity.class).setAction(ParkingWidget.QUICK_SAVE);
            views.setOnClickPendingIntent(R.id.quick_button,
                    PendingIntent.getActivity(context, 20_000 + id, save,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id, views);
        }
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
    }
}

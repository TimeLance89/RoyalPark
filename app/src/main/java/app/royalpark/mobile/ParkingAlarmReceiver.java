package app.royalpark.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Alarm alerts are intentionally inexact; no special exact-alarm permission. */
public final class ParkingAlarmReceiver extends BroadcastReceiver {
    public static final String CHANNEL = "royalpark_parking";

    public static void createChannel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Parkplatz-Erinnerungen", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Parkzeit und Bluetooth-Parkerkennung");
            manager.createNotificationChannel(channel);
        }
    }

    public static Notification notification(Context context, String title, String content, boolean persistent) {
        Intent launch = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, 10, launch,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(context, CHANNEL)
                .setSmallIcon(app.royalpark.mobile.R.drawable.ic_royalpark)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pending)
                .setAutoCancel(!persistent)
                .setOngoing(persistent)
                .build();
    }

    @Override public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("spot_id", -1);
        try (ParkingStore store = new ParkingStore(context)) {
            ParkingSpot active = store.active();
            if (active == null || active.id != id || active.timerAt <= 0
                    || active.timerAt > System.currentTimeMillis() + 120_000L) return;
            createChannel(context);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.notify(902, notification(context,
                    "Parkzeit läuft ab", "Dein Parkplatz-Timer ist abgelaufen.", false));
            ParkingWidget.updateAll(context);
        }
    }
}

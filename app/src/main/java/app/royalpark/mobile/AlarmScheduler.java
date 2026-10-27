package app.royalpark.mobile;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public final class AlarmScheduler {
    private AlarmScheduler() {}

    private static PendingIntent pending(Context context, long id) {
        Intent intent = new Intent(context, ParkingAlarmReceiver.class);
        intent.putExtra("spot_id", id);
        return PendingIntent.getBroadcast(context, 9001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void cancel(Context context) {
        AlarmManager alarm = context.getSystemService(AlarmManager.class);
        if (alarm != null) alarm.cancel(pending(context, -1));
    }

    public static void schedule(Context context, long id, long atMillis) {
        cancel(context);
        if (atMillis <= 0) return;
        ParkingAlarmReceiver.createChannel(context);
        AlarmManager alarm = context.getSystemService(AlarmManager.class);
        if (alarm != null) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                atMillis, pending(context, id));
    }
}

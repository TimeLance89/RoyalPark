package app.royalpark.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

/** Delivers an explicit GMS transition callback to the already-running foreground watcher. */
public final class MotionParkReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        SharedPreferences settings = context.getSharedPreferences("royalpark", Context.MODE_PRIVATE);
        if (!settings.getBoolean("auto_enabled", false)
                || !settings.getBoolean("motion_enabled", false)) return;
        if (!ActivityTransitionResult.hasResult(intent)) return;
        ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
        if (result == null) return;
        for (ActivityTransitionEvent event : result.getTransitionEvents()) {
            if (event.getActivityType() == DetectedActivity.IN_VEHICLE
                    && event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_EXIT) {
                // No background service is started. Receiver forwards only to an active watcher.
                Intent internal = new Intent(AutoParkService.motionAction());
                internal.setPackage(context.getPackageName());
                context.sendBroadcast(internal);
                return;
            }
        }
    }
}

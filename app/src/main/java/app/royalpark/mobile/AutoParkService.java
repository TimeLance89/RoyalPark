package app.royalpark.mobile;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;

import java.util.Arrays;

/**
 * Explicitly enabled foreground watcher.
 * Bluetooth and activity-transition sources share one deduplicated save path.
 * The activity transition is an estimate of leaving a vehicle, not proof of parking.
 */
public final class AutoParkService extends Service {
    private static final String ACTION_MOTION = "app.royalpark.mobile.INTERNAL_MOTION_EXIT";
    private static final long MOTION_SETTLE_MS = 8_000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BroadcastReceiver bluetoothReceiver;
    private BroadcastReceiver motionReceiver;
    private PendingIntent transitionsIntent;
    private SharedPreferences prefs;
    private boolean saving;

    static String motionAction() { return ACTION_MOTION; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("royalpark", MODE_PRIVATE);
        if (!LocationGateway.hasPermission(this) || !prefs.getBoolean("auto_enabled", false)) {
            stopSelf();
            return;
        }
        try {
            ParkingAlarmReceiver.createChannel(this);
            String message = prefs.getBoolean("motion_enabled", false)
                    ? "Bluetooth und/oder Aussteigen werden erkannt."
                    : "Wartet auf Trennung vom Auto-Bluetooth.";
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(901, ParkingAlarmReceiver.notification(this,
                        "RoyalPark-Automatik aktiv", message, true),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(901, ParkingAlarmReceiver.notification(this,
                        "RoyalPark-Automatik aktiv", message, true));
            }
        } catch (Exception error) {
            prefs.edit().putBoolean("auto_enabled", false).apply();
            stopSelf();
            return;
        }
        watchBluetooth();
        if (prefs.getBoolean("motion_enabled", false)) watchMotion();
    }

    private void watchBluetooth() {
        if (prefs.getString("auto_mac", "").isEmpty()) return;
        bluetoothReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) return;
                if (!prefs.getBoolean("auto_enabled", false)) return;
                if (Build.VERSION.SDK_INT >= 31 &&
                        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    return;
                BluetoothDevice device;
                if (Build.VERSION.SDK_INT >= 33)
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                else device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) return;
                try {
                    if (!device.getAddress().equalsIgnoreCase(prefs.getString("auto_mac", ""))) return;
                } catch (SecurityException error) { return; }
                capture("BLUETOOTH", "Bluetooth vom Auto getrennt");
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(bluetoothReceiver, filter);
    }

    private void watchMotion() {
        if (Build.VERSION.SDK_INT >= 29 &&
                checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putString("motion_status", "Bewegungsberechtigung fehlt").apply();
            return;
        }
        motionReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!ACTION_MOTION.equals(intent.getAction())) return;
                if (!prefs.getBoolean("auto_enabled", false)
                        || !prefs.getBoolean("motion_enabled", false)) return;
                // Wait briefly so the GPS fix is taken after the user has exited the car.
                main.postDelayed(() -> {
                    if (prefs.getBoolean("auto_enabled", false)
                            && prefs.getBoolean("motion_enabled", false))
                        capture("MOTION", "Ausstieg aus Fahrzeug erkannt");
                }, MOTION_SETTLE_MS);
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_MOTION);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(motionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(motionReceiver, filter);
        Intent callback = new Intent(this, MotionParkReceiver.class);
        transitionsIntent = PendingIntent.getBroadcast(this, 904, callback,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        ActivityTransition entered = new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build();
        ActivityTransition exited = new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build();
        try {
            ActivityRecognition.getClient(this)
                    .requestActivityTransitionUpdates(
                            new ActivityTransitionRequest(Arrays.asList(entered, exited)),
                            transitionsIntent)
                    .addOnSuccessListener(ignored -> prefs.edit()
                            .putString("motion_status", "Bewegungserkennung aktiv").apply())
                    .addOnFailureListener(error -> prefs.edit()
                            .putString("motion_status", "Bewegungserkennung nicht verfügbar").apply());
        } catch (Exception error) {
            prefs.edit().putString("motion_status", "Bewegungserkennung nicht verfügbar").apply();
        }
    }

    private void capture(String source, String label) {
        if (saving || !prefs.getBoolean("auto_enabled", false)) return;
        long now = System.currentTimeMillis();
        if (!ParkingLogic.mayAutoSave(now, prefs.getLong("last_auto_at", 0))) return;
        saving = true;
        LocationGateway.once(this, (location, error) -> {
            saving = false;
            if (location == null || !prefs.getBoolean("auto_enabled", false)) return;
            if (!ParkingLogic.mayAutoSave(System.currentTimeMillis(), prefs.getLong("last_auto_at", 0))) return;
            try (ParkingStore store = new ParkingStore(this)) {
                AlarmScheduler.cancel(this);
                store.save(location.getLatitude(), location.getLongitude(),
                        "Automatisch gespeichert: " + label, source);
                prefs.edit().putLong("last_auto_at", System.currentTimeMillis()).apply();
                ParkingWidget.updateAll(this);
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) manager.notify(903, ParkingAlarmReceiver.notification(this,
                        "Parkplatz gespeichert", label, false));
            } catch (Exception ignored) { /* Stay alive; next transition can retry. */ }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            prefs.edit().putBoolean("auto_enabled", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (prefs != null) prefs.edit().putBoolean("auto_enabled", false).apply();
        if (bluetoothReceiver != null) {
            try { unregisterReceiver(bluetoothReceiver); } catch (IllegalArgumentException ignored) { }
        }
        if (motionReceiver != null) {
            try { unregisterReceiver(motionReceiver); } catch (IllegalArgumentException ignored) { }
        }
        if (transitionsIntent != null) {
            try { ActivityRecognition.getClient(this).removeActivityTransitionUpdates(transitionsIntent); }
            catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

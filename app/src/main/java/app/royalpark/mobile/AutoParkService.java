package app.royalpark.mobile;

import android.Manifest;
import android.app.NotificationManager;
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
import android.os.IBinder;

/** Opt-in Bluetooth disconnect watcher, running only as visible foreground service. */
public final class AutoParkService extends Service {
    private BroadcastReceiver receiver;
    private SharedPreferences prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("royalpark", MODE_PRIVATE);
        if (!LocationGateway.hasPermission(this)) {
            stopSelf();
            return;
        }
        try {
            ParkingAlarmReceiver.createChannel(this);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(901, ParkingAlarmReceiver.notification(this,
                        "RoyalPark-Automatik aktiv", "Wartet auf das Trennen des Auto-Bluetooths.", true),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(901, ParkingAlarmReceiver.notification(this,
                        "RoyalPark-Automatik aktiv", "Wartet auf das Trennen des Auto-Bluetooths.", true));
            }
        } catch (Exception ex) {
            prefs.edit().putBoolean("auto_enabled", false).apply();
            stopSelf();
            return;
        }
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) return;
                if (!prefs.getBoolean("auto_enabled", false)) return;
                if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) return;
                BluetoothDevice device;
                if (Build.VERSION.SDK_INT >= 33) {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                } else {
                    device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                }
                if (device == null) return;
                try {
                    if (!device.getAddress().equalsIgnoreCase(prefs.getString("auto_mac", ""))) return;
                } catch (SecurityException ignored) {
                    return;
                }
                long last = prefs.getLong("last_auto_at", 0);
                if (!ParkingLogic.mayAutoSave(System.currentTimeMillis(), last)) return;
                // First get a fresh location; never treat a disconnect alone as proof of parking.
                LocationGateway.once(AutoParkService.this, (location, error) -> {
                    if (location == null || !prefs.getBoolean("auto_enabled", false)) return;
                    try (ParkingStore store = new ParkingStore(AutoParkService.this)) {
                        AlarmScheduler.cancel(AutoParkService.this);
                        store.save(location.getLatitude(), location.getLongitude(),
                                "Automatisch über Bluetooth gespeichert", "BLUETOOTH");
                        prefs.edit().putLong("last_auto_at", System.currentTimeMillis()).apply();
                        NotificationManager manager = getSystemService(NotificationManager.class);
                        if (manager != null) manager.notify(903,
                                ParkingAlarmReceiver.notification(AutoParkService.this,
                                        "Parkplatz gespeichert", "Bluetooth getrennt – Standort merken.", false));
                    } catch (Exception ignored) { /* Avoid crashing background service. */ }
                });
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        // Bluetooth broadcast may originate from a privileged system component.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, filter);
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
        if (prefs != null) prefs.edit().putBoolean("auto_enabled", false).apply();
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
            receiver = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

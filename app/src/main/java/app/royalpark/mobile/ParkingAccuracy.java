package app.royalpark.mobile;

/**
 * Conservative quality label for a manually stored parking position.
 * A coarse permission never proves car-level accuracy, even when Android
 * reports an optimistic radius. This does not block user-initiated saves.
 */
public final class ParkingAccuracy {
    public static final float CAR_LEVEL_THRESHOLD_METERS = 80f;
    public static final String SOURCE_APPROXIMATE = "MANUAL_APPROX";

    private ParkingAccuracy() {}

    public static boolean approximate(boolean precisePermission, boolean hasAccuracy,
            float accuracyMeters) {
        return !precisePermission || !hasAccuracy
                || !Float.isFinite(accuracyMeters)
                || accuracyMeters < 0f
                || accuracyMeters > CAR_LEVEL_THRESHOLD_METERS;
    }

    public static String label(boolean precisePermission, boolean hasAccuracy, float accuracyMeters) {
        if (!precisePermission) return "Ungefährer Standort";
        if (!hasAccuracy || !Float.isFinite(accuracyMeters) || accuracyMeters < 0f)
            return "Standortgenauigkeit unbekannt";
        return "Standort ±" + Math.round(accuracyMeters) + " m";
    }
}

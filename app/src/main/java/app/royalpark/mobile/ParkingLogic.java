package app.royalpark.mobile;

import java.util.Locale;

/** Pure Java domain rules with deterministic unit tests. */
public final class ParkingLogic {
    private ParkingLogic() {}

    public static boolean validCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    /** Suppresses multiple disconnect broadcasts within two minutes. */
    public static boolean mayAutoSave(long now, long lastSaved) {
        return now > 0 && (lastSaved <= 0 || now - lastSaved >= 120_000L);
    }

    public static String osmLink(double lat, double lon) {
        if (!validCoordinates(lat, lon)) throw new IllegalArgumentException("Invalid location");
        return String.format(Locale.US,
                "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=18/%.6f/%.6f",
                lat, lon, lat, lon);
    }
}

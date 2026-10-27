package app.royalpark.mobile;

import java.util.Locale;

/** Pure arithmetic for the in-app walking pointer, testable without Android. */
public final class NavigationMath {
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private NavigationMath() {}

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        if (!ParkingLogic.validCoordinates(lat1, lon1) || !ParkingLogic.validCoordinates(lat2, lon2))
            throw new IllegalArgumentException("Invalid coordinates");
        double a = Math.sin(Math.toRadians(lat2 - lat1) / 2);
        double b = Math.sin(Math.toRadians(lon2 - lon1) / 2);
        double h = a * a + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * b * b;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    public static float bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        if (!ParkingLogic.validCoordinates(lat1, lon1) || !ParkingLogic.validCoordinates(lat2, lon2))
            throw new IllegalArgumentException("Invalid coordinates");
        double a = Math.toRadians(lat1);
        double b = Math.toRadians(lat2);
        double d = Math.toRadians(lon2 - lon1);
        double y = Math.sin(d) * Math.cos(b);
        double x = Math.cos(a) * Math.sin(b) - Math.sin(a) * Math.cos(b) * Math.cos(d);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    public static float relativeDirection(float targetBearing, float heading) {
        float angle = (targetBearing - heading) % 360f;
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }

    public static String formatDistance(double meters) {
        if (!Double.isFinite(meters) || meters < 0) throw new IllegalArgumentException("Invalid distance");
        return meters < 1000 ? Math.round(meters) + " m"
                : String.format(Locale.GERMANY, "%.1f km", meters / 1000);
    }
}

package app.royalpark.mobile;

/** Camera decisions independent of Android views; testable in ordinary JUnit. */
public final class MapFraming {
    public static final double CAR_ZOOM = 19.0;
    public static final double MAX_BOTH_ZOOM = 18.5;
    private static final double PADDING_DEGREES = 0.00012;
    private MapFraming() {}

    /** Avoid misleadingly tight focus on inaccurate device positions. */
    public static double myPositionZoom(float accuracyMeters) {
        if (!Float.isFinite(accuracyMeters) || accuracyMeters < 0) return 16.0;
        if (accuracyMeters <= 20) return 18.5;
        if (accuracyMeters <= 60) return 17.5;
        if (accuracyMeters <= 150) return 16.5;
        return 15.5;
    }

    public static boolean nearby(double carLat, double carLon, double userLat, double userLon) {
        return NavigationMath.distanceMeters(carLat, carLon, userLat, userLon) < 35;
    }

    public static final class Frame {
        public final double north;
        public final double east;
        public final double south;
        public final double west;

        private Frame(double north, double east, double south, double west) {
            this.north = north;
            this.east = east;
            this.south = south;
            this.west = west;
        }
    }

    public static Frame aroundBoth(double carLat, double carLon, double myLat, double myLon) {
        if (!ParkingLogic.validCoordinates(carLat, carLon) ||
                !ParkingLogic.validCoordinates(myLat, myLon))
            throw new IllegalArgumentException("Invalid map coordinates");
        double latitudePadding = Math.max(PADDING_DEGREES, Math.abs(myLat - carLat) * 0.11);
        double longitudePadding = Math.max(PADDING_DEGREES, Math.abs(myLon - carLon) * 0.11);
        return new Frame(
                Math.min(85.0511, Math.max(carLat, myLat) + latitudePadding),
                Math.min(180, Math.max(carLon, myLon) + longitudePadding),
                Math.max(-85.0511, Math.min(carLat, myLat) - latitudePadding),
                Math.max(-180, Math.min(carLon, myLon) - longitudePadding));
    }
}

package app.royalpark.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Valhalla encoded polyline6 decoder, independent of Android for unit tests. */
public final class RouteGeometry {
    private static final int MAX_POINTS = 20_000;
    private RouteGeometry() {}

    public static final class Point {
        public final double lat;
        public final double lon;
        public Point(double lat, double lon) {
            if (!ParkingLogic.validCoordinates(lat, lon))
                throw new IllegalArgumentException("Invalid point");
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static List<Point> decodePolyline6(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_POINTS * 16)
            throw new IllegalArgumentException("Invalid route shape");
        List<Point> points = new ArrayList<>();
        long latitude = 0, longitude = 0;
        int[] offset = {0};
        while (offset[0] < value.length()) {
            if (points.size() >= MAX_POINTS) throw new IllegalArgumentException("Route too long");
            latitude += readSigned(value, offset);
            longitude += readSigned(value, offset);
            points.add(new Point(latitude / 1_000_000d, longitude / 1_000_000d));
        }
        if (points.size() < 2) throw new IllegalArgumentException("Route too short");
        return Collections.unmodifiableList(points);
    }

    private static long readSigned(String data, int[] offset) {
        long value = 0;
        int shift = 0;
        for (int i = 0; i < 10; i++) {
            if (offset[0] >= data.length()) throw new IllegalArgumentException("Truncated shape");
            int raw = data.charAt(offset[0]++) - 63;
            if (raw < 0 || raw > 63) throw new IllegalArgumentException("Invalid shape character");
            value |= ((long) (raw & 31)) << shift;
            if ((raw & 32) == 0) return (value & 1L) == 0 ? value >> 1 : ~(value >> 1);
            shift += 5;
        }
        throw new IllegalArgumentException("Invalid shape integer");
    }

    /** Find the nearest known route vertex; used only for display, never for legal navigation. */
    public static int nearestIndex(List<Point> path, double lat, double lon) {
        if (path == null || path.isEmpty() || !ParkingLogic.validCoordinates(lat, lon))
            throw new IllegalArgumentException("Invalid route");
        double best = Double.POSITIVE_INFINITY;
        int index = 0;
        for (int i = 0; i < path.size(); i++) {
            Point p = path.get(i);
            double dLat = p.lat - lat;
            double dLon = (p.lon - lon) * Math.cos(Math.toRadians(lat));
            double squared = dLat * dLat + dLon * dLon;
            if (squared < best) { best = squared; index = i; }
        }
        return index;
    }
}

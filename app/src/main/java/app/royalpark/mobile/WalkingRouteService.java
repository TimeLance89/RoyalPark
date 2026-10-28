package app.royalpark.mobile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Explicit, user-triggered online pedestrian routing.
 * Public FOSSGIS Valhalla demo is rate limited, has no SLA, and must not be queried in a loop.
 */
public final class WalkingRouteService {
    private static final String API = "https://valhalla1.openstreetmap.de/route";
    private WalkingRouteService() {}

    public static final class Step {
        public final int shapeIndex;
        public final String instruction;
        Step(int shapeIndex, String instruction) {
            this.shapeIndex = shapeIndex;
            this.instruction = instruction;
        }
    }
    public static final class Route {
        public final List<RouteGeometry.Point> coordinates;
        public final List<Step> steps;
        public final double lengthKm;
        public final double durationSeconds;
        Route(List<RouteGeometry.Point> coordinates, List<Step> steps, double lengthKm,
              double durationSeconds) {
            this.coordinates = coordinates;
            this.steps = steps;
            this.lengthKm = lengthKm;
            this.durationSeconds = durationSeconds;
        }
    }

    public static Route calculate(double startLat, double startLon, double endLat, double endLon)
            throws IOException, JSONException {
        if (!ParkingLogic.validCoordinates(startLat, startLon)
                || !ParkingLogic.validCoordinates(endLat, endLon))
            throw new IllegalArgumentException("Ungültige Koordinaten.");
        if (NavigationMath.distanceMeters(startLat, startLon, endLat, endLon) > 20_000)
            throw new IllegalArgumentException("Die Fußwegroute ist auf 20 km Luftlinie begrenzt.");

        JSONObject request = new JSONObject()
                .put("locations", new JSONArray()
                        .put(new JSONObject().put("lat", startLat).put("lon", startLon))
                        .put(new JSONObject().put("lat", endLat).put("lon", endLon)))
                .put("costing", "pedestrian")
                .put("units", "km")
                .put("language", "de-DE")
                .put("directions_type", "instructions")
                .put("shape_format", "polyline6");
        HttpURLConnection conn = (HttpURLConnection) new URL(API).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(14000);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "RoyalPark/0.5 (+https://github.com/TimeLance89/RoyalPark)");
        conn.setRequestProperty("X-Client-Id", "RoyalPark-TimeLance89-GitHub");
        byte[] payload = request.toString().getBytes(StandardCharsets.UTF_8);
        try {
            try (OutputStream out = conn.getOutputStream()) { out.write(payload); }
            int code = conn.getResponseCode();
            if (code == 429) throw new IOException("Routingdienst ausgelastet. Bitte später erneut versuchen.");
            if (code != 200) throw new IOException("Keine Fußwegroute verfügbar (HTTP " + code + ").");
            try (InputStream stream = conn.getInputStream()) {
                return parse(readLimited(stream));
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int amount;
        while ((amount = in.read(block)) != -1) {
            if (buffer.size() + amount > 1024 * 1024)
                throw new IOException("Routingantwort zu groß.");
            buffer.write(block, 0, amount);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    static Route parse(String json) throws JSONException {
        JSONObject trip = new JSONObject(json).getJSONObject("trip");
        JSONArray legs = trip.getJSONArray("legs");
        if (legs.length() != 1) throw new JSONException("Unerwartete Route");
        JSONObject leg = legs.getJSONObject(0);
        List<RouteGeometry.Point> points = RouteGeometry.decodePolyline6(leg.getString("shape"));
        JSONObject summary = trip.getJSONObject("summary");
        double length = summary.getDouble("length"), time = summary.getDouble("time");
        if (!Double.isFinite(length) || !Double.isFinite(time) || length < 0 || time < 0)
            throw new JSONException("Ungültige Routendaten");
        List<Step> steps = new ArrayList<>();
        JSONArray maneuvers = leg.optJSONArray("maneuvers");
        if (maneuvers != null) {
            for (int i = 0; i < maneuvers.length() && i < 150; i++) {
                JSONObject item = maneuvers.getJSONObject(i);
                int index = item.optInt("begin_shape_index", -1);
                String description = item.optString("instruction", "").trim();
                if (index >= 0 && index < points.size() && !description.isEmpty()) {
                    if (description.length() > 500) description = description.substring(0, 500);
                    steps.add(new Step(index, description));
                }
            }
        }
        return new Route(points, Collections.unmodifiableList(steps), length, time);
    }
}

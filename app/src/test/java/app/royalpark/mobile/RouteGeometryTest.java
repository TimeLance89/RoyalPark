package app.royalpark.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class RouteGeometryTest {
    private static String encode(long number) {
        long value = number < 0 ? ~(number << 1) : number << 1;
        StringBuilder output = new StringBuilder();
        while (value >= 0x20) {
            output.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        output.append((char) (value + 63));
        return output.toString();
    }

    @Test public void validPolyline6RoundTrip() {
        long[][] coords = {{53539820, 8580000}, {53540280, 8581200}, {53539999, 8579000}};
        long lat=0, lon=0;
        StringBuilder encoded = new StringBuilder();
        for (long[] next : coords) {
            encoded.append(encode(next[0]-lat)).append(encode(next[1]-lon));
            lat=next[0]; lon=next[1];
        }
        List<RouteGeometry.Point> actual = RouteGeometry.decodePolyline6(encoded.toString());
        assertEquals(3, actual.size());
        for(int i=0;i<coords.length;i++) {
            assertEquals(coords[i][0]/1_000_000d, actual.get(i).lat, 0.0000001);
            assertEquals(coords[i][1]/1_000_000d, actual.get(i).lon, 0.0000001);
        }
        assertEquals(1, RouteGeometry.nearestIndex(actual,53.54028,8.5812));
        assertEquals(0, RouteGeometry.nearestIndex(actual,53.53982,8.58));
    }

    @Test(expected=IllegalArgumentException.class)
    public void missingCoordinates() { RouteGeometry.decodePolyline6("?"); }

    @Test(expected=IllegalArgumentException.class)
    public void unsupportedCharacter() { RouteGeometry.decodePolyline6("!!"); }

    @Test(expected=IllegalArgumentException.class)
    public void emptyShape() { RouteGeometry.decodePolyline6(""); }

    @Test(expected=IllegalArgumentException.class)
    public void invalidQuery() {
        List<RouteGeometry.Point> path = new ArrayList<>();
        path.add(new RouteGeometry.Point(52,13));
        RouteGeometry.nearestIndex(path,Double.NaN,0);
    }
}

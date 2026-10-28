package app.royalpark.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MapFramingTest {
    @Test public void carFocusIsClose() {
        assertTrue(MapFraming.CAR_ZOOM >= 18.5);
        assertEquals(18.5, MapFraming.myPositionZoom(15), 0.001);
        assertEquals(17.5, MapFraming.myPositionZoom(40), 0.001);
        assertEquals(16.5, MapFraming.myPositionZoom(80), 0.001);
        assertEquals(15.5, MapFraming.myPositionZoom(230), 0.001);
        assertEquals(16.0, MapFraming.myPositionZoom(Float.NaN), 0.001);
    }
    @Test public void bothFrameContainsBothPoints() {
        MapFraming.Frame frame = MapFraming.aroundBoth(53.545,8.58,53.554,8.59);
        assertTrue(frame.north > 53.554);
        assertTrue(frame.south < 53.545);
        assertTrue(frame.east > 8.59);
        assertTrue(frame.west < 8.58);
    }
    @Test public void samePlaceKeepsPositivePadding() {
        MapFraming.Frame frame = MapFraming.aroundBoth(53.545,8.58,53.545,8.58);
        assertTrue(frame.north > frame.south);
        assertTrue(frame.east > frame.west);
        assertTrue(MapFraming.nearby(53.545,8.58,53.545,8.58));
        assertFalse(MapFraming.nearby(53.545,8.58,53.555,8.58));
    }
    @Test(expected = IllegalArgumentException.class)
    public void invalidPointRejected() {
        MapFraming.aroundBoth(200, 8.58, 53.54, 8.58);
    }
}

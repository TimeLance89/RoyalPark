package app.royalpark.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class MapPerformanceTest {
    private ParkingSpot spot(long id, String note) {
        return new ParkingSpot(id, 53.54, 8.58, note, 178000000L,
                "MANUAL", 0L, "", "-2", "B-17");
    }
    @Test public void sameStateReusesSameMap() {
        assertEquals(MapPerformance.renderKey(spot(3, "A"), "offline|bremen", true),
                     MapPerformance.renderKey(spot(3, "A"), "offline|bremen", true));
    }
    @Test public void changesInvalidateOnlyWhenNecessary() {
        String original = MapPerformance.renderKey(spot(3, "A"), "online", true);
        assertNotEquals(original, MapPerformance.renderKey(spot(3, "B"), "online", true));
        assertNotEquals(original, MapPerformance.renderKey(spot(4, "A"), "online", true));
        assertNotEquals(original, MapPerformance.renderKey(spot(3, "A"), "offline", true));
        assertNotEquals(original, MapPerformance.renderKey(spot(3, "A"), "online", false));
    }
    @Test public void noSpotStaysStable() {
        assertEquals("none|online|true", MapPerformance.renderKey(null, "online", true));
    }
    @Test public void avoidRepeatedZoomRefresh() {
        assertFalse(MapPerformance.shouldZoom(17.48, 17.50));
        assertTrue(MapPerformance.shouldZoom(17.0, 18.5));
    }
}

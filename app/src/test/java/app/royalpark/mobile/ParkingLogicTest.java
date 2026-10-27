package app.royalpark.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ParkingLogicTest {
    @Test public void coordinateRangeIsChecked() {
        assertTrue(ParkingLogic.validCoordinates(53.539, 8.58));
        assertFalse(ParkingLogic.validCoordinates(Double.NaN, 8));
        assertFalse(ParkingLogic.validCoordinates(91, 0));
        assertFalse(ParkingLogic.validCoordinates(20, 181));
    }

    @Test public void disconnectionEventsAreDebounced() {
        assertTrue(ParkingLogic.mayAutoSave(100_000L, 0));
        assertFalse(ParkingLogic.mayAutoSave(219_999L, 100_000L));
        assertTrue(ParkingLogic.mayAutoSave(220_000L, 100_000L));
        assertFalse(ParkingLogic.mayAutoSave(90_000L, 100_000L));
    }

    @Test public void linkContainsCoordinates() {
        assertEquals("https://www.openstreetmap.org/?mlat=53.539000&mlon=8.580000#map=18/53.539000/8.580000",
                ParkingLogic.osmLink(53.539, 8.58));
    }

    @Test(expected = IllegalArgumentException.class)
    public void linkRejectsInvalidPosition() {
        ParkingLogic.osmLink(100, 200);
    }
}

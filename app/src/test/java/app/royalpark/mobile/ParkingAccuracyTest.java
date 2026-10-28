package app.royalpark.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ParkingAccuracyTest {
    @Test public void coarsePermissionAlwaysMarkedApproximate() {
        assertTrue(ParkingAccuracy.approximate(false, true, 5f));
        assertTrue(ParkingAccuracy.approximate(false, true, 100f));
        assertEquals("Ungefährer Standort",
                ParkingAccuracy.label(false, true, 100f));
    }
    @Test public void precisionThreshold() {
        assertFalse(ParkingAccuracy.approximate(true, true, 5f));
        assertFalse(ParkingAccuracy.approximate(true, true, 80f));
        assertTrue(ParkingAccuracy.approximate(true, true, 81f));
        assertTrue(ParkingAccuracy.approximate(true, false, 0f));
        assertTrue(ParkingAccuracy.approximate(true, true, Float.NaN));
    }
    @Test public void accurateStatus() {
        assertEquals("Standort ±12 m", ParkingAccuracy.label(true, true, 12.2f));
        assertEquals("Standortgenauigkeit unbekannt",
                ParkingAccuracy.label(true, false, Float.NaN));
    }
}

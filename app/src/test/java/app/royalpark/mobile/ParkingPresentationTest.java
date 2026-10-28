package app.royalpark.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParkingPresentationTest {
    @Test public void inaccurateGpsDoesNotMisleadAsZeroMeters() {
        assertTrue(ParkingPresentation.distanceText(0,71,true,false).contains("Ortung ungenau"));
        assertTrue(ParkingPresentation.distanceText(34,90,true,false).contains("Ortung ungenau"));
        assertTrue(ParkingPresentation.distanceText(10,8,false,false).contains("Ortung ungenau"));
        assertTrue(ParkingPresentation.distanceText(60,10,true,true).startsWith("ca. "));
    }
    @Test public void accurateGpsUsesMeaningfulDistance() {
        assertEquals("120 m Luftlinie zum Auto",
                ParkingPresentation.distanceText(120,8,true,false));
    }
    @Test public void parkingDurationNatural() {
        long now=1_000_000_000L;
        assertEquals("Gerade geparkt",ParkingPresentation.ageText(now-20_000,now));
        assertEquals("Seit 5 Min. geparkt",ParkingPresentation.ageText(now-300_000,now));
        assertEquals("Seit 3 Std. geparkt",ParkingPresentation.ageText(now-3*3_600_000L,now));
        assertEquals("Seit 2 Tagen geparkt",ParkingPresentation.ageText(now-2*86_400_000L,now));
    }
}

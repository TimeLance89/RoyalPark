package app.royalpark.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationPresentationTest {
    @Test public void noZeroMinutes() {
        assertEquals("< 1 Min · zu Fuß", NavigationPresentation.walkingDuration(33));
        assertEquals("ca. 2 Min · zu Fuß", NavigationPresentation.walkingDuration(100));
    }
    @Test public void noFakeThirteenMetersWithHundredMeterGps() {
        assertEquals("Zielbereich", NavigationPresentation.distanceText(13, 100f));
        assertEquals("Zielbereich", NavigationPresentation.distanceText(20, Float.NaN));
        assertTrue(NavigationPresentation.distanceText(180, 100).startsWith("ca."));
        assertEquals("13 m", NavigationPresentation.distanceText(13, 8));
        assertEquals("GPS ±100 m · ungenau",
            NavigationPresentation.locationHint(13, 100, false, true));
    }
    @Test public void cannotArriveWithCoarseOrSavedApproximateCoordinates() {
        assertFalse(NavigationPresentation.closeToCar(13, 100f, false, true));
        assertFalse(NavigationPresentation.closeToCar(4, 100f, true, false));
        assertFalse(NavigationPresentation.closeToCar(3, 5f, true, true));
        assertFalse(NavigationPresentation.closeToCar(3, 5f, false, false));
        assertTrue(NavigationPresentation.closeToCar(6, 8f, true, false));
    }
    @Test public void conciseInstructions() {
        assertEquals("Route folgen", NavigationPresentation.briefInstruction(null));
        assertEquals("Links abbiegen", NavigationPresentation.briefInstruction(" Links  abbiegen "));
    }
}

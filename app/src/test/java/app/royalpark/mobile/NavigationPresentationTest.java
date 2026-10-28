package app.royalpark.mobile;
import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationPresentationTest {
    @Test public void noZeroMinutes() {
        assertEquals("< 1 Min · zu Fuß", NavigationPresentation.walkingDuration(33));
        assertEquals("ca. 2 Min · zu Fuß", NavigationPresentation.walkingDuration(100));
    }
    @Test public void marksLowAccuracy() {
        assertTrue(NavigationPresentation.distanceText(33, 100).startsWith("≈"));
        assertFalse(NavigationPresentation.distanceText(33, 8).startsWith("≈"));
    }
    @Test public void instructionsNotTechnical() {
        assertEquals("Route folgen", NavigationPresentation.briefInstruction(null));
        assertEquals("Links abbiegen", NavigationPresentation.briefInstruction(" Links  abbiegen "));
    }
}
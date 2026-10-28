package app.royalpark.mobile;
import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationDockTextTest {
    @Test public void clearModeBeforeAnyRoute() {
        assertEquals("↻  Offline-Route", NavigationDockText.routeAction(true, false));
        assertEquals("↻  Online-Route", NavigationDockText.routeAction(false, false));
    }
    @Test public void clearModeWhenRecalculating() {
        assertEquals("↻  Offline neu", NavigationDockText.routeAction(true, true));
        assertEquals("↻  Online neu", NavigationDockText.routeAction(false, true));
    }
}

package app.royalpark.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NavigationMathTest {
    @Test public void distanceZero() { assertEquals(0, NavigationMath.distanceMeters(53.5,8.5,53.5,8.5),0.01); }
    @Test public void distanceNorth() {
        assertEquals(111195, NavigationMath.distanceMeters(0,0,1,0),150);
        assertEquals(0, NavigationMath.bearingDegrees(0,0,1,0),0.001f);
        assertEquals(90, NavigationMath.bearingDegrees(0,0,0,1),0.001f);
    }
    @Test public void headingNormalization() {
        assertEquals(20, NavigationMath.relativeDirection(10,350),0.001f);
        assertEquals(-20, NavigationMath.relativeDirection(350,10),0.001f);
        assertEquals(0, NavigationMath.relativeDirection(30,30),0.001f);
    }
    @Test public void formatDistances() {
        assertEquals("900 m", NavigationMath.formatDistance(900));
        assertEquals("1,5 km", NavigationMath.formatDistance(1500));
    }
    @Test(expected=IllegalArgumentException.class)
    public void coordinatesInvalid() { NavigationMath.distanceMeters(100,0,0,0); }
}

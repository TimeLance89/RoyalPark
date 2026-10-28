package app.royalpark.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public final class GarageLabelsTest {
    @Test public void formatsGarageLocations() {
        assertEquals("", GarageLabels.summary("", ""));
        assertEquals("Ebene -2", GarageLabels.summary(" -2 ", ""));
        assertEquals("Stellplatz B15", GarageLabels.summary("", " B15 "));
        assertEquals("Ebene P3 · Stellplatz A-17", GarageLabels.summary("P3", "A-17"));
    }
    @Test public void cleansTextWithoutLineBreaks() {
        assertEquals("B 13", GarageLabels.clean(" B\n13  "));
        assertEquals(80, GarageLabels.clean("x".repeat(100)).length());
        assertEquals("", GarageLabels.clean(null));
    }
}

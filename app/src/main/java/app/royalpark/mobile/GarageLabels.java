package app.royalpark.mobile;

/** Keeps indoor location labels consistent and bounded across UI, storage and backups. */
public final class GarageLabels {
    private GarageLabels() {}
    public static String clean(String value) {
        if (value == null) return "";
        String trimmed = value.trim().replace('\n', ' ').replace('\r', ' ');
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }
    public static String summary(String floor, String space) {
        String a = clean(floor), b = clean(space);
        if (a.isEmpty()) return b.isEmpty() ? "" : "Stellplatz " + b;
        return b.isEmpty() ? "Ebene " + a : "Ebene " + a + " · Stellplatz " + b;
    }
}

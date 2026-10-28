package app.royalpark.mobile;

import java.util.Locale;

/** Short, accurate navigation copy; avoid "0 min" and "0.0 km". */
public final class NavigationPresentation {
    private NavigationPresentation() {}

    public static String distanceText(double distanceMeters, float reportedAccuracy) {
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) return "–";
        String distance = NavigationMath.formatDistance(distanceMeters);
        if (Float.isFinite(reportedAccuracy) && reportedAccuracy > 50)
            return "≈ " + distance;
        return distance;
    }

    public static String walkingDuration(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) return "Route zu Fuß";
        if (seconds < 60) return "< 1 Min · zu Fuß";
        return "ca. " + Math.max(1L, Math.round(seconds / 60.0)) + " Min · zu Fuß";
    }

    public static String briefInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) return "Route folgen";
        String compact = instruction.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 90) return compact;
        int cutoff = compact.lastIndexOf(' ', 87);
        if (cutoff < 40) cutoff = 87;
        return compact.substring(0, cutoff) + "…";
    }
}

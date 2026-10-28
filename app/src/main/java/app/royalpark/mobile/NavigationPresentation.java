package app.royalpark.mobile;

import java.util.Locale;

/** Short, accurate navigation copy; avoid "0 min" and "0.0 km". */
public final class NavigationPresentation {
    private NavigationPresentation() {}

    public static String distanceText(double distanceMeters, float reportedAccuracy) {
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) return "–";
        // When the GPS uncertainty circle contains the car, a numerical
        // distance would imply a precision the device simply doesn't have.
        if (!Float.isFinite(reportedAccuracy) || reportedAccuracy < 0)
            return "Zielbereich";
        if (reportedAccuracy > 40f && distanceMeters <= reportedAccuracy)
            return "Zielbereich";
        String distance = NavigationMath.formatDistance(distanceMeters);
        return reportedAccuracy > 40f ? "ca. " + distance : distance;
    }

    public static String locationHint(double distanceMeters, float accuracyMeters,
            boolean precisePermission, boolean parkingApproximate) {
        if (!precisePermission || !Float.isFinite(accuracyMeters)
                || accuracyMeters > 40f || accuracyMeters < 0)
            return Float.isFinite(accuracyMeters) && accuracyMeters >= 0
                    ? "GPS ±" + Math.round(accuracyMeters) + " m · ungenau"
                    : "Standort ungenau";
        if (parkingApproximate) return "Parkplatz nur ungefähr gespeichert";
        if (closeToCar(distanceMeters, accuracyMeters, precisePermission, false))
            return "Auto vermutlich in deiner Nähe";
        return "Luftlinie zum Auto";
    }

    /** Never claim an arrival with coarse GPS or an imprecise saved spot. */
    public static boolean closeToCar(double distanceMeters, float accuracyMeters,
            boolean precisePermission, boolean parkingApproximate) {
        return precisePermission && !parkingApproximate
                && Double.isFinite(distanceMeters) && distanceMeters >= 0
                && Float.isFinite(accuracyMeters) && accuracyMeters >= 0
                && accuracyMeters <= 12f && distanceMeters <= 12.0;
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

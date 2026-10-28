package app.royalpark.mobile;

/** Presentation rules for real-world GPS uncertainty and parking age. */
public final class ParkingPresentation {
    private ParkingPresentation() {}

    public static String distanceText(double meters, float accuracyMeters,
            boolean preciseGranted, boolean parkingApproximate) {
        if (!Double.isFinite(meters) || meters < 0)
            return "Entfernung momentan unbekannt";
        final boolean lowPrecision = !preciseGranted || parkingApproximate ||
                !Float.isFinite(accuracyMeters) || accuracyMeters > 40f;
        if (lowPrecision && (!Float.isFinite(accuracyMeters)
                || meters <= Math.max(accuracyMeters, 50f))) {
            return "Auto in deinem Standortbereich (Ortung ungenau)";
        }
        String formatted = NavigationMath.formatDistance(meters);
        return (lowPrecision ? "ca. " : "") + formatted + " Luftlinie zum Auto";
    }

    public static String ageText(long parkedAt, long now) {
        long delta = Math.max(0, now - parkedAt);
        if (delta < 60_000) return "Gerade geparkt";
        long minutes = delta / 60_000L;
        if (minutes < 60) return "Seit " + minutes + " Min. geparkt";
        long hours = delta / 3_600_000L;
        if (hours < 24) return "Seit " + hours + " Std. geparkt";
        long days = delta / 86_400_000L;
        return "Seit " + days + (days == 1 ? " Tag" : " Tagen") + " geparkt";
    }
}

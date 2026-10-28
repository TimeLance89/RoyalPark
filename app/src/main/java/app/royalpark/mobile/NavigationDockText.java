package app.royalpark.mobile;

/** Small human-readable action labels: the selected routing mode is never hidden. */
public final class NavigationDockText {
    private NavigationDockText() {}

    public static String routeAction(boolean offline, boolean existingRoute) {
        if (existingRoute)
            return offline ? "↻  Offline neu" : "↻  Online neu";
        return offline ? "↻  Offline-Route" : "↻  Online-Route";
    }
}

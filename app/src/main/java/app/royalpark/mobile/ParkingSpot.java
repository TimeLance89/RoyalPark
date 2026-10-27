package app.royalpark.mobile;

/** Data created solely by RoyalPark. */
public final class ParkingSpot {
    public final long id;
    public final double latitude;
    public final double longitude;
    public final String note;
    public final long parkedAt;
    public final String source;
    public final long timerAt;
    public final String photoUri;

    ParkingSpot(long id, double latitude, double longitude, String note,
                long parkedAt, String source, long timerAt, String photoUri) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.note = note;
        this.parkedAt = parkedAt;
        this.source = source;
        this.timerAt = timerAt;
        this.photoUri = photoUri;
    }
}

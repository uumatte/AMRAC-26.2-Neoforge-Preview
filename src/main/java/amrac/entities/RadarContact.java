package amrac.entities;

public final class RadarContact {
    /**
     * Contacts and missile locks are keyed by aircraftId (UUID). entityId is -1 for virtual
     * aircraft; keying on it merges them all.
     */
    public final java.util.UUID aircraftId;

    public final int entityId;
    public final double x;
    public final double y;
    public final double z;
    public final float velocityX;
    public final float velocityY;
    public final float velocityZ;
    public final float width;
    public final float height;

    public RadarContact(java.util.UUID aircraftId, int entityId,
                        double x, double y, double z,
                        float velocityX, float velocityY, float velocityZ,
                        float width, float height) {
        this.aircraftId = aircraftId;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.width = width;
        this.height = height;
    }
}

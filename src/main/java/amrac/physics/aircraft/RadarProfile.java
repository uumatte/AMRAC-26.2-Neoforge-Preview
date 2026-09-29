package amrac.physics.aircraft;

import java.util.Map;

public record RadarProfile(double horizontalScanDegrees, double verticalScanDegrees,
                           double scanRateHz, double lockRange,
                           double trackMemorySeconds, int maxContacts,
                           double acquireMargin, double velocityGate,
                           boolean velocityGateLookDownOnly) {
    public RadarProfile(double horizontalScanDegrees, double verticalScanDegrees,
                        double scanRateHz, double lockRange,
                        double trackMemorySeconds, int maxContacts,
                        double acquireMargin) {
        this(horizontalScanDegrees, verticalScanDegrees, scanRateHz, lockRange,
            trackMemorySeconds, maxContacts, acquireMargin, 0.0D, false);
    }

    private static final double TICKS_PER_SECOND = amrac.physics.TickRate.TICKS_PER_SECOND;

    public int trackMemoryTicks() {
        return Math.max(1, (int) Math.round(trackMemorySeconds * TICKS_PER_SECOND));
    }

    public static RadarProfile fromJson(Map<String, Object> root) {
        Map<String, Object> radar = Json.object(root, "radar");
        if (radar == null) {
            radar = Map.of();
        }
        double horizontal = Json.number(radar, "horizontalScan", 140.0D);
        double vertical = Json.number(radar, "verticalScan", 140.0D);
        double rate = Json.number(radar, "scanRateHz", 5.0D);
        double lock = Json.number(radar, "lockRange", 10000.0D);
        double memory = Json.number(radar, "trackMemorySeconds", 0.5D);
        double contacts = Json.number(radar, "maxContacts", 32.0D);
        double margin = Json.number(radar, "acquireMargin", 0.94D);
        double gate = Json.number(radar, "velocityGate", 0.0D);
        boolean lookDown = Json.bool(radar, "velocityGateLookDownOnly", false);
        return new RadarProfile(
            clampAngle(horizontal, 140.0D), clampAngle(vertical, 140.0D),
            positive(rate, 5.0D), positive(lock, 10000.0D),
            positive(memory, 0.5D),
            (int) Math.max(1.0D, Math.min(256.0D, positive(contacts, 32.0D))),
            fraction(margin, 0.94D),
            Double.isFinite(gate) && gate > 0.0D ? gate / TICKS_PER_SECOND : 0.0D,
            lookDown);
    }

    public double azimuthLimit() {
        return Math.toRadians(horizontalScanDegrees * 0.5D);
    }

    public double elevationLimit() {
        return Math.toRadians(verticalScanDegrees * 0.5D);
    }

    public int scanIntervalTicks() {
        return Math.max(1, (int) Math.round(TICKS_PER_SECOND / scanRateHz));
    }

    private static double clampAngle(double value, double fallback) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return fallback;
        }
        return Math.min(value, 360.0D);
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static double fraction(double value, double fallback) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            return fallback;
        }
        return Math.min(value, 1.0D);
    }
}

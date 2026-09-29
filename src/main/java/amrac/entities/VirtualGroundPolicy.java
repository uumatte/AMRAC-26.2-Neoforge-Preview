package amrac.entities;

public final class VirtualGroundPolicy {
    private VirtualGroundPolicy() {
    }

    public static boolean onStrip(double altitude, double strip) {
        if (!Double.isFinite(altitude) || !Double.isFinite(strip)) {
            return false;
        }
        return altitude <= strip;
    }

    public static double heldOnStrip(double altitude, double strip) {
        if (!Double.isFinite(strip)) {
            return altitude;
        }
        return Math.max(altitude, strip);
    }

    public static double heldVerticalSpeed(double verticalSpeed) {
        if (!Double.isFinite(verticalSpeed)) {
            return 0.0D;
        }
        return Math.max(0.0D, verticalSpeed);
    }
}

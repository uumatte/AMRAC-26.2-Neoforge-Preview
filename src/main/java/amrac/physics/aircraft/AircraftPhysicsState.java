package amrac.physics.aircraft;

/**
 * Rate signs: pitch + nose up, yaw + nose left, roll + left wing down, matching the control inputs.
 * AircraftControlInput is the one entry for player, AI and tests, each axis clamped to +-1.
 */
public final class AircraftPhysicsState {
    private final Vec3d velocity;
    private final Vec3d forward;
    private final Vec3d up;
    private final Vec3d right;
    private final double worldY;
    private final double pitchRate;
    private final double yawRate;
    private final double rollRate;

    public AircraftPhysicsState(Vec3d velocity, Vec3d forward, Vec3d up, Vec3d right,
                                double worldY, double pitchRate, double yawRate,
                                double rollRate) {
        this.velocity = velocity == null || !velocity.isFinite() ? Vec3d.ZERO : velocity;
        this.forward = safeAxis(forward, new Vec3d(0.0D, 0.0D, 1.0D));
        this.up = safeAxis(up, new Vec3d(0.0D, 1.0D, 0.0D));
        this.right = safeAxis(right, new Vec3d(1.0D, 0.0D, 0.0D));
        this.worldY = Double.isFinite(worldY) ? worldY : 0.0D;
        this.pitchRate = finite(pitchRate);
        this.yawRate = finite(yawRate);
        this.rollRate = finite(rollRate);
    }

    public Vec3d velocity() {
        return velocity;
    }

    public Vec3d forward() {
        return forward;
    }

    public Vec3d up() {
        return up;
    }

    public Vec3d right() {
        return right;
    }

    public double worldY() {
        return worldY;
    }

    public double pitchRate() {
        return pitchRate;
    }

    public double yawRate() {
        return yawRate;
    }

    public double rollRate() {
        return rollRate;
    }

    public double airspeed() {
        return velocity.length();
    }

    private static Vec3d safeAxis(Vec3d axis, Vec3d fallback) {
        if (axis == null || !axis.isFinite() || axis.lengthSquared() < 1.0E-9D) {
            return fallback;
        }
        return axis.normalize();
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}

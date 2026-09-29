package amrac.client;

import amrac.entities.ai.AiPilotPolicy;

public final class AutoLevelController {
    public static final int IDLE_TICKS = 60;

    public static final double MAX_LOAD_G = 4.5D;

    public static final double MIN_LOAD_G = -1.0D;

    static final double STICK_PER_G = 0.3D;

    static final double MAX_STICK = 0.4D;

    private boolean enabled;
    private double targetAltitude;
    private int idleTicks;
    private boolean flying;

    private float pitch;
    private float roll;

    public boolean enabled() {
        return enabled;
    }

    public boolean flying() {
        return enabled && flying;
    }

    public double targetAltitude() {
        return targetAltitude;
    }

    public float pitch() {
        return pitch;
    }

    public float roll() {
        return roll;
    }

    public void toggle(double altitude) {
        enabled = !enabled;
        targetAltitude = altitude;
        idleTicks = 0;
        flying = false;
    }

    public void reset() {
        enabled = false;
        idleTicks = 0;
        flying = false;
    }

    public boolean tick(boolean pilotFlying, boolean onSurface,
                        double verticalSpeed, double speed, double rightY,
                        double upY, double rollRate, double loadG) {
        if (!enabled || pilotFlying || onSurface) {
            idleTicks = 0;
            flying = false;
            return false;
        }
        if (idleTicks < IDLE_TICKS) {
            idleTicks++;
        }
        flying = idleTicks >= IDLE_TICKS;
        if (!flying) {
            return false;
        }
        if (AiPilotPolicy.inverted(upY)) {
            roll = AiPilotPolicy.upsetRoll(rightY);
            pitch = 0.0F;
            return true;
        }
        double bank = AiPilotPolicy.bankAngle(rightY, upY);
        roll = AiPilotPolicy.rollInput(0.0D, bank, rollRate);
        pitch = limited(AiPilotPolicy.climbAnglePitch(
            AiPilotPolicy.flightPathAngle(verticalSpeed, speed), 0.0D, bank), loadG);
        return true;
    }

    static float limited(float command, double loadG) {
        if (!Double.isFinite(loadG)) {
            return (float) Math.max(-MAX_STICK, Math.min(MAX_STICK, command));
        }
        double most = Math.min(MAX_STICK,
            Math.max(0.0D, (MAX_LOAD_G - loadG) * STICK_PER_G));
        double least = Math.max(-MAX_STICK,
            Math.min(0.0D, (MIN_LOAD_G - loadG) * STICK_PER_G));
        return (float) Math.max(least, Math.min(most, command));
    }
}

package amrac.client;

/**
 * Linked by the BVR Replay mod: keep this class's name, package and public members, or replays
 * crash with NoClassDefFoundError.
 */
public final class SoundMixPolicy {
    public static final int MAX_THROTTLE = 100;

    public static final double ENGINE_IDLE_VOLUME = 0.092D;
    public static final double ENGINE_FULL_VOLUME = 0.263D;

    public static final double ENGINE_IDLE_PITCH = 0.72D;
    public static final double ENGINE_FULL_PITCH = 1.32D;

    public static final double ENGINE_RAM_PITCH = 0.12D;

    public static final double AIRFLOW_START_FRACTION = 0.35D;
    public static final double AIRFLOW_FULL_FRACTION = 1.00D;

    public static final double AIRFLOW_MAX_VOLUME = 0.223D;

    public static final double AIRFLOW_MIN_PITCH = 0.70D;
    public static final double AIRFLOW_MAX_PITCH = 1.45D;

    public static final double COCKPIT_ENGINE_SCALE = 0.45D;
    public static final double COCKPIT_AIRFLOW_SCALE = 0.40D;

    public static final double AIRFLOW_CUTOFF_VOLUME = 0.02D;

    private SoundMixPolicy() {
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }

    public static double speedFraction(double speed, double maxSpeed) {
        if (!(maxSpeed > 1.0E-6D) || !Double.isFinite(speed)
            || !Double.isFinite(maxSpeed)) {
            return 0.0D;
        }
        return Math.max(0.0D, speed) / maxSpeed;
    }

    private static double throttleFraction(int throttle) {
        return clamp01((double) throttle / MAX_THROTTLE);
    }

    public static double engineVolume(int throttle) {
        return ENGINE_IDLE_VOLUME
            + (ENGINE_FULL_VOLUME - ENGINE_IDLE_VOLUME) * throttleFraction(throttle);
    }

    public static double enginePitch(int throttle, double speedFraction) {
        double t = throttleFraction(throttle);
        return ENGINE_IDLE_PITCH + (ENGINE_FULL_PITCH - ENGINE_IDLE_PITCH) * t
            + ENGINE_RAM_PITCH * clamp01(speedFraction);
    }

    public static double airflowVolume(double speedFraction) {
        if (speedFraction <= AIRFLOW_START_FRACTION) {
            return 0.0D;
        }
        double span = AIRFLOW_FULL_FRACTION - AIRFLOW_START_FRACTION;
        double f = clamp01((speedFraction - AIRFLOW_START_FRACTION) / span);
        return AIRFLOW_MAX_VOLUME * f * f;
    }

    public static double airflowPitch(double speedFraction) {
        return AIRFLOW_MIN_PITCH
            + (AIRFLOW_MAX_PITCH - AIRFLOW_MIN_PITCH) * clamp01(speedFraction);
    }

    public static boolean airflowAudible(double speedFraction) {
        return airflowVolume(speedFraction) > AIRFLOW_CUTOFF_VOLUME;
    }

    public static float gunPitch(int shot) {
        int h = shot * 1103515245 + 12345;
        h ^= (h >>> 16);
        double unit = (h & 0xFFFF) / 65535.0D;
        return (float) (GUN_BASE_PITCH + (unit - 0.5D) * GUN_PITCH_SPREAD);
    }

    public static final double GUN_BASE_PITCH = 1.55D;
    public static final double GUN_PITCH_SPREAD = 0.22D;

    public static final float GUN_REPORT_VOLUME = 0.105F;

    public static final float GUN_FIRE_VOLUME = 0.55F;
    public static final float GUN_COCKPIT_FIRE_VOLUME = 0.7F;

    public static float gunSamplePitch(int shot) {
        return (float) (gunPitch(shot) / GUN_BASE_PITCH);
    }
    public static final float GUN_ACTION_VOLUME = 0.065F;

    public static final float GUN_COCKPIT_REPORT_VOLUME = 0.155F;
    public static final float GUN_COCKPIT_ACTION_VOLUME = 0.095F;

    public static final float GUN_COCKPIT_PITCH_SCALE = 0.82F;

    public static final double AFTERBURNER_MAX_VOLUME = 0.326D;
    public static final double AFTERBURNER_PITCH = 1.0D;
    public static final double AFTERBURNER_PITCH_SPOOL = 0.06D;
    public static final double COCKPIT_AFTERBURNER_SCALE = 0.55D;

    public static final float AFTERBURNER_LIGHT_VOLUME = 0.55F;
    public static final float AFTERBURNER_LIGHT_PITCH = 0.55F;
    public static final float AFTERBURNER_CRACK_VOLUME = 0.30F;
    public static final float AFTERBURNER_CRACK_PITCH = 0.70F;
    public static final float AFTERBURNER_SHUTDOWN_VOLUME = 0.5F;
    public static final float AFTERBURNER_SHUTDOWN_PITCH = 0.45F;
    public static final float AFTERBURNER_COCKPIT_LIGHT_VOLUME = 0.6F;

    public static final float SONIC_BOOM_VOLUME = 1.0F;

    public static final float SONIC_BOOM_PITCH = 1.0F;

    public static final boolean SUPERSONIC_SILENCE = false;

    public static final float SUPERSONIC_RUMBLE_VOLUME = 1.0F;

    public static final double SUPERSONIC_ENGINE_SHARE = 0.25D;

    public static double supersonicEngineShare(double current, boolean supersonic) {
        double target = supersonic ? SUPERSONIC_ENGINE_SHARE : 1.0D;
        double step = (1.0D - SUPERSONIC_ENGINE_SHARE)
            / Math.max(1, SUPERSONIC_RUMBLE_FADE_TICKS);
        if (!Double.isFinite(current)) {
            return target;
        }
        return Math.abs(target - current) <= step ? target
            : current + Math.copySign(step, target - current);
    }
    public static final float SUPERSONIC_RUMBLE_PITCH = 1.0F;

    public static final int SUPERSONIC_RUMBLE_FADE_TICKS = 14;

    public static final float MISSILE_IGNITION_VOLUME = 0.9F;
    public static final float MISSILE_WHOOSH_VOLUME = 1.4F;
    public static final float MISSILE_THUMP_VOLUME = 0.5F;

    public static final float MISSILE_LAUNCH_COCKPIT_VOLUME = 1.0F;

    public static final float RWR_WARNING_VOLUME = 0.8F;
    public static final float BOUNDARY_WARNING_VOLUME = 0.7F;
    public static final int WARNING_FADE_TICKS = 4;
}

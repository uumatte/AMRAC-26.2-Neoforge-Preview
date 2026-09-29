package amrac.entities.ai;

import amrac.weapons.CountermeasureService;
import amrac.weapons.MissileProfile;
import amrac.weapons.SeekerPolicy;
import amrac.weapons.SeekerType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class AiNotchPolicy {
    public static final int RELEASES_PER_SECOND = 7;

    public static final double FLARE_RANGE = 2000.0D;

    private AiNotchPolicy() {
    }

    @Nullable
    public static double[] goal(double x, double z, double courseX, double courseZ,
                                double missileX, double missileZ, double error,
                                int latchedSide, double altitude, double distance) {
        return goal(x, z, courseX, courseZ, missileX, missileZ, error, latchedSide,
            0.0D, altitude, distance);
    }

    @Nullable
    public static double[] goal(double x, double z, double courseX, double courseZ,
                                double missileX, double missileZ, double error,
                                int latchedSide, double trim, double altitude,
                                double distance) {
        double lx = missileX - x;
        double lz = missileZ - z;
        double length = Math.hypot(lx, lz);
        if (!(length > 1.0E-6D)) {
            return null;
        }
        lx /= length;
        lz /= length;
        double beamX = -lz;
        double beamZ = lx;
        if (beamX * courseX + beamZ * courseZ < 0.0D) {
            beamX = -beamX;
            beamZ = -beamZ;
        }
        double side = latchedSide != 0 ? Math.signum(latchedSide)
            : sideFor(courseX, courseZ, lx, lz);
        double angle = side * error - trim;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dirX = beamX * cos + lx * sin;
        double dirZ = beamZ * cos + lz * sin;
        return new double[] {x + dirX * distance, altitude, z + dirZ * distance};
    }

    public static double verticalTrim(double x, double y, double z,
                                      double velocityX, double velocityY,
                                      double velocityZ, double missileX,
                                      double missileY, double missileZ,
                                      int side, double error) {
        double dx = missileX - x;
        double dy = missileY - y;
        double dz = missileZ - z;
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontal = Math.hypot(dx, dz);
        double horizontalSpeed = Math.hypot(velocityX, velocityZ);
        if (!(range > 1.0D) || !(horizontal > 1.0D)
                || !(horizontalSpeed > 1.0E-3D)) {
            return 0.0D;
        }
        double speed = Math.sqrt(horizontalSpeed * horizontalSpeed
            + velocityY * velocityY);
        double commanded = Math.signum(side) * error;
        double sine = (speed * Math.sin(commanded) - velocityY * dy / range)
            / (horizontalSpeed * horizontal / range);
        return commanded - Math.asin(Math.max(-VERTICAL_TRIM_LIMIT,
            Math.min(VERTICAL_TRIM_LIMIT, sine)));
    }

    static final double VERTICAL_TRIM_LIMIT = 0.9D;

    public static final double THREAT_HOLD_RANGE = 4000.0D;

    public static final double LAST_DITCH_SECONDS = 1.0D;

    public static final double LAST_DITCH_MIN_SPEED = 120.0D;

    public static boolean lastDitch(AiThreat threat, double seconds, double x,
                                    double y, double z, double velocityX,
                                    double velocityY, double velocityZ) {
        if (threat == null || !(seconds > 0.0D)) {
            return false;
        }
        double dx = x - threat.x();
        double dy = y - threat.y();
        double dz = z - threat.z();
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(range) || range < 1.0E-6D) {
            return false;
        }
        double closing = (dx * (threat.velocityX() - velocityX)
            + dy * (threat.velocityY() - velocityY)
            + dz * (threat.velocityZ() - velocityZ)) / range;
        if (!(closing > 1.0E-6D)) {
            return false;
        }
        double speed = Math.sqrt(velocityX * velocityX + velocityY * velocityY
            + velocityZ * velocityZ) * 20.0D;
        return speed >= LAST_DITCH_MIN_SPEED * amrac.physics.aircraft.SpeedScale.current()
            && range / closing <= seconds * 20.0D;
    }

    /**
     * Whether the AI evades comes straight from MissileWarningService.warnMode (the player's RWR
     * rule), so changing RWR warnings changes AI behaviour too.
     */
    public static boolean rwrSounding(@Nullable amrac.weapons.MissileProfile profile,
                                      @Nullable amrac.weapons.SeekerState seeker,
                                      double distance) {
        if (profile == null || seeker == null) {
            return false;
        }
        int mode = amrac.weapons.MissileWarningService.warnMode(
            profile.seekerType, seeker);
        return mode == amrac.weapons.MissileWarningService.WARN_ALWAYS
            || (mode == amrac.weapons.MissileWarningService.WARN_IN_RANGE
                && amrac.weapons.MissileWarningService.warns(distance));
    }

    public static double urgency(boolean sounding,
                                 double missileX, double missileY, double missileZ,
                                 double missileVx, double missileVy, double missileVz,
                                 double x, double y, double z,
                                 double vx, double vy, double vz) {
        double dx = x - missileX, dy = y - missileY, dz = z - missileZ;
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double closing = range > 1.0E-6D
            ? (dx * (missileVx - vx) + dy * (missileVy - vy) + dz * (missileVz - vz))
                / range : 0.0D;
        double seconds = closing > 1.0E-6D
            ? range / closing / 20.0D : UNLOCKED_URGENCY * 0.5D;
        return (sounding ? 0.0D : UNLOCKED_URGENCY) + seconds;
    }

    public static final double UNLOCKED_URGENCY = 100000.0D;

    public static boolean released(boolean sounding,
                                   @Nullable amrac.weapons.SeekerState seeker,
                                   double missileX, double missileY, double missileZ,
                                   double missileVx, double missileVy, double missileVz,
                                   double x, double y, double z,
                                   double vx, double vy, double vz) {
        if (sounding) {
            return false;
        }
        if (seeker == null || seeker.spent) {
            return true;
        }
        if (seeker.tracking && seeker.capturedDecoy < 0) {
            return false;
        }
        double closing = (x - missileX) * (missileVx - vx)
            + (y - missileY) * (missileVy - vy)
            + (z - missileZ) * (missileVz - vz);
        return !(closing > 0.0D);
    }

    public static double signedBeamAngle(Vec3 aircraft, Vec3 velocity, Vec3 missile) {
        Vec3 toMissile = missile.subtract(aircraft);
        double speed = velocity.length();
        double length = toMissile.length();
        if (speed < 1.0E-9D || length < 1.0E-9D) {
            return 0.0D;
        }
        double radial = velocity.dot(toMissile) / (speed * length);
        return Math.asin(Math.max(-1.0D, Math.min(1.0D, radial)));
    }

    public static final double TRIM_GAIN = 0.05D;

    public static final double TRIM_CAPTURE = Math.toRadians(8.0D);

    public static final double TRIM_LIMIT = Math.toRadians(15.0D);

    public static double trim(double trim, double measured, double commanded) {
        double wrong = measured - commanded;
        if (!Double.isFinite(wrong) || Math.abs(wrong) > TRIM_CAPTURE) {
            return trim;
        }
        return Math.max(-TRIM_LIMIT, Math.min(TRIM_LIMIT, trim + TRIM_GAIN * wrong));
    }

    public static int sideFor(double courseX, double courseZ, double toMissileX,
                              double toMissileZ) {
        return courseX * toMissileX + courseZ * toMissileZ > 0.0D ? 1 : -1;
    }

    public static final double CHAFF_LEAD = 2.0D;

    @Nullable
    public static CountermeasureService.Kind wanted(MissileProfile profile,
                                                    Vec3 missile, Vec3 aircraft,
                                                    Vec3 aircraftVelocity) {
        if (profile.seekerType == SeekerType.IR) {
            return aircraft.distanceTo(missile)
                    <= FLARE_RANGE * amrac.physics.aircraft.SpeedScale.current()
                ? CountermeasureService.Kind.FLARE : null;
        }
        Vec3 los = aircraft.subtract(missile);
        double gate = SeekerPolicy.burnThroughGate(profile.velocityGate,
            profile.burnThroughRange, los.length()) * CHAFF_LEAD;
        return SeekerPolicy.velocityGated(gate, profile.velocityGateLookDownOnly,
            aircraftVelocity, los, missile.y, aircraft.y)
            ? CountermeasureService.Kind.CHAFF : null;
    }

    public static int advance(int phase) {
        return phase + RELEASES_PER_SECOND;
    }

    public static boolean releases(int phase) {
        return phase >= 20;
    }
}

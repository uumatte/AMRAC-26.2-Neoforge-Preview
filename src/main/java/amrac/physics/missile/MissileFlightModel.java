package amrac.physics.missile;

import amrac.physics.aircraft.AtmosphereModel;
import amrac.physics.aircraft.Vec3d;

/**
 * Handles only the axis (thrust, drag, mass); guidance is in MissilePolicy. Normal force goes to
 * the turn model only as available g, never added as a vector, or the missile turns twice.
 */
public final class MissileFlightModel {
    public static final double FIXED_TIME_STEP = amrac.physics.TickRate.SECONDS_PER_TICK;

    public static final double LAUNCH_LOAD_LIMIT_G = 10.0D;

    public static final double LAUNCH_LOAD_LIMIT_SECONDS = 0.5D;

    public static double launchLoadLimitG(double secondsSinceLaunch) {
        return secondsSinceLaunch < LAUNCH_LOAD_LIMIT_SECONDS - 1.0E-9D
            ? LAUNCH_LOAD_LIMIT_G : Double.POSITIVE_INFINITY;
    }

    private static final double MINIMUM_AIRSPEED = 1.0E-4D;

    private final MissilePhysicsProfile profile;

    public MissileFlightModel(MissilePhysicsProfile profile) {
        this.profile = profile;
    }

    public MissilePhysicsProfile profile() {
        return profile;
    }

    /**
     * Anything that measures missile performance (top speed, g, no-escape zone, console charts)
     * uses this; a chart with its own copy once kept accelerating after burnout.
     */
    public Vec3d advanceLevel(Vec3d velocity, Vec3d axis, double worldY,
                              double secondsSinceLaunch,
                              AtmosphereModel atmosphere, double dt) {
        Vec3d next = advance(velocity, axis, worldY, secondsSinceLaunch,
            atmosphere, dt);
        if (axis == null || !next.isFinite()) {
            return next;
        }
        return axis.scale(Math.max(0.0D, next.dot(axis)));
    }

    public Vec3d advance(Vec3d velocity, Vec3d axis, double worldY,
                         double secondsSinceLaunch, AtmosphereModel atmosphere,
                         double dt) {
        if (velocity == null || !velocity.isFinite()) {
            return Vec3d.ZERO;
        }
        double timeStep = Double.isFinite(dt) && dt > 0.0D ? dt : FIXED_TIME_STEP;
        double density = atmosphere.density(worldY);
        double speedOfSound = atmosphere.speedOfSound(worldY);
        double gravity = atmosphere.gravity();
        double mass = Math.max(profile.mass(), 1.0E-6D);

        double airspeed = velocity.length();
        double mach = speedOfSound > 1.0E-6D ? airspeed / speedOfSound : 0.0D;

        Vec3d force = Vec3d.ZERO;
        double thrust = profile.thrust(secondsSinceLaunch);
        if (thrust > 0.0D && axis != null && axis.isFinite() &&
            axis.lengthSquared() > 1.0E-9D) {
            force = axis.normalize().scale(thrust);
        }

        double drag = 0.0D;
        if (airspeed > MINIMUM_AIRSPEED) {
            double dynamicPressure = 0.5D * density * airspeed * airspeed;
            drag = dynamicPressure * profile.referenceArea() *
                profile.dragCoefficient(mach);
            force = force.add(velocity.scale(-drag / airspeed));
        }

        force = force.add(new Vec3d(0.0D, -mass * gravity, 0.0D));

        Vec3d acceleration = force.scale(1.0D / mass);
        if (!acceleration.isFinite()) {
            return velocity;
        }
        Vec3d next = velocity.add(acceleration.scale(timeStep));
        if (!next.isFinite()) {
            return velocity;
        }
        if (thrust <= 0.0D && next.dot(velocity) < 0.0D) {
            return Vec3d.ZERO;
        }
        return next;
    }

    public double availableLoadG(double airspeed, double worldY,
                                 AtmosphereModel atmosphere) {
        if (!Double.isFinite(airspeed) || airspeed <= 0.0D) {
            return 0.0D;
        }
        double density = atmosphere.density(worldY);
        double speedOfSound = atmosphere.speedOfSound(worldY);
        double gravity = atmosphere.gravity();
        double mach = speedOfSound > 1.0E-6D ? airspeed / speedOfSound : 0.0D;
        double dynamicPressure = 0.5D * density * airspeed * airspeed;
        double normalForce = dynamicPressure * profile.referenceArea() *
            profile.normalForceCoefficient(profile.maxAoA()) *
            profile.controlAuthority(mach);
        double weight = profile.mass() * gravity;
        if (weight <= 1.0E-9D) {
            return 0.0D;
        }
        double load = normalForce / weight;
        return Double.isFinite(load) ? Math.min(load, profile.maxG()) : 0.0D;
    }

    public double availableLoadG(double airspeed, double worldY,
                                 AtmosphereModel atmosphere,
                                 double secondsSinceLaunch) {
        return Math.min(availableLoadG(airspeed, worldY, atmosphere),
            launchLoadLimitG(secondsSinceLaunch));
    }

    public double inducedDragDeceleration(double lateralAcceleration,
                                          double airspeed, double worldY,
                                          AtmosphereModel atmosphere,
                                          double factor) {
        if (!Double.isFinite(lateralAcceleration) || !Double.isFinite(airspeed)
            || !Double.isFinite(factor) || factor <= 0.0D
            || airspeed <= MINIMUM_AIRSPEED) {
            return 0.0D;
        }
        double lateral = Math.abs(lateralAcceleration);
        double speedOfSound = atmosphere.speedOfSound(worldY);
        double mach = speedOfSound > 1.0E-6D ? airspeed / speedOfSound : 0.0D;
        double dynamicPressure = 0.5D * atmosphere.density(worldY) *
            airspeed * airspeed;
        double forcePerCoefficient = dynamicPressure * profile.referenceArea() *
            profile.controlAuthority(mach);
        double angle = forcePerCoefficient > 1.0E-9D
            ? profile.angleOfAttackFor(
                profile.mass() * lateral / forcePerCoefficient)
            : profile.maxAoA();
        double deceleration = factor * lateral *
            Math.tan(Math.toRadians(Math.min(angle, 89.0D)));
        return Double.isFinite(deceleration) ? deceleration : 0.0D;
    }

    public MissileFlightState state(Vec3d velocity, Vec3d axis, double worldY,
                                    double secondsSinceLaunch,
                                    AtmosphereModel atmosphere) {
        double density = atmosphere.density(worldY);
        double speedOfSound = atmosphere.speedOfSound(worldY);
        double airspeed = velocity == null ? 0.0D : velocity.length();
        double mach = speedOfSound > 1.0E-6D ? airspeed / speedOfSound : 0.0D;
        double dynamicPressure = 0.5D * density * airspeed * airspeed;
        double angleOfAttack = angleOfAttackDegrees(velocity, axis);
        return new MissileFlightState(worldY, density, speedOfSound, airspeed, mach,
            angleOfAttack, dynamicPressure,
            profile.dragCoefficient(mach),
            dynamicPressure * profile.referenceArea() * profile.dragCoefficient(mach),
            profile.normalForceCoefficient(angleOfAttack),
            profile.thrust(secondsSinceLaunch),
            profile.mass() * atmosphere.gravity(),
            availableLoadG(airspeed, worldY, atmosphere),
            profile.controlAuthority(mach));
    }

    public static double angleOfAttackDegrees(Vec3d velocity, Vec3d axis) {
        if (velocity == null || axis == null || !velocity.isFinite() ||
            !axis.isFinite()) {
            return 0.0D;
        }
        double speed = velocity.length();
        double axisLength = axis.length();
        if (speed < MINIMUM_AIRSPEED || axisLength < 1.0E-9D) {
            return 0.0D;
        }
        double cosine = velocity.dot(axis) / (speed * axisLength);
        cosine = cosine < -1.0D ? -1.0D : Math.min(cosine, 1.0D);
        return Math.toDegrees(Math.acos(cosine));
    }
}

package amrac.physics.missile;

import amrac.physics.aircraft.Vec3d;

/** Bounded steering torque and angular damping for the game's body axis. */
public final class MissileAngularDynamics {
    private MissileAngularDynamics() { }

    public record Result(Vec3d axis, Vec3d angularVelocity) { }

    /**
     * Angular velocity is a world-space rotation vector in radians/second.
     * The desired axis is one tick's commanded heading. Torque and damping
     * scale with pressure; inertia does not. Values are game presets, not
     * measured coefficients of the named weapons.
     */
    public static Result step(MissileInertiaProfile profile, Vec3d axis,
                              Vec3d desired, Vec3d angularVelocity,
                              double pressureFactor, double dt) {
        Vec3d nose = unitOr(axis, new Vec3d(0.0D, 0.0D, 1.0D));
        Vec3d omega = angularVelocity != null && angularVelocity.isFinite()
            && Double.isFinite(angularVelocity.lengthSquared())
                ? angularVelocity : Vec3d.ZERO;
        // An axis has no roll attitude: retain only pitch/yaw motion.
        omega = omega.subtract(nose.scale(omega.dot(nose)));
        if (!Double.isFinite(dt) || dt <= 0.0D) {
            return new Result(nose, omega);
        }
        Vec3d demand = Vec3d.ZERO;
        if (desired != null && desired.isFinite() && desired.lengthSquared() > 1.0E-18D) {
            Vec3d wanted = desired.normalize();
            double cosine = Math.clamp(nose.dot(wanted), -1.0D, 1.0D);
            Vec3d cross = nose.cross(wanted);
            double sine = cross.length();
            double angle = Math.atan2(sine, cosine);
            if (angle > 1.0E-12D) {
                // Opposite vectors still need a deterministic plane to turn in.
                Vec3d direction = sine > 1.0E-9D ? cross.scale(1.0D / sine)
                    : nose.cross(Math.abs(nose.y) < 0.9D
                        ? new Vec3d(0.0D, 1.0D, 0.0D)
                        : new Vec3d(1.0D, 0.0D, 0.0D)).normalize();
                demand = direction.scale(angle / dt);
            }
        }

        // Clamp the drive torque, then solve I*d(omega)/dt = drive - D*omega.
        // Analytic damping stays stable even when a tick spans many time constants.
        double terminalRate = profile.controlMoment() / profile.damping();
        if (demand.length() > terminalRate) {
            demand = demand.normalize().scale(terminalRate);
        }
        double pressure = Double.isFinite(pressureFactor)
            ? Math.clamp(pressureFactor, 0.0D, 20.0D) : 0.0D;
        double decayRate = profile.damping() / profile.turnInertia() * pressure;
        double decay = Math.exp(-decayRate * dt);
        double integral = decayRate > 1.0E-9D
            ? -Math.expm1(-decayRate * dt) / decayRate : dt;
        Vec3d nextOmega = demand.add(omega.subtract(demand).scale(decay));
        Vec3d rotation = demand.scale(dt).add(omega.subtract(demand).scale(integral));
        Vec3d nextAxis = rotate(nose, rotation).normalize();
        nextOmega = nextOmega.subtract(nextAxis.scale(nextOmega.dot(nextAxis)));
        if (!nextAxis.isFinite() || !nextOmega.isFinite()) {
            return new Result(nose, Vec3d.ZERO);
        }
        return new Result(nextAxis, nextOmega);
    }

    private static Vec3d unitOr(Vec3d value, Vec3d fallback) {
        return value != null && value.isFinite() && Double.isFinite(value.lengthSquared())
            && value.lengthSquared() > 1.0E-18D ? value.normalize() : fallback;
    }

    private static Vec3d rotate(Vec3d axis, Vec3d rotation) {
        double angle = rotation.length();
        if (angle < 1.0E-12D) {
            return axis;
        }
        Vec3d around = rotation.scale(1.0D / angle);
        double cosine = Math.cos(angle);
        return axis.scale(cosine).add(around.cross(axis).scale(Math.sin(angle)))
            .add(around.scale(around.dot(axis) * (1.0D - cosine)));
    }
}

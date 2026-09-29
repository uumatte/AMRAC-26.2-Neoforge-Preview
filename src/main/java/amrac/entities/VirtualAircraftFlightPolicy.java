package amrac.entities;

public final class VirtualAircraftFlightPolicy {
    public static final double MAX_TURN_RATE = 0.035D;

    public static final double MAX_CLIMB_RATE = 0.55D;

    public static final double CLIMB_GAIN = 0.02D;

    public static final double SPEED_GAIN = 0.03D;

    public static final double CLIMB_RESPONSE = 0.12D;

    private VirtualAircraftFlightPolicy() {
    }

    public static void step(double[] position, double[] velocity,
                            double goalX, double goalZ, double targetAltitude,
                            double cruiseSpeed) {
        if (!finite(position) || !finite(velocity)) {
            return;
        }
        double vx = velocity[0];
        double vz = velocity[2];
        double horizontal = Math.sqrt(vx * vx + vz * vz);
        if (!(horizontal > 1.0E-6D)) {
            double dx = goalX - position[0];
            double dz = goalZ - position[2];
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > 1.0E-6D) {
                vx = dx / length * cruiseSpeed;
                vz = dz / length * cruiseSpeed;
                horizontal = cruiseSpeed;
            } else {
                return;
            }
        }

        double heading = Math.atan2(vx, vz);
        double desired = Math.atan2(goalX - position[0], goalZ - position[2]);
        double error = wrap(desired - heading);
        double turn = Math.max(-MAX_TURN_RATE, Math.min(MAX_TURN_RATE, error));
        heading += turn;

        double speed = horizontal;
        speed += (cruiseSpeed - speed) * SPEED_GAIN;
        if (!(speed > 0.0D)) {
            speed = cruiseSpeed;
        }

        double maxClimb = MAX_CLIMB_RATE * amrac.physics.aircraft.SpeedScale.current();
        double wanted = clamp((targetAltitude - position[1]) * CLIMB_GAIN,
            -maxClimb, maxClimb);
        double climb = velocity[1] + (wanted - velocity[1]) * CLIMB_RESPONSE;
        climb = clamp(climb, -maxClimb, maxClimb);

        velocity[0] = Math.sin(heading) * speed;
        velocity[1] = climb;
        velocity[2] = Math.cos(heading) * speed;

        position[0] += velocity[0];
        position[1] += velocity[1];
        position[2] += velocity[2];
    }

    public static void attitudeFor(double[] velocity, double[] out) {
        if (!finite(velocity)) {
            out[0] = 0.0D;
            out[1] = 0.0D;
            return;
        }
        double horizontal = Math.sqrt(velocity[0] * velocity[0]
            + velocity[2] * velocity[2]);
        out[0] = Math.toDegrees(Math.atan2(velocity[0], velocity[2]));
        out[1] = horizontal > 1.0E-6D
            ? Math.toDegrees(Math.atan2(velocity[1], horizontal)) : 0.0D;
    }

    public static double wrap(double radians) {
        double a = radians;
        while (a > Math.PI) {
            a -= 2.0D * Math.PI;
        }
        while (a < -Math.PI) {
            a += 2.0D * Math.PI;
        }
        return a;
    }

    private static double clamp(double v, double low, double high) {
        return v < low ? low : Math.min(v, high);
    }

    private static boolean finite(double[] v) {
        return v.length == 3 && Double.isFinite(v[0]) && Double.isFinite(v[1])
            && Double.isFinite(v[2]);
    }
}

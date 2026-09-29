package amrac.weapons;

public final class GunLeadPolicy {
    public static final double MAX_RANGE = 200.0D;

    /**
     * GunLeadPolicy owns the bullet range and MachineGunBulletEntity uses it; range is spent by
     * muzzle speed. Lead and bullet lifetime must follow the same rule, or the sight shows shots
     * that can't land.
     */
    public static double leashSpent(double muzzleSpeed, double remaining) {
        if (!Double.isFinite(muzzleSpeed) || muzzleSpeed <= 0.0D
            || !Double.isFinite(remaining) || remaining <= 0.0D) {
            return 0.0D;
        }
        return Math.min(muzzleSpeed, remaining);
    }

    public static final int MAX_LIFE_TICKS = 80;

    public static final double MIN_RANGE = 1.0D;

    private GunLeadPolicy() {
    }

    public static double interceptTime(double[] relativePosition,
                                       double[] relativeVelocity,
                                       double projectileSpeed) {
        if (!finite(relativePosition) || !finite(relativeVelocity)
            || !Double.isFinite(projectileSpeed) || projectileSpeed <= 1.0E-6D) {
            return Double.NaN;
        }
        double rr = dot(relativePosition, relativePosition);
        if (rr < 1.0E-9D) {
            return Double.NaN;
        }
        double rw = dot(relativePosition, relativeVelocity);
        double ww = dot(relativeVelocity, relativeVelocity);

        double a = projectileSpeed * projectileSpeed - ww;
        if (Math.abs(a) < 1.0E-9D) {
            if (Math.abs(rw) < 1.0E-12D) {
                return Double.NaN;
            }
            double t = rr / (-2.0D * rw);
            return t > 0.0D && Double.isFinite(t) ? t : Double.NaN;
        }
        double discriminant = rw * rw + a * rr;
        if (discriminant < 0.0D) {
            return Double.NaN;
        }
        double root = Math.sqrt(discriminant);
        double first = (rw + root) / a;
        double second = (rw - root) / a;

        double best = Double.NaN;
        if (first > 1.0E-6D) {
            best = first;
        }
        if (second > 1.0E-6D && (Double.isNaN(best) || second < best)) {
            best = second;
        }
        return best;
    }

    public static boolean leadPoint(double[] relativePosition,
                                    double[] relativeVelocity,
                                    double projectileSpeed, double[] out) {
        double t = interceptTime(relativePosition, relativeVelocity,
            projectileSpeed);
        if (Double.isNaN(t)) {
            return false;
        }
        out[0] = relativePosition[0] + relativeVelocity[0] * t;
        out[1] = relativePosition[1] + relativeVelocity[1] * t;
        out[2] = relativePosition[2] + relativeVelocity[2] * t;
        return finite(out);
    }

    public static boolean withinRange(double[] leadOffset) {
        if (!finite(leadOffset)) {
            return false;
        }
        double distance = Math.sqrt(dot(leadOffset, leadOffset));
        return distance >= MIN_RANGE && distance <= maxRange();
    }

    public static double maxRange() {
        return MAX_RANGE * amrac.physics.aircraft.SpeedScale.current();
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static boolean finite(double[] v) {
        return v.length == 3 && Double.isFinite(v[0]) && Double.isFinite(v[1])
            && Double.isFinite(v[2]);
    }
}

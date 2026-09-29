package amrac.client;

import amrac.weapons.MissilePolicy;

public final class SeekerViewPolicy {
    public static final double FIELD_OF_VIEW = MissilePolicy.SEEKER_GIMBAL_LIMIT;

    public static final double MAGNIFICATION = 6.3D;

    public static final double FACE_HALF_ANGLE =
        Math.atan(Math.tan(FIELD_OF_VIEW) / MAGNIFICATION);

    private static final double MAX_TANGENT =
        Math.tan(FIELD_OF_VIEW) / MAGNIFICATION;

    private SeekerViewPolicy() {
    }

    public static boolean project(double[] axis, double[] toTarget,
                                  double[] out) {
        if (axis == null || toTarget == null || out == null
            || out.length < 2 || !finite(axis) || !finite(toTarget)) {
            return false;
        }
        double fl = length(axis);
        double tl = length(toTarget);
        if (fl < 1.0E-9D || tl < 1.0E-9D) {
            return false;
        }
        double fx = axis[0] / fl;
        double fy = axis[1] / fl;
        double fz = axis[2] / fl;

        double ux = 0.0D;
        double uy = 1.0D;
        double uz = 0.0D;
        if (Math.abs(fy) > 0.999D) {
            ux = 1.0D;
            uy = 0.0D;
        }
        double rx = fy * uz - fz * uy;
        double ry = fz * ux - fx * uz;
        double rz = fx * uy - fy * ux;
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 1.0E-9D) {
            return false;
        }
        rx /= rl;
        ry /= rl;
        rz /= rl;
        double vx = ry * fz - rz * fy;
        double vy = rz * fx - rx * fz;
        double vz = rx * fy - ry * fx;

        double along = toTarget[0] * fx + toTarget[1] * fy + toTarget[2] * fz;
        if (!(along > 1.0E-6D)) {
            return false;
        }
        double side = toTarget[0] * rx + toTarget[1] * ry + toTarget[2] * rz;
        double rise = toTarget[0] * vx + toTarget[1] * vy + toTarget[2] * vz;
        out[0] = (side / along) / MAX_TANGENT;
        out[1] = (rise / along) / MAX_TANGENT;
        return Double.isFinite(out[0]) && Double.isFinite(out[1]);
    }

    public static boolean onFace(double x, double y) {
        return Double.isFinite(x) && Double.isFinite(y)
            && x * x + y * y <= 1.0D;
    }

    public static final double GROWTH_RANGE = 18000.0D;

    public static final double FAR_EXTENT = (7.0D / 110.0D) / 3.0D;

    public static final double GROWTH_SHAPE = 5.0D;

    public static double boxExtent(double range) {
        if (!Double.isFinite(range) || range <= 0.0D) {
            return 1.0D;
        }
        if (range >= GROWTH_RANGE) {
            return FAR_EXTENT;
        }
        double closed = 1.0D - range / GROWTH_RANGE;
        return FAR_EXTENT
            + (1.0D - FAR_EXTENT) * Math.pow(closed, GROWTH_SHAPE);
    }

    public static final int MINIMUM_FACE_RADIUS = 48;

    public static final int FACE_MARGIN = 6;

    public static int faceRadius(int width, int height) {
        int fits = Math.min(width, height) / 2 - FACE_MARGIN;
        return Math.max(MINIMUM_FACE_RADIUS, fits);
    }

    public static double timeToImpact(double range, double closure) {
        if (!Double.isFinite(range) || !Double.isFinite(closure)
            || closure <= 0.0D) {
            return -1.0D;
        }
        return range / closure;
    }

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static boolean finite(double[] v) {
        for (double component : v) {
            if (!Double.isFinite(component)) {
                return false;
            }
        }
        return true;
    }
}

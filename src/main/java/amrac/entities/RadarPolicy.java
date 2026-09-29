package amrac.entities;

public final class RadarPolicy {
    public static final double MAX_RANGE = 10000.0D;

    public static final double SCAN_CONE = Math.toRadians(70.0D);
    public static final double ACQUIRE_MARGIN = 0.94D;
    public static final double ARROW_EDGE_INSET = 0.90D;

    public static final int MIN_RELEASE_WINDOW_TICKS = 10;

    public static int releaseWindowTicks(int scanIntervalTicks) {
        return Math.max(MIN_RELEASE_WINDOW_TICKS, 2 * Math.max(1, scanIntervalTicks));
    }

    public static boolean releasable(int ticksSinceHeard, int scanIntervalTicks) {
        return ticksSinceHeard >= 0
            && ticksSinceHeard <= releaseWindowTicks(scanIntervalTicks);
    }

    private RadarPolicy() {
    }

    public static boolean withinScanVolume(double azimuthLimit,
                                           double elevationLimit,
                                           double[] forward, double[] up,
                                           double[] right, double[] toTarget) {
        if (forward == null || up == null || right == null || toTarget == null) {
            return false;
        }
        double alongNose = dot(toTarget, forward);
        double alongWing = dot(toTarget, right);
        double alongFin = dot(toTarget, up);
        if (!Double.isFinite(alongNose) || !Double.isFinite(alongWing) ||
            !Double.isFinite(alongFin)) {
            return false;
        }
        double inPlane = Math.sqrt(alongNose * alongNose + alongWing * alongWing);
        if (inPlane < 1.0E-9D && Math.abs(alongFin) < 1.0E-9D) {
            return true;
        }
        double azimuth = Math.abs(Math.atan2(alongWing, alongNose));
        double elevation = Math.abs(Math.atan2(alongFin, inPlane));
        return azimuth <= azimuthLimit && elevation <= elevationLimit;
    }

    public static boolean canTrack(amrac.physics.aircraft.RadarProfile set,
                                   net.minecraft.world.phys.Vec3 eye,
                                   net.minecraft.world.phys.Vec3 forward,
                                   net.minecraft.world.phys.Vec3 up,
                                   net.minecraft.world.phys.Vec3 right,
                                   net.minecraft.world.phys.Vec3 targetPosition,
                                   net.minecraft.world.phys.Vec3 targetVelocity) {
        net.minecraft.world.phys.Vec3 toTarget = targetPosition.subtract(eye);
        double distance = toTarget.length();
        if (set == null) {
            return isInRange(distance) && withinScanCone(forward.x, forward.y,
                forward.z, toTarget.x, toTarget.y, toTarget.z);
        }
        if (!(distance <= set.lockRange())) {
            return false;
        }
        if (!withinScanVolume(set.azimuthLimit(), set.elevationLimit(),
            new double[] {forward.x, forward.y, forward.z},
            new double[] {up.x, up.y, up.z},
            new double[] {right.x, right.y, right.z},
            new double[] {toTarget.x, toTarget.y, toTarget.z})) {
            return false;
        }
        return !amrac.weapons.SeekerPolicy.velocityGated(set.velocityGate(),
            set.velocityGateLookDownOnly(), targetVelocity, toTarget, eye.y,
            targetPosition.y);
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static boolean isOnScreen(double ndcX, double ndcY, double depth,
                                     double margin) {
        return depth > 0.0D && allFinite(ndcX, ndcY) &&
            Math.abs(ndcX) <= margin && Math.abs(ndcY) <= margin;
    }

    public static double kiloBlocks(double blocks) {
        return Double.isFinite(blocks) ? blocks / 1000.0D : 0.0D;
    }

    public static double closureRate(double toTargetX, double toTargetY,
                                     double toTargetZ,
                                     double relativeVelocityX,
                                     double relativeVelocityY,
                                     double relativeVelocityZ) {
        double range = Math.sqrt(toTargetX * toTargetX + toTargetY * toTargetY
            + toTargetZ * toTargetZ);
        if (!(range > 1.0E-6D) || !Double.isFinite(range)) {
            return 0.0D;
        }
        double along = (relativeVelocityX * toTargetX
            + relativeVelocityY * toTargetY
            + relativeVelocityZ * toTargetZ) / range;
        return -along;
    }

    public static boolean isInRange(double distance) {
        return Double.isFinite(distance)
            && distance <= MAX_RANGE * amrac.physics.aircraft.SpeedScale.current();
    }

    public static boolean withinScanCone(double forwardX, double forwardY,
                                         double forwardZ, double toTargetX,
                                         double toTargetY, double toTargetZ) {
        if (!allFinite(forwardX, forwardY, forwardZ, toTargetX, toTargetY,
            toTargetZ)) {
            return false;
        }
        double forwardLength = Math.sqrt(forwardX * forwardX +
            forwardY * forwardY + forwardZ * forwardZ);
        double targetLength = Math.sqrt(toTargetX * toTargetX +
            toTargetY * toTargetY + toTargetZ * toTargetZ);
        if (forwardLength < 1.0E-9D) {
            return false;
        }
        if (targetLength < 1.0E-9D) {
            return true;
        }
        double cosine = (forwardX * toTargetX + forwardY * toTargetY +
            forwardZ * toTargetZ) / (forwardLength * targetLength);
        return cosine >= Math.cos(SCAN_CONE);
    }

    public static double[] edgeArrow(double ndcX, double ndcY, boolean behind) {
        double x = Double.isFinite(ndcX) ? ndcX : 0.0D;
        double y = Double.isFinite(ndcY) ? ndcY : 0.0D;
        if (behind) {
            x = -x;
            y = -y;
            if (Math.abs(x) < 1.0E-3D && Math.abs(y) < 1.0E-3D) {
                y = -1.0D;
            }
        }
        double magnitude = Math.max(Math.abs(x), Math.abs(y));
        if (magnitude < 1.0E-6D) {
            return new double[] {0.0D, -ARROW_EDGE_INSET};
        }
        double scale = ARROW_EDGE_INSET / magnitude;
        return new double[] {x * scale, y * scale};
    }

    public static double arrowAngle(double edgeX, double edgeY) {
        return Math.atan2(edgeY, edgeX);
    }

    private static boolean allFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}

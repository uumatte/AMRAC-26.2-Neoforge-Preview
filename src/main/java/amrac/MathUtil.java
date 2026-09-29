package amrac;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class MathUtil {
    public static float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        if (current > target) {
            return Math.max(target, current - step);
        }
        return current;
    }

    public static float lerpAngle(float perc, float start, float end) {
        return start + perc * Mth.wrapDegrees(end - start);
    }

    public static float lerpAngle180(float perc, float start, float end) {
        if (degreesDifferenceAbs(start, end) > 90) {
            end += 180;
        }
        return start + perc * Mth.wrapDegrees(end - start);
    }

    public static double lerpAngle180(double perc, double start, double end) {
        if (degreesDifferenceAbs(start, end) > 90) {
            end += 180;
        }
        return start + perc * Mth.wrapDegrees(end - start);
    }

    public static double lerpAngle(double perc, double start, double end) {
        return start + perc * Mth.wrapDegrees(end - start);
    }

    public static double degreesDifferenceAbs(double first, double second) {
        return Math.abs(wrapSubtractDegrees(first, second));
    }

    public static double wrapSubtractDegrees(double first, double second) {
        return Mth.wrapDegrees(second - first);
    }

    public static Vec3 rotationToVector(double yaw, double pitch) {
        yaw = Math.toRadians(yaw);
        pitch = Math.toRadians(pitch);
        double xzLen = Math.cos(pitch);
        return new Vec3(-xzLen * Math.sin(yaw), Math.sin(pitch),
            xzLen * Math.cos(-yaw));
    }

    public static Vec3 rotationToVector(double yaw, double pitch, double size) {
        Vec3 vec = rotationToVector(yaw, pitch);
        return vec.scale(size / vec.length());
    }

    public static EulerAngles toEulerAngles(Quaternionf q) {
        EulerAngles angles = new EulerAngles();

        double sinrCosp = 2 * (q.w() * q.z() + q.x() * q.y());
        double cosrCosp = 1 - 2 * (q.z() * q.z() + q.x() * q.x());
        angles.roll = Math.toDegrees(Math.atan2(sinrCosp, cosrCosp));

        double sinp = 2 * (q.w() * q.x() - q.y() * q.z());
        angles.pitch = -Math.toDegrees(Math.asin(Mth.clamp(sinp, -1.0D, 1.0D)));

        double sinyCosp = 2 * (q.w() * q.y() + q.z() * q.x());
        double cosyCosp = 1 - 2 * (q.x() * q.x() + q.y() * q.y());
        angles.yaw = Math.toDegrees(Math.atan2(sinyCosp, cosyCosp));

        return angles;
    }

    public static Quaternionf normalizeQuaternion(Quaternionf q) {
        float f = q.x() * q.x() + q.y() * q.y() + q.z() * q.z() + q.w() * q.w();
        if (!Float.isFinite(f) || f <= 1.0E-6F) {
            return new Quaternionf();
        }
        float inverseLength = (float) (1.0D / Math.sqrt(f));
        return new Quaternionf(q.x() * inverseLength, q.y() * inverseLength,
            q.z() * inverseLength, q.w() * inverseLength);
    }

    public static Quaternionf toQuaternion(double yaw, double pitch, double roll) {
        yaw = Math.toRadians(yaw);
        pitch = -Math.toRadians(pitch);
        roll = Math.toRadians(roll);

        double cy = Math.cos(yaw * 0.5);
        double sy = Math.sin(yaw * 0.5);
        double cp = Math.cos(pitch * 0.5);
        double sp = Math.sin(pitch * 0.5);
        double cr = Math.cos(roll * 0.5);
        double sr = Math.sin(roll * 0.5);

        float w = (float) (cr * cp * cy + sr * sp * sy);
        float z = (float) (sr * cp * cy - cr * sp * sy);
        float x = (float) (cr * sp * cy + sr * cp * sy);
        float y = (float) (cr * cp * sy - sr * sp * cy);

        return new Quaternionf(x, y, z, w);
    }

    public static Quaternionf lerpQ(float perc, Quaternionf start, Quaternionf end) {
        start = normalizeQuaternion(start);
        end = normalizeQuaternion(end);

        double dot = start.x() * end.x() + start.y() * end.y() +
            start.z() * end.z() + start.w() * end.w();

        if (dot < 0.0f) {
            end = new Quaternionf(-end.x(), -end.y(), -end.z(), -end.w());
            dot = -dot;
        }
        dot = Mth.clamp(dot, 0.0D, 1.0D);

        double dotThreshold = 0.9995;
        if (dot > dotThreshold) {
            return normalizeQuaternion(new Quaternionf(
                start.x() * (1 - perc) + end.x() * perc,
                start.y() * (1 - perc) + end.y() * perc,
                start.z() * (1 - perc) + end.z() * perc,
                start.w() * (1 - perc) + end.w() * perc));
        }

        double theta0 = Math.acos(dot);
        double theta = theta0 * perc;
        double sinTheta = Math.sin(theta);
        double sinTheta0 = Math.sin(theta0);

        float s0 = (float) (Math.cos(theta) - dot * sinTheta / sinTheta0);
        float s1 = (float) (sinTheta / sinTheta0);

        return normalizeQuaternion(new Quaternionf(
            start.x() * s0 + end.x() * s1,
            start.y() * s0 + end.y() * s1,
            start.z() * s0 + end.z() * s1,
            start.w() * s0 + end.w() * s1));
    }

    public static class EulerAngles {
        public double pitch, yaw, roll;

        public EulerAngles() {}

        public EulerAngles(EulerAngles a) {
            this.pitch = a.pitch;
            this.yaw = a.yaw;
            this.roll = a.roll;
        }

        public EulerAngles copy() {
            return new EulerAngles(this);
        }

        @Override
        public String toString() {
            return "EulerAngles{pitch=" + pitch + ", yaw=" + yaw +
                ", roll=" + roll + '}';
        }
    }
}

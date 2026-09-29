package amrac.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ScreenProjection {
    private ScreenProjection() {
    }

    @Nullable
    public static Result project(Minecraft minecraft, Vec3 world) {
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (!camera.isInitialized()) {
            return null;
        }

        Vec3 toTarget = world.subtract(camera.position());
        Vec3 forward = new Vec3(camera.forwardVector()).normalize();
        Vec3 up = new Vec3(camera.upVector()).normalize();
        Vec3 right = forward.cross(up).normalize();

        double depth = toTarget.dot(forward);
        double verticalFov = Math.toRadians(minecraft.options.fov().get());
        double verticalScale = Math.tan(verticalFov * 0.5D);
        if (!Double.isFinite(verticalScale) || verticalScale <= 0.0D) {
            return null;
        }
        double aspect = (double) minecraft.getWindow().getWidth() /
            Math.max(1.0D, minecraft.getWindow().getHeight());

        double safeDepth = Math.max(Math.abs(depth), 1.0E-4D);
        double ndcX = toTarget.dot(right) / (safeDepth * verticalScale * aspect);
        double ndcY = toTarget.dot(up) / (safeDepth * verticalScale);
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY)) {
            return null;
        }
        return new Result(ndcX, ndcY, depth, toTarget.length());
    }

    public static int screenX(double ndcX, int width) {
        return (int) Math.round((ndcX * 0.5D + 0.5D) * width);
    }

    public static int screenY(double ndcY, int height) {
        return (int) Math.round((0.5D - ndcY * 0.5D) * height);
    }

    public record Result(double ndcX, double ndcY, double depth, double distance) {
        public boolean behind() {
            return depth <= 0.0D;
        }
    }
}

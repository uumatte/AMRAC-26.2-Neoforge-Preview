package amrac.mixin;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import amrac.client.PlaneCameraController;

@Mixin(Camera.class)
public abstract class CameraMixin implements PlaneCameraController.CameraAccess {
    @Shadow
    private Entity entity;

    @Shadow
    private boolean detached;

    @Shadow
    private float yRot;

    @Shadow
    private float xRot;

    @Shadow @Final
    private Quaternionf rotation;

    @Shadow @Final
    private Vector3f forwards;

    @Shadow @Final
    private Vector3f up;

    @Shadow @Final
    private Vector3f left;

    @Shadow
    private int matrixPropertiesDirty;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void move(float distance, float vertical, float horizontal);

    @Shadow
    private float getMaxZoom(float startingDistance) {
        throw new AssertionError("shadow");
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void amrac$aircraftCamera(float partialTick, CallbackInfo ci) {
        PlaneCameraController.setup(this, entity, detached, partialTick);
        amrac$applyRoll();
    }

    /**
     * Roll goes on the camera quaternion; in 26.2 the view matrix and frustum culling both derive
     * from it. Applying roll anywhere else makes rendering and culling disagree.
     */
    private void amrac$applyRoll() {
        float roll = PlaneCameraController.cameraRoll();
        if (roll == 0.0F || !Float.isFinite(roll)) {
            return;
        }
        rotation.rotateZ((float) Math.toRadians(-roll));
        new Vector3f(0.0F, 0.0F, -1.0F).rotate(rotation, forwards);
        new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation, up);
        new Vector3f(-1.0F, 0.0F, 0.0F).rotate(rotation, left);
        matrixPropertiesDirty |= 3;
    }

    @Override
    public void position(Vec3 position) {
        setPosition(position);
    }

    @Override
    public void rotation(float yRot, float xRot) {
        setRotation(yRot, xRot);
    }

    @Override
    public void pitchOffset(float degrees) {
        setRotation(yRot, Mth.clamp(xRot + degrees, -89.0F, 89.0F));
    }

    @Override
    public void boom(double distance) {
        float requested = (float) distance;
        move(-getMaxZoom(requested), 0.0F, 0.0F);
    }
}

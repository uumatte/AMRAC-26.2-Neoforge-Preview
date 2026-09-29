package amrac.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import amrac.entities.PlaneEntity;

@Mixin(LevelExtractor.class)
public abstract class HideAircraftRiderMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void amrac$hideAircraftRider(Entity entity, Frustum frustum,
                                                 double camX, double camY,
                                                 double camZ,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (entity.getVehicle() instanceof PlaneEntity plane &&
            !plane.shouldRenderPassengers()) {
            cir.setReturnValue(false);
        }
    }
}

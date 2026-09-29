package amrac.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import amrac.client.PlaneCameraController;
import amrac.client.PlaneClientControls;
import amrac.client.PlaneViewState;
import amrac.entities.PlaneEntity;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void amrac$flightStick(CallbackInfo ci) {
        if (!PlaneCameraController.isMouseFlyingActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null || minecraft.player == null ||
            !minecraft.mouseHandler.isMouseGrabbed() || !minecraft.isWindowActive()) {
            return;
        }
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (plane == null || plane.getControllingPassenger() != minecraft.player) {
            return;
        }

        PlaneClientControls.accumulateMouse(accumulatedDX, accumulatedDY);
        accumulatedDX = 0.0D;
        accumulatedDY = 0.0D;
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void amrac$blockScroll(long window, double xOffset, double yOffset,
                                          CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!PlaneViewState.isPilotingInputActive(minecraft)) {
            return;
        }
        PlaneClientControls.onPitchAuthorityScroll(yOffset);
        ci.cancel();
    }
}

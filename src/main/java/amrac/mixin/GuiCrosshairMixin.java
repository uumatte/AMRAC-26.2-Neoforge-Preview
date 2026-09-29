package amrac.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import amrac.client.PlaneHud;

@Mixin(Hud.class)
public class GuiCrosshairMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void amrac$hideCrosshair(GuiGraphicsExtractor graphics,
                                            DeltaTracker deltaTracker,
                                            CallbackInfo ci) {
        if (PlaneHud.suppressesVanillaCrosshair()) {
            ci.cancel();
        }
    }
}

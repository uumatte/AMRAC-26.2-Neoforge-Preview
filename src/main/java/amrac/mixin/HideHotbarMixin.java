package amrac.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import amrac.client.PlaneViewState;

/**
 * In-flight HUD hiding lives in two places: this mixin hides only the hotbar (extractItemHotbar);
 * health/armour/food/XP are handled one by one in CockpitHud to keep the locator bar. Change one,
 * check the other.
 */
@Mixin(Hud.class)
public class HideHotbarMixin {
    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void amrac$hideHotbar(GuiGraphicsExtractor graphics,
                                         DeltaTracker deltaTracker,
                                         CallbackInfo ci) {
        if (PlaneViewState.ridingPlane(net.minecraft.client.Minecraft.getInstance())
            != null) {
            ci.cancel();
        }
    }
}

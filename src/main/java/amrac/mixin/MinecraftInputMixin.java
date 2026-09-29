package amrac.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import amrac.client.PlaneViewState;

/**
 * The cannon trigger is the vanilla attack key. The attack/break suppression here and the trigger
 * binding are a pair: change the trigger key or drop the suppression and firing also breaks blocks.
 */
@Mixin(Minecraft.class)
public class MinecraftInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void amrac$blockAttack(CallbackInfoReturnable<Boolean> cir) {
        if (PlaneViewState.isPilotingInputActive(Minecraft.getInstance())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void amrac$blockContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (PlaneViewState.isPilotingInputActive(Minecraft.getInstance())) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void amrac$blockUseItem(CallbackInfo ci) {
        if (PlaneViewState.isPilotingInputActive(Minecraft.getInstance())) {
            ci.cancel();
        }
    }
}

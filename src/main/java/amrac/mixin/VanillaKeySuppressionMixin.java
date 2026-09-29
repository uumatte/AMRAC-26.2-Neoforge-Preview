package amrac.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import amrac.client.PlaneKeyBindings;

/**
 * Vanilla keys are suppressed where they are read, not cancelled where they are pressed (that also
 * swallows the release and leaves the key held). The exceptions are in
 * PlaneKeyBindings.isSuppressedWhileFlying; change both together.
 */
@Mixin(KeyMapping.class)
public abstract class VanillaKeySuppressionMixin {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void amrac$suppressHeld(CallbackInfoReturnable<Boolean> callback) {
        if (PlaneKeyBindings.isSuppressedWhileFlying(
            (KeyMapping) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void amrac$suppressClick(CallbackInfoReturnable<Boolean> callback) {
        if (PlaneKeyBindings.isSuppressedWhileFlying(
            (KeyMapping) (Object) this)) {
            callback.setReturnValue(false);
        }
    }
}

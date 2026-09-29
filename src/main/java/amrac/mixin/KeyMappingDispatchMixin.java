package amrac.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import amrac.client.PlaneKeyBindings;

@Mixin(KeyMapping.class)
public class KeyMappingDispatchMixin {
    @Inject(method = "set", at = @At("TAIL"))
    private static void amrac$setShared(InputConstants.Key key, boolean held,
                                               CallbackInfo callback) {
        PlaneKeyBindings.dispatchSet(key, held);
    }

    @Inject(method = "click", at = @At("TAIL"))
    private static void amrac$clickShared(InputConstants.Key key,
                                                 CallbackInfo callback) {
        PlaneKeyBindings.dispatchClick(key);
    }

    /**
     * Vanilla releases and re-reads keys on screen open/close only for its own registry. Aircraft
     * keys are not in it, so they are released and re-read here (paired with
     * PlaneKeyBindings.syncAll), or keys stick or the stick goes slack.
     */
    @Inject(method = "releaseAll", at = @At("TAIL"))
    private static void amrac$releaseShared(CallbackInfo callback) {
        PlaneKeyBindings.releaseAll();
    }

    @Inject(method = "setAll", at = @At("TAIL"))
    private static void amrac$syncShared(CallbackInfo callback) {
        PlaneKeyBindings.syncAll();
    }
}

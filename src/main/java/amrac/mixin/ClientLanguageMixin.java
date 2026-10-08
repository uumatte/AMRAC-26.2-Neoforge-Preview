package amrac.mixin;

import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import amrac.client.ModLanguage;

// Head injections that return only for the mod's own keys, so other mods hooking the
// same lookup still see every other key.
@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {

    @Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        at = @At("HEAD"), cancellable = true)
    private void amrac$modText(String key, String defaultValue,
                               CallbackInfoReturnable<String> cir) {
        String text = ModLanguage.lookup(key);
        if (text != null) {
            cir.setReturnValue(text);
        }
    }

    @Inject(method = "has", at = @At("HEAD"), cancellable = true)
    private void amrac$hasModText(String key, CallbackInfoReturnable<Boolean> cir) {
        if (ModLanguage.lookup(key) != null) {
            cir.setReturnValue(true);
        }
    }
}

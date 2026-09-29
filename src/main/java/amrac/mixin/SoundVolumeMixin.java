package amrac.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import amrac.client.SoundVolumes;

/**
 * The volume slider applies in two places in the sound engine (on start and every tick); both must
 * use the same factor. New sounds need not scale themselves.
 */
@Mixin(SoundEngine.class)
public class SoundVolumeMixin {
    @Redirect(method = "play",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float amrac$scaleAtStart(SoundEngine engine, float volume,
                                     SoundSource source, SoundInstance instance) {
        float scaled = volume * SoundVolumes.factor(instance.getIdentifier());
        return ((SoundEngineAccess) engine).amrac$calculateVolume(scaled, source);
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At("RETURN"), cancellable = true)
    private void amrac$scaleTicking(SoundInstance instance,
                                    CallbackInfoReturnable<Float> cir) {
        float factor = SoundVolumes.factor(instance.getIdentifier());
        if (factor != 1.0F) {
            cir.setReturnValue(cir.getReturnValueF() * factor);
        }
    }

    @Mixin(SoundEngine.class)
    public interface SoundEngineAccess {
        @org.spongepowered.asm.mixin.gen.Invoker("calculateVolume")
        float amrac$calculateVolume(float volume, SoundSource source);
    }
}

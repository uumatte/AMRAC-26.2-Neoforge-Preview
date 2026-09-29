package amrac.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import amrac.AmracSounds;
import amrac.client.TransonicEffects;

/**
 * The supersonic-silence whitelist matches sound ids (launch, afterburner, boom, rumble); keep it
 * in step when these sound events are added or replaced.
 */
@Mixin(SoundEngine.class)
public class SupersonicSilenceMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At("HEAD"), cancellable = true)
    private void amrac$supersonicSilence(SoundInstance sound,
                                                CallbackInfoReturnable<Float> cir) {
        if (!amrac.client.SoundMixPolicy.SUPERSONIC_SILENCE
            || !TransonicEffects.isSilent() || sound == null) {
            return;
        }
        Identifier id = sound.getIdentifier();
        if (id.equals(SoundEvents.FIREWORK_ROCKET_LAUNCH.location())
            || id.equals(AmracSounds.MISSILE_LAUNCH_ID)
            || id.equals(AmracSounds.MISSILE_RELEASE_ID)
            || id.equals(AmracSounds.SUPERSONIC_RUMBLE_ID)
            || id.equals(AmracSounds.SONIC_BOOM_ID)
            || id.equals(AmracSounds.AFTERBURNER_LIGHT_ID)
            || id.equals(AmracSounds.AFTERBURNER_CUT_ID)
            || id.equals(AmracSounds.MISSILE_LAUNCH_COCKPIT_ID)
            || id.equals(AmracSounds.RWR_WARNING_ID)
            || id.equals(AmracSounds.BOUNDARY_WARNING_ID)
            || amrac.client.CountermeasureSounds.isOwn(sound)) {
            return;
        }
        cir.setReturnValue(0.0F);
    }
}

package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import amrac.AmracSounds;
import amrac.client.SoundMixPolicy;

public class SupersonicRumbleSound extends AbstractTickableSoundInstance {
    private static SupersonicRumbleSound playing;

    private int fade;
    private boolean wanted = true;

    private SupersonicRumbleSound() {
        super(AmracSounds.SUPERSONIC_RUMBLE, SoundSource.NEUTRAL,
            RandomSource.create());
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.pitch = SoundMixPolicy.SUPERSONIC_RUMBLE_PITCH;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    public static void update(boolean supersonic) {
        if (SoundMixPolicy.SUPERSONIC_RUMBLE_VOLUME <= 0.0F) {
            supersonic = false;
        }
        SupersonicRumbleSound sound = playing;
        if (sound != null && !sound.isStopped()) {
            sound.wanted = supersonic;
            return;
        }
        if (!supersonic) {
            return;
        }
        sound = new SupersonicRumbleSound();
        playing = sound;
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public static void clear() {
        SupersonicRumbleSound sound = playing;
        playing = null;
        if (sound != null && !sound.isStopped()) {
            sound.stop();
        }
    }

    @Override
    public void tick() {
        int span = SoundMixPolicy.SUPERSONIC_RUMBLE_FADE_TICKS;
        fade = Math.max(0, Math.min(span, wanted ? fade + 1 : fade - 1));
        if (fade == 0 && !wanted) {
            if (playing == this) {
                playing = null;
            }
            stop();
            return;
        }
        volume = SoundMixPolicy.SUPERSONIC_RUMBLE_VOLUME * ((float) fade / span);
    }
}

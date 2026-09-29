package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import amrac.entities.PlaneEntity;
import amrac.client.SoundMixPolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WindSound extends AbstractTickableSoundInstance {
    private static final int FADE_OUT_TICKS = 8;

    private static final Map<Integer, WindSound> PLAYING_FOR =
        Collections.synchronizedMap(new HashMap<>());

    private final PlaneEntity plane;
    private final boolean cockpit;
    private int fadeOut = -1;

    private boolean live;
    private int grace = 20;

    public WindSound(PlaneEntity plane, boolean cockpit) {
        super(amrac.AmracSounds.PLANE_AIRFLOW, SoundSource.NEUTRAL,
            RandomSource.create());
        this.plane = plane;
        this.cockpit = cockpit;
        if (cockpit) {
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.x = cockpit ? 0.0D : plane.getX();
        this.y = cockpit ? 0.0D : plane.getY();
        this.z = cockpit ? 0.0D : plane.getZ();
    }

    private static double mixScale(boolean cockpit) {
        return cockpit ? SoundMixPolicy.COCKPIT_AIRFLOW_SCALE : 1.0D;
    }

    public static boolean isPlaying(int entityId) {
        WindSound sound = PLAYING_FOR.get(entityId);
        return sound != null && !sound.isStopped();
    }

    public static void tryToPlay(PlaneEntity plane) {
        boolean cockpit = isListenerAboard(plane);
        WindSound existing = PLAYING_FOR.get(plane.getId());
        if (existing != null && !existing.isStopped()
            && existing.cockpit != cockpit) {
            existing.stop();
            PLAYING_FOR.remove(plane.getId());
            existing = null;
        }
        if (existing != null && !existing.isStopped()) {
            if (existing.live
                || Minecraft.getInstance().getSoundManager().isActive(existing)) {
                existing.live = true;
                return;
            }
            if (--existing.grace > 0) {
                return;
            }
            PLAYING_FOR.remove(plane.getId());
        }
        double fraction = SoundMixPolicy.speedFraction(
            plane.getDeltaMovement().length(), plane.getMaxSpeed());
        if (!SoundMixPolicy.airflowAudible(fraction)) {
            return;
        }
        WindSound sound = new WindSound(plane, cockpit);
        PLAYING_FOR.put(plane.getId(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private static boolean isListenerAboard(PlaneEntity plane) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() == plane;
    }

    public static void clear() {
        synchronized (PLAYING_FOR) {
            PLAYING_FOR.clear();
        }
    }

    @Override
    public void tick() {
        live = true;
        if (!cockpit) {
            x = plane.getX();
            y = plane.getY();
            z = plane.getZ();
        }

        double fraction = SoundMixPolicy.speedFraction(
            plane.getDeltaMovement().length(), plane.getMaxSpeed());
        boolean gone = plane.isRemoved()
            || !SoundMixPolicy.airflowAudible(fraction);

        if (fadeOut < 0 && gone) {
            fadeOut = 0;
            synchronized (PLAYING_FOR) {
                PLAYING_FOR.remove(plane.getId());
            }
        } else if (fadeOut >= FADE_OUT_TICKS) {
            stop();
        } else if (fadeOut >= 0) {
            volume = (float) (SoundMixPolicy.airflowVolume(fraction)
                * mixScale(cockpit)
                * (1.0D - (double) fadeOut / FADE_OUT_TICKS));
            ++fadeOut;
        } else {
            volume = (float) (SoundMixPolicy.airflowVolume(fraction)
                * mixScale(cockpit));
            pitch = (float) SoundMixPolicy.airflowPitch(fraction);
        }
    }
}

package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import amrac.AmracSounds;
import amrac.entities.PlaneEntity;
import amrac.client.SoundMixPolicy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AfterburnerSound extends AbstractTickableSoundInstance {
    private static final int FADE_OUT_TICKS = 8;

    private static final Map<Integer, AfterburnerSound> PLAYING_FOR =
        Collections.synchronizedMap(new HashMap<>());

    private static final Map<Integer, Boolean> WAS_LIT =
        Collections.synchronizedMap(new HashMap<>());

    private final PlaneEntity plane;
    private final boolean cockpit;
    private int fadeOut = -1;
    private boolean live;
    private int grace = 20;

    public AfterburnerSound(PlaneEntity plane, boolean cockpit) {
        super(AmracSounds.PLANE_AFTERBURNER, SoundSource.NEUTRAL,
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
        this.pitch = (float) SoundMixPolicy.AFTERBURNER_PITCH;
        this.x = cockpit ? 0.0D : plane.getX();
        this.y = cockpit ? 0.0D : plane.getY();
        this.z = cockpit ? 0.0D : plane.getZ();
    }

    private static double mixScale(boolean cockpit) {
        return cockpit ? SoundMixPolicy.COCKPIT_AFTERBURNER_SCALE : 1.0D;
    }

    public static void tryToPlay(PlaneEntity plane) {
        boolean lit = plane.isAfterburnerLit();
        Boolean previous = WAS_LIT.put(plane.getId(), lit);
        if (previous != null && previous != lit) {
            playTransient(plane, lit);
        }

        float spool = plane.getAfterburnerSpool(1.0F);
        if (spool <= 0.0F && !lit) {
            return;
        }

        boolean cockpit = isListenerAboard(plane);
        AfterburnerSound existing = PLAYING_FOR.get(plane.getId());
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
        AfterburnerSound sound = new AfterburnerSound(plane, cockpit);
        PLAYING_FOR.put(plane.getId(), sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    private static void playTransient(PlaneEntity plane, boolean lighting) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean cockpit = isListenerAboard(plane);
        var event = lighting ? AmracSounds.AFTERBURNER_LIGHT
            : AmracSounds.AFTERBURNER_CUT;
        if (cockpit) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F,
                lighting ? SoundMixPolicy.AFTERBURNER_COCKPIT_LIGHT_VOLUME
                    : SoundMixPolicy.AFTERBURNER_SHUTDOWN_VOLUME));
            return;
        }
        if (minecraft.level == null) {
            return;
        }
        minecraft.level.playLocalSound(plane.getX(), plane.getY(), plane.getZ(),
            event, SoundSource.NEUTRAL,
            lighting ? SoundMixPolicy.AFTERBURNER_LIGHT_VOLUME
                : SoundMixPolicy.AFTERBURNER_SHUTDOWN_VOLUME,
            1.0F, false);
    }

    private static boolean isListenerAboard(PlaneEntity plane) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() == plane;
    }

    public static void clear() {
        synchronized (PLAYING_FOR) {
            PLAYING_FOR.clear();
        }
        synchronized (WAS_LIT) {
            WAS_LIT.clear();
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

        float spool = plane.getAfterburnerSpool(1.0F);
        pitch = (float) (SoundMixPolicy.AFTERBURNER_PITCH
            + SoundMixPolicy.AFTERBURNER_PITCH_SPOOL * spool);

        boolean gone = plane.isRemoved() || !plane.isEngineRunning()
            || (spool <= 0.0F && !plane.isAfterburnerEngaged());
        if (fadeOut < 0 && gone) {
            fadeOut = 0;
            synchronized (PLAYING_FOR) {
                PLAYING_FOR.remove(plane.getId());
            }
        } else if (fadeOut >= FADE_OUT_TICKS) {
            stop();
        } else if (fadeOut >= 0) {
            volume = (float) (SoundMixPolicy.AFTERBURNER_MAX_VOLUME
                * mixScale(cockpit) * spool
                * (cockpit ? TransonicEffects.engineShare() : 1.0D)
                * (1.0D - (double) fadeOut / FADE_OUT_TICKS));
            ++fadeOut;
        } else {
            volume = (float) (SoundMixPolicy.AFTERBURNER_MAX_VOLUME
                * mixScale(cockpit) * spool
                * (cockpit ? TransonicEffects.engineShare() : 1.0D));
        }
    }
}

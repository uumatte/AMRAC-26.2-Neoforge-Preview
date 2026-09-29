package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import amrac.AmracSounds;
import amrac.entities.PlaneEntity;
import amrac.entities.ai.WorldBoundaryPolicy;
import amrac.client.SoundMixPolicy;

public final class CockpitWarnings {
    private static final Loop RWR =
        new Loop(AmracSounds.RWR_WARNING, SoundMixPolicy.RWR_WARNING_VOLUME);
    private static final Loop BOUNDARY =
        new Loop(AmracSounds.BOUNDARY_WARNING, SoundMixPolicy.BOUNDARY_WARNING_VOLUME);

    private static double reportedDistance = -1.0D;
    private static long reportedAt;

    private static final long REPORT_TIMEOUT_MS = 2500L;

    private CockpitWarnings() {
    }

    public static void acceptBoundary(double distance) {
        reportedDistance = distance;
        reportedAt = System.currentTimeMillis();
    }

    private static boolean insideBoundaryBand() {
        return reportedDistance >= 0.0D
            && reportedDistance <= WorldBoundaryPolicy.playerWarningDistance()
            && System.currentTimeMillis() - reportedAt <= REPORT_TIMEOUT_MS;
    }

    public static void tick(Minecraft minecraft) {
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (plane == null || minecraft.player == null || minecraft.level == null) {
            RWR.stopNow();
            BOUNDARY.stopNow();
            return;
        }
        RWR.update(!MissileThreats.against(minecraft, plane).isEmpty());
        BOUNDARY.update(insideBoundaryBand());
    }

    public static void clear() {
        RWR.stopNow();
        BOUNDARY.stopNow();
        reportedDistance = -1.0D;
        reportedAt = 0L;
    }

    private static final class Loop {
        private final SoundEvent event;
        private final float volume;
        private Instance playing;

        Loop(SoundEvent event, float volume) {
            this.event = event;
            this.volume = volume;
        }

        void update(boolean wanted) {
            Instance sound = playing;
            if (sound != null && !sound.isStopped()) {
                sound.wanted = wanted;
                return;
            }
            playing = null;
            if (!wanted) {
                return;
            }
            sound = new Instance(event, volume);
            playing = sound;
            Minecraft.getInstance().getSoundManager().play(sound);
        }

        void stopNow() {
            Instance sound = playing;
            playing = null;
            if (sound != null && !sound.isStopped()) {
                sound.end();
            }
        }
    }

    private static final class Instance extends AbstractTickableSoundInstance {
        private final float full;
        private boolean wanted = true;
        private int fade = SoundMixPolicy.WARNING_FADE_TICKS;

        Instance(SoundEvent event, float volume) {
            super(event, SoundSource.PLAYERS, RandomSource.create());
            this.relative = true;
            this.attenuation = Attenuation.NONE;
            this.looping = true;
            this.delay = 0;
            this.full = volume;
            this.volume = volume;
            this.x = 0.0D;
            this.y = 0.0D;
            this.z = 0.0D;
        }

        void end() {
            stop();
        }

        @Override
        public void tick() {
            int span = SoundMixPolicy.WARNING_FADE_TICKS;
            fade = wanted ? span : fade - 1;
            if (fade <= 0) {
                stop();
                return;
            }
            volume = full * ((float) fade / span);
        }
    }
}

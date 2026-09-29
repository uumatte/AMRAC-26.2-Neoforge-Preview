package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import amrac.AmracSounds;
import amrac.entities.PlaneEntity;
import amrac.entities.TransonicPolicy;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.client.SoundMixPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class TransonicEffects {
    private static final float BOOM_PITCH = SoundMixPolicy.SONIC_BOOM_PITCH;
    private static final float BOOM_VOLUME = SoundMixPolicy.SONIC_BOOM_VOLUME;

    private static final float BYSTANDER_BOOM_VOLUME = 1.0F;

    private static final int CONE_PARTICLES = 26;
    private static final double CONE_LENGTH = 7.0D;
    private static final double CONE_RADIUS = 3.4D;

    private static final class PendingBoom {
        final double x, y, z;
        int ticksLeft;

        PendingBoom(double x, double y, double z, int ticksLeft) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticksLeft = ticksLeft;
        }
    }

    private static boolean boomArmed = true;
    private static boolean silent;
    private static int trackedPlaneId = -1;

    private static final Map<Integer, Boolean> BYSTANDER_ARMED = new HashMap<>();
    private static final List<PendingBoom> PENDING = new ArrayList<>();

    private TransonicEffects() {
    }

    public static boolean isSilent() {
        return silent;
    }

    public static double engineShare() {
        return engineShare;
    }

    private static double engineShare = 1.0D;

    public static void reset() {
        boomArmed = true;
        silent = false;
        engineShare = 1.0D;
        trackedPlaneId = -1;
        SupersonicRumbleSound.clear();
    }

    public static void clear() {
        reset();
        BYSTANDER_ARMED.clear();
        PENDING.clear();
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            clear();
            return;
        }
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (plane == null) {
            reset();
        } else {
            tickRidden(minecraft, plane);
        }
        tickBystanders(minecraft, plane);
    }

    private static void tickRidden(Minecraft minecraft, PlaneEntity plane) {
        if (plane.getId() != trackedPlaneId) {
            trackedPlaneId = plane.getId();
            boomArmed = true;
            silent = false;
        }
        double mach = machOf(plane);
        if (TransonicPolicy.rearmsBoom(mach)) {
            boomArmed = true;
        } else if (TransonicPolicy.shouldBoom(mach, boomArmed)) {
            boomArmed = false;
            minecraft.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    AmracSounds.SONIC_BOOM, BOOM_PITCH, BOOM_VOLUME));
        }
        silent = TransonicPolicy.isSilent(mach, silent);
        engineShare = SoundMixPolicy.supersonicEngineShare(engineShare, silent);
        SupersonicRumbleSound.update(silent);
        double intensity = TransonicPolicy.cloudIntensity(mach);
        if (intensity > 0.0D) {
            spawnCone(minecraft, plane, intensity);
        }
    }

    private static void tickBystanders(Minecraft minecraft, PlaneEntity ridden) {
        var listener = minecraft.player;
        if (listener == null) {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof PlaneEntity other) || other == ridden) {
                continue;
            }
            double mach = machOf(other);
            boolean armed = BYSTANDER_ARMED.getOrDefault(other.getId(), true);
            if (TransonicPolicy.rearmsBoom(mach)) {
                if (!armed) {
                    BYSTANDER_ARMED.remove(other.getId());
                }
            } else if (TransonicPolicy.shouldBoom(mach, armed)) {
                BYSTANDER_ARMED.put(other.getId(), false);
                double distance = listener.position().distanceTo(other.position());
                double speedOfSound = FlightModelRegistry.instance().atmosphere()
                    .speedOfSound(other.getY());
                int delay = speedOfSound > 1.0E-6D
                    ? (int) Math.round(distance / speedOfSound * 20.0D) : 0;
                PENDING.add(new PendingBoom(other.getX(), other.getY(), other.getZ(),
                    delay));
            }
        }
        if (BYSTANDER_ARMED.size() > 256) {
            BYSTANDER_ARMED.clear();
        }
        for (Iterator<PendingBoom> it = PENDING.iterator(); it.hasNext();) {
            PendingBoom boom = it.next();
            if (--boom.ticksLeft <= 0) {
                minecraft.level.playLocalSound(boom.x, boom.y, boom.z,
                    AmracSounds.SONIC_BOOM, SoundSource.PLAYERS,
                    BYSTANDER_BOOM_VOLUME, BOOM_PITCH, false);
                it.remove();
            }
        }
    }

    private static double machOf(PlaneEntity plane) {
        double speed = plane.getDeltaMovement().length() * 20.0D;
        double speedOfSound = FlightModelRegistry.instance().atmosphere()
            .speedOfSound(plane.getY());
        return speedOfSound > 1.0E-6D ? speed / speedOfSound : 0.0D;
    }

    private static void spawnCone(Minecraft minecraft, PlaneEntity plane,
                                  double intensity) {
        int count = Math.max(1, (int) Math.round(CONE_PARTICLES * intensity));
        Vec3 centre = plane.position();
        Vec3 forward = plane.getBodyDirection(0.0F, 0.0F, 1.0F);
        Vec3 right = plane.getBodyDirection(1.0F, 0.0F, 0.0F);
        Vec3 up = plane.getBodyDirection(0.0F, 1.0F, 0.0F);
        Vec3 motion = plane.getDeltaMovement();
        var random = plane.getRandom();
        for (int i = 0; i < count; i++) {
            double along = Math.sqrt(random.nextDouble());
            double radius = CONE_RADIUS * along * intensity;
            double back = -CONE_LENGTH * along;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 at = centre
                .add(forward.scale(back))
                .add(right.scale(Math.cos(angle) * radius))
                .add(up.scale(Math.sin(angle) * radius));
            minecraft.level.addParticle(ParticleTypes.CLOUD,
                at.x, at.y, at.z,
                motion.x * 0.6D, motion.y * 0.6D, motion.z * 0.6D);
        }
    }
}

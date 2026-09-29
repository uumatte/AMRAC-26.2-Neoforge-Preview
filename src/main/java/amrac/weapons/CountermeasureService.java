package amrac.weapons;

import amrac.entities.PlaneEntity;
import amrac.platform.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Linked by the BVR Replay mod: keep this class's name, package and public members, or replays
 * crash with NoClassDefFoundError.
 */
public final class CountermeasureService {
    public static final int CHAFF_LIFETIME_TICKS = 20;

    public static final int FLARE_LIFETIME_TICKS = 30;

    public static final int RELEASE_COOLDOWN_TICKS = 2;

    public static int lifetimeTicks(Kind kind) {
        return kind == Kind.CHAFF ? CHAFF_LIFETIME_TICKS : FLARE_LIFETIME_TICKS;
    }

    public enum Kind { CHAFF, FLARE }

    private static final class Bundle {
        final int id;
        final Kind kind;
        Vec3 position;
        Vec3 velocity;
        int age;

        Bundle(int id, Kind kind, Vec3 position, Vec3 velocity) {
            this.id = id;
            this.kind = kind;
            this.position = position;
            this.velocity = velocity;
        }
    }

    private static final Map<ResourceKey<Level>, List<Bundle>> AIRBORNE =
        new HashMap<>();

    private static int nextId;

    private CountermeasureService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CountermeasureService::tick);
    }

    public static void release(ServerLevel level, PlaneEntity plane,
                               @org.jetbrains.annotations.Nullable
                               net.minecraft.world.entity.player.Player pilot,
                               Kind kind) {
        releaseAt(level, plane.getBoundingBox().getCenter(),
            plane.getDeltaMovement(), plane.getBodyDirection(0.0F, -1.0F, 0.0F),
            plane.getBodyDirection(0.0F, 0.0F, -1.0F),
            plane.getBodyDirection(1.0F, 0.0F, 0.0F), pilot, kind);
    }

    public static void releaseAt(ServerLevel level, Vec3 centre, Vec3 motion,
                                 Vec3 down, Vec3 aft, Vec3 right,
                                 @org.jetbrains.annotations.Nullable
                                 net.minecraft.world.entity.player.Player pilot,
                                 Kind kind) {
        double scale = amrac.physics.aircraft.SpeedScale.current();
        double side = (level.getRandom().nextDouble() - 0.5D) * 0.4D * scale;
        Vec3 origin = centre.add(down.scale(1.2D)).add(aft.scale(2.0D));
        Vec3 velocity = motion
            .add(down.scale((kind == Kind.CHAFF ? 0.35D : 0.5D) * scale))
            .add(right.scale(side));
        Bundle bundle = new Bundle(nextId++, kind, origin, velocity);
        AIRBORNE.computeIfAbsent(level.dimension(), k -> new ArrayList<>())
            .add(bundle);
        level.playSound(pilot, origin.x, origin.y, origin.z,
            kind == Kind.FLARE ? SoundEvents.FIREWORK_ROCKET_LAUNCH
                : SoundEvents.DISPENSER_LAUNCH,
            SoundSource.PLAYERS, 0.8F, kind == Kind.FLARE ? 0.7F : 1.4F);
        draw(level, bundle, true);
    }

    public static List<SeekerPolicy.Decoy> chaffNear(ServerLevel level,
                                                     Vec3 position) {
        List<Bundle> bundles = AIRBORNE.get(level.dimension());
        if (bundles == null || bundles.isEmpty()) {
            return List.of();
        }
        List<SeekerPolicy.Decoy> out = new ArrayList<>(bundles.size());
        for (Bundle bundle : bundles) {
            if (bundle.kind == Kind.CHAFF) {
                out.add(new SeekerPolicy.Decoy(bundle.id, bundle.position,
                    bundle.velocity));
            }
        }
        return out;
    }

    public static List<SeekerPolicy.Decoy> flaresNear(ServerLevel level,
                                                      Vec3 position) {
        List<Bundle> bundles = AIRBORNE.get(level.dimension());
        if (bundles == null || bundles.isEmpty()) {
            return List.of();
        }
        List<SeekerPolicy.Decoy> out = new ArrayList<>(bundles.size());
        for (Bundle bundle : bundles) {
            if (bundle.kind == Kind.FLARE) {
                out.add(new SeekerPolicy.Decoy(bundle.id, bundle.position,
                    bundle.velocity, true));
            }
        }
        return out;
    }

    private static void tick(MinecraftServer server) {
        if (AIRBORNE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<ResourceKey<Level>, List<Bundle>>> levels =
            AIRBORNE.entrySet().iterator();
        while (levels.hasNext()) {
            Map.Entry<ResourceKey<Level>, List<Bundle>> entry = levels.next();
            ServerLevel level = server.getLevel(entry.getKey());
            List<Bundle> bundles = entry.getValue();
            if (level == null) {
                levels.remove();
                continue;
            }
            bundles.removeIf(bundle -> ++bundle.age > lifetimeTicks(bundle.kind));
            for (Bundle bundle : bundles) {
                step(bundle);
                draw(level, bundle, false);
            }
            if (bundles.isEmpty()) {
                levels.remove();
            }
        }
    }

    private static void step(Bundle bundle) {
        double scale = amrac.physics.aircraft.SpeedScale.current();
        if (bundle.kind == Kind.CHAFF) {
            bundle.velocity = bundle.velocity.scale(0.80D)
                .add(0.0D, -0.004D * scale, 0.0D);
        } else {
            bundle.velocity = bundle.velocity.scale(0.94D)
                .add(0.0D, -0.03D * scale, 0.0D);
        }
        bundle.position = bundle.position.add(bundle.velocity);
    }

    private static void draw(ServerLevel level, Bundle bundle, boolean burst) {
        Vec3 p = bundle.position;
        if (bundle.kind == Kind.FLARE) {
            level.sendParticles(ParticleTypes.FLAME, true, true, p.x, p.y, p.z,
                burst ? 6 : 3, 0.15D, 0.15D, 0.15D, 0.01D);
            level.sendParticles(ParticleTypes.SMOKE, true, true, p.x, p.y, p.z,
                2, 0.1D, 0.1D, 0.1D, 0.005D);
        } else {
            level.sendParticles(ParticleTypes.WHITE_ASH, true, true, p.x, p.y, p.z,
                burst ? 24 : 8, 1.2D, 1.2D, 1.2D, 0.0D);
            if (burst) {
                level.sendParticles(ParticleTypes.POOF, true, true, p.x, p.y, p.z,
                    3, 0.3D, 0.3D, 0.3D, 0.01D);
            }
        }
    }
}

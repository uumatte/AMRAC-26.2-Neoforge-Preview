package amrac.weapons;

import net.minecraft.network.chat.Component;
import amrac.AmracSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import amrac.entities.MissileEntity;
import amrac.entities.PlaneEntity;

public final class MissileSystem {
    public static double[][] railPositions() {
        return RAILS;
    }

    private static final double[][] RAILS = {
        {-0.86D, -0.06D, 0.10D},
        { 0.86D, -0.06D, 0.10D},
        {-0.60D, -0.10D, 0.35D},
        { 0.60D, -0.10D, 0.35D}
    };

    private final PlaneEntity plane;
    private long readyAtGameTime;
    private long launchedAtGameTime = Long.MIN_VALUE / 2;
    private static final long QUIET_AFTER_LAUNCH_TICKS = 10L;

    private long lastRefusalTime;

    private long lastHandoverTime;

    @org.jetbrains.annotations.Nullable
    private String warmingRound;
    @org.jetbrains.annotations.Nullable
    private java.util.UUID warmingPilot;
    private long warmingSinceGameTime;
    private long warmingSinceNanos;

    public static final int WARMUP_TOLERANCE_TICKS = 4;

    private boolean refuse(LivingEntity pilot, String key, String detail,
                           Object... args) {
        if (pilot instanceof Player player) {
            amrac.AmracMod.sendOverlay(player,
                Component.translatable(key, args), true);
        }
        long now = plane.level().getGameTime();
        if (now - lastRefusalTime >= 20L || now < lastRefusalTime) {
            lastRefusalTime = now;
            amrac.AmracMod.LOGGER.info(
                "missile launch refused for {}: {} [gameTime {}, planeTick {}]",
                pilot == null ? "nobody" : pilot.getName().getString(), detail,
                now, plane.tickCount);
        }
        return false;
    }

    public MissileProfile profile() {
        String selected = plane.getSelectedMissile();
        return selected == null ? MissileProfiles.defaultProfile()
            : MissileProfiles.byId(selected);
    }

    public MissileSystem(PlaneEntity plane) {
        this.plane = plane;
    }

    /**
     * Cooldown runs on world game time, not entity ticks: an aircraft still flies and fires in
     * chunks that are loaded but not ticking entities (MissileEntity.handOverIfNotTicked and
     * MachineGunSystem do the same). Logic tied to entity ticks would stall there.
     */
    public void tick() {
    }

    public void beginWarmup(LivingEntity pilot,
                            @org.jetbrains.annotations.Nullable String roundId) {
        if (plane.level().isClientSide()) {
            return;
        }
        String[] slots = plane.getLoadout();
        if (roundId != null && !roundId.isEmpty()) {
            for (String slot : slots) {
                if (slot != null && MissileProfiles.byId(slot).id.equals(roundId)) {
                    stampWarmup(pilot, roundId);
                    return;
                }
            }
        }
        int rail = MissileLoadout.firstOf(slots, plane.getSelectedMissile());
        if (rail < 0) {
            rail = MissileLoadout.firstLoaded(slots);
        }
        if (rail < 0) {
            warmingRound = null;
            return;
        }
        stampWarmup(pilot, MissileProfiles.byId(slots[rail]).id);
    }

    private void stampWarmup(LivingEntity pilot, String roundId) {
        warmingRound = roundId;
        warmingPilot = pilot == null ? null : pilot.getUUID();
        warmingSinceGameTime = plane.level().getGameTime();
        warmingSinceNanos = System.nanoTime();
    }

    private boolean warmingFor(LivingEntity pilot, MissileProfile round) {
        return warmingRound != null && warmingRound.equals(round.id)
            && java.util.Objects.equals(warmingPilot,
                pilot == null ? null : pilot.getUUID());
    }

    public static long warmedTicks(long stampTick, long stampNanos,
                                   long nowTick, long nowNanos,
                                   long nanosPerTick) {
        long byLevel = nowTick - stampTick;
        long byWall = (nowNanos - stampNanos) / Math.max(1L, nanosPerTick);
        return Math.max(byLevel, byWall);
    }

    public static boolean warmupAccepted(long stampTick, long stampNanos,
                                         long nowTick, long nowNanos,
                                         int warmupTicks, int toleranceTicks,
                                         long nanosPerTick) {
        return warmedTicks(stampTick, stampNanos, nowTick, nowNanos,
            nanosPerTick) >= (long) warmupTicks - toleranceTicks;
    }

    public boolean launch(LivingEntity pilot,
                          amrac.entities.AircraftTargetSnapshot target) {
        if (plane.level().isClientSide() || !plane.hasMissiles()) {
            return false;
        }
        if (plane.getControllingPassenger() != pilot) {
            return refuse(pilot, "amrac.message.missile_no_solution",
                "not the controlling pilot (passenger graph out of step)");
        }
        long now = plane.level().getGameTime();
        if (now < readyAtGameTime) {
            if (now - launchedAtGameTime <= QUIET_AFTER_LAUNCH_TICKS) {
                return refuse(null, "amrac.message.missile_reloading",
                    "still reloading, " + (readyAtGameTime - now)
                        + " ticks to go (" + pilot.getName().getString()
                        + "'s spare request after a launch, not shown)");
            }
            return refuse(pilot, "amrac.message.missile_reloading",
                "still reloading, " + (readyAtGameTime - now) + " ticks to go");
        }
        if (target == null || target.id().equals(plane.getUUID())) {
            return refuse(pilot, "amrac.message.missile_no_target",
                target == null ? "no target snapshot" : "designated itself");
        }
        String[] slots = plane.getLoadout();
        int rail = MissileLoadout.firstOf(slots, plane.getSelectedMissile());
        if (rail < 0) {
            rail = MissileLoadout.firstLoaded(slots);
        }
        if (rail < 0) {
            return refuse(pilot, "amrac.message.no_missiles",
                "the rails are empty, selected " + plane.getSelectedMissile());
        }
        MissileProfile profile = MissileProfiles.byId(slots[rail]);

        if (profile.warmupTicks > 0
                && !(pilot instanceof amrac.entities.ai.AiPilotEntity)) {
            long perTick = plane.level().tickRateManager().nanosecondsPerTick();
            if (!warmingFor(pilot, profile)) {
                stampWarmup(pilot, profile.id);
                return refuse(pilot, "amrac.message.missile_warming",
                    profile.id + " was cold; warm-up started",
                    String.format(java.util.Locale.ROOT, "%.1f",
                        profile.warmupTicks * perTick / 1.0E9D));
            }
            long nowNanos = System.nanoTime();
            if (!warmupAccepted(warmingSinceGameTime, warmingSinceNanos, now,
                    nowNanos, profile.warmupTicks, WARMUP_TOLERANCE_TICKS,
                    perTick)) {
                long warmed = warmedTicks(warmingSinceGameTime,
                    warmingSinceNanos, now, nowNanos, perTick);
                return refuse(pilot, "amrac.message.missile_still_warming",
                    profile.id + " still warming, " + warmed + " of "
                        + profile.warmupTicks + " ticks",
                    String.format(java.util.Locale.ROOT, "%.1f",
                        (profile.warmupTicks - warmed) * perTick / 1.0E9D));
            }
        }

        Vec3 aim = target.position();
        Vec3 toTarget = aim.subtract(plane.position());
        double range = toTarget.length();
        Vec3 forward = plane.getBodyDirection(0.0F, 0.0F, 1.0F);
        double cos = range > 1.0E-6D ? toTarget.dot(forward) / range : -1.0D;
        if (!MissilePolicy.canLaunch(profile, range, cos)) {
            return refuse(pilot, "amrac.message.missile_no_solution",
                String.format(java.util.Locale.ROOT,
                    "%s out of parameters: range %.0f (allowed %.0f..%.0f), "
                        + "%.0f deg off the nose (allowed %.0f)",
                    profile.id, range, profile.minLaunchRange,
                    profile.maxLaunchRange,
                    Math.toDegrees(Math.acos(Math.max(-1.0D,
                        Math.min(1.0D, cos)))),
                    Math.toDegrees(MissilePolicy.launchCone(profile))));
        }

        double scale = plane.getModelScale();
        double[][] rails = plane.railPositions();
        double[] mount = rails[Math.min(rail, rails.length - 1)];
        Vec3 origin = plane.machineGuns().worldPoint(mount[0] * scale,
            mount[1] * scale, mount[2] * scale);

        Vec3 velocity = plane.getDeltaMovement();

        MissileEntity missile = new MissileEntity(plane.level(), pilot, plane,
            target, origin, velocity, profile);
        plane.level().addFreshEntity(missile);
        if (plane.level() instanceof net.minecraft.server.level.ServerLevel level
                && !amrac.entities.AircraftUpkeepService
                    .isTicking(plane)
                && missile.handOverIfNotTicked(level)) {
            long at = level.getGameTime();
            if (at - lastHandoverTime >= 20L || at < lastHandoverTime) {
                lastHandoverTime = at;
                amrac.AmracMod.LOGGER.info(
                    "{} fired into a chunk that is not entity-ticking; flying it"
                        + " virtually [gameTime {}, planeTick {}]",
                    profile.id, at, plane.tickCount);
            }
        }
        slots[rail] = null;
        plane.setLoadout(slots);
        readyAtGameTime = now + profile.launchCooldownTicks;
        launchedAtGameTime = now;
        warmingRound = null;

        AmracSounds.playMissileLaunch(plane.level(),
            pilot instanceof Player player ? player : null, origin);
        return true;
    }

    public int cooldownTicks() {
        long remaining = readyAtGameTime - plane.level().getGameTime();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remaining));
    }
}

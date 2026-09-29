package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import amrac.client.render.ScreenProjection;
import amrac.entities.PlaneEntity;
import amrac.entities.RadarContact;
import amrac.entities.RadarPolicy;
import amrac.network.PlaneNetworking;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaneRadar {
    public static final class Lock {
        public final java.util.UUID aircraftId;
        public int entityId = -1;
        @Nullable
        public PlaneEntity target;
        public Vec3 position = Vec3.ZERO;
        public Vec3 velocity = Vec3.ZERO;
        public double distance;
        public float width = 6.0F;
        public float height = 3.0F;
        public int ticksSinceSeen;
        public boolean onScreen;
        public boolean current;
        public int ticksSinceHeard;

        Lock(java.util.UUID aircraftId) {
            this.aircraftId = aircraftId;
        }
    }

    private static final int CONTACT_MEMORY_TICKS = 10;

    private static boolean enabled;
    private static final Map<java.util.UUID, Lock> LOCKS = new LinkedHashMap<>();

    private PlaneRadar() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        if (enabled == on) {
            return;
        }
        enabled = on;
        if (!on) {
            LOCKS.clear();
        }
        PlaneNetworking.sendRadarSwitch(on);
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    public static void reset() {
        if (enabled) {
            PlaneNetworking.sendRadarSwitch(false);
        }
        enabled = false;
        LOCKS.clear();
    }

    public static List<Lock> locks() {
        return new ArrayList<>(LOCKS.values());
    }

    public static int lockCount() {
        return LOCKS.size();
    }

    public static void acceptContacts(List<RadarContact> contacts) {
        if (!enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        PlaneEntity own = Minecraft.getInstance().player == null ? null
            : (Minecraft.getInstance().player.getVehicle()
                instanceof PlaneEntity plane ? plane : null);
        var set = own == null ? null : own.getRadarProfile();
        int memory = set == null
            ? CONTACT_MEMORY_TICKS : set.trackMemoryTicks();
        for (RadarContact contact : contacts) {
            Lock lock = LOCKS.computeIfAbsent(contact.aircraftId, Lock::new);
            lock.entityId = contact.entityId;
            lock.position = new Vec3(contact.x, contact.y, contact.z);
            lock.velocity = new Vec3(contact.velocityX, contact.velocityY,
                contact.velocityZ);
            lock.width = contact.width;
            lock.height = contact.height;
            lock.ticksSinceHeard = 0;
            lock.target = minecraft.level != null && contact.entityId >= 0 &&
                minecraft.level.getEntity(contact.entityId)
                    instanceof PlaneEntity plane ? plane : null;
        }

        java.util.Set<java.util.UUID> reported = new java.util.HashSet<>();
        for (RadarContact contact : contacts) {
            reported.add(contact.aircraftId);
        }
        LOCKS.values().removeIf(lock ->
            !reported.contains(lock.aircraftId) &&
                lock.ticksSinceHeard > memory);
    }

    @Nullable
    public static Lock primaryTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        Lock best = null;
        double bestOffset = Double.MAX_VALUE;
        for (Lock lock : LOCKS.values()) {
            if (!lock.onScreen || !lock.current) {
                continue;
            }
            ScreenProjection.Result projected =
                ScreenProjection.project(minecraft, lock.position);
            if (projected == null || projected.behind()) {
                continue;
            }
            double offset = projected.ndcX() * projected.ndcX() +
                projected.ndcY() * projected.ndcY();
            if (offset < bestOffset) {
                bestOffset = offset;
                best = lock;
            }
        }
        return best;
    }

    public static void tick(Minecraft minecraft, PlaneEntity own) {
        if (!enabled || minecraft.level == null || own == null || !own.hasRadar()) {
            if (!LOCKS.isEmpty()) {
                LOCKS.clear();
            }
            return;
        }

        var set = own.getRadarProfile();
        double margin = set == null
            ? RadarPolicy.ACQUIRE_MARGIN : set.acquireMargin();
        int memory = set == null
            ? CONTACT_MEMORY_TICKS : set.trackMemoryTicks();

        Vec3 centre = own.getBoundingBox().getCenter();
        for (Lock lock : LOCKS.values()) {
            ++lock.ticksSinceHeard;
            if (lock.target != null && lock.target.isAlive()) {
                lock.position = lock.target.getBoundingBox().getCenter();
                lock.velocity = lock.target.getDeltaMovement();
            } else {
                lock.position = lock.position.add(lock.velocity);
            }

            lock.distance = centre.distanceTo(lock.position);
            ScreenProjection.Result projected =
                ScreenProjection.project(minecraft, lock.position);
            boolean visible = projected != null && RadarPolicy.isOnScreen(
                projected.ndcX(), projected.ndcY(), projected.depth(), margin);
            lock.onScreen = visible;
            lock.current = RadarPolicy.releasable(lock.ticksSinceHeard,
                set == null ? 4 : set.scanIntervalTicks());
            if (visible) {
                lock.ticksSinceSeen = 0;
            } else {
                ++lock.ticksSinceSeen;
            }
        }

        LOCKS.values().removeIf(lock -> lock.ticksSinceHeard > memory);
    }
}

package amrac.entities;

import amrac.platform.ServerEntityEvents;
import amrac.platform.ServerTickEvents;
import amrac.platform.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import amrac.network.PlaneNetworking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlaneRadarService {
    public static final int SWEEP_INTERVAL_TICKS = 4;

    public static final int MAX_CONTACTS = 32;

    private static final Set<UUID> RADAR_ON = new HashSet<>();

    private static final Set<UUID> HOLDING_CONTACTS = new HashSet<>();

    private PlaneRadarService() {
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((player, server) -> {
            UUID id = player.getUUID();
            RADAR_ON.remove(id);
            HOLDING_CONTACTS.remove(id);
        });
        ServerTickEvents.END_SERVER_TICK.register(PlaneRadarService::tick);
    }

    public static void setRadarEnabled(ServerPlayer player, boolean on) {
        if (on) {
            RADAR_ON.add(player.getUUID());
        } else {
            RADAR_ON.remove(player.getUUID());
        }
    }

    public static boolean isRadarEnabled(ServerPlayer player) {
        return RADAR_ON.contains(player.getUUID());
    }

    private static int sweepIntervalFor(ServerPlayer player) {
        if (player.getVehicle() instanceof PlaneEntity plane) {
            var set = plane.getRadarProfile();
            if (set != null) {
                return set.scanIntervalTicks();
            }
        }
        return SWEEP_INTERVAL_TICKS;
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (now % sweepIntervalFor(player) != 0) {
                continue;
            }
            List<RadarContact> contacts = sweepFor(player);
            UUID id = player.getUUID();
            if (contacts.isEmpty()) {
                if (HOLDING_CONTACTS.remove(id)) {
                    PlaneNetworking.sendRadarContacts(player, contacts);
                }
                continue;
            }
            HOLDING_CONTACTS.add(id);
            PlaneNetworking.sendRadarContacts(player, contacts);
        }
    }

    public static List<RadarContact> sweepFor(ServerPlayer player) {
        if (!RADAR_ON.contains(player.getUUID())) {
            return Collections.emptyList();
        }
        if (!(player.getVehicle() instanceof PlaneEntity own) ||
            own.getControllingPassenger() != player || !own.hasRadar() ||
            !own.isAlive()) {
            return Collections.emptyList();
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return Collections.emptyList();
        }
        return scanFrom(level, own);
    }

    private static double heightAboveGround(ServerLevel level,
                                            AircraftRegistry.Record target) {
        Vec3 from = target.position;
        if (!level.hasChunkAt(BlockPos.containing(from))) {
            return GroundProximityPolicy.NO_GROUND;
        }
        Vec3 to = from.subtract(0.0D, GroundProximityPolicy.MAX_SOUNDING_DEPTH, 0.0D);
        HitResult hit = level.clip(new ClipContext(from, to,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
            net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            return GroundProximityPolicy.NO_GROUND;
        }
        return Math.max(0.0D, from.y - hit.getLocation().y);
    }

    public static List<RadarContact> scanFrom(ServerLevel level, PlaneEntity own) {
        List<AircraftRegistry.Record> candidates =
            AircraftRegistry.simulated(level);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        Vec3 eye = own.getBoundingBox().getCenter();
        Vec3 forward = own.getBodyDirection(0.0F, 0.0F, 1.0F);
        var set = own.getRadarProfile();
        Vec3 up = set == null ? null : own.getBodyDirection(0.0F, 1.0F, 0.0F);
        Vec3 right = set == null ? null : own.getBodyDirection(1.0F, 0.0F, 0.0F);
        List<RadarContact> contacts = new ArrayList<>();
        List<Double> ranges = new ArrayList<>();

        for (AircraftRegistry.Record target : candidates) {
            if (target.id.equals(own.getUUID())) {
                continue;
            }
            if (target.presence == AircraftRegistry.Presence.LIVE
                && AircraftRegistry.liveEntity(target.id) == null) {
                continue;
            }
            Vec3 aim = target.position;
            double distance = eye.distanceTo(aim);
            if (set == null ? !RadarPolicy.isInRange(distance)
                : distance > set.lockRange()) {
                continue;
            }
            if (set == null) {
                if (!RadarPolicy.withinScanCone(forward.x(), forward.y(),
                    forward.z(), aim.x() - eye.x(), aim.y() - eye.y(),
                    aim.z() - eye.z())) {
                    continue;
                }
            } else if (!RadarPolicy.withinScanVolume(set.azimuthLimit(),
                set.elevationLimit(),
                new double[] {forward.x(), forward.y(), forward.z()},
                new double[] {up.x(), up.y(), up.z()},
                new double[] {right.x(), right.y(), right.z()},
                new double[] {aim.x() - eye.x(), aim.y() - eye.y(),
                    aim.z() - eye.z()})) {
                continue;
            }
            if (set != null && amrac.weapons.SeekerPolicy.velocityGated(
                set.velocityGate(), set.velocityGateLookDownOnly(),
                target.velocity, aim.subtract(eye), eye.y, aim.y)) {
                continue;
            }
            if (GroundProximityPolicy.isLostInClutter(
                heightAboveGround(level, target))) {
                continue;
            }
            contacts.add(new RadarContact(target.id, target.entityId, aim.x(), aim.y(),
                aim.z(), (float) target.velocity.x(),
                (float) target.velocity.y(), (float) target.velocity.z(),
                target.width, target.height));
            ranges.add(distance);
        }

        int held = set == null ? MAX_CONTACTS : set.maxContacts();
        if (contacts.size() > held) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < contacts.size(); i++) {
                order.add(i);
            }
            order.sort((a, b) -> Double.compare(ranges.get(a), ranges.get(b)));
            List<RadarContact> nearest = new ArrayList<>(held);
            for (int i = 0; i < held; i++) {
                nearest.add(contacts.get(order.get(i)));
            }
            return nearest;
        }
        return contacts;
    }
}

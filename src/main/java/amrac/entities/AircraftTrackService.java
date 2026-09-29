package amrac.entities;

import amrac.platform.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import amrac.network.PlaneNetworking;

public final class AircraftTrackService {
    public static final double TRACK_RANGE = 2000.0D;

    public static final int MAX_CONTACTS = 12;

    private AircraftTrackService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AircraftTrackService::tick);
    }

    private static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) {
                continue;
            }
            List<AircraftRegistry.Record> simulated =
                AircraftRegistry.simulated(level);
            if (simulated.isEmpty()) {
                continue;
            }
            Map<UUID, VirtualAircraftState> virtual = virtualById();
            for (ServerPlayer player : players) {
                send(player, simulated, virtual);
            }
        }
    }

    private static Map<UUID, VirtualAircraftState> virtualById() {
        List<VirtualAircraftState> states = AircraftVirtualService.all();
        if (states.isEmpty()) {
            return Map.of();
        }
        Map<UUID, VirtualAircraftState> byId = new HashMap<>(states.size() * 2);
        for (VirtualAircraftState state : states) {
            byId.put(state.id, state);
        }
        return byId;
    }

    private static void send(ServerPlayer player,
                             List<AircraftRegistry.Record> simulated,
                             Map<UUID, VirtualAircraftState> virtual) {
        Vec3 eye = player.position();
        Entity vehicle = player.getRootVehicle();
        UUID own = vehicle == player ? null : vehicle.getUUID();

        List<PlaneNetworking.AircraftTrack> near = null;
        for (AircraftRegistry.Record record : simulated) {
            if (record.id.equals(own)) {
                continue;
            }
            double dx = record.position.x - eye.x;
            double dy = record.position.y - eye.y;
            double dz = record.position.z - eye.z;
            if (dx * dx + dy * dy + dz * dz > TRACK_RANGE * TRACK_RANGE) {
                continue;
            }
            PlaneNetworking.AircraftTrack contact = contactFor(record, virtual);
            if (contact == null) {
                continue;
            }
            if (near == null) {
                near = new ArrayList<>(MAX_CONTACTS);
            }
            near.add(contact);
            if (near.size() >= MAX_CONTACTS) {
                break;
            }
        }
        if (near != null) {
            PlaneNetworking.sendAircraftTracks(player, eye, near);
        }
    }

    private static PlaneNetworking.AircraftTrack contactFor(
            AircraftRegistry.Record record,
            Map<UUID, VirtualAircraftState> virtual) {
        PlaneEntity live = AircraftRegistry.liveEntity(record.id);
        if (live != null && live.isAlive()) {
            Quaternionf attitude = live.getQ();
            return new PlaneNetworking.AircraftTrack(record.id,
                BuiltInRegistries.ENTITY_TYPE.getId(live.getType()),
                live.getX(), live.getY(), live.getZ(),
                attitude.x(), attitude.y(), attitude.z(), attitude.w(),
                live.isGearDown(), live.isAfterburnerLit());
        }
        VirtualAircraftState state = virtual.get(record.id);
        if (state == null) {
            return null;
        }
        int typeId = BuiltInRegistries.ENTITY_TYPE.getId(
            BuiltInRegistries.ENTITY_TYPE.getValue(state.type));
        if (typeId < 0) {
            return null;
        }
        Quaternionf attitude = state.attitude;
        return new PlaneNetworking.AircraftTrack(record.id, typeId,
            state.position.x, state.position.y, state.position.z,
            attitude.x(), attitude.y(), attitude.z(), attitude.w(),
            false, false);
    }
}

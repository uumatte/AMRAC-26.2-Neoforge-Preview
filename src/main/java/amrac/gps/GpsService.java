package amrac.gps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import amrac.entities.AircraftRegistry;
import amrac.entities.MissileEntity;
import amrac.entities.ai.AiPilotBrain;
import amrac.entities.ai.AiPilotService;
import amrac.network.PlaneNetworking;
import amrac.weapons.VirtualMissileService;
import amrac.weapons.VirtualMissileState;

public final class GpsService {
    private static final long MIN_INTERVAL_TICKS = 3L;

    private static final Map<UUID, Long> LAST_SERVED = new HashMap<>();

    private static final double WORLD_REACH = 3.0E7D;

    private static final Map<net.minecraft.resources.ResourceKey<
        net.minecraft.world.level.Level>, Cached> MISSILE_CACHE = new HashMap<>();

    private GpsService() {
    }

    public static void serve(ServerPlayer player, boolean full) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_SERVED.get(player.getUUID());
        if (last != null && now - last < MIN_INTERVAL_TICKS && now >= last) {
            return;
        }
        LAST_SERVED.put(player.getUUID(), now);
        PlaneNetworking.sendGpsContacts(player,
            contacts(level, player.position(), player, full),
            PinService.visibleTo(player));
    }

    public static void forget(UUID playerId) {
        LAST_SERVED.remove(playerId);
    }

    public static List<GpsContact> contacts(ServerLevel level, Vec3 centre,
                                            @org.jetbrains.annotations.Nullable
                                            ServerPlayer viewer, boolean full) {
        List<GpsContact> out = new ArrayList<>();
        addMissiles(level, viewer, out);
        if (!full) {
            return out;
        }

        for (ServerPlayer player : level.players()) {
            if (player == viewer || player.isSpectator()) {
                continue;
            }
            if (!GpsPolicy.onDial(player.getX() - centre.x,
                    player.getZ() - centre.z, GpsPolicy.RANGE)) {
                continue;
            }
            net.minecraft.world.entity.Entity tracked =
                player.getVehicle() == null ? player : player.getVehicle();
            out.add(new GpsContact(GpsContact.Kind.PLAYER,
                player.getGameProfile().name(),
                tracked.getX(), tracked.getY(), tracked.getZ(),
                tracked.getDeltaMovement().length()));
        }

        for (AircraftRegistry.Record record : AircraftRegistry.all(level)) {
            Vec3 at = record.position;
            if (!GpsPolicy.onDial(at.x - centre.x, at.z - centre.z,
                    GpsPolicy.RANGE)) {
                continue;
            }
            if (record.playerCrewed) {
                continue;
            }
            AiPilotBrain pilot = AiPilotService.flying(record.id);
            GpsContact.Kind kind = pilot != null || record.crewed
                ? GpsContact.Kind.AI_AIRCRAFT : GpsContact.Kind.EMPTY_AIRCRAFT;
            String name = pilot == null ? "Aircraft" : contactName(pilot);
            out.add(new GpsContact(kind, name, at.x, at.y, at.z,
                record.velocity.length(), record.simulated()));
        }

        return out;
    }

    private static String contactName(AiPilotBrain pilot) {
        String team = pilot.team();
        return team == null || team.isBlank()
            ? pilot.callsign() : pilot.callsign() + " [" + team + "]";
    }

    private static void addMissiles(ServerLevel level,
                                    @org.jetbrains.annotations.Nullable
                                    ServerPlayer viewer,
                                    List<GpsContact> out) {
        UUID owner = viewer == null ? null : viewer.getUUID();
        for (Mark mark : missileMarks(level)) {
            out.add(new GpsContact(owner != null && owner.equals(mark.owner())
                ? GpsContact.Kind.OWN_MISSILE : GpsContact.Kind.MISSILE,
                mark.name(), mark.x(), mark.y(), mark.z(), mark.speed(), true,
                mark.seekerActive()));
        }
    }

    private record Mark(UUID owner, String name, double x, double y, double z,
                        double speed, boolean seekerActive) {
    }

    private static List<Mark> missileMarks(ServerLevel level) {
        long now = level.getServer().getTickCount();
        Cached cached = MISSILE_CACHE.get(level.dimension());
        if (cached != null && cached.tick == now) {
            return cached.marks;
        }

        List<Mark> contacts = new ArrayList<>();
        AABB everywhere = new AABB(-WORLD_REACH, level.getMinY(), -WORLD_REACH,
            WORLD_REACH, level.getMaxY(), WORLD_REACH);
        for (Entity entity : level.getEntities(
                EntityTypeTest.forClass(MissileEntity.class), everywhere,
                MissileEntity::isAlive)) {
            if (entity instanceof MissileEntity missile) {
                contacts.add(new Mark(missile.ownerId(),
                    missile.weaponName().getString(),
                    missile.getX(), missile.getY(), missile.getZ(),
                    missile.getDeltaMovement().length(),
                    missile.isSeekerActive()));
            }
        }
        for (VirtualMissileState state : VirtualMissileService.all()) {
            if (!state.dimension.equals(level.dimension())) {
                continue;
            }
            contacts.add(new Mark(state.ownerId, state.profile.id,
                state.position.x, state.position.y, state.position.z,
                state.velocity.length(),
                state.profile.seekerType == amrac.weapons.SeekerType.ARH
                    && state.seeker.active));
        }

        MISSILE_CACHE.put(level.dimension(), new Cached(now, contacts));
        return contacts;
    }

    private record Cached(long tick, List<Mark> marks) {
    }
}

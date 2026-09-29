package amrac.weapons;

import amrac.platform.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import amrac.network.PlaneNetworking;

import java.util.ArrayList;
import java.util.List;

public final class MissileTrackService {
    public static final double TRACK_RANGE = 3000.0D;

    public static final int MAX_TRACKS = 24;

    private static final List<PlaneNetworking.MissileTrack> REPORTS =
        new ArrayList<>();

    private MissileTrackService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!REPORTS.isEmpty()) {
                for (ServerLevel level : server.getAllLevels()) {
                    for (ServerPlayer player : level.players()) {
                        send(player);
                    }
                }
            }
            REPORTS.clear();
        });
    }

    public static void report(int key, Vec3 position, boolean powered) {
        if (REPORTS.size() >= MAX_TRACKS * 8) {
            return;
        }
        REPORTS.add(new PlaneNetworking.MissileTrack(key, position.x,
            position.y, position.z, powered));
    }

    private static void send(ServerPlayer player) {
        Vec3 eye = player.position();
        List<PlaneNetworking.MissileTrack> near = null;
        for (PlaneNetworking.MissileTrack track : REPORTS) {
            double dx = track.x() - eye.x;
            double dy = track.y() - eye.y;
            double dz = track.z() - eye.z;
            if (dx * dx + dy * dy + dz * dz > TRACK_RANGE * TRACK_RANGE) {
                continue;
            }
            if (near == null) {
                near = new ArrayList<>(MAX_TRACKS);
            }
            near.add(track);
            if (near.size() >= MAX_TRACKS) {
                break;
            }
        }
        if (near != null) {
            PlaneNetworking.sendMissileTracks(player, eye, near);
        }
    }
}

package amrac.weapons;

import amrac.platform.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.network.PlaneNetworking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MissileSeekerService {
    public static final int MAX_PER_PILOT = 8;

    private static final Map<UUID, List<PlaneNetworking.MissileSeeker>> REPORTS =
        new HashMap<>();

    private MissileSeekerService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!REPORTS.isEmpty()) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    List<PlaneNetworking.MissileSeeker> frames =
                        REPORTS.get(player.getUUID());
                    if (frames != null && !frames.isEmpty()) {
                        PlaneNetworking.sendSeekerFrames(player,
                            player.position(), frames);
                    }
                }
            }
            REPORTS.clear();
        });
    }

    public static void report(int key, @Nullable UUID owner, Vec3 position,
                              Vec3 axis, Vec3 velocity,
                              @Nullable Vec3 targetPos,
                              @Nullable Vec3 targetVelocity,
                              boolean locked, boolean powered) {
        if (owner == null) {
            return;
        }
        List<PlaneNetworking.MissileSeeker> frames =
            REPORTS.computeIfAbsent(owner, id -> new ArrayList<>(MAX_PER_PILOT));
        if (frames.size() >= MAX_PER_PILOT) {
            return;
        }
        double closure = 0.0D;
        if (targetPos != null) {
            Vec3 line = targetPos.subtract(position);
            double range = line.length();
            if (range > 1.0E-6D) {
                Vec3 relative = velocity.subtract(
                    targetVelocity == null ? Vec3.ZERO : targetVelocity);
                closure = relative.dot(line.scale(1.0D / range));
            }
        }
        frames.add(new PlaneNetworking.MissileSeeker(key,
            position.x, position.y, position.z,
            (float) axis.x, (float) axis.y, (float) axis.z,
            (float) (velocity.length() * 20.0D),
            targetPos != null,
            targetPos == null ? 0.0D : targetPos.x,
            targetPos == null ? 0.0D : targetPos.y,
            targetPos == null ? 0.0D : targetPos.z,
            (float) (closure * 20.0D),
            (float) ((targetVelocity == null ? Vec3.ZERO : targetVelocity)
                .length() * 20.0D),
            locked, powered, false));
    }

    public static void reportHit(int key, @Nullable UUID owner) {
        if (owner == null) {
            return;
        }
        REPORTS.computeIfAbsent(owner, id -> new ArrayList<>(MAX_PER_PILOT))
            .add(new PlaneNetworking.MissileSeeker(key,
                0.0D, 0.0D, 0.0D, 0.0F, 0.0F, 0.0F, 0.0F,
                false, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, false, false, true));
    }
}

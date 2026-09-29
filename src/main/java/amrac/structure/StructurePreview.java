package amrac.structure;

import amrac.platform.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import amrac.network.PlaneNetworking;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class StructurePreview {
    public static final int TIMEOUT_TICKS = 100;

    public enum Kind {
        RUNWAY,
        HANGAR
    }

    private static final class Pending {
        final BlockPos origin;
        final Direction facing;
        final Kind kind;
        int ticksLeft = TIMEOUT_TICKS;

        Pending(BlockPos origin, Direction facing, Kind kind) {
            this.origin = origin;
            this.facing = facing;
            this.kind = kind;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private StructurePreview() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StructurePreview::tick);
    }

    @Nullable
    public static Direction armOrConfirm(ServerPlayer player, BlockPos origin,
                                         Direction facing, Kind kind,
                                         int length, int halfWidth, int height) {
        UUID owner = player.getUUID();
        Pending pending = PENDING.get(owner);
        if (pending != null && pending.kind == kind
                && pending.origin.equals(origin)) {
            PENDING.remove(owner);
            PlaneNetworking.sendStructurePreview(player, null, 0);
            return pending.facing;
        }

        PENDING.put(owner, new Pending(origin.immutable(), facing, kind));
        int[] box = new int[6];
        StructureAxis.bounds(origin.getX(), origin.getY(), origin.getZ(),
            facing.getStepX(), facing.getStepZ(), length, halfWidth, height, box);
        PlaneNetworking.sendStructurePreview(player, box, TIMEOUT_TICKS);
        return null;
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Pending>> entries =
                PENDING.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<UUID, Pending> entry = entries.next();
            if (--entry.getValue().ticksLeft > 0) {
                continue;
            }
            entries.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                PlaneNetworking.sendStructurePreview(player, null, 0);
            }
        }
    }

    public static void forget(UUID owner) {
        PENDING.remove(owner);
    }
}

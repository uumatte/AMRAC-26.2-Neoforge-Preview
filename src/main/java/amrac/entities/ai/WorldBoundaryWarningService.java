package amrac.entities.ai;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import amrac.platform.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import amrac.AmracMod;
import amrac.network.PlaneNetworking;

public final class WorldBoundaryWarningService {
    private static final int UPDATE_INTERVAL_TICKS = 20;

    private static final Set<UUID> INSIDE = new HashSet<>();

    private WorldBoundaryWarningService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(WorldBoundaryWarningService::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % UPDATE_INTERVAL_TICKS != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            PlayAreaBounds.Rectangle area = PlayAreaBounds.of(level);
            for (ServerPlayer player : level.players()) {
                double distance = WorldBoundaryPolicy.distanceToBoundary(
                    area.minX(), area.maxX(), area.minZ(), area.maxZ(),
                    player.getX(), player.getZ());
                boolean inside = distance <= WorldBoundaryPolicy.playerWarningDistance();
                if (inside) {
                    long metres = Math.max(0L, Math.round(distance));
                    AmracMod.sendOverlay(player, Component.literal(
                        "Warning: boundary " + metres + " m")
                        .withStyle(ChatFormatting.RED), true);
                    INSIDE.add(player.getUUID());
                    PlaneNetworking.sendBoundary(player, distance);
                } else if (INSIDE.remove(player.getUUID())) {
                    PlaneNetworking.sendBoundary(player, -1.0D);
                }
            }
        }
    }
}

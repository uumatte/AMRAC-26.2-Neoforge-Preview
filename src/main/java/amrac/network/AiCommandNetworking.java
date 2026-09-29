package amrac.network;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import amrac.platform.PayloadTypeRegistry;
import amrac.platform.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.AmracMod;
import amrac.blocks.AiCommandBlockEntity;
import amrac.entities.ai.AiLaunchCatalog;
import amrac.entities.ai.AiLaunchOrder;
import amrac.entities.ai.AiPilotRank;

public final class AiCommandNetworking {
    public static final CustomPacketPayload.Type<PlaneNetworking.RawPayload> OPEN =
        new CustomPacketPayload.Type<>(AmracMod.id("ai_command_open"));
    public static final CustomPacketPayload.Type<PlaneNetworking.RawPayload> SET =
        new CustomPacketPayload.Type<>(AmracMod.id("ai_command_set"));

    private static final double EDIT_REACH = 64.0D;

    private static final int NAME_LENGTH = 64;

    private AiCommandNetworking() {
    }

    static void registerPayloadTypes() {
        PayloadTypeRegistry.clientboundPlay().register(OPEN,
            PlaneNetworking.codec(OPEN, 64 * 1024));
        PayloadTypeRegistry.serverboundPlay().register(SET,
            PlaneNetworking.codec(SET, 2 * 1024));
    }

    static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(SET, (payload, context) -> {
            FriendlyByteBuf buf = payload.buffer();
            BlockPos pos = buf.readBlockPos();
            AiLaunchOrder order = readOrder(buf);
            ServerPlayer player = context.player();
            context.server().execute(() -> apply(player, pos, order));
        });
    }

    private static void apply(ServerPlayer player, BlockPos pos,
                              AiLaunchOrder order) {
        if (!player.canUseGameMasterBlocks()
                || !(player.level() instanceof ServerLevel level)
                || !level.isLoaded(pos)
                || player.distanceToSqr(Vec3.atCenterOf(pos))
                    > EDIT_REACH * EDIT_REACH) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof AiCommandBlockEntity block) {
            block.setOrder(order);
        }
    }

    public record Opened(BlockPos pos, AiLaunchCatalog catalog,
                         List<String> teams) {
    }

    public static void open(ServerPlayer player, BlockPos pos,
                            AiLaunchCatalog catalog, List<String> teams) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(catalog.airframes().size());
        for (AiLaunchCatalog.Airframe airframe : catalog.airframes()) {
            buf.writeUtf(airframe.name(), NAME_LENGTH);
            buf.writeUtf(airframe.flightModelId(), NAME_LENGTH);
            buf.writeBoolean(airframe.hasMissiles());
            buf.writeVarInt(airframe.pylons());
            buf.writeVarInt(airframe.chaff());
            buf.writeVarInt(airframe.flare());
            writeNames(buf, airframe.missiles());
        }
        buf.writeVarInt(catalog.clearances().size());
        for (Map.Entry<AiPilotRank, Set<String>> entry
                : catalog.clearances().entrySet()) {
            buf.writeVarInt(entry.getKey().ordinal());
            writeNames(buf, entry.getValue());
        }
        List<String> fitting = teams.stream()
            .filter(team -> team.length() <= NAME_LENGTH).toList();
        writeNames(buf, fitting);
        ServerPlayNetworking.send(player, PlaneNetworking.payload(OPEN, buf));
    }

    public static Opened readOpen(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = Math.min(buf.readVarInt(), 256);
        List<AiLaunchCatalog.Airframe> airframes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf(NAME_LENGTH);
            String model = buf.readUtf(NAME_LENGTH);
            boolean missiles = buf.readBoolean();
            int pylons = buf.readVarInt();
            int chaff = buf.readVarInt();
            int flare = buf.readVarInt();
            airframes.add(new AiLaunchCatalog.Airframe(name, model, missiles,
                pylons, new LinkedHashSet<>(readNames(buf)), chaff, flare));
        }
        Map<AiPilotRank, Set<String>> clearances = new EnumMap<>(AiPilotRank.class);
        int ranks = Math.min(buf.readVarInt(), 64);
        for (int i = 0; i < ranks; i++) {
            AiPilotRank rank = AiPilotRank.byOrdinal(buf.readVarInt());
            clearances.put(rank, new LinkedHashSet<>(readNames(buf)));
        }
        List<String> teams = readNames(buf);
        return new Opened(pos, new AiLaunchCatalog(airframes, clearances), teams);
    }

    private static void writeNames(FriendlyByteBuf buf,
                                   java.util.Collection<String> names) {
        buf.writeVarInt(names.size());
        for (String name : names) {
            buf.writeUtf(name, NAME_LENGTH);
        }
    }

    private static List<String> readNames(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 1024);
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            names.add(buf.readUtf(NAME_LENGTH));
        }
        return names;
    }

    public interface Sender {
        void send(BlockPos pos, AiLaunchOrder order);
    }

    @Nullable
    private static Sender sender;

    public static void setSender(Sender value) {
        sender = value;
    }

    public static void send(BlockPos pos, AiLaunchOrder order) {
        if (sender != null) {
            sender.send(pos, order);
        }
    }

    public static void writeOrder(FriendlyByteBuf buf, BlockPos pos,
                                  AiLaunchOrder order) {
        buf.writeBlockPos(pos);
        buf.writeUtf(order.aircraft(), NAME_LENGTH);
        buf.writeVarInt(order.rank().ordinal());
        buf.writeUtf(order.team(), NAME_LENGTH);
        buf.writeVarInt(order.fuelPercent());
        buf.writeUtf(order.loadout(), AiLaunchOrder.MAX_TEXT);
        buf.writeUtf(order.position(), AiLaunchOrder.MAX_TEXT);
        buf.writeFloat(order.heading());
    }

    static AiLaunchOrder readOrder(FriendlyByteBuf buf) {
        return new AiLaunchOrder(buf.readUtf(NAME_LENGTH),
            AiPilotRank.byOrdinal(buf.readVarInt()), buf.readUtf(NAME_LENGTH),
            buf.readVarInt(), buf.readUtf(AiLaunchOrder.MAX_TEXT),
            buf.readUtf(AiLaunchOrder.MAX_TEXT), buf.readFloat());
    }
}

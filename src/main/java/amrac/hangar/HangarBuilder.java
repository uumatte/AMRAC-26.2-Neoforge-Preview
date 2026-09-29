package amrac.hangar;

import amrac.platform.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import amrac.AmracMod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class HangarBuilder {
    private static final int STEPS_PER_TICK = 4;

    private static final BlockState FLOOR =
        Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
    private static final BlockState SHELL =
        Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
    private static final BlockState LAMP = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState STRUCTURE =
        Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState();
    private static final BlockState PAINT =
        Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
    private static final BlockState GLAZING = Blocks.GLASS.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private static final List<Job> JOBS = new ArrayList<>();

    private HangarBuilder() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(HangarBuilder::tick);
    }

    private static final class Job {
        final ServerLevel level;
        final UUID owner;
        final BlockPos origin;
        final int forwardX;
        final int forwardZ;
        int step;

        Job(ServerLevel level, UUID owner, BlockPos origin, Direction facing) {
            this.level = level;
            this.owner = owner;
            this.origin = origin;
            this.forwardX = facing.getStepX();
            this.forwardZ = facing.getStepZ();
        }
    }

    public static boolean busy(UUID owner) {
        for (Job job : JOBS) {
            if (job.owner.equals(owner)) {
                return true;
            }
        }
        return false;
    }

    public static void start(ServerLevel level, ServerPlayer player,
                             BlockPos origin, Direction facing) {
        JOBS.add(new Job(level, player.getUUID(), origin.immutable(), facing));
        AmracMod.sendOverlay(player, Component.translatable(
            "amrac.message.hangar_started",
            String.valueOf(HangarPolicy.HALL_LENGTH),
            String.valueOf(HangarPolicy.interiorWidth()),
            facing.getName()).withStyle(ChatFormatting.GRAY), false);
    }

    private static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        for (Iterator<Job> jobs = JOBS.iterator(); jobs.hasNext(); ) {
            Job job = jobs.next();
            if (advance(job)) {
                continue;
            }
            jobs.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(job.owner);
            if (player != null) {
                AmracMod.sendOverlay(player, Component.translatable(
                    "amrac.message.hangar_done")
                    .withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    private static boolean advance(Job job) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] offset = new int[2];
        int end = Math.min(HangarPolicy.TOTAL_LENGTH, job.step + STEPS_PER_TICK);

        for (; job.step < end; job.step++) {
            for (int lateral = -HangarPolicy.HALF_WIDTH;
                    lateral <= HangarPolicy.HALF_WIDTH; lateral++) {
                HangarPolicy.offset(job.step, lateral, job.forwardX,
                    job.forwardZ, offset);
                int x = job.origin.getX() + offset[0];
                int z = job.origin.getZ() + offset[1];

                for (int up = 0; up < HangarPolicy.TOTAL_HEIGHT; up++) {
                    cursor.set(x, job.origin.getY() + up, z);
                    BlockState wanted = switch (HangarPolicy.piece(job.step, lateral, up)) {
                        case FLOOR -> FLOOR;
                        case MARKING -> PAINT;
                        case WALL, ROOF -> SHELL;
                        case TRUSS, PLINTH, FRAME -> STRUCTURE;
                        case GLASS -> GLAZING;
                        case LIGHT -> LAMP;
                        case CLEAR -> AIR;
                    };
                    if (wanted == AIR && job.level.getBlockState(cursor).isAir()) {
                        continue;
                    }
                    job.level.setBlock(cursor, wanted, FLAGS);
                }
            }
        }
        return job.step < HangarPolicy.TOTAL_LENGTH;
    }

    public static void forget(UUID owner) {
        JOBS.removeIf(job -> job.owner.equals(owner));
    }
}

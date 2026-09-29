package amrac.runway;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
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

public final class RunwayBuilder {
    public static final int ROWS_PER_TICK = 2;

    private static final BlockState SURFACE =
        Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState();
    private static final BlockState PAINT =
        Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private static final List<Job> JOBS = new ArrayList<>();

    private RunwayBuilder() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RunwayBuilder::tick);
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
            "amrac.message.runway_started",
            String.valueOf(RunwayPolicy.LENGTH),
            String.valueOf(RunwayPolicy.WIDTH),
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
                    "amrac.message.runway_done",
                    String.valueOf(RunwayPolicy.LENGTH))
                    .withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    private static boolean advance(Job job) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] offset = new int[2];
        int end = Math.min(RunwayPolicy.LENGTH, job.step + ROWS_PER_TICK);

        for (; job.step < end; job.step++) {
            for (int lateral = -RunwayPolicy.HALF_WIDTH;
                    lateral <= RunwayPolicy.HALF_WIDTH; lateral++) {
                RunwayPolicy.offset(job.step, lateral, job.forwardX,
                    job.forwardZ, offset);
                int x = job.origin.getX() + offset[0];
                int z = job.origin.getZ() + offset[1];
                int y = job.origin.getY();

                cursor.set(x, y, z);
                job.level.setBlock(cursor,
                    RunwayPolicy.painted(job.step, lateral) ? PAINT : SURFACE,
                    FLAGS);

                for (int up = 1; up <= RunwayPolicy.CLEARANCE; up++) {
                    cursor.set(x, y + up, z);
                    if (!job.level.getBlockState(cursor).isAir()) {
                        job.level.setBlock(cursor, AIR, FLAGS);
                    }
                }
            }
        }
        return job.step < RunwayPolicy.LENGTH;
    }

    public static void forget(UUID owner) {
        JOBS.removeIf(job -> job.owner.equals(owner));
    }
}

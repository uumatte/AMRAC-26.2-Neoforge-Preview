package amrac.weapons;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import amrac.entities.ImpactTntEntity;
import amrac.entities.PlaneEntity;

import java.util.Locale;
import java.util.UUID;

public final class BombingSystem {
    private final PlaneEntity plane;
    private long cooldownEndGameTime;
    private long secondBombReleaseGameTime = -1L;
    @Nullable
    private UUID pendingOwnerUuid;

    public BombingSystem(PlaneEntity plane) {
        this.plane = plane;
    }

    public void tryDropBombs(Player player) {
        if (plane.level().isClientSide() || !plane.isAlive() ||
            plane.getControllingPassenger() != player ||
            !BombingPolicy.isWeaponSystemAvailable(plane.hasBombRack())) {
            return;
        }

        long gameTime = plane.level().getGameTime();
        int remainingCooldown = BombingPolicy.remainingCooldownTicks(gameTime,
            cooldownEndGameTime);
        if (remainingCooldown > 0) {
            amrac.AmracMod.sendOverlay(player,
                Component.translatable(
                "amrac.message.bomb_cooldown",
                String.format(Locale.ROOT, "%.1f", (double) remainingCooldown /
                    BombingPolicy.SERVER_TICKS_PER_SECOND)), true);
            playRejectedSound(player);
            return;
        }

        boolean creativeMode = player.isCreative();
        if (!BombingPolicy.hasRequiredTnt(creativeMode, plane.getTntAmmoCount())) {
            amrac.AmracMod.sendOverlay(player,
                Component.translatable(
                "amrac.message.not_enough_tnt",
                BombingPolicy.BOMBS_PER_SALVO), true);
            playRejectedSound(player);
            return;
        }

        int requiredTnt = BombingPolicy.requiredTnt(creativeMode);
        if (requiredTnt > 0 && !plane.consumeTntAmmo(requiredTnt)) {
            amrac.AmracMod.sendOverlay(player,
                Component.translatable(
                "amrac.message.not_enough_tnt",
                BombingPolicy.BOMBS_PER_SALVO), true);
            playRejectedSound(player);
            return;
        }

        releaseBomb(player);
        pendingOwnerUuid = player.getUUID();
        secondBombReleaseGameTime = gameTime + BombingPolicy.INTER_BOMB_DELAY_TICKS;
        cooldownEndGameTime = gameTime + BombingPolicy.COOLDOWN_TICKS;
    }

    public void tick() {
        if (plane.level().isClientSide() || secondBombReleaseGameTime < 0L) {
            return;
        }
        if (!plane.isAlive()) {
            clearPendingBomb();
            return;
        }
        if (plane.level().getGameTime() < secondBombReleaseGameTime) {
            return;
        }

        releaseBomb(resolvePendingOwner());
        clearPendingBomb();
    }

    public int remainingCooldownTicks() {
        return BombingPolicy.remainingCooldownTicks(
            plane.level().getGameTime(), cooldownEndGameTime);
    }

    private void releaseBomb(@Nullable LivingEntity owner) {
        Vec3 position = getWorldPoint(0.0D, BombingPolicy.RELEASE_LOCAL_Y, 0.0D);
        Vector3f transformedDown = plane.transformPos(
            new Vector3f(0.0F, -1.0F, 0.0F));
        Vec3 down = new Vec3(transformedDown.x(), transformedDown.y(),
            transformedDown.z());
        if (!isFinite(down) || down.lengthSqr() < 1.0E-12D) {
            down = new Vec3(0.0D, -1.0D, 0.0D);
        } else {
            down = down.normalize();
        }
        Vec3 velocity = plane.getDeltaMovement()
            .scale(BombingPolicy.INHERITED_AIRCRAFT_VELOCITY_MULTIPLIER)
            .add(down.scale(BombingPolicy.INITIAL_DROP_SPEED_BLOCKS_PER_TICK));

        ImpactTntEntity bomb = new ImpactTntEntity(plane.level(), owner, plane,
            position, velocity);
        plane.level().addFreshEntity(bomb);
        plane.level().playSound(null, position.x, position.y, position.z,
            SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    private Vec3 getWorldPoint(double x, double y, double z) {
        double pivot = BombingPolicy.AIRFRAME_ROTATION_PIVOT_HEIGHT;
        Vector3f transformed = plane.transformPos(new Vector3f((float) x,
            (float) (y - pivot), (float) z));
        return plane.position().add(transformed.x(),
            transformed.y() + pivot, transformed.z());
    }

    @Nullable
    private LivingEntity resolvePendingOwner() {
        if (pendingOwnerUuid == null ||
            !(plane.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(pendingOwnerUuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    private void clearPendingBomb() {
        secondBombReleaseGameTime = -1L;
        pendingOwnerUuid = null;
    }

    private static void playRejectedSound(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSoundPacket(
                SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                0.35F, 0.75F, player.getRandom().nextLong()));
        }
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) &&
            Double.isFinite(vector.z()) && Double.isFinite(vector.lengthSqr());
    }
}

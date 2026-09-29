package amrac.items;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import amrac.AmracEntities;
import amrac.entities.Mig29Entity;

public class Mig29Item extends Item {
    public Mig29Item(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        HitResult hit = getPlayerPOVHitResult(level, player,
            ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos pos = blockHit.getBlockPos().above();
        Mig29Entity plane = new Mig29Entity(AmracEntities.MIG29, level);
        plane.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        plane.setPlacedYaw(player.getYRot());
        if (!level.noCollision(plane, plane.getBoundingBox())) {
            return InteractionResult.FAIL;
        }

        level.addFreshEntity(plane);
        level.playSound(null, plane.getX(), plane.getY(), plane.getZ(),
            SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.CONSUME;
    }
}

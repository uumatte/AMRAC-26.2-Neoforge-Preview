package amrac.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import amrac.AmracEntities;
import amrac.entities.loader.MissileLoaderEntity;

public class MissileLoaderItem extends Item {
    public MissileLoaderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockPos spawn = context.getClickedFace() == Direction.UP
            ? clicked.above() : clicked.relative(context.getClickedFace());

        MissileLoaderEntity truck = AmracEntities.MISSILE_LOADER.create(
            server, EntitySpawnReason.SPAWN_ITEM_USE);
        if (truck == null) {
            return InteractionResult.FAIL;
        }
        truck.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            context.getHorizontalDirection().toYRot(), 0.0F);
        truck.rememberHome();
        server.addFreshEntity(truck);
        server.playSound(null, spawn, SoundEvents.IRON_DOOR_CLOSE,
            SoundSource.NEUTRAL, 0.7F, 0.8F);

        ItemStack stack = context.getItemInHand();
        if (context.getPlayer() != null
                && !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}

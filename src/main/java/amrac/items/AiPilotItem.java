package amrac.items;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import amrac.AmracEntities;
import amrac.entities.ai.AiPilotEntity;
import amrac.entities.ai.AiPilotVariant;

public class AiPilotItem extends Item {
    private final AiPilotVariant variant;

    public AiPilotItem(Properties properties, AiPilotVariant variant) {
        super(properties);
        this.variant = variant;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        BlockPos spawn = context.getClickedFace() == Direction.UP
            ? pos.above() : pos.relative(context.getClickedFace());

        AiPilotEntity pilot = AmracEntities.AI_PILOT.create(server,
            EntitySpawnReason.SPAWN_ITEM_USE);
        if (pilot == null) {
            return InteractionResult.FAIL;
        }
        pilot.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            context.getHorizontalDirection().toYRot(), 0.0F);
        pilot.finalizeSpawn(server, server.getCurrentDifficultyAt(spawn),
            EntitySpawnReason.SPAWN_ITEM_USE, null);
        pilot.setVariant(variant);
        server.addFreshEntity(pilot);

        ItemStack stack = context.getItemInHand();
        if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}

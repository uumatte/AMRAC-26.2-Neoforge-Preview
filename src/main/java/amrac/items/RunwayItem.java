package amrac.items;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import amrac.AmracMod;
import amrac.runway.RunwayBuilder;
import amrac.runway.RunwayPolicy;
import amrac.structure.StructurePreview;

public class RunwayItem extends Item {
    public RunwayItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        if (RunwayBuilder.busy(player.getUUID())) {
            AmracMod.sendOverlay(player, Component.translatable(
                    "amrac.message.runway_busy")
                .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        BlockPos origin = context.getClickedPos();
        Direction facing = context.getHorizontalDirection();

        Direction confirmed = StructurePreview.armOrConfirm(player, origin,
            facing, StructurePreview.Kind.RUNWAY, RunwayPolicy.LENGTH,
            RunwayPolicy.HALF_WIDTH, 1 + RunwayPolicy.CLEARANCE);
        if (confirmed == null) {
            AmracMod.sendOverlay(player, Component.translatable(
                    "amrac.message.structure_preview")
                .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        RunwayBuilder.start(level, player, origin, confirmed);
        return InteractionResult.CONSUME;
    }
}

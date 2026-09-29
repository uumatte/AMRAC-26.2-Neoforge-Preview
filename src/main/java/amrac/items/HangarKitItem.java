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
import amrac.hangar.HangarBuilder;
import amrac.hangar.HangarPolicy;
import amrac.structure.StructurePreview;

public class HangarKitItem extends Item {
    public HangarKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        if (HangarBuilder.busy(player.getUUID())) {
            AmracMod.sendOverlay(player, Component.translatable(
                    "amrac.message.hangar_busy")
                .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        BlockPos origin = context.getClickedPos();
        Direction facing = context.getHorizontalDirection();

        Direction confirmed = StructurePreview.armOrConfirm(player, origin,
            facing, StructurePreview.Kind.HANGAR, HangarPolicy.TOTAL_LENGTH,
            HangarPolicy.HALF_WIDTH, HangarPolicy.TOTAL_HEIGHT);
        if (confirmed == null) {
            AmracMod.sendOverlay(player, Component.translatable(
                    "amrac.message.structure_preview")
                .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }

        HangarBuilder.start(level, player, origin, confirmed);
        return InteractionResult.CONSUME;
    }
}

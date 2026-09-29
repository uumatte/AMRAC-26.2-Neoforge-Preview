package amrac.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import amrac.entities.ai.AiCommandLaunchService;

public class AiCommandBlock extends BaseEntityBlock implements GameMasterBlock {
    public static final MapCodec<AiCommandBlock> CODEC =
        simpleCodec(AiCommandBlock::new);

    public static final EnumProperty<Direction> FACING =
        HorizontalDirectionalBlock.FACING;

    public AiCommandBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AiCommandBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
            context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof AiCommandBlockEntity block) {
            block.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block neighbour, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        if (level.isClientSide()
                || !(level.getBlockEntity(pos) instanceof AiCommandBlockEntity block)) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == block.isPowered()) {
            return;
        }
        block.setPowered(powered);
        if (powered) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos,
                        RandomSource random) {
        if (level.getBlockEntity(pos) instanceof AiCommandBlockEntity block) {
            AiCommandLaunchService.trigger(level, pos, state.getValue(FACING),
                block);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.canUseGameMasterBlocks()) {
            if (!level.isClientSide()) {
                amrac.AmracMod.sendOverlay(player,
                    Component.translatable("advMode.notAllowed"), true);
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof AiCommandBlockEntity) {
            amrac.network.AiCommandNetworking.open(serverPlayer, pos,
                AiCommandLaunchService.catalog(serverLevel),
                serverLevel.getScoreboard().getTeamNames().stream().sorted()
                    .toList());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level,
                                        BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof AiCommandBlockEntity block
            && block.lastSucceeded() ? 15 : 0;
    }
}

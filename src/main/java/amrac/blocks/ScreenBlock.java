package amrac.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import amrac.display.DisplayPolicy;

public class ScreenBlock extends BaseEntityBlock {
    public static final MapCodec<ScreenBlock> CODEC = simpleCodec(ScreenBlock::new);

    public static final EnumProperty<Direction> FACING =
        BlockStateProperties.FACING;
    public static final BooleanProperty CORE =
        BooleanProperty.create("core");

    public static final double THICKNESS = 2.0D;

    public ScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(CORE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORE);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScreenBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        double t = THICKNESS;
        return switch (facing) {
            case NORTH -> Block.box(0, 0, 16 - t, 16, 16, 16);
            case SOUTH -> Block.box(0, 0, 0, 16, 16, t);
            case WEST -> Block.box(16 - t, 0, 0, 16, 16, 16);
            case EAST -> Block.box(0, 0, 0, t, 16, 16);
            case UP -> Block.box(0, 0, 0, 16, t, 16);
            case DOWN -> Block.box(0, 16 - t, 0, 16, 16, 16);
        };
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockPos centre = context.getClickedPos();
        Level level = context.getLevel();
        int[] basis = new int[6];
        DisplayPolicy.screenBasis(face.getStepX(), face.getStepY(),
            face.getStepZ(), basis);
        int[] offset = new int[3];
        for (int across = -DisplayPolicy.SCREEN_HALF;
                across <= DisplayPolicy.SCREEN_HALF; across++) {
            for (int up = -DisplayPolicy.SCREEN_HALF;
                    up <= DisplayPolicy.SCREEN_HALF; up++) {
                DisplayPolicy.screenOffset(basis, across, up, offset);
                BlockPos square = centre.offset(offset[0], offset[1], offset[2]);
                if (!level.getBlockState(square).canBeReplaced(context)
                        || !level.getWorldBorder().isWithinBounds(square)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, face).setValue(CORE, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        Direction face = state.getValue(FACING);
        int[] basis = new int[6];
        DisplayPolicy.screenBasis(face.getStepX(), face.getStepY(),
            face.getStepZ(), basis);
        int[] offset = new int[3];
        for (int across = -DisplayPolicy.SCREEN_HALF;
                across <= DisplayPolicy.SCREEN_HALF; across++) {
            for (int up = -DisplayPolicy.SCREEN_HALF;
                    up <= DisplayPolicy.SCREEN_HALF; up++) {
                DisplayPolicy.screenOffset(basis, across, up, offset);
                BlockPos square = pos.offset(offset[0], offset[1], offset[2]);
                if (!square.equals(pos)) {
                    level.setBlock(square, state.setValue(CORE, false),
                        Block.UPDATE_ALL);
                }
                if (level.getBlockEntity(square) instanceof ScreenBlockEntity panel) {
                    panel.setCorePos(pos);
                }
            }
        }
    }

    /**
     * Removing a multiblock (screen, console) reads the core position from a surviving block (the
     * broken block's entity is already gone) and guards against recursion; keep both if the
     * structure size changes.
     */
    public static void removePanel(Level level, BlockPos gone, BlockState state) {
        if (level.isClientSide() || removing) {
            return;
        }
        Direction face = state.getValue(FACING);
        BlockPos core = findCore(level, gone, face);
        if (core == null) {
            return;
        }
        int[] basis = new int[6];
        DisplayPolicy.screenBasis(face.getStepX(), face.getStepY(),
            face.getStepZ(), basis);
        int[] offset = new int[3];
        removing = true;
        try {
            for (int across = -DisplayPolicy.SCREEN_HALF;
                    across <= DisplayPolicy.SCREEN_HALF; across++) {
                for (int up = -DisplayPolicy.SCREEN_HALF;
                        up <= DisplayPolicy.SCREEN_HALF; up++) {
                    DisplayPolicy.screenOffset(basis, across, up, offset);
                    BlockPos square = core.offset(offset[0], offset[1], offset[2]);
                    if (level.getBlockState(square).getBlock() instanceof ScreenBlock) {
                        level.setBlock(square, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL);
                    }
                }
            }
        } finally {
            removing = false;
        }
    }

    @Nullable
    private static BlockPos findCore(Level level, BlockPos gone, Direction face) {
        int[] basis = new int[6];
        DisplayPolicy.screenBasis(face.getStepX(), face.getStepY(),
            face.getStepZ(), basis);
        int[] offset = new int[3];
        for (int across = -DisplayPolicy.SCREEN_SIZE;
                across <= DisplayPolicy.SCREEN_SIZE; across++) {
            for (int up = -DisplayPolicy.SCREEN_SIZE;
                    up <= DisplayPolicy.SCREEN_SIZE; up++) {
                DisplayPolicy.screenOffset(basis, across, up, offset);
                BlockPos square = gone.offset(offset[0], offset[1], offset[2]);
                if (level.getBlockEntity(square) instanceof ScreenBlockEntity panel
                        && level.getBlockState(panel.corePos()).getBlock()
                            instanceof ScreenBlock) {
                    return panel.corePos();
                }
            }
        }
        return null;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state,
                                               ServerLevel level, BlockPos pos,
                                               boolean moved) {
        removePanel(level, pos, state);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    private static boolean removing;
}

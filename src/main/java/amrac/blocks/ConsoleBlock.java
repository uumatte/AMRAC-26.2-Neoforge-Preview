package amrac.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import amrac.AmracBlockEntities;
import amrac.AmracMod;
import amrac.display.DisplayPolicy;

public class ConsoleBlock extends BaseEntityBlock {
    public static final MapCodec<ConsoleBlock> CODEC = simpleCodec(ConsoleBlock::new);

    public static final EnumProperty<Direction> FACING =
        HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final BooleanProperty CORE = BooleanProperty.create("core");
    public static final BooleanProperty FRONT = BooleanProperty.create("front");

    public ConsoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(UPPER, false)
            .setValue(CORE, false)
            .setValue(FRONT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, UPPER, CORE, FRONT);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConsoleBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static final double[][] NORTH_SHAPES = {
        {0, 0, 0, 16, 12.5, 16},
        {0, 0, 0, 16, 16, 16},
        {0, 0, 13, 16, 8, 16},
        {0, 0, 0, 16, 15, 16},
    };

    private static final VoxelShape[][] SHAPES = new VoxelShape[4][4];

    static {
        for (Direction facing : new Direction[] {Direction.NORTH,
                Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            for (int part = 0; part < NORTH_SHAPES.length; part++) {
                double[] b = NORTH_SHAPES[part];
                double x0 = b[0];
                double z0 = b[2];
                double x1 = b[3];
                double z1 = b[5];
                double[] turned = switch (facing) {
                    case EAST -> new double[] {16 - z1, 16 - z0, x0, x1};
                    case SOUTH -> new double[] {16 - x1, 16 - x0, 16 - z1, 16 - z0};
                    case WEST -> new double[] {z0, z1, 16 - x1, 16 - x0};
                    default -> new double[] {x0, x1, z0, z1};
                };
                SHAPES[facing.get2DDataValue()][part] = Block.box(turned[0],
                    b[1], turned[2], turned[1], b[4], turned[3]);
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        boolean front = state.getValue(FRONT) || state.getValue(CORE);
        int part = (state.getValue(UPPER) ? 2 : 0) + (front ? 0 : 1);
        return SHAPES[state.getValue(FACING).get2DDataValue()][part];
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos anchor = context.getClickedPos();
        int[] offset = new int[3];
        for (int along = -1; along <= 1; along++) {
            for (int deep = 0; deep < DisplayPolicy.CONSOLE_WIDTH; deep++) {
                for (int up = 0; up < DisplayPolicy.CONSOLE_HEIGHT; up++) {
                    DisplayPolicy.consoleOffset(facing.getStepX(),
                        facing.getStepZ(), along, deep, up, offset);
                    BlockPos block = anchor.offset(offset[0], offset[1], offset[2]);
                    if (!level.getBlockState(block).canBeReplaced(context)
                            || !level.getWorldBorder().isWithinBounds(block)) {
                        return null;
                    }
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing)
            .setValue(UPPER, false).setValue(CORE, true).setValue(FRONT, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        int[] offset = new int[3];
        for (int along = -1; along <= 1; along++) {
            for (int deep = 0; deep < DisplayPolicy.CONSOLE_WIDTH; deep++) {
                for (int up = 0; up < DisplayPolicy.CONSOLE_HEIGHT; up++) {
                    DisplayPolicy.consoleOffset(facing.getStepX(),
                        facing.getStepZ(), along, deep, up, offset);
                    BlockPos block = pos.offset(offset[0], offset[1], offset[2]);
                    if (!block.equals(pos)) {
                        level.setBlock(block, state.setValue(CORE, false)
                            .setValue(UPPER, up == 1).setValue(FRONT, deep == 0),
                            Block.UPDATE_ALL);
                    }
                    if (level.getBlockEntity(block) instanceof ConsoleBlockEntity desk) {
                        desk.setCorePos(pos);
                    }
                }
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ConsoleBlockEntity part)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(part.corePos()) instanceof ConsoleBlockEntity desk)) {
            return InteractionResult.PASS;
        }
        amrac.display.ConsoleService.refreshNames(desk);
        player.openMenu(desk);
        return InteractionResult.CONSUME;
    }

    public static void removeDesk(Level level, BlockPos gone, BlockState state) {
        if (level.isClientSide() || removing) {
            return;
        }
        Direction facing = state.getValue(FACING);
        BlockPos core = findCore(level, gone, facing);
        if (core == null) {
            return;
        }
        int[] offset = new int[3];
        removing = true;
        try {
            for (int along = -1; along <= 1; along++) {
                for (int deep = 0; deep < DisplayPolicy.CONSOLE_WIDTH; deep++) {
                    for (int up = 0; up < DisplayPolicy.CONSOLE_HEIGHT; up++) {
                        DisplayPolicy.consoleOffset(facing.getStepX(),
                            facing.getStepZ(), along, deep, up, offset);
                        BlockPos block = core.offset(offset[0], offset[1], offset[2]);
                        if (level.getBlockState(block).getBlock() instanceof ConsoleBlock) {
                            level.setBlock(block, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL);
                        }
                    }
                }
            }
        } finally {
            removing = false;
        }
    }

    @Nullable
    private static BlockPos findCore(Level level, BlockPos gone, Direction facing) {
        int[] offset = new int[3];
        for (int along = -DisplayPolicy.CONSOLE_LENGTH;
                along <= DisplayPolicy.CONSOLE_LENGTH; along++) {
            for (int deep = -DisplayPolicy.CONSOLE_WIDTH;
                    deep <= DisplayPolicy.CONSOLE_WIDTH; deep++) {
                for (int up = -DisplayPolicy.CONSOLE_HEIGHT;
                        up <= DisplayPolicy.CONSOLE_HEIGHT; up++) {
                    DisplayPolicy.consoleOffset(facing.getStepX(),
                        facing.getStepZ(), along, deep, up, offset);
                    BlockPos block = gone.offset(offset[0], offset[1], offset[2]);
                    if (level.getBlockEntity(block) instanceof ConsoleBlockEntity desk
                            && level.getBlockState(desk.corePos()).getBlock()
                                instanceof ConsoleBlock) {
                        return desk.corePos();
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state,
                                               ServerLevel level, BlockPos pos,
                                               boolean moved) {
        removeDesk(level, pos, state);
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    @Nullable
    public <T extends BlockEntity>
            net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, AmracBlockEntities.CONSOLE,
            ConsoleBlockEntity::serverTick);
    }

    private static boolean removing;
}

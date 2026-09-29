package amrac.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import amrac.AmracBlocks;
import amrac.AmracItems;
import amrac.AmracMod;
import amrac.items.MissileItem;

public class MissileRackBlock extends HorizontalDirectionalBlock
        implements EntityBlock {
    public static final MapCodec<MissileRackBlock> CODEC =
        simpleCodec(MissileRackBlock::new);

    public static final EnumProperty<RackPart> PART =
        EnumProperty.create("part", RackPart.class);

    private static final VoxelShape SHAPE = Shapes.or(
        Shapes.box(0.0D, 0.5D, 0.125D, 1.0D, 0.6875D, 0.875D),
        Shapes.box(0.0D, 0.0D, 0.125D, 1.0D, 0.5D, 0.3125D),
        Shapes.box(0.0D, 0.0D, 0.6875D, 1.0D, 0.5D, 0.875D));

    public MissileRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
            .setValue(FACING, Direction.NORTH)
            .setValue(PART, RackPart.LEFT));
    }

    @Override
    public MapCodec<MissileRackBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static Direction along(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    public static BlockPos anchor(BlockState state, BlockPos pos) {
        return pos.relative(along(state).getOpposite(), state.getValue(PART).index());
    }

    public static BlockPos leftHalf(BlockState state, BlockPos pos) {
        return anchor(state, pos);
    }

    @Nullable
    public static MissileRackBlockEntity rackAt(BlockGetter level,
                                                BlockState state, BlockPos pos) {
        if (!state.is(AmracBlocks.MISSILE_RACK)) {
            return null;
        }
        return level.getBlockEntity(leftHalf(state, pos))
            instanceof MissileRackBlockEntity rack ? rack : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        for (int i = 1; i < RackPart.LENGTH; i++) {
            BlockPos next = context.getClickedPos().relative(facing.getClockWise(), i);
            if (!level.getBlockState(next).canBeReplaced(context)
                    || !level.getWorldBorder().isWithinBounds(next)) {
                return null;
            }
        }
        return defaultBlockState()
            .setValue(FACING, facing)
            .setValue(PART, RackPart.LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        for (int i = 1; i < RackPart.LENGTH; i++) {
            level.setBlock(pos.relative(along(state), i),
                state.setValue(PART, RackPart.at(i)), Block.UPDATE_ALL);
        }
        level.updateNeighborsAt(pos, Blocks.AIR);
        state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
                                     ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos,
                                     BlockState neighbourState,
                                     RandomSource random) {
        int index = state.getValue(PART).index();
        int expected = direction == along(state) ? index + 1
            : direction == along(state).getOpposite() ? index - 1 : -1;
        if (expected >= 0 && expected < RackPart.LENGTH) {
            return neighbourState.is(this)
                && neighbourState.getValue(PART).index() == expected
                ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction,
            neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos,
                                        BlockState state, Player player) {
        if (!level.isClientSide() && !player.getAbilities().instabuild) {
            Block.popResource(level, pos,
                new ItemStack(AmracItems.MISSILE_RACK));
        }
        if (!level.isClientSide()) {
            BlockPos anchor = anchor(state, pos);
            for (int i = 0; i < RackPart.LENGTH; i++) {
                BlockPos other = anchor.relative(along(state), i);
                if (other.equals(pos)) {
                    continue;
                }
                BlockState otherState = level.getBlockState(other);
                if (otherState.is(this)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, other, Block.getId(otherState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state,
                                          Level level, BlockPos pos,
                                          Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!(stack.getItem() instanceof MissileItem missile)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        MissileRackBlockEntity rack = rackAt(level, state, pos);
        if (rack == null) {
            return InteractionResult.CONSUME;
        }
        if (!rack.isEmpty()) {
            AmracMod.sendOverlay(player, Component.translatable(
                "amrac.message.rack_occupied")
                .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        rack.setProfileId(missile.profileId());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D,
            pos.getZ() + 0.5D, SoundEvents.ARMOR_EQUIP_IRON,
            SoundSource.BLOCKS, 0.7F, 1.2F);
        AmracMod.sendOverlay(player, Component.translatable(
            "amrac.message.rack_mounted",
            AmracItems.missileName(missile.profileId())), true);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Client and server must return the same result (whether the rack is empty comes from the block
     * entity's update tag); SUCCESS on one side and PASS on the other places a ghost block.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        MissileRackBlockEntity rack = rackAt(level, state, pos);
        if (rack == null || rack.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack round = rack.mountedItem();
        rack.setProfileId("");
        if (!round.isEmpty() && !player.getInventory().add(round)) {
            Block.popResource(level, pos, round);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP,
            SoundSource.BLOCKS, 0.6F, 1.0F);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == RackPart.LEFT
            ? new MissileRackBlockEntity(pos, state) : null;
    }
}

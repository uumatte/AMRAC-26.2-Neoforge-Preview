package amrac.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import amrac.AmracBlockEntities;
import amrac.display.ScreenContent;

public class ScreenBlockEntity extends BlockEntity {
    private BlockPos corePos;

    @Nullable
    private BlockPos consolePos;

    private final ScreenContent content = new ScreenContent();

    public ScreenBlockEntity(BlockPos pos, BlockState state) {
        super(AmracBlockEntities.SCREEN, pos, state);
        corePos = pos;
    }

    public BlockPos corePos() {
        return corePos;
    }

    public void setCorePos(BlockPos corePos) {
        this.corePos = corePos.immutable();
        setChanged();
    }

    public boolean isCore() {
        return corePos.equals(getBlockPos());
    }

    public ScreenContent content() {
        return content;
    }

    public void contentChanged() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(),
                getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Nullable
    public BlockPos consolePos() {
        return consolePos;
    }

    public void setConsolePos(@Nullable BlockPos consolePos) {
        this.consolePos = consolePos == null ? null : consolePos.immutable();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(),
                getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        corePos = new BlockPos(
            input.getIntOr("CoreX", getBlockPos().getX()),
            input.getIntOr("CoreY", getBlockPos().getY()),
            input.getIntOr("CoreZ", getBlockPos().getZ()));
        content.load(input);
        consolePos = input.getBooleanOr("HasConsole", false)
            ? new BlockPos(input.getIntOr("ConsoleX", 0),
                input.getIntOr("ConsoleY", 0), input.getIntOr("ConsoleZ", 0))
            : null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("CoreX", corePos.getX());
        output.putInt("CoreY", corePos.getY());
        output.putInt("CoreZ", corePos.getZ());
        content.save(output);
        output.putBoolean("HasConsole", consolePos != null);
        if (consolePos != null) {
            output.putInt("ConsoleX", consolePos.getX());
            output.putInt("ConsoleY", consolePos.getY());
            output.putInt("ConsoleZ", consolePos.getZ());
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}

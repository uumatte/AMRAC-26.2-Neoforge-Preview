package amrac.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import amrac.AmracBlockEntities;
import amrac.entities.ai.AiLaunchOrder;
import amrac.entities.ai.AiPilotRank;

public class AiCommandBlockEntity extends BlockEntity {
    private AiLaunchOrder order = AiLaunchOrder.DEFAULT;

    private boolean powered;

    private Component lastOutput = Component.empty();
    private String lastTime = "";
    private boolean lastSucceeded;

    public AiCommandBlockEntity(BlockPos pos, BlockState state) {
        super(AmracBlockEntities.AI_COMMAND_BLOCK, pos, state);
    }

    public AiLaunchOrder order() {
        return order;
    }

    public void setOrder(AiLaunchOrder order) {
        this.order = order == null ? AiLaunchOrder.DEFAULT : order;
        sync();
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
        setChanged();
    }

    public Component lastOutput() {
        return lastOutput;
    }

    public String lastTime() {
        return lastTime;
    }

    public boolean lastSucceeded() {
        return lastSucceeded;
    }

    public void report(Component message, boolean success) {
        lastOutput = message == null ? Component.empty() : message;
        lastSucceeded = success;
        lastTime = new java.text.SimpleDateFormat("HH:mm:ss")
            .format(new java.util.Date());
        sync();
        if (level != null) {
            level.updateNeighbourForOutputSignal(getBlockPos(),
                getBlockState().getBlock());
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(),
                getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        AiLaunchOrder shipped = AiLaunchOrder.DEFAULT;
        order = new AiLaunchOrder(
            input.getStringOr("Aircraft", shipped.aircraft()),
            AiPilotRank.bySavedName(input.getStringOr("Rank",
                shipped.rank().name())),
            input.getStringOr("Team", shipped.team()),
            input.getIntOr("Fuel", shipped.fuelPercent()),
            input.getStringOr("Loadout", shipped.loadout()),
            input.getStringOr("Position", shipped.position()),
            input.getFloatOr("Heading", shipped.heading()));
        powered = input.getBooleanOr("Powered", false);
        lastOutput = input.read("LastOutput", ComponentSerialization.CODEC)
            .orElse(Component.empty());
        lastTime = input.getStringOr("LastTime", "");
        lastSucceeded = input.getBooleanOr("LastSucceeded", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("Aircraft", order.aircraft());
        output.putString("Rank", order.rank().name());
        output.putString("Team", order.team());
        output.putInt("Fuel", order.fuelPercent());
        output.putString("Loadout", order.loadout());
        output.putString("Position", order.position());
        output.putFloat("Heading", order.heading());
        output.putBoolean("Powered", powered);
        output.store("LastOutput", ComponentSerialization.CODEC, lastOutput);
        output.putString("LastTime", lastTime);
        output.putBoolean("LastSucceeded", lastSucceeded);
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

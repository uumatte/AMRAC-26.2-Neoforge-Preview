package amrac.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import amrac.AmracBlockEntities;
import amrac.AmracItems;

public class MissileRackBlockEntity extends BlockEntity {
    private String profileId = "";

    public MissileRackBlockEntity(BlockPos pos, BlockState state) {
        super(AmracBlockEntities.MISSILE_RACK, pos, state);
    }

    public String profileId() {
        return profileId;
    }

    public boolean isEmpty() {
        return profileId.isEmpty();
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId == null ? "" : profileId;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(),
                getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    public ItemStack mountedItem() {
        if (profileId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item item = AmracItems.missileItem(profileId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Rack and bowser contents drop only here; the block loot table is off. The two are a pair:
     * enable the loot table or move this drop and items duplicate or vanish.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        ItemStack round = mountedItem();
        if (level != null && !round.isEmpty()) {
            profileId = "";
            Block.popResource(level, pos, round);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        profileId = input.getStringOr("Missile", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("Missile", profileId);
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

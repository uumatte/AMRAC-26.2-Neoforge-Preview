package amrac.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

import amrac.AmracBlockEntities;
import amrac.display.ChartKind;
import amrac.display.ConsoleMode;
import amrac.display.DisplayPolicy;

public class ConsoleBlockEntity extends BlockEntity
        implements net.minecraft.world.MenuProvider {
    public static final int SLOT_COUNT = 1;

    private BlockPos corePos;
    private final SimpleContainer slot = new SimpleContainer(SLOT_COUNT);
    private ConsoleMode mode = ConsoleMode.MAP;

    @Nullable
    private BlockPos screenPos;

    private List<String> parameterNames = List.of();

    private String parameterName = "";
    private String parameterPath = "";
    private String parameterValue = "";

    private String status = "";

    private ChartKind chartKind = ChartKind.TURN_RATE_VS_SPEED;
    private double chartAltitude = 1000.0D;
    private double chartTargetSpeed = 250.0D;

    public ConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(AmracBlockEntities.CONSOLE, pos, state);
        corePos = pos;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ConsoleBlockEntity desk) {
        if (!desk.isCore()
                || !(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        desk.noticeSlotChange();
        if (!amrac.display.GpsMirror.mirroring(desk)) {
            return;
        }
        if (level.getGameTime() % amrac.display.GpsMirror
                .REFRESH_TICKS != 0L) {
            return;
        }
        BlockPos screen = desk.screenPos();
        if (screen == null
                || !(level.getBlockEntity(screen) instanceof ScreenBlockEntity panel)) {
            return;
        }
        amrac.display.GpsMirror.refresh(server, desk, panel);
    }

    private net.minecraft.world.item.ItemStack lastSlotItem =
        net.minecraft.world.item.ItemStack.EMPTY;

    private void noticeSlotChange() {
        net.minecraft.world.item.ItemStack now = slot.getItem(0);
        if (net.minecraft.world.item.ItemStack.isSameItem(now, lastSlotItem)) {
            return;
        }
        lastSlotItem = now.copy();
        amrac.display.ConsoleService.slotChanged(this);
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

    public SimpleContainer slot() {
        return slot;
    }

    public List<String> parameterNames() {
        return parameterNames;
    }

    public void setParameterNames(List<String> names) {
        parameterNames = List.copyOf(names);
        sync();
    }

    public String parameterName() {
        return parameterName;
    }

    public String parameterPath() {
        return parameterPath;
    }

    public String parameterValue() {
        return parameterValue;
    }

    public void setParameter(String name, String path, String value) {
        parameterName = name == null ? "" : name;
        parameterPath = path == null ? "" : path;
        parameterValue = value == null ? "" : value;
        sync();
    }

    public String status() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
        sync();
    }

    public ChartKind chartKind() {
        return chartKind;
    }

    public void setChartKind(ChartKind kind) {
        chartKind = kind;
        sync();
    }

    public double chartAltitude() {
        return chartAltitude;
    }

    public double chartTargetSpeed() {
        return chartTargetSpeed;
    }

    public void setChartInputs(double altitude, double targetSpeed) {
        chartAltitude = altitude;
        chartTargetSpeed = targetSpeed;
        sync();
    }

    public ConsoleMode mode() {
        return mode;
    }

    public void setMode(ConsoleMode mode) {
        this.mode = mode;
        sync();
    }

    @Nullable
    public BlockPos screenPos() {
        return screenPos;
    }

    public void connect(@Nullable BlockPos screen) {
        if (level != null && screenPos != null
                && level.getBlockEntity(screenPos) instanceof ScreenBlockEntity old) {
            old.setConsolePos(null);
        }
        screenPos = screen == null ? null : screen.immutable();
        if (level != null && screenPos != null
                && level.getBlockEntity(screenPos) instanceof ScreenBlockEntity panel) {
            panel.setConsolePos(corePos);
        }
        sync();
    }

    @Nullable
    public BlockPos findNearestScreen(Level level) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int reach = DisplayPolicy.CONNECT_RANGE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                corePos.offset(-reach, -reach, -reach),
                corePos.offset(reach, reach, reach))) {
            if (!(level.getBlockEntity(candidate) instanceof ScreenBlockEntity panel)
                    || !panel.isCore()) {
                continue;
            }
            double distance = candidate.distSqr(corePos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate.immutable();
            }
        }
        return best;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(
            "block.amrac.console");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new ConsoleMenu(containerId, inventory, slot, this);
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
        corePos = new BlockPos(
            input.getIntOr("CoreX", getBlockPos().getX()),
            input.getIntOr("CoreY", getBlockPos().getY()),
            input.getIntOr("CoreZ", getBlockPos().getZ()));
        mode = ConsoleMode.byName(input.getStringOr("Mode", ""));
        parameterName = input.getStringOr("ParamName", "");
        parameterPath = input.getStringOr("ParamPath", "");
        parameterValue = input.getStringOr("ParamValue", "");
        status = input.getStringOr("Status", "");
        chartKind = ChartKind.byName(input.getStringOr("Chart", ""), mode);
        chartAltitude = input.getDoubleOr("ChartAlt", 1000.0D);
        chartTargetSpeed = input.getDoubleOr("ChartTargetSpeed", 250.0D);
        int names = Math.max(0, Math.min(input.getIntOr("ParamCount", 0), 512));
        List<String> loaded = new ArrayList<>(names);
        for (int i = 0; i < names; i++) {
            loaded.add(input.getStringOr("Param" + i, ""));
        }
        parameterNames = List.copyOf(loaded);
        slot.clearContent();
        net.minecraft.world.ContainerHelper.loadAllItems(input, slot.getItems());
        screenPos = input.getBooleanOr("HasScreen", false)
            ? new BlockPos(input.getIntOr("ScreenX", 0),
                input.getIntOr("ScreenY", 0), input.getIntOr("ScreenZ", 0))
            : null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("CoreX", corePos.getX());
        output.putInt("CoreY", corePos.getY());
        output.putInt("CoreZ", corePos.getZ());
        output.putString("Mode", mode.getSerializedName());
        output.putString("ParamName", parameterName);
        output.putString("ParamPath", parameterPath);
        output.putString("ParamValue", parameterValue);
        output.putString("Status", status);
        output.putString("Chart", chartKind.getSerializedName());
        output.putDouble("ChartAlt", chartAltitude);
        output.putDouble("ChartTargetSpeed", chartTargetSpeed);
        output.putInt("ParamCount", parameterNames.size());
        for (int i = 0; i < parameterNames.size(); i++) {
            output.putString("Param" + i, parameterNames.get(i));
        }
        net.minecraft.world.ContainerHelper.saveAllItems(output, slot.getItems());
        output.putBoolean("HasScreen", screenPos != null);
        if (screenPos != null) {
            output.putInt("ScreenX", screenPos.getX());
            output.putInt("ScreenY", screenPos.getY());
            output.putInt("ScreenZ", screenPos.getZ());
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

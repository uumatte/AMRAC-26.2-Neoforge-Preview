package amrac.entities.ai;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import amrac.entities.PlaneEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public class AiPilotEntity extends PathfinderMob implements MenuProvider {
    public static final int INVENTORY_SIZE = 18;

    private static final EntityDataAccessor<Integer> VARIANT =
        SynchedEntityData.defineId(AiPilotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK =
        SynchedEntityData.defineId(AiPilotEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MISSION_ACTIVE =
        SynchedEntityData.defineId(AiPilotEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> TEAM =
        SynchedEntityData.defineId(AiPilotEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> CALLSIGN =
        SynchedEntityData.defineId(AiPilotEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> IDLE_REASON =
        SynchedEntityData.defineId(AiPilotEntity.class, EntityDataSerializers.INT);

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);

    public AiPilotEntity(EntityType<? extends AiPilotEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.32D)
            .add(Attributes.FOLLOW_RANGE,
                AiPilotBrain.BOARDING_SEARCH_RADIUS);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, AiPilotVariant.SKELETON.ordinal());
        builder.define(RANK, AiPilotRank.TRAINEE.ordinal());
        builder.define(MISSION_ACTIVE, false);
        builder.define(TEAM, "");
        builder.define(CALLSIGN, AiCallsignPolicy.UNASSIGNED);
        builder.define(IDLE_REASON, AiPilotIdleReason.NONE.ordinal());
    }

    public AiPilotIdleReason idleReason() {
        return AiPilotIdleReason.byOrdinal(entityData.get(IDLE_REASON));
    }

    public void setIdleReason(AiPilotIdleReason reason) {
        if (idleReason() != reason) entityData.set(IDLE_REASON, reason.ordinal());
    }

    public AiPilotVariant variant() {
        return AiPilotVariant.byOrdinal(entityData.get(VARIANT));
    }

    public void setVariant(AiPilotVariant variant) {
        entityData.set(VARIANT, variant.ordinal());
        if (!variant.allows(rank())) {
            setRank(variant.defaultRank());
        }
        AiPilotService.brainFor(getUUID()).reset();
    }

    public AiPilotRank rank() {
        AiPilotRank rank = AiPilotRank.byOrdinal(entityData.get(RANK));
        return variant().allows(rank) ? rank : variant().defaultRank();
    }

    public boolean setRank(AiPilotRank rank) {
        if (!variant().allows(rank)) {
            return false;
        }
        entityData.set(RANK, rank.ordinal());
        assignCallsign();
        AiPilotService.brainFor(getUUID()).resetTactics();
        return true;
    }

    public String callsign() {
        int number = entityData.get(CALLSIGN);
        if (!AiCallsignPolicy.assigned(number) && !level().isClientSide()) {
            assignCallsign();
            number = entityData.get(CALLSIGN);
        }
        return AiCallsignPolicy.format(rank(),
            AiCallsignPolicy.assigned(number) ? number : 0);
    }

    private void assignCallsign() {
        entityData.set(CALLSIGN,
            random.nextInt(AiCallsignPolicy.NUMBER_BOUND));
    }

    public boolean isMissionActive() {
        return entityData.get(MISSION_ACTIVE);
    }

    public void startMission() {
        entityData.set(MISSION_ACTIVE, true);
        AiPilotService.brainFor(getUUID()).reset();
    }

    public void stopMission() {
        entityData.set(MISSION_ACTIVE, false);
        AiPilotService.brainFor(getUUID()).reset();
    }

    public String teamName() {
        return entityData.get(TEAM);
    }

    public void setTeamName(String teamName) {
        String safe = teamName == null ? "" : teamName;
        if (!safe.isEmpty() && level().getScoreboard().getPlayerTeam(safe) == null) {
            return;
        }
        entityData.set(TEAM, safe);
        AiPilotService.brainFor(getUUID()).resetTactics();
    }

    public void cycleTeam(int direction) {
        java.util.List<String> teams = new java.util.ArrayList<>();
        teams.add("");
        teams.addAll(level().getScoreboard().getTeamNames().stream().sorted().toList());
        int current = teams.indexOf(teamName());
        if (current < 0) {
            current = 0;
        }
        int next = Math.floorMod(current + Integer.signum(direction), teams.size());
        setTeamName(teams.get(next));
    }

    public SimpleContainer pilotInventory() {
        return inventory;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide()) {
            player.openMenu(this);
        }
        return level().isClientSide()
            ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal(
            AiCallsignPolicy.displayName(callsign(), teamName()));
    }

    @Override
    public void die(DamageSource source) {
        boolean alreadyDead = this.dead;
        super.die(source);
        if (alreadyDead || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!Boolean.TRUE.equals(serverLevel.getGameRules().get(
                net.minecraft.world.level.gamerules.GameRules.SHOW_DEATH_MESSAGES))) {
            return;
        }
        serverLevel.getServer().getPlayerList().broadcastSystemMessage(
            source.getLocalizedDeathMessage(this), false);
        AiPilotRoster.of(serverLevel).forgetPilot(getUUID());
        AiPilotService.forget(getUUID());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId,
                                            Inventory playerInventory,
                                            Player player) {
        return new AiPilotMenu(containerId, playerInventory, inventory, this);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return vehicle instanceof PlaneEntity;
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier,
                                   DamageSource source) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        LivingEntity vehicle = getVehicle() instanceof LivingEntity living
            ? living : null;
        return vehicle != null && source.getEntity() == vehicle
            || super.isInvulnerableTo(level, source);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source,
                                       boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (net.minecraft.world.item.ItemStack stack : inventory.removeAllItems()) {
            spawnAtLocation(level, stack);
        }
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Variant", variant().name());
        output.putString("Rank", rank().name());
        output.putBoolean("MissionActive", isMissionActive());
        output.putString("PilotTeam", teamName());
        output.putInt("Callsign", entityData.get(CALLSIGN));
        ContainerHelper.saveAllItems(output, inventory.getItems());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        AiPilotVariant loadedVariant;
        try {
            loadedVariant = AiPilotVariant.valueOf(input.getStringOr(
                "Variant", AiPilotVariant.SKELETON.name()));
        } catch (IllegalArgumentException ignored) {
            loadedVariant = AiPilotVariant.SKELETON;
        }
        AiPilotRank loadedRank = AiPilotRank.bySavedName(
            input.getStringOr("Rank", loadedVariant.defaultRank().name()));
        if (!loadedVariant.allows(loadedRank)) {
            loadedVariant = AiPilotVariant.of(loadedRank);
        }
        entityData.set(VARIANT, loadedVariant.ordinal());
        entityData.set(RANK, loadedRank.ordinal());
        entityData.set(CALLSIGN, input.getIntOr("Callsign",
            AiCallsignPolicy.UNASSIGNED));
        entityData.set(MISSION_ACTIVE, input.getBooleanOr("MissionActive", false));
        String loadedTeam = input.getStringOr("PilotTeam", "");
        entityData.set(TEAM, loadedTeam);
        ContainerHelper.loadAllItems(input, inventory.getItems());
    }
}

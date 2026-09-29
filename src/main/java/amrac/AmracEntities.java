package amrac;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import amrac.entities.ai.AiPilotEntity;
import amrac.entities.loader.MissileLoaderEntity;
import amrac.entities.F15EEntity;
import amrac.entities.F15Entity;
import amrac.entities.F16Entity;
import amrac.entities.F4JEntity;
import amrac.entities.ImpactTntEntity;
import amrac.entities.MachineGunBulletEntity;
import amrac.entities.MissileEntity;
import amrac.entities.Mig21Entity;
import amrac.entities.J10cEntity;
import amrac.entities.J8IIEntity;
import amrac.entities.F18Entity;
import amrac.entities.Mig23Entity;
import amrac.entities.RafaleEntity;
import amrac.entities.Su30Entity;
import amrac.entities.Mig29Entity;
import amrac.entities.Su27Entity;
import amrac.entities.TyphoonEntity;
import amrac.network.PlaneSyncPolicy;

public final class AmracEntities {
    public static final EntityType<F16Entity> F16 = register("f16",
        EntityType.Builder.<F16Entity>of(F16Entity::new, MobCategory.MISC)
            .sized(6.0F, 3.0F)
            // In chunks, not blocks, and clamped by the server view distance; radar and distant-
            // image range both depend on it.
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<F15Entity> F15 = register("f15",
        EntityType.Builder.<F15Entity>of(F15Entity::new, MobCategory.MISC)
            .sized(7.5F, 3.4F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<F15EEntity> F15E = register("f15e",
        EntityType.Builder.<F15EEntity>of(F15EEntity::new, MobCategory.MISC)
            .sized(7.5F, 3.4F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<Su27Entity> SU27 = register("su27",
        EntityType.Builder.<Su27Entity>of(Su27Entity::new, MobCategory.MISC)
            .sized(6.5F, 3.0F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<Mig29Entity> MIG29 = register("mig29",
        EntityType.Builder.<Mig29Entity>of(Mig29Entity::new, MobCategory.MISC)
            .sized(5.6F, 2.8F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<J10cEntity> J10C = register("j10c",
        EntityType.Builder.<J10cEntity>of(J10cEntity::new, MobCategory.MISC)
            .sized(5.6F, 2.8F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<TyphoonEntity> TYPHOON = register("typhoon",
        EntityType.Builder.<TyphoonEntity>of(TyphoonEntity::new, MobCategory.MISC)
            .sized(5.6F, 2.8F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<Mig21Entity> MIG21 = register("mig21",
        EntityType.Builder.<Mig21Entity>of(Mig21Entity::new, MobCategory.MISC)
            .sized(5.6F, 2.8F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<J8IIEntity> J8II = register("j8ii",
        EntityType.Builder.<J8IIEntity>of(J8IIEntity::new, MobCategory.MISC)
            .sized(6.2F, 2.8F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<F18Entity> F18 = register("f18",
        EntityType.Builder.<F18Entity>of(F18Entity::new, MobCategory.MISC)
            .sized(5.6F, 2.9F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<Mig23Entity> MIG23 = register("mig23",
        EntityType.Builder.<Mig23Entity>of(Mig23Entity::new, MobCategory.MISC)
            .sized(5.4F, 2.8F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<RafaleEntity> RAFALE = register("rafale",
        EntityType.Builder.<RafaleEntity>of(RafaleEntity::new, MobCategory.MISC)
            .sized(5.0F, 2.7F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<Su30Entity> SU30 = register("su30",
        EntityType.Builder.<Su30Entity>of(Su30Entity::new, MobCategory.MISC)
            .sized(7.4F, 3.2F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<F4JEntity> F4J = register("f4j",
        EntityType.Builder.<F4JEntity>of(F4JEntity::new, MobCategory.MISC)
            .sized(5.4F, 2.8F)
            .clientTrackingRange(32)
            .updateInterval(PlaneSyncPolicy.ENTITY_UPDATE_INTERVAL_TICKS)
            );

    public static final EntityType<MachineGunBulletEntity> MACHINE_GUN_BULLET =
        register("machine_gun_bullet",
        EntityType.Builder.<MachineGunBulletEntity>of(MachineGunBulletEntity::new, MobCategory.MISC)
                .sized(0.12F, 0.12F)
                .clientTrackingRange(8)
                .updateInterval(1)
                );

    public static final EntityType<MissileEntity> MISSILE =
        register("missile",
        EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(88)
                .updateInterval(1)
                .fireImmune()
                );

    public static final EntityType<ImpactTntEntity> IMPACT_TNT =
        register("impact_tnt",
        EntityType.Builder.<ImpactTntEntity>of(ImpactTntEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(10)
                .fireImmune()
                );

    public static final EntityType<AiPilotEntity> AI_PILOT =
        register("ai_pilot",
        EntityType.Builder.<AiPilotEntity>of(AiPilotEntity::new, MobCategory.CREATURE)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(16)
                );

    public static final EntityType<MissileLoaderEntity> MISSILE_LOADER =
        register("missile_loader",
        EntityType.Builder.<MissileLoaderEntity>of(MissileLoaderEntity::new,
                MobCategory.MISC)
                .sized(1.6F, 1.5F)
                .clientTrackingRange(8)
                );

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
            String path, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(
            Registries.ENTITY_TYPE, AmracMod.id(path));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
            builder.build(key));
    }
    private AmracEntities() {
    }

    public static void register() {
    }
}

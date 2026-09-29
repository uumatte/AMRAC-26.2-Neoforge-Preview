package amrac;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class AmracDamage {
    public static final ResourceKey<DamageType> PLANE_COLLISION =
        key("plane_collision");
    public static final ResourceKey<DamageType> PLANE_CRASH_DEATH =
        key("plane_crash_death");
    public static final ResourceKey<DamageType> PLANE_SHOT_DOWN =
        key("plane_shot_down");
    public static final ResourceKey<DamageType> PLANE_MIDAIR_COLLISION =
        key("plane_midair_collision");

    private AmracDamage() {
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, AmracMod.id(path));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type,
                                      @Nullable Entity direct,
                                      @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type),
            direct, attacker);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type,
                                      @Nullable Entity direct) {
        return source(level, type, direct, direct);
    }

    public static DamageSource weaponSource(Level level,
                                            ResourceKey<DamageType> type,
                                            @Nullable Entity direct,
                                            @Nullable Entity attacker,
                                            Component weapon) {
        return weaponSource(level, type, direct, attacker, weapon, null);
    }

    public static DamageSource weaponSource(Level level,
                                            ResourceKey<DamageType> type,
                                            @Nullable Entity direct,
                                            @Nullable Entity attacker,
                                            Component weapon,
                                            @Nullable Component attackerName) {
        return new WeaponSource(level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type),
            direct, attacker, weapon, attackerName);
    }

    private static final class WeaponSource extends DamageSource {
        private final Component weapon;
        @Nullable private final Component attackerName;

        private WeaponSource(Holder<DamageType> type, @Nullable Entity direct,
                             @Nullable Entity attacker, Component weapon,
                             @Nullable Component attackerName) {
            super(type, direct, attacker);
            this.weapon = weapon;
            this.attackerName = attackerName;
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity victim) {
            if (attackerName != null) {
                return Component.translatable(
                    "death.attack." + type().msgId() + ".item",
                    victim.getDisplayName(), attackerName, weapon);
            }
            Entity attacker = getEntity() != null ? getEntity() : getDirectEntity();
            if (attacker == null) {
                return super.getLocalizedDeathMessage(victim);
            }
            return Component.translatable(
                "death.attack." + type().msgId() + ".item",
                victim.getDisplayName(), attacker.getDisplayName(), weapon);
        }
    }

    @Nullable
    public static Component weapon(DamageSource source) {
        return source instanceof WeaponSource weaponSource ? weaponSource.weapon : null;
    }

    @Nullable
    public static Component attackerName(DamageSource source) {
        return source instanceof WeaponSource weaponSource
            ? weaponSource.attackerName : null;
    }

    public static boolean is(DamageSource source, ResourceKey<DamageType> type) {
        return source.is(type);
    }
}

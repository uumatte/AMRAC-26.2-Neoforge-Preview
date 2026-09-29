package amrac;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import amrac.blocks.ConsoleMenu;
import amrac.blocks.RefuelerMenu;
import amrac.entities.ai.AiPilotMenu;
import amrac.entities.loader.MissileLoaderMenu;

public final class AmracMenus {
    public static final MenuType<AiPilotMenu> AI_PILOT = register("ai_pilot",
        new MenuType<>(AiPilotMenu::new, FeatureFlags.VANILLA_SET));

    public static final MenuType<RefuelerMenu> REFUELER = register("refueler",
        new MenuType<>(RefuelerMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<MissileLoaderMenu> MISSILE_LOADER =
        register("missile_loader",
            new MenuType<>(MissileLoaderMenu::new, FeatureFlags.VANILLA_SET));

    public static final MenuType<ConsoleMenu> CONSOLE = register("console",
        new MenuType<>(ConsoleMenu::new, FeatureFlags.VANILLA_SET));

    private AmracMenus() {
    }

    private static <T extends net.minecraft.world.inventory.AbstractContainerMenu>
            MenuType<T> register(String path, MenuType<T> type) {
        ResourceKey<MenuType<?>> key = ResourceKey.create(
            Registries.MENU, AmracMod.id(path));
        return Registry.register(BuiltInRegistries.MENU, key, type);
    }

    public static void register() {
    }
}

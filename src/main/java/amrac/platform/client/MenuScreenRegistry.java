package amrac.platform.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public final class MenuScreenRegistry {
    private MenuScreenRegistry() {
    }

    public static <M extends AbstractContainerMenu,
                   U extends Screen & MenuAccess<M>> void register(
        MenuType<? extends M> type,
        MenuScreens.ScreenConstructor<M, U> constructor) {
        ClientPlatform.backend().registerMenuScreen(type, constructor);
    }
}

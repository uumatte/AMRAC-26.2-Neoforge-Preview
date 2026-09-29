package amrac.platform.client;

import java.util.function.UnaryOperator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ClientPlatform {
    private static Backend backend;

    private ClientPlatform() {
    }

    public static void install(Backend loaderBackend) {
        if (backend != null) {
            throw new IllegalStateException(
                "Client platform backend already installed");
        }
        backend = loaderBackend;
    }

    static Backend backend() {
        if (backend == null) {
            throw new IllegalStateException(
                "No loader has installed the AMRAC client platform backend");
        }
        return backend;
    }

    public interface Backend {
        void sendToServer(CustomPacketPayload payload);

        <T extends CustomPacketPayload> void registerClientReceiver(
            CustomPacketPayload.Type<T> type,
            ClientPlayNetworking.PlayPayloadHandler<T> handler);

        void registerKeyMapping(KeyMapping mapping);

        void addHudElementLast(Identifier id, HudElement element);

        void replaceHudElement(VanillaHudElements element,
                               UnaryOperator<HudElement> replacer);

        <T extends Entity> void registerEntityRenderer(
            EntityType<? extends T> type, EntityRendererProvider<T> provider);

        <T extends BlockEntity, S extends BlockEntityRenderState>
        void registerBlockEntityRenderer(
            BlockEntityType<? extends T> type,
            BlockEntityRendererProvider<T, S> provider);

        <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>>
        void registerMenuScreen(MenuType<? extends M> type,
                                MenuScreens.ScreenConstructor<M, U> constructor);
    }
}

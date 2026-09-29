package amrac.platform.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class BlockEntityRendererRegistry {
    private BlockEntityRendererRegistry() {
    }

    public static <T extends BlockEntity, S extends BlockEntityRenderState>
    void register(BlockEntityType<? extends T> type,
                  BlockEntityRendererProvider<T, S> provider) {
        ClientPlatform.backend().registerBlockEntityRenderer(type, provider);
    }
}

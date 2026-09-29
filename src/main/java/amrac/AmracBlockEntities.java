package amrac;

import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;
import amrac.blocks.AiCommandBlockEntity;
import amrac.blocks.MissileRackBlockEntity;
import amrac.blocks.ConsoleBlockEntity;
import amrac.blocks.RefuelerBlockEntity;
import amrac.blocks.ScreenBlockEntity;

public final class AmracBlockEntities {
    public static final BlockEntityType<MissileRackBlockEntity> MISSILE_RACK =
        register("missile_rack", new BlockEntityType<>(
            MissileRackBlockEntity::new, Set.of(AmracBlocks.MISSILE_RACK)));

    public static final BlockEntityType<RefuelerBlockEntity> REFUELER =
        register("refueler", new BlockEntityType<>(
            RefuelerBlockEntity::new, Set.of(AmracBlocks.REFUELER)));

    public static final BlockEntityType<ScreenBlockEntity> SCREEN =
        register("screen", new BlockEntityType<>(
            ScreenBlockEntity::new, Set.of(AmracBlocks.SCREEN)));

    public static final BlockEntityType<ConsoleBlockEntity> CONSOLE =
        register("console", new BlockEntityType<>(
            ConsoleBlockEntity::new, Set.of(AmracBlocks.CONSOLE)));

    public static final BlockEntityType<AiCommandBlockEntity> AI_COMMAND_BLOCK =
        register("ai_command_block", new BlockEntityType<>(
            AiCommandBlockEntity::new, Set.of(AmracBlocks.AI_COMMAND_BLOCK)));

    private AmracBlockEntities() {
    }

    public static void register() {
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity>
            BlockEntityType<T> register(String path, BlockEntityType<T> type) {
        ResourceKey<BlockEntityType<?>> key = ResourceKey.create(
            Registries.BLOCK_ENTITY_TYPE, AmracMod.id(path));
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, type);
    }
}

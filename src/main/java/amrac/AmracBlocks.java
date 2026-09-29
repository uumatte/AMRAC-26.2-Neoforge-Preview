package amrac;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import amrac.blocks.AiCommandBlock;
import amrac.blocks.MissileRackBlock;
import amrac.blocks.ConsoleBlock;
import amrac.blocks.RefuelerBlock;
import amrac.blocks.ScreenBlock;

public final class AmracBlocks {
    public static final MissileRackBlock MISSILE_RACK = register("missile_rack",
        MissileRackBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F, 6.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable()
            .pushReaction(PushReaction.BLOCK));

    public static final RefuelerBlock REFUELER = register("refueler",
        RefuelerBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_YELLOW)
            .strength(3.0F, 9.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable()
            .pushReaction(PushReaction.BLOCK));

    public static final ScreenBlock SCREEN = register("screen",
        ScreenBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F, 3.0F)
            .sound(SoundType.GLASS)
            .lightLevel(state -> 11)
            .noOcclusion()
            .noLootTable()
            .pushReaction(PushReaction.BLOCK));

    public static final ConsoleBlock CONSOLE = register("console",
        ConsoleBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.5F, 6.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .noLootTable()
            .pushReaction(PushReaction.BLOCK));

    public static final AiCommandBlock AI_COMMAND_BLOCK = register(
        "ai_command_block", AiCommandBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .requiresCorrectToolForDrops()
            .strength(-1.0F, 3600000.0F)
            .noLootTable());

    private AmracBlocks() {
    }

    public static void register() {
    }

    private static <T extends Block> T register(
            String path, Function<BlockBehaviour.Properties, T> factory,
            BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(
            Registries.BLOCK, AmracMod.id(path));
        T block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }
}

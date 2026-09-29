package amrac;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import amrac.items.F15Item;
import amrac.items.F16Item;
import amrac.items.GpsItem;
import amrac.items.HangarKitItem;
import amrac.items.RunwayItem;
import amrac.items.F4JItem;
import amrac.items.J10cItem;
import amrac.items.J8IIItem;
import amrac.items.F15EItem;
import amrac.items.F18Item;
import amrac.items.Mig23Item;
import amrac.items.RafaleItem;
import amrac.items.Su30Item;
import amrac.items.Mig21Item;
import amrac.items.Mig29Item;
import amrac.items.MissileItem;
import amrac.items.MissileLoaderItem;
import amrac.items.Su27Item;
import amrac.items.TyphoonItem;
import amrac.items.AiPilotItem;
import amrac.entities.ai.AiPilotVariant;

public final class AmracItems {
    public static final Item F16 = register("f16",
        properties -> new F16Item(properties.stacksTo(1)));
    public static final Item BULLET = register("bullet", Item::new);

    public static final Item AVIATION_FUEL = register("aviation_fuel",
        properties -> new Item(properties.stacksTo(16)));

    public static final Item CHAFF = register("chaff", Item::new);
    public static final Item FLARE = register("flare", Item::new);
    public static final Item F15 = register("f15",
        properties -> new F15Item(properties.stacksTo(1)));
    public static final Item SU27 = register("su27",
        properties -> new Su27Item(properties.stacksTo(1)));
    public static final Item F4J = register("f4j",
        properties -> new F4JItem(properties.stacksTo(1)));
    public static final Item MIG29 = register("mig29",
        properties -> new Mig29Item(properties.stacksTo(1)));
    public static final Item J10C = register("j10c",
        properties -> new J10cItem(properties.stacksTo(1)));
    public static final Item TYPHOON = register("typhoon",
        properties -> new TyphoonItem(properties.stacksTo(1)));
    public static final Item J8II = register("j8ii",
        properties -> new J8IIItem(properties.stacksTo(1)));
    public static final Item F15E = register("f15e",
        properties -> new F15EItem(properties.stacksTo(1)));
    public static final Item F18 = register("f18",
        properties -> new F18Item(properties.stacksTo(1)));
    public static final Item MIG23 = register("mig23",
        properties -> new Mig23Item(properties.stacksTo(1)));
    public static final Item RAFALE = register("rafale",
        properties -> new RafaleItem(properties.stacksTo(1)));
    public static final Item SU30 = register("su30",
        properties -> new Su30Item(properties.stacksTo(1)));
    public static final Item MIG21 = register("mig21",
        properties -> new Mig21Item(properties.stacksTo(1)));

    public static final Item MISSILE_SPARROW = register("missile_sparrow",
        properties -> new MissileItem(properties.stacksTo(16), "AIM7"));
    public static final Item MISSILE_METEOR = register("missile_meteor",
        properties -> new MissileItem(properties.stacksTo(16), "METEOR"));
    public static final Item MISSILE_AIM120 = register("missile_aim120",
        properties -> new MissileItem(properties.stacksTo(16), "AIM120"));
    public static final Item MISSILE_AIM9 = register("missile_aim9",
        properties -> new MissileItem(properties.stacksTo(16), "AIM9"));
    public static final Item MISSILE_R27 = register("missile_r27",
        properties -> new MissileItem(properties.stacksTo(16), "R27"));
    public static final Item MISSILE_R77 = register("missile_r77",
        properties -> new MissileItem(properties.stacksTo(16), "R77"));
    public static final Item MISSILE_R73 = register("missile_r73",
        properties -> new MissileItem(properties.stacksTo(16), "R73"));
    public static final Item MISSILE_PL12 = register("missile_pl12",
        properties -> new MissileItem(properties.stacksTo(16), "PL12"));
    public static final Item MISSILE_MICA = register("missile_mica",
        properties -> new MissileItem(properties.stacksTo(16), "MICA"));
    public static final Item MISSILE_PL8 = register("missile_pl8",
        properties -> new MissileItem(properties.stacksTo(16), "PL8"));

    public static final Item MISSILE_AIM120L = register("missile_aim120l",
        properties -> new MissileItem(properties.stacksTo(16), "AIM120L"));
    public static final Item MISSILE_R771 = register("missile_r771",
        properties -> new MissileItem(properties.stacksTo(16), "R771"));
    public static final Item MISSILE_PL12A = register("missile_pl12a",
        properties -> new MissileItem(properties.stacksTo(16), "PL12A"));
    public static final Item MISSILE_PL15 = register("missile_pl15",
        properties -> new MissileItem(properties.stacksTo(16), "PL15"));

    public static final Item MISSILE_PL10 = register("missile_pl10",
        properties -> new MissileItem(properties.stacksTo(16), "PL10"));
    public static final Item GPS = register("gps",
        properties -> new GpsItem(properties.stacksTo(1)));
    public static final Item RUNWAY = register("runway",
        properties -> new RunwayItem(properties.stacksTo(1)));
    public static final Item HANGAR_KIT = register("hangar_kit",
        properties -> new HangarKitItem(properties.stacksTo(1)));

    public static final Item MISSILE_RACK = register("missile_rack",
        properties -> new BlockItem(AmracBlocks.MISSILE_RACK,
            properties.stacksTo(16).useBlockDescriptionPrefix()));
    public static final Item SCREEN = register("screen",
        properties -> new BlockItem(AmracBlocks.SCREEN,
            properties.useBlockDescriptionPrefix()));
    public static final Item CONSOLE = register("console",
        properties -> new BlockItem(AmracBlocks.CONSOLE,
            properties.useBlockDescriptionPrefix()));
    public static final Item REFUELER = register("refueler",
        properties -> new BlockItem(AmracBlocks.REFUELER,
            properties.stacksTo(16).useBlockDescriptionPrefix()));
    public static final Item MISSILE_LOADER = register("missile_loader",
        properties -> new MissileLoaderItem(properties.stacksTo(16)));

    public static final Item AIRCRAFT_REMOVER = register("aircraft_remover",
        properties -> new amrac.items.AircraftRemoverItem(properties.stacksTo(1)));

    public static final Item AI_COMMAND_BLOCK = register("ai_command_block",
        properties -> new net.minecraft.world.item.GameMasterBlockItem(
            AmracBlocks.AI_COMMAND_BLOCK,
            properties.rarity(net.minecraft.world.item.Rarity.EPIC)
                .useBlockDescriptionPrefix()));

    public static final Item AI_PILOT = register("ai_pilot",
        properties -> new AiPilotItem(properties.stacksTo(16),
            AiPilotVariant.SKELETON));
    public static final Item WITHER_AI_PILOT = register("wither_ai_pilot",
        properties -> new AiPilotItem(properties.stacksTo(16),
            AiPilotVariant.WITHER_SKELETON));

    public static final ResourceKey<CreativeModeTab> TAB_KEY =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            AmracMod.id("general"));

    private AmracItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY,
            amrac.platform.Platform.creativeTabBuilder()
                .title(Component.translatable("itemGroup.amrac.general"))
                .icon(() -> new ItemStack(F16))
                .displayItems((params, output) -> {
                    output.accept(F16);
                    output.accept(F15);
                    output.accept(SU27);
                    output.accept(F4J);
                    output.accept(MIG29);
                    output.accept(J10C);
                    output.accept(TYPHOON);
                    output.accept(MIG21);
                    output.accept(J8II);
                    output.accept(F15E);
                    output.accept(F18);
                    output.accept(MIG23);
                    output.accept(RAFALE);
                    output.accept(SU30);
                    output.accept(BULLET);
                    output.accept(AVIATION_FUEL);
                    output.accept(CHAFF);
                    output.accept(FLARE);
                    output.accept(MISSILE_SPARROW);
                    output.accept(MISSILE_AIM120);
                    output.accept(MISSILE_AIM9);
                    output.accept(MISSILE_MICA);
                    output.accept(MISSILE_R27);
                    output.accept(MISSILE_R77);
                    output.accept(MISSILE_R73);
                    output.accept(MISSILE_PL12);
                    output.accept(MISSILE_PL8);
                    output.accept(MISSILE_AIM120L);
                    output.accept(MISSILE_R771);
                    output.accept(MISSILE_PL12A);
                    output.accept(MISSILE_PL15);
                    output.accept(MISSILE_PL10);
                    output.accept(MISSILE_METEOR);
                    output.accept(GPS);
                    output.accept(RUNWAY);
                    output.accept(HANGAR_KIT);
                    output.accept(MISSILE_RACK);
                    output.accept(REFUELER);
                    output.accept(SCREEN);
                    output.accept(CONSOLE);
                    output.accept(MISSILE_LOADER);
                    output.accept(AIRCRAFT_REMOVER);
                    output.accept(AI_PILOT);
                    output.accept(WITHER_AI_PILOT);
                    output.accept(AI_COMMAND_BLOCK);
                })
                .build());
    }

    public static Component missileName(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            return Component.translatable("entity.amrac.missile");
        }
        Item item = missileIndex().get(profileId);
        return item != null ? item.getName(new ItemStack(item))
            : Component.literal(profileId);
    }

    public static Component missileShortName(String profileId) {
        String full = missileName(profileId).getString();
        int end = full.length();
        for (int i = 0; i < full.length(); i++) {
            char c = full.charAt(i);
            if (c == ' ' || c == '(') {
                end = i;
                break;
            }
        }
        String designation = full.substring(0, end).trim();
        return Component.literal(designation.isEmpty() ? full : designation);
    }

    public static Item missileItem(String profileId) {
        return profileId == null || profileId.isBlank()
            ? null : missileIndex().get(profileId);
    }

    private static Map<String, Item> missileIndex() {
        Map<String, Item> index = missileByProfile;
        if (index == null) {
            index = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof MissileItem missile) {
                    index.putIfAbsent(missile.profileId(), item);
                }
            }
            missileByProfile = index;
        }
        return index;
    }

    private static Map<String, Item> missileByProfile;

    private static <T extends Item> T register(
            String path, Function<Item.Properties, T> factory) {
        ResourceKey<Item> key = ResourceKey.create(
            Registries.ITEM, AmracMod.id(path));
        T item = factory.apply(new Item.Properties().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}

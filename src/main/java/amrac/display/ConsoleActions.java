package amrac.display;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;
import amrac.items.MissileItem;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.AtmosphereModel;
import amrac.physics.aircraft.DocumentEdit;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.physics.missile.MissileFlightModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConsoleActions {
    public static final int MAP_CHUNK_RADIUS = 32;

    private ConsoleActions() {
    }

    private static FlightModelRegistry registry() {
        FlightModelRegistry registry = FlightModelRegistry.instance();
        registry.refresh();
        return registry;
    }

    @Nullable
    public static String documentId(ItemStack stack, ConsoleMode mode) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (mode == ConsoleMode.CONFIG_MISSILE) {
            return stack.getItem() instanceof MissileItem missile
                ? missile.profileId().toLowerCase(Locale.ROOT) : null;
        }
        if (mode == ConsoleMode.CONFIG_PLANE) {
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            return registry().aircraftIds().contains(path) ? path : null;
        }
        return null;
    }

    public static boolean accepts(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(net.minecraft.world.item.Items.FILLED_MAP)
                || stack.is(net.minecraft.world.item.Items.MAP)
                || stack.is(amrac.AmracItems.GPS)
                || stack.getItem() instanceof MissileItem) {
            return true;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return registry().aircraftIds().contains(path);
    }

    @Nullable
    private static Path document(ConsoleMode mode, String id) {
        return mode == ConsoleMode.CONFIG_MISSILE
            ? registry().missileDocument(id) : registry().aircraftDocument(id);
    }

    @Nullable
    public static String documentText(ConsoleMode mode, String id) {
        return registry().documentText(document(mode, id));
    }

    @Nullable
    public static String readParameter(ConsoleMode mode, String id, String name) {
        String text = documentText(mode, id);
        if (text == null) {
            return null;
        }
        DocumentEdit.Leaf leaf = DocumentEdit.find(text, liveName(mode, text, name));
        return leaf == null ? null : leaf.value();
    }

    @Nullable
    public static String parameterPath(ConsoleMode mode, String id, String name) {
        String text = documentText(mode, id);
        if (text == null) {
            return null;
        }
        DocumentEdit.Leaf leaf = DocumentEdit.find(text, liveName(mode, text, name));
        return leaf == null ? null : leaf.path();
    }

    public static List<String> complete(ConsoleMode mode, String id,
                                        String prefix) {
        String text = documentText(mode, id);
        return text == null ? List.of()
            : DocumentEdit.complete(text, prefix, superseded(mode));
    }

    private static Map<String, String> superseded(ConsoleMode mode) {
        return mode == ConsoleMode.CONFIG_PLANE
            ? AircraftPhysicsProfile.LEGACY_LEAVES : Map.of();
    }

    private static String liveName(ConsoleMode mode, String text, String name) {
        return DocumentEdit.liveName(text, name, superseded(mode));
    }

    public static boolean applyParameter(ConsoleMode mode, String id,
                                         String name, String value) {
        String text = documentText(mode, id);
        if (text == null) {
            return false;
        }
        String edited = DocumentEdit.setValue(text,
            liveName(mode, text, name), value);
        return edited != null
            && registry().writeDocument(document(mode, id), edited);
    }

    public static ChartData chart(ConsoleMode mode, String id, ChartKind kind,
                                  double altitude, double targetSpeed) {
        FlightModelRegistry registry = registry();
        AtmosphereModel atmosphere = registry.atmosphere();
        if (mode == ConsoleMode.CONFIG_PLANE) {
            AircraftPhysicsProfile profile = registry.profile(id);
            if (profile == null) {
                return ChartData.EMPTY;
            }
            return switch (kind) {
                case TURN_RATE_VS_SPEED ->
                    AircraftCharts.turnRateVsSpeed(profile, atmosphere, altitude);
                case SEP_VS_SPEED ->
                    AircraftCharts.sepVsSpeed(profile, atmosphere, altitude);
                case MAX_SPEED_VS_ALTITUDE ->
                    AircraftCharts.maxSpeedVsAltitude(profile, atmosphere);
                default -> ChartData.EMPTY;
            };
        }
        if (mode == ConsoleMode.CONFIG_MISSILE) {
            MissileFlightModel model = registry.missileFlightModel(
                id == null ? null : id.toUpperCase(Locale.ROOT));
            if (model == null) {
                model = registry.missileFlightModel(id);
            }
            if (model == null) {
                return ChartData.EMPTY;
            }
            return switch (kind) {
                case SPEED_VS_TIME ->
                    MissileCharts.speedVsTime(model, atmosphere, altitude,
                        targetSpeed);
                case AVAILABLE_G_VS_SPEED ->
                    MissileCharts.availableGvsSpeed(model, atmosphere, altitude);
                case NEZ_VS_SPEED ->
                    MissileCharts.nezVsSpeed(model, atmosphere, altitude, targetSpeed);
                default -> ChartData.EMPTY;
            };
        }
        return ChartData.EMPTY;
    }

    public static int[] map(ServerLevel level, double centreX, double centreZ,
                            double span) {
        int resolution = ScreenContent.MAP_RESOLUTION;
        int[] out = new int[resolution * resolution];
        double step = span / resolution;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int row = 0; row < resolution; row++) {
            for (int column = 0; column < resolution; column++) {
                int x = (int) Math.floor(centreX - span / 2.0D + column * step);
                int z = (int) Math.floor(centreZ - span / 2.0D + row * step);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    out[row * resolution + column] = 0;
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                cursor.set(x, Math.max(level.getMinY(), y - 1), z);
                BlockState state = level.getBlockState(cursor);
                int colour = state.getMapColor(level, cursor).col;
                out[row * resolution + column] = colour == 0 ? 0x010101 : colour;
            }
        }
        return out;
    }

    public static double mapSpan() {
        return MAP_CHUNK_RADIUS * 2.0D * 16.0D;
    }

    public static double[] mapCentre(net.minecraft.world.level.Level level,
                                     ItemStack stack, BlockPos console) {
        if (stack != null && stack.is(net.minecraft.world.item.Items.FILLED_MAP)) {
            net.minecraft.world.level.saveddata.maps.MapItemSavedData data =
                net.minecraft.world.item.MapItem.getSavedData(stack, level);
            if (data != null) {
                return new double[] {data.centerX, data.centerZ};
            }
        }
        return new double[] {console.getX(), console.getZ()};
    }
}

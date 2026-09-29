package amrac.entities.ai;

import java.util.Map;
import net.minecraft.server.level.ServerLevel;

final class PlayAreaBounds {
    static final String HVS_PLAY_AREA = "highvelocitystreaming:play_area";

    private PlayAreaBounds() {
    }

    record Rectangle(double minX, double maxX, double minZ, double maxZ) {
    }

    static Rectangle of(ServerLevel level) {
        Rectangle published = published(amrac.platform.Platform.sharedObject(HVS_PLAY_AREA));
        if (published != null) return published;
        var border = level.getWorldBorder();
        return new Rectangle(border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ());
    }

    static Rectangle forAi(ServerLevel level) {
        Rectangle world = of(level);
        AiPilotSettings settings = AiPilotSettings.current();
        Rectangle cut = cut(world, settings);
        if (cut == null) {
            if (warnedAbout != settings) {
                warnedAbout = settings;
                amrac.AmracMod.LOGGER.warn("ai/pilot.json area {}..{} x {}..{}"
                    + " lies outside the world ({}..{} x {}..{}); AI pilots"
                    + " are using the world's edge instead",
                    (long) settings.areaMinX, (long) settings.areaMaxX,
                    (long) settings.areaMinZ, (long) settings.areaMaxZ,
                    (long) world.minX(), (long) world.maxX(),
                    (long) world.minZ(), (long) world.maxZ());
            }
            return world;
        }
        return cut;
    }

    static Rectangle cut(Rectangle world, AiPilotSettings settings) {
        if (!settings.areaEnabled) {
            return world;
        }
        double minX = Math.max(world.minX(), settings.areaMinX);
        double maxX = Math.min(world.maxX(), settings.areaMaxX);
        double minZ = Math.max(world.minZ(), settings.areaMinZ);
        double maxZ = Math.min(world.maxZ(), settings.areaMaxZ);
        return minX < maxX && minZ < maxZ
            ? new Rectangle(minX, maxX, minZ, maxZ) : null;
    }

    private static volatile AiPilotSettings warnedAbout;

    static Rectangle published(Object shared) {
        if (shared instanceof Map<?, ?> area
                && area.get("minX") instanceof Number minX
                && area.get("maxX") instanceof Number maxX
                && area.get("minZ") instanceof Number minZ
                && area.get("maxZ") instanceof Number maxZ
                && minX.intValue() <= maxX.intValue()
                && minZ.intValue() <= maxZ.intValue()) {
            return new Rectangle(minX.intValue(), maxX.intValue(), minZ.intValue(), maxZ.intValue());
        }
        return null;
    }
}

package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import amrac.entities.MissileEntity;
import amrac.entities.PlaneEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The warning range has one source, MissileWarningService.WARNING_RANGE; RwrPolicy.MAX_RANGE
 * derives from it and only limits the display. An extra, narrower filter on the client makes
 * warnings late.
 */
public final class MissileThreats {
    public record Threat(int missileId, double distance, double bearing) {
    }

    private MissileThreats() {
    }

    private static final java.util.Map<Integer, Vec3> REPORTED =
        new java.util.LinkedHashMap<>();
    private static final java.util.Set<Integer> RANGE_LIMITED =
        new java.util.HashSet<>();
    private static long reportedAt;

    private static final long REPORT_TIMEOUT_MS = 1500L;

    public static void acceptReported(List<amrac.network.PlaneNetworking.ThreatReport> reports) {
        REPORTED.clear();
        for (var r : reports) {
            REPORTED.put(r.missileId(), new Vec3(r.x(), r.y(), r.z()));
            if (r.rangeLimited()) {
                RANGE_LIMITED.add(r.missileId());
            } else {
                RANGE_LIMITED.remove(r.missileId());
            }
        }
        reportedAt = System.currentTimeMillis();
    }

    public static void clear() {
        REPORTED.clear();
        RANGE_LIMITED.clear();
        reportedAt = 0L;
    }

    public static List<Threat> against(Minecraft minecraft, PlaneEntity plane) {
        List<Threat> threats = new ArrayList<>();
        if (minecraft.level == null || plane == null) {
            return threats;
        }
        Vec3 centre = plane.getBoundingBox().getCenter();
        Vec3 nose = plane.getBodyDirection(0.0F, 0.0F, 1.0F);
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        for (var entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof MissileEntity missile)) {
                continue;
            }
            if (missile.getTargetId() != plane.getId() || !missile.isAlive()) {
                continue;
            }
            Vec3 toThreat = missile.position().subtract(centre);
            double distance = toThreat.length();
            seen.add(missile.getId());
            int mode = missile.getWarnMode();
            if (mode == amrac.weapons.MissileWarningService.WARN_NEVER
                || !amrac.weapons.MissileWarningService.warns(
                    mode == amrac.weapons.MissileWarningService.WARN_IN_RANGE,
                    distance)) {
                continue;
            }
            threats.add(new Threat(missile.getId(), distance,
                RwrPolicy.relativeBearing(nose.x, nose.z, toThreat.x, toThreat.z)));
        }
        if (System.currentTimeMillis() - reportedAt <= REPORT_TIMEOUT_MS) {
            for (var entry : REPORTED.entrySet()) {
                if (seen.contains(entry.getKey())) {
                    continue;
                }
                Vec3 toThreat = entry.getValue().subtract(centre);
                double distance = toThreat.length();
                if (!amrac.weapons.MissileWarningService.warns(
                    RANGE_LIMITED.contains(entry.getKey()), distance)) {
                    continue;
                }
                threats.add(new Threat(entry.getKey(), distance,
                    RwrPolicy.relativeBearing(nose.x, nose.z, toThreat.x, toThreat.z)));
            }
        }
        threats.sort(Comparator.comparingDouble(Threat::distance));
        return threats;
    }
}

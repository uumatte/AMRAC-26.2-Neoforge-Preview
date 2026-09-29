package amrac.weapons;

import amrac.platform.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import amrac.entities.PlaneEntity;
import amrac.network.PlaneNetworking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MissileWarningService {
    private static final int SEND_INTERVAL = 4;

    public static final double WARNING_RANGE = 12000.0D;

    public static final int WARN_NEVER = 0;
    public static final int WARN_IN_RANGE = 1;
    public static final int WARN_ALWAYS = 2;

    public static int warnMode(SeekerType type, SeekerState seeker) {
        if (seeker.spent) {
            return WARN_NEVER;
        }
        if (type == SeekerType.IR) {
            return WARN_IN_RANGE;
        }
        if (seeker.capturedDecoy >= 0 || seeker.relockingAfterDecoy) {
            return WARN_NEVER;
        }
        if (type == SeekerType.ARH && !seeker.active) {
            return WARN_NEVER;
        }
        return switch (seeker.loss) {
            case NONE, VELOCITY_GATE, REACQUIRING, CONFIRMING -> WARN_ALWAYS;
            case OUT_OF_VIEW, NO_ILLUMINATION, NO_TARGET, OVERSHOT -> WARN_NEVER;
        };
    }

    public static boolean warns(boolean rangeLimited, double distance) {
        return rangeLimited ? warns(distance) : Double.isFinite(distance);
    }

    public static boolean warns(double distance) {
        return Double.isFinite(distance) && distance <= warningRange();
    }

    public static double warningRange() {
        return WARNING_RANGE * amrac.physics.aircraft.SpeedScale.current();
    }

    public static boolean warns(double dx, double dy, double dz) {
        return warns(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static final Map<Integer, List<PlaneNetworking.ThreatReport>> REPORTS =
        new HashMap<>();

    private static int tickCounter;

    private MissileWarningService() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < SEND_INTERVAL) {
                REPORTS.clear();
                return;
            }
            tickCounter = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player.getVehicle() instanceof PlaneEntity plane)) {
                    continue;
                }
                List<PlaneNetworking.ThreatReport> threats =
                    REPORTS.get(plane.getId());
                PlaneNetworking.sendThreats(player,
                    threats == null ? List.of() : withinWarningRange(threats,
                        plane.getBoundingBox().getCenter()));
            }
            REPORTS.clear();
        });
    }

    private static List<PlaneNetworking.ThreatReport> withinWarningRange(
            List<PlaneNetworking.ThreatReport> threats, Vec3 centre) {
        List<PlaneNetworking.ThreatReport> near = new ArrayList<>(threats.size());
        for (PlaneNetworking.ThreatReport threat : threats) {
            double dx = threat.x() - centre.x;
            double dy = threat.y() - centre.y;
            double dz = threat.z() - centre.z;
            if (warns(threat.rangeLimited(), Math.sqrt(dx * dx + dy * dy + dz * dz))) {
                near.add(threat);
            }
        }
        return near;
    }

    public static void report(int targetEntityId, int missileId, Vec3 position,
                              boolean rangeLimited) {
        if (targetEntityId < 0 || position == null) {
            return;
        }
        REPORTS.computeIfAbsent(targetEntityId, k -> new ArrayList<>())
            .add(new PlaneNetworking.ThreatReport(missileId,
                position.x, position.y, position.z, rangeLimited));
    }
}

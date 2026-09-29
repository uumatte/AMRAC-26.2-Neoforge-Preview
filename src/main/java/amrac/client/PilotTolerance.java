package amrac.client;

import net.minecraft.client.Minecraft;
import amrac.entities.GForcePolicy;
import amrac.entities.PlaneEntity;

public final class PilotTolerance {
    public static final boolean EFFECTS_ENABLED = false;

    private static final GForcePolicy.State STATE = new GForcePolicy.State();
    private static int trackedPlaneId = -1;
    private static double currentLoad = 1.0D;

    private PilotTolerance() {
    }

    public static double loadFactor() {
        return currentLoad;
    }

    public static double blackout() {
        return EFFECTS_ENABLED ? STATE.blackout : 0.0D;
    }

    public static double redout() {
        return EFFECTS_ENABLED ? STATE.redout : 0.0D;
    }

    public static boolean isTunnelVision() {
        return EFFECTS_ENABLED && GForcePolicy.isTunnelVision(STATE);
    }

    public static void reset() {
        STATE.reset();
        trackedPlaneId = -1;
        currentLoad = 1.0D;
    }

    public static void tick(Minecraft minecraft) {
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (plane == null) {
            reset();
            return;
        }
        if (plane.getId() != trackedPlaneId) {
            STATE.reset();
            trackedPlaneId = plane.getId();
        }
        var flight = plane.getFlightState();
        currentLoad = flight == null ? 1.0D : flight.loadFactor();
        if (EFFECTS_ENABLED) {
            GForcePolicy.step(STATE, currentLoad, 1.0D / 20.0D);
        }
    }
}

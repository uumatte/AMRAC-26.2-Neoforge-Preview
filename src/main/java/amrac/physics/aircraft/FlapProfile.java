package amrac.physics.aircraft;

import java.util.Map;

public record FlapProfile(double liftIncrement, double dragIncrement,
                          double placardSpeed, int travelTicks) {
    public static final FlapProfile DEFAULT = new FlapProfile(
        FlapPolicy.FULL_LIFT_INCREMENT, FlapPolicy.FULL_DRAG_INCREMENT,
        FlapPolicy.DESTRUCTION_SPEED_BLOCKS_PER_SECOND, FlapPolicy.TRAVEL_TICKS);

    public static FlapProfile fromJson(Map<String, Object> root) {
        Map<String, Object> flaps = Json.object(root, "flaps");
        if (flaps == null) {
            return DEFAULT;
        }
        return new FlapProfile(
            nonNegative(Json.number(flaps, "liftIncrement", DEFAULT.liftIncrement()),
                DEFAULT.liftIncrement()),
            nonNegative(Json.number(flaps, "dragIncrement", DEFAULT.dragIncrement()),
                DEFAULT.dragIncrement()),
            positive(Json.number(flaps, "placardSpeed", DEFAULT.placardSpeed()),
                DEFAULT.placardSpeed()),
            (int) Math.max(1L, Math.round(positive(
                Json.number(flaps, "travelTicks", DEFAULT.travelTicks()),
                DEFAULT.travelTicks()))));
    }

    public double placardSpeedBlocksPerTick() {
        return placardSpeed / amrac.physics.TickRate.TICKS_PER_SECOND;
    }

    public double liftAt(double position) {
        return liftIncrement * clampPosition(position);
    }

    public double dragAt(double position) {
        return dragIncrement * clampPosition(position);
    }

    private static double clampPosition(double position) {
        if (!Double.isFinite(position)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, position));
    }

    private static double nonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0D ? value : fallback;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }
}

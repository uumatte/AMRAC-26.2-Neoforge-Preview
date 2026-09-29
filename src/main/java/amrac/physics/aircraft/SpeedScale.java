package amrac.physics.aircraft;

/**
 * Built-in speed and distance constants are multiplied by SpeedScale.current(), and gains per unit
 * of speed or distance divided by it; time, angles, g and world distances are not scaled. Do the
 * same for new constants.
 */
public final class SpeedScale {
    private SpeedScale() {
    }

    public static double current() {
        return FlightModelRegistry.instance().atmosphere().speedScale();
    }

    public static double gravityBlocksPerTickSquared() {
        return FlightModelRegistry.instance().atmosphere().gravity()
            / amrac.physics.TickRate.TICKS_PER_SECOND_SQUARED;
    }
}

package amrac.client;

public final class AutoThrottleController {
    public static final double REARM_SPEED_BLOCKS_PER_SECOND = 20.0D;
    public static final double STATIONARY_SPEED_BLOCKS_PER_SECOND = 0.10D;

    public static final double GUARD_MARGIN_BLOCKS_PER_SECOND = 40.0D;

    public static final double FULL_CLOSURE_FRACTION = 0.6D;

    public static final int LOOKAHEAD_TICKS = 30;

    public static final float MAX_CLOSURE_SLEW_PER_TICK = 4.0F;
    public static final float MAX_RELEASE_SLEW_PER_TICK = 1.2F;

    private static final double ACCELERATION_SMOOTHING = 0.12D;

    private double previousSpeed = Double.NaN;
    private double smoothedAcceleration;

    private int controlledPlaneId = -1;
    private boolean lowSpeedLocked;
    private boolean initialAccelerationLocked;
    private boolean previousThrottleUp;
    private boolean lockedAccelerationHeld;

    private float commandedThrottle;
    private float guardCap = Float.MAX_VALUE;
    private boolean intervening;

    public void reset() {
        controlledPlaneId = -1;
        previousSpeed = Double.NaN;
        smoothedAcceleration = 0.0D;
        lowSpeedLocked = false;
        initialAccelerationLocked = false;
        previousThrottleUp = false;
        lockedAccelerationHeld = false;
        commandedThrottle = 0.0F;
        guardCap = Float.MAX_VALUE;
        intervening = false;
    }

    public boolean isIntervening() {
        return intervening;
    }

    public float tick(int planeId, float currentThrottle,
                      boolean requireInitialAcceleration,
                      boolean onSurface, double speedBlocksPerSecond,
                      boolean throttleUp,
                      boolean throttleDown, float increaseStep,
                      float decreaseStep, float maximumThrottle,
                      double structuralLimitBlocksPerSecond) {
        float maximum = Float.isFinite(maximumThrottle) &&
            maximumThrottle > 0.0F ? maximumThrottle : 0.0F;
        float throttle = Float.isFinite(currentThrottle)
            ? clamp(currentThrottle, 0.0F, maximum) : 0.0F;
        float increase = Float.isFinite(increaseStep)
            ? Math.max(0.0F, increaseStep) : 0.0F;
        float decrease = Float.isFinite(decreaseStep)
            ? Math.max(0.0F, decreaseStep) : 0.0F;
        double speed = Double.isFinite(speedBlocksPerSecond)
            ? Math.max(0.0D, speedBlocksPerSecond) : 0.0D;
        double rearmSpeed = REARM_SPEED_BLOCKS_PER_SECOND
            * amrac.physics.aircraft.SpeedScale.current();
        boolean belowRearmSpeed =
            speed < rearmSpeed;
        boolean groundedBelowRearmSpeed = onSurface && belowRearmSpeed;

        double acceleration = Double.isNaN(previousSpeed) ? 0.0D
            : speed - previousSpeed;
        previousSpeed = speed;
        smoothedAcceleration += (acceleration - smoothedAcceleration) *
            ACCELERATION_SMOOTHING;

        if (controlledPlaneId != planeId) {
            controlledPlaneId = planeId;
            previousSpeed = speed;
            smoothedAcceleration = 0.0D;
            initialAccelerationLocked = requireInitialAcceleration;
            lowSpeedLocked = initialAccelerationLocked ||
                groundedBelowRearmSpeed;
            previousThrottleUp = throttleUp;
            lockedAccelerationHeld = false;
            commandedThrottle = throttle;
            guardCap = maximum;
            intervening = false;
            if (lowSpeedLocked) {
                throttle = 0.0F;
                commandedThrottle = 0.0F;
            }
        } else if (lowSpeedLocked && !onSurface &&
                   !initialAccelerationLocked) {
            lowSpeedLocked = false;
            lockedAccelerationHeld = false;
        } else if (!lowSpeedLocked && groundedBelowRearmSpeed) {
            lowSpeedLocked = true;
            initialAccelerationLocked = false;
            previousThrottleUp = throttleUp;
            lockedAccelerationHeld = false;
            throttle = 0.0F;
            commandedThrottle = 0.0F;
        }

        if (lowSpeedLocked) {
            if (!throttleUp) {
                lockedAccelerationHeld = false;
            } else if (!previousThrottleUp) {
                lockedAccelerationHeld = true;
            }
            boolean acceleratingForRecovery = lockedAccelerationHeld &&
                throttleUp && !throttleDown;
            if (acceleratingForRecovery &&
                (!onSurface || speed >= rearmSpeed)) {
                lowSpeedLocked = false;
                initialAccelerationLocked = false;
                lockedAccelerationHeld = false;
                commandedThrottle = clamp(commandedThrottle + increase,
                    0.0F, maximum);
            } else {
                commandedThrottle = clamp(commandedThrottle
                    + (acceleratingForRecovery ? increase : -decrease),
                    0.0F, maximum);
            }
            previousThrottleUp = throttleUp;
            guardCap = maximum;
            intervening = false;
            return clamp(commandedThrottle, 0.0F, maximum);
        }

        if (throttleUp || throttleDown) {
            commandedThrottle = clamp(commandedThrottle
                + (throttleDown ? -decrease : increase), 0.0F, maximum);
        }

        previousThrottleUp = throttleUp;
        guardCap = nextGuardCap(speed, structuralLimitBlocksPerSecond, maximum);
        float delivered = Math.min(commandedThrottle, guardCap);
        intervening = delivered < commandedThrottle - 1.0E-4F;
        return clamp(delivered, 0.0F, maximum);
    }

    private float nextGuardCap(double speed, double structuralLimit,
                               float maximum) {
        if (!Double.isFinite(structuralLimit) || structuralLimit <= 0.0D) {
            return maximum;
        }
        double predicted = speed + smoothedAcceleration * LOOKAHEAD_TICKS;
        if (!Double.isFinite(predicted)) {
            predicted = speed;
        }
        double guardMargin = GUARD_MARGIN_BLOCKS_PER_SECOND
            * amrac.physics.aircraft.SpeedScale.current();
        double guardSpeed = structuralLimit - guardMargin;
        double target;
        if (predicted <= guardSpeed) {
            target = maximum;
        } else {
            double band = Math.max(guardMargin, 1.0E-6D)
                * FULL_CLOSURE_FRACTION;
            double into = clampDouble((predicted - guardSpeed) / band,
                0.0D, 1.0D);
            target = maximum * (1.0D - into);
        }
        float wanted = (float) clampDouble(target, 0.0D, maximum);
        float current = Float.isFinite(guardCap)
            ? clamp(guardCap, 0.0F, maximum) : maximum;
        float step = wanted < current
            ? MAX_CLOSURE_SLEW_PER_TICK : MAX_RELEASE_SLEW_PER_TICK;
        if (Math.abs(wanted - current) <= step) {
            return wanted;
        }
        return wanted < current ? current - step : current + step;
    }

    private static double clampDouble(double value, double minimum,
                                      double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

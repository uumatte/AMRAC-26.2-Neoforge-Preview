package amrac.network;

// How far a pilot's client may move its aircraft between two server ticks.
// Vanilla measures every vehicle move from the tick's starting position and
// allows one tick of velocity plus ten blocks, so two moves arriving together
// (jitter, a retransmit, a long server tick) refuse the second -- and every
// move after it, until the client has been pulled back. This budget refills by
// the wall clock at the reported speed and is spent by accepted moves: up to
// five seconds of flight may be caught up at once, never more ground than the
// speed allows over time, and never stricter than vanilla. Plain numbers so the
// regression tests can drive it.
public final class ClientMoveBudget {

    // One second was too short: lossy links go quiet for 1.5-2 s.
    public static final int MAX_CATCH_UP_TICKS = 100;

    // The refill uses the current speed; a slowing aircraft covered a little more.
    public static final double SPEED_MARGIN = 1.1D;

    private boolean open;
    private long gapTime;
    private long refilledAtNanos;
    private double startX;
    private double startY;
    private double startZ;
    private double budget;
    private double allowance;

    public void reset() {
        open = false;
    }

    // Every move handled between two ticks shares one allowance, since vanilla
    // measures them all from the same start; what they covered is charged when
    // the next group arrives.
    public double expectedSqr(double vanillaExpectedSqr, long gameTime,
                              long nowNanos, long nanosPerTick,
                              double x, double y, double z,
                              double speedPerTick) {
        if (!open || gameTime != gapTime) {
            openGap(gameTime, nowNanos, nanosPerTick, x, y, z, speedPerTick);
        }
        return Math.max(vanillaExpectedSqr, allowance * allowance);
    }

    private void openGap(long gameTime, long nowNanos, long nanosPerTick,
                         double x, double y, double z, double speedPerTick) {
        double reach = Double.isFinite(speedPerTick)
            ? Math.max(0.0D, speedPerTick) * SPEED_MARGIN : 0.0D;
        double cap = reach * MAX_CATCH_UP_TICKS;
        if (!open) {
            budget = cap;
        } else {
            double dx = x - startX;
            double dy = y - startY;
            double dz = z - startZ;
            double spent = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double ticks = Math.max(0L, nowNanos - refilledAtNanos)
                / (double) Math.max(1L, nanosPerTick);
            // Floored at zero so a teleport cannot lock the pilot out.
            budget = budget - (Double.isFinite(spent) ? spent : 0.0D)
                + ticks * reach;
        }
        budget = Math.max(0.0D, Math.min(cap, budget));
        allowance = budget;
        open = true;
        gapTime = gameTime;
        refilledAtNanos = nowNanos;
        startX = x;
        startY = y;
        startZ = z;
    }
}

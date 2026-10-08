package amrac.network;

// How far the server's picture of a player's aircraft has lately turned out to
// be wrong, in blocks: the room a missile's fuse gets on top of the airframe.
// Between bursts on a lossy link the picture is carried straight ahead (or
// frozen past the cap) while the pilot turns; when the next burst lands it
// jumps, and a round about to hit finds the target elsewhere. Each jump is
// measured on the tick a new state arrives -- this tick's picture against last
// tick's carried on by its velocity -- and the largest recent one, fading with
// a two-second half-life, is the slack. A clean link gets a fraction of a
// block. Plain numbers for the regression tests.
public final class ClientPositionSlack {

    public static final double MAX_SLACK = 24.0D;

    public static final int HALF_LIFE_TICKS = 40;

    private static final double DECAY_PER_TICK =
        Math.pow(0.5D, 1.0D / HALF_LIFE_TICKS);

    private boolean seen;
    private long lastTick;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double lastVx;
    private double lastVy;
    private double lastVz;
    private double slack;

    public void reset() {
        seen = false;
        slack = 0.0D;
    }

    // fresh: a new client state arrived this tick. Same tick twice is a no-op.
    public double update(long tick, double x, double y, double z,
                         double vx, double vy, double vz, boolean fresh) {
        if (seen && tick == lastTick) {
            return slack;
        }
        if (seen && tick > lastTick) {
            long elapsed = tick - lastTick;
            slack *= Math.pow(DECAY_PER_TICK, elapsed);
            if (fresh) {
                double dx = x - (lastX + lastVx * elapsed);
                double dy = y - (lastY + lastVy * elapsed);
                double dz = z - (lastZ + lastVz * elapsed);
                double jump = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (Double.isFinite(jump)) {
                    slack = Math.max(slack, Math.min(MAX_SLACK, jump));
                }
            }
        } else if (seen) {
            // Level clock went backwards: a reload.
            slack = 0.0D;
        }
        seen = true;
        lastTick = tick;
        lastX = x;
        lastY = y;
        lastZ = z;
        lastVx = Double.isFinite(vx) ? vx : 0.0D;
        lastVy = Double.isFinite(vy) ? vy : 0.0D;
        lastVz = Double.isFinite(vz) ? vz : 0.0D;
        return slack;
    }

    public double slack() {
        return slack;
    }
}

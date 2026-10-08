package amrac.network;

import java.util.ArrayDeque;

// How late a pilot's newest flight state was when it reached the server, in
// ticks. On a lossy link a lost segment holds everything behind it until the
// retransmit, then the queue lands in one tick: the state is already several
// ticks old, so carrying the aircraft forward from its arrival leaves it that
// far behind. Each state carries the client's tick; the shortest recent trip
// (arrival tick minus client tick) is the baseline, and anything longer is
// lateness. The baseline itself is not taken off -- the server's world runs
// that far behind evenly, missiles included. The window follows drifting
// clocks; the cap bounds a briefly wrong baseline. Plain numbers for tests.
public final class ClientSampleClock {

    public static final int WINDOW_TICKS = 100;

    public static final int MAX_LATENESS_TICKS = 10;

    // (arrival tick, trip), trips strictly rising from the front.
    private final ArrayDeque<long[]> shortest = new ArrayDeque<>();
    private long lastClientTick;
    private boolean seen;
    private int lateness;

    public void reset() {
        shortest.clear();
        seen = false;
        lateness = 0;
    }

    public int sample(long clientTick, long serverTick) {
        // Ticks running backwards: a new aeroplane or session on the client.
        if (seen && clientTick < lastClientTick) {
            reset();
        }
        seen = true;
        lastClientTick = clientTick;
        long trip = serverTick - clientTick;
        while (!shortest.isEmpty() && shortest.peekLast()[1] >= trip) {
            shortest.pollLast();
        }
        shortest.addLast(new long[] {serverTick, trip});
        while (shortest.peekFirst()[0] < serverTick - WINDOW_TICKS) {
            shortest.pollFirst();
        }
        long late = trip - shortest.peekFirst()[1];
        lateness = (int) Math.max(0L, Math.min(MAX_LATENESS_TICKS, late));
        return lateness;
    }

    public int lateness() {
        return lateness;
    }
}

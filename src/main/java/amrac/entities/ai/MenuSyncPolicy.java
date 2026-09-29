package amrac.entities.ai;

/**
 * Container data slots are 16-bit: 32-bit values (entity ids, block coordinates) are split into
 * high and low slots, the low one & 0xFFFF. ConsoleMenu sends block coordinates this way (the
 * client menu comes from the two-argument constructor and has no block entity); new fields like
 * that go through here.
 */
public final class MenuSyncPolicy {
    public static final int SLOT_MASK = 0xFFFF;

    private MenuSyncPolicy() {
    }

    public static int high(int value) {
        return (value >>> 16) & SLOT_MASK;
    }

    public static int low(int value) {
        return value & SLOT_MASK;
    }

    public static int combine(int high, int low) {
        return (high << 16) | (low & SLOT_MASK);
    }
}

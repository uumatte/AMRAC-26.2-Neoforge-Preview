package amrac.weapons;

import amrac.entities.MissileEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LiveMissileIndex {
    private static final Map<UUID, MissileEntity> LIVE = new ConcurrentHashMap<>();

    private LiveMissileIndex() {
    }

    public static void note(MissileEntity missile) {
        LIVE.put(missile.getUUID(), missile);
    }

    public static List<MissileEntity> chasing(UUID aircraftId) {
        if (aircraftId == null || LIVE.isEmpty()) {
            return List.of();
        }
        List<MissileEntity> out = new ArrayList<>(2);
        for (Iterator<MissileEntity> it = LIVE.values().iterator(); it.hasNext();) {
            MissileEntity missile = it.next();
            if (missile.isRemoved() || !missile.isAlive()) {
                it.remove();
                continue;
            }
            if (missile.chasing(aircraftId)) {
                out.add(missile);
            }
        }
        return out;
    }

    public static void clear() {
        LIVE.clear();
    }
}

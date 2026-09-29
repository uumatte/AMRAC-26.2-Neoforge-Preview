package amrac.weapons;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class AirframeLoadoutPolicy {
    private AirframeLoadoutPolicy() {
    }

    public static boolean semiActive(MissileProfile round) {
        return !round.activeHoming;
    }

    public static boolean permits(MissileFaction rail, boolean semiActiveOnly,
                                  Set<String> barred, MissileProfile round) {
        if (round == null || rail == null || !rail.accepts(round.faction)) {
            return false;
        }
        if (semiActiveOnly && !semiActive(round)) {
            return false;
        }
        return barred == null || !barred.contains(round.id);
    }

    public static boolean armable(List<MissileProfile> candidates,
                                  Predicate<String> rankAllows,
                                  Predicate<String> airframeCarries) {
        for (MissileProfile round : candidates) {
            if (rankAllows.test(round.id) && airframeCarries.test(round.id)) {
                return true;
            }
        }
        return false;
    }
}

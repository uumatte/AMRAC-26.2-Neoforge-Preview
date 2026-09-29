package amrac.weapons;

import org.jetbrains.annotations.Nullable;

public final class MissileEndgameLog {
    private MissileEndgameLog() {
    }

    public static void spent(String profileId, @Nullable String shooter,
                             int ageTicks, double closestMiss, double fuseRadius,
                             double speedBlocksPerSecond, double rangeToTarget,
                             String layer, String seeker) {
        amrac.AmracMod.LOGGER.info(
            "{} fired by {} ran out of life after {} s in the {} layer:"
            + " closest approach {}, fuse {} m, {} b/s left, target {} away;"
            + " seeker: {}",
            profileId, shooter == null ? "nobody" : shooter,
            String.format("%.1f", ageTicks / 20.0F), layer,
            Double.isInfinite(closestMiss) ? "never saw the target"
                : String.format("%.1f m", closestMiss),
            String.format("%.1f", fuseRadius),
            String.format("%.0f", speedBlocksPerSecond),
            Double.isNaN(rangeToTarget) ? "unknown"
                : String.format("%.0f m", rangeToTarget), seeker);
    }

    public static void targetLost(String profileId, @Nullable String shooter,
                                  int ageTicks, double rangeWhenLost,
                                  String layer) {
        amrac.AmracMod.LOGGER.info(
            "{} fired by {} lost its target after {} s in the {} layer,"
            + " {} away: nothing is simulating that aircraft any more",
            profileId, shooter == null ? "nobody" : shooter,
            String.format("%.1f", ageTicks / 20.0F), layer,
            Double.isNaN(rangeWhenLost) ? "range unknown"
                : String.format("%.0f m", rangeWhenLost));
    }
}

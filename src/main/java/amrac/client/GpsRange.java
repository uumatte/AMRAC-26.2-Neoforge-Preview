package amrac.client;

import amrac.gps.GpsPolicy;

public final class GpsRange {
    private static int airborneIndex =
        GpsPolicy.defaultScaleIndex(GpsPolicy.AIRBORNE_SCALES);
    private static int tacticalIndex =
        GpsPolicy.defaultScaleIndex(GpsPolicy.TACTICAL_SCALES);

    private GpsRange() {
    }

    public static double outerRing(boolean tactical) {
        return GpsPolicy.scaleAt(GpsPolicy.ladderFor(tactical),
            tactical ? tacticalIndex : airborneIndex);
    }

    public static void cycle(boolean tactical) {
        int stops = GpsPolicy.ladderFor(tactical).length;
        if (tactical) {
            tacticalIndex = GpsPolicy.nextScaleIndex(tacticalIndex, stops);
        } else {
            airborneIndex = GpsPolicy.nextScaleIndex(airborneIndex, stops);
        }
    }

    public static double airborne() {
        return outerRing(false);
    }

    public static double tactical() {
        return outerRing(true);
    }
}

package amrac.physics.missile;

/**
 * Missile flight models are chosen by profile id, not by a MissileProfile field: two missiles
 * derive from MissileProfile.from(AIM120) and would inherit it.
 */
public final class MissileFlightModelIds {
    public static final String AIM120 = "AIM120";

    private MissileFlightModelIds() {
    }
}

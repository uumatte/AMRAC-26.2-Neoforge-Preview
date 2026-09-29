package amrac.client.render;

import net.minecraft.resources.Identifier;

import java.util.Map;

import amrac.AmracMod;
import amrac.client.render.models.MissileModel;

/**
 * Pylon rounds (PylonMissiles), missiles in flight (MissileRenderer) and the missile rack all use
 * this one mesh/texture/sink table; don't give any of them its own model.
 *
 * Linked by the BVR Replay mod: keep this class's name, package and public members, or replays
 * crash with NoClassDefFoundError.
 */
public final class MissileKits {
    public record Kit(MissileModel model, Identifier texture, float drop) {
    }

    private static final Kit SPARROW = kit("missile", MissileModel.SPARROW, 0.06F);
    private static final Kit AIM120 = kit("aim120", MissileModel.AIM120, 0.00F);
    private static final Kit AIM9 = kit("aim9", MissileModel.AIM9, -0.11F);
    private static final Kit R27 = kit("r27", MissileModel.R27, 0.10F);
    private static final Kit R73 = kit("r73", MissileModel.R73, -0.02F);
    private static final Kit R77 = kit("r77", MissileModel.R77, 0.04F);
    private static final Kit PL8 = kit("pl8", MissileModel.PL8, -0.04F);
    private static final Kit PL10 = kit("pl10", MissileModel.PL10, -0.02F);
    private static final Kit PL12 = kit("pl12", MissileModel.PL12, 0.05F);
    private static final Kit PL15 = kit("pl15", MissileModel.PL15, 0.05F);
    private static final Kit MICA = kit("mica", MissileModel.MICA, -0.02F);
    private static final Kit METEOR = kit("meteor", MissileModel.METEOR, 0.00F);

    private static final Map<String, Kit> BY_ID = Map.ofEntries(
        Map.entry("MICA", MICA),
        Map.entry("METEOR", METEOR),
        Map.entry("AIM7", SPARROW),
        Map.entry("AIM120", AIM120),
        Map.entry("AIM120L", AIM120),
        Map.entry("AIM9", AIM9),
        Map.entry("PL8", PL8),
        Map.entry("R27", R27),
        Map.entry("R73", R73),
        Map.entry("R77", R77),
        Map.entry("R771", R77),
        Map.entry("PL10", PL10),
        Map.entry("PL12", PL12),
        Map.entry("PL12A", PL12),
        Map.entry("PL15", PL15));

    private MissileKits() {
    }

    private static Kit kit(String slug, String mesh, float drop) {
        return new Kit(new MissileModel(mesh), AmracMod.id(
            "textures/entity/" + slug + ".png"), drop);
    }

    public static Kit forProfile(String profileId) {
        if (profileId == null) {
            return SPARROW;
        }
        return BY_ID.getOrDefault(profileId, SPARROW);
    }
}

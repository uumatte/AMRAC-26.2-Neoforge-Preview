package amrac.client;

import com.mojang.datafixers.util.Pair;
import amrac.platform.client.HudElement;
import amrac.platform.client.HudElementRegistry;
import amrac.platform.client.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import amrac.mixin.HudContextualBarAccessor;

public final class CockpitHud {
    private static final VanillaHudElements[] PLAYER_STATUS = {
        VanillaHudElements.ARMOR_BAR,
        VanillaHudElements.HEALTH_BAR,
        VanillaHudElements.FOOD_BAR,
        VanillaHudElements.AIR_BAR,
        VanillaHudElements.EXPERIENCE_LEVEL,
    };

    private CockpitHud() {
    }

    public static void suppressPlayerStatusInCockpit() {
        for (VanillaHudElements element : PLAYER_STATUS) {
            HudElementRegistry.replaceElement(element,
                original -> skipWhenAboard(original, false));
        }
        HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR,
            original -> skipWhenAboard(original, true));
    }

    private static HudElement skipWhenAboard(HudElement original,
                                             boolean onlyExperience) {
        return (graphics, deltaTracker) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (PlaneViewState.ridingPlane(minecraft) != null
                && (!onlyExperience || showingExperienceBar(minecraft))) {
                return;
            }
            original.extractRenderState(graphics, deltaTracker);
        };
    }

    private static boolean showingExperienceBar(Minecraft minecraft) {
        if (minecraft.gui == null || minecraft.gui.hud == null) {
            return false;
        }
        Pair<?, ?> contextual =
            ((HudContextualBarAccessor) minecraft.gui.hud).amrac$contextualInfoBar();
        return contextual != null && contextual.getSecond() instanceof ExperienceBar;
    }
}

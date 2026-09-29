package amrac.platform.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface HudElement {
    void extractRenderState(GuiGraphicsExtractor graphics,
                            DeltaTracker deltaTracker);
}

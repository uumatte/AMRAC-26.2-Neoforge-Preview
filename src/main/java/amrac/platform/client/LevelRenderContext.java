package amrac.platform.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

public interface LevelRenderContext {
    SubmitNodeCollector submitNodeCollector();

    PoseStack poseStack();
}

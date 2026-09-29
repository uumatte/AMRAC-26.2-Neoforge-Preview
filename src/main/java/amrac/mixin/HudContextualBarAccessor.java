package amrac.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Hud.class)
public interface HudContextualBarAccessor {
    @Accessor("contextualInfoBar")
    Pair<?, ?> amrac$contextualInfoBar();
}

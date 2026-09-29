package amrac.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("ALL")
    static Map<String, KeyMapping> amrac$all() {
        throw new AssertionError("mixin accessor");
    }

    @Accessor("key")
    InputConstants.Key amrac$getKey();

    @Accessor("clickCount")
    int amrac$getClickCount();

    @Accessor("clickCount")
    void amrac$setClickCount(int clickCount);
}

package amrac.platform.client;

import net.minecraft.client.KeyMapping;

public final class KeyMappingHelper {
    private KeyMappingHelper() {
    }

    public static KeyMapping registerKeyMapping(KeyMapping mapping) {
        ClientPlatform.backend().registerKeyMapping(mapping);
        return mapping;
    }
}

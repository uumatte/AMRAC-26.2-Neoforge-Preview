package amrac.platform.client;

import java.util.function.UnaryOperator;
import net.minecraft.resources.Identifier;

public final class HudElementRegistry {
    private HudElementRegistry() {
    }

    public static void addLast(Identifier id, HudElement element) {
        ClientPlatform.backend().addHudElementLast(id, element);
    }

    public static void replaceElement(VanillaHudElements element,
                                      UnaryOperator<HudElement> replacer) {
        ClientPlatform.backend().replaceHudElement(element, replacer);
    }
}

package amrac.client.render;

import amrac.AmracMod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class IrisCompat {
    private static boolean resolved;
    private static MethodHandle shadowPass;

    private IrisCompat() {
    }

    static boolean renderingShadowPass() {
        MethodHandle handle = shadowPassHandle();
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invokeExact();
        } catch (Throwable failure) {
            return false;
        }
    }

    private static MethodHandle shadowPassHandle() {
        if (resolved) {
            return shadowPass;
        }
        resolved = true;
        if (!amrac.platform.Platform.isModLoaded("iris")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            shadowPass = MethodHandles.publicLookup()
                .findVirtual(api, "isRenderingShadowPass",
                    MethodType.methodType(boolean.class))
                .bindTo(instance);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            AmracMod.LOGGER.warn("Iris is installed but its shadow-pass query "
                + "could not be found; far aircraft may cast stray shadows",
                failure);
        }
        return shadowPass;
    }
}

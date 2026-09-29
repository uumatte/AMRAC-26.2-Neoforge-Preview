package amrac.client;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import amrac.entities.PlaneEntity;

public final class CountermeasureSounds {
    private static final Set<SoundInstance> OWN =
        Collections.newSetFromMap(new WeakHashMap<>());

    private static int trackedPlaneId = -1;
    private static int chaffLastTick = -1;
    private static int flareLastTick = -1;

    private CountermeasureSounds() {
    }

    public static void tick(Minecraft minecraft) {
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (plane == null) {
            reset();
            return;
        }
        int chaff = plane.getChaffCount();
        int flare = plane.getFlareCount();
        if (plane.getId() != trackedPlaneId) {
            trackedPlaneId = plane.getId();
            chaffLastTick = chaff;
            flareLastTick = flare;
            return;
        }
        if (chaffLastTick >= 0 && chaff < chaffLastTick) {
            play(minecraft, SimpleSoundInstance.forUI(
                SoundEvents.DISPENSER_LAUNCH, 1.4F, 0.8F));
        }
        if (flareLastTick >= 0 && flare < flareLastTick) {
            play(minecraft, SimpleSoundInstance.forUI(
                SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.7F, 0.8F));
        }
        chaffLastTick = chaff;
        flareLastTick = flare;
    }

    public static boolean isOwn(SoundInstance sound) {
        return OWN.contains(sound);
    }

    public static void reset() {
        trackedPlaneId = -1;
        chaffLastTick = -1;
        flareLastTick = -1;
    }

    private static void play(Minecraft minecraft, SoundInstance sound) {
        OWN.add(sound);
        minecraft.getSoundManager().play(sound);
    }
}

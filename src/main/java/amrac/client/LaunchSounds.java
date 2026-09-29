package amrac.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import amrac.AmracSounds;
import amrac.entities.PlaneEntity;
import amrac.client.SoundMixPolicy;
import amrac.weapons.MissilePolicy;

public final class LaunchSounds {
    private static int trackedPlaneId = -1;
    private static int loadedLastTick = -1;
    private static int motorCountdown = -1;

    private LaunchSounds() {
    }

    public static void tick(Minecraft minecraft) {
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (plane == null) {
            reset();
            return;
        }
        int loaded = countLoaded(plane.getLoadout());
        if (plane.getId() != trackedPlaneId) {
            trackedPlaneId = plane.getId();
            loadedLastTick = loaded;
            motorCountdown = -1;
            return;
        }
        if (loadedLastTick >= 0 && loaded < loadedLastTick) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                AmracSounds.MISSILE_RELEASE, 1.0F,
                SoundMixPolicy.MISSILE_THUMP_VOLUME));
            motorCountdown = 0;
        }
        loadedLastTick = loaded;
        if (motorCountdown >= 0 && motorCountdown-- == 0) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                AmracSounds.MISSILE_LAUNCH_COCKPIT, 1.0F,
                SoundMixPolicy.MISSILE_LAUNCH_COCKPIT_VOLUME));
        }
    }

    public static void reset() {
        trackedPlaneId = -1;
        loadedLastTick = -1;
        motorCountdown = -1;
    }

    private static int countLoaded(String[] loadout) {
        if (loadout == null) {
            return 0;
        }
        int n = 0;
        for (String slot : loadout) {
            if (slot != null && !slot.isEmpty()) {
                n++;
            }
        }
        return n;
    }
}

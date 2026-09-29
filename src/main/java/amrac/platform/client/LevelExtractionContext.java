package amrac.platform.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;

public interface LevelExtractionContext {
    ClientLevel level();

    Camera camera();

    DeltaTracker deltaTracker();
}

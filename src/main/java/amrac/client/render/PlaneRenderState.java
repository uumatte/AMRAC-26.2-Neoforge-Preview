package amrac.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;

/**
 * Linked by the BVR Replay mod: keep this class's name, package and public members, or replays
 * crash with NoClassDefFoundError.
 */
public final class PlaneRenderState extends EntityRenderState {
    public final Quaternionf attitude = new Quaternionf();
    public float modelScale = 1.0F;
    public float gearPosition = 1.0F;
    public float controlPitch;
    public float controlRoll;
    public float controlYaw;
    public float flapPosition;
    public float speedBrakePosition;
    public float afterburnerSpool;
    public float enginePower;
    public boolean engineRunning;
    public boolean firstPersonRide;
    public boolean firstPersonPilot;
    public String[] loadout = new String[0];
}

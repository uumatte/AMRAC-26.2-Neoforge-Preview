package amrac.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.UUID;

public final class VirtualAircraftState {
    public final UUID id;
    public final ResourceKey<Level> dimension;
    public final Identifier type;

    public Vec3 position;
    public Vec3 velocity;
    public float yaw;
    public float pitch;

    @Nullable
    public final UUID pilotId;

    @Nullable
    public final CompoundTag pilotSaved;

    /**
     * The tag PlaneEntity.addAdditionalSaveData writes. Defaults such as angleOfAttackLimiter must
     * match PlaneEntity's synced-data defaults (a plain field defaulting to false once switched the
     * limiter off).
     */
    public final CompoundTag saved;

    public long since;

    @Nullable
    public String flightModelId;

    public Quaternionf attitude = new Quaternionf();

    public float pitchRate;
    public float yawRate;
    public float rollRate;

    public boolean onGround;

    public double groundAltitude = Double.NaN;

    public int throttle;
    public float gearPosition;
    public float flapPosition;
    public float speedBrakePosition;
    public float afterburnerSpool;

    public double fuelMassOffsetKilograms;
    public double storesMassKilograms;
    public double storesDragArea;

    public boolean powered = true;
    public boolean angleOfAttackLimiter = true;

    public boolean heldAboveFloor;

    public String team = "";

    public String[] loadout = new String[0];

    public int missileCooldown;

    @Nullable
    public amrac.physics.aircraft.FlightState flightState;

    public VirtualAircraftState(UUID id, ResourceKey<Level> dimension,
                                Identifier type, Vec3 position,
                                Vec3 velocity, float yaw, float pitch,
                                @Nullable UUID pilotId,
                                @Nullable CompoundTag pilotSaved,
                                CompoundTag saved,
                                long since) {
        this.id = id;
        this.dimension = dimension;
        this.type = type;
        this.position = position;
        this.velocity = velocity;
        this.yaw = yaw;
        this.pitch = pitch;
        this.pilotId = pilotId;
        this.pilotSaved = pilotSaved;
        this.saved = saved;
        this.since = since;
    }
}

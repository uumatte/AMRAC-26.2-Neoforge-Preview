package amrac.weapons;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class VirtualMissileState {
    public final UUID id;
    public final ResourceKey<Level> dimension;

    public final MissileProfile profile;

    public Vec3 position;
    public Vec3 velocity;
    public Vec3 axis;
    /** World-space angular velocity in radians/second, including across handovers. */
    public final double[] angularVelocity = new double[3];

    @Nullable
    public final UUID targetId;
    @Nullable
    public final UUID ownerId;
    @Nullable
    public String ownerName;
    @Nullable
    public final UUID sourcePlaneId;

    public int age;
    public double travelled;
    public double lastLoadG;
    public boolean movePending;
    public boolean guidanceLost;

    public double closestMiss = Double.POSITIVE_INFINITY;

    public double lastTargetRange = Double.NaN;

    public boolean targetSeen;

    @Nullable
    public Vec3 lastTargetPosition;

    @Nullable
    public Vec3 lastTargetVelocity;

    public SeekerState seeker;

    public VirtualMissileState(UUID id, MissileProfile profile,
                               ResourceKey<Level> dimension,
                               Vec3 position, Vec3 velocity, Vec3 axis,
                               @Nullable UUID targetId, @Nullable UUID ownerId,
                               @Nullable UUID sourcePlaneId, int age,
                               double travelled, boolean guidanceLost,
                               @Nullable Vec3 lastTargetPosition,
                               @Nullable Vec3 lastTargetVelocity) {
        this.id = id;
        this.profile = profile == null
            ? MissileProfiles.defaultProfile() : profile;
        this.dimension = dimension;
        this.position = position;
        this.velocity = velocity;
        this.axis = axis;
        this.targetId = targetId;
        this.ownerId = ownerId;
        this.sourcePlaneId = sourcePlaneId;
        this.age = age;
        this.travelled = travelled;
        this.guidanceLost = guidanceLost;
        this.lastTargetPosition = lastTargetPosition;
        this.lastTargetVelocity = lastTargetVelocity;
        this.seeker = new SeekerState(lastTargetPosition, lastTargetVelocity);
    }
}

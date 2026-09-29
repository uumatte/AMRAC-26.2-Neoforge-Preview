package amrac.weapons;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Held by both the live missile and VirtualMissileState and copied whole at handover; a new seeker
 * field must go here or it is lost on handover.
 */
public final class SeekerState {
    public boolean active;

    public boolean tracking;

    @Nullable
    public Vec3 linkPosition;
    @Nullable
    public Vec3 linkVelocity;

    @Nullable
    public Vec3 cuePosition;
    @Nullable
    public Vec3 cueVelocity;

    @Nullable
    public Vec3 holdPosition;

    public double scanArc;

    @Nullable
    public Vec3 look;

    public int capturedDecoy = -1;

    public final Set<Integer> rolledDecoys = new HashSet<>();

    public SeekerPolicy.Loss loss = SeekerPolicy.Loss.NONE;

    public int reacquireTimer;

    public int confirmTicks;

    public int gimbalMemoryTicks;

    public boolean relockingAfterDecoy;

    public int cueAge;
    public int midcourseAge;
    public int cueUpdates;
    public double launcherRange = Double.NaN;

    public int flightTicks;
    @org.jetbrains.annotations.Nullable
    public net.minecraft.world.phys.Vec3 inertialAxis;

    public int linkAge;
    public double activatedAt = Double.NaN;
    public int ticksTracking;
    public int ticksInGate;
    public boolean spent;
    public double lastRange = Double.NaN;
    public double closestRange = Double.POSITIVE_INFINITY;
    public double furthestRange = 0.0D;

    public boolean everDecoyed;
    public int relocks;
    public boolean everTracked;

    public SeekerState() {
    }

    public SeekerState(@Nullable Vec3 position, @Nullable Vec3 velocity) {
        this.linkPosition = position;
        this.linkVelocity = velocity;
    }

    public String summary(SeekerType type) {
        StringBuilder out = new StringBuilder().append(type);
        if (type == SeekerType.ARH) {
            out.append(active ? String.format(" on at %.0f m", activatedAt)
                : " never switched on");
        }
        out.append(String.format(", held the aircraft %.1f s", ticksTracking / 20.0D));
        if (ticksInGate > 0) {
            out.append(String.format(", %.1f s in the velocity gate", ticksInGate / 20.0D));
        }
        if (type != SeekerType.IR) {
            out.append(String.format(", launcher last had the target %.1f s ago",
                linkAge / 20.0D));
        }
        if (everDecoyed) {
            out.append(", taken by chaff");
        }
        if (relocks > 0) {
            out.append(", relocked ").append(relocks)
                .append(relocks == 1 ? " time" : " times");
        }
        if (spent) {
            out.append(String.format(", went past it at %.0f m and lost it", closestRange));
        }
        out.append(", ends ").append(tracking ? "tracking" : loss);
        return out.toString();
    }

    public SeekerState copy() {
        SeekerState copy = new SeekerState(linkPosition, linkVelocity);
        copy.active = active;
        copy.tracking = tracking;
        copy.look = look;
        copy.cuePosition = cuePosition;
        copy.cueVelocity = cueVelocity;
        copy.holdPosition = holdPosition;
        copy.scanArc = scanArc;
        copy.capturedDecoy = capturedDecoy;
        copy.rolledDecoys.addAll(rolledDecoys);
        copy.loss = loss;
        copy.reacquireTimer = reacquireTimer;
        copy.confirmTicks = confirmTicks;
        copy.gimbalMemoryTicks = gimbalMemoryTicks;
        copy.relockingAfterDecoy = relockingAfterDecoy;
        copy.relocks = relocks;
        copy.everTracked = everTracked;
        copy.linkAge = linkAge;
        copy.flightTicks = flightTicks;
        copy.cueAge = cueAge;
        copy.midcourseAge = midcourseAge;
        copy.cueUpdates = cueUpdates;
        copy.launcherRange = launcherRange;
        copy.inertialAxis = inertialAxis;
        copy.activatedAt = activatedAt;
        copy.ticksTracking = ticksTracking;
        copy.ticksInGate = ticksInGate;
        copy.everDecoyed = everDecoyed;
        copy.spent = spent;
        copy.lastRange = lastRange;
        copy.closestRange = closestRange;
        copy.furthestRange = furthestRange;
        return copy;
    }
}

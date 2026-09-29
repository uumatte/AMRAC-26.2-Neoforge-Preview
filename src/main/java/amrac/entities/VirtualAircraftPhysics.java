package amrac.entities;

import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import amrac.MathUtil;
import amrac.entities.ai.AiCommand;
import amrac.physics.aircraft.AircraftControlInput;
import amrac.physics.aircraft.AircraftPhysicsResult;
import amrac.physics.aircraft.AircraftPhysicsState;
import amrac.physics.aircraft.FlapProfile;
import amrac.physics.aircraft.Vec3d;

/**
 * Virtual and live aircraft share AircraftFlightModelBridge and the same AiCommand decisions. Any
 * difference in how one layer flies changes the aircraft at the moment of handover.
 */
public final class VirtualAircraftPhysics {
    private static final double TICKS_PER_SECOND =
        AircraftFlightModelBridge.TICKS_PER_SECOND;

    private static final float SPOOL_RATE = 0.08F;

    private VirtualAircraftPhysics() {
    }

    public static boolean step(VirtualAircraftState state, AiCommand command) {
        return stepReporting(state, command) != null;
    }

    @org.jetbrains.annotations.Nullable
    public static AircraftPhysicsResult stepReporting(VirtualAircraftState state,
                                                      AiCommand command) {
        if (state.flightModelId == null
                || !AircraftFlightModelBridge.hasProfile(state.flightModelId)) {
            return null;
        }

        Vec3 forward = PlaneEntity.bodyDirection(state.attitude, 0.0F, 0.0F, 1.0F);
        Vec3 up = PlaneEntity.bodyDirection(state.attitude, 0.0F, 1.0F, 0.0F);
        Vec3 right = PlaneEntity.bodyDirection(state.attitude, 1.0F, 0.0F, 0.0F);
        Vec3 velocity = state.velocity;

        int throttle = command.throttle();
        boolean afterburner = command.afterburner(PlaneEntity.MAX_THROTTLE);
        state.throttle = throttle;
        state.afterburnerSpool = spool(state.afterburnerSpool, afterburner);

        state.gearPosition = MathUtil.approach(state.gearPosition,
            command.gearDown() ? 1.0F : 0.0F,
            1.0F / PlaneEntity.GEAR_TRAVEL_TICKS);
        var profile = amrac.physics.aircraft
            .FlightModelRegistry.instance().profile(state.flightModelId);
        FlapProfile flaps = profile == null
            ? FlapProfile.DEFAULT : profile.flaps();
        state.flapPosition = MathUtil.approach(state.flapPosition,
            command.flapsDown() ? 1.0F : 0.0F, 1.0F / flaps.travelTicks());
        amrac.weapons.MissileLoadout.StoresInertia stores =
            AircraftFlightModelBridge.storesInertia(profile, state.loadout);

        AircraftPhysicsState physics = new AircraftPhysicsState(
            metresPerSecond(velocity), vec3d(forward), vec3d(up), vec3d(right),
            state.position.y,
            state.pitchRate * TICKS_PER_SECOND,
            -state.yawRate * TICKS_PER_SECOND,
            state.rollRate * TICKS_PER_SECOND);

        AircraftControlInput input = AircraftControlInput.builder()
            .throttle(Math.min(throttle, PlaneEntity.MAX_THROTTLE)
                / (double) PlaneEntity.MAX_THROTTLE)
            .pitch(command.pitch())
            .yaw(command.yaw())
            .roll(command.roll())
            .gearPosition(state.gearPosition)
            .flapPosition(state.flapPosition)
            .speedBrakePosition(state.speedBrakePosition)
            .afterburnerSpool(state.afterburnerSpool)
            .fuelMassOffsetKilograms(state.fuelMassOffsetKilograms)
            .storesMassKilograms(state.storesMassKilograms)
            .storesDragArea(state.storesDragArea)
            .storesRollInertia(stores.rollInertia())
            .storesPitchInertia(stores.pitchInertia())
            .storesRollMoment(stores.rollMoment())
            .engineRunning(state.powered)
            .angleOfAttackLimiterEnabled(state.angleOfAttackLimiter)
            .groundContact(state.onGround)
            .groundReverse(false)
            .groundFrictionMultiplier(1.0D)
            .groundLateralGripMultiplier(1.0D)
            .build();

        AircraftPhysicsResult result = AircraftFlightModelBridge.step(
            state.flightModelId, physics, input, state.position.y,
            state.onGround);
        if (result == null) {
            return null;
        }

        state.velocity = blocksPerTick(result.velocity());
        state.pitchRate = (float) (result.pitchRate() / TICKS_PER_SECOND);
        state.yawRate = (float) (-result.yawRate() / TICKS_PER_SECOND);
        state.rollRate = (float) (result.rollRate() / TICKS_PER_SECOND);

        Quaternionf attitude = new Quaternionf(state.attitude);
        attitude.mul(Axis.ZP.rotationDegrees(state.rollRate));
        attitude.mul(Axis.XN.rotationDegrees(state.pitchRate));
        attitude.mul(Axis.YP.rotationDegrees(state.yawRate));
        attitude.mul(Axis.XN.rotationDegrees(
            (float) (result.pitchStabilityRate() / TICKS_PER_SECOND)));
        attitude.mul(Axis.YP.rotationDegrees(
            (float) (-result.yawStabilityRate() / TICKS_PER_SECOND)));
        state.attitude = MathUtil.normalizeQuaternion(attitude);

        state.position = state.position.add(state.velocity);

        if (Double.isFinite(state.groundAltitude)) {
            state.onGround = VirtualGroundPolicy.onStrip(state.position.y,
                state.groundAltitude);
            if (state.onGround) {
                state.position = new Vec3(state.position.x,
                    VirtualGroundPolicy.heldOnStrip(state.position.y,
                        state.groundAltitude),
                    state.position.z);
                state.velocity = new Vec3(state.velocity.x,
                    VirtualGroundPolicy.heldVerticalSpeed(state.velocity.y),
                    state.velocity.z);
            }
        }

        MathUtil.EulerAngles display = MathUtil.toEulerAngles(state.attitude);
        state.yaw = (float) display.yaw;
        state.pitch = (float) display.pitch;
        return result;
    }

    private static float spool(float current, boolean lit) {
        float target = lit ? 1.0F : 0.0F;
        if (current < target) {
            return Math.min(target, current + SPOOL_RATE);
        }
        return Math.max(target, current - SPOOL_RATE);
    }

    private static Vec3d metresPerSecond(Vec3 blocksPerTick) {
        return new Vec3d(blocksPerTick.x * TICKS_PER_SECOND,
            blocksPerTick.y * TICKS_PER_SECOND,
            blocksPerTick.z * TICKS_PER_SECOND);
    }

    private static Vec3 blocksPerTick(Vec3d metresPerSecond) {
        return new Vec3(metresPerSecond.x / TICKS_PER_SECOND,
            metresPerSecond.y / TICKS_PER_SECOND,
            metresPerSecond.z / TICKS_PER_SECOND);
    }

    private static Vec3d vec3d(Vec3 vector) {
        return new Vec3d(vector.x, vector.y, vector.z);
    }
}

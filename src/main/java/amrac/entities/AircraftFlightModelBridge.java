package amrac.entities;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import amrac.AmracMod;
import amrac.physics.aircraft.AircraftControlInput;
import amrac.physics.aircraft.AircraftEnvironment;
import amrac.physics.aircraft.AerodynamicsModel;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.AircraftPhysicsResult;
import amrac.physics.aircraft.AircraftPhysicsState;
import amrac.physics.aircraft.AtmosphereModel;
import amrac.physics.aircraft.EngineModel;
import amrac.physics.aircraft.FlightModel;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.physics.aircraft.InertiaProfile;
import amrac.physics.aircraft.Vec3d;
import amrac.weapons.MissileLoadout;

/**
 * The only conversion point between the flight model (SI, per second) and PlaneEntity (blocks, per
 * tick): speeds and rates x20, and the one sign flip for the entity's stored yaw rate. Don't
 * convert or negate on either side.
 */
public final class AircraftFlightModelBridge {
    public static final double TICKS_PER_SECOND = amrac.physics.TickRate.TICKS_PER_SECOND;

    private AircraftFlightModelBridge() {
    }

    public static boolean hasProfile(String profileId) {
        return FlightModelRegistry.instance().flightModel(profileId) != null;
    }

    public static MissileLoadout.StoresInertia storesInertia(
            @Nullable AircraftPhysicsProfile profile, String[] loadout) {
        InertiaProfile inertia = profile == null ? null : profile.inertia();
        return MissileLoadout.storesInertia(loadout,
            inertia == null ? null : inertia.stationSpan(),
            inertia == null ? 0.0D : inertia.storesArm());
    }

    /**
     * The flight model covers only the airborne part; ground handling (taxi, steering, friction,
     * ground attitude) stays in PlaneEntity.tickOnGround / tickRotation. Returns null when there is
     * no document, and the caller then holds its state.
     */
    public static AircraftPhysicsResult step(PlaneEntity plane, String profileId,
                                             PlaneEntity.TempMotionVars vars,
                                             boolean onGroundOrWater,
                                             Quaternionf attitude,
                                             float afterburnerSpool,
                                             boolean applyPitchCommand) {
        return stepEntity(plane, profileId, vars, onGroundOrWater, attitude,
            afterburnerSpool, applyPitchCommand);
    }

    public static double serviceCeiling(String profileId) {
        FlightModelRegistry registry = FlightModelRegistry.instance();
        AircraftPhysicsProfile profile = registry.profile(profileId);
        AtmosphereModel atmosphere = registry.atmosphere();
        if (profile == null || atmosphere == null) {
            return Double.MAX_VALUE;
        }
        Ceiling cached = CEILINGS.get(profileId);
        if (cached != null && cached.profile() == profile
                && cached.atmosphere() == atmosphere) {
            return cached.altitude();
        }
        double ceiling = measureCeiling(profile, atmosphere);
        CEILINGS.put(profileId, new Ceiling(profile, atmosphere, ceiling));
        return ceiling;
    }

    private static final java.util.Map<String, Ceiling> CEILINGS =
        new java.util.concurrent.ConcurrentHashMap<>();

    private record Ceiling(AircraftPhysicsProfile profile,
                           AtmosphereModel atmosphere, double altitude) {
    }

    private static double measureCeiling(AircraftPhysicsProfile profile,
                                         AtmosphereModel atmosphere) {
        AerodynamicsModel aero =
            new AerodynamicsModel(profile);
        EngineModel engine =
            new EngineModel(profile);
        double weight = profile.mass() * atmosphere.gravity();
        double best = 0.0D;
        double scale = atmosphere.speedScale();
        double sea = atmosphere.seaLevelY();
        for (int step = 0; step <= 100; step++) {
            double worldY = sea + (step * 240.0D - sea) * scale;
            if (canHoldLevel(aero, engine, atmosphere, profile, weight, worldY,
                    scale)) {
                best = worldY;
            }
        }
        return best;
    }

    private static boolean canHoldLevel(
            AerodynamicsModel aero,
            EngineModel engine,
            AtmosphereModel atmosphere, AircraftPhysicsProfile profile,
            double weight, double worldY, double scale) {
        double density = atmosphere.density(worldY);
        double atmospheric = atmosphere.atmosphericAltitude(worldY);
        double fade = profile.liftAltitudeMultiplier(atmospheric);
        if (fade <= 1.0E-9D) {
            return false;
        }
        for (int i = 0; i <= 120; i++) {
            double speed = (40.0D + i * 10.0D) * scale;
            double mach = atmosphere.mach(speed, worldY);
            double required = 2.0D * weight
                / Math.max(1.0E-9D, density * speed * speed * profile.wingArea());
            double angle = 30.0D;
            for (int a = 0; a <= 240; a++) {
                double candidate = 30.0D * a / 240.0D;
                if (aero.liftCoefficient(candidate) * fade >= required) {
                    angle = candidate;
                    break;
                }
            }
            if (aero.liftCoefficient(angle) * fade < required) {
                continue;
            }
            double drag = aero.drag(density, speed, angle, mach, 0.0D, 0.0D);
            if (engine.thrust(atmospheric, mach, 1.0D, 1.0F, true) >= drag) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static AircraftPhysicsResult step(String profileId,
                                             AircraftPhysicsState state,
                                             AircraftControlInput input,
                                             double altitude,
                                             boolean onGroundOrWater) {
        FlightModelRegistry registry = FlightModelRegistry.instance();
        registry.refresh();
        FlightModel model = registry.flightModel(profileId);
        if (model == null) {
            return null;
        }
        return model.step(state, input, AircraftEnvironment.sample(
            registry.atmosphere(), altitude, onGroundOrWater));
    }

    private static AircraftPhysicsResult stepEntity(
            PlaneEntity plane, String profileId,
            PlaneEntity.TempMotionVars vars, boolean onGroundOrWater,
            Quaternionf attitude, float afterburnerSpool,
            boolean applyPitchCommand) {
        FlightModelRegistry registry = FlightModelRegistry.instance();
        registry.refresh();

        FlightModel model = registry.flightModel(profileId);
        if (model == null) {
            return null;
        }
        AtmosphereModel atmosphere = registry.atmosphere();

        Vec3 motion = plane.getDeltaMovement();
        Vec3 forward = plane.getBodyDirection(attitude, 0.0F, 0.0F, 1.0F);
        Vec3 up = plane.getBodyDirection(attitude, 0.0F, 1.0F, 0.0F);
        Vec3 right = plane.getBodyDirection(attitude, 1.0F, 0.0F, 0.0F);

        AircraftPhysicsState state = new AircraftPhysicsState(
            toMetresPerSecond(motion), toVec3d(forward), toVec3d(up), toVec3d(right),
            plane.getY(),
            plane.getPitchAngularRate() * TICKS_PER_SECOND,
            -plane.getYawAngularRate() * TICKS_PER_SECOND,
            plane.getRollAngularRate() * TICKS_PER_SECOND);

        MissileLoadout.StoresInertia stores = storesInertia(model.profile(),
            plane.getLoadout());

        AircraftControlInput input = AircraftControlInput.builder()
            .throttle(plane.getThrottle() / (double) PlaneEntity.MAX_THROTTLE)
            .pitch(applyPitchCommand ? vars.pitchInput : 0.0F)
            .yaw(vars.yawInput)
            .roll(vars.rollInput)
            .gearPosition(plane.getGearPosition())
            .flapPosition(plane.getFlapPosition())
            .speedBrakePosition(plane.getSpeedBrakePosition())
            .afterburnerSpool(afterburnerSpool)
            .fuelMassOffsetKilograms(plane.getFuelMassOffsetKilograms())
            .storesMassKilograms(plane.getStoresMassKilograms())
            .storesDragArea(plane.getStoresDragArea())
            .storesRollInertia(stores.rollInertia())
            .storesPitchInertia(stores.pitchInertia())
            .storesRollMoment(stores.rollMoment())
            .engineRunning(plane.isPowered())
            .angleOfAttackLimiterEnabled(plane.isAngleOfAttackLimiterEnabled())
            .groundContact(onGroundOrWater)
            .groundReverse(vars.groundReverse)
            .groundFrictionMultiplier(vars.groundFrictionMultiplier)
            .wheelBrake(plane.isWheelBrakeOn() ? 1.0D : 0.0D)
            .build();

        AircraftEnvironment environment = AircraftEnvironment.sample(atmosphere,
            plane.getY(), onGroundOrWater);

        AircraftPhysicsResult result = model.step(state, input, environment);

        plane.setDeltaMovement(toBlocksPerTick(result.velocity()));

        float pitchRate = (float) (result.pitchRate() / TICKS_PER_SECOND);
        float yawRate = (float) (-result.yawRate() / TICKS_PER_SECOND);
        float rollRate = (float) (result.rollRate() / TICKS_PER_SECOND);
        if (applyPitchCommand) {
            plane.setBodyAngularRates(pitchRate, yawRate, rollRate);
        } else {
            plane.setBodyAngularRates(plane.getPitchAngularRate(), yawRate, rollRate);
        }

        vars.pitchStabilityRate =
            (float) (result.pitchStabilityRate() / TICKS_PER_SECOND);
        vars.yawStabilityRate =
            (float) (-result.yawStabilityRate() / TICKS_PER_SECOND);

        if (atmosphere.debugTelemetry() && !plane.level().isClientSide()) {
            int interval = Math.max(atmosphere.debugIntervalTicks(), 1);
            if (plane.tickCount % interval == 0) {
                AmracMod.LOGGER.info("[fm/{}] {}", profileId,
                    result.state().summary());
            }
        }
        return result;
    }

    private static Vec3d toMetresPerSecond(Vec3 blocksPerTick) {
        return new Vec3d(blocksPerTick.x() * TICKS_PER_SECOND,
            blocksPerTick.y() * TICKS_PER_SECOND,
            blocksPerTick.z() * TICKS_PER_SECOND);
    }

    private static Vec3 toBlocksPerTick(Vec3d metresPerSecond) {
        return new Vec3(metresPerSecond.x / TICKS_PER_SECOND,
            metresPerSecond.y / TICKS_PER_SECOND,
            metresPerSecond.z / TICKS_PER_SECOND);
    }

    private static Vec3d toVec3d(Vec3 vector) {
        return new Vec3d(vector.x(), vector.y(), vector.z());
    }
}

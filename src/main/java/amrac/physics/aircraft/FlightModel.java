package amrac.physics.aircraft;

public final class FlightModel {
    public static final double FIXED_TIME_STEP = amrac.physics.TickRate.SECONDS_PER_TICK;

    private static final double MINIMUM_AIRSPEED = 1.0E-3D;

    private static final double MAXIMUM_SPEED_LOSS_FRACTION = 0.5D;

    private final AircraftPhysicsProfile profile;
    private final AerodynamicsModel aerodynamics;
    private final EngineModel engine;

    public FlightModel(AircraftPhysicsProfile profile) {
        this.profile = profile;
        this.aerodynamics = new AerodynamicsModel(profile);
        this.engine = new EngineModel(profile);
    }

    public AircraftPhysicsProfile profile() {
        return profile;
    }

    public AerodynamicsModel aerodynamics() {
        return aerodynamics;
    }

    public EngineModel engine() {
        return engine;
    }

    public AircraftPhysicsResult step(AircraftPhysicsState state,
                                      AircraftControlInput input,
                                      AircraftEnvironment environment) {
        return step(state, input, environment, FIXED_TIME_STEP);
    }

    public AircraftPhysicsResult step(AircraftPhysicsState state,
                                      AircraftControlInput input,
                                      AircraftEnvironment environment,
                                      double dt) {
        double timeStep = Double.isFinite(dt) && dt > 0.0D ? dt : FIXED_TIME_STEP;

        Vec3d velocity = state.velocity();
        Vec3d forward = state.forward();
        Vec3d up = state.up();
        Vec3d right = state.right();

        double airspeed = velocity.length();
        double density = environment.density();
        double speedOfSound = environment.speedOfSound();
        double gravity = environment.gravity();
        double mass = Math.max(profile.mass() + input.fuelMassOffsetKilograms()
            + input.storesMassKilograms(), 1.0E-6D);
        double weight = mass * gravity;
        boolean onGround = environment.groundContact();

        double forwardSpeed = velocity.dot(forward);
        double normalSpeed = velocity.dot(up);
        double wingFlowSpeed = Math.hypot(forwardSpeed, normalSpeed);
        double angleOfAttack = AerodynamicsModel.angleOfAttackDegrees(velocity,
            forward, up);
        double sideSlip = AerodynamicsModel.sideSlipDegrees(velocity, right);
        double mach = speedOfSound > 1.0E-6D ? airspeed / speedOfSound : 0.0D;

        double dynamicPressure = AerodynamicsModel.dynamicPressure(density, airspeed);
        FlightControlProfile law = profile.flightControl();
        double pressureRatio = ControlAuthorityPolicy.pressureRatio(dynamicPressure,
            profile.referenceDynamicPressure());
        double authorityFloor = profile.minimumControlAuthority();

        double dynamicPressureAuthority = ControlAuthorityPolicy.axisAuthority(
            pressureRatio, law.pitchAuthorityExponent(), authorityFloor);
        double rollPressureAuthority = ControlAuthorityPolicy.axisAuthority(
            pressureRatio, law.rollAuthorityExponent(), authorityFloor)
            * ControlAuthorityPolicy.highPressureRollTaper(pressureRatio,
                profile.rollTaperOnsetRatio(), law.rollHighPressureTaper());
        double yawPressureAuthority = ControlAuthorityPolicy.axisAuthority(
            pressureRatio, law.yawAuthorityExponent(), authorityFloor);

        double pitchAuthority = dynamicPressureAuthority
            * aerodynamics.postStallAuthority(angleOfAttack,
                profile.postStallPitchAuthority());
        double rollAuthority = rollPressureAuthority
            * aerodynamics.postStallAuthority(angleOfAttack,
                profile.postStallRollAuthority());
        double yawAuthority = yawPressureAuthority
            * aerodynamics.postStallAuthority(angleOfAttack,
                profile.postStallYawAuthority());
        double controlAuthority = pitchAuthority;

        double throttle = input.throttle();
        double thrust = engine.thrust(environment.atmosphericAltitude(), mach, throttle,
            input.afterburnerSpool(), input.engineRunning());
        if (onGround) {
            thrust *= Math.max(profile.groundThrustFactor(), 0.0D);
            if (input.groundReverse() && throttle <= 0.0D) {
                thrust = -engine.staticThrust(environment.atmosphericAltitude()) *
                    0.05D;
            }
        }

        Vec3d force = forward.scale(thrust);

        force = force.add(new Vec3d(0.0D, -weight, 0.0D));

        double liftCoefficient = 0.0D;
        double dragCoefficient = 0.0D;
        double lift = 0.0D;
        double drag = 0.0D;
        double sideForce = 0.0D;
        double ceilingLiftMultiplier = 1.0D;

        if (airspeed > MINIMUM_AIRSPEED) {
            Vec3d velocityDirection = velocity.scale(1.0D / airspeed);

            ceilingLiftMultiplier = profile.liftAltitudeMultiplier(
                environment.atmosphericAltitude());
            liftCoefficient = aerodynamics.liftCoefficient(angleOfAttack) *
                ceilingLiftMultiplier;
            lift = aerodynamics.lift(density, wingFlowSpeed, angleOfAttack) *
                ceilingLiftMultiplier;

            double flapLiftCoefficient =
                profile.flaps().liftAt(input.flapPosition()) * ceilingLiftMultiplier;
            liftCoefficient += flapLiftCoefficient;
            lift += AerodynamicsModel.dynamicPressure(density, wingFlowSpeed)
                * profile.wingArea() * flapLiftCoefficient;

            double positiveLimit = weight * profile.maxPositiveG();
            double negativeLimit = weight * profile.maxNegativeG();
            lift = AerodynamicsModel.clamp(lift, negativeLimit, positiveLimit);

            Vec3d wingFlow = forward.scale(forwardSpeed).add(up.scale(normalSpeed));
            Vec3d liftDirection = liftDirection(up, wingFlow, wingFlowSpeed);
            force = force.add(liftDirection.scale(lift));

            double storesDrag = profile.wingArea() > 1.0E-6D
                ? input.storesDragArea() / profile.wingArea() : 0.0D;
            dragCoefficient = aerodynamics.totalDragCoefficient(angleOfAttack, mach,
                input.gearPosition(), input.speedBrakePosition()) +
                profile.flaps().dragAt(input.flapPosition()) +
                aerodynamics.sideSlipDragCoefficient(sideSlip) + storesDrag;
            drag = dynamicPressure * profile.wingArea() * dragCoefficient;
            force = force.add(velocityDirection.scale(-drag));

            sideForce = aerodynamics.sideForce(density, airspeed, sideSlip);
            force = force.add(right.scale(sideForce));
        }

        InertiaProfile airframe = profile.inertia();
        double fuelOffset = input.fuelMassOffsetKilograms();
        double rollInertia = airframe.rollInertiaWith(fuelOffset,
            input.storesRollInertia());
        double pitchInertia = airframe.pitchInertiaWith(fuelOffset,
            input.storesPitchInertia());
        double yawInertia = airframe.yawInertiaWith(fuelOffset,
            input.storesRollInertia(), input.storesPitchInertia());

        double wingPressure = AerodynamicsModel.dynamicPressure(density, wingFlowSpeed)
            * ceilingLiftMultiplier;
        CurveInterpolator liftCurve = profile.liftCoefficientCurve();
        boolean limiterEnabled = input.angleOfAttackLimiterEnabled();
        double limitAngle = profile.limiterAngleOfAttack();
        double negativeLimit = Math.abs(profile.maxNegativeG());

        double targetAngleUp = ManoeuvreLimitPolicy.targetAngleOfAttack(liftCurve,
            true, limitAngle, profile.maxPositiveG(), weight, wingPressure,
            profile.wingArea());
        double targetAngleDown = ManoeuvreLimitPolicy.targetAngleOfAttack(liftCurve,
            false, limitAngle, negativeLimit, weight, wingPressure,
            profile.wingArea());
        double loadAvailableUp = Math.min(profile.maxPositiveG(),
            ManoeuvreLimitPolicy.loadAvailable(wingPressure, profile.wingArea(),
                ManoeuvreLimitPolicy.liftMagnitude(liftCurve, true, targetAngleUp),
                weight));
        double loadAvailableDown = Math.min(negativeLimit,
            ManoeuvreLimitPolicy.loadAvailable(wingPressure, profile.wingArea(),
                ManoeuvreLimitPolicy.liftMagnitude(liftCurve, false, targetAngleDown),
                weight));

        double rawPitchRate = input.pitch() * profile.maxPitchRate() * pitchAuthority;
        double commandedPitchRate = rawPitchRate;
        if (limiterEnabled) {
            double capture = law.angleOfAttackCaptureSeconds();
            double band = limitAngle - profile.limiterSoftStartAngleOfAttack();
            commandedPitchRate = ManoeuvreLimitPolicy.limitPitchRate(rawPitchRate,
                ManoeuvreLimitPolicy.pullRateLimit(angleOfAttack, targetAngleUp,
                    band, loadAvailableUp, up.y, gravity, airspeed, capture),
                ManoeuvreLimitPolicy.pushRateLimit(angleOfAttack, targetAngleDown,
                    band, loadAvailableDown, up.y, gravity, airspeed, capture),
                law.limitSoftness());
        }
        boolean limiterActive = limiterEnabled
            && Math.abs(commandedPitchRate) < Math.abs(rawPitchRate) - 1.0E-6D;

        double normalAcceleration = mass > 1.0E-9D ? lift / mass : 0.0D;
        double rollBiasMoment = -input.storesRollMoment() * normalAcceleration
            * (gravity > 1.0E-9D ? AtmosphereModel.STANDARD_GRAVITY / gravity : 1.0D);

        double recoveryMoment = -Math.signum(angleOfAttack)
            * profile.postStall().recoveryMomentAt(angleOfAttack,
                profile.stallAngleOfAttack(), pressureRatio);

        double openLoop = profile.postStall().openLoopShare(angleOfAttack,
            profile.stallAngleOfAttack());

        AngularDynamics.Result pitching = AngularDynamics.step(state.pitchRate(),
            commandedPitchRate, limiterEnabled && Math.abs(rawPitchRate) > 1.0E-9D
                ? input.pitch() * commandedPitchRate / rawPitchRate : input.pitch(),
            openLoop, pitchInertia,
            airframe.pitchControlMoment() * pitchAuthority,
            airframe.pitchDamping() * pitchAuthority, recoveryMoment, 0.0D,
            responseSeconds(profile.pitchRateResponse()), profile.maxPitchRate(),
            timeStep);
        AngularDynamics.Result rolling = AngularDynamics.step(state.rollRate(),
            input.roll() * profile.maxRollRate() * rollAuthority,
            input.roll(), openLoop, rollInertia,
            airframe.rollControlMoment() * rollAuthority,
            airframe.rollDamping() * rollAuthority, rollBiasMoment,
            law.storesAsymmetryTrim(),
            responseSeconds(profile.rollRateResponse()), profile.maxRollRate(),
            timeStep);
        AngularDynamics.Result yawing = AngularDynamics.step(state.yawRate(),
            input.yaw() * profile.maxYawRate() * yawAuthority,
            input.yaw(), openLoop, yawInertia,
            airframe.yawControlMoment() * yawAuthority,
            airframe.yawDamping() * yawAuthority, 0.0D, 0.0D,
            responseSeconds(profile.yawRateResponse()), profile.maxYawRate(),
            timeStep);

        double pitchRate = pitching.rate();
        double rollRate = rolling.rate();
        double yawRate = yawing.rate();
        double authorityMargin = Math.min(pitching.authorityMargin(),
            Math.min(rolling.authorityMargin(), yawing.authorityMargin()));
        boolean controlSaturated = pitching.saturated() || rolling.saturated()
            || yawing.saturated();

        if (onGround) {
            force = addGroundReaction(force, velocity, right, mass, weight, lift,
                thrust, forward, input.groundFrictionMultiplier(),
                input.groundLateralGripMultiplier(), input.wheelBrake(),
                timeStep);
        }

        Vec3d acceleration = force.scale(1.0D / mass);
        if (!acceleration.isFinite()) {
            acceleration = Vec3d.ZERO;
        }
        Vec3d newVelocity = velocity.add(acceleration.scale(timeStep));
        newVelocity = guardSpeedLoss(velocity, newVelocity);
        if (!newVelocity.isFinite()) {
            newVelocity = Vec3d.ZERO;
        }

        double pitchStabilityRate = 0.0D;
        double yawStabilityRate = 0.0D;
        if (!onGround && airspeed > MINIMUM_AIRSPEED) {
            pitchStabilityRate = -AerodynamicsModel.clamp(
                angleOfAttack * profile.pitchStability() * dynamicPressureAuthority,
                -profile.maxPitchStabilityRate(), profile.maxPitchStabilityRate());
            if (forwardSpeed > 0.0D) {
                yawStabilityRate = AerodynamicsModel.clamp(
                    sideSlip * profile.yawStability() * dynamicPressureAuthority,
                    -profile.maxYawStabilityRate(), profile.maxYawStabilityRate());
            }
        }

        boolean structuralFailure = !onGround && newVelocity.length() >
            profile.structuralSpeedLimit(environment.atmosphericAltitude());

        FlightState.Builder telemetry = FlightState.builder();
        telemetry.worldY = environment.worldY();
        telemetry.atmosphericAltitude = environment.atmosphericAltitude();
        telemetry.density = density;
        telemetry.speedOfSound = speedOfSound;
        telemetry.airspeed = airspeed;
        telemetry.mach = mach;
        telemetry.angleOfAttack = angleOfAttack;
        telemetry.sideSlip = sideSlip;
        telemetry.dynamicPressure = dynamicPressure;
        telemetry.liftCoefficient = liftCoefficient;
        telemetry.dragCoefficient = dragCoefficient;
        telemetry.lift = lift;
        telemetry.drag = drag;
        telemetry.sideForce = sideForce;
        telemetry.thrust = thrust;
        telemetry.weight = weight;
        telemetry.loadFactor = weight > 1.0E-9D ? lift / weight : 0.0D;
        telemetry.controlAuthority = controlAuthority;
        telemetry.rollAuthority = rollAuthority;
        telemetry.yawAuthority = yawAuthority;
        telemetry.loadAvailable = loadAvailableUp;
        telemetry.targetAngleOfAttack = targetAngleUp;
        telemetry.rollInertia = rollInertia;
        telemetry.pitchInertia = pitchInertia;
        telemetry.authorityMargin = authorityMargin;
        telemetry.controlSaturated = controlSaturated;
        telemetry.stalled = airspeed > MINIMUM_AIRSPEED &&
            (aerodynamics.isStalled(angleOfAttack) || ceilingLiftMultiplier < 0.5D);
        telemetry.limiterActive = limiterActive;
        telemetry.groundContact = onGround;

        return new AircraftPhysicsResult(newVelocity, pitchRate, yawRate, rollRate,
            pitchStabilityRate, yawStabilityRate, telemetry.build(),
            structuralFailure);
    }

    private static Vec3d liftDirection(Vec3d up, Vec3d wingFlow, double wingFlowSpeed) {
        if (wingFlowSpeed <= AerodynamicsModel.MINIMUM_FLOW_SPEED) {
            return up;
        }
        Vec3d flowDirection = wingFlow.scale(1.0D / wingFlowSpeed);
        Vec3d direction = up.subtract(flowDirection.scale(up.dot(flowDirection)));
        return direction.lengthSquared() > 1.0E-10D ? direction.normalize() : Vec3d.ZERO;
    }

    private Vec3d addGroundReaction(Vec3d force, Vec3d velocity, Vec3d right,
                                    double mass, double weight, double lift,
                                    double thrust, Vec3d forward,
                                    double frictionMultiplier,
                                    double lateralGripMultiplier,
                                    double wheelBrake, double timeStep) {
        double liftSupport = Math.max(lift, 0.0D);
        double thrustSupport = Math.max(thrust * forward.y, 0.0D);
        double normalForce = weight > 1.0E-9D
            ? AerodynamicsModel.clamp(weight - liftSupport - thrustSupport, 0.0D, weight)
            : 0.0D;

        Vec3d horizontal = new Vec3d(velocity.x, 0.0D, velocity.z);
        double horizontalSpeed = horizontal.length();
        if (horizontalSpeed > 1.0E-4D) {
            double coefficient = profile.rollingResistance()
                + AerodynamicsModel.clamp(wheelBrake, 0.0D, 1.0D)
                    * profile.wheelBrakeFriction();
            double rolling = coefficient * frictionMultiplier * normalForce;
            if (timeStep > 1.0E-9D) {
                rolling = Math.min(rolling, mass * horizontalSpeed / timeStep);
            }
            force = force.add(horizontal.scale(-rolling / horizontalSpeed));
        }

        Vec3d horizontalRight = new Vec3d(right.x, 0.0D, right.z);
        if (horizontalRight.lengthSquared() > 1.0E-8D) {
            horizontalRight = horizontalRight.normalize();
            double lateralSpeed = velocity.dot(horizontalRight);
            double gripAcceleration = -lateralSpeed * profile.groundLateralGrip() *
                lateralGripMultiplier *
                (weight > 1.0E-9D ? normalForce / weight : 0.0D);
            force = force.add(horizontalRight.scale(gripAcceleration * mass));
        }
        return force;
    }

    private static double responseSeconds(double perTickResponse) {
        double response = AerodynamicsModel.clamp(perTickResponse, 0.01D, 1.0D);
        return FIXED_TIME_STEP / response;
    }

    private static Vec3d guardSpeedLoss(Vec3d before, Vec3d after) {
        double previous = before.length();
        if (previous <= MINIMUM_AIRSPEED) {
            return after;
        }
        double next = after.length();
        double floor = previous * (1.0D - MAXIMUM_SPEED_LOSS_FRACTION);
        if (next >= floor) {
            return after;
        }
        return next > 1.0E-9D ? after.scale(floor / next) : before.scale(floor / previous);
    }
}

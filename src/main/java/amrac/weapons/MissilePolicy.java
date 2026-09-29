package amrac.weapons;

public final class MissilePolicy {
    public static final double TICKS_PER_SECOND = amrac.physics.TickRate.TICKS_PER_SECOND;

    public static final int MISSILES_CARRIED = 4;
    public static final int LAUNCH_COOLDOWN_TICKS = 30;
    public static final int REARM_INTERVAL_TICKS = 60;

    public static final int LAUNCH_LOAD_LIMIT_TICKS = (int) Math.round(
        amrac.physics.missile.MissileFlightModel.LAUNCH_LOAD_LIMIT_SECONDS
            * TICKS_PER_SECOND);
    public static final double LAUNCH_LOAD_LIMIT_G =
        amrac.physics.missile.MissileFlightModel.LAUNCH_LOAD_LIMIT_G;
    public static final int BOOST_TICKS = 60;
    public static final double BOOST_ACCELERATION = 0.96D;
    public static final double MAX_SPEED = 60.0D;
    public static final double COAST_DRAG = 0.006D;

    public static final double GRAVITY =
        amrac.physics.aircraft.AtmosphereModel.STANDARD_GRAVITY /
            (TICKS_PER_SECOND * TICKS_PER_SECOND);

    public static double gravity() {
        return amrac.physics.aircraft.FlightModelRegistry.instance()
            .atmosphere().gravity() / (TICKS_PER_SECOND * TICKS_PER_SECOND);
    }

    public static final double FLIGHT_GRAVITY_FRACTION = 1.0D;

    public static final double MAX_LOAD_G = 25.0D;
    public static final double CORNER_SPEED = 40.0D;
    public static final double MIN_LOAD_G = 1.0D;
    public static final double INDUCED_DRAG_PER_G2 = 1.5E-5D;
    public static final double MAX_INDUCED_LOSS = 0.05D;
    public static final double NAVIGATION_CONSTANT = 4.0D;

    public static final int MAX_LEAD_TICKS = 60;

    public static final double MAX_TURN_RATE = 0.30D;
    public static final double SEEKER_GIMBAL_LIMIT = Math.toRadians(70.0D);

    public static final int MAX_LIFETIME_TICKS = 220;

    public static final double PROXIMITY_FUSE_RADIUS = 10.0D;
    public static final double ARMING_DISTANCE = 24.0D;
    public static final float EXPLOSION_POWER = 3.0F;

    public static final double MAX_LAUNCH_RANGE = 20000.0D;
    public static final double MIN_LAUNCH_RANGE = 24.0D;

    private MissilePolicy() {
    }

    public static double referenceAltitudeY() {
        var registry = amrac.physics.aircraft
            .FlightModelRegistry.instance();
        var profile = registry.missileProfile(
            amrac.physics.missile.MissileFlightModelIds.AIM120);
        double altitude = profile == null ? 5000.0D : profile.referenceAltitude();
        return altitude + registry.atmosphere().seaLevelY();
    }

    public static amrac.physics.missile.MissileFlightModel
            flightModel(MissileProfile profile) {
        return profile == null ? null : amrac.physics.aircraft
            .FlightModelRegistry.instance().missileFlightModel(profile.id);
    }

    public static boolean usesNewFlightModel(MissileProfile profile) {
        return flightModel(profile) != null;
    }

    public static double availableLoadG(MissileProfile profile, double speed,
                                        double worldY) {
        var model = flightModel(profile);
        if (model != null) {
            if (!Double.isFinite(speed) || speed <= 0.0D) {
                return 0.0D;
            }
            var atmosphere = amrac.physics.aircraft
                .FlightModelRegistry.instance().atmosphere();
            return model.availableLoadG(speed * TICKS_PER_SECOND, worldY, atmosphere);
        }
        return legacyAvailableLoadG(profile, speed);
    }

    public static double availableLoadG(MissileProfile profile, double speed) {
        return usesNewFlightModel(profile)
            ? availableLoadG(profile, speed, referenceAltitudeY())
            : legacyAvailableLoadG(profile, speed);
    }

    private static double legacyAvailableLoadG(MissileProfile profile,
                                               double speed) {
        if (!Double.isFinite(speed) || speed <= 0.0D) {
            return profile.minLoadG;
        }
        double ratio = speed / profile.cornerSpeed;
        double available = profile.maxLoadG * ratio * ratio;
        return Math.max(profile.minLoadG, Math.min(profile.maxLoadG, available));
    }

    public static double availableLoadG(double speed) {
        return availableLoadG(MissileProfiles.defaultProfile(), speed);
    }

    public static double maxLateralAcceleration(MissileProfile profile,
                                                double speed, double worldY) {
        var model = flightModel(profile);
        if (model != null) {
            return availableLoadG(profile, speed, worldY) * gravity();
        }
        return legacyLateralAcceleration(profile, speed);
    }

    public static double maxLateralAcceleration(MissileProfile profile,
                                                double speed) {
        return usesNewFlightModel(profile)
            ? maxLateralAcceleration(profile, speed, referenceAltitudeY())
            : legacyLateralAcceleration(profile, speed);
    }

    private static double legacyLateralAcceleration(MissileProfile profile,
                                                    double speed) {
        return legacyAvailableLoadG(profile, speed) * gravity();
    }

    public static double maxLateralAcceleration(double speed) {
        return maxLateralAcceleration(MissileProfiles.defaultProfile(), speed);
    }

    public static double loadFactorFromTurn(MissileProfile profile, double speed,
                                            double turnRadians) {
        if (!Double.isFinite(speed) || !Double.isFinite(turnRadians)) {
            return 0.0D;
        }
        return Math.abs(speed * turnRadians / gravity());
    }

    public static double loadFactorFromTurn(double speed, double turnRadians) {
        return loadFactorFromTurn(MissileProfiles.defaultProfile(), speed,
            turnRadians);
    }

    public static double inducedDragRetention(MissileProfile profile,
                                              double loadFactor) {
        if (!Double.isFinite(loadFactor)) {
            return 1.0D;
        }
        double n = Math.abs(loadFactor);
        return 1.0D - Math.min(profile.inducedDragPerG2 * n * n,
            profile.maxInducedLoss);
    }

    public static double speedAfterTurn(MissileProfile profile, double speed,
                                        double turnRadians, double worldY) {
        if (!Double.isFinite(speed) || !Double.isFinite(turnRadians)) {
            return speed;
        }
        double retained = speed;
        var model = flightModel(profile);
        if (model != null && Double.isFinite(profile.inducedDragFactor)
            && profile.inducedDragFactor > 0.0D) {
            double metresPerSecond = speed * TICKS_PER_SECOND;
            double lateral = metresPerSecond * Math.abs(turnRadians) *
                TICKS_PER_SECOND;
            double deceleration = model.inducedDragDeceleration(lateral,
                metresPerSecond, worldY, amrac.physics.aircraft
                    .FlightModelRegistry.instance().atmosphere(),
                profile.inducedDragFactor);
            retained = Math.max(0.0D, speed - deceleration /
                (TICKS_PER_SECOND * TICKS_PER_SECOND));
        }
        return retained * inducedDragRetention(profile,
            loadFactorFromTurn(profile, speed, turnRadians));
    }

    public static double inducedDragRetention(double loadFactor) {
        return inducedDragRetention(MissileProfiles.defaultProfile(), loadFactor);
    }

    public static boolean motorLit(MissileProfile profile, int age) {
        var model = flightModel(profile);
        if (model != null) {
            return model.profile().thrust(flightSeconds(age)) > 0.0D;
        }
        return age < profile.burnoutTick();
    }

    public static boolean motorLit(int age) {
        return motorLit(MissileProfiles.defaultProfile(), age);
    }

    public static double launchLoadLimitG(int age) {
        return amrac.physics.missile.MissileFlightModel.launchLoadLimitG(
            flightSeconds(age));
    }

    public static double flightSeconds(int age) {
        return Math.max(0, age - 1) / TICKS_PER_SECOND;
    }

    public static boolean launchLoadLimited(int age) {
        return Double.isFinite(launchLoadLimitG(age));
    }

    public static final double MAX_LOFT_ELEVATION = Math.toRadians(75.0D);

    public static final double LOFT_CEILING_FADE = 500.0D;

    public static double loftAngle(MissileProfile profile, double range,
                                   double worldY) {
        if (profile == null || !(profile.loftAngle > 0.0D) ||
            !Double.isFinite(range)) {
            return 0.0D;
        }
        double full = profile.loftFullRange;
        double minimum = profile.loftMinRange;
        if (!Double.isFinite(full) || !Double.isFinite(minimum) ||
            full <= minimum) {
            return 0.0D;
        }
        if (range <= minimum) {
            return 0.0D;
        }
        double taper = range >= full ? 1.0D
            : (range - minimum) / (full - minimum);

        double ceiling = profile.loftCeiling;
        if (Double.isFinite(ceiling) && Double.isFinite(worldY)) {
            double room = ceiling - worldY;
            if (room <= 0.0D) {
                return 0.0D;
            }
            double fade = LOFT_CEILING_FADE * amrac.physics.aircraft.SpeedScale.current();
            if (room < fade) {
                taper *= room / fade;
            }
        }
        return profile.loftAngle * taper;
    }

    public static boolean loftedLineOfSight(MissileProfile profile,
                                            double[] toTarget, double worldY,
                                            double[] out) {
        if (!finite(toTarget)) {
            return false;
        }
        double range = Math.sqrt(dot(toTarget, toTarget));
        raiseElevation(toTarget, loftAngle(profile, range, worldY), out);
        return finite(out);
    }

    private static void raiseElevation(double[] vector, double loft,
                                       double[] out) {
        double x = vector[0];
        double y = vector[1];
        double z = vector[2];
        out[0] = x;
        out[1] = y;
        out[2] = z;
        if (!(loft > 0.0D)) {
            return;
        }
        double length = Math.sqrt(x * x + y * y + z * z);
        double horizontal = Math.hypot(x, z);
        if (length < 1.0E-6D || horizontal < 1.0E-6D) {
            return;
        }
        double elevation = Math.atan2(y, horizontal) + loft;
        if (elevation > MAX_LOFT_ELEVATION) {
            elevation = MAX_LOFT_ELEVATION;
        }
        double horizontalLeg = Math.cos(elevation) * length;
        out[0] = x / horizontal * horizontalLeg;
        out[1] = Math.sin(elevation) * length;
        out[2] = z / horizontal * horizontalLeg;
    }

    public static boolean guidance(double[] toTarget, double[] relative,
                                   double speed, double[] out) {
        return guidance(MissileProfiles.defaultProfile(), toTarget, relative,
            speed, out);
    }

    public static boolean guidance(MissileProfile profile, double[] toTarget,
                                   double[] relative, double speed,
                                   double[] out) {
        return guidance(profile, toTarget, relative, speed, referenceAltitudeY(), out);
    }

    public static boolean guidance(MissileProfile profile, double[] toTarget,
                                   double[] relative, double speed,
                                   double worldY, double[] out) {
        if (!finite(toTarget) || !finite(relative) || !Double.isFinite(speed) ||
            speed <= 1.0E-6D) {
            return false;
        }
        double[] lofted = new double[3];
        if (loftedLineOfSight(profile, toTarget, worldY, lofted)) {
            toTarget = lofted;
        }
        double rangeSqr = dot(toTarget, toTarget);
        if (rangeSqr < 1.0E-9D) {
            return false;
        }

        double[] omega = {
            (toTarget[1] * relative[2] - toTarget[2] * relative[1]) / rangeSqr,
            (toTarget[2] * relative[0] - toTarget[0] * relative[2]) / rangeSqr,
            (toTarget[0] * relative[1] - toTarget[1] * relative[0]) / rangeSqr
        };

        double range = Math.sqrt(rangeSqr);
        double[] losUnit = {toTarget[0] / range, toTarget[1] / range,
            toTarget[2] / range};
        double closing = -dot(relative, losUnit);
        double gain = profile.navigationConstant
            * Math.max(closing, speed * 0.25D);

        double[] command = {
            gain * (omega[1] * losUnit[2] - omega[2] * losUnit[1]),
            gain * (omega[2] * losUnit[0] - omega[0] * losUnit[2]),
            gain * (omega[0] * losUnit[1] - omega[1] * losUnit[0])
        };
        if (!finite(command)) {
            return false;
        }

        double limit = maxLateralAcceleration(profile, speed, worldY);
        double magnitude = Math.sqrt(dot(command, command));
        if (magnitude > limit && magnitude > 1.0E-9D) {
            double scale = limit / magnitude;
            command[0] *= scale;
            command[1] *= scale;
            command[2] *= scale;
        }
        out[0] = command[0];
        out[1] = command[1];
        out[2] = command[2];
        return true;
    }

    public static double closestApproachFraction(double fromX, double fromY,
                                                 double fromZ, double toX,
                                                 double toY, double toZ,
                                                 double pointX, double pointY,
                                                 double pointZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(lengthSquared) || lengthSquared < 1.0E-12D) {
            return 0.0D;
        }
        double t = ((pointX - fromX) * dx + (pointY - fromY) * dy +
            (pointZ - fromZ) * dz) / lengthSquared;
        if (!Double.isFinite(t)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, t));
    }

    public static boolean steeringDirection(double[] toTarget,
                                            double[] targetVelocity,
                                            double missileSpeed, double[] out) {
        return steeringDirection(MissileProfiles.defaultProfile(), toTarget,
            targetVelocity, missileSpeed, out);
    }

    public static boolean steeringDirection(MissileProfile profile,
                                            double[] toTarget,
                                            double[] targetVelocity,
                                            double missileSpeed, double[] out) {
        return steeringDirection(profile, toTarget, targetVelocity, missileSpeed,
            referenceAltitudeY(), out);
    }

    public static boolean steeringDirection(MissileProfile profile,
                                            double[] toTarget,
                                            double[] targetVelocity,
                                            double missileSpeed, double worldY,
                                            double[] out) {
        if (!finite(toTarget) || !finite(targetVelocity) ||
            !Double.isFinite(missileSpeed)) {
            return false;
        }
        double range = Math.sqrt(dot(toTarget, toTarget));
        if (range < 1.0E-6D) {
            return false;
        }
        double lead = Math.min(range / Math.max(missileSpeed, 1.0E-3D),
            profile.maxLeadTicks);
        double[] aim = {
            toTarget[0] + targetVelocity[0] * lead,
            toTarget[1] + targetVelocity[1] * lead,
            toTarget[2] + targetVelocity[2] * lead
        };
        raiseElevation(aim, loftAngle(profile, range, worldY), aim);
        double length = Math.sqrt(dot(aim, aim));
        if (!finite(aim) || length < 1.0E-6D) {
            return false;
        }
        out[0] = aim[0] / length;
        out[1] = aim[1] / length;
        out[2] = aim[2] / length;
        return true;
    }

    public static double maxTurnRadians(MissileProfile profile, double speed,
                                        double worldY) {
        if (!Double.isFinite(speed) || speed < 1.0E-3D) {
            return profile.maxTurnRate;
        }
        return Math.min(maxLateralAcceleration(profile, speed, worldY) / speed,
            profile.maxTurnRate);
    }

    public static double maxTurnRadians(MissileProfile profile, double speed,
                                        double worldY, int age) {
        double turn = maxTurnRadians(profile, speed, worldY);
        double cap = launchLoadLimitG(age);
        if (Double.isFinite(cap) && Double.isFinite(speed) && speed >= 1.0E-3D) {
            turn = Math.min(turn, cap * gravity() / speed);
        }
        return turn;
    }

    public static double maxTurnRadians(MissileProfile profile, double speed) {
        if (!Double.isFinite(speed) || speed < 1.0E-3D) {
            return profile.maxTurnRate;
        }
        return Math.min(maxLateralAcceleration(profile, speed) / speed,
            profile.maxTurnRate);
    }

    public static boolean advanceAxial(MissileProfile profile, int age,
                                       double worldY, double[] velocity,
                                       double[] axis, double[] out) {
        if (!finite(velocity) || !finite(axis)) {
            return false;
        }

        var model = flightModel(profile);
        if (model != null) {
            var registry = amrac.physics.aircraft
                .FlightModelRegistry.instance();
            registry.refresh();
            var metresPerSecond = new amrac.physics.aircraft.Vec3d(
                velocity[0] * TICKS_PER_SECOND, velocity[1] * TICKS_PER_SECOND,
                velocity[2] * TICKS_PER_SECOND);
            var nose = new amrac.physics.aircraft.Vec3d(
                axis[0], axis[1], axis[2]);
            var next = model.advance(metresPerSecond, nose, worldY,
                flightSeconds(age), registry.atmosphere(),
                amrac.physics.missile.MissileFlightModel
                    .FIXED_TIME_STEP);
            out[0] = next.x / TICKS_PER_SECOND;
            out[1] = next.y / TICKS_PER_SECOND;
            out[2] = next.z / TICKS_PER_SECOND;
            return finite(out);
        }

        if (motorLit(profile, age)) {
            out[0] = velocity[0] + axis[0] * profile.boostAcceleration;
            out[1] = velocity[1] + axis[1] * profile.boostAcceleration;
            out[2] = velocity[2] + axis[2] * profile.boostAcceleration;
        } else {
            double retained = 1.0D - profile.coastDrag;
            out[0] = velocity[0] * retained;
            out[1] = velocity[1] * retained;
            out[2] = velocity[2] * retained;
        }
        out[1] -= gravity() * FLIGHT_GRAVITY_FRACTION;
        return finite(out);
    }

    public static double speedCap(MissileProfile profile) {
        return usesNewFlightModel(profile) ? -1.0D : profile.maxSpeed;
    }

    public static double maxTurnRadians(double speed) {
        return maxTurnRadians(MissileProfiles.defaultProfile(), speed);
    }

    public static boolean alignAndCharge(MissileProfile profile,
                                         double[] velocity, double[] axis,
                                         double[] out) {
        return alignAndCharge(profile, velocity, axis, null, out);
    }

    public static boolean alignAndCharge(MissileProfile profile,
                                         double[] velocity, double[] axis,
                                         double[] lateralCommand,
                                         double[] out) {
        return alignAndCharge(profile, velocity, axis, lateralCommand,
            referenceAltitudeY(), out);
    }

    public static boolean alignAndCharge(MissileProfile profile,
                                         double[] velocity, double[] axis,
                                         double[] lateralCommand, double worldY,
                                         double[] out) {
        return alignAndCharge(profile, velocity, axis, lateralCommand, worldY,
            null, out);
    }

    public static boolean alignAndCharge(MissileProfile profile,
                                         double[] velocity, double[] axis,
                                         double[] lateralCommand, double worldY,
                                         double[] load, double[] out) {
        return alignAndCharge(profile, velocity, axis, lateralCommand, worldY,
            load, Integer.MAX_VALUE, out);
    }

    public static boolean alignAndCharge(MissileProfile profile,
                                         double[] velocity, double[] axis,
                                         double[] lateralCommand, double worldY,
                                         double[] load, int age, double[] out) {
        if (!finite(velocity) || !finite(axis)) {
            return false;
        }
        double speed = Math.sqrt(dot(velocity, velocity));
        if (!Double.isFinite(speed) || speed < 1.0E-6D) {
            return false;
        }
        double[] direction = {velocity[0] / speed, velocity[1] / speed,
            velocity[2] / speed};
        double budget = maxTurnRadians(profile, speed, worldY, age);
        if (load != null && load.length > 0 && profile.maxLoadRate > 0.0D
            && Double.isFinite(load[0])) {
            double allowedG = Math.max(0.0D, load[0])
                + profile.maxLoadRate / TICKS_PER_SECOND;
            budget = Math.min(budget, allowedG * gravity() / speed);
        }

        if (lateralCommand != null && finite(lateralCommand)
            && dot(lateralCommand, lateralCommand) > 1.0E-18D) {
            double[] demanded = {velocity[0] + lateralCommand[0],
                velocity[1] + lateralCommand[1],
                velocity[2] + lateralCommand[2]};
            double[] afterGuidance = new double[3];
            if (turnToward(direction, demanded, budget, afterGuidance)) {
                budget -= angleBetween(direction, afterGuidance);
                direction = afterGuidance;
            }
        }

        if (budget > 1.0E-9D) {
            double[] afterFins = new double[3];
            if (turnToward(direction, axis, budget, afterFins)) {
                direction = afterFins;
            }
        }

        double turned = angleBetween(
            new double[] {velocity[0] / speed, velocity[1] / speed,
                velocity[2] / speed}, direction);
        double retained = speedAfterTurn(profile, speed, turned, worldY);
        if (load != null && load.length > 0) {
            load[0] = loadFactorFromTurn(profile, speed, turned);
        }
        out[0] = direction[0] * retained;
        out[1] = direction[1] * retained;
        out[2] = direction[2] * retained;
        return finite(out);
    }

    private static double angleBetween(double[] a, double[] b) {
        return Math.acos(Math.max(-1.0D, Math.min(1.0D, dot(a, b))));
    }

    public static boolean alignAndCharge(double[] velocity, double[] axis,
                                         double[] out) {
        return alignAndCharge(MissileProfiles.defaultProfile(), velocity, axis,
            out);
    }

    public static boolean turnToward(double[] from, double[] to,
                                     double maxRadians, double[] out) {
        if (!finite(from) || !finite(to) || !Double.isFinite(maxRadians)) {
            return false;
        }
        double fromLength = Math.sqrt(dot(from, from));
        double toLength = Math.sqrt(dot(to, to));
        if (fromLength < 1.0E-9D || toLength < 1.0E-9D) {
            return false;
        }
        double[] f = {from[0] / fromLength, from[1] / fromLength,
            from[2] / fromLength};
        double[] t = {to[0] / toLength, to[1] / toLength, to[2] / toLength};
        double cos = Math.max(-1.0D, Math.min(1.0D, dot(f, t)));
        double angle = Math.acos(cos);
        if (angle <= maxRadians || angle < 1.0E-9D) {
            out[0] = t[0];
            out[1] = t[1];
            out[2] = t[2];
            return true;
        }

        double[] perpendicular = {t[0] - f[0] * cos, t[1] - f[1] * cos,
            t[2] - f[2] * cos};
        double perpendicularLength = Math.sqrt(dot(perpendicular, perpendicular));
        if (perpendicularLength < 1.0E-9D) {
            perpendicular = Math.abs(f[1]) < 0.9D
                ? new double[] {-f[2], 0.0D, f[0]}
                : new double[] {1.0D, 0.0D, 0.0D};
            perpendicularLength = Math.sqrt(dot(perpendicular, perpendicular));
        }
        double sin = Math.sin(maxRadians);
        double cosStep = Math.cos(maxRadians);
        out[0] = f[0] * cosStep + perpendicular[0] / perpendicularLength * sin;
        out[1] = f[1] * cosStep + perpendicular[1] / perpendicularLength * sin;
        out[2] = f[2] * cosStep + perpendicular[2] / perpendicularLength * sin;
        return finite(out);
    }

    public static boolean withinGimbal(MissileProfile profile, double[] forward,
                                       double[] toTarget) {
        if (!finite(forward) || !finite(toTarget)) {
            return false;
        }
        double f = Math.sqrt(dot(forward, forward));
        double t = Math.sqrt(dot(toTarget, toTarget));
        if (f < 1.0E-9D || t < 1.0E-9D) {
            return false;
        }
        double cos = dot(forward, toTarget) / (f * t);
        return cos >= Math.cos(profile.seekerGimbalLimit);
    }

    public static boolean withinGimbal(double[] forward, double[] toTarget) {
        return withinGimbal(MissileProfiles.defaultProfile(), forward, toTarget);
    }

    public static boolean canLaunch(MissileProfile profile, double range,
                                    double cosOffBoresight) {
        return Double.isFinite(range) && Double.isFinite(cosOffBoresight) &&
            range >= profile.minLaunchRange && range <= profile.maxLaunchRange &&
            cosOffBoresight >= Math.cos(launchCone(profile));
    }

    public static double launchCone(MissileProfile profile) {
        return profile.seekerGimbalLimit;
    }

    public static boolean canLaunch(double range, double cosOffBoresight) {
        return canLaunch(MissileProfiles.defaultProfile(), range,
            cosOffBoresight);
    }

    public static void closestApproachPoint(double[] from, double[] to,
                                            double[] target,
                                            double[] targetVelocity,
                                            double[] out) {
        double t = closestApproachTime(from, to, target, targetVelocity);
        for (int axis = 0; axis < 3; axis++) {
            out[axis] = from[axis] + (to[axis] - from[axis]) * t;
        }
    }

    public static double closestApproachTime(double[] from, double[] to,
                                             double[] target,
                                             double[] targetVelocity) {
        return closestApproachFraction(
            from[0] - (target[0] - targetVelocity[0]),
            from[1] - (target[1] - targetVelocity[1]),
            from[2] - (target[2] - targetVelocity[2]),
            to[0] - target[0], to[1] - target[1], to[2] - target[2],
            0.0D, 0.0D, 0.0D);
    }

    public static double passDistanceSqr(double[] from, double[] to,
                                         double[] target,
                                         double[] targetVelocity,
                                         double[] halfExtents, double inflate,
                                         double[] out) {
        double t = closestApproachTime(from, to, target, targetVelocity);
        double skin = 0.0D;
        double grown = 0.0D;
        for (int axis = 0; axis < 3; axis++) {
            double point = from[axis] + (to[axis] - from[axis]) * t;
            out[axis] = point;
            double centreThen = target[axis] - targetVelocity[axis] * (1.0D - t);
            double off = Math.abs(point - centreThen);
            double outside = Math.max(0.0D, off - halfExtents[axis]);
            double outsideGrown = Math.max(0.0D, off - halfExtents[axis] - inflate);
            skin += outside * outside;
            grown += outsideGrown * outsideGrown;
        }
        out[3] = Math.sqrt(skin);
        return grown;
    }

    public static boolean shouldDetonate(MissileProfile profile,
                                         double distanceToTarget,
                                         double travelled) {
        return travelled >= profile.armingDistance &&
            distanceToTarget <= profile.proximityFuseRadius;
    }

    public static boolean shouldDetonate(double distanceToTarget,
                                         double travelled) {
        return shouldDetonate(MissileProfiles.defaultProfile(),
            distanceToTarget, travelled);
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static boolean finite(double[] v) {
        return v.length == 3 && Double.isFinite(v[0]) && Double.isFinite(v[1]) &&
            Double.isFinite(v[2]);
    }
}

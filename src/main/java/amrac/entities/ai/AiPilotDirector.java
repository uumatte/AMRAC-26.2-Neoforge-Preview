package amrac.entities.ai;

/**
 * AI decisions are a pure function called by both the live and the virtual layer (AiPilotBrain only
 * reads state and applies the result); don't add a decision branch in either layer.
 */
public final class AiPilotDirector {
    public static final double COURSE_GOAL_DISTANCE = 12000.0D;

    public static final double GUN_TRACKING_COSINE = 0.985D;

    public static final double GUN_RANGE = 640.0D;

    public static final double RECOVERY_PITCH_BIAS = 0.35D;

    public static final double GLIDE_BANK = Math.toRadians(40.0D);

    private AiPilotDirector() {
    }

    public static AiCommand decide(AiSituation situation) {
        return decide(situation, null);
    }

    public static AiCommand decide(AiSituation situation, @org.jetbrains.annotations.Nullable AiThreat threat) {
        if (situation == null) {
            return AiCommand.idle(AiFlightPhase.IDLE);
        }
        if (!situation.missionActive()) {
            return AiCommand.idle(AiFlightPhase.IDLE);
        }
        if (situation.onGround()) {
            return takeoff(situation);
        }
        if (!situation.powered()) {
            return emergency(situation);
        }
        return fly(situation, threat);
    }

    private static AiCommand takeoff(AiSituation situation) {
        if (!situation.powered()) {
            return AiCommand.idle(AiFlightPhase.IDLE);
        }
        float pitch = AiPilotPolicy.readyToRotate(situation.speed()) ? 1.0F : 0.0F;
        return new AiCommand(throttle(situation), pitch, 0.0F, 0.0F,
            true, true, false, false, AiFlightPhase.TAKEOFF);
    }

    private static AiCommand emergency(AiSituation situation) {
        double bank = AiPilotPolicy.bankAngle(situation.rightY(), situation.upY());
        boolean upset = AiPilotPolicy.inverted(situation.upY());

        double[] goal = goal(situation, AiFlightPhase.EMERGENCY);
        double heading = AiPilotPolicy.headingError(
            situation.forwardX(), situation.forwardZ(),
            goal[0] - situation.x(), goal[2] - situation.z());
        double glideBank = AiPilotSettings.current().glideBank;
        double wanted = Math.max(-glideBank, Math.min(glideBank,
            AiPilotPolicy.desiredBank(heading, bank)));
        float roll = upset ? AiPilotPolicy.upsetRoll(situation.rightY())
            : AiPilotPolicy.rollInput(
                AiPilotPolicy.affordableBank(wanted, situation.speed()),
                bank, situation.rollRate());
        float pitch = upset ? 0.0F : AiPilotPolicy.climbAnglePitch(
            AiPilotPolicy.flightPathAngle(situation.velocityY(),
                situation.speed()),
            -Math.toRadians(6.0D), bank);
        return new AiCommand(0, pitch, 0.0F, roll, false, false, false, false,
            AiFlightPhase.EMERGENCY);
    }

    private static AiCommand fly(AiSituation situation, @org.jetbrains.annotations.Nullable AiThreat threat) {
        AiFlightPhase phase = phase(situation);
        boolean turnBack = turningBack(situation, new double[2]);
        double[] goal = goal(situation, phase, threat);

        double heading = AiPilotPolicy.headingError(
            situation.forwardX(), situation.forwardZ(),
            goal[0] - situation.x(), goal[2] - situation.z());
        double bank = AiPilotPolicy.bankAngle(situation.rightY(), situation.upY());
        boolean upset = AiPilotPolicy.inverted(situation.upY());

        boolean notching = phase == AiFlightPhase.EGRESS && threat != null
            && !turnBack;
        double limit = AiPilotPolicy.bankLimit(heading, bank,
            situation.maxLoadG(), turnBack
                || (notching && Math.abs(heading) > AiPilotPolicy.NOTCH_HARD_ERROR));
        float roll = upset
            ? AiPilotPolicy.upsetRoll(situation.rightY())
            : AiPilotPolicy.rollInput(
                notching
                    ? clampBank(AiPilotPolicy.notchBank(heading, bank), limit)
                    : engageBank(situation, phase, turnBack,
                        clampBank(AiPilotPolicy.desiredBank(heading, bank), limit),
                        heading, goal[1]),
                bank, situation.rollRate());

        float pitch = upset ? 0.0F : pitchCommand(situation, goal[1], bank);
        if (turnBack && !upset) {
            pitch = AiPilotPolicy.turnBackPitch(pitch, heading, bank,
                situation.speedBlocksPerSecond());
        } else if (notching && !upset) {
            pitch = AiPilotPolicy.notchPitch(pitch, heading, bank,
                situation.speedBlocksPerSecond());
        } else if (phase == AiFlightPhase.ENGAGE && !upset) {
            pitch = AiPilotPolicy.hardTurnPitch(pitch, heading, bank,
                situation.speedBlocksPerSecond(), situation.maxLoadG(),
                situation.loadG());
        }
        if (!upset && situation.rank().evades() && AiNotchPolicy.lastDitch(threat,
                AiPilotSettings.current().lastDitchSeconds, situation.x(),
                situation.y(), situation.z(), situation.velocityX(),
                situation.velocityY(), situation.velocityZ())) {
            pitch = 1.0F;
        }

        boolean gunSolution = gunSolution(situation, phase);
        boolean missileSolution = missileSolution(situation, phase);

        return new AiCommand(turnBack ? turnBackThrottle(situation)
            : throttle(situation), pitch, 0.0F, roll,
            false, false, gunSolution, missileSolution, phase);
    }

    public static AiFlightPhase phase(AiSituation situation) {
        if (AiAltitudePolicy.belowFloor(situation.y(), !situation.departureComplete())) {
            return AiFlightPhase.RECOVER;
        }
        if (!situation.departureComplete()) {
            if (situation.underMissileThreat() && situation.rank().evades()) {
                return AiFlightPhase.EGRESS;
            }
            if (climbShotInRange(situation)) {
                return AiFlightPhase.ENGAGE;
            }
            return AiFlightPhase.CLIMB;
        }
        if (situation.underMissileThreat() && situation.rank().evades()) {
            return AiFlightPhase.EGRESS;
        }
        if (situation.hasTarget()) {
            return AiFlightPhase.ENGAGE;
        }
        return AiFlightPhase.CRUISE;
    }

    public static double[] goal(AiSituation situation, AiFlightPhase phase) {
        return goal(situation, phase, null);
    }

    public static double[] goal(AiSituation situation, AiFlightPhase phase,
                                @org.jetbrains.annotations.Nullable AiThreat threat) {
        double altitude = situation.assignedAltitude();

        double[] turnBack = new double[2];
        if (turningBack(situation, turnBack)) {
            return new double[] {turnBack[0], altitude, turnBack[1]};
        }
        double[] course = heading(situation);

        return switch (phase) {
            case CLIMB, RECOVER -> ahead(situation, course, altitude);
            case EGRESS -> threat != null
                ? notch(situation, course, threat, altitude)
                : away(situation, course, altitude);
            case ENGAGE -> intercept(situation, altitude);
            default -> patrol(situation, altitude);
        };
    }

    static boolean climbShotInRange(AiSituation situation) {
        return situation.rank().attacks() && situation.hasTarget()
            && situation.targetIsAircraft()
            && situation.targetRange() <= situation.rank().engagementRange();
    }

    private static double[] heading(AiSituation situation) {
        double vx = situation.velocityX();
        double vz = situation.velocityZ();
        if (vx * vx + vz * vz > 1.0E-6D) {
            return new double[] {vx, vz};
        }
        return new double[] {situation.forwardX(), situation.forwardZ()};
    }

    private static double[] ahead(AiSituation situation, double[] course,
                                  double altitude) {
        double length = Math.max(1.0E-6D, Math.hypot(course[0], course[1]));
        return new double[] {
            situation.x() + course[0] / length * AiPilotSettings.current().courseGoalDistance,
            altitude,
            situation.z() + course[1] / length * AiPilotSettings.current().courseGoalDistance};
    }

    static final double NOTCH_ALTITUDE_MARGIN = 1500.0D;

    static double notchAltitude(double y, double assigned) {
        return Double.isFinite(assigned)
            ? Math.min(y, assigned + NOTCH_ALTITUDE_MARGIN
                * amrac.physics.aircraft.SpeedScale.current()) : y;
    }

    private static double[] notch(AiSituation situation, double[] course,
                                  AiThreat threat, double altitude) {
        double length = Math.max(1.0E-6D, Math.hypot(course[0], course[1]));
        double trim = threat.trim() + AiNotchPolicy.verticalTrim(
            situation.x(), situation.y(), situation.z(), situation.velocityX(),
            situation.velocityY(), situation.velocityZ(), threat.x(), threat.y(),
            threat.z(), threat.side() != 0 ? threat.side()
                : AiNotchPolicy.sideFor(course[0], course[1],
                    threat.x() - situation.x(), threat.z() - situation.z()),
            threat.notchError());
        double[] goal = AiNotchPolicy.goal(situation.x(), situation.z(),
            course[0] / length, course[1] / length, threat.x(), threat.z(),
            threat.notchError(), threat.side(), trim,
            notchAltitude(situation.y(), altitude),
            AiPilotSettings.current().courseGoalDistance);
        return goal != null ? goal : ahead(situation, course, altitude);
    }

    private static double[] away(AiSituation situation, double[] course,
                                 double altitude) {
        if (!situation.hasTarget()) {
            return ahead(situation, course, altitude);
        }
        double dx = situation.x() - situation.targetX();
        double dz = situation.z() - situation.targetZ();
        double length = Math.hypot(dx, dz);
        if (!(length > 1.0E-6D)) {
            return ahead(situation, course, altitude);
        }
        return new double[] {
            situation.x() + dx / length * AiPilotSettings.current().courseGoalDistance,
            altitude,
            situation.z() + dz / length * AiPilotSettings.current().courseGoalDistance};
    }

    private static double[] intercept(AiSituation situation, double altitude) {
        double[] out = new double[3];
        AiPilotPolicy.interceptGoal(
            new double[] {situation.targetX(), situation.targetY(),
                situation.targetZ()},
            new double[] {situation.targetVelocityX(), situation.targetVelocityY(),
                situation.targetVelocityZ()},
            new double[] {situation.x(), situation.y(), situation.z()},
            new double[] {situation.velocityX(), situation.velocityY(),
                situation.velocityZ()},
            80.0D, out);
        double height = situation.targetIsAircraft()
            ? AiAltitudePolicy.engageAltitude(altitude, situation.targetY(),
                situation.targetRange(), situation.rank().engagementRange(),
                situation.serviceCeiling())
            : altitude;
        if (!situation.departureComplete()) {
            height = Math.max(height, situation.y());
        }
        return new double[] {out[0], height, out[2]};
    }

    private static double[] patrol(AiSituation situation, double altitude) {
        double[] circuit = new double[2];
        AiPilotPolicy.patrolGoal(situation.stationX(), situation.stationZ(),
            situation.x(), situation.z(), circuit);
        return new double[] {circuit[0], altitude, circuit[1]};
    }

    private static float pitchCommand(AiSituation situation, double targetAltitude,
                                      double bank) {
        float pitch = AiPilotPolicy.altitudeHoldPitch(
            targetAltitude - situation.y(), situation.velocityY(),
            situation.speed(), bank);
        double urgency = AiAltitudePolicy.recoveryUrgency(situation.y(),
            !situation.departureComplete());
        if (urgency <= 0.0D) {
            return pitch;
        }
        return (float) Math.min(AiPilotPolicy.MAX_PITCH,
            pitch + RECOVERY_PITCH_BIAS * urgency);
    }

    private static double engageBank(AiSituation situation, AiFlightPhase phase,
                                     boolean turnBack, double wanted,
                                     double heading, double goalAltitude) {
        double affordable = AiPilotPolicy.affordableBank(wanted,
            situation.speed(), situation.structuralSpeedLimit());
        if (phase != AiFlightPhase.ENGAGE || turnBack) {
            return affordable;
        }
        double share = AiPilotPolicy.hardTurnShare(heading,
            situation.speedBlocksPerSecond());
        double cap = AiPilotPolicy.climbingBankLimit(goalAltitude - situation.y());
        double banked = affordable + (wanted - affordable) * share;
        return Math.max(-cap, Math.min(cap, banked));
    }

    private static double clampBank(double wanted, double limit) {
        if (!Double.isFinite(wanted)) {
            return 0.0D;
        }
        return Math.abs(wanted) < AiPilotSettings.current().maxBank - 1.0E-6D
            ? wanted : Math.copySign(limit, wanted);
    }

    private static boolean turningBack(AiSituation situation, double[] turnBack) {
        double[] course = heading(situation);
        return WorldBoundaryPolicy.avoidanceGoal(
            situation.boundaryMinX(), situation.boundaryMaxX(),
            situation.boundaryMinZ(), situation.boundaryMaxZ(),
            situation.x(), situation.z(), course[0], course[1],
            turnBack);
    }

    private static int turnBackThrottle(AiSituation situation) {
        return AiPilotPolicy.throttleInput(situation.speed(),
            AiPilotSettings.current().borderTurnBackSpeed / 20.0D,
            situation.throttle(), situation.maxThrottle());
    }

    private static int throttle(AiSituation situation) {
        double fraction = AiThrottlePolicy.maximumThrottleFraction(
            situation.speedBlocksPerSecond(),
            situation.smoothedAcceleration(),
            situation.structuralSpeedLimit() * 20.0D);
        return AiThrottlePolicy.commandedThrottle(fraction,
            situation.maxThrottle(), situation.rank().usesAfterburner());
    }

    public static boolean gunSolution(AiSituation situation, AiFlightPhase phase) {
        if (!situation.rank().attacks() || phase != AiFlightPhase.ENGAGE
                || !situation.hasTarget()) {
            return false;
        }
        double range = situation.targetRange();
        if (!(range <= AiPilotSettings.current().gunRange)) {
            return false;
        }
        double dx = situation.targetX() - situation.x();
        double dz = situation.targetZ() - situation.z();
        double dy = situation.targetY() - situation.y();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 1.0E-6D)) {
            return false;
        }
        double forwardY = Math.copySign(Math.sqrt(Math.max(0.0D, 1.0D
            - situation.forwardX() * situation.forwardX()
            - situation.forwardZ() * situation.forwardZ())),
            situation.velocityY());
        double cosine = (dx * situation.forwardX() + dy * forwardY
            + dz * situation.forwardZ()) / length;
        return cosine >= AiPilotSettings.current().gunTrackingCosine;
    }

    public static boolean missileSolution(AiSituation situation,
                                          AiFlightPhase phase) {
        return AiPilotCombatPolicy.whyNoLaunch(situation, phase) == null;
    }
}

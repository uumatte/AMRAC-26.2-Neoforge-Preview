package amrac.entities.ai;

/**
 * Control signs follow the client: pitch + is nose up, roll + is left, yaw + is left (a target on
 * the right gives negative roll), as for player input and PlaneEntity.
 */
public final class AiPilotPolicy {
    public static final double MAX_BANK = Math.toRadians(60.0D);

    public static final double TURN_BACK_LOAD_FRACTION = 0.9D;

    public static double bankForLoad(double loadG) {
        return bankForLoad(loadG, AiPilotSettings.current().hardTurnLoad);
    }

    public static double bankForTurnBack(double loadG) {
        return bankForLoad(loadG, AiPilotSettings.current().borderTurnBackLoad);
    }

    private static double bankForLoad(double loadG, double cap) {
        AiPilotSettings settings = AiPilotSettings.current();
        if (!Double.isFinite(loadG) || loadG <= 1.0D) {
            return settings.maxBank;
        }
        double usable = Math.max(1.0D, Math.min(
            loadG * settings.turnBackLoadFraction, cap));
        double bank = Math.acos(clamp(1.0D / usable, 0.0D, 1.0D));
        return Math.max(settings.maxBank, bank);
    }

    public static final double HARD_TURN_LOAD = 10.0D;

    public static final double TURN_BACK_LOAD = 5.0D;

    public static final double TURN_BACK_PULL = 0.3D;

    public static final double TURN_BACK_PULL_MIN_SPEED = 150.0D;

    static final double TURN_BACK_PULL_MIN_BANK = Math.toRadians(30.0D);
    static final double TURN_BACK_PULL_BANK_RAMP = Math.toRadians(30.0D);

    static final double TURN_BACK_PULL_SPEED_RAMP = 0.3D;

    static final double TURN_BACK_PULL_MIN_ERROR = Math.toRadians(60.0D);

    public static final double NOTCH_BANK_PER_HEADING_ERROR = 12.8D;

    public static double notchBank(double headingError, double currentBank) {
        if (!Double.isFinite(headingError)) {
            return 0.0D;
        }
        if (Math.abs(headingError) >= REVERSAL_AMBIGUITY) {
            return desiredBank(headingError, currentBank);
        }
        double maxBank = AiPilotSettings.current().maxBank;
        return clamp(headingError * NOTCH_BANK_PER_HEADING_ERROR, -maxBank, maxBank);
    }

    public static float notchPitch(float pitch, double headingError, double bank,
                                   double speedBps) {
        AiPilotSettings settings = AiPilotSettings.current();
        if (settings.borderTurnBackPull <= 0.0D || !Double.isFinite(headingError)
                || !Double.isFinite(bank) || !Double.isFinite(speedBps)) {
            return pitch;
        }
        double errorShare = clamp((Math.abs(headingError) - NOTCH_PULL_MIN_ERROR)
            / NOTCH_PULL_ERROR_RAMP, 0.0D, 1.0D);
        double bankShare = clamp((Math.abs(bank) - TURN_BACK_PULL_MIN_BANK)
            / TURN_BACK_PULL_BANK_RAMP, 0.0D, 1.0D);
        double minSpeed = settings.borderTurnBackPullMinSpeed;
        double speedShare = clamp((speedBps - minSpeed)
            / Math.max(1.0D, minSpeed * TURN_BACK_PULL_SPEED_RAMP), 0.0D, 1.0D);
        double scale = errorShare * bankShare * speedShare;
        if (scale <= 0.0D) {
            return pitch;
        }
        return (float) Math.max(pitch, settings.borderTurnBackPull * scale);
    }

    public static final double NOTCH_PULL_MIN_ERROR = Math.toRadians(2.0D);
    static final double NOTCH_PULL_ERROR_RAMP = Math.toRadians(10.0D);
    public static final double NOTCH_HARD_ERROR = Math.toRadians(20.0D);

    public static final double HARD_TURN_PULL = 1.0D;

    static final double HARD_TURN_PULL_MIN_ERROR = Math.toRadians(5.0D);
    static final double HARD_TURN_PULL_ERROR_RAMP = Math.toRadians(15.0D);

    public static float hardTurnPitch(float pitch, double headingError,
                                      double bank, double speedBps,
                                      double loadG, double measuredG) {
        AiPilotSettings settings = AiPilotSettings.current();
        if (settings.hardTurnPull <= 0.0D || !Double.isFinite(headingError)
                || !Double.isFinite(bank) || !Double.isFinite(speedBps)) {
            return pitch;
        }
        double errorShare = clamp((Math.abs(headingError) - HARD_TURN_PULL_MIN_ERROR)
            / HARD_TURN_PULL_ERROR_RAMP, 0.0D, 1.0D);
        double bankShare = clamp((Math.abs(bank) - TURN_BACK_PULL_MIN_BANK)
            / TURN_BACK_PULL_BANK_RAMP, 0.0D, 1.0D);
        double minSpeed = settings.borderTurnBackPullMinSpeed;
        double speedShare = clamp((speedBps - minSpeed)
            / Math.max(1.0D, minSpeed * TURN_BACK_PULL_SPEED_RAMP), 0.0D, 1.0D);
        double scale = errorShare * bankShare * speedShare
            * hardTurnEnergyShare(speedBps);
        if (scale <= 0.0D) {
            return pitch;
        }
        double level = HARD_TURN_LEVEL_MARGIN
            / Math.max(Math.cos(Math.min(Math.abs(bank), Math.PI / 2.0D)), 1.0E-3D);
        double target = Math.min(hardTurnTargetLoad(loadG), level);
        double stick = 1.0D;
        if (Double.isFinite(measuredG)) {
            if (measuredG > target + HARD_TURN_OVERSHOOT_G) {
                return (float) (pitch + (Math.min(pitch, 0.0D) - pitch) * scale);
            }
            stick = clamp((target + HARD_TURN_OVERSHOOT_G - measuredG)
                    * HARD_TURN_STICK_PER_G,
                0.0D, 1.0D);
        }
        double pulled = Math.max(pitch, settings.hardTurnPull * stick);
        return (float) (pitch + (pulled - pitch) * scale);
    }

    static final double HARD_TURN_LEVEL_MARGIN = 1.1D;

    static final double HARD_TURN_OVERSHOOT_G = 0.75D;

    public static double signedLoad(double vx, double vy, double vz,
                                    double px, double py, double pz,
                                    double upX, double upY, double upZ) {
        double g = amrac.physics.aircraft.SpeedScale.gravityBlocksPerTickSquared();
        double load = ((vx - px) * upX + (vy - py + g) * upY + (vz - pz) * upZ) / g;
        return Double.isFinite(load) ? load : Double.NaN;
    }

    static final double HARD_TURN_STICK_PER_G = 0.5D;

    public static double hardTurnTargetLoad(double loadG) {
        AiPilotSettings settings = AiPilotSettings.current();
        if (!Double.isFinite(loadG) || loadG <= 1.0D) {
            return settings.hardTurnLoad;
        }
        return Math.max(1.0D, Math.min(loadG * settings.turnBackLoadFraction,
            settings.hardTurnLoad));
    }

    public static double measuredLoad(double vx, double vy, double vz,
                                      double px, double py, double pz) {
        double g = amrac.physics.aircraft.SpeedScale.gravityBlocksPerTickSquared();
        double ax = vx - px, ay = vy - py + g, az = vz - pz;
        double load = Math.sqrt(ax * ax + ay * ay + az * az) / g;
        return Double.isFinite(load) ? load : Double.NaN;
    }

    public static final double HARD_TURN_MIN_SPEED = 250.0D;
    public static final double HARD_TURN_FULL_SPEED = 310.0D;

    public static double hardTurnEnergyShare(double speedBps) {
        if (!Double.isFinite(speedBps)) {
            return 0.0D;
        }
        double fullSize = speedBps / amrac.physics.aircraft.SpeedScale.current();
        return clamp((fullSize - HARD_TURN_MIN_SPEED)
            / Math.max(1.0D, HARD_TURN_FULL_SPEED - HARD_TURN_MIN_SPEED), 0.0D, 1.0D);
    }

    public static final double CLIMBING_BANK = Math.toRadians(45.0D);

    public static final double CLIMBING_BANK_FROM = 500.0D;

    public static final double CLIMBING_BANK_FULL = 1200.0D;

    public static double climbingBankLimit(double altitudeDeficit) {
        double deficit = altitudeDeficit / amrac.physics.aircraft.SpeedScale.current();
        if (!Double.isFinite(deficit) || deficit <= CLIMBING_BANK_FROM) {
            return Math.PI;
        }
        double share = clamp((deficit - CLIMBING_BANK_FROM)
            / (CLIMBING_BANK_FULL - CLIMBING_BANK_FROM), 0.0D, 1.0D);
        return Math.PI / 2.0D + (CLIMBING_BANK - Math.PI / 2.0D) * share;
    }

    public static double hardTurnShare(double headingError, double speedBps) {
        AiPilotSettings settings = AiPilotSettings.current();
        if (settings.hardTurnPull <= 0.0D || !Double.isFinite(headingError)
                || !Double.isFinite(speedBps)) {
            return 0.0D;
        }
        double errorShare = clamp((Math.abs(headingError) - HARD_TURN_PULL_MIN_ERROR)
            / HARD_TURN_PULL_ERROR_RAMP, 0.0D, 1.0D);
        double minSpeed = settings.borderTurnBackPullMinSpeed;
        double speedShare = clamp((speedBps - minSpeed)
            / Math.max(1.0D, minSpeed * TURN_BACK_PULL_SPEED_RAMP), 0.0D, 1.0D);
        return errorShare * speedShare * hardTurnEnergyShare(speedBps);
    }

    public static float turnBackPitch(float pitch, double headingError,
                                      double bank, double speedBps) {
        AiPilotSettings settings = AiPilotSettings.current();
        if (settings.borderTurnBackPull <= 0.0D
                || !(Math.abs(headingError) > TURN_BACK_PULL_MIN_ERROR)
                || !Double.isFinite(bank) || !Double.isFinite(speedBps)) {
            return pitch;
        }
        double bankShare = clamp((Math.abs(bank) - TURN_BACK_PULL_MIN_BANK)
            / TURN_BACK_PULL_BANK_RAMP, 0.0D, 1.0D);
        double minSpeed = settings.borderTurnBackPullMinSpeed;
        double speedShare = clamp((speedBps - minSpeed)
            / Math.max(1.0D, minSpeed * TURN_BACK_PULL_SPEED_RAMP), 0.0D, 1.0D);
        double scale = bankShare * speedShare;
        if (scale <= 0.0D) {
            return pitch;
        }
        return (float) Math.max(pitch, settings.borderTurnBackPull * scale);
    }

    public static final double HARD_TURN_ERROR = Math.toRadians(10.0D);

    public static double bankLimit(double headingError, double currentBank,
                                   double loadG, boolean urgent) {
        if (urgent) {
            return bankForTurnBack(loadG);
        }
        double hard = bankForLoad(loadG);
        AiPilotSettings settings = AiPilotSettings.current();
        if (!Double.isFinite(headingError)) {
            return settings.maxBank;
        }
        double error = Math.abs(headingError);
        if (error >= settings.hardTurnError) {
            return hard;
        }
        boolean committed = Double.isFinite(currentBank)
            && Math.abs(currentBank) > settings.maxBank + 1.0E-6D;
        boolean saturated = error * BANK_PER_HEADING_ERROR >= settings.maxBank;
        return committed && saturated ? hard : settings.maxBank;
    }

    public static final double BANK_PER_HEADING_ERROR = 1.6D;
    public static final double ROLL_GAIN = 2.2D;

    public static final double ROLL_RATE_LEAD_TICKS = 3.0D;

    public static final double TURN_COMPENSATION = 0.55D;
    public static final double MAX_PITCH = 0.85D;

    public static final double ALTITUDE_CAPTURE_BAND = 600.0D;

    public static final double THROTTLE_PER_SPEED_ERROR = 22.5D;

    public static final double ROTATE_SPEED = 4.0D;

    public static final double CRUISE_SPEED = 12.5D;

    public static final double CLIMB_POWER_ALTITUDE_ERROR = 200.0D;

    public static final double DEPARTURE_ALTITUDE = 2000.0D;

    public static final double DEPARTURE_CLIMB_ANGLE = Math.toRadians(30.0D);

    public static final double PITCH_PER_CLIMB_ANGLE_ERROR = 2.5D;

    public static final double TURN_COMPENSATION_FADE = Math.toRadians(8.0D);

    private AiPilotPolicy() {
    }

    public static double headingError(double forwardX, double forwardZ,
                                      double toGoalX, double toGoalZ) {
        double fLength = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        double gLength = Math.sqrt(toGoalX * toGoalX + toGoalZ * toGoalZ);
        if (!(fLength > 1.0E-6D) || !(gLength > 1.0E-6D)) {
            return 0.0D;
        }
        double fx = forwardX / fLength, fz = forwardZ / fLength;
        double gx = toGoalX / gLength, gz = toGoalZ / gLength;
        // The sign follows the game; the pure-Java AiDepartureSim rolls the other way and once got
        // this line changed wrongly. Verify changes with VirtualClimbProbe (not in this
        // repository).
        double cross = fz * gx - fx * gz;
        double dot = fx * gx + fz * gz;
        return Math.atan2(cross, dot);
    }

    public static double desiredBank(double headingErrorRadians) {
        if (!Double.isFinite(headingErrorRadians)) {
            return 0.0D;
        }
        return clamp(headingErrorRadians * BANK_PER_HEADING_ERROR,
            -AiPilotSettings.current().maxBank, AiPilotSettings.current().maxBank);
    }

    public static final double REVERSAL_AMBIGUITY = Math.PI - Math.toRadians(15.0D);

    public static double desiredBank(double headingErrorRadians,
                                     double currentBank) {
        if (!Double.isFinite(headingErrorRadians)) {
            return 0.0D;
        }
        if (Math.abs(headingErrorRadians) >= REVERSAL_AMBIGUITY) {
            double committed = Double.isFinite(currentBank) && currentBank != 0.0D
                ? Math.signum(currentBank) : 1.0D;
            return committed * AiPilotSettings.current().maxBank;
        }
        return desiredBank(headingErrorRadians);
    }

    public static double affordableBank(double wanted, double speed) {
        if (!Double.isFinite(wanted) || !Double.isFinite(speed)) {
            return 0.0D;
        }
        return wanted
            * clamp(speed / AiPilotSettings.current().cruiseSpeed, 0.0D, 1.0D);
    }

    public static double affordableBank(double wanted, double speed,
                                        double structuralLimit) {
        if (!Double.isFinite(structuralLimit) || structuralLimit <= 1.0E-6D) {
            return affordableBank(wanted, speed);
        }
        double reference = structuralLimit * BANK_PRESSURE_FRACTION;
        if (!Double.isFinite(wanted) || !Double.isFinite(speed)
            || reference <= 1.0E-6D) {
            return 0.0D;
        }
        return wanted * clamp(speed / reference, 0.0D, 1.0D);
    }

    public static final double BANK_PRESSURE_FRACTION = 0.62D;

    public static double bankAngle(double rightY, double upY) {
        if (!Double.isFinite(rightY) || !Double.isFinite(upY)) {
            return 0.0D;
        }
        return Math.atan2(-rightY, upY);
    }

    public static float rollInput(double desiredBank, double currentBank) {
        return rollInput(desiredBank, currentBank, 0.0D);
    }

    public static float rollInput(double desiredBank, double currentBank,
                                  double rollRate) {
        if (!Double.isFinite(desiredBank) || !Double.isFinite(currentBank)) {
            return 0.0F;
        }
        double error = wrapAngle(desiredBank - currentBank);
        if (Double.isFinite(rollRate)) {
            error -= ROLL_RATE_LEAD_TICKS * rollRate;
        }
        return (float) clamp(error * ROLL_GAIN, -1.0D, 1.0D);
    }

    public static double wrapAngle(double radians) {
        if (!Double.isFinite(radians)) {
            return 0.0D;
        }
        double wrapped = Math.IEEEremainder(radians, 2.0D * Math.PI);
        return Double.isFinite(wrapped) ? wrapped : 0.0D;
    }

    public static final double WINGS_LEVEL_DEADBAND = 0.05D;

    public static float upsetRoll(double rightY) {
        if (!Double.isFinite(rightY) || Math.abs(rightY) < WINGS_LEVEL_DEADBAND) {
            return 1.0F;
        }
        return rightY > 0.0D ? 1.0F : -1.0F;
    }

    public static boolean inverted(double upY) {
        return !Double.isFinite(upY) || upY < 0.0D;
    }

    public static double climbAngleForError(double altitudeError) {
        if (!Double.isFinite(altitudeError)) {
            return 0.0D;
        }
        return AiPilotSettings.current().maxClimbAngle
            * clamp(altitudeError / (ALTITUDE_CAPTURE_BAND
                * amrac.physics.aircraft.SpeedScale.current()), -1.0D, 1.0D);
    }

    public static float altitudeHoldPitch(double altitudeError,
                                          double verticalSpeed, double speed,
                                          double bankAngle) {
        double wanted = climbAngleForError(altitudeError);
        if (wanted > 0.0D) {
            double bank = Double.isFinite(bankAngle)
                ? Math.min(Math.abs(bankAngle), AiPilotSettings.current().maxBank) : 0.0D;
            wanted = sustainableClimbAngle(wanted, speed,
                AiPilotSettings.current().cruiseSpeed / Math.cos(bank));
        }
        return climbAnglePitch(flightPathAngle(verticalSpeed, speed), wanted,
            bankAngle);
    }

    public static int throttleInput(double speed, double targetSpeed,
                                    int currentThrottle, int maxThrottle) {
        if (!Double.isFinite(speed) || !Double.isFinite(targetSpeed)) {
            return currentThrottle;
        }
        double error = (targetSpeed - speed) / amrac.physics.aircraft.SpeedScale.current();
        int step = (int) Math.round(error * THROTTLE_PER_SPEED_ERROR);
        int next = currentThrottle + clampInt(step, -6, 6);
        return clampInt(next, 0, maxThrottle);
    }

    public static int commandedThrottle(int throttle, int maxThrottle,
                                        boolean afterburner) {
        return afterburner ? maxThrottle + 1 : clampInt(throttle, 0, maxThrottle);
    }

    public static boolean departing(double altitude) {
        return !Double.isFinite(altitude)
            || altitude < AiPilotSettings.current().departureAltitude;
    }

    public static double flightPathAngle(double verticalSpeed, double speed) {
        if (!Double.isFinite(verticalSpeed) || !(speed > 1.0E-6D)) {
            return 0.0D;
        }
        return Math.asin(clamp(verticalSpeed / speed, -1.0D, 1.0D));
    }

    public static double sustainableClimbAngle(double targetAngle, double speed,
                                               double minSpeed) {
        if (!Double.isFinite(speed) || !(minSpeed > 0.0D)) {
            return 0.0D;
        }
        return targetAngle * clamp(speed / minSpeed, 0.0D, 1.0D);
    }

    public static float climbAnglePitch(double climbAngle, double targetAngle,
                                        double bankAngle) {
        if (!Double.isFinite(climbAngle) || !Double.isFinite(targetAngle)) {
            return 0.0F;
        }
        double command = (targetAngle - climbAngle) * PITCH_PER_CLIMB_ANGLE_ERROR;
        double bank = Double.isFinite(bankAngle) ? Math.abs(bankAngle) : 0.0D;
        double excess = climbAngle - targetAngle;
        command += TURN_COMPENSATION * (1.0D - Math.cos(bank))
            * clamp(1.0D - excess / TURN_COMPENSATION_FADE, 0.0D, 1.0D);
        return (float) clamp(command, -MAX_PITCH, MAX_PITCH);
    }

    public static void interceptGoal(double[] targetPosition,
                                     double[] targetVelocity,
                                     double[] ownPosition, double ownSpeed,
                                     double maxLeadTicks, double[] out) {
        leadGoal(targetPosition, targetVelocity, ownPosition, ownSpeed, 1.0D,
            maxLeadTicks, out);
    }

    public static final double LEAD_FULL_COSINE = 0.5D;

    public static final double LEAD_NONE_COSINE = -0.5D;

    public static void interceptGoal(double[] targetPosition,
                                     double[] targetVelocity,
                                     double[] ownPosition, double[] ownVelocity,
                                     double maxLeadTicks, double[] out) {
        double dx = targetPosition[0] - ownPosition[0];
        double dy = targetPosition[1] - ownPosition[1];
        double dz = targetPosition[2] - ownPosition[2];
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double speed = Math.sqrt(ownVelocity[0] * ownVelocity[0]
            + ownVelocity[1] * ownVelocity[1] + ownVelocity[2] * ownVelocity[2]);
        double share = 1.0D;
        if (range > 1.0E-6D && speed > 1.0E-3D) {
            double cosine = (dx * ownVelocity[0] + dy * ownVelocity[1]
                + dz * ownVelocity[2]) / (range * speed);
            share = clamp((cosine - LEAD_NONE_COSINE)
                / (LEAD_FULL_COSINE - LEAD_NONE_COSINE), 0.0D, 1.0D);
        }
        leadGoal(targetPosition, targetVelocity, ownPosition, speed, share,
            maxLeadTicks, out);
    }

    private static void leadGoal(double[] targetPosition, double[] targetVelocity,
                                 double[] ownPosition, double ownSpeed,
                                 double share, double maxLeadTicks,
                                 double[] out) {
        double dx = targetPosition[0] - ownPosition[0];
        double dy = targetPosition[1] - ownPosition[1];
        double dz = targetPosition[2] - ownPosition[2];
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double lead = 0.0D;
        if (range > 1.0E-6D && ownSpeed > 1.0E-3D) {
            lead = Math.min(range / ownSpeed, maxLeadTicks) * share;
        }
        out[0] = targetPosition[0] + targetVelocity[0] * lead;
        out[1] = targetPosition[1] + targetVelocity[1] * lead;
        out[2] = targetPosition[2] + targetVelocity[2] * lead;
    }

    public static final double PATROL_RADIUS = 10000.0D;

    public static final double PATROL_LOOKAHEAD = 2000.0D;

    public static final double PATROL_CAPTURE = 24000.0D;

    public static void patrolGoal(double stationX, double stationZ,
                                  double x, double z, double[] out) {
        double dx = x - stationX;
        double dz = z - stationZ;
        double range = Math.hypot(dx, dz);
        double ux = range > 1.0E-6D ? dx / range : 1.0D;
        double uz = range > 1.0E-6D ? dz / range : 0.0D;
        AiPilotSettings settings = AiPilotSettings.current();
        double closing = clamp(
            (range - settings.patrolRadius) / settings.patrolCapture,
            -1.0D, 1.0D);
        double along = Math.sqrt(Math.max(0.0D, 1.0D - closing * closing));
        double courseX = -uz * along - ux * closing;
        double courseZ = ux * along - uz * closing;
        out[0] = x + courseX * settings.patrolLookahead;
        out[1] = z + courseZ * settings.patrolLookahead;
    }

    public static boolean readyToRotate(double speed) {
        return Double.isFinite(speed)
            && speed >= AiPilotSettings.current().rotateSpeed;
    }

    private static double clamp(double v, double low, double high) {
        return v < low ? low : Math.min(v, high);
    }

    private static int clampInt(int v, int low, int high) {
        return v < low ? low : Math.min(v, high);
    }
}

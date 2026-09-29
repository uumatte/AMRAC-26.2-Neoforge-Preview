package amrac.physics.missile;

import amrac.physics.aircraft.AtmosphereModel;
import amrac.physics.aircraft.Vec3d;

public record MissilePerformance(double peakSpeed, double peakMach, double maxLoadG,
                                 double noEscapeZone, double burnoutSpeed,
                                 double timeToPeakSeconds, double totalImpulse,
                                 double referenceAltitude, double referenceLaunchSpeed,
                                 double referenceTargetSpeed) {
    private static final double MAXIMUM_FLIGHT_SECONDS = 180.0D;

    private static volatile MissileFlightModel cachedModel;
    private static volatile AtmosphereModel cachedAtmosphere;
    private static volatile MissilePerformance cached;
    private static volatile double cachedSeconds;

    public static MissilePerformance of(MissileFlightModel model,
                                        AtmosphereModel atmosphere,
                                        double maxSeconds) {
        MissilePerformance hit = cached;
        if (hit != null && cachedModel == model && cachedAtmosphere == atmosphere
            && cachedSeconds == maxSeconds) {
            return hit;
        }
        MissilePerformance measured = measure(model, atmosphere, maxSeconds);
        cachedSeconds = maxSeconds;
        cachedModel = model;
        cachedAtmosphere = atmosphere;
        cached = measured;
        return measured;
    }

    public static MissilePerformance measure(MissileFlightModel model,
                                             AtmosphereModel atmosphere,
                                             double maxSeconds) {
        MissilePhysicsProfile profile = model.profile();
        return measureAt(model, atmosphere, profile.referenceAltitude(),
            profile.referenceLaunchSpeed(), profile.referenceTargetSpeed(),
            maxSeconds);
    }

    public static MissilePerformance measureAt(MissileFlightModel model,
                                               AtmosphereModel atmosphere,
                                               double altitude,
                                               double launchSpeed,
                                               double targetSpeed,
                                               double maxSeconds) {
        MissilePhysicsProfile profile = model.profile();
        double worldY = altitude + atmosphere.seaLevelY();
        double step = MissileFlightModel.FIXED_TIME_STEP;

        Vec3d axis = new Vec3d(0.0D, 0.0D, 1.0D);
        Vec3d velocity = axis.scale(launchSpeed);

        double peakSpeed = velocity.length();
        double peakLoad = model.availableLoadG(peakSpeed, worldY, atmosphere,
            0.0D);
        double timeToPeak = 0.0D;
        double closure = 0.0D;
        double bestClosure = 0.0D;
        double burnout = profile.burnoutSeconds();
        double burnoutSpeed = peakSpeed;

        double horizon = Double.isFinite(maxSeconds) && maxSeconds > 0.0D
            ? Math.min(maxSeconds, MAXIMUM_FLIGHT_SECONDS) : MAXIMUM_FLIGHT_SECONDS;
        int tick = 0;
        for (double time = 0.0D; time < horizon; time += step) {
            double clock = tick++
                / (double) amrac.physics.TickRate.TICKS_PER_SECOND;
            velocity = model.advanceLevel(velocity, axis, worldY, clock,
                atmosphere, step);

            double speed = velocity.length();
            if (speed > peakSpeed) {
                peakSpeed = speed;
                timeToPeak = time + step;
            }
            peakLoad = Math.max(peakLoad,
                model.availableLoadG(speed, worldY, atmosphere, clock));
            if (time <= burnout && time + step > burnout) {
                burnoutSpeed = speed;
            }

            closure += (speed - targetSpeed) * step;
            bestClosure = Math.max(bestClosure, closure);
            if (speed <= targetSpeed && time > burnout) {
                break;
            }
        }

        double speedOfSound = atmosphere.speedOfSound(worldY);
        return new MissilePerformance(peakSpeed,
            speedOfSound > 1.0E-6D ? peakSpeed / speedOfSound : 0.0D,
            peakLoad, bestClosure, burnoutSpeed, timeToPeak, profile.totalImpulse(),
            altitude, launchSpeed, targetSpeed);
    }

    public double peakSpeedBlocksPerSecond() {
        return peakSpeed;
    }

    public String summary() {
        return String.format(
            "peak %.1f m/s (M%.2f) at %.1fs, burnout %.1f m/s, %.0f g, " +
                "NEZ %.0f m, impulse %.0f Ns (ref: %.0f m, %.0f m/s vs %.0f m/s)",
            peakSpeed, peakMach, timeToPeakSeconds, burnoutSpeed, maxLoadG,
            noEscapeZone, totalImpulse, referenceAltitude, referenceLaunchSpeed,
            referenceTargetSpeed);
    }
}

package amrac.display;

import amrac.physics.aircraft.AerodynamicsModel;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.AtmosphereModel;
import amrac.physics.aircraft.EngineModel;

public final class AircraftCharts {
    private static final int SAMPLES = 72;

    private static final double MIN_SPEED = 40.0D;
    private static final double MAX_SPEED = 1240.0D;

    private static final double MIN_ALTITUDE = 0.0D;
    private static final double MAX_ALTITUDE = 18000.0D;

    private static final double MAX_AOA = 30.0D;
    private static final int AOA_STEPS = 240;

    private AircraftCharts() {
    }

    public static ChartData turnRateVsSpeed(AircraftPhysicsProfile profile,
                                            AtmosphereModel atmosphere,
                                            double worldY) {
        AerodynamicsModel aero = new AerodynamicsModel(profile);
        double weight = profile.mass() * atmosphere.gravity();
        double density = atmosphere.density(worldY);
        double maximumLiftCoefficient = maximumLiftCoefficient(aero);

        double[] xs = new double[SAMPLES];
        double[] ys = new double[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            double speed = MIN_SPEED
                + (MAX_SPEED - MIN_SPEED) * i / (double) (SAMPLES - 1);
            double liftLimited = 0.5D * density * speed * speed
                * profile.wingArea() * maximumLiftCoefficient / weight;
            double load = Math.min(profile.maxPositiveG(), liftLimited);
            xs[i] = speed;
            ys[i] = load <= 1.0D ? 0.0D
                : Math.toDegrees(atmosphere.gravity()
                    * Math.sqrt(load * load - 1.0D) / speed);
        }
        return new ChartData(ChartKind.TURN_RATE_VS_SPEED.label(),
            ChartKind.TURN_RATE_VS_SPEED.xLabel(),
            ChartKind.TURN_RATE_VS_SPEED.yLabel(), xs, ys,
            profile.id() + " at " + Math.round(worldY) + " m");
    }

    public static ChartData sepVsSpeed(AircraftPhysicsProfile profile,
                                       AtmosphereModel atmosphere,
                                       double worldY) {
        AerodynamicsModel aero = new AerodynamicsModel(profile);
        EngineModel engine = new EngineModel(profile);
        double weight = profile.mass() * atmosphere.gravity();
        double density = atmosphere.density(worldY);
        double atmospheric = atmosphere.atmosphericAltitude(worldY);

        double[] xs = new double[SAMPLES];
        double[] ys = new double[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            double speed = MIN_SPEED
                + (MAX_SPEED - MIN_SPEED) * i / (double) (SAMPLES - 1);
            double mach = atmosphere.mach(speed, worldY);
            double thrust = engine.thrust(atmospheric, mach, 1.0D, 1.0F, true);
            double drag = levelDrag(aero, profile, density, speed, mach, weight);
            xs[i] = speed;
            ys[i] = speed * (thrust - drag) / weight;
        }
        return new ChartData(ChartKind.SEP_VS_SPEED.label(),
            ChartKind.SEP_VS_SPEED.xLabel(), ChartKind.SEP_VS_SPEED.yLabel(),
            xs, ys, profile.id() + " at " + Math.round(worldY) + " m, burner");
    }

    public static ChartData maxSpeedVsAltitude(AircraftPhysicsProfile profile,
                                               AtmosphereModel atmosphere) {
        AerodynamicsModel aero = new AerodynamicsModel(profile);
        EngineModel engine = new EngineModel(profile);
        double weight = profile.mass() * atmosphere.gravity();

        double[] xs = new double[SAMPLES];
        double[] ys = new double[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            double worldY = MIN_ALTITUDE
                + (MAX_ALTITUDE - MIN_ALTITUDE) * i / (double) (SAMPLES - 1);
            double density = atmosphere.density(worldY);
            double atmospheric = atmosphere.atmosphericAltitude(worldY);
            double best = 0.0D;
            for (int step = 0; step < SAMPLES * 4; step++) {
                double speed = MIN_SPEED
                    + (MAX_SPEED - MIN_SPEED) * step / (double) (SAMPLES * 4 - 1);
                double mach = atmosphere.mach(speed, worldY);
                double thrust = engine.thrust(atmospheric, mach, 1.0D, 1.0F, true);
                double drag = levelDrag(aero, profile, density, speed, mach, weight);
                if (thrust >= drag) {
                    best = speed;
                }
            }
            xs[i] = worldY;
            ys[i] = best;
        }
        return new ChartData(ChartKind.MAX_SPEED_VS_ALTITUDE.label(),
            ChartKind.MAX_SPEED_VS_ALTITUDE.xLabel(),
            ChartKind.MAX_SPEED_VS_ALTITUDE.yLabel(), xs, ys,
            profile.id() + ", burner, level");
    }

    private static double levelDrag(AerodynamicsModel aero,
                                    AircraftPhysicsProfile profile,
                                    double density, double speed, double mach,
                                    double weight) {
        double angle = angleOfAttackForLevelFlight(aero, profile, density,
            speed, weight);
        return aero.drag(density, speed, angle, mach, 0.0D, 0.0D);
    }

    private static double angleOfAttackForLevelFlight(AerodynamicsModel aero,
                                                      AircraftPhysicsProfile profile,
                                                      double density,
                                                      double speed,
                                                      double weight) {
        double required = 2.0D * weight
            / Math.max(1.0E-9D, density * speed * speed * profile.wingArea());
        double best = MAX_AOA;
        for (int i = 0; i <= AOA_STEPS; i++) {
            double angle = MAX_AOA * i / (double) AOA_STEPS;
            if (aero.liftCoefficient(angle) >= required) {
                best = angle;
                break;
            }
        }
        return best;
    }

    private static double maximumLiftCoefficient(AerodynamicsModel aero) {
        double best = 0.0D;
        for (int i = 0; i <= AOA_STEPS; i++) {
            double angle = MAX_AOA * i / (double) AOA_STEPS;
            best = Math.max(best, aero.liftCoefficient(angle));
        }
        return Math.max(best, 1.0E-6D);
    }
}

package amrac.display;

import amrac.physics.aircraft.AtmosphereModel;
import amrac.physics.aircraft.Vec3d;
import amrac.physics.missile.MissileFlightModel;
import amrac.physics.missile.MissilePerformance;
import amrac.physics.missile.MissilePhysicsProfile;

public final class MissileCharts {
    private static final int SAMPLES = 72;

    private static final double MAX_SECONDS = 120.0D;

    private static double horizon(MissilePhysicsProfile profile) {
        double life = profile.lifetimeTicks() / 20.0D;
        return life > 0.0D ? Math.min(life, MAX_SECONDS) : MAX_SECONDS;
    }

    private static final double MIN_SPEED = 80.0D;
    private static final double MAX_SPEED = 1800.0D;

    private MissileCharts() {
    }

    public static ChartData speedVsTime(MissileFlightModel model,
                                        AtmosphereModel atmosphere) {
        return speedVsTime(model, atmosphere, Double.NaN, Double.NaN);
    }

    public static ChartData speedVsTime(MissileFlightModel model,
                                        AtmosphereModel atmosphere,
                                        double altitude, double launchSpeed) {
        MissilePhysicsProfile profile = model.profile();
        double offTheRail = Double.isFinite(launchSpeed) && launchSpeed > 0.0D
            ? launchSpeed : profile.referenceLaunchSpeed();
        double worldY = Double.isFinite(altitude)
            ? altitude : profile.referenceAltitude() + atmosphere.seaLevelY();
        double step = MissileFlightModel.FIXED_TIME_STEP;

        Vec3d axis = new Vec3d(0.0D, 0.0D, 1.0D);
        Vec3d velocity = axis.scale(offTheRail);

        int steps = (int) Math.ceil(horizon(profile) / step);
        int every = Math.max(1, steps / SAMPLES);
        double[] xs = new double[steps / every + 1];
        double[] ys = new double[xs.length];
        int written = 0;
        double seconds = 0.0D;
        for (int i = 0; i < steps && written < xs.length; i++) {
            if (i % every == 0) {
                xs[written] = seconds;
                ys[written] = velocity.length();
                written++;
            }
            velocity = model.advanceLevel(velocity, axis, worldY,
                i / (double) amrac.physics.TickRate.TICKS_PER_SECOND, atmosphere,
                step);
            seconds += step;
        }
        return new ChartData(ChartKind.SPEED_VS_TIME.label(),
            ChartKind.SPEED_VS_TIME.xLabel(), ChartKind.SPEED_VS_TIME.yLabel(),
            trim(xs, written), trim(ys, written), "");
    }

    public static ChartData availableGvsSpeed(MissileFlightModel model,
                                              AtmosphereModel atmosphere,
                                              double worldY) {
        double[] xs = new double[SAMPLES];
        double[] ys = new double[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            double speed = MIN_SPEED
                + (MAX_SPEED - MIN_SPEED) * i / (double) (SAMPLES - 1);
            xs[i] = speed;
            ys[i] = model.availableLoadG(speed, worldY, atmosphere);
        }
        return new ChartData(ChartKind.AVAILABLE_G_VS_SPEED.label(),
            ChartKind.AVAILABLE_G_VS_SPEED.xLabel(),
            ChartKind.AVAILABLE_G_VS_SPEED.yLabel(), xs, ys,
            model.profile().id() + " at " + Math.round(worldY) + " m");
    }

    public static ChartData nezVsSpeed(MissileFlightModel model,
                                       AtmosphereModel atmosphere,
                                       double worldY, double targetSpeed) {
        double altitude = worldY - atmosphere.seaLevelY();
        double life = horizon(model.profile());
        double[] xs = new double[SAMPLES];
        double[] ys = new double[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            double launchSpeed = MIN_SPEED
                + (MAX_SPEED - MIN_SPEED) * i / (double) (SAMPLES - 1);
            xs[i] = launchSpeed;
            ys[i] = MissilePerformance.measureAt(model, atmosphere, altitude,
                launchSpeed, targetSpeed, life).noEscapeZone();
        }
        return new ChartData(ChartKind.NEZ_VS_SPEED.label(),
            ChartKind.NEZ_VS_SPEED.xLabel(), ChartKind.NEZ_VS_SPEED.yLabel(),
            xs, ys, "target " + Math.round(targetSpeed) + " m/s at "
                + Math.round(worldY) + " m");
    }

    private static double[] trim(double[] values, int length) {
        double[] out = new double[length];
        System.arraycopy(values, 0, out, 0, length);
        return out;
    }
}

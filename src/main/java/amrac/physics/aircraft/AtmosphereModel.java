package amrac.physics.aircraft;

import java.util.Map;

public final class AtmosphereModel {
    public static final double SEA_LEVEL_DENSITY = 1.225D;

    public static final double SEA_LEVEL_SPEED_OF_SOUND = 340.0D;

    public static final double STANDARD_GRAVITY = 9.81D;

    private final CurveInterpolator densityCurve;
    private final CurveInterpolator speedOfSoundCurve;
    private final double gravity;
    private final double altitudeScale;
    private final double seaLevelY;
    private final boolean debugTelemetry;
    private final int debugIntervalTicks;
    private final double speedScale;

    private AtmosphereModel(CurveInterpolator densityCurve,
                            CurveInterpolator speedOfSoundCurve,
                            double gravity, double altitudeScale,
                            double seaLevelY, boolean debugTelemetry,
                            int debugIntervalTicks) {
        this.densityCurve = densityCurve;
        this.speedOfSoundCurve = speedOfSoundCurve;
        this.gravity = gravity;
        this.altitudeScale = altitudeScale;
        this.seaLevelY = seaLevelY;
        this.debugTelemetry = debugTelemetry;
        this.debugIntervalTicks = debugIntervalTicks;
        double sound = speedOfSoundCurve.interpolate(0.0D);
        double scale = sound / SEA_LEVEL_SPEED_OF_SOUND;
        this.speedScale = Double.isFinite(scale) && scale > 0.0D
            ? Math.min(Math.max(scale, 0.01D), 10.0D) : 1.0D;
    }

    public static AtmosphereModel fromJson(Map<String, Object> root) {
        double[][] density = Json.curve(root, "densityCurve");
        double[][] speedOfSound = Json.curve(root, "speedOfSoundCurve");
        Map<String, Object> debug = Json.object(root, "debug");
        return new AtmosphereModel(
            density != null ? CurveInterpolator.of(density) : defaultDensityCurve(),
            speedOfSound != null ? CurveInterpolator.of(speedOfSound)
                : defaultSpeedOfSoundCurve(),
            clampPositive(Json.number(root, "gravity", STANDARD_GRAVITY),
                STANDARD_GRAVITY, 100.0D),
            clampPositive(Json.number(root, "altitudeScale", 1.0D), 1.0D, 5000.0D),
            Json.number(root, "seaLevelY", 63.0D),
            Json.bool(debug, "telemetry", false),
            (int) clampPositive(Json.number(debug, "intervalTicks", 20.0D),
                20.0D, 6000.0D));
    }

    public static AtmosphereModel standard() {
        return new AtmosphereModel(defaultDensityCurve(), defaultSpeedOfSoundCurve(),
            STANDARD_GRAVITY, 1.0D, 63.0D, false, 20);
    }

    public double atmosphericAltitude(double worldY) {
        if (!Double.isFinite(worldY)) {
            return 0.0D;
        }
        return (worldY - seaLevelY) * altitudeScale;
    }

    public double density(double worldY) {
        return Math.max(densityCurve.interpolate(atmosphericAltitude(worldY)),
            1.0E-4D);
    }

    public double speedOfSound(double worldY) {
        return Math.max(speedOfSoundCurve.interpolate(atmosphericAltitude(worldY)),
            1.0D);
    }

    public double speedScale() {
        return speedScale;
    }

    public double mach(double airspeed, double worldY) {
        return Math.max(airspeed, 0.0D) / speedOfSound(worldY);
    }

    public double gravity() {
        return gravity;
    }

    public double altitudeScale() {
        return altitudeScale;
    }

    public double seaLevelY() {
        return seaLevelY;
    }

    public boolean debugTelemetry() {
        return debugTelemetry;
    }

    public int debugIntervalTicks() {
        return debugIntervalTicks;
    }

    public CurveInterpolator densityCurve() {
        return densityCurve;
    }

    public CurveInterpolator speedOfSoundCurve() {
        return speedOfSoundCurve;
    }

    private static CurveInterpolator defaultDensityCurve() {
        return CurveInterpolator.of(new double[][] {
            {0.0D, 1.2250D},
            {1000.0D, 1.1117D},
            {2000.0D, 1.0066D},
            {3000.0D, 0.9093D},
            {4000.0D, 0.8194D},
            {5000.0D, 0.7364D},
            {6000.0D, 0.6601D},
            {7000.0D, 0.5900D},
            {8000.0D, 0.5258D},
            {9000.0D, 0.4671D},
            {10000.0D, 0.4135D},
            {11000.0D, 0.3639D},
            {12000.0D, 0.3119D},
            {13000.0D, 0.2666D},
            {14000.0D, 0.2279D},
            {16000.0D, 0.1665D},
            {18000.0D, 0.1216D},
            {20000.0D, 0.0889D},
            {24000.0D, 0.0469D},
            {28000.0D, 0.0251D}
        });
    }

    private static CurveInterpolator defaultSpeedOfSoundCurve() {
        return CurveInterpolator.of(new double[][] {
            {0.0D, 340.0D},
            {1000.0D, 336.4D},
            {2000.0D, 332.6D},
            {3000.0D, 328.6D},
            {4000.0D, 324.6D},
            {5000.0D, 320.6D},
            {6000.0D, 316.4D},
            {7000.0D, 312.4D},
            {8000.0D, 308.2D},
            {9000.0D, 303.8D},
            {10000.0D, 299.6D},
            {11000.0D, 295.2D},
            {20000.0D, 295.2D},
            {28000.0D, 300.0D}
        });
    }

    private static double clampPositive(double value, double fallback, double maximum) {
        if (!Double.isFinite(value) || value <= 0.0D || value > maximum) {
            return fallback;
        }
        return value;
    }
}

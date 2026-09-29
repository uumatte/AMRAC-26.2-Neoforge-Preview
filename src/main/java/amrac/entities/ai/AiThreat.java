package amrac.entities.ai;

import amrac.weapons.MissileProfile;

public record AiThreat(double x, double y, double z,
                       double velocityX, double velocityY, double velocityZ,
                       MissileProfile profile, double notchError, int side,
                       double trim) {
    public AiThreat(double x, double y, double z, double velocityX,
                    double velocityY, double velocityZ, MissileProfile profile,
                    double notchError) {
        this(x, y, z, velocityX, velocityY, velocityZ, profile, notchError, 0, 0.0D);
    }

    public AiThreat(double x, double y, double z, double velocityX,
                    double velocityY, double velocityZ, MissileProfile profile,
                    double notchError, int side) {
        this(x, y, z, velocityX, velocityY, velocityZ, profile, notchError, side, 0.0D);
    }
}

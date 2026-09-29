package amrac.physics.aircraft;

public final class AircraftEnvironment {
    private final double density;
    private final double speedOfSound;
    private final double gravity;
    private final double atmosphericAltitude;
    private final double worldY;
    private final boolean groundContact;

    public AircraftEnvironment(double density, double speedOfSound, double gravity,
                               double atmosphericAltitude, double worldY,
                               boolean groundContact) {
        this.density = density;
        this.speedOfSound = speedOfSound;
        this.gravity = gravity;
        this.atmosphericAltitude = atmosphericAltitude;
        this.worldY = worldY;
        this.groundContact = groundContact;
    }

    public static AircraftEnvironment sample(AtmosphereModel atmosphere, double worldY,
                                             boolean groundContact) {
        return new AircraftEnvironment(atmosphere.density(worldY),
            atmosphere.speedOfSound(worldY), atmosphere.gravity(),
            atmosphere.atmosphericAltitude(worldY), worldY, groundContact);
    }

    public double density() {
        return density;
    }

    public double speedOfSound() {
        return speedOfSound;
    }

    public double gravity() {
        return gravity;
    }

    public double atmosphericAltitude() {
        return atmosphericAltitude;
    }

    public double worldY() {
        return worldY;
    }

    public boolean groundContact() {
        return groundContact;
    }
}

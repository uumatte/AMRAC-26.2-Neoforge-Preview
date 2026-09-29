package amrac.physics.missile;

public record MissileFlightState(double worldY, double density, double speedOfSound,
                                 double airspeed, double mach, double angleOfAttack,
                                 double dynamicPressure, double dragCoefficient,
                                 double drag, double normalForceCoefficient,
                                 double thrust, double weight, double availableLoadG,
                                 double controlAuthority) {
    public double excessThrust() {
        return thrust - drag;
    }

    public String summary() {
        return String.format(
            "y=%.0f v=%.1fm/s M=%.2f aoa=%.1f q=%.0fPa Cd=%.3f D=%.0fN T=%.0fN G=%.1f",
            worldY, airspeed, mach, angleOfAttack, dynamicPressure, dragCoefficient,
            drag, thrust, availableLoadG);
    }

    @Override
    public String toString() {
        return summary();
    }
}

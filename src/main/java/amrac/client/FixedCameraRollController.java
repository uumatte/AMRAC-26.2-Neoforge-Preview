package amrac.client;

public final class FixedCameraRollController {
    private FixedCameraRollController() {}

    public static float resolveCameraRoll(boolean fixedFirstPerson,
                                          boolean followPlaneRoll,
                                          float planeCameraRoll) {
        return fixedFirstPerson || followPlaneRoll ? planeCameraRoll : 0.0F;
    }
}

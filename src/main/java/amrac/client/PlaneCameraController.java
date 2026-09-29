package amrac.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import amrac.MathUtil;
import amrac.client.render.PlaneVisualFrame;
import amrac.entities.PlaneEntity;

public final class PlaneCameraController {
    private static final double FIXED_EXTERNAL_CAMERA_DISTANCE = 7.5D;
    private static final double FIXED_EXTERNAL_CAMERA_HEIGHT = 0.75D;
    private static final double CHASE_ATTITUDE_HALF_LIFE_SECONDS = 0.11D;
    private static final double CHASE_MAX_LAG_DEGREES = 32.0D;
    private static final double CHASE_MIN_LAG_DOT =
        Math.cos(Math.toRadians(CHASE_MAX_LAG_DEGREES * 0.5D));
    private static final double CHASE_RESET_GAP_SECONDS = 0.25D;
    private static final double FIXED_THIRD_PERSON_FOCUS_LEAD = 2.0D;
    private static final double FREE_EXTERNAL_CAMERA_DISTANCE = 4.0D;

    private static final int FREE_LOOK_MAX_YAW = 105;
    private static final int FREE_LOOK_MAX_PITCH = 45;

    private static boolean fixedPlaneView;
    private static int fixedViewPlaneId = -1;
    private static float fixedViewYaw;
    private static float fixedViewPitch;

    private static Quaternionf chaseAttitude = new Quaternionf();
    private static int chasePlaneId = -1;
    private static boolean chaseInitialized;
    private static long chaseUpdateNanos;

    private static float cameraRoll;

    private PlaneCameraController() {
    }

    public static boolean isFixedView() {
        return fixedPlaneView;
    }

    public static boolean isMouseFlyingActive() {
        return fixedPlaneView && !PlaneViewState.isPassengerInteraction();
    }

    public static void beginFlight(LocalPlayer player, PlaneEntity plane) {
        resetChase();
        fixedPlaneView = PlaneKeyBindings.mouseStick();
        if (fixedPlaneView) {
            fixedViewPlaneId = plane.getId();
            alignFixedView(player, plane);
        } else {
            fixedViewPlaneId = -1;
        }
    }

    public static void toggleFixedView(LocalPlayer player, PlaneEntity plane) {
        fixedPlaneView = !fixedPlaneView;
        resetChase();
        if (fixedPlaneView) {
            fixedViewPlaneId = plane.getId();
            alignFixedView(player, plane);
        } else {
            fixedViewPlaneId = -1;
        }
    }

    public static void resetFixedView() {
        fixedPlaneView = false;
        fixedViewPlaneId = -1;
        cameraRoll = 0.0F;
        resetChase();
    }

    public static void resetChase() {
        chasePlaneId = -1;
        chaseInitialized = false;
        chaseUpdateNanos = 0L;
    }

    public static void alignFixedView(LocalPlayer player, PlaneEntity plane) {
        fixedViewYaw = Mth.wrapDegrees(plane.getYRot());
        fixedViewPitch = 0.0F;
        lockPlayerView(player);
    }

    public static void tickFixedView(LocalPlayer player, PlaneEntity plane) {
        if (!isMouseFlyingActive()) {
            return;
        }
        if (fixedViewPlaneId != plane.getId()) {
            fixedViewPlaneId = plane.getId();
            alignFixedView(player, plane);
            return;
        }
        lockPlayerView(player);
    }

    public static void lockPlayerView(LocalPlayer player) {
        if (player == null) {
            return;
        }
        player.setYRot(fixedViewYaw);
        player.yRotO = fixedViewYaw;
        player.setXRot(fixedViewPitch);
        player.xRotO = fixedViewPitch;
        player.setYHeadRot(fixedViewYaw);
        player.yHeadRotO = fixedViewYaw;
        player.setYBodyRot(fixedViewYaw);
        player.yBodyRotO = fixedViewYaw;
    }

    public static float cameraRoll() {
        return cameraRoll;
    }

    public interface CameraAccess {
        void position(Vec3 position);

        void rotation(float yRot, float xRot);

        void pitchOffset(float degrees);

        void boom(double distance);
    }

    public static boolean setup(CameraAccess access, Entity cameraEntity,
                                boolean detached, float partialTicks) {
        if (!(cameraEntity instanceof LocalPlayer player) ||
            !(player.getVehicle() instanceof PlaneEntity plane)) {
            cameraRoll = 0.0F;
            resetChase();
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        CameraType cameraType = minecraft.options.getCameraType();
        boolean firstPerson = cameraType == CameraType.FIRST_PERSON;
        boolean useFixedView = isMouseFlyingActive();
        double thirdPersonScale = plane.getCameraDistanceMultiplayer() *
            PlaneKeyBindings.thirdPersonDistanceMultiplier();
        Quaternionf attitude = MathUtil.lerpQ(partialTicks, plane.getQ_Prev(),
            plane.getQ_Client());

        if (!useFixedView || !detached) {
            resetChase();
        }

        double externalHeight = useFixedView && detached
            ? FIXED_EXTERNAL_CAMERA_HEIGHT * thirdPersonScale
            : 0.0D;
        Vec3 anchor = !useFixedView && detached
            ? freeExternalAnchor(plane, player, partialTicks)
            : PlaneVisualFrame.visualAnchor(plane, attitude, partialTicks,
                firstPerson,
                PlaneVisualFrame.passengerEyeHeight(plane, player) + externalHeight);

        double focusLead = 0.0D;
        boolean chaseFraming = useFixedView && cameraType == CameraType.THIRD_PERSON_BACK;
        if (chaseFraming) {
            focusLead = FIXED_THIRD_PERSON_FOCUS_LEAD * thirdPersonScale;
            anchor = anchor.add(PlaneVisualFrame.rotateToWorld(attitude, 0.0D,
                0.0D, focusLead));
        }
        access.position(anchor);

        if (useFixedView) {
            applyFixedRotation(access, plane, attitude, detached, firstPerson,
                cameraType == CameraType.THIRD_PERSON_FRONT, focusLead,
                thirdPersonScale);
            return true;
        }

        if (detached) {
            cameraRoll = 0.0F;
            access.pitchOffset(PlaneKeyBindings.thirdPersonVerticalAngle());
            access.boom(FREE_EXTERNAL_CAMERA_DISTANCE * thirdPersonScale);
            return true;
        }

        applyFreeLookRotation(access, plane, player, partialTicks);
        return true;
    }

    private static void applyFixedRotation(CameraAccess access, PlaneEntity plane,
                                           Quaternionf attitude, boolean detached,
                                           boolean firstPerson, boolean frontFacing,
                                           double focusLead,
                                           double thirdPersonScale) {
        Quaternionf cameraAttitude = detached
            ? chaseAttitude(plane, attitude) : new Quaternionf(attitude);
        if (frontFacing) {
            cameraAttitude.rotateY((float) Math.PI);
        }

        MathUtil.EulerAngles angles = MathUtil.toEulerAngles(cameraAttitude);
        float cameraPitch = -(float) angles.pitch;
        if (detached) {
            cameraPitch = Mth.clamp(cameraPitch +
                PlaneKeyBindings.thirdPersonVerticalAngle(), -89.0F, 89.0F);
        }
        float cameraYaw = (float) angles.yaw;
        cameraRoll = FixedCameraRollController.resolveCameraRoll(firstPerson,
            PlaneKeyBindings.cameraFollowsPlaneRoll(), -(float) angles.roll);
        access.rotation(cameraYaw, cameraPitch);

        if (detached) {
            double distance = FIXED_EXTERNAL_CAMERA_DISTANCE * thirdPersonScale;
            access.boom(distance + focusLead);
        }
    }

    private static void applyFreeLookRotation(CameraAccess access, PlaneEntity plane,
                                              LocalPlayer player, float partialTicks) {
        float pitchLimit = Mth.clamp(
            Mth.lerp(partialTicks, player.xRotO, player.getXRot()),
            -FREE_LOOK_MAX_PITCH, FREE_LOOK_MAX_PITCH);

        Quaternionf previous = plane.getQ_Prev();
        float previousYawDifference = (float) Mth.clamp(
            MathUtil.wrapSubtractDegrees(plane.yRotO, player.yRotO),
            -FREE_LOOK_MAX_YAW, FREE_LOOK_MAX_YAW);
        previous.rotateY((float) Math.toRadians(previousYawDifference));
        previous.rotateX((float) Math.toRadians(pitchLimit));
        MathUtil.EulerAngles previousAngles = MathUtil.toEulerAngles(previous);

        Quaternionf current = plane.getQ_Client();
        float yawDifference = (float) Mth.clamp(
            MathUtil.wrapSubtractDegrees(plane.getYRot(), player.getYRot()),
            -FREE_LOOK_MAX_YAW, FREE_LOOK_MAX_YAW);
        current.rotateY((float) Math.toRadians(yawDifference));
        current.rotateX((float) Math.toRadians(pitchLimit));
        MathUtil.EulerAngles angles = MathUtil.toEulerAngles(current);

        float cameraPitch = -(float) MathUtil.lerpAngle180(partialTicks,
            previousAngles.pitch, angles.pitch);
        float cameraYaw = (float) MathUtil.lerpAngle(partialTicks,
            previousAngles.yaw, angles.yaw);
        cameraRoll = FixedCameraRollController.resolveCameraRoll(false,
            PlaneKeyBindings.cameraFollowsPlaneRoll(),
            -(float) MathUtil.lerpAngle(partialTicks, previousAngles.roll,
                angles.roll));
        access.rotation(cameraYaw, cameraPitch);
    }

    private static Vec3 freeExternalAnchor(PlaneEntity plane, LocalPlayer player,
                                           float partialTicks) {
        return PlaneVisualFrame.interpolatedRenderPosition(plane, partialTicks)
            .add(0.0D, PlaneVisualFrame.passengerEyeHeight(plane, player), 0.0D);
    }

    private static Quaternionf chaseAttitude(PlaneEntity plane, Quaternionf target) {
        long now = System.nanoTime();
        if (!chaseInitialized || chasePlaneId != plane.getId()) {
            chaseAttitude = new Quaternionf(target);
            chasePlaneId = plane.getId();
            chaseInitialized = true;
            chaseUpdateNanos = now;
            return new Quaternionf(chaseAttitude);
        }

        double frameSeconds = (now - chaseUpdateNanos) * 1.0E-9D;
        chaseUpdateNanos = now;
        if (!Double.isFinite(frameSeconds) || frameSeconds <= 0.0D) {
            return new Quaternionf(chaseAttitude);
        }
        if (frameSeconds > CHASE_RESET_GAP_SECONDS) {
            chaseAttitude = new Quaternionf(target);
            return new Quaternionf(chaseAttitude);
        }

        double follow = 1.0D - Math.pow(0.5D,
            frameSeconds / CHASE_ATTITUDE_HALF_LIFE_SECONDS);
        chaseAttitude = MathUtil.lerpQ((float) follow, chaseAttitude, target);

        double dot = absoluteDot(chaseAttitude, target);
        if (dot < CHASE_MIN_LAG_DOT) {
            double lagDegrees = Math.toDegrees(2.0D * Math.acos(dot));
            double catchUp = 1.0D - CHASE_MAX_LAG_DEGREES / lagDegrees;
            chaseAttitude = MathUtil.lerpQ((float) catchUp, chaseAttitude, target);
        }
        return new Quaternionf(chaseAttitude);
    }

    private static double absoluteDot(Quaternionf first, Quaternionf second) {
        double dot = Math.abs(first.x() * second.x() + first.y() * second.y() +
            first.z() * second.z() + first.w() * second.w());
        return Mth.clamp(dot, 0.0D, 1.0D);
    }
}

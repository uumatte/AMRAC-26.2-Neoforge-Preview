package amrac.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import amrac.entities.PlaneEntity;

public final class PlaneViewState {
    private static boolean passengerInteraction;
    private static boolean rearViewHeld;
    private static boolean rearViewKeyWasDown;
    private static int managedPlaneId = -1;
    private static CameraType cameraTypeBeforePlane;

    private PlaneViewState() {
    }

    public static boolean isPassengerInteraction() {
        return passengerInteraction;
    }

    public static boolean isPilotingInputActive(Minecraft minecraft) {
        return minecraft.gui.screen() == null && !passengerInteraction &&
            minecraft.player != null &&
            minecraft.player.getVehicle() instanceof PlaneEntity plane &&
            plane.getControllingPassenger() == minecraft.player;
    }

    public static void tick(Minecraft minecraft, PlaneEntity plane) {
        if (managedPlaneId != plane.getId()) {
            if (managedPlaneId < 0 && cameraTypeBeforePlane == null) {
                cameraTypeBeforePlane = minecraft.options.getCameraType();
            }
            passengerInteraction = false;
            rearViewHeld = false;
            managedPlaneId = plane.getId();
            setCameraType(minecraft, CameraType.THIRD_PERSON_BACK);
            rearViewKeyWasDown = PlaneKeyBindings.REAR_VIEW.isDown();
        }

        suppressVanillaPerspective(minecraft);
        forceChaseView(minecraft);

        if (passengerInteraction &&
            minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            setPassengerInteraction(false);
        }
        if (rearViewHeld &&
            minecraft.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) {
            rearViewHeld = false;
        }
        if (minecraft.gui.screen() != null) {
            return;
        }

        boolean rearViewDown = PlaneKeyBindings.REAR_VIEW.isDown();
        if (rearViewDown == rearViewKeyWasDown) {
            return;
        }
        rearViewKeyWasDown = rearViewDown;
        if (rearViewDown) {
            onRearViewPressed(minecraft);
        } else if (rearViewHeld) {
            rearViewHeld = false;
            setCameraType(minecraft, CameraType.THIRD_PERSON_BACK);
        }
    }

    public static void reset(Minecraft minecraft) {
        CameraType restore = cameraTypeBeforePlane;
        cameraTypeBeforePlane = null;
        if (restore != null) {
            setCameraType(minecraft, restore);
        } else if (rearViewHeld &&
            minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            setCameraType(minecraft, CameraType.THIRD_PERSON_BACK);
        }
        passengerInteraction = false;
        rearViewHeld = false;
        rearViewKeyWasDown = false;
        managedPlaneId = -1;
    }

    private static void onRearViewPressed(Minecraft minecraft) {
        passengerInteraction = false;
        rearViewHeld = true;
        setCameraType(minecraft, CameraType.THIRD_PERSON_FRONT);
    }

    private static void setPassengerInteraction(boolean enabled) {
        if (passengerInteraction == enabled) {
            return;
        }
        passengerInteraction = enabled;
        PlaneCameraController.resetChase();
        PlaneClientControls.onPassengerInteractionChanged(enabled);
    }

    public static void forceChaseView(Minecraft minecraft) {
        if (minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            return;
        }
        rearViewHeld = false;
        setPassengerInteraction(false);
        setCameraType(minecraft, CameraType.THIRD_PERSON_BACK);
    }

    public static void suppressVanillaPerspective(Minecraft minecraft) {
        boolean pressed = false;
        while (minecraft.options.keyTogglePerspective.consumeClick()) {
            pressed = true;
        }
        minecraft.options.keyTogglePerspective.setDown(false);
        if (pressed && minecraft.gui.screen() == null) {
            forceChaseView(minecraft);
        }
    }

    private static void setCameraType(Minecraft minecraft, CameraType cameraType) {
        CameraType previous = minecraft.options.getCameraType();
        if (previous == cameraType) {
            return;
        }
        minecraft.options.setCameraType(cameraType);
        if (previous.isFirstPerson() != cameraType.isFirstPerson()) {
            minecraft.gameRenderer.checkEntityPostEffect(
                cameraType.isFirstPerson() ? minecraft.getCameraEntity() : null);
        }
        PlaneCameraController.resetChase();
    }

    public static PlaneEntity ridingPlane(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player != null && player.getVehicle() instanceof PlaneEntity plane
            ? plane : null;
    }
}

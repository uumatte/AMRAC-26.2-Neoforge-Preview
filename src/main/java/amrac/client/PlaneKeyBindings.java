package amrac.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.Nullable;
import amrac.mixin.KeyMappingAccessor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aircraft bindings (all but OPEN_CONTROLS) are taken out of the vanilla KeyMapping registry right
 * after construction and dispatched by KeyMappingDispatchMixin. Put them back, or change the
 * mixin's dispatch, and an aircraft key silently steals the vanilla key it shares (W stops
 * walking).
 */
public final class PlaneKeyBindings {
    public static final int MIN_MOUSE_SENSITIVITY = 0;
    public static final int MAX_MOUSE_SENSITIVITY = 100;
    public static final int DEFAULT_MOUSE_SENSITIVITY = 50;
    public static final int VERTICAL_SENSITIVITY_CEILING = 50;
    public static final int DEFAULT_MOUSE_VERTICAL_SENSITIVITY = 40;
    public static final int MIN_KEYBOARD_SENSITIVITY = 0;
    public static final int MAX_KEYBOARD_SENSITIVITY = 100;
    public static final int DEFAULT_KEYBOARD_SENSITIVITY = 67;

    public static final int MIN_PITCH_AUTHORITY = 0;
    public static final int MAX_PITCH_AUTHORITY = 100;
    public static final int DEFAULT_PITCH_AUTHORITY = 100;

    public static final int MIN_PITCH_AUTHORITY_STEP = 0;
    public static final int MAX_PITCH_AUTHORITY_STEP = 25;
    public static final int DEFAULT_PITCH_AUTHORITY_STEP = 8;
    public static final int MIN_THIRD_PERSON_DISTANCE = 25;
    public static final int MAX_THIRD_PERSON_DISTANCE = 250;
    public static final int DEFAULT_THIRD_PERSON_DISTANCE = 90;
    public static final int MIN_THIRD_PERSON_VERTICAL_ANGLE = -45;
    public static final int MAX_THIRD_PERSON_VERTICAL_ANGLE = 60;
    public static final int DEFAULT_THIRD_PERSON_VERTICAL_ANGLE = 0;

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("amrac", "controls"));
    public static final String SECTION_FLIGHT = "amrac.controls.category.flight";
    public static final String SECTION_WEAPONS = "amrac.controls.category.weapons";
    public static final String SECTION_VIEW = "amrac.controls.category.view";
    public static final String SECTION_UTILITY = "amrac.controls.category.utility";
    public static final String SECTION_MOUSE = "amrac.controls.category.mouse";
    public static final String SECTION_KEYBOARD = "amrac.controls.category.keyboard";
    public static final String SECTION_CAMERA = "amrac.controls.category.camera";
    public static final String SECTION_ASSISTANCE = "amrac.controls.category.assistance";

    private static final Logger LOGGER = LoggerFactory.getLogger("amrac-controls");
    private static final String CONFIG_FILE_NAME = "amrac-controls.txt";

    private static final String INVERT_VERTICAL_MOUSE_OPTION = "invertVerticalMouse";
    private static final String MOUSE_SENSITIVITY_OPTION = "mouseSensitivity";
    private static final String MOUSE_SENSITIVITY_VERTICAL_OPTION =
        "mouseSensitivityVertical";
    private static final String PITCH_AUTHORITY_OPTION = "pitchAuthority";
    private static final String PITCH_AUTHORITY_STEP_OPTION = "pitchAuthorityStep";
    private static final String MOUSE_SENSITIVITY_RULE_OPTION = "mouseSensitivityRule";
    private static final String KEYBOARD_SENSITIVITY_OPTION = "keyboardSensitivity";
    private static final String AUTO_THROTTLE_OPTION = "autoThrottle";
    private static final String CAMERA_FOLLOWS_PLANE_ROLL_OPTION = "cameraFollowsPlaneRoll";
    private static final String THIRD_PERSON_DISTANCE_OPTION = "thirdPersonDistance";
    private static final String THIRD_PERSON_VERTICAL_ANGLE_OPTION =
        "thirdPersonVerticalAngle";
    private static final String MOUSE_STICK_OPTION = "mouseStick";
    private static final String HORIZONTAL_MOUSE_STICK_OPTION =
        "horizontalMouseStick";
    private static final String CONFIG_VERSION_OPTION = "configVersion";

    private static final int FIRST_CONFIG_VERSION = 1;

    /**
     * Bump when a default changes and a saved value could not be told apart from the new default.
     */
    private static final int CONFIG_VERSION = 5;

    private static final boolean DEFAULT_INVERT_VERTICAL_MOUSE = false;
    private static final boolean DEFAULT_AUTO_THROTTLE = false;
    private static final boolean DEFAULT_CAMERA_FOLLOWS_PLANE_ROLL = true;
    private static final boolean DEFAULT_MOUSE_STICK = true;
    private static final MouseSensitivityRule DEFAULT_MOUSE_SENSITIVITY_RULE =
        MouseSensitivityRule.VELOCITY;
    private static final HorizontalStickPolicy.Axis DEFAULT_HORIZONTAL_MOUSE_AXIS =
        HorizontalStickPolicy.DEFAULT_AXIS;

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, Entry> ENTRIES_BY_NAME = new LinkedHashMap<>();
    private static final Map<InputConstants.Key, List<KeyMapping>> SHARED_BY_KEY =
        new HashMap<>();

    private static final Map<String, List<String>> SUPERSEDED_DEFAULTS =
        Map.ofEntries(
            Map.entry("key.amrac.pitch_up", List.of("key.keyboard.unknown")),
            Map.entry("key.amrac.pitch_down", List.of("key.keyboard.unknown")),
            Map.entry("key.amrac.roll_left",
                List.of("key.keyboard.unknown", "key.keyboard.a")),
            Map.entry("key.amrac.roll_right",
                List.of("key.keyboard.unknown", "key.keyboard.d")),
            Map.entry("key.amrac.yaw_left", List.of("key.keyboard.left")),
            Map.entry("key.amrac.yaw_right", List.of("key.keyboard.right")),
            Map.entry("key.amrac.throttle_up", List.of("key.keyboard.up")),
            Map.entry("key.amrac.throttle_down", List.of("key.keyboard.down")),
            Map.entry("key.amrac.auto_throttle",
                List.of("key.keyboard.t", "key.keyboard.e")),
            Map.entry("key.amrac.wheel_brake", List.of("key.keyboard.k")),
            Map.entry("key.amrac.radar", List.of("key.keyboard.v")),
            Map.entry("key.amrac.cycle_weapon", List.of("key.keyboard.r")),
            Map.entry("key.amrac.release_chaff", List.of("key.keyboard.unknown")),
            Map.entry("key.amrac.release_flare", List.of("key.keyboard.unknown")),
            Map.entry("key.amrac.flight_data", List.of("key.keyboard.unknown")),
            Map.entry("key.amrac.launch_missile", List.of("key.keyboard.f")),
            Map.entry("key.amrac.rear_view",
                List.of("key.keyboard.h", "key.keyboard.c")));

    private static boolean invertVerticalMouse = DEFAULT_INVERT_VERTICAL_MOUSE;
    private static int mouseSensitivity = DEFAULT_MOUSE_SENSITIVITY;
    private static int mouseVerticalSensitivity =
        DEFAULT_MOUSE_VERTICAL_SENSITIVITY;
    private static boolean sawVerticalSensitivity;
    private static int pitchAuthority = DEFAULT_PITCH_AUTHORITY;
    private static int pitchAuthorityStep = DEFAULT_PITCH_AUTHORITY_STEP;
    private static MouseSensitivityRule mouseSensitivityRule = DEFAULT_MOUSE_SENSITIVITY_RULE;
    private static int keyboardSensitivity = DEFAULT_KEYBOARD_SENSITIVITY;
    private static boolean autoThrottle = DEFAULT_AUTO_THROTTLE;
    private static boolean cameraFollowsPlaneRoll = DEFAULT_CAMERA_FOLLOWS_PLANE_ROLL;
    private static int thirdPersonDistance = DEFAULT_THIRD_PERSON_DISTANCE;
    private static int thirdPersonVerticalAngle = DEFAULT_THIRD_PERSON_VERTICAL_ANGLE;
    private static boolean mouseStick = DEFAULT_MOUSE_STICK;
    private static HorizontalStickPolicy.Axis horizontalMouseAxis =
        DEFAULT_HORIZONTAL_MOUSE_AXIS;
    private static int loadedVersion = CONFIG_VERSION;

    public static final KeyMapping THROTTLE_UP = create(
        "key.amrac.throttle_up", SECTION_FLIGHT, GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping THROTTLE_DOWN = create(
        "key.amrac.throttle_down", SECTION_FLIGHT, GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping PITCH_UP = create(
        "key.amrac.pitch_up", SECTION_FLIGHT, GLFW.GLFW_KEY_S);
    public static final KeyMapping PITCH_DOWN = create(
        "key.amrac.pitch_down", SECTION_FLIGHT, GLFW.GLFW_KEY_W);
    public static final KeyMapping ROLL_LEFT = create(
        "key.amrac.roll_left", SECTION_FLIGHT, GLFW.GLFW_KEY_Q);
    public static final KeyMapping ROLL_RIGHT = create(
        "key.amrac.roll_right", SECTION_FLIGHT, GLFW.GLFW_KEY_E);
    public static final KeyMapping YAW_LEFT = create(
        "key.amrac.yaw_left", SECTION_FLIGHT, GLFW.GLFW_KEY_A);
    public static final KeyMapping YAW_RIGHT = create(
        "key.amrac.yaw_right", SECTION_FLIGHT, GLFW.GLFW_KEY_D);
    public static final KeyMapping AUTO_THROTTLE = create(
        "key.amrac.auto_throttle", SECTION_FLIGHT, GLFW.GLFW_KEY_T);

    public static final KeyMapping SPEED_BRAKE = create(
        "key.amrac.speed_brake", SECTION_FLIGHT, GLFW.GLFW_KEY_H);

    public static final KeyMapping WHEEL_BRAKE = create(
        "key.amrac.wheel_brake", SECTION_FLIGHT, GLFW.GLFW_KEY_LEFT_CONTROL);

    public static final KeyMapping GEAR = create(
        "key.amrac.gear", SECTION_FLIGHT, GLFW.GLFW_KEY_G);

    public static final KeyMapping GPS = create(
        "key.amrac.gps", SECTION_FLIGHT, GLFW.GLFW_KEY_M);

    public static final KeyMapping AI_DEBUG = create(
        "key.amrac.ai_debug", SECTION_FLIGHT, GLFW.GLFW_KEY_F8);

    public static final KeyMapping GPS_RANGE = create(
        "key.amrac.gps_range", SECTION_FLIGHT, GLFW.GLFW_KEY_N);

    public static final KeyMapping FLAPS = create(
        "key.amrac.flaps", SECTION_FLIGHT, GLFW.GLFW_KEY_F);

    public static final KeyMapping FUEL_DUMP = create(
        "key.amrac.fuel_dump", SECTION_FLIGHT, GLFW.GLFW_KEY_J);

    public static final KeyMapping AOA_LIMITER = create(
        "key.amrac.aoa_limiter", SECTION_FLIGHT, GLFW.GLFW_KEY_L);

    public static final KeyMapping STICK_LIMITER = create(
        "key.amrac.stick_limiter", SECTION_FLIGHT, GLFW.GLFW_KEY_1);

    public static final KeyMapping AUTO_LEVEL = create(
        "key.amrac.auto_level", SECTION_FLIGHT, GLFW.GLFW_KEY_2);

    public static final KeyMapping FLIGHT_DATA = create(
        "key.amrac.flight_data", SECTION_UTILITY, GLFW.GLFW_KEY_Y);

    public static final KeyMapping RADAR = create(
        "key.amrac.radar", SECTION_WEAPONS, GLFW.GLFW_KEY_R);

    public static final KeyMapping SEEKER_VIEW = create(
        "key.amrac.seeker_view", SECTION_WEAPONS, GLFW.GLFW_KEY_Z);

    public static final KeyMapping SELECT_WEAPON_ONE = create(
        "key.amrac.select_weapon_1", SECTION_WEAPONS, GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping SELECT_WEAPON_TWO = create(
        "key.amrac.select_weapon_2", SECTION_WEAPONS, GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping CYCLE_WEAPON = create(
        "key.amrac.cycle_weapon", SECTION_WEAPONS, GLFW.GLFW_KEY_SLASH);

    public static final KeyMapping MISSILE_SWITCH = create(
        "key.amrac.missile_switch", SECTION_WEAPONS, GLFW.GLFW_KEY_X);

    public static final KeyMapping LAUNCH_MISSILE = create(
        "key.amrac.launch_missile", SECTION_WEAPONS, GLFW.GLFW_KEY_SPACE);

    public static final KeyMapping RELEASE_CHAFF = create(
        "key.amrac.release_chaff", SECTION_WEAPONS, GLFW.GLFW_KEY_C);
    public static final KeyMapping RELEASE_FLARE = create(
        "key.amrac.release_flare", SECTION_WEAPONS, GLFW.GLFW_KEY_V);

    public static final KeyMapping TOGGLE_FIXED_VIEW = create(
        "key.amrac.toggle_fixed_view", SECTION_VIEW, GLFW.GLFW_KEY_B);
    public static final KeyMapping REAR_VIEW = create(
        "key.amrac.rear_view", SECTION_VIEW, InputConstants.Type.MOUSE,
        InputConstants.MOUSE_BUTTON_RIGHT);

    public static final KeyMapping DISMOUNT = create(
        "key.amrac.dismount", SECTION_UTILITY, GLFW.GLFW_KEY_LEFT_ALT);

    public static final KeyMapping OPEN_CONTROLS = create(
        "key.amrac.open_controls", SECTION_UTILITY, GLFW.GLFW_KEY_I,
        true);

    static {
        KeyMapping.resetMapping();
        rebuildSharedIndex();
    }

    private PlaneKeyBindings() {
    }

    private static KeyMapping create(String name, String section, int defaultKey) {
        return create(name, section, InputConstants.Type.KEYSYM, defaultKey,
            false);
    }

    private static KeyMapping create(String name, String section, int defaultKey,
                                     boolean vanillaManaged) {
        return create(name, section, InputConstants.Type.KEYSYM, defaultKey,
            vanillaManaged);
    }

    private static KeyMapping create(String name, String section,
                                     InputConstants.Type type, int defaultKey) {
        return create(name, section, type, defaultKey, false);
    }

    private static KeyMapping create(String name, String section,
                                     InputConstants.Type type, int defaultKey,
                                     boolean vanillaManaged) {
        if (ENTRIES_BY_NAME.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate AMRAC binding " + name);
        }
        KeyMapping binding = new KeyMapping(name, type, defaultKey, CATEGORY);
        if (!vanillaManaged) {
            KeyMappingAccessor.amrac$all().remove(name, binding);
        }
        Entry entry = new Entry(binding, section, vanillaManaged);
        ENTRIES.add(entry);
        ENTRIES_BY_NAME.put(name, entry);
        return binding;
    }

    public static List<Entry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static boolean invertVerticalMouse() {
        return invertVerticalMouse;
    }

    public static void setInvertVerticalMouse(boolean inverted) {
        invertVerticalMouse = inverted;
        save();
    }

    public static boolean isInvertVerticalMouseDefault() {
        return invertVerticalMouse == DEFAULT_INVERT_VERTICAL_MOUSE;
    }

    public static void resetInvertVerticalMouse() {
        setInvertVerticalMouse(DEFAULT_INVERT_VERTICAL_MOUSE);
    }

    public static int mouseSensitivity() {
        return mouseSensitivity;
    }

    public static double mouseSensitivityMultiplier() {
        return (double) mouseSensitivity / DEFAULT_MOUSE_SENSITIVITY;
    }

    public static void previewMouseSensitivity(int sensitivity) {
        mouseSensitivity = Math.max(MIN_MOUSE_SENSITIVITY,
            Math.min(MAX_MOUSE_SENSITIVITY, sensitivity));
    }

    public static void setMouseSensitivity(int sensitivity) {
        previewMouseSensitivity(sensitivity);
        save();
    }

    public static boolean isMouseSensitivityDefault() {
        return mouseSensitivity == DEFAULT_MOUSE_SENSITIVITY;
    }

    public static void resetMouseSensitivity() {
        setMouseSensitivity(DEFAULT_MOUSE_SENSITIVITY);
    }

    public static int mouseVerticalSensitivity() {
        return mouseVerticalSensitivity;
    }

    public static double mouseVerticalSensitivityMultiplier() {
        return (double) mouseVerticalSensitivity * VERTICAL_SENSITIVITY_CEILING
            / (MAX_MOUSE_SENSITIVITY * (double) DEFAULT_MOUSE_SENSITIVITY);
    }

    public static void previewMouseVerticalSensitivity(int sensitivity) {
        mouseVerticalSensitivity = Math.max(MIN_MOUSE_SENSITIVITY,
            Math.min(MAX_MOUSE_SENSITIVITY, sensitivity));
    }

    public static void setMouseVerticalSensitivity(int sensitivity) {
        previewMouseVerticalSensitivity(sensitivity);
        save();
    }

    public static boolean isMouseVerticalSensitivityDefault() {
        return mouseVerticalSensitivity == DEFAULT_MOUSE_VERTICAL_SENSITIVITY;
    }

    public static void resetMouseVerticalSensitivity() {
        setMouseVerticalSensitivity(DEFAULT_MOUSE_VERTICAL_SENSITIVITY);
    }

    public static int pitchAuthority() {
        return pitchAuthority;
    }

    public static void previewPitchAuthority(int authority) {
        pitchAuthority = Math.max(MIN_PITCH_AUTHORITY,
            Math.min(MAX_PITCH_AUTHORITY, authority));
    }

    public static void setPitchAuthority(int authority) {
        previewPitchAuthority(authority);
        save();
    }

    public static boolean isPitchAuthorityDefault() {
        return pitchAuthority == DEFAULT_PITCH_AUTHORITY;
    }

    public static void resetPitchAuthority() {
        setPitchAuthority(DEFAULT_PITCH_AUTHORITY);
    }

    public static boolean nudgePitchAuthority(double notches) {
        if (!Double.isFinite(notches) || pitchAuthorityStep <= 0) {
            return false;
        }
        int before = pitchAuthority;
        previewPitchAuthority(before +
            (int) Math.round(notches * pitchAuthorityStep));
        return pitchAuthority != before;
    }

    public static int pitchAuthorityStep() {
        return pitchAuthorityStep;
    }

    public static void previewPitchAuthorityStep(int step) {
        pitchAuthorityStep = Math.max(MIN_PITCH_AUTHORITY_STEP,
            Math.min(MAX_PITCH_AUTHORITY_STEP, step));
    }

    public static void setPitchAuthorityStep(int step) {
        previewPitchAuthorityStep(step);
        save();
    }

    public static boolean isPitchAuthorityStepDefault() {
        return pitchAuthorityStep == DEFAULT_PITCH_AUTHORITY_STEP;
    }

    public static void resetPitchAuthorityStep() {
        setPitchAuthorityStep(DEFAULT_PITCH_AUTHORITY_STEP);
    }

    public static void persistPitchAuthority() {
        save();
    }

    public static MouseSensitivityRule mouseSensitivityRule() {
        return mouseSensitivityRule;
    }

    public static void setMouseSensitivityRule(MouseSensitivityRule rule) {
        mouseSensitivityRule = rule == null ? DEFAULT_MOUSE_SENSITIVITY_RULE : rule;
        save();
    }

    public static boolean isMouseSensitivityRuleDefault() {
        return mouseSensitivityRule == DEFAULT_MOUSE_SENSITIVITY_RULE;
    }

    public static void resetMouseSensitivityRule() {
        setMouseSensitivityRule(DEFAULT_MOUSE_SENSITIVITY_RULE);
    }

    public static int keyboardSensitivity() {
        return keyboardSensitivity;
    }

    public static float keyboardSensitivityMultiplier() {
        return (float) keyboardSensitivity / MAX_KEYBOARD_SENSITIVITY;
    }

    public static void previewKeyboardSensitivity(int sensitivity) {
        keyboardSensitivity = Math.max(MIN_KEYBOARD_SENSITIVITY,
            Math.min(MAX_KEYBOARD_SENSITIVITY, sensitivity));
    }

    public static void setKeyboardSensitivity(int sensitivity) {
        previewKeyboardSensitivity(sensitivity);
        save();
    }

    public static boolean isKeyboardSensitivityDefault() {
        return keyboardSensitivity == DEFAULT_KEYBOARD_SENSITIVITY;
    }

    public static void resetKeyboardSensitivity() {
        setKeyboardSensitivity(DEFAULT_KEYBOARD_SENSITIVITY);
    }

    public static boolean autoThrottle() {
        return autoThrottle;
    }

    public static void setAutoThrottle(boolean enabled) {
        autoThrottle = enabled;
        save();
    }

    public static boolean isAutoThrottleDefault() {
        return autoThrottle == DEFAULT_AUTO_THROTTLE;
    }

    public static void resetAutoThrottle() {
        setAutoThrottle(DEFAULT_AUTO_THROTTLE);
    }

    public static boolean mouseStick() {
        return mouseStick;
    }

    public static void setMouseStick(boolean enabled) {
        mouseStick = enabled;
        save();
    }

    public static boolean isMouseStickDefault() {
        return mouseStick == DEFAULT_MOUSE_STICK;
    }

    public static void resetMouseStick() {
        setMouseStick(DEFAULT_MOUSE_STICK);
    }

    public static HorizontalStickPolicy.Axis horizontalMouseAxis() {
        return horizontalMouseAxis;
    }

    public static void setHorizontalMouseAxis(HorizontalStickPolicy.Axis axis) {
        horizontalMouseAxis = axis == null ? DEFAULT_HORIZONTAL_MOUSE_AXIS : axis;
        save();
    }

    public static boolean isHorizontalMouseAxisDefault() {
        return horizontalMouseAxis == DEFAULT_HORIZONTAL_MOUSE_AXIS;
    }

    public static void resetHorizontalMouseAxis() {
        setHorizontalMouseAxis(DEFAULT_HORIZONTAL_MOUSE_AXIS);
    }

    public static boolean cameraFollowsPlaneRoll() {
        return cameraFollowsPlaneRoll;
    }

    public static void setCameraFollowsPlaneRoll(boolean enabled) {
        cameraFollowsPlaneRoll = enabled;
        save();
    }

    public static boolean isCameraFollowsPlaneRollDefault() {
        return cameraFollowsPlaneRoll == DEFAULT_CAMERA_FOLLOWS_PLANE_ROLL;
    }

    public static void resetCameraFollowsPlaneRoll() {
        setCameraFollowsPlaneRoll(DEFAULT_CAMERA_FOLLOWS_PLANE_ROLL);
    }

    public static int thirdPersonDistance() {
        return thirdPersonDistance;
    }

    public static double thirdPersonDistanceMultiplier() {
        return thirdPersonDistance / 100.0D;
    }

    public static void previewThirdPersonDistance(int distance) {
        thirdPersonDistance = Math.max(MIN_THIRD_PERSON_DISTANCE,
            Math.min(MAX_THIRD_PERSON_DISTANCE, distance));
        PlaneCameraController.resetChase();
    }

    public static void setThirdPersonDistance(int distance) {
        previewThirdPersonDistance(distance);
        save();
    }

    public static boolean isThirdPersonDistanceDefault() {
        return thirdPersonDistance == DEFAULT_THIRD_PERSON_DISTANCE;
    }

    public static void resetThirdPersonDistance() {
        setThirdPersonDistance(DEFAULT_THIRD_PERSON_DISTANCE);
    }

    public static int thirdPersonVerticalAngle() {
        return thirdPersonVerticalAngle;
    }

    public static void previewThirdPersonVerticalAngle(int angle) {
        thirdPersonVerticalAngle = Math.max(MIN_THIRD_PERSON_VERTICAL_ANGLE,
            Math.min(MAX_THIRD_PERSON_VERTICAL_ANGLE, angle));
        PlaneCameraController.resetChase();
    }

    public static void setThirdPersonVerticalAngle(int angle) {
        previewThirdPersonVerticalAngle(angle);
        save();
    }

    public static boolean isThirdPersonVerticalAngleDefault() {
        return thirdPersonVerticalAngle == DEFAULT_THIRD_PERSON_VERTICAL_ANGLE;
    }

    public static void resetThirdPersonVerticalAngle() {
        setThirdPersonVerticalAngle(DEFAULT_THIRD_PERSON_VERTICAL_ANGLE);
    }

    public static void load() {
        resetOptionsToDefault();
        loadedVersion = CONFIG_VERSION;
        File file = configFile();
        if (!file.isFile()) {
            save();
            return;
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = utf8Reader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not load AMRAC controls from {}", file, exception);
            return;
        }

        loadedVersion = readVersion(lines);

        for (String line : lines) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                continue;
            }
            applyStoredLine(line.substring(0, separator),
                line.substring(separator + 1), line);
        }

        if (!sawVerticalSensitivity) {
            mouseVerticalSensitivity = mouseSensitivity;
        }
        sawVerticalSensitivity = false;

        KeyMapping.resetMapping();
        rebuildSharedIndex();
        if (loadedVersion < CONFIG_VERSION) {
            loadedVersion = CONFIG_VERSION;
            save();
        }
    }

    private static int readVersion(List<String> lines) {
        String prefix = CONFIG_VERSION_OPTION + "=";
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return parseRange(line.substring(prefix.length()), 1,
                    CONFIG_VERSION, FIRST_CONFIG_VERSION, line);
            }
        }
        return FIRST_CONFIG_VERSION;
    }

    private static void applyStoredLine(String name, String value, String sourceLine) {
        switch (name) {
            case INVERT_VERTICAL_MOUSE_OPTION ->
                invertVerticalMouse = parseBoolean(value, invertVerticalMouse, sourceLine);
            case AUTO_THROTTLE_OPTION ->
                autoThrottle = parseBoolean(value, autoThrottle, sourceLine);
            case CAMERA_FOLLOWS_PLANE_ROLL_OPTION ->
                cameraFollowsPlaneRoll = parseBoolean(value, cameraFollowsPlaneRoll, sourceLine);
            case THIRD_PERSON_DISTANCE_OPTION ->
                thirdPersonDistance = parseRange(value, MIN_THIRD_PERSON_DISTANCE,
                    MAX_THIRD_PERSON_DISTANCE, thirdPersonDistance, sourceLine);
            case THIRD_PERSON_VERTICAL_ANGLE_OPTION ->
                thirdPersonVerticalAngle = parseRange(value,
                    MIN_THIRD_PERSON_VERTICAL_ANGLE,
                    MAX_THIRD_PERSON_VERTICAL_ANGLE,
                    thirdPersonVerticalAngle, sourceLine);
            case MOUSE_STICK_OPTION ->
                mouseStick = parseBoolean(value, mouseStick, sourceLine);
            case MOUSE_SENSITIVITY_OPTION ->
                mouseSensitivity = parseRange(value, MIN_MOUSE_SENSITIVITY,
                    MAX_MOUSE_SENSITIVITY, mouseSensitivity, sourceLine);
            case MOUSE_SENSITIVITY_VERTICAL_OPTION -> {
                sawVerticalSensitivity = true;
                mouseVerticalSensitivity = parseRange(value, MIN_MOUSE_SENSITIVITY,
                    MAX_MOUSE_SENSITIVITY, mouseVerticalSensitivity, sourceLine);
            }
            case PITCH_AUTHORITY_OPTION ->
                pitchAuthority = parseRange(value, MIN_PITCH_AUTHORITY,
                    MAX_PITCH_AUTHORITY, pitchAuthority, sourceLine);
            case PITCH_AUTHORITY_STEP_OPTION ->
                pitchAuthorityStep = parseRange(value, MIN_PITCH_AUTHORITY_STEP,
                    MAX_PITCH_AUTHORITY_STEP, pitchAuthorityStep, sourceLine);
            case KEYBOARD_SENSITIVITY_OPTION ->
                keyboardSensitivity = parseRange(value, MIN_KEYBOARD_SENSITIVITY,
                    MAX_KEYBOARD_SENSITIVITY, keyboardSensitivity, sourceLine);
            case CONFIG_VERSION_OPTION -> { }
            case MOUSE_SENSITIVITY_RULE_OPTION -> {
                MouseSensitivityRule rule = MouseSensitivityRule.fromConfigName(value);
                if (rule == null) {
                    LOGGER.warn("Ignoring invalid AMRAC option: {}", sourceLine);
                } else {
                    mouseSensitivityRule = rule;
                }
            }
            case HORIZONTAL_MOUSE_STICK_OPTION -> {
                HorizontalStickPolicy.Axis axis = HorizontalStickPolicy.Axis.fromConfigName(value);
                if (axis == null) {
                    LOGGER.warn("Ignoring invalid AMRAC option: {}", sourceLine);
                } else {
                    horizontalMouseAxis = axis;
                }
            }
            default -> {
                Entry entry = ENTRIES_BY_NAME.get(name);
                if (entry != null && !entry.vanillaManaged) {
                    applyStoredKey(entry, value, sourceLine);
                }
            }
        }
    }

    public static void save() {
        File file = configFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            LOGGER.error("Could not create AMRAC config directory {}", parent);
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("# AMRAC aircraft controls and client options");
            writer.newLine();
            writeOption(writer, CONFIG_VERSION_OPTION,
                Integer.toString(CONFIG_VERSION));
            writeOption(writer, INVERT_VERTICAL_MOUSE_OPTION,
                Boolean.toString(invertVerticalMouse));
            writeOption(writer, MOUSE_SENSITIVITY_OPTION,
                Integer.toString(mouseSensitivity));
            writeOption(writer, MOUSE_SENSITIVITY_RULE_OPTION,
                mouseSensitivityRule.configName());
            writeOption(writer, MOUSE_SENSITIVITY_VERTICAL_OPTION,
                Integer.toString(mouseVerticalSensitivity));
            writeOption(writer, PITCH_AUTHORITY_OPTION,
                Integer.toString(pitchAuthority));
            writeOption(writer, PITCH_AUTHORITY_STEP_OPTION,
                Integer.toString(pitchAuthorityStep));
            writeOption(writer, KEYBOARD_SENSITIVITY_OPTION,
                Integer.toString(keyboardSensitivity));
            writeOption(writer, AUTO_THROTTLE_OPTION, Boolean.toString(autoThrottle));
            writeOption(writer, CAMERA_FOLLOWS_PLANE_ROLL_OPTION,
                Boolean.toString(cameraFollowsPlaneRoll));
            writeOption(writer, THIRD_PERSON_DISTANCE_OPTION,
                Integer.toString(thirdPersonDistance));
            writeOption(writer, THIRD_PERSON_VERTICAL_ANGLE_OPTION,
                Integer.toString(thirdPersonVerticalAngle));
            writeOption(writer, MOUSE_STICK_OPTION, Boolean.toString(mouseStick));
            writeOption(writer, HORIZONTAL_MOUSE_STICK_OPTION,
                horizontalMouseAxis.configName());
            for (Entry entry : ENTRIES) {
                if (entry.vanillaManaged) {
                    continue;
                }
                writeOption(writer, entry.binding.getName(), entry.binding.saveString());
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save AMRAC controls to {}", file, exception);
        }
    }

    public static void set(Entry entry, InputConstants.Key key) {
        entry.binding.setKey(key);
        applyBindingChange(entry.vanillaManaged);
    }

    public static void reset(Entry entry) {
        entry.binding.setKey(entry.binding.getDefaultKey());
        applyBindingChange(entry.vanillaManaged);
    }

    public static void resetAll() {
        resetOptionsToDefault();
        boolean touchedVanilla = false;
        for (Entry entry : ENTRIES) {
            entry.binding.setKey(entry.binding.getDefaultKey());
            touchedVanilla |= entry.vanillaManaged;
        }
        applyBindingChange(touchedVanilla);
    }

    private static void applyBindingChange(boolean touchedVanilla) {
        KeyMapping.resetMapping();
        rebuildSharedIndex();
        Minecraft minecraft = Minecraft.getInstance();
        if (touchedVanilla && minecraft.options != null) {
            minecraft.options.save();
        }
        save();
    }

    public static void releaseAll() {
        for (Entry entry : ENTRIES) {
            entry.binding.setDown(false);
            while (entry.binding.consumeClick()) {
            }
        }
    }

    public static boolean hasConflict(Entry target) {
        if (target.binding.isUnbound()) {
            return false;
        }
        for (Entry other : ENTRIES) {
            if (other != target && target.binding.same(other.binding)) {
                return true;
            }
        }
        return target.vanillaManaged && vanillaBindingOn(target) != null;
    }

    @Nullable
    public static Component sharedVanillaAction(Entry target) {
        KeyMapping vanilla = vanillaBindingOn(target);
        return vanilla == null ? null : Component.translatable(vanilla.getName());
    }

    @Nullable
    private static KeyMapping vanillaBindingOn(Entry target) {
        if (target.binding.isUnbound()) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            return null;
        }
        for (KeyMapping other : minecraft.options.keyMappings) {
            if (other != target.binding && target.binding.same(other)) {
                return other;
            }
        }
        return null;
    }

    private static boolean flightSuppression;

    private static Set<KeyMapping> exemptBindings;

    private static final Set<KeyMapping> latchedBindings =
        Collections.newSetFromMap(new IdentityHashMap<>());

    public static void setFlightSuppression(boolean active) {
        if (flightSuppression && !active) {
            latchHeldBindings();
        }
        flightSuppression = active;
    }

    public static void tickSuppression() {
        if (!latchedBindings.isEmpty()) {
            latchedBindings.removeIf(binding -> !isPhysicallyHeld(binding));
        }
        if (!flightSuppression && latchedBindings.isEmpty()) {
            return;
        }
        for (KeyMapping binding : KeyMappingAccessor.amrac$all().values()) {
            if (!isSuppressedWhileFlying(binding)) {
                continue;
            }
            KeyMappingAccessor accessor = (KeyMappingAccessor) (Object) binding;
            if (accessor.amrac$getClickCount() != 0) {
                accessor.amrac$setClickCount(0);
            }
        }
    }

    private static void latchHeldBindings() {
        for (KeyMapping binding : KeyMappingAccessor.amrac$all().values()) {
            if (isSuppressedWhileFlying(binding) && isPhysicallyHeld(binding)) {
                latchedBindings.add(binding);
            }
        }
    }

    private static boolean isPhysicallyHeld(KeyMapping binding) {
        if (binding.isUnbound()) {
            return false;
        }
        InputConstants.Key key =
            ((KeyMappingAccessor) (Object) binding).amrac$getKey();
        if (key == null || key.getType() != InputConstants.Type.KEYSYM) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow() != null &&
            InputConstants.isKeyDown(minecraft.getWindow(), key.getValue());
    }

    public static boolean isSuppressedWhileFlying(KeyMapping binding) {
        if (!flightSuppression) {
            return !latchedBindings.isEmpty() && latchedBindings.contains(binding);
        }
        Set<KeyMapping> exempt = exemptBindings;
        if (exempt == null) {
            exempt = buildExemptBindings();
            if (exempt == null) {
                return false;
            }
            exemptBindings = exempt;
        }
        return !exempt.contains(binding);
    }

    @Nullable
    private static Set<KeyMapping> buildExemptBindings() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options == null) {
            return null;
        }
        Set<KeyMapping> exempt = Collections.newSetFromMap(
            new IdentityHashMap<>());
        for (Entry entry : ENTRIES) {
            exempt.add(entry.binding);
        }
        Collections.addAll(exempt,
            minecraft.options.keyAttack, minecraft.options.keyUse,
            minecraft.options.keyPlayerList,
            minecraft.options.keyScreenshot, minecraft.options.keyFullscreen);
        return exempt;
    }

    public static void dispatchSet(InputConstants.Key key, boolean held) {
        List<KeyMapping> shared = SHARED_BY_KEY.get(key);
        if (shared == null) {
            return;
        }
        for (KeyMapping binding : shared) {
            binding.setDown(held);
        }
    }

    public static void dispatchClick(InputConstants.Key key) {
        List<KeyMapping> shared = SHARED_BY_KEY.get(key);
        if (shared == null) {
            return;
        }
        for (KeyMapping binding : shared) {
            KeyMappingAccessor accessor = (KeyMappingAccessor) (Object) binding;
            accessor.amrac$setClickCount(
                accessor.amrac$getClickCount() + 1);
        }
    }

    public static void syncAll() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return;
        }
        for (Entry entry : ENTRIES) {
            if (entry.vanillaManaged) {
                continue;
            }
            InputConstants.Key key = keyOf(entry.binding);
            if (key.getType() != InputConstants.Type.KEYSYM ||
                key.getValue() == InputConstants.UNKNOWN.getValue()) {
                continue;
            }
            entry.binding.setDown(InputConstants.isKeyDown(
                minecraft.getWindow(), key.getValue()));
        }
    }

    private static void rebuildSharedIndex() {
        SHARED_BY_KEY.clear();
        for (Entry entry : ENTRIES) {
            if (entry.vanillaManaged || entry.binding.isUnbound()) {
                continue;
            }
            SHARED_BY_KEY
                .computeIfAbsent(keyOf(entry.binding), key -> new ArrayList<>(2))
                .add(entry.binding);
        }
    }

    private static InputConstants.Key keyOf(KeyMapping binding) {
        return ((KeyMappingAccessor) (Object) binding).amrac$getKey();
    }

    public static boolean anyChanged() {
        if (!isInvertVerticalMouseDefault() || !isMouseSensitivityDefault() ||
            !isMouseSensitivityRuleDefault() || !isKeyboardSensitivityDefault() ||
            !isAutoThrottleDefault() || !isCameraFollowsPlaneRollDefault() ||
            !isThirdPersonDistanceDefault() ||
            !isThirdPersonVerticalAngleDefault() ||
            !isMouseStickDefault()) {
            return true;
        }
        for (Entry entry : ENTRIES) {
            if (!entry.binding.isDefault()) {
                return true;
            }
        }
        return false;
    }

    private static void resetOptionsToDefault() {
        invertVerticalMouse = DEFAULT_INVERT_VERTICAL_MOUSE;
        mouseSensitivity = DEFAULT_MOUSE_SENSITIVITY;
        mouseVerticalSensitivity = DEFAULT_MOUSE_VERTICAL_SENSITIVITY;
        mouseSensitivityRule = DEFAULT_MOUSE_SENSITIVITY_RULE;
        keyboardSensitivity = DEFAULT_KEYBOARD_SENSITIVITY;
        autoThrottle = DEFAULT_AUTO_THROTTLE;
        cameraFollowsPlaneRoll = DEFAULT_CAMERA_FOLLOWS_PLANE_ROLL;
        thirdPersonDistance = DEFAULT_THIRD_PERSON_DISTANCE;
        thirdPersonVerticalAngle = DEFAULT_THIRD_PERSON_VERTICAL_ANGLE;
        mouseStick = DEFAULT_MOUSE_STICK;
        horizontalMouseAxis = DEFAULT_HORIZONTAL_MOUSE_AXIS;
    }

    private static void applyStoredKey(Entry entry, String storedValue,
                                       String sourceLine) {
        List<String> superseded = loadedVersion < CONFIG_VERSION
            ? SUPERSEDED_DEFAULTS.get(entry.binding.getName()) : null;
        if (superseded != null && superseded.contains(storedValue)) {
            entry.binding.setKey(entry.binding.getDefaultKey());
            return;
        }
        try {
            entry.binding.setKey(InputConstants.getKey(storedValue));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Ignoring invalid AMRAC key binding: {}", sourceLine);
        }
    }

    private static boolean parseBoolean(String value, boolean fallback,
                                        String sourceLine) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        LOGGER.warn("Ignoring invalid AMRAC option: {}", sourceLine);
        return fallback;
    }

    private static int parseRange(String value, int minimum, int maximum,
                                  int fallback, String sourceLine) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new NumberFormatException("out of range");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            LOGGER.warn("Ignoring invalid AMRAC option: {}", sourceLine);
            return fallback;
        }
    }

    private static void writeOption(BufferedWriter writer, String name,
                                    String value) throws IOException {
        writer.write(name);
        writer.write('=');
        writer.write(value);
        writer.newLine();
    }

    private static BufferedReader utf8Reader(File file) throws IOException {
        return new BufferedReader(new InputStreamReader(
            new FileInputStream(file), StandardCharsets.UTF_8));
    }

    private static File configFile() {
        return new File(new File(Minecraft.getInstance().gameDirectory, "config"),
            CONFIG_FILE_NAME);
    }

    public enum MouseSensitivityRule {
        VELOCITY("velocity"),
        LINEAR("linear");

        private final String configName;

        MouseSensitivityRule(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public String translationKey() {
            return "amrac.controls.mouse_sensitivity_rule." + configName;
        }

        public MouseSensitivityRule next() {
            return this == VELOCITY ? LINEAR : VELOCITY;
        }

        static MouseSensitivityRule fromConfigName(String name) {
            for (MouseSensitivityRule rule : values()) {
                if (rule.configName.equalsIgnoreCase(name)) {
                    return rule;
                }
            }
            return null;
        }
    }

    public static final class Entry {
        private final KeyMapping binding;
        private final String section;
        private final boolean vanillaManaged;

        private Entry(KeyMapping binding, String section, boolean vanillaManaged) {
            this.binding = binding;
            this.section = section;
            this.vanillaManaged = vanillaManaged;
        }

        public KeyMapping binding() {
            return binding;
        }

        public String section() {
            return section;
        }
    }
}

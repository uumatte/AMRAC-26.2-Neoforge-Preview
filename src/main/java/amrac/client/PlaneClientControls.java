package amrac.client;

import amrac.platform.client.ClientTickEvents;
import amrac.platform.client.KeyMappingHelper;
import amrac.platform.client.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import amrac.client.gui.PlaneControlsScreen;
import amrac.entities.GroundProximityPolicy;
import amrac.entities.MachineGunBulletEntity;
import amrac.entities.PitchRampPolicy;
import amrac.entities.PlaneEntity;
import amrac.network.PlaneNetworking;
import amrac.client.SoundMixPolicy;
import amrac.upgrades.shooter.MachineGunFirePolicy;

public final class PlaneClientControls {
    private static final float THROTTLE_INCREASE_STEP = 20.0F / 7.0F;

    private static final double NO_STRUCTURAL_LIMIT = 0.0D;
    private static final float THROTTLE_DECREASE_STEP = 6.0F;

    // Public compile-time constants that the regression tests inline; tests must not keep their own
    // copies, or they keep checking old values.
    public static final double MOUSE_BASE_SENSITIVITY = 0.70D;
    public static final double MOUSE_DEAD_ZONE = 0.02D;
    public static final double MOUSE_FULL_INPUT_DISTANCE = 45.0D;
    public static final double MOUSE_INPUT_HALF_LIFE_SECONDS = 0.12D;
    private static final double MOUSE_DELTA_LIMIT = 180.0D;
    private static final double MOUSE_DELTA_EPSILON = 1.0E-6D;

    public static final float PLAYER_ROLL_INPUT_SCALE =
        PlaneEntity.DEFAULT_PLAYER_ROLL_INPUT_SCALE;

    public static final int WEAPON_SLOT_MACHINE_GUN = 1;
    public static final int WEAPON_SLOT_BOMBS = 2;
    public static final int WEAPON_SLOT_MISSILE = 3;

    private static boolean wasRiding;
    private static boolean speedBrakeHeld;
    private static boolean wheelBrakeHeld;
    private static final int PITCH_AUTHORITY_SAVE_DELAY_TICKS = 20;
    private static int pitchAuthoritySaveDelay;
    private static int pitchRampTicks;
    private static float rampedPitchDirection;
    private static int controlledPlaneId = -1;
    private static int clientThrottle;
    private static float clientThrottleAccumulator;
    private static final AutoThrottleController AUTO_THROTTLE =
        new AutoThrottleController();
    private static final AutoLevelController AUTO_LEVEL =
        new AutoLevelController();
    @org.jetbrains.annotations.Nullable
    private static Vec3 autoLevelLastMotion;
    private static boolean stickLimiter;

    public static boolean stickLimiterOn() {
        return stickLimiter;
    }

    public static double stickLimiterShare(PlaneEntity plane) {
        return StickLimitPolicy.pitchShare(altitudeAboveSeaLevel(plane));
    }

    public static boolean autoLevelOn() {
        return AUTO_LEVEL.enabled();
    }

    public static boolean autoLevelFlying() {
        return AUTO_LEVEL.flying();
    }

    public static double autoLevelAltitude() {
        return AUTO_LEVEL.targetAltitude() - seaLevelY();
    }

    private static double seaLevelY() {
        return amrac.physics.aircraft.FlightModelRegistry.instance()
            .atmosphere().seaLevelY();
    }

    private static double altitudeAboveSeaLevel(PlaneEntity plane) {
        return plane.getY() - seaLevelY();
    }

    private static double mouseHorizontalAccumulator;
    private static double mousePitchAccumulator;
    private static PlaneKeyBindings.MouseSensitivityRule activeMouseRule;
    private static HorizontalStickPolicy.Axis activeHorizontalAxis;

    private static int selectedWeaponSlot = WEAPON_SLOT_MACHINE_GUN;
    private static int weaponSelectionPlaneId = -1;
    private static boolean triggerSent;
    private static int predictionCooldown;
    private static boolean predictionRightSide;
    private static int predictionPlaneId = -1;
    private static int nextPredictedBulletId = Integer.MIN_VALUE;

    private static int lastSentThrottle = -1;
    private static float lastSentPitch;
    private static float lastSentYaw;
    private static float lastSentRoll;

    private PlaneClientControls() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(PlaneKeyBindings.OPEN_CONTROLS);
        PlaneKeyBindings.load();
        SoundVolumes.load();
        ModLanguage.load();

        PlaneNetworking.setRotationSender((attitude, motion, clientTick) ->
            sendPayload(PlaneNetworking.ROTATION,
                buf -> PlaneNetworking.writeRotation(buf, attitude, motion,
                    clientTick)));
        PlaneNetworking.setControlSender((throttle, pitch, yaw, roll, reverse) ->
            sendPayload(PlaneNetworking.CONTROL, buf ->
                PlaneNetworking.writeControls(buf, throttle, pitch, yaw, roll,
                    reverse)));
        PlaneNetworking.setGearSender(down ->
            sendPayload(PlaneNetworking.GEAR,
                buf -> PlaneNetworking.writeGear(buf, down)));
        PlaneNetworking.setFlapsSender(down ->
            sendPayload(PlaneNetworking.FLAPS,
                buf -> PlaneNetworking.writeGear(buf, down)));
        PlaneNetworking.setTriggerSender(firing ->
            sendPayload(PlaneNetworking.TRIGGER,
                buf -> PlaneNetworking.writeTrigger(buf, firing)));
        PlaneNetworking.setBombSender(() -> sendPayload(PlaneNetworking.BOMB));
        PlaneNetworking.setCountermeasureSenders(
            () -> sendPayload(PlaneNetworking.RELEASE_CHAFF),
            () -> sendPayload(PlaneNetworking.RELEASE_FLARE));
        PlaneNetworking.setDismountSender(() ->
            sendPayload(PlaneNetworking.DISMOUNT));
        PlaneNetworking.setCrashSender(() -> sendPayload(PlaneNetworking.CRASH));
        PlaneNetworking.setRadarSwitchSender(on ->
            sendPayload(PlaneNetworking.RADAR_SWITCH,
                buf -> buf.writeBoolean(on)));
        PlaneNetworking.setGpsOpener(() -> net.minecraft.client.Minecraft
            .getInstance().setScreenAndShow(
                new amrac.client.gui.GpsScreen(
                    amrac.client.gui.GpsScreen.Mode.TACTICAL)));
        PlaneNetworking.setGpsRequester(full ->
            sendPayload(PlaneNetworking.GPS_REQUEST,
                buf -> buf.writeBoolean(full)));
        PlaneNetworking.setPinEditor((action, id, name, x, z) ->
            sendPayload(PlaneNetworking.GPS_PIN, buf -> {
                buf.writeByte(action);
                buf.writeBoolean(id != null);
                if (id != null) {
                    buf.writeUUID(id);
                }
                buf.writeUtf(name == null ? "" : name, 64);
                buf.writeDouble(x);
                buf.writeDouble(z);
            }));
        PlaneNetworking.setGpsSink(GpsData::accept);
        ClientPlayNetworking.registerGlobalReceiver(PlaneNetworking.GPS_CONTACTS,
            (payload, context) -> {
                FriendlyByteBuf buf = payload.buffer();
                java.util.List<amrac.gps.GpsContact> contacts =
                    PlaneNetworking.readGpsContacts(buf);
                java.util.Set<java.util.UUID> mine = new java.util.HashSet<>();
                java.util.List<amrac.gps.GpsPin> pins =
                    PlaneNetworking.readGpsPins(buf, mine);
                context.client().execute(() ->
                    PlaneNetworking.deliverGpsContacts(contacts, pins, mine));
            });

        PlaneNetworking.setContactSink(PlaneRadar::acceptContacts);
        ClientPlayNetworking.registerGlobalReceiver(PlaneNetworking.RADAR_CONTACTS,
            (payload, context) -> {
                FriendlyByteBuf buf = payload.buffer();
                java.util.List<amrac.entities.RadarContact>
                    contacts = PlaneNetworking.readContacts(buf);
                context.client().execute(() ->
                    PlaneNetworking.deliverContacts(contacts));
            });
        // Server flight-model sync has three parts: sent on join (AmracMod), broadcast on file
        // reload (PlaneNetworking.broadcastFlightModel), and applied here, with the local files
        // restored on disconnect. Miss one and a change works in singleplayer but not on a server,
        // or server values leak into singleplayer.
        ClientPlayNetworking.registerGlobalReceiver(PlaneNetworking.FLIGHT_MODEL_SYNC,
            (payload, context) -> {
                FriendlyByteBuf buf = payload.buffer();
                java.util.Map<String, String> documents =
                    PlaneNetworking.readDocuments(buf);
                context.client().execute(() ->
                    amrac.physics.aircraft.FlightModelRegistry
                        .instance().applyServerDocuments(documents));
            });
        amrac.platform.client.ClientPlayConnectionEvents
            .DISCONNECT.register(client ->
                amrac.physics.aircraft.FlightModelRegistry
                    .instance().clearServerDocuments());
        PlaneNetworking.setThreatSink(MissileThreats::acceptReported);
        ClientPlayNetworking.registerGlobalReceiver(PlaneNetworking.MISSILE_THREATS,
            (payload, context) -> {
                FriendlyByteBuf buf = payload.buffer();
                java.util.List<PlaneNetworking.ThreatReport> threats =
                    PlaneNetworking.readThreats(buf);
                context.client().execute(() ->
                    PlaneNetworking.deliverThreats(threats));
            });
        PlaneNetworking.setBoundarySink(CockpitWarnings::acceptBoundary);
        ClientPlayNetworking.registerGlobalReceiver(PlaneNetworking.BOUNDARY,
            (payload, context) -> {
                double distance = payload.buffer().readDouble();
                context.client().execute(() ->
                    PlaneNetworking.deliverBoundary(distance));
            });
        PlaneNetworking.setSeekerSink(MissileSeekerView::acceptFrames);
        ClientPlayNetworking.registerGlobalReceiver(PlaneNetworking.MISSILE_SEEKER,
            (payload, context) -> {
                FriendlyByteBuf buf = payload.buffer();
                java.util.List<PlaneNetworking.MissileSeeker> frames =
                    PlaneNetworking.readSeekerFrames(buf);
                context.client().execute(() ->
                    PlaneNetworking.deliverSeekerFrames(frames));
            });
        PlaneNetworking.setTrackSink(amrac.client.render
            .MissileTrails::acceptReported);
        ClientPlayNetworking.registerGlobalReceiver(PlaneNetworking.MISSILE_TRACKS,
            (payload, context) -> {
                FriendlyByteBuf buf = payload.buffer();
                java.util.List<PlaneNetworking.MissileTrack> tracks =
                    PlaneNetworking.readMissileTracks(buf);
                context.client().execute(() ->
                    PlaneNetworking.deliverMissileTracks(tracks));
            });
        PlaneNetworking.setAircraftTrackSink(amrac.client.render
            .RemoteAircraft::accept);
        ClientPlayNetworking.registerGlobalReceiver(PlaneNetworking.AIRCRAFT_TRACKS,
            (payload, context) -> {
                FriendlyByteBuf buf = payload.buffer();
                java.util.List<PlaneNetworking.AircraftTrack> contacts =
                    PlaneNetworking.readAircraftTracks(buf);
                context.client().execute(() ->
                    PlaneNetworking.deliverAircraftTracks(contacts));
            });
        PlaneNetworking.setSpeedBrakeSender(out ->
            sendPayload(PlaneNetworking.SPEED_BRAKE,
                buf -> buf.writeBoolean(out)));
        PlaneNetworking.setWheelBrakeSender(on ->
            sendPayload(PlaneNetworking.WHEEL_BRAKE,
                buf -> buf.writeBoolean(on)));
        PlaneNetworking.setFuelDumpSender(open ->
            sendPayload(PlaneNetworking.FUEL_DUMP,
                buf -> buf.writeBoolean(open)));
        PlaneNetworking.setAoaLimiterSender(enabled ->
            sendPayload(PlaneNetworking.AOA_LIMITER,
                buf -> buf.writeBoolean(enabled)));
        PlaneNetworking.setConsoleSender(
            (pos, action, text, value, first, second, bounds) ->
                sendPayload(PlaneNetworking.CONSOLE_ACTION,
                    buf -> PlaneNetworking.writeConsole(buf, pos, action, text,
                        value, first, second, bounds)));
        amrac.network.AiCommandNetworking.setSender((pos, order) ->
            sendPayload(amrac.network.AiCommandNetworking.SET,
                buf -> amrac.network.AiCommandNetworking.writeOrder(buf, pos,
                    order)));
        ClientPlayNetworking.registerGlobalReceiver(
            amrac.network.AiCommandNetworking.OPEN, (payload, context) -> {
                amrac.network.AiCommandNetworking.Opened opened =
                    amrac.network.AiCommandNetworking.readOpen(payload.buffer());
                context.client().execute(() -> {
                    var minecraft = context.client();
                    amrac.entities.ai.AiLaunchOrder current =
                        minecraft.level != null
                            && minecraft.level.getBlockEntity(opened.pos())
                                instanceof amrac.blocks.AiCommandBlockEntity block
                            ? block.order() : null;
                    minecraft.setScreenAndShow(
                        new amrac.client.gui.AiCommandScreen(opened.pos(),
                            opened.catalog(), opened.teams(), current));
                });
            });
        PlaneNetworking.setMissileSender(targetId ->
            sendPayload(PlaneNetworking.MISSILE,
                buf -> PlaneNetworking.writeMissile(buf, targetId)));

        ClientTickEvents.END_CLIENT_TICK.register(PlaneClientControls::tick);
    }

    private static void sendPayload(
        net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<
            PlaneNetworking.RawPayload> type) {
        sendPayload(type, ignored -> { });
    }

    private static void sendPayload(
        net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<
            PlaneNetworking.RawPayload> type,
        java.util.function.Consumer<FriendlyByteBuf> writer) {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer());
        try {
            writer.accept(buffer);
            ClientPlayNetworking.send(PlaneNetworking.payload(type, buffer));
        } finally {
            buffer.release();
        }
    }

    private static void tick(Minecraft minecraft) {
        PlaneKeyBindings.tickSuppression();

        GpsData.tick(amrac.client.gui.GpsScreen.isOpen(),
            PlaneViewState.ridingPlane(minecraft) != null);

        while (PlaneKeyBindings.AI_DEBUG.consumeClick()) {
            if (minecraft.player != null) {
                sendPayload(PlaneNetworking.AI_DEBUG, buf -> { });
            }
        }

        if (openControlsScreenIfRequested(minecraft)) {
            return;
        }

        LocalPlayer player = minecraft.player;
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (player == null || plane == null) {
            onLeftAircraft(minecraft);
            return;
        }

        wasRiding = true;
        PlaneViewState.tick(minecraft, plane);

        boolean piloting = plane.getControllingPassenger() == player;
        if (!piloting) {
            drainPilotBindings();
            releaseTrigger();
            return;
        }

        boolean newAircraft = controlledPlaneId != plane.getId();
        if (newAircraft) {
            controlledPlaneId = plane.getId();
            clientThrottle = 0;
        clientAfterburner = false;
            clientAfterburner = false;
            clientThrottleAccumulator = 0.0F;
            lastSentThrottle = -1;
            AUTO_THROTTLE.reset();
            resetMouseControl();
            PlaneCameraController.beginFlight(player, plane);
            PlaneNetworking.sendRadarSwitch(PlaneRadar.isEnabled());
        }

        boolean inputAvailable = minecraft.gui.screen() == null &&
            !PlaneViewState.isPassengerInteraction();
        PlaneKeyBindings.setFlightSuppression(inputAvailable);

        if (plane.isDying()) {
            drainPilotBindings();
            releaseTrigger();
            return;
        }

        tickViewBindings(minecraft, player, plane, inputAvailable);
        tickGear(plane, inputAvailable);
        tickFlaps(plane, inputAvailable);
        tickSpeedBrake(plane, inputAvailable);
        tickWheelBrake(plane, inputAvailable);
        tickFuelDump(plane, inputAvailable);
        tickCountermeasures(inputAvailable);
        tickAngleOfAttackLimiter(plane, inputAvailable);
        tickFlightAids(minecraft, plane, inputAvailable);
        tickFlightData(inputAvailable);
        tickPitchAuthoritySave();
        if (tickDismount(minecraft, player, inputAvailable)) {
            return;
        }

        boolean throttleUp = inputAvailable && PlaneKeyBindings.THROTTLE_UP.isDown();
        boolean throttleDown = inputAvailable && PlaneKeyBindings.THROTTLE_DOWN.isDown();
        updateThrottle(plane, newAircraft, throttleUp, throttleDown);

        float pitchInput = 0.0F;
        float yawInput = 0.0F;
        float rollInput = 0.0F;
        boolean groundReverse = false;
        if (inputAvailable) {
            float sensitivity = PlaneKeyBindings.keyboardSensitivityMultiplier();
            float keyboardPitch = axis(PlaneKeyBindings.PITCH_UP,
                PlaneKeyBindings.PITCH_DOWN);
            float keyboardRoll = axis(PlaneKeyBindings.ROLL_LEFT,
                PlaneKeyBindings.ROLL_RIGHT);
            boolean keyboardPitchActive = keyboardPitch != 0.0F;
            boolean keyboardRollActive = keyboardRoll != 0.0F;

            int authority = GroundProximityPolicy.takeoffPitchAuthority(
                PlaneKeyBindings.pitchAuthority(), plane.getTicksSinceAirborne());

            if (!keyboardPitchActive || keyboardPitch != rampedPitchDirection) {
                pitchRampTicks = 0;
                rampedPitchDirection = keyboardPitchActive ? keyboardPitch : 0.0F;
            } else if (pitchRampTicks < PitchRampPolicy.RAMP_TICKS) {
                ++pitchRampTicks;
            }
            int rampedAuthority =
                PitchRampPolicy.rampedAuthority(authority, pitchRampTicks);

            pitchInput = keyboardPitchActive
                ? keyboardPitch * sensitivity
                    * (rampedAuthority / (float) PlaneKeyBindings.MAX_PITCH_AUTHORITY)
                : 0.0F;
            rollInput = keyboardRollActive ? keyboardRoll * sensitivity : 0.0F;
            yawInput = axis(PlaneKeyBindings.YAW_LEFT, PlaneKeyBindings.YAW_RIGHT);

            if (PlaneCameraController.isMouseFlyingActive()) {
                PlaneKeyBindings.MouseSensitivityRule rule =
                    PlaneKeyBindings.mouseSensitivityRule();
                if (!keyboardPitchActive) {
                    pitchInput = mouseAxis(mousePitchAccumulator, rule);
                }
                float horizontal = mouseAxis(mouseHorizontalAccumulator, rule);
                HorizontalStickPolicy.Axis axis =
                    PlaneKeyBindings.horizontalMouseAxis();
                rollInput = HorizontalStickPolicy.roll(axis, rollInput, horizontal);
                yawInput = HorizontalStickPolicy.yaw(axis, yawInput, horizontal);
            }

            groundReverse = throttleDown && !throttleUp && clientThrottle == 0;
        }

        boolean pilotFlying = pitchInput != 0.0F || yawInput != 0.0F
            || rollInput != 0.0F;
        if (stickLimiter) {
            pitchInput = StickLimitPolicy.limit(pitchInput,
                altitudeAboveSeaLevel(plane));
        }

        rollInput = clampAxis(rollInput * plane.getPlayerRollInputScale());

        Vec3 motion = plane.getDeltaMovement();
        Vec3 autoLevelUp = plane.getBodyDirection(0.0F, 1.0F, 0.0F);
        double loadG = autoLevelLastMotion == null ? Double.NaN
            : amrac.entities.ai.AiPilotPolicy.signedLoad(
                motion.x, motion.y, motion.z, autoLevelLastMotion.x,
                autoLevelLastMotion.y, autoLevelLastMotion.z,
                autoLevelUp.x, autoLevelUp.y, autoLevelUp.z);
        autoLevelLastMotion = motion;
        if (AUTO_LEVEL.tick(pilotFlying, plane.onGround() || plane.isOnWater(),
            motion.y, motion.length(),
            plane.getBodyDirection(1.0F, 0.0F, 0.0F).y,
            plane.getBodyDirection(0.0F, 1.0F, 0.0F).y,
            Math.toRadians(plane.getRollAngularRate()), loadG)) {
            pitchInput = AUTO_LEVEL.pitch();
            rollInput = AUTO_LEVEL.roll();
            yawInput = 0.0F;
        }

        plane.setControlInputs(throttleOnTheWire(), pitchInput, yawInput, rollInput,
            groundReverse);
        sendControlsIfChanged(throttleOnTheWire(), pitchInput, yawInput, rollInput,
            groundReverse);

        tickWeapons(minecraft, player, plane, inputAvailable);
        tickSensors(minecraft, plane, inputAvailable);
    }

    private static void tickSensors(Minecraft minecraft, PlaneEntity plane,
                                    boolean inputAvailable) {
        if (consumeClicks(PlaneKeyBindings.AUTO_THROTTLE) && inputAvailable) {
            PlaneKeyBindings.setAutoThrottle(!PlaneKeyBindings.autoThrottle());
            minecraft.gui.hud.setOverlayMessage(Component.translatable(
                PlaneKeyBindings.autoThrottle()
                    ? "amrac.message.auto_throttle_on"
                    : "amrac.message.auto_throttle_off"), false);
        }
        if (consumeClicks(PlaneKeyBindings.MISSILE_SWITCH) && inputAvailable) {
            if (plane.getMissileCount() > 0) {
                sendPayload(PlaneNetworking.MISSILE_SWITCH);
            } else {
                minecraft.gui.hud.setOverlayMessage(Component.translatable(
                    "amrac.message.no_missile_selected"), false);
            }
        }
        if (consumeClicks(PlaneKeyBindings.RADAR) && inputAvailable) {
            if (plane.hasRadar()) {
                PlaneRadar.toggle();
                minecraft.gui.hud.setOverlayMessage(Component.translatable(
                    PlaneRadar.isEnabled() ? "amrac.message.radar_on"
                        : "amrac.message.radar_off"), false);
            } else {
                minecraft.gui.hud.setOverlayMessage(Component.translatable(
                    "amrac.message.no_radar"), false);
            }
        }
        PlaneRadar.tick(minecraft, plane);
    }

        private static boolean openControlsScreenIfRequested(Minecraft minecraft) {
        boolean requested = false;
        while (PlaneKeyBindings.OPEN_CONTROLS.consumeClick()) {
            requested = true;
        }
        if (!requested || minecraft.gui.screen() != null) {
            return false;
        }
        PlaneKeyBindings.OPEN_CONTROLS.setDown(false);
        PlaneKeyBindings.releaseAll();
        minecraft.gui.setScreen(new PlaneControlsScreen(null));
        return true;
    }

    private static void onLeftAircraft(Minecraft minecraft) {
        if (!wasRiding) {
            return;
        }
        wasRiding = false;
        resetSpeedBrake();
        resetWheelBrake();
        resetFuelDump();
        PlaneKeyBindings.setFlightSuppression(false);
        controlledPlaneId = -1;
        clientThrottle = 0;
        clientThrottleAccumulator = 0.0F;
        lastSentThrottle = -1;
        AUTO_THROTTLE.reset();
        AUTO_LEVEL.reset();
        autoLevelLastMotion = null;
        cancelWarmup();
        resetMouseControl();
        releaseTrigger();
        resetPrediction();
        selectedWeaponSlot = WEAPON_SLOT_MACHINE_GUN;
        weaponSelectionPlaneId = -1;
        PlaneRadar.reset();
        PlaneCameraController.resetFixedView();
        PlaneViewState.reset(minecraft);
        drainPilotBindings();
    }

    private static void updateThrottle(PlaneEntity plane, boolean newAircraft,
                                       boolean throttleUp, boolean throttleDown) {
        if (PlaneKeyBindings.autoThrottle()) {
            double speedBlocksPerSecond = plane.getDeltaMovement().length() * 20.0D;
            boolean onSurface = plane.onGround() || plane.isOnWater();
            double limitPerTick = plane.getStructuralSpeedLimit();
            double structuralLimit = limitPerTick > 0.0D
                ? limitPerTick * 20.0D : NO_STRUCTURAL_LIMIT;
            clientThrottleAccumulator = AUTO_THROTTLE.tick(plane.getId(),
                clientThrottleAccumulator, newAircraft, onSurface,
                speedBlocksPerSecond, throttleUp, throttleDown,
                THROTTLE_INCREASE_STEP, THROTTLE_DECREASE_STEP,
                PlaneEntity.MAX_THROTTLE, structuralLimit);
            clientThrottle = Math.round(clientThrottleAccumulator);
            if (AUTO_THROTTLE.isIntervening()
                || clientThrottle < PlaneEntity.MAX_THROTTLE) {
                clientAfterburner = false;
            } else if (throttleUp && !clientAfterburner
                && clientThrottleAccumulator >= PlaneEntity.MAX_THROTTLE) {
                clientAfterburner = true;
            } else if (throttleDown && clientAfterburner) {
                clientAfterburner = false;
            }
            return;
        }

        AUTO_THROTTLE.reset();
        if (throttleUp != throttleDown) {
            if (throttleUp && !clientAfterburner
                && clientThrottleAccumulator >= PlaneEntity.MAX_THROTTLE) {
                clientAfterburner = true;
            } else if (throttleDown && clientAfterburner) {
                clientAfterburner = false;
            } else {
                clientThrottleAccumulator = Mth.clamp(clientThrottleAccumulator +
                        (throttleUp ? THROTTLE_INCREASE_STEP : -THROTTLE_DECREASE_STEP),
                    0.0F, (float) PlaneEntity.MAX_THROTTLE);
            }
            clientThrottle = Math.round(clientThrottleAccumulator);
        }
        if (clientThrottle < PlaneEntity.MAX_THROTTLE) {
            clientAfterburner = false;
        }
    }

    private static boolean clientAfterburner;

    public static boolean overspeedGuardActing() {
        return PlaneKeyBindings.autoThrottle() && AUTO_THROTTLE.isIntervening();
    }

    private static int throttleOnTheWire() {
        return clientAfterburner
            ? PlaneEntity.MAX_THROTTLE + 1 : clientThrottle;
    }

    private static void sendControlsIfChanged(int throttle, float pitch, float yaw,
                                              float roll, boolean groundReverse) {
        if (throttle == lastSentThrottle && pitch == lastSentPitch &&
            yaw == lastSentYaw && roll == lastSentRoll) {
            return;
        }
        PlaneNetworking.sendControls(throttle, pitch, yaw, roll, groundReverse);
        lastSentThrottle = throttle;
        lastSentPitch = pitch;
        lastSentYaw = yaw;
        lastSentRoll = roll;
    }

    private static void tickViewBindings(Minecraft minecraft, LocalPlayer player,
                                         PlaneEntity plane, boolean inputAvailable) {
        boolean toggleFixed = false;
        while (PlaneKeyBindings.TOGGLE_FIXED_VIEW.consumeClick()) {
            toggleFixed = true;
        }
        if (toggleFixed && inputAvailable) {
            PlaneCameraController.toggleFixedView(player, plane);
        }
        PlaneCameraController.tickFixedView(player, plane);
    }

    private static void tickGear(PlaneEntity plane, boolean inputAvailable) {
        while (PlaneKeyBindings.GPS_RANGE.consumeClick()) {
            GpsRange.cycle(false);
        }
        while (PlaneKeyBindings.GPS.consumeClick()) {
            Minecraft.getInstance().setScreenAndShow(
                new amrac.client.gui.GpsScreen(
                    amrac.client.gui.GpsScreen.Mode.AIRBORNE));
        }
        while (PlaneKeyBindings.GEAR.consumeClick()) {
            if (!inputAvailable || !plane.hasRetractableGear()) {
                continue;
            }
            boolean down = !plane.isGearDown();
            if (down && plane.getDeltaMovement().length()
                    > PlaneEntity.gearDeploySpeed()
                && !plane.onGround() && !plane.isOnWater()) {
                Minecraft.getInstance().gui.hud.setOverlayMessage(
                    Component.translatable("amrac.message.gear_too_fast",
                        Math.round(PlaneEntity.gearDeploySpeed() * 20.0D)), false);
                continue;
            }
            PlaneNetworking.sendGear(down);
        }
    }

    private static void tickFlaps(PlaneEntity plane, boolean inputAvailable) {
        while (PlaneKeyBindings.FLAPS.consumeClick()) {
            if (!inputAvailable || !plane.hasFlaps() || plane.areFlapsBroken()) {
                continue;
            }
            boolean down = !plane.areFlapsDown();
            if (down && plane.isTooFastForFlaps()) {
                Minecraft.getInstance().gui.hud.setOverlayMessage(
                    Component.translatable("amrac.message.flaps_too_fast",
                        Math.round(plane.getFlapProfile().placardSpeed())), false);
                continue;
            }
            PlaneNetworking.sendFlaps(down);
        }
    }

    private static void tickFlightAids(Minecraft minecraft, PlaneEntity plane,
                                       boolean inputAvailable) {
        if (consumeClicks(PlaneKeyBindings.STICK_LIMITER) && inputAvailable) {
            stickLimiter = !stickLimiter;
            minecraft.gui.hud.setOverlayMessage(Component.translatable(
                stickLimiter ? "amrac.message.stick_limiter_on"
                    : "amrac.message.stick_limiter_off"), false);
        }
        if (consumeClicks(PlaneKeyBindings.AUTO_LEVEL) && inputAvailable) {
            AUTO_LEVEL.toggle(plane.getY());
            minecraft.gui.hud.setOverlayMessage(AUTO_LEVEL.enabled()
                ? Component.translatable("amrac.message.auto_level_on",
                    String.format(java.util.Locale.ROOT, "%.0f",
                        autoLevelAltitude()))
                : Component.translatable("amrac.message.auto_level_off"), false);
        }
    }

    private static void tickAngleOfAttackLimiter(PlaneEntity plane,
                                                 boolean inputAvailable) {
        boolean pressed = false;
        while (PlaneKeyBindings.AOA_LIMITER.consumeClick()) {
            pressed = true;
        }
        if (!pressed || !inputAvailable || !plane.hasAngleOfAttackLimiter()) {
            return;
        }
        boolean enabled = !plane.isAngleOfAttackLimiterEnabled();
        PlaneNetworking.sendAoaLimiter(enabled);
        Minecraft.getInstance().gui.hud.setOverlayMessage(
            Component.translatable(enabled
                ? "amrac.message.aoa_limiter_on"
                : "amrac.message.aoa_limiter_off"), false);
    }

    public static void onPitchAuthorityScroll(double notches) {
        if (notches == 0.0D || !PlaneKeyBindings.nudgePitchAuthority(notches)) {
            return;
        }
        pitchAuthoritySaveDelay = PITCH_AUTHORITY_SAVE_DELAY_TICKS;
        Minecraft.getInstance().gui.hud.setOverlayMessage(
            Component.translatable("amrac.message.pitch_authority",
                PlaneKeyBindings.pitchAuthority()), false);
    }

    private static void tickPitchAuthoritySave() {
        if (pitchAuthoritySaveDelay <= 0) {
            return;
        }
        if (--pitchAuthoritySaveDelay == 0) {
            PlaneKeyBindings.persistPitchAuthority();
        }
    }

    private static void tickFlightData(boolean inputAvailable) {
        boolean pressed = false;
        while (PlaneKeyBindings.FLIGHT_DATA.consumeClick()) {
            pressed = !pressed;
        }
        if (pressed && inputAvailable) {
            PlaneHud.toggleFlightData();
        }
    }

    private static boolean fuelDumpHeld;

    private static void tickCountermeasures(boolean inputAvailable) {
        boolean chaff = false;
        while (PlaneKeyBindings.RELEASE_CHAFF.consumeClick()) {
            chaff = true;
        }
        boolean flare = false;
        while (PlaneKeyBindings.RELEASE_FLARE.consumeClick()) {
            flare = true;
        }
        if (!inputAvailable) {
            return;
        }
        if (chaff) {
            PlaneNetworking.sendChaffRelease();
        }
        if (flare) {
            PlaneNetworking.sendFlareRelease();
        }
    }

    private static void tickFuelDump(PlaneEntity plane, boolean inputAvailable) {
        boolean down = inputAvailable && PlaneKeyBindings.FUEL_DUMP.isDown();
        if (down == fuelDumpHeld) {
            return;
        }
        fuelDumpHeld = down;
        plane.setDumpingFuel(down);
        PlaneNetworking.sendFuelDump(down);
    }

    private static void tickSpeedBrake(PlaneEntity plane, boolean inputAvailable) {
        boolean pressed = false;
        while (PlaneKeyBindings.SPEED_BRAKE.consumeClick()) {
            pressed = !pressed;
        }
        if (!pressed || !inputAvailable) {
            return;
        }
        speedBrakeHeld = !speedBrakeHeld;
        PlaneNetworking.sendSpeedBrake(speedBrakeHeld);
    }

    private static void tickWheelBrake(PlaneEntity plane, boolean inputAvailable) {
        boolean down = inputAvailable && PlaneKeyBindings.WHEEL_BRAKE.isDown();
        if (down == wheelBrakeHeld) {
            return;
        }
        wheelBrakeHeld = down;
        plane.setWheelBrakeOn(down);
        PlaneNetworking.sendWheelBrake(down);
    }

    private static void resetWheelBrake() {
        if (!wheelBrakeHeld) {
            return;
        }
        wheelBrakeHeld = false;
        PlaneNetworking.sendWheelBrake(false);
    }

    private static void resetSpeedBrake() {
        if (!speedBrakeHeld) {
            return;
        }
        speedBrakeHeld = false;
        PlaneNetworking.sendSpeedBrake(false);
    }

    private static void resetFuelDump() {
        if (!fuelDumpHeld) {
            return;
        }
        fuelDumpHeld = false;
        PlaneNetworking.sendFuelDump(false);
    }

    private static boolean tickDismount(Minecraft minecraft, LocalPlayer player,
                                        boolean inputAvailable) {
        boolean dismount = false;
        while (PlaneKeyBindings.DISMOUNT.consumeClick()) {
            dismount = true;
        }
        if (!dismount || !inputAvailable) {
            return false;
        }
        releaseTrigger();
        PlaneNetworking.sendDismount();
        PlaneCameraController.resetFixedView();
        PlaneViewState.reset(minecraft);
        player.stopRiding();
        return true;
    }

    private static void tickWeapons(Minecraft minecraft, LocalPlayer player,
                                    PlaneEntity plane, boolean inputAvailable) {
        if (weaponSelectionPlaneId != plane.getId()) {
            weaponSelectionPlaneId = plane.getId();
            selectedWeaponSlot = WEAPON_SLOT_MACHINE_GUN;
            releaseTrigger();
        }

        boolean armed = plane.hasMachineGun() || plane.hasBombRack() ||
            plane.hasMissiles();
        boolean weaponInput = inputAvailable && armed &&
            minecraft.mouseHandler.isMouseGrabbed();

        if (consumeClicks(PlaneKeyBindings.SELECT_WEAPON_ONE) && weaponInput) {
            selectWeapon(minecraft, WEAPON_SLOT_MACHINE_GUN,
                "amrac.message.weapon_one_selected");
        }
        if (consumeClicks(PlaneKeyBindings.SELECT_WEAPON_TWO) && weaponInput) {
            selectWeapon(minecraft, WEAPON_SLOT_BOMBS,
                "amrac.message.weapon_two_selected");
        }
        if (consumeClicks(PlaneKeyBindings.CYCLE_WEAPON) && weaponInput) {
            cycleWeapon(minecraft, plane);
        }
        tickWarmup(plane);

        if (consumeClicks(PlaneKeyBindings.LAUNCH_MISSILE) && weaponInput) {
            launchMissile(minecraft, plane);
        }

        boolean attackHeld = weaponInput && minecraft.options.keyAttack.isDown();
        boolean attackPressed = consumeClicks(minecraft.options.keyAttack) &&
            weaponInput;

        boolean firing = attackHeld && plane.hasMachineGun() &&
            selectedWeaponSlot == WEAPON_SLOT_MACHINE_GUN;
        updateTrigger(firing);

        if (attackPressed && selectedWeaponSlot == WEAPON_SLOT_BOMBS &&
            plane.hasBombRack()) {
            PlaneNetworking.sendBomb();
        }
        if (attackPressed && selectedWeaponSlot == WEAPON_SLOT_MISSILE) {
            launchMissile(minecraft, plane);
        }

        tickGunPrediction(minecraft, player, plane, firing);
    }

    private static void cycleWeapon(Minecraft minecraft, PlaneEntity plane) {
        for (int step = 1; step <= 3; step++) {
            int slot = (selectedWeaponSlot - 1 + step) % 3 + 1;
            if (slot == WEAPON_SLOT_MACHINE_GUN && plane.hasMachineGun()) {
                selectWeapon(minecraft, slot, "amrac.message.weapon_one_selected");
                return;
            }
            if (slot == WEAPON_SLOT_BOMBS && plane.hasBombRack()) {
                selectWeapon(minecraft, slot, "amrac.message.weapon_two_selected");
                return;
            }
            if (slot == WEAPON_SLOT_MISSILE && plane.hasMissiles()) {
                selectWeapon(minecraft, slot, "amrac.message.weapon_three_selected");
                return;
            }
        }
    }

    @org.jetbrains.annotations.Nullable
    private static String warmingRound;
    private static int warmingTicks;
    private static int warmingPlaneId = -1;
    private static long warmingSinceNanos;
    private static int warmingAboard;
    private static boolean launchSent;
    private static long launchSentNanos;

    @org.jetbrains.annotations.Nullable
    public static amrac.weapons.MissileProfile roundOnTheKey(PlaneEntity plane) {
        String[] slots = plane.getLoadout();
        int rail = amrac.weapons.MissileLoadout.firstOf(slots,
            plane.getSelectedMissile());
        if (rail < 0) {
            rail = amrac.weapons.MissileLoadout.firstLoaded(slots);
        }
        return rail < 0 ? null : amrac.weapons.MissileProfiles.byId(slots[rail]);
    }

    @org.jetbrains.annotations.Nullable
    public static amrac.weapons.MissileProfile warmingProfile(PlaneEntity plane) {
        if (warmingRound == null || warmingPlaneId != plane.getId()) {
            return null;
        }
        return amrac.weapons.MissileProfiles.byId(warmingRound);
    }

    public static boolean warmupReady(PlaneEntity plane) {
        amrac.weapons.MissileProfile profile = warmingProfile(plane);
        return profile != null && warmingTicks >= profile.warmupTicks
            && System.nanoTime() - warmingSinceNanos
                >= profile.warmupTicks * nanosPerTick(plane);
    }

    public static double warmupSecondsLeft(PlaneEntity plane) {
        amrac.weapons.MissileProfile profile = warmingProfile(plane);
        if (profile == null) {
            return 0.0D;
        }
        long perTick = nanosPerTick(plane);
        long byTicks = Math.max(0, profile.warmupTicks - warmingTicks) * perTick;
        long byWall = profile.warmupTicks * perTick
            - (System.nanoTime() - warmingSinceNanos);
        return Math.max(0L, Math.max(byTicks, byWall)) / 1.0E9D;
    }

    private static long nanosPerTick(PlaneEntity plane) {
        return Math.max(1L, plane.level().tickRateManager().nanosecondsPerTick());
    }

    private static void cancelWarmup() {
        warmingRound = null;
        warmingTicks = 0;
        warmingPlaneId = -1;
        warmingAboard = 0;
        launchSent = false;
    }

    private static int roundsAboard(PlaneEntity plane, String roundId) {
        int count = 0;
        for (String slot : plane.getLoadout()) {
            if (slot != null
                    && amrac.weapons.MissileProfiles.byId(slot).id.equals(roundId)) {
                count++;
            }
        }
        return count;
    }

    private static void tickWarmup(PlaneEntity plane) {
        if (warmingRound == null) {
            return;
        }
        amrac.weapons.MissileProfile onKey = roundOnTheKey(plane);
        if (warmingPlaneId != plane.getId() || onKey == null
                || !onKey.id.equals(warmingRound)) {
            cancelWarmup();
            return;
        }
        int aboard = roundsAboard(plane, warmingRound);
        if (aboard < warmingAboard) {
            cancelWarmup();
            return;
        }
        warmingAboard = aboard;
        if (warmingTicks < onKey.warmupTicks) {
            warmingTicks++;
        }
    }

    private static void launchMissile(Minecraft minecraft, PlaneEntity plane) {
        if (!plane.hasMissiles()) {
            return;
        }
        if (!PlaneRadar.isEnabled()) {
            minecraft.gui.hud.setOverlayMessage(Component.translatable(
                "amrac.message.missile_needs_radar"), false);
            return;
        }
        if (plane.getMissileCount() <= 0) {
            minecraft.gui.hud.setOverlayMessage(Component.translatable(
                "amrac.message.no_missiles"), false);
            return;
        }
        amrac.weapons.MissileProfile round = roundOnTheKey(plane);
        if (round != null && round.warmupTicks > 0) {
            if (warmingProfile(plane) == null || !round.id.equals(warmingRound)) {
                warmingRound = round.id;
                warmingTicks = 0;
                warmingPlaneId = plane.getId();
                warmingAboard = roundsAboard(plane, round.id);
                launchSent = false;
                String named = round.id.length()
                    <= PlaneNetworking.MISSILE_WARMUP_ROUND_CHARS ? round.id : "";
                sendPayload(PlaneNetworking.MISSILE_WARMUP,
                    buf -> buf.writeUtf(named,
                        PlaneNetworking.MISSILE_WARMUP_ROUND_CHARS));
                warmingSinceNanos = System.nanoTime();
                minecraft.gui.hud.setOverlayMessage(Component.translatable(
                    "amrac.message.missile_warming",
                    String.format(java.util.Locale.ROOT, "%.1f",
                        round.warmupTicks * nanosPerTick(plane) / 1.0E9D)),
                    false);
                return;
            }
            if (!warmupReady(plane)) {
                minecraft.gui.hud.setOverlayMessage(Component.translatable(
                    "amrac.message.missile_still_warming",
                    String.format(java.util.Locale.ROOT, "%.1f",
                        warmupSecondsLeft(plane))), false);
                return;
            }
        }
        PlaneRadar.Lock primary = PlaneRadar.primaryTarget();
        if (primary == null) {
            minecraft.gui.hud.setOverlayMessage(Component.translatable(
                "amrac.message.missile_no_target"), false);
            return;
        }
        if (round != null) {
            Vec3 toTarget = primary.position.subtract(plane.position());
            double range = toTarget.length();
            double cos = range > 1.0E-6D
                ? toTarget.dot(plane.getBodyDirection(0.0F, 0.0F, 1.0F)) / range
                : -1.0D;
            if (!amrac.weapons.MissilePolicy.canLaunch(round, range, cos)) {
                minecraft.gui.hud.setOverlayMessage(Component.translatable(
                    "amrac.message.missile_no_solution"), false);
                return;
            }
        }
        long sentAt = System.nanoTime();
        if (round != null && round.warmupTicks > 0 && launchSent
                && sentAt - launchSentNanos < launchAnswerNanos(minecraft, plane)) {
            return;
        }
        PlaneNetworking.sendMissile(primary.aircraftId);
        launchSent = true;
        launchSentNanos = sentAt;
    }

    private static long launchAnswerNanos(Minecraft minecraft, PlaneEntity plane) {
        long latencyMillis = 0L;
        var connection = minecraft.getConnection();
        if (connection != null && minecraft.player != null) {
            var info = connection.getPlayerInfo(minecraft.player.getUUID());
            if (info != null) {
                latencyMillis = Math.max(0, Math.min(1000, info.getLatency()));
            }
        }
        return latencyMillis * 1_000_000L + 3L * nanosPerTick(plane);
    }

    private static void selectWeapon(Minecraft minecraft, int slot, String messageKey) {
        selectedWeaponSlot = slot;
        releaseTrigger();
        minecraft.gui.hud.setOverlayMessage(Component.translatable(messageKey), false);
    }

    public static int selectedWeaponSlot() {
        return selectedWeaponSlot;
    }

    public static boolean isBombWeaponSelected(PlaneEntity plane) {
        return weaponSelectionPlaneId == plane.getId() &&
            selectedWeaponSlot == WEAPON_SLOT_BOMBS;
    }

    private static void updateTrigger(boolean firing) {
        if (triggerSent == firing) {
            return;
        }
        triggerSent = firing;
        PlaneNetworking.sendTrigger(firing);
    }

    private static void releaseTrigger() {
        updateTrigger(false);
    }

    private static void tickGunPrediction(Minecraft minecraft, LocalPlayer player,
                                          PlaneEntity plane, boolean firing) {
        if (!firing || minecraft.level == null) {
            predictionCooldown = 0;
            return;
        }
        boolean creative = player.isCreative();
        if (plane.isGunOverheated() || !MachineGunFirePolicy.hasUsableAmmo(creative,
            plane.getMachineGunAmmoCount())) {
            predictionCooldown = 0;
            return;
        }
        if (predictionPlaneId != plane.getId()) {
            predictionPlaneId = plane.getId();
            predictionRightSide = false;
            predictionCooldown = 0;
        }
        if (predictionCooldown > 0) {
            --predictionCooldown;
            return;
        }

        Vec3 muzzle = plane.machineGuns().muzzlePoint(predictionRightSide);
        Vec3 velocity = plane.machineGuns().muzzleVelocity(muzzle);

        MachineGunBulletEntity bullet = new MachineGunBulletEntity(minecraft.level,
            player, plane, muzzle, velocity,
            MachineGunFirePolicy.DAMAGE_PER_PROJECTILE);
        bullet.markAsClientPrediction();
        bullet.setSourcePlaneId(plane.getId());
        int entityId = nextPredictedBulletId++;
        bullet.setId(entityId);
        ((ClientLevel) minecraft.level).addEntity(bullet);
        float pitch = SoundMixPolicy.gunPitch(entityId)
            * SoundMixPolicy.GUN_COCKPIT_PITCH_SCALE;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
            amrac.AmracSounds.GUN_FIRE,
            SoundMixPolicy.gunSamplePitch(entityId),
            SoundMixPolicy.GUN_COCKPIT_FIRE_VOLUME));

        predictionRightSide = !predictionRightSide;
        predictionCooldown = MachineGunFirePolicy.resetCooldown();
    }

    private static void resetPrediction() {
        predictionCooldown = 0;
        predictionPlaneId = -1;
    }

    public static void accumulateMouse(double deltaX, double deltaY) {
        PlaneKeyBindings.MouseSensitivityRule rule =
            PlaneKeyBindings.mouseSensitivityRule();
        if (activeMouseRule != rule) {
            activeMouseRule = rule;
            resetMouseControl();
        }
        HorizontalStickPolicy.Axis axis =
            PlaneKeyBindings.horizontalMouseAxis();
        if (activeHorizontalAxis != axis) {
            activeHorizontalAxis = axis;
            resetMouseControl();
        }

        double clampedX = Mth.clamp(deltaX, -MOUSE_DELTA_LIMIT, MOUSE_DELTA_LIMIT);
        double clampedY = Mth.clamp(deltaY, -MOUSE_DELTA_LIMIT, MOUSE_DELTA_LIMIT);
        if (!Double.isFinite(clampedX) || !Double.isFinite(clampedY)) {
            resetMouseControl();
            return;
        }

        double retention = 1.0D;
        if (rule == PlaneKeyBindings.MouseSensitivityRule.VELOCITY) {
            double frameSeconds = Mth.clamp(
                Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks()
                    / 20.0D,
                1.0D / 240.0D, 0.10D);
            retention = Math.pow(0.5D, frameSeconds / MOUSE_INPUT_HALF_LIFE_SECONDS);
        }
        double horizontalSensitivity = MOUSE_BASE_SENSITIVITY *
            PlaneKeyBindings.mouseSensitivityMultiplier();
        double verticalSensitivity = MOUSE_BASE_SENSITIVITY *
            PlaneKeyBindings.mouseVerticalSensitivityMultiplier();

        mouseHorizontalAccumulator = Mth.clamp(
            mouseHorizontalAccumulator * retention -
                deadZone(clampedX) * horizontalSensitivity,
            -MOUSE_FULL_INPUT_DISTANCE, MOUSE_FULL_INPUT_DISTANCE);
        double verticalDirection = PlaneKeyBindings.invertVerticalMouse() ? 1.0D : -1.0D;
        mousePitchAccumulator = Mth.clamp(
            mousePitchAccumulator * retention +
                deadZone(clampedY) * verticalSensitivity * verticalDirection,
            -MOUSE_FULL_INPUT_DISTANCE, MOUSE_FULL_INPUT_DISTANCE);
    }

    public static void resetMouseControl() {
        mouseHorizontalAccumulator = 0.0D;
        mousePitchAccumulator = 0.0D;
    }

    static void onPassengerInteractionChanged(boolean enabled) {
        resetMouseControl();
        if (enabled) {
            releaseTrigger();
        }
    }

    private static double deadZone(double delta) {
        double magnitude = Math.abs(delta);
        if (magnitude <= MOUSE_DELTA_EPSILON) {
            return 0.0D;
        }
        return Math.copySign(magnitude - MOUSE_DELTA_EPSILON, delta);
    }

    private static float mouseAxis(double accumulated,
                                   PlaneKeyBindings.MouseSensitivityRule rule) {
        double normalized = Mth.clamp(accumulated / MOUSE_FULL_INPUT_DISTANCE,
            -1.0D, 1.0D);
        double magnitude = Math.abs(normalized);
        if (!Double.isFinite(magnitude) || magnitude <= MOUSE_DEAD_ZONE) {
            return 0.0F;
        }
        double rescaled = (magnitude - MOUSE_DEAD_ZONE) / (1.0D - MOUSE_DEAD_ZONE);
        if (rule == PlaneKeyBindings.MouseSensitivityRule.LINEAR) {
            return (float) Math.copySign(Mth.clamp(rescaled, 0.0D, 1.0D), normalized);
        }
        double curved = rescaled * (0.35D + 0.65D * rescaled);
        return (float) Math.copySign(Mth.clamp(curved, 0.0D, 1.0D), normalized);
    }

    private static float axis(KeyMapping positive, KeyMapping negative) {
        return (positive.isDown() ? 1.0F : 0.0F) - (negative.isDown() ? 1.0F : 0.0F);
    }

    private static float clampAxis(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, -1.0F, 1.0F) : 0.0F;
    }

    private static boolean consumeClicks(KeyMapping binding) {
        boolean clicked = false;
        while (binding.consumeClick()) {
            clicked = true;
        }
        return clicked;
    }

    private static void drainPilotBindings() {
        for (PlaneKeyBindings.Entry entry : PlaneKeyBindings.entries()) {
            while (entry.binding().consumeClick()) {
            }
        }
    }

    public static int throttle() {
        return clientThrottle;
    }
}

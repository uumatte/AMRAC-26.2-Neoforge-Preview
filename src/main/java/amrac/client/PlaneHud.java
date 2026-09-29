package amrac.client;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import amrac.MathUtil;
import amrac.client.render.PlaneVisualFrame;
import amrac.client.render.ScreenProjection;
import amrac.upgrades.shooter.MachineGunFirePolicy;
import amrac.weapons.GunLeadPolicy;
import amrac.entities.RadarPolicy;
import amrac.entities.AttitudePolicy;
import amrac.entities.GForcePolicy;
import amrac.entities.GroundProximityPolicy;
import amrac.entities.PlaneEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlaneHud {
    private static final Identifier GUI_ICONS =
        Identifier.withDefaultNamespace("hud/crosshair");
    private static final int SIGHT_SIZE = 15;

    private static final double CHASE_SIGHT_HORIZONTAL_LIMIT = 0.055D;
    private static final double CHASE_SIGHT_VERTICAL_LIMIT = 0.065D;
    private static final double CHASE_SIGHT_HALF_LIFE_SECONDS = 0.055D;
    private static final double CHASE_SIGHT_RESET_GAP_SECONDS = 0.25D;

    private static double chaseSightX;
    private static double chaseSightY;
    private static int chaseSightPlaneId = -1;
    private static boolean chaseSightInitialized;
    private static long chaseSightUpdateNanos;

    private PlaneHud() {
    }

    public static void render(GuiGraphicsExtractor graphics,
                              DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (plane == null) {
            return;
        }

        renderFlightData(minecraft, graphics, plane);
        renderAttitude(minecraft, graphics, plane);
        renderSpeedLimit(minecraft, graphics, plane);
        renderPullUp(minecraft, graphics, plane);
        renderCriticalAoa(minecraft, graphics, plane);
        renderOverload(minecraft, graphics);
        renderPilotTolerance(minecraft, graphics);
        renderTelemetry(minecraft, graphics, plane, player);
        renderMountMessage(minecraft, plane);
        renderRadar(minecraft, graphics, plane, player);
        if (PlaneKeyBindings.SEEKER_VIEW.isDown()) {
            MissileSeekerView.render(minecraft, graphics, player.position());
        }
        renderSight(minecraft, graphics, plane, player,
            deltaTracker.getGameTimeDeltaPartialTick(false));

        java.util.List<MissileThreats.Threat> threats =
            MissileThreats.against(minecraft, plane);
        renderMissileWarning(minecraft, graphics, threats);
        renderRwr(minecraft, graphics, threats);
    }

    private static final Identifier G_VIGNETTE = Identifier.fromNamespaceAndPath(
        "amrac", "textures/gui/g_vignette.png");
    private static final int G_VIGNETTE_SIZE = 256;
    private static final double G_VIGNETTE_WIDEST = 1.55D;
    private static final double G_VIGNETTE_TIGHTEST = 0.62D;

    private static final int OVERLOAD_BOTTOM_MARGIN = 70;

    private static void renderPilotTolerance(Minecraft minecraft,
                                             GuiGraphicsExtractor graphics) {
        if (!PilotTolerance.EFFECTS_ENABLED) {
            return;
        }
        double blackout = PilotTolerance.blackout();
        double redout = PilotTolerance.redout();
        if (blackout > 0.001D) {
            drawVignette(minecraft, graphics, blackout, 0, 0, 0);
        }
        if (redout > 0.001D) {
            drawVignette(minecraft, graphics, redout, 190, 20, 20);
        }
    }

    private static void drawVignette(Minecraft minecraft,
                                     GuiGraphicsExtractor graphics,
                                     double intensity, int red, int green,
                                     int blue) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        double clamped = Math.min(1.0D, Math.max(0.0D, intensity));
        double eased = clamped * clamped * (3.0D - 2.0D * clamped);
        int alpha = (int) Math.round(255.0D * Math.min(1.0D, 0.25D + 0.75D * eased)
            * Math.min(1.0D, clamped * 4.0D));
        if (alpha <= 0) {
            return;
        }
        int colour = (alpha << 24) | (red << 16) | (green << 8) | blue;

        double scale = G_VIGNETTE_WIDEST
            + (G_VIGNETTE_TIGHTEST - G_VIGNETTE_WIDEST) * eased;
        int drawnWidth = (int) Math.round(width * scale);
        int drawnHeight = (int) Math.round(height * scale);
        int left = (width - drawnWidth) / 2;
        int top = (height - drawnHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, G_VIGNETTE, left, top,
            0.0F, 0.0F, drawnWidth, drawnHeight, G_VIGNETTE_SIZE, G_VIGNETTE_SIZE,
            G_VIGNETTE_SIZE, G_VIGNETTE_SIZE, colour);

        if (left > 0) {
            graphics.fill(0, 0, left, height, colour);
            graphics.fill(left + drawnWidth, 0, width, height, colour);
        }
        if (top > 0) {
            int from = Math.max(0, left);
            int to = Math.min(width, left + drawnWidth);
            graphics.fill(from, 0, to, top, colour);
            graphics.fill(from, top + drawnHeight, to, height, colour);
        }
    }

    private static void renderOverload(Minecraft minecraft,
                                       GuiGraphicsExtractor graphics) {
        double load = PilotTolerance.loadFactor();
        boolean straining = load >= GForcePolicy.BLACKOUT_THRESHOLD_G
            || load <= GForcePolicy.REDOUT_THRESHOLD_G;
        if (!straining && PilotTolerance.blackout() <= 0.01D
            && PilotTolerance.redout() <= 0.01D) {
            return;
        }
        String text = Component.translatable("amrac.hud.overload",
            String.format(Locale.ROOT, "%.1f", load)).getString();
        int colour = load >= GForcePolicy.BLACKOUT_SNATCH_FROM_G ? PULL_UP_RED
            : (load <= GForcePolicy.REDOUT_THRESHOLD_G ? 0xFFFF7A6A
                : CRITICAL_AOA_AMBER);
        int width = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        graphics.text(minecraft.font, text,
            (width - minecraft.font.width(text)) / 2,
            screenHeight - OVERLOAD_BOTTOM_MARGIN, colour);
    }

    private static final int ATTITUDE_LEFT_MARGIN = 12;
    private static final int ATTITUDE_BOTTOM_MARGIN = 10;
    private static final int ATTITUDE_LABEL = 0xFF9FB8C8;
    private static final int ATTITUDE_VALUE = 0xFFE8F4FF;
    private static final int CLIMB_UP = 0xFF8FE39A;
    private static final int CLIMB_DOWN = 0xFFFFC24A;
    private static final double CLIMB_LEVEL_DEGREES = 0.5D;

    private static void renderAttitude(Minecraft minecraft,
                                       GuiGraphicsExtractor graphics,
                                       PlaneEntity plane) {
        Vec3 forward = plane.getBodyDirection(0.0F, 0.0F, 1.0F);
        Vec3 up = plane.getBodyDirection(0.0F, 1.0F, 0.0F);
        Vec3 right = plane.getBodyDirection(1.0F, 0.0F, 0.0F);
        Vec3 velocity = plane.getDeltaMovement();

        double pitch = AttitudePolicy.pitchDegrees(forward.y);
        double bank = AttitudePolicy.bankDegrees(right.y, up.y);
        double climb = AttitudePolicy.climbAngleDegrees(velocity.y,
            velocity.length());

        String bankSide = Math.abs(bank) < CLIMB_LEVEL_DEGREES ? ""
            : (bank > 0.0D ? " R" : " L");

        String limiterValue = Component.translatable(
            plane.hasAngleOfAttackLimiter()
                ? (plane.isAngleOfAttackLimiterEnabled()
                    ? "amrac.hud.limiter.on"
                    : "amrac.hud.limiter.off")
                : "amrac.hud.limiter.none").getString();
        int limiterColour = !plane.hasAngleOfAttackLimiter() ? ATTITUDE_LABEL
            : (plane.isAngleOfAttackLimiterEnabled() ? CLIMB_UP : CLIMB_DOWN);

        String flapValue = plane.areFlapsBroken()
            ? Component.translatable("amrac.hud.flaps.broken").getString()
            : plane.getFlapPosition() > 0.0F
                ? Component.translatable("amrac.hud.flaps.down").getString()
                : null;

        double alpha = amrac.physics.aircraft
            .AerodynamicsModel.angleOfAttackDegrees(
                new amrac.physics.aircraft.Vec3d(
                    velocity.x, velocity.y, velocity.z),
                new amrac.physics.aircraft.Vec3d(
                    forward.x, forward.y, forward.z),
                new amrac.physics.aircraft.Vec3d(
                    up.x, up.y, up.z));
        int alphaColour = alphaColour(plane, alpha);

        List<String[]> rowList = new ArrayList<>(6);
        List<Integer> colourList = new ArrayList<>(6);
        rowList.add(new String[] {
            Component.translatable("amrac.hud.aoa").getString(),
            String.format(Locale.ROOT, "%+.1f°", alpha)});
        colourList.add(alphaColour);
        rowList.add(new String[] {
            Component.translatable("amrac.hud.limiter").getString(),
            limiterValue});
        colourList.add(limiterColour);
        if (flapValue != null) {
            rowList.add(new String[] {
                Component.translatable("amrac.hud.flaps").getString(),
                flapValue});
            colourList.add(ATTITUDE_VALUE);
        }
        rowList.add(new String[] {
            Component.translatable("amrac.hud.attitude.pitch").getString(),
            String.format(Locale.ROOT, "%+.1f°", pitch)});
        colourList.add(ATTITUDE_VALUE);
        rowList.add(new String[] {
            Component.translatable("amrac.hud.attitude.bank").getString(),
            String.format(Locale.ROOT, "%.1f°%s", Math.abs(bank), bankSide)});
        colourList.add(ATTITUDE_VALUE);
        rowList.add(new String[] {
            Component.translatable("amrac.hud.attitude.climb").getString(),
            String.format(Locale.ROOT, "%+.1f°", climb)});
        colourList.add(Math.abs(climb) < CLIMB_LEVEL_DEGREES ? ATTITUDE_VALUE
            : (climb > 0.0D ? CLIMB_UP : CLIMB_DOWN));
        String[][] rows = rowList.toArray(new String[0][]);

        int lineHeight = minecraft.font.lineHeight + 2;
        int bottom = minecraft.getWindow().getGuiScaledHeight()
            - ATTITUDE_BOTTOM_MARGIN - minecraft.font.lineHeight;
        int labelWidth = 0;
        for (String[] row : rows) {
            labelWidth = Math.max(labelWidth, minecraft.font.width(row[0]));
        }

        for (int i = 0; i < rows.length; i++) {
            int y = bottom - (rows.length - 1 - i) * lineHeight;
            graphics.text(minecraft.font, rows[i][0],
                ATTITUDE_LEFT_MARGIN, y, ATTITUDE_LABEL);
            int colour = colourList.get(i);
            graphics.text(minecraft.font, rows[i][1],
                ATTITUDE_LEFT_MARGIN + labelWidth + 6, y, colour);
        }
    }

    private static int alphaColour(PlaneEntity plane, double alpha) {
        var profile = amrac.physics.aircraft
            .FlightModelRegistry.instance().profile(plane.flightModelId());
        if (profile == null) {
            return ATTITUDE_VALUE;
        }
        double magnitude = Math.abs(alpha);
        if (magnitude >= profile.limiterAngleOfAttack()) {
            return CLIMB_DOWN;
        }
        if (magnitude >= profile.limiterSoftStartAngleOfAttack()) {
            return SPEED_LIMIT_AMBER;
        }
        return ATTITUDE_VALUE;
    }

    private static final double SPEED_LIMIT_WARNING_MARGIN = 60.0D;
    private static final int SPEED_LIMIT_BOTTOM_MARGIN = 58;
    private static final int SPEED_LIMIT_AMBER = 0xFFFFC24A;
    private static final int SPEED_LIMIT_RED = 0xFFFF3A2A;

    private static void renderSpeedLimit(Minecraft minecraft,
                                         GuiGraphicsExtractor graphics,
                                         PlaneEntity plane) {
        double limit = plane.getStructuralSpeedLimit();
        if (limit <= 0.0D || plane.onGround() || plane.isOnWater()) {
            return;
        }
        double limitPerSecond = limit * 20.0D;
        double speedPerSecond = plane.getDeltaMovement().length() * 20.0D;
        double margin = limitPerSecond - speedPerSecond;
        if (margin > SPEED_LIMIT_WARNING_MARGIN
                * amrac.physics.aircraft.SpeedScale.current()) {
            return;
        }
        boolean past = margin <= 0.0D;
        String text = Component.translatable(past
                ? "amrac.hud.speed_limit_exceeded"
                : "amrac.hud.speed_limit",
            String.format(Locale.ROOT, "%.0f", limitPerSecond)).getString();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        graphics.text(minecraft.font, text,
            (width - minecraft.font.width(text)) / 2,
            screenHeight - SPEED_LIMIT_BOTTOM_MARGIN,
            past ? SPEED_LIMIT_RED : SPEED_LIMIT_AMBER);
    }

    private static final int PULL_UP_BOTTOM_MARGIN = 46;
    private static final int PULL_UP_RED = 0xFFFF3A2A;
    private static final long PULL_UP_SLOW_PERIOD_MS = 700L;
    private static final long PULL_UP_FAST_PERIOD_MS = 220L;

    private static void renderPullUp(Minecraft minecraft,
                                     GuiGraphicsExtractor graphics,
                                     PlaneEntity plane) {
        double height = plane.getHeightAboveGround();
        if (!GroundProximityPolicy.shouldWarnPullUp(height,
            plane.onGround() || plane.isOnWater(), plane.isGearDown())) {
            return;
        }
        double urgency = GroundProximityPolicy.warningUrgency(height);
        long period = (long) (PULL_UP_SLOW_PERIOD_MS -
            urgency * (PULL_UP_SLOW_PERIOD_MS - PULL_UP_FAST_PERIOD_MS));
        if (System.currentTimeMillis() % period >= period / 2L) {
            return;
        }
        String text = Component.translatable("amrac.hud.pull_up").getString();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        graphics.text(minecraft.font, text,
            (width - minecraft.font.width(text)) / 2,
            screenHeight - PULL_UP_BOTTOM_MARGIN, PULL_UP_RED);
    }

    private static final int CRITICAL_AOA_BOTTOM_MARGIN = 58;
    private static final int CRITICAL_AOA_AMBER = 0xFFFFC24A;
    private static final long CRITICAL_AOA_BLINK_MS = 400L;
    private static final double CRITICAL_AOA_FRACTION = 0.90D;

    private static void renderCriticalAoa(Minecraft minecraft,
                                          GuiGraphicsExtractor graphics,
                                          PlaneEntity plane) {
        var state = plane.getFlightState();
        var profile = plane.getFlightModelProfile();
        if (state == null || profile == null) {
            return;
        }
        if (amrac.entities.GroundProximityPolicy.isTakingOff(
                plane.onGround() || plane.isInWater(),
                plane.getTicksSinceAirborne())) {
            return;
        }
        double stall = profile.stallAngleOfAttack();
        if (!(stall > 0.0D) ||
            Math.abs(state.angleOfAttack()) < stall * CRITICAL_AOA_FRACTION) {
            return;
        }
        if (System.currentTimeMillis() % CRITICAL_AOA_BLINK_MS
            >= CRITICAL_AOA_BLINK_MS / 2L) {
            return;
        }
        String text = Component.translatable(
            "amrac.hud.critical_aoa").getString();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        graphics.text(minecraft.font, text,
            (width - minecraft.font.width(text)) / 2,
            screenHeight - CRITICAL_AOA_BOTTOM_MARGIN, CRITICAL_AOA_AMBER);
    }

    private static boolean flightDataVisible;

    private static final int DATA_TOP_MARGIN = 4;
    private static final int DATA_HEADING = 0xFF8FD4FF;
    private static final int DATA_VALUE = 0xFFE8F4FF;
    private static final int DATA_ABSENT = 0xFFB0B0B0;

    public static void toggleFlightData() {
        flightDataVisible = !flightDataVisible;
    }

    private static void renderFlightData(Minecraft minecraft,
                                         GuiGraphicsExtractor graphics,
                                         PlaneEntity plane) {
        if (!flightDataVisible) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        var state = plane.getFlightState();
        if (state == null) {
            String absent = Component.translatable(
                "amrac.hud.flight_data.absent").getString();
            graphics.text(minecraft.font, absent,
                (width - minecraft.font.width(absent)) / 2, DATA_TOP_MARGIN,
                DATA_ABSENT);
            return;
        }

        String[] rows = {
            String.format(Locale.ROOT,
                "ALT %.0f m   rho %.3f kg/m3   a %.1f m/s   q %.1f kPa",
                state.atmosphericAltitude(), state.density(),
                state.speedOfSound(), state.dynamicPressure() / 1000.0D),
            String.format(Locale.ROOT,
                "TAS %.1f m/s   M %.2f   AoA %.1f deg   beta %.1f deg   G %.2f",
                state.airspeed(), state.mach(), state.angleOfAttack(),
                state.sideSlip(), state.loadFactor()),
            String.format(Locale.ROOT,
                "CL %.3f   Cd %.3f   L %.1f kN   D %.1f kN   T %.1f kN   W %.1f kN",
                state.liftCoefficient(), state.dragCoefficient(),
                state.lift() / 1000.0D, state.drag() / 1000.0D,
                state.thrust() / 1000.0D, state.weight() / 1000.0D)
        };

        String heading = Component.translatable(
            "amrac.hud.flight_data.title").getString();
        int y = DATA_TOP_MARGIN;
        graphics.text(minecraft.font, heading,
            (width - minecraft.font.width(heading)) / 2, y, DATA_HEADING);
        for (String row : rows) {
            y += minecraft.font.lineHeight + 1;
            graphics.text(minecraft.font, row,
                (width - minecraft.font.width(row)) / 2, y, DATA_VALUE);
        }
    }

    private static final int WARN_RED = 0xFFFF4A3A;
    private static final int WARN_AMBER = 0xFFFFC24A;
    private static final int WARN_BOTTOM_MARGIN = 62;

    private static void renderMissileWarning(Minecraft minecraft,
                                             GuiGraphicsExtractor graphics,
                                             java.util.List<MissileThreats.Threat> threats) {
        if (threats.isEmpty()) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int shown = Math.min(threats.size(), RwrPolicy.MAX_SHOWN);
        long now = System.currentTimeMillis();

        for (int i = 0; i < shown; i++) {
            MissileThreats.Threat threat = threats.get(i);
            int y = height - WARN_BOTTOM_MARGIN
                - i * (minecraft.font.lineHeight + 1);
            String text = "Missile: " + Math.round(threat.distance()) + " b";
            boolean critical = RwrPolicy.isCritical(threat.distance());
            if (critical && !RwrPolicy.blinkOn(threat.distance(), now)) {
                continue;
            }
            int colour = critical ? WARN_RED : WARN_AMBER;
            int x = (width - minecraft.font.width(text)) / 2;
            graphics.text(minecraft.font, text, x, y, colour);
        }
    }

    private static final int RWR_CENTRE_X = 42;
    private static final int RWR_CENTRE_Y = 42;
    private static final int RWR_RADIUS = 30;
    private static final int RWR_RING = 0x803BE05A;
    private static final int RWR_RING_FAINT = 0x403BE05A;
    private static final int RWR_NOSE = 0xC03BE05A;

    private static void renderRwr(Minecraft minecraft, GuiGraphicsExtractor graphics,
                                  java.util.List<MissileThreats.Threat> threats) {
        if (threats.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();

        drawRing(graphics, RWR_CENTRE_X, RWR_CENTRE_Y, RWR_RADIUS, RWR_RING);
        drawRing(graphics, RWR_CENTRE_X, RWR_CENTRE_Y, RWR_RADIUS / 2, RWR_RING_FAINT);
        graphics.fill(RWR_CENTRE_X, RWR_CENTRE_Y - RWR_RADIUS - 3,
            RWR_CENTRE_X + 1, RWR_CENTRE_Y - RWR_RADIUS + 2, RWR_NOSE);
        graphics.fill(RWR_CENTRE_X - 1, RWR_CENTRE_Y - 1,
            RWR_CENTRE_X + 2, RWR_CENTRE_Y + 2, RWR_NOSE);

        int shown = Math.min(threats.size(), RwrPolicy.MAX_SHOWN);
        for (int i = 0; i < shown; i++) {
            MissileThreats.Threat threat = threats.get(i);
            if (RwrPolicy.isCritical(threat.distance())
                && !RwrPolicy.blinkOn(threat.distance(), now)) {
                continue;
            }
            double r = RwrPolicy.ringFraction(threat.distance()) * RWR_RADIUS;
            double bearing = threat.bearing();
            int x = RWR_CENTRE_X + (int) Math.round(Math.sin(bearing) * r);
            int y = RWR_CENTRE_Y - (int) Math.round(Math.cos(bearing) * r);
            int colour = RwrPolicy.isCritical(threat.distance())
                ? WARN_RED : WARN_AMBER;
            graphics.fill(x - 2, y - 2, x + 3, y + 3, colour);
        }
    }

    private static void drawRing(GuiGraphicsExtractor graphics, int cx, int cy,
                                 int radius, int colour) {
        int steps = Math.max(24, radius * 3);
        for (int i = 0; i < steps; i++) {
            double angle = 2.0D * Math.PI * i / steps;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private static final int RADAR_GREEN = 0xFF3BE05A;
    private static final int RADAR_GREEN_DIM = 0xB03BE05A;
    private static final int RADAR_LOCK_RED = 0xFFFF5A4A;
    private static final int GUN_LEAD_AMBER = 0xFFFFC24A;
    private static final int GUN_LEAD_AMBER_DIM = 0x90FFC24A;
    private static final int GUN_PIP_RADIUS = 5;
    private static final int BOX_MIN_SIZE = 10;
    private static final int ARROW_LENGTH = 11;
    private static final int ARROW_HALF_WIDTH = 5;

    private static void renderRadar(Minecraft minecraft, GuiGraphicsExtractor graphics,
                                    PlaneEntity plane, LocalPlayer player) {
        if (!PlaneRadar.isEnabled() || !plane.hasRadar() ||
            plane.getControllingPassenger() != player ||
            PlaneViewState.isPassengerInteraction()) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        drawScanVolume(minecraft, graphics, plane, width, height);

        PlaneRadar.Lock primary = plane.hasMissiles()
            ? PlaneRadar.primaryTarget() : null;
        for (PlaneRadar.Lock lock : PlaneRadar.locks()) {
            if (lock.onScreen) {
                drawLockBox(minecraft, graphics, plane, lock, width, height,
                    lock.distance, lock == primary);
            } else {
                drawEdgeArrow(minecraft, graphics, lock, width, height);
            }
        }

        drawWarmupRings(minecraft, graphics, plane, primary, width, height);

        AirborneGps.render(minecraft, graphics, plane, width, height);

        PlaneRadar.Lock gunTarget = PlaneRadar.primaryTarget();
        if (gunTarget != null) {
            drawGunLead(minecraft, graphics, plane, gunTarget, width, height);
        }

        String status = Component.translatable("amrac.gui.radar",
            PlaneRadar.lockCount()).getString();
        int statusY = height / 2 - minecraft.font.lineHeight / 2;
        graphics.text(minecraft.font, status, 12, statusY, RADAR_GREEN);

        var set = plane.getRadarProfile();
        if (set != null) {
            String volume = String.format("%.0f° x %.0f°  %.0f Kb",
                set.horizontalScanDegrees(), set.verticalScanDegrees(),
                set.lockRange() / 1000.0D);
            graphics.text(minecraft.font, volume, 12,
                statusY + minecraft.font.lineHeight + 2, RADAR_GREEN_DIM);
        }
    }

    private static final int WARMUP_WHITE = 0xFFFFFFFF;
    private static final double DEFAULT_SEEKER_FOV = Math.toRadians(5.0D);
    private static final int FOV_RING_MIN_RADIUS = 9;

    private static void drawWarmupRings(Minecraft minecraft,
                                        GuiGraphicsExtractor graphics,
                                        PlaneEntity plane,
                                        PlaneRadar.Lock primary,
                                        int width, int height) {
        amrac.weapons.MissileProfile round =
            PlaneClientControls.warmingProfile(plane);
        if (round == null) {
            return;
        }
        boolean ready = PlaneClientControls.warmupReady(plane);
        int colour = ready ? RADAR_LOCK_RED : WARMUP_WHITE;

        double verticalFov = Math.toRadians(minecraft.options.fov().get());
        double pixelsPerTan = height * 0.5D / Math.max(1.0E-3D,
            Math.tan(verticalFov * 0.5D));

        Vec3 nose = plane.getBodyDirection(0.0F, 0.0F, 1.0F);
        ScreenProjection.Result bore = ScreenProjection.project(minecraft,
            plane.position().add(nose.scale(2000.0D)));
        int boreX = width / 2;
        int boreY = height / 2;
        if (bore != null && !bore.behind()) {
            boreX = ScreenProjection.screenX(bore.ndcX(), width);
            boreY = ScreenProjection.screenY(bore.ndcY(), height);
        }

        double range = primary == null ? Double.POSITIVE_INFINITY
            : primary.position.distanceTo(plane.position());
        double cone = amrac.weapons.MissilePolicy.launchCone(round);

        Vec3 side = nose.cross(plane.getBodyDirection(0.0F, 1.0F, 0.0F));
        side = side.lengthSqr() > 1.0E-9D ? side.normalize() : new Vec3(1, 0, 0);
        Vec3 over = side.cross(nose).normalize();
        if (cone < Math.toRadians(89.5D)) {
            double sin = Math.sin(cone);
            double cos = Math.cos(cone);
            int steps = 900;
            for (int step = 0; step < steps; step++) {
                double around = 2.0D * Math.PI * step / steps;
                Vec3 direction = nose.scale(cos)
                    .add(side.scale(sin * Math.cos(around)))
                    .add(over.scale(sin * Math.sin(around)));
                ScreenProjection.Result at = ScreenProjection.project(minecraft,
                    minecraft.gameRenderer.mainCamera().position()
                        .add(direction.scale(2000.0D)));
                if (at == null || at.behind()) {
                    continue;
                }
                int x = ScreenProjection.screenX(at.ndcX(), width);
                int y = ScreenProjection.screenY(at.ndcY(), height);
                if (x < 0 || y < 0 || x >= width || y >= height) {
                    continue;
                }
                graphics.fill(x, y, x + 1, y + 1, colour);
            }
        }
        boolean refused = false;
        boolean outOfRange = false;
        if (primary != null && range > 1.0E-6D) {
            double cosine = primary.position.subtract(plane.position()).dot(nose)
                / range;
            refused = !amrac.weapons.MissilePolicy.canLaunch(round, range, cosine);
            outOfRange = range < round.minLaunchRange
                || range > round.maxLaunchRange;
        }
        String state = !ready
            ? Component.translatable("amrac.gui.missile_warming", round.id,
                String.format(Locale.ROOT, "%.1f",
                    PlaneClientControls.warmupSecondsLeft(plane))).getString()
            : outOfRange
                ? Component.translatable("amrac.gui.missile_out_of_range")
                    .getString()
            : refused
                ? Component.translatable("amrac.gui.missile_ready_no_solution",
                    round.id).getString()
                : Component.translatable("amrac.gui.missile_ready", round.id)
                    .getString();
        graphics.text(minecraft.font, state,
            boreX - minecraft.font.width(state) / 2, boreY + 14,
            ready && refused ? WARMUP_WHITE : colour);

        int fovX = boreX;
        int fovY = boreY;
        if (primary != null) {
            ScreenProjection.Result at = ScreenProjection.project(minecraft,
                primary.position);
            if (at == null || at.behind()) {
                return;
            }
            fovX = ScreenProjection.screenX(at.ndcX(), width);
            fovY = ScreenProjection.screenY(at.ndcY(), height);
        }
        double halfFov = (round.seekerFov > 0.0D ? round.seekerFov
            : DEFAULT_SEEKER_FOV) * 0.5D;
        int fovRadius = (int) Math.max(FOV_RING_MIN_RADIUS,
            Math.round(Math.tan(halfFov) * pixelsPerTan));
        drawRing(graphics, fovX, fovY, fovRadius, colour);
    }

    private static void drawGunLead(Minecraft minecraft, GuiGraphicsExtractor graphics,
                                    PlaneEntity plane, PlaneRadar.Lock lock,
                                    int width, int height) {
        Vec3 muzzle = plane.machineGuns().worldPoint(0.0D,
            plane.getMachineGunMuzzleHeight(),
            plane.getMachineGunMuzzleForwardOffset());
        double speed = MachineGunFirePolicy.muzzleSpeed(
            plane.getMachineGunMuzzleVelocity());

        Vec3 toTarget = lock.position.subtract(muzzle);
        Vec3 relative = lock.velocity.subtract(plane.getDeltaMovement());
        double[] lead = new double[3];
        if (!GunLeadPolicy.leadPoint(
            new double[] {toTarget.x, toTarget.y, toTarget.z},
            new double[] {relative.x, relative.y, relative.z}, speed, lead)) {
            return;
        }
        boolean inRange = GunLeadPolicy.withinRange(lead);

        ScreenProjection.Result projected = ScreenProjection.project(minecraft,
            muzzle.add(lead[0], lead[1], lead[2]));
        if (projected == null || projected.behind()) {
            return;
        }
        int x = ScreenProjection.screenX(projected.ndcX(), width);
        int y = ScreenProjection.screenY(projected.ndcY(), height);
        int colour = inRange ? GUN_LEAD_AMBER : GUN_LEAD_AMBER_DIM;

        for (int i = 0; i < GUN_PIP_RADIUS; i++) {
            int span = GUN_PIP_RADIUS - i;
            graphics.fill(x - span, y - i, x - span + 1, y - i + 1, colour);
            graphics.fill(x + span - 1, y - i, x + span, y - i + 1, colour);
            graphics.fill(x - span, y + i, x - span + 1, y + i + 1, colour);
            graphics.fill(x + span - 1, y + i, x + span, y + i + 1, colour);
        }
        if (inRange) {
            graphics.fill(x - 1, y - 1, x + 1, y + 1, colour);
        }
    }

    private static final int SCAN_ARM = 11;
    private static final int SCAN_INSET = 6;
    private static final int SCAN_COLOUR = 0x7048C8FF;

    private static final int SCAN_OPEN_DEPTH = 5;

    private static void drawScanVolume(Minecraft minecraft,
                                       GuiGraphicsExtractor graphics,
                                       PlaneEntity plane, int width, int height) {
        var set = plane.getRadarProfile();
        if (set == null) {
            return;
        }
        double verticalFov = Math.toRadians(minecraft.options.fov().get());
        double halfScreenY = height * 0.5D;
        double halfScreenX = width * 0.5D;
        double focal = halfScreenY / Math.tan(verticalFov * 0.5D);

        double wantX = extent(set.azimuthLimit(), focal, halfScreenX);
        double wantY = extent(set.elevationLimit(), focal, halfScreenY);
        double spanX = Math.min(halfScreenX - SCAN_INSET, wantX);
        double spanY = Math.min(halfScreenY - SCAN_INSET, wantY);
        boolean openX = wantX > halfScreenX + 0.5D;
        boolean openY = wantY > halfScreenY + 0.5D;

        int left = (int) Math.round(halfScreenX - spanX);
        int right = (int) Math.round(halfScreenX + spanX);
        int top = (int) Math.round(halfScreenY - spanY);
        int bottom = (int) Math.round(halfScreenY + spanY);

        drawCorner(graphics, left, top, 1, 1);
        drawCorner(graphics, right, top, -1, 1);
        drawCorner(graphics, left, bottom, 1, -1);
        drawCorner(graphics, right, bottom, -1, -1);

        int midX = (int) Math.round(halfScreenX);
        int midY = (int) Math.round(halfScreenY);
        if (openX) {
            drawOpenEdge(graphics, left, midY, -1, false);
            drawOpenEdge(graphics, right, midY, 1, false);
        }
        if (openY) {
            drawOpenEdge(graphics, midX, top, -1, true);
            drawOpenEdge(graphics, midX, bottom, 1, true);
        }
    }

    private static void drawOpenEdge(GuiGraphicsExtractor graphics, int x, int y,
                                     int step, boolean stacked) {
        for (int i = 0; i < SCAN_OPEN_DEPTH; i++) {
            int out = step * i;
            if (stacked) {
                graphics.fill(x - SCAN_OPEN_DEPTH + i, y + out,
                    x - SCAN_OPEN_DEPTH + i + 1, y + out + 1, SCAN_COLOUR);
                graphics.fill(x + SCAN_OPEN_DEPTH - i, y + out,
                    x + SCAN_OPEN_DEPTH - i + 1, y + out + 1, SCAN_COLOUR);
            } else {
                graphics.fill(x + out, y - SCAN_OPEN_DEPTH + i,
                    x + out + 1, y - SCAN_OPEN_DEPTH + i + 1, SCAN_COLOUR);
                graphics.fill(x + out, y + SCAN_OPEN_DEPTH - i,
                    x + out + 1, y + SCAN_OPEN_DEPTH - i + 1, SCAN_COLOUR);
            }
        }
    }

    private static double extent(double angle, double focal, double halfScreen) {
        if (!Double.isFinite(angle) || angle >= Math.PI * 0.5D - 1.0E-6D) {
            return halfScreen;
        }
        return focal * Math.tan(angle);
    }

    private static void drawCorner(GuiGraphicsExtractor graphics, int x, int y,
                                   int stepX, int stepY) {
        int horizontalStart = stepX > 0 ? x : x - SCAN_ARM;
        graphics.fill(horizontalStart, y, horizontalStart + SCAN_ARM, y + 1,
            SCAN_COLOUR);
        int verticalStart = stepY > 0 ? y : y - SCAN_ARM;
        graphics.fill(x, verticalStart, x + 1, verticalStart + SCAN_ARM,
            SCAN_COLOUR);
    }

    private static void drawLockBox(Minecraft minecraft, GuiGraphicsExtractor graphics,
                                    PlaneEntity own, PlaneRadar.Lock lock,
                                    int width, int height,
                                    double distance, boolean primary) {
        double halfWidth = lock.width * 0.5D;
        double halfHeight = lock.height * 0.5D;
        AABB box = new AABB(
            lock.position.x - halfWidth, lock.position.y - halfHeight,
            lock.position.z - halfWidth,
            lock.position.x + halfWidth, lock.position.y + halfHeight,
            lock.position.z + halfWidth);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 point = new Vec3(
                (corner & 1) == 0 ? box.minX : box.maxX,
                (corner & 2) == 0 ? box.minY : box.maxY,
                (corner & 4) == 0 ? box.minZ : box.maxZ);
            ScreenProjection.Result projected =
                ScreenProjection.project(minecraft, point);
            if (projected == null || projected.behind()) {
                continue;
            }
            any = true;
            minX = Math.min(minX, projected.ndcX());
            maxX = Math.max(maxX, projected.ndcX());
            minY = Math.min(minY, projected.ndcY());
            maxY = Math.max(maxY, projected.ndcY());
        }
        if (!any) {
            return;
        }

        int x0 = ScreenProjection.screenX(minX, width);
        int x1 = ScreenProjection.screenX(maxX, width);
        int y0 = ScreenProjection.screenY(maxY, height);
        int y1 = ScreenProjection.screenY(minY, height);
        if (x1 - x0 < BOX_MIN_SIZE) {
            int centre = (x0 + x1) / 2;
            x0 = centre - BOX_MIN_SIZE / 2;
            x1 = centre + BOX_MIN_SIZE / 2;
        }
        if (y1 - y0 < BOX_MIN_SIZE) {
            int centre = (y0 + y1) / 2;
            y0 = centre - BOX_MIN_SIZE / 2;
            y1 = centre + BOX_MIN_SIZE / 2;
        }

        int colour = primary ? RADAR_LOCK_RED : RADAR_GREEN;
        int tick = Math.max(3, Math.min(10, (x1 - x0) / 4));
        graphics.fill(x0, y0, x0 + tick, y0 + 1, colour);
        graphics.fill(x0, y0, x0 + 1, y0 + tick, colour);
        graphics.fill(x1 - tick, y0, x1, y0 + 1, colour);
        graphics.fill(x1 - 1, y0, x1, y0 + tick, colour);
        graphics.fill(x0, y1 - 1, x0 + tick, y1, colour);
        graphics.fill(x0, y1 - tick, x0 + 1, y1, colour);
        graphics.fill(x1 - tick, y1 - 1, x1, y1, colour);
        graphics.fill(x1 - 1, y1 - tick, x1, y1, colour);

        int readout = primary ? RADAR_LOCK_RED : RADAR_GREEN_DIM;

        String range = String.format(Locale.ROOT, "%.1fKb",
            RadarPolicy.kiloBlocks(distance));
        graphics.text(minecraft.font, range, x0, y1 + 2, readout, false);

        Vec3 toTarget = lock.position.subtract(own.position());
        Vec3 relative = lock.velocity.subtract(own.getDeltaMovement());
        double closure = RadarPolicy.closureRate(toTarget.x, toTarget.y,
            toTarget.z, relative.x, relative.y, relative.z) * 20.0D;
        String vc = String.format(Locale.ROOT, "%+.0f b/s", closure);
        graphics.text(minecraft.font, vc, x0, y0 - 11, readout, false);
    }

    private static void drawEdgeArrow(Minecraft minecraft, GuiGraphicsExtractor graphics,
                                      PlaneRadar.Lock lock, int width, int height) {
        ScreenProjection.Result projected =
            ScreenProjection.project(minecraft, lock.position);
        if (projected == null) {
            return;
        }
        double[] edge = RadarPolicy.edgeArrow(projected.ndcX(), projected.ndcY(),
            projected.behind());
        int x = ScreenProjection.screenX(edge[0], width);
        int y = ScreenProjection.screenY(edge[1], height);
        float angle = (float) RadarPolicy.arrowAngle(edge[0], edge[1]);

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().rotate(-angle);
        for (int step = 0; step < ARROW_LENGTH; step++) {
            int halfWidth = Math.max(1, ARROW_HALF_WIDTH -
                (step * ARROW_HALF_WIDTH) / ARROW_LENGTH);
            graphics.fill(step, -halfWidth, step + 1, halfWidth, RADAR_GREEN);
        }
        graphics.pose().popMatrix();
    }

    private static void line(GuiGraphicsExtractor graphics, int x0, int y0,
                             int x1, int y1,
                             int colour) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, colour);
            return;
        }
        if (steps > 2000) {
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int x = x0 + dx * step / steps;
            int y = y0 + dy * step / steps;
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private static void renderTelemetry(Minecraft minecraft,
                                        GuiGraphicsExtractor graphics,
                                        PlaneEntity plane, LocalPlayer player) {
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        List<String> lines = new ArrayList<>(6);
        lines.add(plane.isAfterburnerEngaged()
            ? Component.translatable("amrac.gui.throttle_ab").getString()
            : Component.translatable(
                !PlaneKeyBindings.autoThrottle()
                    ? "amrac.gui.throttle"
                    : PlaneClientControls.overspeedGuardActing()
                        ? "amrac.gui.throttle_guarding"
                        : "amrac.gui.throttle_auto",
                plane.getThrottle()).getString());
        if (plane.getSpeedBrakePosition() > 0.01F) {
            lines.add(Component.translatable(
                "amrac.gui.speed_brake").getString());
        }
        if (PlaneClientControls.stickLimiterOn()) {
            lines.add(Component.translatable("amrac.gui.stick_limiter",
                String.format(Locale.ROOT, "%.0f",
                    PlaneClientControls.stickLimiterShare(plane) * 100.0D))
                .getString());
        }
        if (PlaneClientControls.autoLevelOn()) {
            lines.add(Component.translatable(
                PlaneClientControls.autoLevelFlying()
                    ? "amrac.gui.auto_level_flying" : "amrac.gui.auto_level",
                String.format(Locale.ROOT, "%.0f",
                    PlaneClientControls.autoLevelAltitude())).getString());
        }
        double metresPerSecond = plane.getDeltaMovement().length() * 20.0D;
        lines.add(Component.translatable("amrac.gui.speed",
            String.format(Locale.ROOT, "%.2f", metresPerSecond)).getString());
        lines.add(Component.translatable("amrac.gui.mach",
            String.format(Locale.ROOT, "%.2f",
                amrac.physics.aircraft.FlightModelRegistry
                    .instance().atmosphere()
                    .mach(metresPerSecond, plane.getY()))).getString());
        lines.add(Component.translatable("amrac.gui.altitude",
            String.format(Locale.ROOT, "%.1f", plane.getY())).getString());
        lines.add(Component.translatable("amrac.gui.health",
            Math.max(0, plane.getHealth()),
            Math.max(1, plane.getMaxHealth())).getString());

        boolean dry = plane.getFuel() <= 0
            && (plane.isFuelMetered() || !player.isCreative());
        int fuelLine = lines.size();
        lines.add(Component.translatable(dry
                ? "amrac.gui.fuel_empty"
                : "amrac.gui.fuel",
            Math.round(plane.getFuelFraction() * 100.0F)).getString()
            + (plane.isDumpingFuel() ? "  DUMP" : ""));

        boolean overheated = false;
        int gunHeatLine = -1;
        if (plane.hasMachineGun()) {
            lines.add(Component.translatable("amrac.gui.machine_gun_ammo",
                player.isCreative() ? "∞"
                    : Integer.toString(plane.getMachineGunAmmoCount())).getString());
            overheated = plane.isGunOverheated();
            gunHeatLine = lines.size();
            lines.add(Component.translatable(overheated
                    ? "amrac.gui.machine_gun_overheated"
                    : "amrac.gui.machine_gun_heat",
                plane.getGunHeatPercentage()).getString());
        }
        if (plane.hasMissiles()) {
            String selected = plane.getSelectedMissile();
            lines.add(Component.translatable("amrac.gui.missiles",
                plane.getMissileCount()).getString()
                + (selected == null ? ""
                    : "  " + amrac.AmracItems
                        .missileShortName(selected).getString()));
        }
        if (plane.getCountermeasureCapacity() > 0) {
            lines.add(Component.translatable("amrac.gui.chaff",
                plane.getChaffCount()).getString());
            lines.add(Component.translatable("amrac.gui.flare",
                plane.getFlareCount()).getString());
        }

        float spacing = minecraft.font.lineHeight + 2.0F;
        float total = minecraft.font.lineHeight * lines.size() + 2.0F * (lines.size() - 1);
        float top = (height - total) / 2.0F;
        for (int index = 0; index < lines.size(); ++index) {
            String text = lines.get(index);
            int colour = 0xFFFFFFFF;
            if (overheated && index == gunHeatLine) {
                colour = 0xFFFF5555;
            } else if (index == fuelLine && dry) {
                colour = 0xFFFF5555;
            }
            graphics.text(minecraft.font, text,
                width - minecraft.font.width(text) - 12,
                Math.round(top + index * spacing), colour);
        }
    }

    private static void renderMountMessage(Minecraft minecraft, PlaneEntity plane) {
        if (!plane.mountMessage) {
            return;
        }
        plane.mountMessage = false;
        minecraft.gui.hud.setOverlayMessage(Component.translatable("plane.onboard",
            PlaneKeyBindings.DISMOUNT.getTranslatedKeyMessage()), false);
    }

    private static void renderSight(Minecraft minecraft, GuiGraphicsExtractor graphics,
                                    PlaneEntity plane, LocalPlayer player,
                                    float partialTicks) {
        if (!plane.hasMachineGun() || plane.getControllingPassenger() != player ||
            PlaneViewState.isPassengerInteraction() ||
            PlaneClientControls.isBombWeaponSelected(plane) ||
            minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        boolean fixedView = PlaneCameraController.isFixedView();
        boolean chaseView = fixedView &&
            minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK;

        int[] position;
        if (chaseView) {
            position = chaseSightPosition(minecraft, plane, width, height, partialTicks);
        } else if (fixedView) {
            position = new int[] {width / 2, height / 2};
        } else {
            position = projectSight(minecraft, plane, width, height, partialTicks);
        }
        if (position == null) {
            return;
        }

        int x = position[0] - SIGHT_SIZE / 2;
        int y = position[1] - SIGHT_SIZE / 2;
        graphics.blitSprite(RenderPipelines.CROSSHAIR, GUI_ICONS, x, y,
            SIGHT_SIZE, SIGHT_SIZE);
    }

    private static int[] projectSight(Minecraft minecraft, PlaneEntity plane,
                                      int width, int height, float partialTicks) {
        double[] normalized = projectNormalized(minecraft, plane, partialTicks);
        if (normalized == null || Math.abs(normalized[0]) > 1.0D ||
            Math.abs(normalized[1]) > 1.0D) {
            return null;
        }
        return toScreen(normalized[0], normalized[1], width, height);
    }

    private static int[] chaseSightPosition(Minecraft minecraft, PlaneEntity plane,
                                            int width, int height, float partialTicks) {
        double[] projected = projectNormalized(minecraft, plane, partialTicks);
        double targetX = projected == null ? 0.0D
            : softLimit(projected[0], CHASE_SIGHT_HORIZONTAL_LIMIT);
        double targetY = projected == null ? 0.0D
            : softLimit(projected[1], CHASE_SIGHT_VERTICAL_LIMIT);

        long now = System.nanoTime();
        if (!chaseSightInitialized || chaseSightPlaneId != plane.getId()) {
            chaseSightX = 0.0D;
            chaseSightY = 0.0D;
            chaseSightPlaneId = plane.getId();
            chaseSightInitialized = true;
            chaseSightUpdateNanos = now;
        } else {
            double frameSeconds = (now - chaseSightUpdateNanos) * 1.0E-9D;
            chaseSightUpdateNanos = now;
            if (Double.isFinite(frameSeconds) && frameSeconds > 0.0D) {
                if (frameSeconds > CHASE_SIGHT_RESET_GAP_SECONDS) {
                    chaseSightX = targetX;
                    chaseSightY = targetY;
                } else {
                    double follow = 1.0D - Math.pow(0.5D,
                        frameSeconds / CHASE_SIGHT_HALF_LIFE_SECONDS);
                    chaseSightX += (targetX - chaseSightX) * follow;
                    chaseSightY += (targetY - chaseSightY) * follow;
                }
            }
        }
        return toScreen(chaseSightX, chaseSightY, width, height);
    }

    private static double softLimit(double value, double limit) {
        if (!Double.isFinite(value) || limit <= 0.0D) {
            return 0.0D;
        }
        return limit * Math.tanh(value / limit);
    }

    private static int[] toScreen(double normalizedX, double normalizedY,
                                  int width, int height) {
        return new int[] {
            (int) Math.round((normalizedX * 0.5D + 0.5D) * width),
            (int) Math.round((0.5D - normalizedY * 0.5D) * height)
        };
    }

    private static double[] projectNormalized(Minecraft minecraft, PlaneEntity plane,
                                              float partialTicks) {
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (!camera.isInitialized()) {
            return null;
        }

        Vec3 target = convergencePoint(plane, partialTicks);
        Vec3 toTarget = target.subtract(camera.position());
        Vec3 forward = new Vec3(camera.forwardVector()).normalize();
        Vec3 up = new Vec3(camera.upVector()).normalize();
        Vec3 right = forward.cross(up).normalize();
        double depth = toTarget.dot(forward);
        if (!Double.isFinite(depth) || depth <= 0.05D) {
            return null;
        }

        double verticalFov = Math.toRadians(minecraft.options.fov().get());
        double verticalScale = Math.tan(verticalFov * 0.5D);
        if (!Double.isFinite(verticalScale) || verticalScale <= 0.0D) {
            return null;
        }
        double aspect = (double) minecraft.getWindow().getWidth() /
            Math.max(1.0D, minecraft.getWindow().getHeight());
        double normalizedX = toTarget.dot(right) / (depth * verticalScale * aspect);
        double normalizedY = toTarget.dot(up) / (depth * verticalScale);
        if (!Double.isFinite(normalizedX) || !Double.isFinite(normalizedY)) {
            return null;
        }
        return new double[] {normalizedX, normalizedY};
    }

    private static Vec3 convergencePoint(PlaneEntity plane, float partialTicks) {
        Quaternionf attitude = MathUtil.lerpQ(partialTicks, plane.getQ_Prev(),
            plane.getQ_Client());
        double pivot = plane.getAirframeRotationPivotHeight();
        Vec3 offset = PlaneVisualFrame.rotateToWorld(attitude, 0.0D,
            plane.getMachineGunMuzzleHeight() - pivot,
            plane.getMachineGunConvergenceDistance());
        return PlaneVisualFrame.interpolatedRenderPosition(plane, partialTicks)
            .add(offset.x(), offset.y() + pivot, offset.z());
    }

    public static boolean suppressesVanillaCrosshair() {
        Minecraft minecraft = Minecraft.getInstance();
        PlaneEntity plane = PlaneViewState.ridingPlane(minecraft);
        if (plane == null || minecraft.player == null) {
            return false;
        }
        if (PlaneViewState.isPassengerInteraction()) {
            return false;
        }
        return PlaneClientControls.isBombWeaponSelected(plane) ||
            PlaneCameraController.isFixedView() || plane.hasMachineGun();
    }
}

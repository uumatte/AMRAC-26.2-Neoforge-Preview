package amrac.entities.ai;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;
import amrac.AmracItems;
import amrac.entities.AircraftFlightModelBridge;
import amrac.entities.AircraftRegistry;
import amrac.entities.AircraftTargetSnapshot;
import amrac.entities.PlaneEntity;
import amrac.entities.VirtualAircraftState;
import amrac.items.MissileItem;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.weapons.MissileLoadout;
import amrac.weapons.MissilePolicy;
import amrac.weapons.MissileProfile;
import amrac.weapons.MissileProfiles;
import amrac.weapons.VirtualMissileService;
import amrac.weapons.VirtualMissileState;

public final class AiPilotBrain {
    public static final double BOARDING_SEARCH_RADIUS = 32.0D;
    public static final double BOARDING_REACH = 4.5D;

    private static final int BOARDING_RETARGET_TICKS = 20;
    private static final int ENEMY_RETARGET_TICKS = 20;

    static final double MISSILE_WARNING_REACH = 512.0D;

    public final UUID id;

    @Nullable private UUID boardingTarget;
    @Nullable private UUID enemyAircraft;
    @Nullable private UUID enemyPlayer;
    @Nullable private UUID flownAircraft;
    private int boardingCooldown;
    private int enemyCooldown;

    private int launchInterval;

    private boolean wasDefending;

    private void noteEvasion() {
        boolean defending = incoming != null;
        if (wasDefending && !defending) {
            launchInterval = 0;
        }
        wasDefending = defending;
    }

    private double stationX = Double.NaN;
    private double stationZ = Double.NaN;

    private boolean departureComplete;

    private double smoothedAcceleration;
    @Nullable private Vec3 previousVelocity;
    private double previousSpeedBlocksPerSecond = Double.NaN;

    private AiPilotRank lastRank = AiPilotRank.TRAINEE;
    private String lastTeam = "";
    private String lastCallsign = "";
    private boolean lastMissionActive;
    @Nullable private String lastFlightModelId;
    private AiFlightPhase lastPhase = AiFlightPhase.IDLE;
    private boolean lastThreatened;
    @Nullable private String lastFireBlock;
    @Nullable private String[] lastLoadout;
    @Nullable private AiCommand lastCommand;

    public AiPilotBrain(UUID id) {
        this.id = id;
    }

    public void reset() {
        stationX = Double.NaN;
        stationZ = Double.NaN;
        boardingTarget = null;
        boardingCooldown = 0;
        departureComplete = false;
        smoothedAcceleration = 0.0D;
        previousVelocity = null;
        incoming = null;
        wasDefending = false;
        defending.clear();
        previousSpeedBlocksPerSecond = Double.NaN;
        resetTactics();
    }

    public void resetTactics() {
        enemyAircraft = null;
        enemyPlayer = null;
        enemyCooldown = 0;
        launchInterval = 0;
    }

    @Nullable
    public UUID flownAircraft() {
        return flownAircraft;
    }

    public AiPilotRank rank() {
        return lastRank;
    }

    public String callsign() {
        return lastCallsign.isBlank()
            ? AiCallsignPolicy.format(lastRank, 0) : lastCallsign;
    }

    public String team() {
        return lastTeam;
    }

    public boolean missionActive() {
        return lastMissionActive;
    }

    public boolean departureCompleteForDebug() {
        return departureComplete;
    }

    @Nullable
    public UUID enemyAircraftForDebug() {
        return enemyAircraft;
    }

    public int launchIntervalForDebug() {
        return launchInterval;
    }

    public boolean threatenedForDebug() {
        return lastThreatened;
    }

    @Nullable
    public String fireBlockForDebug() {
        return lastFireBlock;
    }

    @Nullable
    public String[] loadoutForDebug() {
        return lastLoadout;
    }

    @Nullable
    public AiCommand lastCommandForDebug() {
        return lastCommand;
    }

    @Nullable
    public AiSituation lastSituationForDebug() {
        return lastSituation;
    }

    @Nullable
    public AiThreat lastThreatForDebug() {
        return lastThreat;
    }

    public int notchSideForDebug() {
        return notchSide;
    }

    public double notchTrimForDebug() {
        return notchTrim;
    }

    @Nullable
    public amrac.weapons.CountermeasureService.Kind dispensingForDebug() {
        return dispensing;
    }

    public long dispensedAtForDebug() {
        return lastDispensedTick;
    }

    public AiFlightPhase phase() {
        return lastPhase;
    }

    public void seed(AiPilotRank rank, String callsign, String team,
                     UUID aircraft, String flightModelId) {
        lastRank = rank;
        lastCallsign = callsign == null ? "" : callsign;
        lastTeam = team == null ? "" : team;
        lastMissionActive = true;
        flownAircraft = aircraft;
        lastFlightModelId = flightModelId;
    }

    public void tick(ServerLevel level, AiPilotEntity body) {
        if (boardingCooldown > 0) --boardingCooldown;
        if (enemyCooldown > 0) --enemyCooldown;
        if (launchInterval > 0) --launchInterval;

        lastRank = body.rank();
        lastTeam = body.teamName();
        lastCallsign = body.callsign();
        lastMissionActive = body.isMissionActive();

        if (body.getVehicle() instanceof PlaneEntity plane) {
            flownAircraft = plane.getUUID();
            lastFlightModelId = plane.flightModelId();
            lastLoadout = plane.getLoadout();
            fly(level, body, plane);
        } else {
            flownAircraft = null;
            walk(level, body);
        }
    }

    private void walk(ServerLevel level, AiPilotEntity body) {
        lastPhase = AiFlightPhase.IDLE;
        if (!body.isMissionActive()) {
            body.setIdleReason(AiPilotIdleReason.NOT_STARTED);
            body.getNavigation().stop();
            recordOnFoot(level, body);
            return;
        }
        PlaneEntity target = resolveBoardingTarget(level, body);
        if (target == null) {
            recordOnFoot(level, body);
            return;
        }
        recordOnFoot(level, body);
        body.setIdleReason(AiPilotIdleReason.NONE);
        if (body.distanceTo(target) <= BOARDING_REACH) {
            body.startRiding(target, true, true);
            body.getNavigation().stop();
            reset();
            return;
        }
        body.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(),
            1.15D);
        body.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    private void fly(ServerLevel level, AiPilotEntity body, PlaneEntity plane) {
        if (!body.isMissionActive()) {
            lastPhase = AiFlightPhase.IDLE;
            body.setIdleReason(AiPilotIdleReason.NOT_STARTED);
            shutDown(plane, body);
            return;
        }

        boolean onSurface = plane.getOnGround() || plane.isOnWater();
        if (onSurface) {
            stationX = plane.getX();
            stationZ = plane.getZ();
            departureComplete = false;
            serviceAircraft(body, plane);
            plane.setParked(false);
            if (!plane.isPowered()) {
                lastPhase = AiFlightPhase.IDLE;
                body.setIdleReason(AiPilotIdleReason.NO_FUEL);
                shutDown(plane, body);
                return;
            }
        }
        body.setIdleReason(AiPilotIdleReason.NONE);

        updateDepartureLatch(plane.getY(), onSurface,
            AiAltitudePolicy.assignedAltitude(lastRank.cruiseAltitude(),
                ceilingOf(lastFlightModelId)));
        updateAcceleration(plane.getDeltaMovement().length() * 20.0D);

        Contact enemy = !onSurface
            ? resolveContact(level, lastTeam, plane.getUUID(), plane.position())
            : null;

        incoming = nearestThreat(level, plane.getUUID(),
            plane.getBoundingBox().getCenter(), plane.getDeltaMovement());
        noteEvasion();
        AiSituation situation = situationOf(level, plane, enemy);
        lastSituation = situation;
        lastThreat = threatFor(situation);
        AiCommand command = AiPilotDirector.decide(situation, lastThreat);
        lastPhase = command.phase();
        lastCommand = command;

        apply(plane, body, command);
        if (dispenserDue(plane.getBoundingBox().getCenter(),
                plane.getDeltaMovement(), command.phase())) {
            lastDispensedTick = level.getGameTime();
            amrac.weapons.CountermeasureService.releaseAt(level,
                plane.getBoundingBox().getCenter(), plane.getDeltaMovement(),
                plane.getBodyDirection(0.0F, -1.0F, 0.0F),
                plane.getBodyDirection(0.0F, 0.0F, -1.0F),
                plane.getBodyDirection(1.0F, 0.0F, 0.0F), null, dispensing);
        }
        lastFireBlock = AiPilotCombatPolicy.whyNoLaunch(situation,
            command.phase());
        if (command.launchMissile()) {
            launchFromEntity(level, body, plane, enemy);
        }
        recordLaunchIfAny(level.getGameTime());
        AiFlightRecorder.note(level.getGameTime(), this, "live",
            plane.position().x, plane.position().y, plane.position().z,
            plane.getDeltaMovement().length() * 20.0D,
            enemy == null ? -1.0D
                : plane.position().distanceTo(enemy.position()),
            offBoresight(plane.position(),
                plane.getBodyDirection(0.0F, 0.0F, 1.0F), enemy));
    }

    private void apply(PlaneEntity plane, AiPilotEntity body, AiCommand command) {
        plane.setGearDown(command.gearDown());
        plane.setFlapsDown(command.flapsDown());
        if (!command.afterburner(PlaneEntity.MAX_THROTTLE)) {
            plane.setAfterburnerEngaged(false);
        }
        plane.setControlInputs(command.throttle(), command.pitch(),
            command.yaw(), command.roll(), false);
        plane.setMachineGunTrigger(command.fireGun(), body);
    }

    private void shutDown(PlaneEntity plane, AiPilotEntity body) {
        plane.setMachineGunTrigger(false, body);
        plane.setAfterburnerEngaged(false);
        plane.setControlInputs(0, 0.0F, 0.0F, 0.0F, false);
    }

    @Nullable
    private static final boolean DEBUG =
        System.getProperty("amrac.aidebug") != null;

    public AiCommand virtualCommand(ServerLevel level, VirtualAircraftState state) {
        if (enemyCooldown > 0) --enemyCooldown;
        if (launchInterval > 0) --launchInterval;
        // The missile cooldown counts down here every tick; the launch logic runs only when the AI
        // wants to fire. Move the countdown into the launch path and an AI holding fire can never
        // fire again.
        if (state.missileCooldown > 0) --state.missileCooldown;
        if (!lastMissionActive) {
            return null;
        }
        lastFlightModelId = state.flightModelId;
        lastLoadout = state.loadout;
        if (state.team != null && !state.team.isBlank()) {
            lastTeam = state.team;
        }

        updateDepartureLatch(state.position.y, false,
            AiAltitudePolicy.assignedAltitude(lastRank.cruiseAltitude(),
                ceilingOf(lastFlightModelId)));
        updateAcceleration(state.velocity.length() * 20.0D);

        Contact enemy = !state.onGround
            ? resolveContact(level, lastTeam, state.id, state.position) : null;

        incoming = nearestThreat(level, state.id, state.position,
            state.velocity);
        noteEvasion();
        AiSituation situation = situationOf(level, state, enemy);
        lastSituation = situation;
        lastThreat = threatFor(situation);
        AiCommand command = AiPilotDirector.decide(situation, lastThreat);
        lastPhase = command.phase();
        lastCommand = command;
        if (dispenserDue(state.position, state.velocity, command.phase())) {
            lastDispensedTick = level.getGameTime();
            amrac.weapons.CountermeasureService.releaseAt(level, state.position,
                state.velocity,
                PlaneEntity.bodyDirection(state.attitude, 0.0F, -1.0F, 0.0F),
                PlaneEntity.bodyDirection(state.attitude, 0.0F, 0.0F, -1.0F),
                PlaneEntity.bodyDirection(state.attitude, 1.0F, 0.0F, 0.0F),
                null, dispensing);
        }
        lastFireBlock = AiPilotCombatPolicy.whyNoLaunch(situation,
            command.phase());
        if (command.launchMissile()) {
            launchFromVirtual(state, enemy);
        }
        recordLaunchIfAny(level.getGameTime());
        AiFlightRecorder.note(level.getGameTime(), this, "virtual",
            state.position.x, state.position.y, state.position.z,
            state.velocity.length() * 20.0D,
            enemy == null ? -1.0D
                : state.position.distanceTo(enemy.position()),
            offBoresight(state.position, PlaneEntity.bodyDirection(
                state.attitude, 0.0F, 0.0F, 1.0F), enemy));
        return command;
    }

    private AiSituation situationOf(ServerLevel level, PlaneEntity plane,
                                    @Nullable Contact enemy) {
        Vec3 forward = plane.getBodyDirection(0.0F, 0.0F, 1.0F);
        Vec3 up = plane.getBodyDirection(0.0F, 1.0F, 0.0F);
        Vec3 right = plane.getBodyDirection(1.0F, 0.0F, 0.0F);
        return situation(level, plane.position(), plane.getDeltaMovement(),
            forward, up, right,
            Math.toRadians(plane.getRollAngularRate()),
            noteThreat(incoming != null),
            plane.getOnGround() || plane.isOnWater(), plane.isPowered(),
            plane.getThrottle(), plane.getStructuralSpeedLimit(), enemy);
    }

    private AiSituation situationOf(ServerLevel level, VirtualAircraftState state,
                                    @Nullable Contact enemy) {
        Vec3 forward = PlaneEntity.bodyDirection(state.attitude, 0.0F, 0.0F, 1.0F);
        Vec3 up = PlaneEntity.bodyDirection(state.attitude, 0.0F, 1.0F, 0.0F);
        Vec3 right = PlaneEntity.bodyDirection(state.attitude, 1.0F, 0.0F, 0.0F);
        return situation(level, state.position, state.velocity, forward, up,
            right, Math.toRadians(state.rollRate),
            noteThreat(incoming != null),
            state.onGround, state.powered, state.throttle,
            structuralLimit(state.flightModelId, state.position.y), enemy);
    }

    private AiSituation situation(ServerLevel level, Vec3 position, Vec3 velocity,
                                  Vec3 forward, Vec3 up, Vec3 right,
                                  double rollRate, boolean underMissileThreat,
                                  boolean onGround, boolean powered,
                                  int throttle, double structuralSpeedLimit,
                                  @Nullable Contact enemy) {
        PlayAreaBounds.Rectangle area = PlayAreaBounds.forAi(level);
        Vec3 station = station(position);
        return new AiSituation(
            lastRank, lastMissionActive,
            position.x, position.y, position.z,
            velocity.x, velocity.y, velocity.z,
            forward.x, forward.z, up.y, right.y, rollRate,
            onGround, powered, throttle, PlaneEntity.MAX_THROTTLE,
            structuralSpeedLimit, loadLimitG(lastFlightModelId),
            ceilingOf(lastFlightModelId),
            smoothedAcceleration, departureComplete,
            enemy != null, enemy != null && enemy.aircraftId() != null,
            enemy == null ? 0.0D : enemy.position().x,
            enemy == null ? 0.0D : enemy.position().y,
            enemy == null ? 0.0D : enemy.position().z,
            enemy == null ? 0.0D : enemy.velocity().x,
            enemy == null ? 0.0D : enemy.velocity().y,
            enemy == null ? 0.0D : enemy.velocity().z,
            underMissileThreat,
            area.minX(), area.maxX(),
            area.minZ(), area.maxZ(),
            station.x, station.z,
            measureLoad(velocity));
    }

    private double measureLoad(Vec3 velocity) {
        Vec3 previous = previousVelocity;
        previousVelocity = velocity;
        return previous == null ? Double.NaN : AiPilotPolicy.measuredLoad(
            velocity.x, velocity.y, velocity.z,
            previous.x, previous.y, previous.z);
    }

    private void updateDepartureLatch(double altitude, boolean onSurface,
                                      double assignedAltitude) {
        if (onSurface) {
            departureComplete = false;
            return;
        }
        if (!departureComplete
                && AiAltitudePolicy.levelledOff(altitude, assignedAltitude)) {
            departureComplete = true;
        }
    }

    private void updateAcceleration(double speedBlocksPerSecond) {
        double delta = Double.isNaN(previousSpeedBlocksPerSecond) ? 0.0D
            : speedBlocksPerSecond - previousSpeedBlocksPerSecond;
        previousSpeedBlocksPerSecond = speedBlocksPerSecond;
        smoothedAcceleration =
            AiThrottlePolicy.smoothAcceleration(smoothedAcceleration, delta);
    }

    private Vec3 station(Vec3 position) {
        if (Double.isNaN(stationX) || Double.isNaN(stationZ)) {
            stationX = position.x;
            stationZ = position.z;
        }
        return new Vec3(stationX, position.y, stationZ);
    }

    private static double ceilingOf(@Nullable String flightModelId) {
        if (flightModelId == null) {
            return -1.0D;
        }
        double ceiling = AircraftFlightModelBridge.serviceCeiling(flightModelId);
        return ceiling == Double.MAX_VALUE ? -1.0D : ceiling;
    }

    private static double loadLimitG(@Nullable String flightModelId) {
        if (flightModelId == null) {
            return -1.0D;
        }
        AircraftPhysicsProfile profile =
            FlightModelRegistry.instance().profile(flightModelId);
        return profile == null ? -1.0D : profile.maxPositiveG();
    }

    private static double structuralLimit(@Nullable String flightModelId,
                                          double worldY) {
        if (flightModelId == null) {
            return -1.0D;
        }
        AircraftPhysicsProfile profile =
            FlightModelRegistry.instance().profile(flightModelId);
        if (profile == null) {
            return -1.0D;
        }
        double altitude = FlightModelRegistry.instance().atmosphere()
            .atmosphericAltitude(worldY);
        return profile.structuralSpeedLimit(altitude)
            / AircraftFlightModelBridge.TICKS_PER_SECOND;
    }

    static void serviceAircraft(AiPilotEntity body, PlaneEntity plane) {
        for (int slot = 0; slot < body.pilotInventory().getContainerSize(); slot++) {
            ItemStack stack = body.pilotInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(AmracItems.AVIATION_FUEL)) {
                while (!stack.isEmpty()
                    && plane.addFuelLitres(PlaneEntity.FUEL_ITEM_LITRES) > 0.0D) {
                    stack.shrink(1);
                }
            } else if (stack.is(AmracItems.BULLET)) {
                int taken = plane.loadMachineGunAmmo(stack.getCount());
                if (taken > 0) {
                    stack.shrink(taken);
                }
            } else if (stack.getItem() instanceof MissileItem missile
                    && plane.canCarry(missile.profileId())) {
                while (!stack.isEmpty()
                    && plane.loadMissile(missile.profileId()) != null) {
                    stack.shrink(1);
                }
            }
        }
        body.pilotInventory().setChanged();
    }

    @Nullable
    private PlaneEntity resolveBoardingTarget(ServerLevel level,
                                              AiPilotEntity body) {
        Entity heldEntity = boardingTarget == null ? null
            : level.getEntity(boardingTarget);
        if (heldEntity instanceof PlaneEntity held && held.isAlive()
                && held.getPassengers().isEmpty()) {
            return held;
        }
        if (boardingCooldown > 0) {
            return null;
        }
        boardingCooldown = BOARDING_RETARGET_TICKS;

        AiPilotRank rank = body.rank();
        Vec3 origin = body.position();
        double reachSquared = BOARDING_SEARCH_RADIUS * BOARDING_SEARCH_RADIUS;

        AircraftRegistry.Record found = AircraftRegistry.nearest(level, origin,
            record -> record.presence == AircraftRegistry.Presence.LIVE
                && !record.crewed
                && record.position.distanceToSqr(origin) <= reachSquared
                && flyable(rank, AircraftRegistry.liveEntity(record.id))
                && armable(rank, body, AircraftRegistry.liveEntity(record.id)));
        PlaneEntity nearest = found == null ? null
            : AircraftRegistry.liveEntity(found.id);
        boardingTarget = nearest == null ? null : nearest.getUUID();
        if (nearest != null) {
            return nearest;
        }

        boolean anyParked = AircraftRegistry.nearest(level, origin,
            record -> record.presence == AircraftRegistry.Presence.LIVE
                && !record.crewed
                && record.position.distanceToSqr(origin) <= reachSquared) != null;
        boolean anyFlyable = AircraftRegistry.nearest(level, origin,
            record -> record.presence == AircraftRegistry.Presence.LIVE
                && !record.crewed
                && record.position.distanceToSqr(origin) <= reachSquared
                && flyable(rank, AircraftRegistry.liveEntity(record.id))) != null;
        AiPilotIdleReason why = AiPilotIdleReason.onFoot(anyParked, anyFlyable,
            false);
        if (DEBUG) {
            org.slf4j.LoggerFactory.getLogger("amrac").info(
                "AIDEBUG {} on foot at {} refuses: {} (parked={} flyable={})",
                callsign(), origin, why, anyParked, anyFlyable);
        }
        body.setIdleReason(why);
        return null;
    }

    private static boolean flyable(AiPilotRank rank, @Nullable PlaneEntity plane) {
        return plane != null && rank.canFly(plane.flightModelId());
    }

    private static boolean armable(AiPilotRank rank, AiPilotEntity body,
                                   @Nullable PlaneEntity plane) {
        if (plane == null) {
            return false;
        }
        if (!rank.requiresMissiles()) {
            return true;
        }
        if (AiPilotCombatPolicy.armed(plane.getLoadout())) {
            return true;
        }
        for (int slot = 0; slot < body.pilotInventory().getContainerSize(); slot++) {
            ItemStack stack = body.pilotInventory().getItem(slot);
            if (stack.getItem() instanceof MissileItem missile
                    && plane.canCarry(missile.profileId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private String chooseRound(String[] loadout, Vec3 position, Vec3 forward,
                               Vec3 target, double range) {
        String selected = AiPilotCombatPolicy.selectMissile(loadout, range,
            AiPilotBrain::launchRangeOf);
        if (selected == null) {
            lastFireBlock = "every rail is empty";
            return null;
        }
        MissileProfile profile = MissileProfiles.byId(selected);
        if (profile == null) {
            lastFireBlock = selected + " is on the rail but has no profile"
                + " loaded";
            return null;
        }
        double cosine = range > 1.0E-6D
            ? target.subtract(position).dot(forward) / range : -1.0D;
        boolean warm = warmedUp(profile);
        double cone = MissilePolicy.launchCone(profile)
            - AI_LAUNCH_CONE_MARGIN;
        if (!MissilePolicy.canLaunch(profile, range, cosine)
                || cosine < Math.cos(Math.max(0.0D, cone))) {
            lastFireBlock = String.format(
                "%s will not launch from here: range %.0f against its %.0f to"
                    + " %.0f, target %.0f deg off the nose against its %.0f",
                selected, range, profile.minLaunchRange, profile.maxLaunchRange,
                Math.toDegrees(Math.acos(Math.max(-1.0D,
                    Math.min(1.0D, cosine)))),
                Math.toDegrees(Math.max(0.0D, cone)));
            return null;
        }
        return warm ? selected : null;
    }

    private static final double AI_LAUNCH_CONE_MARGIN = Math.toRadians(5.0D);

    @Nullable
    private String warmingRound;
    private int warmingTicks;

    private boolean warmedUp(MissileProfile profile) {
        if (!profile.id.equals(warmingRound)) {
            warmingRound = profile.id;
            warmingTicks = 0;
        }
        if (warmingTicks < profile.warmupTicks) {
            warmingTicks++;
            lastFireBlock = String.format("%s is warming up: %d of %d ticks",
                profile.id, warmingTicks, profile.warmupTicks);
            return false;
        }
        return true;
    }

    private boolean radarClear(String selected,
                               java.util.function.BooleanSupplier holds) {
        MissileProfile profile = MissileProfiles.byId(selected);
        if (profile == null || !profile.seekerType.radar() || holds.getAsBoolean()) {
            return true;
        }
        lastFireBlock = selected + " needs the radar to hold the target, and it"
            + " does not: outside the set's volume or range, or beaming it";
        return false;
    }

    private void launchFromEntity(ServerLevel level, AiPilotEntity body,
                                  PlaneEntity plane, @Nullable Contact enemy) {
        if (enemy == null || enemy.aircraftId() == null) {
            lastFireBlock = "no target at the moment of launch";
            return;
        }
        if (launchInterval > 0) {
            lastFireBlock = "reloading: " + launchInterval + " ticks left";
            return;
        }
        double range = plane.position().distanceTo(enemy.position());
        String selected = chooseRound(plane.getLoadout(), plane.position(),
            plane.getBodyDirection(0.0F, 0.0F, 1.0F), enemy.position(), range);
        if (selected == null || !radarClear(selected,
                () -> amrac.weapons.SeekerLinks.tracks(plane, enemy.position(),
                    enemy.velocity()))) {
            return;
        }
        AircraftTargetSnapshot snapshot =
            AircraftTargetSnapshot.of(level, enemy.aircraftId());
        if (snapshot == null) {
            lastFireBlock = "no snapshot of the target could be taken";
            return;
        }
        plane.setSelectedMissile(selected);
        if (plane.launchMissile(body, snapshot)) {
            noteLaunch();
            lastFireBlock = null;
        } else {
            lastFireBlock = "the aeroplane itself refused the launch";
        }
    }

    private void launchFromVirtual(VirtualAircraftState state,
                                   @Nullable Contact enemy) {
        if (enemy == null || enemy.aircraftId() == null) {
            lastFireBlock = "no target at the moment of launch";
            return;
        }
        if (launchInterval > 0) {
            lastFireBlock = "reloading: " + launchInterval + " ticks left";
            return;
        }
        if (state.missileCooldown > 0) {
            lastFireBlock = "the rail is still cycling: "
                + state.missileCooldown + " ticks";
            return;
        }
        double range = state.position.distanceTo(enemy.position());
        Vec3 forward = PlaneEntity.bodyDirection(state.attitude, 0.0F, 0.0F, 1.0F);
        String selected = chooseRound(state.loadout, state.position, forward,
            enemy.position(), range);
        if (selected == null || !radarClear(selected,
                () -> amrac.weapons.SeekerLinks.tracks(state, enemy.position(),
                    enemy.velocity()))) {
            return;
        }
        int rail = MissileLoadout.firstOf(state.loadout, selected);
        if (rail < 0) {
            lastFireBlock = selected + " was chosen but is on no rail";
            return;
        }
        MissileProfile profile = MissileProfiles.byId(selected);

        VirtualMissileState round = new VirtualMissileState(UUID.randomUUID(),
            profile, state.dimension, state.position,
            state.velocity, forward,
            enemy.aircraftId(), state.pilotId, state.id, 0, 0.0D, false,
            enemy.position(), enemy.velocity());
        round.ownerName = AiCallsignPolicy.displayName(callsign(), team());
        pendingRounds.add(new PendingRound(round, state.position));

        state.loadout[rail] = null;
        state.missileCooldown = profile.launchCooldownTicks;
        state.storesMassKilograms = sumStores(state.loadout, true);
        state.storesDragArea = sumStores(state.loadout, false);
        noteLaunch();
    }

    private final java.util.List<PendingRound> pendingRounds =
        new java.util.ArrayList<>();

    private record PendingRound(VirtualMissileState round, Vec3 aircraftAt) {
    }

    public void releaseVirtualRounds(VirtualAircraftState state) {
        if (pendingRounds.isEmpty()) {
            return;
        }
        Vec3 forward = PlaneEntity.bodyDirection(state.attitude, 0.0F, 0.0F, 1.0F);
        for (PendingRound pending : pendingRounds) {
            VirtualMissileState round = pending.round();
            round.position = round.position.add(
                state.position.subtract(pending.aircraftAt()));
            round.velocity = state.velocity;
            round.axis = forward;
            VirtualMissileService.adopt(round);
        }
        pendingRounds.clear();
    }

    private static double offBoresight(Vec3 from, Vec3 forward,
                                       @Nullable Contact enemy) {
        if (enemy == null) {
            return Double.NaN;
        }
        Vec3 toTarget = enemy.position().subtract(from);
        double range = toTarget.length();
        double facing = forward.length();
        if (range < 1.0E-6D || facing < 1.0E-6D) {
            return Double.NaN;
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D,
            toTarget.dot(forward) / (range * facing)))));
    }

    private boolean noteThreat(boolean threatened) {
        lastThreatened = threatened;
        return threatened;
    }

    private void noteLaunch() {
        launchInterval = AiPilotSettings.current().launchIntervalTicks;
        lastLaunchPending = true;
        warmingRound = null;
        warmingTicks = 0;
    }

    private void recordOnFoot(ServerLevel level, AiPilotEntity body) {
        lastFireBlock = "on foot: " + body.idleReason().label();
        AiFlightRecorder.note(level.getGameTime(), this, "foot",
            body.position().x, body.position().y, body.position().z,
            0.0D, -1.0D, Double.NaN);
    }

    private boolean lastLaunchPending;

    private void recordLaunchIfAny(long tick) {
        if (lastLaunchPending) {
            lastLaunchPending = false;
            AiFlightRecorder.event(tick, callsign() + " launched");
        }
    }

    private record Incoming(UUID id, Vec3 position, Vec3 velocity,
                            MissileProfile profile) {
    }

    @Nullable
    private Incoming incoming;

    private amrac.weapons.CountermeasureService.Kind dispensing;

    private int dispenserPhase;

    private int notchSide;

    private double notchTrim;

    @Nullable
    private UUID notchThreat;

    @Nullable
    private AiSituation lastSituation;
    @Nullable
    private AiThreat lastThreat;

    private long lastDispensedTick = Long.MIN_VALUE;

    private final java.util.Set<UUID> defending = new java.util.HashSet<>();

    @Nullable
    private Incoming nearestThreat(ServerLevel level, UUID aircraftId,
                                   Vec3 position, Vec3 velocity) {
        if (aircraftId == null) {
            defending.clear();
            return null;
        }
        Incoming best = null;
        double bestDistance = Double.MAX_VALUE;
        java.util.Set<UUID> alive = new java.util.HashSet<>();
        for (VirtualMissileState state : VirtualMissileService.chasing(aircraftId)) {
            double d = state.position.distanceTo(position);
            if (defends(state.id, state.profile, state.seeker, state.position,
                    state.velocity, position, velocity, d)) {
                alive.add(state.id);
                double urgency = urgency(state.profile, state.seeker,
                    state.position, state.velocity, position, velocity, d);
                if (urgency < bestDistance) {
                    bestDistance = urgency;
                    best = new Incoming(state.id, state.position, state.velocity,
                        state.profile);
                }
            }
        }
        for (amrac.entities.MissileEntity missile
                : amrac.weapons.LiveMissileIndex.chasing(aircraftId)) {
            if (missile.level() != level) {
                continue;
            }
            double d = missile.position().distanceTo(position);
            if (defends(missile.getUUID(), missile.profile(), missile.seekerState(),
                    missile.position(), missile.getDeltaMovement(), position,
                    velocity, d)) {
                alive.add(missile.getUUID());
                double urgency = urgency(missile.profile(), missile.seekerState(),
                    missile.position(), missile.getDeltaMovement(), position,
                    velocity, d);
                if (urgency < bestDistance) {
                    bestDistance = urgency;
                    best = new Incoming(missile.getUUID(), missile.position(),
                        missile.getDeltaMovement(), missile.profile());
                }
            }
        }
        defending.retainAll(alive);
        return best;
    }

    private static double urgency(MissileProfile profile,
                                  @Nullable amrac.weapons.SeekerState seeker,
                                  Vec3 missile, Vec3 missileVelocity,
                                  Vec3 aircraft, Vec3 aircraftVelocity,
                                  double distance) {
        return AiNotchPolicy.urgency(
            AiNotchPolicy.rwrSounding(profile, seeker, distance),
            missile.x, missile.y, missile.z, missileVelocity.x,
            missileVelocity.y, missileVelocity.z, aircraft.x, aircraft.y,
            aircraft.z, aircraftVelocity.x, aircraftVelocity.y, aircraftVelocity.z);
    }

    private boolean defends(UUID id, MissileProfile profile,
                            @Nullable amrac.weapons.SeekerState seeker,
                            Vec3 missile, Vec3 missileVelocity,
                            Vec3 aircraft, Vec3 aircraftVelocity, double distance) {
        boolean sounding = AiNotchPolicy.rwrSounding(profile, seeker, distance);
        if (sounding) {
            defending.add(id);
            return true;
        }
        if (!defending.contains(id)) {
            return false;
        }
        if (AiNotchPolicy.released(false, seeker,
                missile.x, missile.y, missile.z,
                missileVelocity.x, missileVelocity.y, missileVelocity.z,
                aircraft.x, aircraft.y, aircraft.z,
                aircraftVelocity.x, aircraftVelocity.y, aircraftVelocity.z)) {
            defending.remove(id);
            return false;
        }
        return true;
    }

    @Nullable
    private AiThreat threatFor(AiSituation situation) {
        Incoming threat = incoming;
        if (threat == null) {
            notchSide = 0;
            notchTrim = 0.0D;
            notchThreat = null;
            return null;
        }
        if (!threat.id().equals(notchThreat)) {
            notchThreat = threat.id();
            notchSide = 0;
            notchTrim = 0.0D;
        }
        if (notchSide == 0) {
            notchSide = AiNotchPolicy.sideFor(situation.velocityX(),
                situation.velocityZ(), threat.position().x - situation.x(),
                threat.position().z - situation.z());
        }
        double error = lastRank.notchError();
        notchTrim = AiNotchPolicy.trim(notchTrim,
            AiNotchPolicy.signedBeamAngle(
                new Vec3(situation.x(), situation.y(), situation.z()),
                new Vec3(situation.velocityX(), situation.velocityY(),
                    situation.velocityZ()), threat.position()),
            notchSide * error);
        return new AiThreat(threat.position().x, threat.position().y,
            threat.position().z, threat.velocity().x, threat.velocity().y,
            threat.velocity().z, threat.profile(), error, notchSide, notchTrim);
    }

    private boolean dispenserDue(Vec3 position, Vec3 velocity, AiFlightPhase phase) {
        Incoming threat = incoming;
        amrac.weapons.CountermeasureService.Kind kind = threat == null
            || phase != AiFlightPhase.EGRESS || !lastRank.evades() ? null
            : AiNotchPolicy.wanted(threat.profile(), threat.position(), position,
                velocity);
        if (kind == null) {
            dispenserPhase = 0;
            return false;
        }
        dispenserPhase = AiNotchPolicy.advance(dispenserPhase);
        if (!AiNotchPolicy.releases(dispenserPhase)) {
            return false;
        }
        dispenserPhase -= 20;
        dispensing = kind;
        return true;
    }

    private static double launchRangeOf(String profileId) {
        MissileProfile profile = MissileProfiles.byId(profileId);
        return profile == null ? Double.NEGATIVE_INFINITY : profile.maxLaunchRange;
    }

    private static double sumStores(String[] loadout, boolean mass) {
        return mass ? MissileLoadout.storesMass(loadout)
            : MissileLoadout.storesDragArea(loadout);
    }

    public record Contact(@Nullable UUID aircraftId, Vec3 position, Vec3 velocity,
                          @Nullable Entity entity) {
    }

    @Nullable
    private Contact resolveContact(ServerLevel level, String team,
                                   @Nullable UUID ownAircraft, Vec3 position) {
        Contact held = heldContact(level, team, position);
        if (held != null) {
            return held;
        }
        if (enemyCooldown > 0) {
            return null;
        }
        enemyCooldown = ENEMY_RETARGET_TICKS;
        enemyAircraft = null;
        enemyPlayer = null;

        Contact best = null;
        double bestDistance = Double.MAX_VALUE;
        AircraftRegistry.Record found = AircraftRegistry.nearest(level, position,
            record -> record.simulated()
                && (ownAircraft == null || !record.id.equals(ownAircraft))
                && AiTeamPolicy.hostile(team, record.team));
        if (found != null) {
            best = new Contact(found.id, found.position, found.velocity,
                AircraftRegistry.liveEntity(found.id));
            bestDistance = found.position.distanceToSqr(position);
            enemyAircraft = found.id;
        }
        if (found != null) {
            return best;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()
                    || player.getVehicle() instanceof PlaneEntity
                    || !hostile(level, team, player)) {
                continue;
            }
            double distance = player.position().distanceToSqr(position);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new Contact(null, player.getBoundingBox().getCenter(),
                    player.getDeltaMovement(), player);
                enemyAircraft = null;
                enemyPlayer = player.getUUID();
            }
        }
        return best;
    }

    @Nullable
    private Contact heldContact(ServerLevel level, String team, Vec3 position) {
        if (enemyAircraft != null) {
            AircraftRegistry.Record record =
                AircraftRegistry.record(level, enemyAircraft);
            if (record != null && record.simulated()
                    && AiTeamPolicy.hostile(team, record.team)) {
                return new Contact(record.id, record.position, record.velocity,
                    AircraftRegistry.liveEntity(record.id));
            }
            enemyAircraft = null;
        }
        if (enemyPlayer != null) {
            ServerPlayer player = level.getServer().getPlayerList()
                .getPlayer(enemyPlayer);
            if (player != null && player.level() == level && player.isAlive()
                    && !player.isSpectator() && hostile(level, team, player)) {
                if (!hostileAircraftExists(level, team, ownAircraftOf())) {
                    return new Contact(null,
                        player.getBoundingBox().getCenter(),
                        player.getDeltaMovement(), player);
                }
            }
            enemyPlayer = null;
        }
        return null;
    }

    @Nullable
    private UUID ownAircraftOf() {
        return flownAircraft;
    }

    private static boolean hostileAircraftExists(ServerLevel level, String team,
                                                 @Nullable UUID own) {
        return AircraftRegistry.nearest(level, Vec3.ZERO,
            record -> record.simulated()
                && (own == null || !record.id.equals(own))
                && AiTeamPolicy.hostile(team, record.team)) != null;
    }

    private static boolean hostile(ServerLevel level, String pilotTeam,
                                   ServerPlayer player) {
        if (pilotTeam == null || pilotTeam.isBlank()
                || level.getScoreboard().getPlayerTeam(pilotTeam) == null) {
            return false;
        }
        PlayerTeam playerTeam = level.getScoreboard().getPlayersTeam(
            player.getScoreboardName());
        return AiTeamPolicy.hostile(pilotTeam,
            playerTeam == null ? "" : playerTeam.getName());
    }
}

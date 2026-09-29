package amrac.trace;

import amrac.entities.AircraftFlightModelBridge;
import amrac.entities.AircraftRegistry;
import amrac.entities.AircraftVirtualService;
import amrac.entities.PlaneEntity;
import amrac.entities.VirtualAircraftState;
import amrac.entities.ai.AiCommand;
import amrac.entities.ai.AiFlightPhase;
import amrac.entities.ai.AiNotchPolicy;
import amrac.entities.ai.AiPilotBrain;
import amrac.entities.ai.AiPilotDirector;
import amrac.entities.ai.AiPilotService;
import amrac.entities.ai.AiPilotSettings;
import amrac.entities.ai.AiSituation;
import amrac.entities.ai.AiThreat;
import amrac.physics.aircraft.AircraftPhysicsProfile;
import amrac.physics.aircraft.AtmosphereModel;
import amrac.physics.aircraft.FlightModelRegistry;
import amrac.physics.aircraft.FlightState;
import amrac.physics.aircraft.RadarProfile;
import amrac.weapons.CountermeasureService;
import amrac.weapons.MissilePolicy;
import amrac.weapons.MissileProfile;
import amrac.weapons.SeekerPolicy;
import amrac.weapons.SeekerState;
import amrac.platform.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BvrTraceRecorder {
    private static final double TICK_SECONDS = amrac.physics.TickRate.SECONDS_PER_TICK;

    private static final double TPS = amrac.physics.TickRate.TICKS_PER_SECOND;

    private static final int FLUSH_INTERVAL_TICKS = 20;

    public static final int DEFAULT_LIMIT_TICKS = 20 * 60 * 30;

    private static boolean recording;

    @Nullable
    private static Session session;

    private BvrTraceRecorder() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BvrTraceRecorder::endTick);
    }

    public static boolean recording() {
        return recording;
    }

    public record Status(String name, Path file, int frames, int limitTicks,
                         long bytes, int aircraft, int missiles) {
    }

    @Nullable
    public static Status status() {
        Session s = session;
        return s == null ? null : s.status();
    }

    public static Path start(ServerLevel level, String name, int limitTicks)
            throws IOException {
        stop();
        Path directory = level.getServer().getServerDirectory()
            .resolve("amrac-traces");
        Files.createDirectories(directory);
        Path file = directory.resolve(name + ".ndjson");
        Session started = new Session(level, name, file, limitTicks);
        session = started;
        recording = true;
        return file;
    }

    @Nullable
    public static Status stop() {
        Session s = session;
        if (s == null) {
            return null;
        }
        recording = false;
        session = null;
        return s.close();
    }

    public record MissileSample(UUID id, MissileProfile profile, Vec3 position,
                                Vec3 velocity, Vec3 axis, int age,
                                double travelled, @Nullable UUID ownerId,
                                @Nullable UUID targetId,
                                @Nullable UUID sourcePlaneId,
                                @Nullable Vec3 targetPosition,
                                @Nullable Vec3 targetVelocity,
                                SeekerPolicy.Link link, SeekerState seeker,
                                SeekerPolicy.Solution solution,
                                double closestMiss, boolean virtual) {
    }

    public static void sampleMissile(MissileSample sample) {
        Session s = session;
        if (s != null) {
            s.sample(sample);
        }
    }

    private static final class Session {
        final String name;
        final Path file;
        final ServerLevel level;
        final int limitTicks;
        long startedAtGameTime = Long.MIN_VALUE;

        BufferedWriter writer;
        boolean broken;
        int frames;
        long bytes;
        int lastAircraft;
        int lastMissiles;

        final List<MissileSample> pending = new ArrayList<>();

        final Map<UUID, SeekerState> previous = new HashMap<>();

        final Map<UUID, Vec3> previousVelocity = new HashMap<>();

        final Map<UUID, String> names = new HashMap<>();
        int nextMissileName;

        final Map<Integer, Long> decoySeen = new HashMap<>();

        final List<UUID> lastAircraftIds = new ArrayList<>();
        final List<UUID> lastRoundIds = new ArrayList<>();

        Session(ServerLevel level, String name, Path file, int limitTicks)
                throws IOException {
            this.level = level;
            this.name = name;
            this.file = file;
            this.limitTicks = limitTicks;
            this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        }

        void sample(MissileSample sample) {
            if (!broken) {
                pending.add(sample);
            }
        }

        Status status() {
            return new Status(name, file, frames, limitTicks, bytes,
                lastAircraft, lastMissiles);
        }

        @Nullable
        Status close() {
            Status out = status();
            try {
                if (writer != null) {
                    writer.flush();
                    writer.close();
                }
            } catch (IOException e) {
                amrac.AmracMod.LOGGER.warn(
                    "[amrac] could not close the trace {}: {}", file, e);
            }
            writer = null;
            return out;
        }

        void write(String line) {
            if (broken || writer == null) {
                return;
            }
            try {
                writer.write(line);
                writer.write('\n');
                bytes += line.length() + 1L;
            } catch (IOException e) {
                broken = true;
                amrac.AmracMod.LOGGER.warn(
                    "[amrac] trace {} stopped: {}", file, e);
            }
        }

        String nameOf(UUID id, boolean missile) {
            String existing = names.get(id);
            if (existing != null) {
                return existing;
            }
            String made = missile ? "m" + nextMissileName++
                : id.toString().substring(0, 8);
            names.put(id, made);
            return made;
        }
    }

    private static void endTick(MinecraftServer server) {
        Session s = session;
        if (s == null) {
            return;
        }
        if (s.broken) {
            stop();
            return;
        }
        long now = s.level.getGameTime();
        if (s.startedAtGameTime == Long.MIN_VALUE) {
            s.startedAtGameTime = now;
        }
        int tick = (int) (now - s.startedAtGameTime);
        if (tick < 0 || (s.limitTicks > 0 && tick >= s.limitTicks)) {
            Status done = stop();
            if (done != null) {
                server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.translatable(
                        "amrac.message.trace_limit", done.name(),
                        done.frames()), false);
            }
            return;
        }

        List<Frame.Aircraft> aircraft = collectAircraft(s, now);
        List<Frame.Decoy> decoys = collectDecoys(s, now);
        List<Map<String, Object>> events = new ArrayList<>();

        if (s.frames == 0) {
            s.write(meta(s, rosterFor(s)));
        }

        noteAircraftEvents(s, aircraft, events);
        noteMissileEvents(s, events);

        s.write(frame(s, tick, aircraft, decoys, events));
        s.frames++;
        s.lastAircraft = aircraft.size();
        s.lastMissiles = s.pending.size();
        s.pending.clear();

        if (s.frames % FLUSH_INTERVAL_TICKS == 0 && s.writer != null) {
            try {
                s.writer.flush();
            } catch (IOException e) {
                s.broken = true;
                amrac.AmracMod.LOGGER.warn(
                    "[amrac] trace {} stopped on flush: {}", s.file, e);
            }
        }
    }

    private static final class Frame {
        private record Aircraft(UUID id, String traceId, String side,
                                @Nullable String airframe, Vec3 position,
                                Vec3 velocity, Vec3 forward, Vec3 up,
                                Vec3 right, int throttle, float spool,
                                float gear, float flaps, boolean onGround,
                                @Nullable String[] loadout,
                                @Nullable FlightState flight,
                                @Nullable AiPilotBrain brain, boolean live) {
        }

        private record Decoy(int id, CountermeasureService.Kind kind,
                             Vec3 position, Vec3 velocity, int age,
                             double separation) {
        }
    }

    private static List<Frame.Aircraft> collectAircraft(Session s, long now) {
        List<Frame.Aircraft> out = new ArrayList<>();
        for (AircraftRegistry.Record record
                : AircraftRegistry.simulated(s.level)) {
            PlaneEntity live = AircraftRegistry.liveEntity(record.id);
            AiPilotBrain brain = AiPilotService.flying(record.id);
            if (live != null) {
                out.add(new Frame.Aircraft(record.id,
                    s.nameOf(record.id, false), record.team,
                    live.flightModelId(),
                    live.getBoundingBox().getCenter(), live.getDeltaMovement(),
                    live.getBodyDirection(0.0F, 0.0F, 1.0F),
                    live.getBodyDirection(0.0F, 1.0F, 0.0F),
                    live.getBodyDirection(1.0F, 0.0F, 0.0F),
                    live.getThrottle(), live.getAfterburnerSpool(1.0F),
                    live.getGearPosition(), live.getFlapPosition(),
                    live.onGround() || live.isOnWater(), live.getLoadout(),
                    live.getFlightState(), brain, true));
                continue;
            }
            VirtualAircraftState v = AircraftVirtualService.get(record.id);
            if (v == null) {
                continue;
            }
            out.add(new Frame.Aircraft(record.id, s.nameOf(record.id, false),
                v.team, v.flightModelId, v.position, v.velocity,
                PlaneEntity.bodyDirection(v.attitude, 0.0F, 0.0F, 1.0F),
                PlaneEntity.bodyDirection(v.attitude, 0.0F, 1.0F, 0.0F),
                PlaneEntity.bodyDirection(v.attitude, 1.0F, 0.0F, 0.0F),
                v.throttle, v.afterburnerSpool, v.gearPosition, v.flapPosition,
                v.onGround, v.loadout,
                v.flightState, brain, false));
        }
        return out;
    }

    private static List<Frame.Decoy> collectDecoys(Session s, long now) {
        List<Frame.Decoy> out = new ArrayList<>();
        addDecoys(s, now, out,
            CountermeasureService.chaffNear(s.level, Vec3.ZERO),
            CountermeasureService.Kind.CHAFF);
        addDecoys(s, now, out,
            CountermeasureService.flaresNear(s.level, Vec3.ZERO),
            CountermeasureService.Kind.FLARE);
        if (!s.decoySeen.isEmpty()) {
            List<Integer> alive = new ArrayList<>(out.size());
            for (Frame.Decoy d : out) {
                alive.add(d.id());
            }
            s.decoySeen.keySet().retainAll(alive);
        }
        return out;
    }

    private static void addDecoys(Session s, long now, List<Frame.Decoy> out,
                                  List<SeekerPolicy.Decoy> bundles,
                                  CountermeasureService.Kind kind) {
        for (SeekerPolicy.Decoy bundle : bundles) {
            long seen = s.decoySeen.computeIfAbsent(bundle.id(), k -> now);
            double separation = Double.NaN;
            for (AircraftRegistry.Record record
                    : AircraftRegistry.simulated(s.level)) {
                double d = record.position.distanceTo(bundle.position());
                if (Double.isNaN(separation) || d < separation) {
                    separation = d;
                }
            }
            out.add(new Frame.Decoy(bundle.id(), kind, bundle.position(),
                bundle.velocity(), (int) (now - seen), separation));
        }
    }

    private static List<Frame.Aircraft> rosterFor(Session s) {
        List<Frame.Aircraft> out = new ArrayList<>();
        for (AircraftRegistry.Record record : AircraftRegistry.all(s.level)) {
            PlaneEntity live = AircraftRegistry.liveEntity(record.id);
            VirtualAircraftState v = AircraftVirtualService.get(record.id);
            String airframe = live != null ? live.flightModelId()
                : v != null ? v.flightModelId : null;
            out.add(new Frame.Aircraft(record.id, s.nameOf(record.id, false),
                record.team, airframe, record.position, record.velocity,
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0, 0.0F, 0.0F, 0.0F, false,
                live != null ? live.getLoadout() : v != null ? v.loadout : null,
                null, AiPilotService.flying(record.id), live != null));
        }
        return out;
    }

    private static void noteAircraftEvents(Session s,
                                           List<Frame.Aircraft> aircraft,
                                           List<Map<String, Object>> events) {
        List<UUID> present = new ArrayList<>(aircraft.size());
        for (Frame.Aircraft a : aircraft) {
            present.add(a.id());
            if (!s.lastAircraftIds.contains(a.id()) && s.frames > 0) {
                events.add(extra("kind", "aircraftAppeared",
                    "aircraft", a.traceId(), "airframe", a.airframe()));
            }
        }
        for (UUID was : s.lastAircraftIds) {
            if (!present.contains(was)) {
                events.add(extra("kind", "aircraftGone",
                    "aircraft", s.nameOf(was, false)));
            }
        }
        s.lastAircraftIds.clear();
        s.lastAircraftIds.addAll(present);
    }

    private static void noteMissileEvents(Session s,
                                          List<Map<String, Object>> events) {
        List<UUID> present = new ArrayList<>(s.pending.size());
        for (MissileSample m : s.pending) {
            present.add(m.id());
            String id = s.nameOf(m.id(), true);
            SeekerState now = m.seeker();
            SeekerState was = s.previous.get(m.id());
            double range = m.targetPosition() == null ? Double.NaN
                : m.position().distanceTo(m.targetPosition());

            if (was == null) {
                events.add(m.age() <= 1
                    ? extra("kind", "launch", "missile", id,
                        "type", m.profile().id,
                        "shooter", nameOrNull(s, m.sourcePlaneId(), false),
                        "target", nameOrNull(s, m.targetId(), false),
                        "rangeBlocks", round3(range))
                    : extra("kind", "appeared", "missile", id,
                        "type", m.profile().id, "age", m.age(),
                        "rangeBlocks", round3(range)));
            }
            if ((was == null || !was.active) && now.active) {
                events.add(extra("kind", "activate", "missile", id,
                    "rangeBlocks", round3(range)));
            }
            boolean wasTracking = was != null && was.tracking;
            if (!wasTracking && now.tracking) {
                events.add(extra("kind",
                    was != null && was.everTracked ? "relock" : "lock",
                    "missile", id, "rangeBlocks", round3(range),
                    "onDecoy", now.capturedDecoy >= 0));
            } else if (wasTracking && !now.tracking) {
                events.add(extra("kind", "lost", "missile", id,
                    "reason", now.loss.name(), "rangeBlocks", round3(range)));
            }
            int wasDecoy = was == null ? -1 : was.capturedDecoy;
            if (now.capturedDecoy >= 0 && now.capturedDecoy != wasDecoy) {
                events.add(extra("kind", "decoyed", "missile", id,
                    "decoyId", now.capturedDecoy, "rangeBlocks", round3(range)));
            }
            if ((was == null || !was.spent) && now.spent) {
                events.add(extra("kind", "spent", "missile", id,
                    "reason", now.loss.name(),
                    "closestRange", round3(now.closestRange)));
            }
            s.previous.put(m.id(), now.copy());
        }
        for (UUID was : s.lastRoundIds) {
            if (!present.contains(was)) {
                SeekerState last = s.previous.remove(was);
                s.previousVelocity.remove(was);
                events.add(extra("kind", "missileGone",
                    "missile", s.nameOf(was, true),
                    "closestRange",
                    last == null ? null : round3(last.closestRange)));
            }
        }
        s.lastRoundIds.clear();
        s.lastRoundIds.addAll(present);
    }

    @Nullable
    private static String nameOrNull(Session s, @Nullable UUID id,
                                     boolean missile) {
        return id == null ? null : s.nameOf(id, missile);
    }

    private static String meta(Session s, List<Frame.Aircraft> aircraft) {
        StringBuilder b = new StringBuilder(4096);
        AtmosphereModel air = FlightModelRegistry.instance().atmosphere();
        b.append("{\"type\":\"meta\",\"version\":1,\"tickSeconds\":")
            .append(num(TICK_SECONDS))
            .append(",\"generator\":\"amrac.trace.BvrTraceRecorder\"")
            .append(",\"source\":\"recording\"")
            .append(",\"recordingName\":").append(quote(s.name))
            .append(",\"dimension\":")
            .append(quote(s.level.dimension().identifier().toString()))
            .append(",\"startedAtGameTime\":").append(s.startedAtGameTime)
            .append(",\"limitTicks\":").append(s.limitTicks)
            .append(",\"frameInstant\":\"end of tick\"")
            .append(",\"aircraftMetaIsSnapshot\":true")
            .append(",\"aircraft\":[");
        for (int i = 0; i < aircraft.size(); i++) {
            Frame.Aircraft a = aircraft.get(i);
            if (i > 0) {
                b.append(',');
            }
            writeAircraftMeta(b, a);
        }
        b.append("],\"missileTypes\":{");
        int i = 0;
        for (MissileProfile table : amrac.weapons.MissileProfiles.all()) {
            if (i++ > 0) {
                b.append(',');
            }
            writeMissileType(b,
                amrac.weapons.MissileProfiles.byId(table.id));
        }
        b.append('}')
            .append(",\"constants\":{\"overshootRange\":")
            .append(num(SeekerPolicy.OVERSHOOT_RANGE))
            .append(",\"overshootClosure\":")
            .append(num(SeekerPolicy.OVERSHOOT_CLOSURE))
            .append(",\"threatHoldRange\":")
            .append(num(AiNotchPolicy.THREAT_HOLD_RANGE))
            .append(",\"chaffLifetimeTicks\":")
            .append(CountermeasureService.CHAFF_LIFETIME_TICKS)
            .append(",\"flareLifetimeTicks\":")
            .append(CountermeasureService.FLARE_LIFETIME_TICKS)
            .append(",\"chaffMinSeparation\":")
            .append(num(SeekerPolicy.CHAFF_MIN_SEPARATION))
            .append(",\"chaffRangeGate\":")
            .append(num(SeekerPolicy.CHAFF_RANGE_GATE))
            .append(",\"flareViewHalfAngleDeg\":")
            .append(num(Math.toDegrees(SeekerPolicy.FLARE_VIEW_HALF_ANGLE)))
            .append(",\"releasesPerSecond\":")
            .append(AiNotchPolicy.RELEASES_PER_SECOND)
            .append(",\"chaffLead\":").append(num(AiNotchPolicy.CHAFF_LEAD))
            .append(",\"flareRange\":").append(num(AiNotchPolicy.FLARE_RANGE))
            .append(",\"gimbalLimitDeg\":")
            .append(num(Math.toDegrees(MissilePolicy.SEEKER_GIMBAL_LIMIT)))
            .append(",\"launchIntervalTicks\":")
            .append(AiPilotSettings.current().launchIntervalTicks)
            .append(",\"seaLevelSpeedOfSound\":")
            .append(num(air.speedOfSound(air.seaLevelY())))
            .append(",\"gravity\":").append(num(air.gravity()))
            .append('}')
            .append(",\"units\":{\"Bpt\":\"blocks per tick\","
                + "\"Bps\":\"blocks per second\",\"Mps\":\"metres per second\","
                + "\"Deg\":\"degrees\"}")
            .append(",\"notes\":[")
            .append(quote("Recorded from a live server. Aircraft, decoys and"
                + " events are read at the end of the tick; each round's"
                + " seeker is sampled inside the tick, the moment its own"
                + " solution came back. seeker.sawTargetAt is the target"
                + " position that seeker was handed."))
            .append(',')
            .append(quote("missileTypes lists only the rounds seen so far when"
                + " the first frame was written, because meta is the first"
                + " line and a round fired later cannot be in it."))
            .append(',')
            .append(quote("decoys[].age counts from the tick this recording"
                + " first saw the bundle; bloomed is separation from the"
                + " NEAREST aeroplane, while the model tests separation from"
                + " the round's own target."))
            .append(',')
            .append(quote("aircraftGone is not a kill: a recording cannot tell"
                + " one from an aeroplane leaving the dimension."))
            .append(',')
            .append(quote("aircraft[] here is a snapshot taken when recording"
                + " began. side, rank and pilot are assigned by the server"
                + " after that -- often seconds later -- so read those from"
                + " each frame's own aircraft entry, which is authoritative."
                + " The airframe facts beside them do not change."))
            .append("]}");
        return b.toString();
    }

    private static void writeAircraftMeta(StringBuilder b, Frame.Aircraft a) {
        AircraftPhysicsProfile profile = a.airframe() == null ? null
            : FlightModelRegistry.instance().profile(a.airframe());
        RadarProfile radar = profile == null ? null : profile.radar();
        double ceiling = a.airframe() == null ? -1.0D
            : AircraftFlightModelBridge.serviceCeiling(a.airframe());
        b.append("{\"id\":").append(quote(a.traceId()))
            .append(",\"uuid\":").append(quote(a.id().toString()))
            .append(",\"side\":").append(quote(side(a)))
            .append(",\"team\":").append(quote(a.side()))
            .append(",\"airframe\":").append(quote(a.airframe()))
            .append(",\"rank\":")
            .append(a.brain() == null ? "null" : quote(a.brain().rank().name()))
            .append(",\"mass\":")
            .append(num(profile == null ? 0.0D : profile.mass()))
            .append(",\"wingArea\":")
            .append(num(profile == null ? 0.0D : profile.wingArea()))
            .append(",\"maxPositiveG\":")
            .append(num(profile == null ? 0.0D : profile.maxPositiveG()))
            .append(",\"militaryLevelSpeedMps\":")
            .append(num(profile == null ? 0.0D : profile.militaryLevelSpeed()))
            .append(",\"afterburnerLevelSpeedMps\":")
            .append(num(profile == null ? 0.0D
                : profile.afterburnerLevelSpeed()))
            .append(",\"serviceCeilingY\":")
            .append(num(ceiling == Double.MAX_VALUE ? -1.0D : ceiling))
            .append(",\"radar\":");
        if (radar == null) {
            b.append("null");
        } else {
            b.append("{\"azimuthHalfDeg\":")
                .append(num(Math.toDegrees(radar.azimuthLimit())))
                .append(",\"elevationHalfDeg\":")
                .append(num(Math.toDegrees(radar.elevationLimit())))
                .append(",\"lockRange\":").append(num(radar.lockRange()))
                .append(",\"velocityGateBpt\":").append(num(radar.velocityGate()))
                .append(",\"velocityGateBps\":")
                .append(num(radar.velocityGate() * TPS))
                .append(",\"lookDownOnly\":")
                .append(radar.velocityGateLookDownOnly())
                .append(",\"trackMemoryTicks\":")
                .append(radar.trackMemoryTicks()).append('}');
        }
        b.append(",\"notchErrorDeg\":")
            .append(a.brain() == null ? "null"
                : num(a.brain().rank().notchError()))
            .append(",\"pilot\":")
            .append(a.brain() == null ? "null"
                : quote(a.brain().callsign()))
            .append('}');
    }

    private static void writeMissileType(StringBuilder b, MissileProfile m) {
        b.append(quote(m.id)).append(":{\"velocityGateBpt\":")
            .append(num(m.velocityGate))
            .append(",\"velocityGateBps\":").append(num(m.velocityGate * TPS))
            .append(",\"velocityGateLookDownOnly\":")
            .append(m.velocityGateLookDownOnly)
            .append(",\"burnThroughRange\":").append(num(m.burnThroughRange))
            .append(",\"seekerFovDeg\":").append(num(Math.toDegrees(m.seekerFov)))
            .append(",\"gimbalLimitDeg\":")
            .append(num(Math.toDegrees(m.seekerGimbalLimit)))
            .append(",\"seekerActivationRange\":")
            .append(num(m.seekerActivationRange))
            .append(",\"reacquireTicks\":").append(m.reacquireTicks)
            .append(",\"lockConfirmTicks\":").append(m.lockConfirmTicks)
            .append(",\"gimbalMemoryTicks\":").append(m.gimbalMemoryTicks)
            .append(",\"gimbalConfirmTicks\":").append(m.gimbalConfirmTicks)
            .append(",\"extrapolateOnLoss\":").append(m.extrapolateOnLoss)
            .append(",\"extrapolateAfterDecoy\":").append(m.extrapolateAfterDecoy)
            .append(",\"scanRateDegreesPerSecond\":")
            .append(num(Math.toDegrees(m.seekerScanRate) * TPS))
            .append(",\"datalinkUpdateSeconds\":")
            .append(num(m.datalinkUpdateTicks / TPS))
            .append(",\"datalinkErrorDegrees\":")
            .append(num(Math.toDegrees(m.datalinkError)))
            .append(",\"inertialDriftMetresPerSecond\":")
            .append(num(m.inertialDrift * TPS))
            .append(",\"chaffSusceptibility\":").append(num(m.chaffSusceptibility))
            .append(",\"flareSusceptibility\":").append(num(m.flareSusceptibility))
            .append(",\"heatSourceCoefficient\":")
            .append(num(m.heatSourceCoefficient))
            .append(",\"seekerType\":").append(quote(m.seekerType.name()))
            .append(",\"twoWayDatalink\":").append(m.twoWayDatalink)
            .append(",\"maxLifetimeTicks\":").append(m.maxLifetimeTicks)
            .append(",\"maxLaunchRange\":").append(num(m.maxLaunchRange))
            .append(",\"minLaunchRange\":").append(num(m.minLaunchRange))
            .append(",\"proximityFuseRadius\":").append(num(m.proximityFuseRadius))
            .append(",\"armingDistance\":").append(num(m.armingDistance))
            .append(",\"maxLoadG\":").append(num(m.maxLoadG))
            .append('}');
    }

    private static String frame(Session s, int tick,
                                List<Frame.Aircraft> aircraft,
                                List<Frame.Decoy> decoys,
                                List<Map<String, Object>> events) {
        StringBuilder b = new StringBuilder(4096);
        b.append("{\"t\":").append(tick)
            .append(",\"gameTime\":").append(s.startedAtGameTime + tick)
            .append(",\"aircraft\":[");
        for (int i = 0; i < aircraft.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            writeAircraft(s, b, aircraft.get(i));
        }
        b.append("],\"missiles\":[");
        for (int i = 0; i < s.pending.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            writeMissile(s, b, s.pending.get(i));
        }
        b.append("],\"decoys\":[");
        for (int i = 0; i < decoys.size(); i++) {
            Frame.Decoy d = decoys.get(i);
            if (i > 0) {
                b.append(',');
            }
            b.append("{\"id\":").append(d.id())
                .append(",\"kind\":").append(quote(d.kind().name()))
                .append(",\"pos\":").append(vec(d.position()))
                .append(",\"vel\":").append(vec(d.velocity()))
                .append(",\"age\":").append(d.age())
                .append(",\"nearestAircraftBlocks\":").append(num(d.separation()))
                .append(",\"bloomed\":")
                .append(d.separation() >= SeekerPolicy.CHAFF_MIN_SEPARATION)
                .append('}');
        }
        b.append("],\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(object(events.get(i)));
        }
        return b.append("]}").toString();
    }

    private static void writeAircraft(Session s, StringBuilder b,
                                      Frame.Aircraft a) {
        double bpt = a.velocity().length();
        AtmosphereModel air = FlightModelRegistry.instance().atmosphere();
        AiPilotBrain brain = a.brain();
        AiSituation situation = brain == null ? null
            : brain.lastSituationForDebug();
        AiThreat threat = brain == null ? null : brain.lastThreatForDebug();
        AiCommand command = brain == null ? null : brain.lastCommandForDebug();
        AiFlightPhase phase = brain == null ? null : brain.phase();

        b.append("{\"id\":").append(quote(a.traceId()))
            .append(",\"side\":").append(quote(side(a)))
            .append(",\"airframe\":").append(quote(a.airframe()))
            .append(",\"layer\":").append(a.live() ? "\"entity\"" : "\"virtual\"")
            .append(",\"pos\":").append(vec(a.position()))
            .append(",\"vel\":").append(vec(a.velocity()))
            .append(",\"speedBpt\":").append(num(bpt))
            .append(",\"speedBps\":").append(num(bpt * TPS))
            .append(",\"speedMps\":").append(num(bpt * TPS))
            .append(",\"mach\":").append(num(air.mach(bpt * TPS, a.position().y)))
            .append(",\"fwd\":").append(vec(a.forward()))
            .append(",\"up\":").append(vec(a.up()))
            .append(",\"right\":").append(vec(a.right()))
            .append(",\"throttle\":").append(a.throttle())
            .append(",\"afterburner\":").append(a.spool() > 0.5F)
            .append(",\"afterburnerSpool\":").append(num(a.spool()))
            .append(",\"gear\":").append(num(a.gear()))
            .append(",\"flaps\":").append(num(a.flaps()))
            .append(",\"onGround\":").append(a.onGround());

        FlightState flight = a.flight();
        if (flight == null) {
            b.append(",\"alphaDeg\":null,\"sideSlipDeg\":null,\"loadG\":null,")
                .append("\"stalled\":null,\"limiterActive\":null,")
                .append("\"dynamicPressure\":null");
        } else {
            b.append(",\"alphaDeg\":")
                .append(num(flight.angleOfAttack()))
                .append(",\"limiterAlphaDeg\":").append(num(limiterAlpha(a)))
                .append(",\"sideSlipDeg\":")
                .append(num(flight.sideSlip()))
                .append(",\"loadG\":").append(num(flight.loadFactor()))
                .append(",\"maxPositiveG\":").append(num(limitG(a)))
                .append(",\"stalled\":").append(flight.stalled())
                .append(",\"limiterActive\":").append(flight.limiterActive())
                .append(",\"dynamicPressure\":").append(num(flight.dynamicPressure()));
        }

        b.append(",\"phase\":").append(phase == null ? "null" : quote(phase.name()));
        if (situation != null && phase != null) {
            double[] goal = AiPilotDirector.goal(situation, phase, threat);
            b.append(",\"goal\":[").append(num(goal[0])).append(',')
                .append(num(goal[1])).append(',').append(num(goal[2]))
                .append(']')
                .append(",\"targetRange\":")
                .append(num(situation.hasTarget() ? situation.targetRange()
                    : Double.NaN))
                .append(",\"underMissileThreat\":")
                .append(situation.underMissileThreat());
        } else {
            b.append(",\"goal\":null,\"targetRange\":null,")
                .append("\"underMissileThreat\":null");
        }
        b.append(",\"whyNoLaunch\":")
            .append(brain == null ? "null" : quote(brain.fireBlockForDebug()))
            .append(",\"launchInterval\":")
            .append(brain == null ? "null" : String.valueOf(
                brain.launchIntervalForDebug()));
        if (command != null) {
            b.append(",\"stick\":{\"pitch\":").append(num(command.pitch()))
                .append(",\"yaw\":").append(num(command.yaw()))
                .append(",\"roll\":").append(num(command.roll()))
                .append(",\"fireGun\":").append(command.fireGun())
                .append(",\"launchMissile\":").append(command.launchMissile())
                .append('}');
        }
        b.append(",\"stores\":[");
        String[] loadout = a.loadout();
        for (int i = 0; loadout != null && i < loadout.length; i++) {
            if (i > 0) {
                b.append(',');
            }
            String id = loadout[i];
            b.append(id == null || id.isBlank() ? "null" : quote(id));
        }
        b.append(']');
        writeNotch(s, b, a, brain, threat);
        b.append('}');
    }

    private static void writeNotch(Session s, StringBuilder b,
                                   Frame.Aircraft a,
                                   @Nullable AiPilotBrain brain,
                                   @Nullable AiThreat threat) {
        b.append(",\"notch\":{\"threat\":");
        String threatId = null;
        if (threat != null) {
            double best = Double.MAX_VALUE;
            for (MissileSample m : s.pending) {
                double d = m.position().distanceToSqr(
                    new Vec3(threat.x(), threat.y(), threat.z()));
                if (d < best) {
                    best = d;
                    threatId = s.nameOf(m.id(), true);
                }
            }
            if (best > 1.0D) {
                threatId = null;
            }
        }
        b.append(threatId == null ? "null" : quote(threatId));
        if (brain == null || threat == null) {
            b.append(",\"sideLatched\":null,\"trimDeg\":null,")
                .append("\"commandedDeg\":null,\"flownBeamDeg\":null");
        } else {
            double flown = AiNotchPolicy.signedBeamAngle(a.position(),
                a.velocity(), new Vec3(threat.x(), threat.y(), threat.z()));
            b.append(",\"sideLatched\":").append(brain.notchSideForDebug())
                .append(",\"trimDeg\":")
                .append(num(Math.toDegrees(brain.notchTrimForDebug())))
                .append(",\"commandedDeg\":")
                .append(num(Math.toDegrees(
                    brain.notchSideForDebug() * brain.rank().notchError()
                        + brain.notchTrimForDebug())))
                .append(",\"flownBeamDeg\":").append(num(Math.toDegrees(flown)));
        }
        b.append(",\"inGateOf\":[");
        boolean first = true;
        for (MissileSample m : s.pending) {
            if (!a.id().equals(m.targetId())) {
                continue;
            }
            Gate gate = Gate.of(m);
            if (gate.inGate) {
                if (!first) {
                    b.append(',');
                }
                first = false;
                b.append(quote(s.nameOf(m.id(), true)));
            }
        }
        b.append(']');
        if (brain != null) {
            long now = s.startedAtGameTime + s.frames;
            b.append(",\"dispensing\":")
                .append(brain.dispensedAtForDebug() >= now - 1L
                    && brain.dispensingForDebug() != null
                    ? quote(brain.dispensingForDebug().name()) : "null");
        }
        b.append('}');
    }

    private record Gate(double radialBpt, double gateBpt, boolean inGate,
                        boolean applies) {
        static Gate of(MissileSample m) {
            MissileProfile p = m.profile();
            if (m.targetPosition() == null || m.targetVelocity() == null
                    || !p.seekerType.radar()) {
                return new Gate(Double.NaN, Double.NaN, false,
                    p.seekerType.radar());
            }
            Vec3 los = m.targetPosition().subtract(m.position());
            double distance = los.length();
            double gate = SeekerPolicy.burnThroughGate(p.velocityGate,
                p.burnThroughRange, distance);
            double radial = distance < 1.0E-6D ? Double.NaN
                : m.targetVelocity().dot(los) / distance;
            return new Gate(radial, gate, SeekerPolicy.velocityGated(gate,
                p.velocityGateLookDownOnly, m.targetVelocity(), los,
                m.position().y, m.targetPosition().y), true);
        }
    }

    private static void writeMissile(Session s, StringBuilder b,
                                     MissileSample m) {
        SeekerState k = m.seeker();
        Gate gate = Gate.of(m);
        double bpt = m.velocity().length();
        double loadG = loadPulled(m.profile(),
            s.previousVelocity.put(m.id(), m.velocity()), m.velocity());
        double range = m.targetPosition() == null ? Double.NaN
            : m.position().distanceTo(m.targetPosition());
        b.append("{\"id\":").append(quote(s.nameOf(m.id(), true)))
            .append(",\"type\":").append(quote(m.profile().id))
            .append(",\"layer\":").append(m.virtual() ? "\"virtual\"" : "\"entity\"")
            .append(",\"shooter\":")
            .append(quote(nameOrNull(s, m.sourcePlaneId(), false)))
            .append(",\"target\":")
            .append(quote(nameOrNull(s, m.targetId(), false)))
            .append(",\"pos\":").append(vec(m.position()))
            .append(",\"vel\":").append(vec(m.velocity()))
            .append(",\"axis\":").append(vec(m.axis()))
            .append(",\"speedBpt\":").append(num(bpt))
            .append(",\"speedBps\":").append(num(bpt * TPS))
            .append(",\"loadG\":").append(num(loadG))
            .append(",\"maxLoadG\":").append(num(m.profile().maxLoadG))
            .append(",\"age\":").append(m.age())
            .append(",\"travelled\":").append(num(m.travelled()))
            .append(",\"motorLit\":")
            .append(MissilePolicy.motorLit(m.profile(), m.age()))
            .append(",\"launchLoadLimited\":")
            .append(MissilePolicy.launchLoadLimited(m.age()))
            .append(",\"rangeToTarget\":").append(num(range))
            .append(",\"closestMiss\":").append(num(m.closestMiss()))
            .append(",\"phase\":").append(quote(m.solution().phase().name()))
            .append(",\"seeker\":{\"active\":").append(k.active)
            .append(",\"tracking\":").append(k.tracking)
            .append(",\"decoyedNow\":").append(m.solution().decoyed())
            .append(",\"loss\":").append(quote(k.loss.name()))
            .append(",\"look\":").append(vec(k.look))
            .append(",\"capturedDecoy\":").append(k.capturedDecoy)
            .append(",\"everDecoyed\":").append(k.everDecoyed)
            .append(",\"relockingAfterDecoy\":").append(k.relockingAfterDecoy)
            .append(",\"reacquireTimer\":").append(k.reacquireTimer)
            .append(",\"confirmTicks\":").append(k.confirmTicks)
            .append(",\"gimbalMemoryTicks\":").append(k.gimbalMemoryTicks)
            .append(",\"ticksInGate\":").append(k.ticksInGate)
            .append(",\"ticksTracking\":").append(k.ticksTracking)
            .append(",\"relocks\":").append(k.relocks)
            .append(",\"everTracked\":").append(k.everTracked)
            .append(",\"spent\":").append(k.spent)
            .append(",\"link\":").append(quote(m.link().name()))
            .append(",\"linkAge\":").append(k.linkAge)
            .append(",\"midcourseAge\":").append(k.midcourseAge)
            .append(",\"cueAge\":").append(k.cueAge)
            .append(",\"linkPos\":").append(vec(k.linkPosition))
            .append(",\"cuePos\":").append(vec(k.cuePosition))
            .append(",\"activatedAt\":").append(num(k.activatedAt))
            .append(",\"lastRange\":").append(num(k.lastRange))
            .append(",\"closestRange\":").append(num(k.closestRange))
            .append(",\"furthestRange\":").append(num(k.furthestRange))
            .append(",\"sawTargetAt\":").append(vec(m.targetPosition()))
            .append('}')
            .append(",\"gate\":{\"radialBpt\":").append(num(gate.radialBpt()))
            .append(",\"radialBps\":").append(num(gate.radialBpt() * TPS))
            .append(",\"gateBpt\":").append(num(gate.gateBpt()))
            .append(",\"gateBps\":").append(num(gate.gateBpt() * TPS))
            .append(",\"inGate\":").append(gate.inGate())
            .append(",\"applies\":").append(gate.applies())
            .append('}')
            .append(",\"aim\":").append(vec(m.solution().aimPosition()))
            .append('}');
    }

    private static double limiterAlpha(Frame.Aircraft a) {
        AircraftPhysicsProfile profile = a.airframe() == null ? null
            : FlightModelRegistry.instance().profile(a.airframe());
        return profile == null ? Double.NaN
            : profile.limiterAngleOfAttack();
    }

    private static double limitG(Frame.Aircraft a) {
        AircraftPhysicsProfile profile = a.airframe() == null ? null
            : FlightModelRegistry.instance().profile(a.airframe());
        return profile == null ? Double.NaN : profile.maxPositiveG();
    }

    private static double loadPulled(MissileProfile profile,
                                     @Nullable Vec3 before, Vec3 after) {
        if (before == null) {
            return Double.NaN;
        }
        double a = before.length();
        double b = after.length();
        if (a < 1.0E-6D || b < 1.0E-6D) {
            return 0.0D;
        }
        double cos = Math.max(-1.0D, Math.min(1.0D,
            before.dot(after) / (a * b)));
        return MissilePolicy.loadFactorFromTurn(profile, b, Math.acos(cos));
    }

    private static String side(Frame.Aircraft a) {
        String team = a.side();
        if (team == null || team.isBlank()) {
            return "blue";
        }
        String lower = team.toLowerCase(Locale.ROOT);
        if (lower.contains("red")) {
            return "red";
        }
        if (lower.contains("blue")) {
            return "blue";
        }
        return (team.hashCode() & 1) == 0 ? "blue" : "red";
    }

    private static Map<String, Object> extra(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return out;
    }

    private static String object(Map<String, Object> map) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(quote(e.getKey())).append(':').append(value(e.getValue()));
        }
        return b.append('}').toString();
    }

    private static String value(@Nullable Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof String text) {
            return quote(text);
        }
        if (o instanceof Boolean) {
            return o.toString();
        }
        if (o instanceof Number number) {
            return num(number.doubleValue());
        }
        return quote(String.valueOf(o));
    }

    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "null";
        }
        double rounded = Math.round(v * 1000.0D) / 1000.0D;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1.0E15D) {
            return String.valueOf((long) rounded);
        }
        String s = String.format(Locale.ROOT, "%.3f", rounded);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static double round3(double v) {
        return Double.isFinite(v) ? Math.round(v * 1000.0D) / 1000.0D
            : Double.NaN;
    }

    private static String vec(@Nullable Vec3 v) {
        return v == null ? "null"
            : "[" + num(v.x) + "," + num(v.y) + "," + num(v.z) + "]";
    }

    private static String quote(@Nullable String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}

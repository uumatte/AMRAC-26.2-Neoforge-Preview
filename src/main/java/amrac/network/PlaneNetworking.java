package amrac.network;

import amrac.platform.PayloadTypeRegistry;
import amrac.platform.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;
import amrac.MathUtil;
import amrac.AmracMod;
import amrac.entities.PlaneEntity;
import amrac.entities.RadarContact;

/**
 * Common code sends packets only through the sender hook the client registers; if common code can
 * reach ClientPlayNetworking, a dedicated server crashes in class verification.
 */
public final class PlaneNetworking {
    public static final CustomPacketPayload.Type<RawPayload> ROTATION =
        payloadType("rotation");
    public static final CustomPacketPayload.Type<RawPayload> CONTROL =
        payloadType("control");
    public static final CustomPacketPayload.Type<RawPayload> GEAR =
        payloadType("gear");
    public static final CustomPacketPayload.Type<RawPayload> FLAPS =
        payloadType("flaps");
    public static final CustomPacketPayload.Type<RawPayload> TRIGGER =
        payloadType("trigger");
    public static final CustomPacketPayload.Type<RawPayload> BOMB =
        payloadType("bomb");
    public static final CustomPacketPayload.Type<RawPayload> RELEASE_CHAFF =
        payloadType("release_chaff");
    public static final CustomPacketPayload.Type<RawPayload> RELEASE_FLARE =
        payloadType("release_flare");
    public static final CustomPacketPayload.Type<RawPayload> DISMOUNT =
        payloadType("dismount");
    public static final CustomPacketPayload.Type<RawPayload> MISSILE =
        payloadType("missile");
    public static final CustomPacketPayload.Type<RawPayload> CRASH =
        payloadType("crash");
    public static final CustomPacketPayload.Type<RawPayload> SPEED_BRAKE =
        payloadType("speed_brake");
    public static final CustomPacketPayload.Type<RawPayload> WHEEL_BRAKE =
        payloadType("wheel_brake");
    public static final CustomPacketPayload.Type<RawPayload> FUEL_DUMP =
        payloadType("fuel_dump");
    public static final CustomPacketPayload.Type<RawPayload> AOA_LIMITER =
        payloadType("aoa_limiter");
    public static final CustomPacketPayload.Type<RawPayload> RADAR_SWITCH =
        payloadType("radar_switch");
    public static final CustomPacketPayload.Type<RawPayload> MISSILE_SWITCH =
        payloadType("missile_switch");
    public static final CustomPacketPayload.Type<RawPayload> MISSILE_WARMUP =
        payloadType("missile_warmup");
    public static final int MISSILE_WARMUP_ROUND_CHARS = 64;
    public static final CustomPacketPayload.Type<RawPayload> RADAR_CONTACTS =
        payloadType("radar_contacts");

    public static final CustomPacketPayload.Type<RawPayload> MISSILE_THREATS =
        payloadType("missile_threats");

    public static final CustomPacketPayload.Type<RawPayload> BOUNDARY =
        payloadType("boundary");

    public static final CustomPacketPayload.Type<RawPayload> MISSILE_TRACKS =
        payloadType("missile_tracks");

    public static final CustomPacketPayload.Type<RawPayload> AIRCRAFT_TRACKS =
        payloadType("aircraft_tracks");

    public static final CustomPacketPayload.Type<RawPayload> MISSILE_SEEKER =
        payloadType("missile_seeker");

    public static final CustomPacketPayload.Type<RawPayload> STRUCTURE_PREVIEW =
        payloadType("structure_preview");

    public static final CustomPacketPayload.Type<RawPayload> CONSOLE_ACTION =
        payloadType("console_action");

    public static final CustomPacketPayload.Type<RawPayload> FLIGHT_MODEL_SYNC =
        payloadType("flight_model_sync");

    public static final CustomPacketPayload.Type<RawPayload> GPS_REQUEST =
        payloadType("gps_request");

    public static final CustomPacketPayload.Type<RawPayload> AI_DEBUG =
        payloadType("ai_debug");

    public static final CustomPacketPayload.Type<RawPayload> GPS_CONTACTS =
        payloadType("gps_contacts");

    public static final CustomPacketPayload.Type<RawPayload> GPS_PIN =
        payloadType("gps_pin");

    private static boolean payloadTypesRegistered;

    @FunctionalInterface
    public interface RotationSender {
        void send(Quaternionf attitude, Vec3 motion);
    }

    @FunctionalInterface
    public interface ControlSender {
        void send(int throttle, float pitch, float yaw, float roll,
                  boolean groundReverse);
    }

    @FunctionalInterface
    public interface GearSender {
        void send(boolean down);
    }

    @FunctionalInterface
    public interface TriggerSender {
        void send(boolean firing);
    }

    @FunctionalInterface
    public interface RequestSender {
        void send();
    }

    @FunctionalInterface
    public interface MissileSender {
        void send(java.util.UUID targetAircraftId);
    }

    @Nullable
    private static RotationSender rotationSender;
    @Nullable
    private static ControlSender controlSender;
    @Nullable
    private static GearSender gearSender;
    private static GearSender flapsSender;
    @Nullable
    private static TriggerSender triggerSender;
    @Nullable
    private static RequestSender bombSender;
    @Nullable
    private static RequestSender chaffSender;
    @Nullable
    private static RequestSender flareSender;
    @Nullable
    private static RequestSender dismountSender;
    @Nullable
    private static RequestSender crashSender;
    @Nullable
    private static GearSender speedBrakeSender;
    @Nullable
    private static GearSender wheelBrakeSender;
    @Nullable
    private static GearSender fuelDumpSender;
    @Nullable
    private static GearSender radarSwitchSender;
    @Nullable
    private static GearSender aoaLimiterSender;
    @Nullable
    private static ContactSink contactSink;

    @FunctionalInterface
    public interface ContactSink {
        void accept(java.util.List<RadarContact> contacts);
    }
    @Nullable
    private static MissileSender missileSender;

    private PlaneNetworking() {
    }

    private static CustomPacketPayload.Type<RawPayload> payloadType(String path) {
        return new CustomPacketPayload.Type<>(AmracMod.id(path));
    }

    public record RawPayload(CustomPacketPayload.Type<RawPayload> type,
                             byte[] data) implements CustomPacketPayload {
        public RawPayload {
            data = data.clone();
        }

        public FriendlyByteBuf buffer() {
            return new FriendlyByteBuf(
                io.netty.buffer.Unpooled.wrappedBuffer(data));
        }
    }

    public static RawPayload payload(CustomPacketPayload.Type<RawPayload> type,
                                     FriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), data);
        return new RawPayload(type, data);
    }

    static StreamCodec<RegistryFriendlyByteBuf, RawPayload> codec(
        CustomPacketPayload.Type<RawPayload> type, int maximumBytes) {
        return StreamCodec.of((output, value) -> {
            if (!type.equals(value.type())) {
                throw new IllegalArgumentException("Payload type mismatch: " +
                    value.type().id());
            }
            output.writeByteArray(value.data());
        }, input -> new RawPayload(type, input.readByteArray(maximumBytes)));
    }

    public static void setRotationSender(RotationSender sender) {
        rotationSender = sender;
    }

    public static void setControlSender(ControlSender sender) {
        controlSender = sender;
    }

    public static void sendRotation(Quaternionf attitude, Vec3 motion) {
        if (rotationSender != null) {
            rotationSender.send(attitude, motion);
        }
    }

    public static void sendControls(int throttle, float pitch, float yaw,
                                    float roll, boolean groundReverse) {
        if (controlSender != null) {
            controlSender.send(throttle, pitch, yaw, roll, groundReverse);
        }
    }

    public static void setGearSender(GearSender sender) {
        gearSender = sender;
    }

    public static void setFlapsSender(GearSender sender) {
        flapsSender = sender;
    }

    public static void sendFlaps(boolean down) {
        if (flapsSender != null) {
            flapsSender.send(down);
        }
    }

    public static void sendGear(boolean down) {
        if (gearSender != null) {
            gearSender.send(down);
        }
    }

    public static void setTriggerSender(TriggerSender sender) {
        triggerSender = sender;
    }

    public static void sendTrigger(boolean firing) {
        if (triggerSender != null) {
            triggerSender.send(firing);
        }
    }

    public static void setBombSender(RequestSender sender) {
        bombSender = sender;
    }

    public static void setCountermeasureSenders(RequestSender chaff,
                                                RequestSender flare) {
        chaffSender = chaff;
        flareSender = flare;
    }

    public static void sendChaffRelease() {
        if (chaffSender != null) {
            chaffSender.send();
        }
    }

    public static void sendFlareRelease() {
        if (flareSender != null) {
            flareSender.send();
        }
    }

    public static void sendBomb() {
        if (bombSender != null) {
            bombSender.send();
        }
    }

    public static void setDismountSender(RequestSender sender) {
        dismountSender = sender;
    }

    public static void sendDismount() {
        if (dismountSender != null) {
            dismountSender.send();
        }
    }

    public static void setCrashSender(RequestSender sender) {
        crashSender = sender;
    }

    public static void sendCrash() {
        if (crashSender != null) {
            crashSender.send();
        }
    }

    public static void setSpeedBrakeSender(GearSender sender) {
        speedBrakeSender = sender;
    }

    public static void setWheelBrakeSender(GearSender sender) {
        wheelBrakeSender = sender;
    }

    public static void setFuelDumpSender(GearSender sender) {
        fuelDumpSender = sender;
    }

    public static void setRadarSwitchSender(GearSender sender) {
        radarSwitchSender = sender;
    }

    public static void setAoaLimiterSender(GearSender sender) {
        aoaLimiterSender = sender;
    }

    public static void sendAoaLimiter(boolean enabled) {
        if (aoaLimiterSender != null) {
            aoaLimiterSender.send(enabled);
        }
    }

    public static void sendRadarSwitch(boolean on) {
        if (radarSwitchSender != null) {
            radarSwitchSender.send(on);
        }
    }

    public static void setContactSink(ContactSink sink) {
        contactSink = sink;
    }

    public record ThreatReport(int missileId, double x, double y, double z,
                               boolean rangeLimited) {
    }

    @FunctionalInterface
    public interface ThreatSink {
        void accept(java.util.List<ThreatReport> threats);
    }

    private static ThreatSink threatSink;

    public static void setThreatSink(ThreatSink sink) {
        threatSink = sink;
    }

    public static void deliverThreats(java.util.List<ThreatReport> threats) {
        if (threatSink != null) {
            threatSink.accept(threats);
        }
    }

    @FunctionalInterface
    public interface BoundarySink {
        void accept(double distance);
    }

    private static BoundarySink boundarySink;

    public static void setBoundarySink(BoundarySink sink) {
        boundarySink = sink;
    }

    public static void deliverBoundary(double distance) {
        if (boundarySink != null) {
            boundarySink.accept(distance);
        }
    }

    public static void sendBoundary(net.minecraft.server.level.ServerPlayer player,
                                    double distance) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeDouble(distance);
        ServerPlayNetworking.send(player, payload(BOUNDARY, buf));
    }

    public record MissileTrack(int key, double x, double y, double z,
                               boolean powered) {
    }

    @FunctionalInterface
    public interface TrackSink {
        void accept(java.util.List<MissileTrack> tracks);
    }

    private static TrackSink trackSink;

    public static void setTrackSink(TrackSink sink) {
        trackSink = sink;
    }

    public static void deliverMissileTracks(java.util.List<MissileTrack> tracks) {
        if (trackSink != null) {
            trackSink.accept(tracks);
        }
    }

    public static void writeMissileTracks(FriendlyByteBuf buf, double originX,
                                          double originY, double originZ,
                                          java.util.List<MissileTrack> tracks) {
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeVarInt(tracks.size());
        for (MissileTrack track : tracks) {
            buf.writeInt(track.key());
            buf.writeFloat((float) (track.x() - originX));
            buf.writeFloat((float) (track.y() - originY));
            buf.writeFloat((float) (track.z() - originZ));
            buf.writeBoolean(track.powered());
        }
    }

    public static java.util.List<MissileTrack> readMissileTracks(
            FriendlyByteBuf buf) {
        double originX = buf.readDouble();
        double originY = buf.readDouble();
        double originZ = buf.readDouble();
        int count = buf.readVarInt();
        java.util.List<MissileTrack> out = new java.util.ArrayList<>(
            Math.max(0, Math.min(count, 64)));
        for (int i = 0; i < count; i++) {
            int key = buf.readInt();
            double x = originX + buf.readFloat();
            double y = originY + buf.readFloat();
            double z = originZ + buf.readFloat();
            out.add(new MissileTrack(key, x, y, z, buf.readBoolean()));
        }
        return out;
    }

    public static void sendMissileTracks(
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.world.phys.Vec3 origin,
            java.util.List<MissileTrack> tracks) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeMissileTracks(buf, origin.x, origin.y, origin.z, tracks);
        ServerPlayNetworking.send(player, payload(MISSILE_TRACKS, buf));
    }

    public record AircraftTrack(java.util.UUID id, int typeId,
                                double x, double y, double z,
                                float qx, float qy, float qz, float qw,
                                boolean gearDown, boolean afterburner) {
    }

    @FunctionalInterface
    public interface AircraftTrackSink {
        void accept(java.util.List<AircraftTrack> contacts);
    }

    private static AircraftTrackSink aircraftTrackSink;

    public static void setAircraftTrackSink(AircraftTrackSink sink) {
        aircraftTrackSink = sink;
    }

    public static void deliverAircraftTracks(
            java.util.List<AircraftTrack> contacts) {
        if (aircraftTrackSink != null) {
            aircraftTrackSink.accept(contacts);
        }
    }

    public static void writeAircraftTracks(FriendlyByteBuf buf, double originX,
                                           double originY, double originZ,
                                           java.util.List<AircraftTrack> contacts) {
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeVarInt(contacts.size());
        for (AircraftTrack contact : contacts) {
            buf.writeUUID(contact.id());
            buf.writeVarInt(contact.typeId());
            buf.writeFloat((float) (contact.x() - originX));
            buf.writeFloat((float) (contact.y() - originY));
            buf.writeFloat((float) (contact.z() - originZ));
            buf.writeFloat(contact.qx());
            buf.writeFloat(contact.qy());
            buf.writeFloat(contact.qz());
            buf.writeFloat(contact.qw());
            buf.writeByte((contact.gearDown() ? 1 : 0)
                | (contact.afterburner() ? 2 : 0));
        }
    }

    public static java.util.List<AircraftTrack> readAircraftTracks(
            FriendlyByteBuf buf) {
        double originX = buf.readDouble();
        double originY = buf.readDouble();
        double originZ = buf.readDouble();
        int count = buf.readVarInt();
        java.util.List<AircraftTrack> out = new java.util.ArrayList<>(
            Math.max(0, Math.min(count, 64)));
        for (int i = 0; i < count; i++) {
            java.util.UUID id = buf.readUUID();
            int typeId = buf.readVarInt();
            double x = originX + buf.readFloat();
            double y = originY + buf.readFloat();
            double z = originZ + buf.readFloat();
            float qx = buf.readFloat();
            float qy = buf.readFloat();
            float qz = buf.readFloat();
            float qw = buf.readFloat();
            int flags = buf.readByte();
            out.add(new AircraftTrack(id, typeId, x, y, z, qx, qy, qz, qw,
                (flags & 1) != 0, (flags & 2) != 0));
        }
        return out;
    }

    public static void sendAircraftTracks(
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.world.phys.Vec3 origin,
            java.util.List<AircraftTrack> contacts) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeAircraftTracks(buf, origin.x, origin.y, origin.z, contacts);
        ServerPlayNetworking.send(player, payload(AIRCRAFT_TRACKS, buf));
    }

    public record MissileSeeker(int key, double x, double y, double z,
                                float axisX, float axisY, float axisZ,
                                float speed, boolean hasTarget,
                                double targetX, double targetY, double targetZ,
                                float closure, float targetSpeed,
                                boolean locked, boolean powered,
                                boolean hit) {
    }

    @FunctionalInterface
    public interface SeekerSink {
        void accept(java.util.List<MissileSeeker> frames);
    }

    private static SeekerSink seekerSink;

    public static void setSeekerSink(SeekerSink sink) {
        seekerSink = sink;
    }

    public static void deliverSeekerFrames(java.util.List<MissileSeeker> frames) {
        if (seekerSink != null) {
            seekerSink.accept(frames);
        }
    }

    public static void writeSeekerFrames(FriendlyByteBuf buf, double originX,
                                         double originY, double originZ,
                                         java.util.List<MissileSeeker> frames) {
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeVarInt(frames.size());
        for (MissileSeeker frame : frames) {
            buf.writeInt(frame.key());
            buf.writeFloat((float) (frame.x() - originX));
            buf.writeFloat((float) (frame.y() - originY));
            buf.writeFloat((float) (frame.z() - originZ));
            buf.writeFloat(frame.axisX());
            buf.writeFloat(frame.axisY());
            buf.writeFloat(frame.axisZ());
            buf.writeFloat(frame.speed());
            buf.writeBoolean(frame.hasTarget());
            if (frame.hasTarget()) {
                buf.writeFloat((float) (frame.targetX() - originX));
                buf.writeFloat((float) (frame.targetY() - originY));
                buf.writeFloat((float) (frame.targetZ() - originZ));
                buf.writeFloat(frame.closure());
                buf.writeFloat(frame.targetSpeed());
            }
            buf.writeBoolean(frame.locked());
            buf.writeBoolean(frame.powered());
            buf.writeBoolean(frame.hit());
        }
    }

    public static java.util.List<MissileSeeker> readSeekerFrames(
            FriendlyByteBuf buf) {
        double originX = buf.readDouble();
        double originY = buf.readDouble();
        double originZ = buf.readDouble();
        int count = buf.readVarInt();
        java.util.List<MissileSeeker> out = new java.util.ArrayList<>(
            Math.max(0, Math.min(count, 16)));
        for (int i = 0; i < count; i++) {
            int key = buf.readInt();
            double x = originX + buf.readFloat();
            double y = originY + buf.readFloat();
            double z = originZ + buf.readFloat();
            float ax = buf.readFloat();
            float ay = buf.readFloat();
            float az = buf.readFloat();
            float speed = buf.readFloat();
            boolean hasTarget = buf.readBoolean();
            double tx = 0.0D;
            double ty = 0.0D;
            double tz = 0.0D;
            float closure = 0.0F;
            float targetSpeed = 0.0F;
            if (hasTarget) {
                tx = originX + buf.readFloat();
                ty = originY + buf.readFloat();
                tz = originZ + buf.readFloat();
                closure = buf.readFloat();
                targetSpeed = buf.readFloat();
            }
            out.add(new MissileSeeker(key, x, y, z, ax, ay, az, speed,
                hasTarget, tx, ty, tz, closure, targetSpeed,
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }
        return out;
    }

    public static void sendSeekerFrames(
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.world.phys.Vec3 origin,
            java.util.List<MissileSeeker> frames) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeSeekerFrames(buf, origin.x, origin.y, origin.z, frames);
        ServerPlayNetworking.send(player, payload(MISSILE_SEEKER, buf));
    }

    public static void writeThreats(FriendlyByteBuf buf,
                                    java.util.List<ThreatReport> threats) {
        buf.writeVarInt(threats.size());
        for (ThreatReport t : threats) {
            buf.writeVarInt(t.missileId());
            buf.writeDouble(t.x());
            buf.writeDouble(t.y());
            buf.writeDouble(t.z());
            buf.writeBoolean(t.rangeLimited());
        }
    }

    public static java.util.List<ThreatReport> readThreats(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        java.util.List<ThreatReport> out = new java.util.ArrayList<>(
            Math.max(0, Math.min(count, 64)));
        for (int i = 0; i < count; i++) {
            out.add(new ThreatReport(buf.readVarInt(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readBoolean()));
        }
        return out;
    }

    public enum ConsoleAction {
        SET_MODE, CONNECT, ENTER, APPLY, DRAW
    }

    public interface ConsoleSender {
        void send(net.minecraft.core.BlockPos pos, ConsoleAction action,
                  String text, String value, double first, double second,
                  amrac.display.AxisBounds bounds);
    }

    @Nullable
    private static ConsoleSender consoleSender;

    public static void setConsoleSender(ConsoleSender sender) {
        consoleSender = sender;
    }

    public static void sendConsole(net.minecraft.core.BlockPos pos,
                                   ConsoleAction action, String text,
                                   String value, double first, double second,
                                   amrac.display.AxisBounds bounds) {
        if (consoleSender != null) {
            consoleSender.send(pos, action, text, value, first, second, bounds);
        }
    }

    public static void writeConsole(FriendlyByteBuf buf,
                                    net.minecraft.core.BlockPos pos,
                                    ConsoleAction action, String text,
                                    String value, double first, double second,
                                    amrac.display.AxisBounds bounds) {
        buf.writeBlockPos(pos);
        buf.writeByte(action.ordinal());
        buf.writeUtf(text == null ? "" : text, 128);
        buf.writeUtf(value == null ? "" : value, 256);
        buf.writeDouble(first);
        buf.writeDouble(second);
        amrac.display.AxisBounds pinned = bounds == null ? amrac.display.AxisBounds.AUTO : bounds;
        buf.writeDouble(pinned.xMin());
        buf.writeDouble(pinned.xMax());
        buf.writeDouble(pinned.yMin());
        buf.writeDouble(pinned.yMax());
    }

    public static void sendStructurePreview(
            net.minecraft.server.level.ServerPlayer player,
            int[] box, int ticks) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeBoolean(box != null);
        if (box != null) {
            for (int value : box) buf.writeInt(value);
            buf.writeVarInt(ticks);
        }
        ServerPlayNetworking.send(player, payload(STRUCTURE_PREVIEW, buf));
    }

    public static int[] readStructurePreview(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        int[] out = new int[7];
        for (int i = 0; i < 6; i++) out[i] = buf.readInt();
        out[6] = buf.readVarInt();
        return out;
    }

    public static void sendThreats(net.minecraft.server.level.ServerPlayer player,
                                   java.util.List<ThreatReport> threats) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeThreats(buf, threats);
        ServerPlayNetworking.send(player, payload(MISSILE_THREATS, buf));
    }

    public static void writeContacts(FriendlyByteBuf buf,
                                     java.util.List<RadarContact> contacts) {
        buf.writeVarInt(contacts.size());
        for (RadarContact contact : contacts) {
            buf.writeUUID(contact.aircraftId);
            buf.writeVarInt(contact.entityId);
            buf.writeDouble(contact.x);
            buf.writeDouble(contact.y);
            buf.writeDouble(contact.z);
            buf.writeFloat(contact.velocityX);
            buf.writeFloat(contact.velocityY);
            buf.writeFloat(contact.velocityZ);
            buf.writeFloat(contact.width);
            buf.writeFloat(contact.height);
        }
    }

    public static java.util.List<RadarContact> readContacts(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        java.util.List<RadarContact> contacts = new java.util.ArrayList<>(
            Math.max(0, Math.min(count, 256)));
        for (int i = 0; i < count; i++) {
            contacts.add(new RadarContact(buf.readUUID(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat()));
        }
        return contacts;
    }

    public static void writeGpsContacts(FriendlyByteBuf buf,
            java.util.List<amrac.gps.GpsContact> contacts) {
        buf.writeVarInt(contacts.size());
        for (var contact : contacts) {
            buf.writeVarInt(contact.kind().ordinal());
            buf.writeUtf(contact.name(), 64);
            buf.writeDouble(contact.x());
            buf.writeDouble(contact.y());
            buf.writeDouble(contact.z());
            buf.writeFloat((float) contact.speed());
            buf.writeBoolean(contact.live());
            buf.writeBoolean(contact.seekerActive());
        }
    }

    public static java.util.List<amrac.gps.GpsContact>
            readGpsContacts(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        var contacts = new java.util.ArrayList
            <amrac.gps.GpsContact>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            contacts.add(new amrac.gps.GpsContact(
                amrac.gps.GpsContact.Kind.byOrdinal(
                    buf.readVarInt()),
                buf.readUtf(64), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readFloat(), buf.readBoolean(),
                buf.readBoolean()));
        }
        return contacts;
    }

    public static void writeGpsPins(FriendlyByteBuf buf,
            java.util.List<amrac.gps.GpsPin> pins,
            java.util.UUID owner) {
        buf.writeVarInt(pins.size());
        for (var pin : pins) {
            buf.writeUUID(pin.id());
            buf.writeUtf(pin.ownerName() == null ? "" : pin.ownerName(), 32);
            buf.writeUtf(pin.name(), 64);
            buf.writeDouble(pin.x());
            buf.writeDouble(pin.z());
            buf.writeUtf(pin.sharedTeam() == null ? "" : pin.sharedTeam(), 32);
            buf.writeBoolean(owner != null && owner.equals(pin.owner()));
        }
    }

    public static java.util.List<amrac.gps.GpsPin>
            readGpsPins(FriendlyByteBuf buf, java.util.Set<java.util.UUID> mine) {
        int count = buf.readVarInt();
        var pins = new java.util.ArrayList
            <amrac.gps.GpsPin>(Math.min(count, 256));
        for (int i = 0; i < count; i++) {
            java.util.UUID id = buf.readUUID();
            String ownerName = buf.readUtf(32);
            String name = buf.readUtf(64);
            double x = buf.readDouble();
            double z = buf.readDouble();
            String team = buf.readUtf(32);
            if (buf.readBoolean()) {
                mine.add(id);
            }
            pins.add(new amrac.gps.GpsPin(id, null,
                ownerName, name, x, z, team));
        }
        return pins;
    }

    public static void sendGpsContacts(net.minecraft.server.level.ServerPlayer player,
            java.util.List<amrac.gps.GpsContact> contacts,
            java.util.List<amrac.gps.GpsPin> pins) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeGpsContacts(buf, contacts);
        writeGpsPins(buf, pins, player.getUUID());
        ServerPlayNetworking.send(player, payload(GPS_CONTACTS, buf));
    }

    public static void requestGpsContacts(boolean full) {
        if (gpsRequester != null) {
            gpsRequester.accept(full);
        }
    }

    public static void setGpsRequester(
            java.util.function.Consumer<Boolean> requester) {
        gpsRequester = requester;
    }

    private static java.util.function.Consumer<Boolean> gpsRequester;

    public static final int PIN_ADD = 0;
    public static final int PIN_REMOVE = 1;
    public static final int PIN_SHARE = 2;

    public static void sendPinEdit(int action, java.util.UUID id, String name,
                                   double x, double z) {
        if (pinEditor != null) {
            pinEditor.send(action, id, name, x, z);
        }
    }

    public interface PinEditor {
        void send(int action, java.util.UUID id, String name, double x, double z);
    }

    public static void setPinEditor(PinEditor editor) {
        pinEditor = editor;
    }

    private static PinEditor pinEditor;

    public static void openGps() {
        if (gpsOpener != null) {
            gpsOpener.run();
        }
    }

    public static void setGpsOpener(Runnable opener) {
        gpsOpener = opener;
    }

    private static Runnable gpsOpener;

    public interface GpsSink {
        void accept(java.util.List<amrac.gps.GpsContact> contacts,
                    java.util.List<amrac.gps.GpsPin> pins,
                    java.util.Set<java.util.UUID> ownPins);
    }

    public static void deliverGpsContacts(
            java.util.List<amrac.gps.GpsContact> contacts,
            java.util.List<amrac.gps.GpsPin> pins,
            java.util.Set<java.util.UUID> ownPins) {
        if (gpsSink != null) {
            gpsSink.accept(contacts, pins, ownPins);
        }
    }

    public static void setGpsSink(GpsSink sink) {
        gpsSink = sink;
    }

    private static GpsSink gpsSink;

    public static void sendRadarContacts(net.minecraft.server.level.ServerPlayer player,
                                         java.util.List<RadarContact> contacts) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeContacts(buf, contacts);
        ServerPlayNetworking.send(player, payload(RADAR_CONTACTS, buf));
    }

    public static void deliverContacts(java.util.List<RadarContact> contacts) {
        if (contactSink != null) {
            contactSink.accept(contacts);
        }
    }

    public static void sendSpeedBrake(boolean out) {
        if (speedBrakeSender != null) {
            speedBrakeSender.send(out);
        }
    }

    public static void sendWheelBrake(boolean on) {
        if (wheelBrakeSender != null) {
            wheelBrakeSender.send(on);
        }
    }

    public static void sendFuelDump(boolean open) {
        if (fuelDumpSender != null) {
            fuelDumpSender.send(open);
        }
    }

    public static void setMissileSender(MissileSender sender) {
        missileSender = sender;
    }

    public static void sendMissile(java.util.UUID targetAircraftId) {
        if (missileSender != null) {
            missileSender.send(targetAircraftId);
        }
    }

    public static void writeMissile(FriendlyByteBuf buf,
                                    java.util.UUID targetAircraftId) {
        buf.writeUUID(targetAircraftId);
    }

    public static void writeRotation(FriendlyByteBuf buf, Quaternionf attitude,
                                     Vec3 motion) {
        buf.writeFloat(attitude.x());
        buf.writeFloat(attitude.y());
        buf.writeFloat(attitude.z());
        buf.writeFloat(attitude.w());
        buf.writeDouble(motion.x());
        buf.writeDouble(motion.y());
        buf.writeDouble(motion.z());
    }

    public static void writeControls(FriendlyByteBuf buf, int throttle,
                                     float pitch, float yaw, float roll,
                                     boolean groundReverse) {
        buf.writeByte(Math.max(0, Math.min(PlaneEntity.MAX_THROTTLE, throttle)));
        buf.writeByte(toAxis(pitch));
        buf.writeByte(toAxis(yaw));
        buf.writeByte(toAxis(roll));
        buf.writeBoolean(groundReverse);
    }

    public static void writeGear(FriendlyByteBuf buf, boolean down) {
        buf.writeBoolean(down);
    }

    public static void writeTrigger(FriendlyByteBuf buf, boolean firing) {
        buf.writeBoolean(firing);
    }

    private static byte toAxis(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return (byte) Math.round(Math.max(-1.0F, Math.min(1.0F, value)) * 127.0F);
    }

    private static float fromAxis(byte value) {
        return Math.max(-1.0F, Math.min(1.0F, value / 127.0F));
    }

    public static void registerPayloadTypes() {
        if (payloadTypesRegistered) {
            return;
        }
        payloadTypesRegistered = true;

        PayloadTypeRegistry.serverboundPlay().register(CONSOLE_ACTION,
            codec(CONSOLE_ACTION, 1024));
        PayloadTypeRegistry.serverboundPlay().register(ROTATION,
            codec(ROTATION, 64));
        PayloadTypeRegistry.serverboundPlay().register(CONTROL,
            codec(CONTROL, 16));
        PayloadTypeRegistry.serverboundPlay().register(RADAR_SWITCH,
            codec(RADAR_SWITCH, 4));
        PayloadTypeRegistry.serverboundPlay().register(MISSILE_SWITCH,
            codec(MISSILE_SWITCH, 0));
        PayloadTypeRegistry.serverboundPlay().register(MISSILE_WARMUP,
            codec(MISSILE_WARMUP, 3 + 3 * MISSILE_WARMUP_ROUND_CHARS));
        PayloadTypeRegistry.serverboundPlay().register(CRASH,
            codec(CRASH, 0));
        PayloadTypeRegistry.serverboundPlay().register(SPEED_BRAKE,
            codec(SPEED_BRAKE, 4));
        PayloadTypeRegistry.serverboundPlay().register(WHEEL_BRAKE,
            codec(WHEEL_BRAKE, 4));
        PayloadTypeRegistry.serverboundPlay().register(FUEL_DUMP,
            codec(FUEL_DUMP, 4));
        PayloadTypeRegistry.serverboundPlay().register(AOA_LIMITER,
            codec(AOA_LIMITER, 4));
        PayloadTypeRegistry.serverboundPlay().register(GEAR,
            codec(GEAR, 4));
        PayloadTypeRegistry.serverboundPlay().register(FLAPS,
            codec(FLAPS, 4));
        PayloadTypeRegistry.serverboundPlay().register(TRIGGER,
            codec(TRIGGER, 4));
        PayloadTypeRegistry.serverboundPlay().register(BOMB,
            codec(BOMB, 0));
        PayloadTypeRegistry.serverboundPlay().register(RELEASE_CHAFF,
            codec(RELEASE_CHAFF, 0));
        PayloadTypeRegistry.serverboundPlay().register(RELEASE_FLARE,
            codec(RELEASE_FLARE, 0));
        // The size limit must cover the contents: the missile packet carries a UUID (16 bytes), and
        // a limit sized for an 8-byte network id once failed every launch and disconnected the
        // player.
        PayloadTypeRegistry.serverboundPlay().register(MISSILE,
            codec(MISSILE, 32));
        PayloadTypeRegistry.serverboundPlay().register(DISMOUNT,
            codec(DISMOUNT, 0));

        PayloadTypeRegistry.serverboundPlay().register(GPS_REQUEST,
            codec(GPS_REQUEST, 4));
        PayloadTypeRegistry.serverboundPlay().register(AI_DEBUG,
            codec(AI_DEBUG, 4));
        PayloadTypeRegistry.serverboundPlay().register(GPS_PIN,
            codec(GPS_PIN, 256));
        PayloadTypeRegistry.clientboundPlay().register(GPS_CONTACTS,
            codec(GPS_CONTACTS, 64 * 1024));

        PayloadTypeRegistry.clientboundPlay().register(RADAR_CONTACTS,
            codec(RADAR_CONTACTS, 32 * 1024));
        PayloadTypeRegistry.clientboundPlay().register(MISSILE_THREATS,
            codec(MISSILE_THREATS, 8 * 1024));
        PayloadTypeRegistry.clientboundPlay().register(BOUNDARY,
            codec(BOUNDARY, 16));
        PayloadTypeRegistry.clientboundPlay().register(MISSILE_TRACKS,
            codec(MISSILE_TRACKS, 4 * 1024));
        PayloadTypeRegistry.clientboundPlay().register(AIRCRAFT_TRACKS,
            codec(AIRCRAFT_TRACKS, 4 * 1024));
        PayloadTypeRegistry.clientboundPlay().register(MISSILE_SEEKER,
            codec(MISSILE_SEEKER, 4 * 1024));
        PayloadTypeRegistry.clientboundPlay().register(STRUCTURE_PREVIEW,
            codec(STRUCTURE_PREVIEW, 64));
        PayloadTypeRegistry.clientboundPlay().register(FLIGHT_MODEL_SYNC,
            codec(FLIGHT_MODEL_SYNC, 1024 * 1024));
        AiCommandNetworking.registerPayloadTypes();
    }

    public static void writeDocuments(FriendlyByteBuf buf,
                                      java.util.Map<String, String> documents) {
        buf.writeVarInt(documents.size());
        for (java.util.Map.Entry<String, String> document : documents.entrySet()) {
            buf.writeUtf(document.getKey(), 256);
            buf.writeUtf(document.getValue(), 512 * 1024);
        }
    }

    public static java.util.Map<String, String> readDocuments(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf(256);
            out.put(name, buf.readUtf(512 * 1024));
        }
        return out;
    }

    public static void broadcastFlightModel(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        var documents = amrac.physics.aircraft
            .FlightModelRegistry.instance().documentsForSync();
        for (var player : server.getPlayerList().getPlayers()) {
            FriendlyByteBuf buf =
                new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            writeDocuments(buf, documents);
            ServerPlayNetworking.send(player, payload(FLIGHT_MODEL_SYNC, buf));
        }
    }

    public static void sendFlightModel(net.minecraft.server.level.ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        writeDocuments(buf, amrac.physics.aircraft
            .FlightModelRegistry.instance().documentsForSync());
        ServerPlayNetworking.send(player, payload(FLIGHT_MODEL_SYNC, buf));
    }

    public static void registerServerReceivers() {
        registerPayloadTypes();
        AiCommandNetworking.registerServerReceivers();
        ServerPlayNetworking.registerGlobalReceiver(AI_DEBUG,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                server.execute(() -> {
                    if (!(player.level() instanceof net.minecraft.server.level
                            .ServerLevel level)) {
                        return;
                    }
                    long now = level.getGameTime();
                    if (amrac.entities.ai.AiFlightRecorder
                            .recording()) {
                        java.util.List<String> log = amrac
                            .entities.ai.AiFlightRecorder.stop();
                        for (String line : log) {
                            amrac.AmracMod.LOGGER
                                .info("[AI] {}", line);
                        }
                        for (String line : amrac.entities.ai
                                .AiDebugReport.lines(level, player)) {
                            amrac.AmracMod.LOGGER
                                .info("[AI] {}", line);
                        }
                        player.sendSystemMessage(net.minecraft.network.chat
                            .Component.translatable("amrac.message.ai_recording_stopped",
                                log.size()));
                    } else {
                        amrac.entities.ai.AiFlightRecorder
                            .start(now);
                        player.sendSystemMessage(net.minecraft.network.chat
                            .Component.translatable(
                                "amrac.message.ai_recording_started_hint"));
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(GPS_REQUEST,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean full = buf.isReadable() && buf.readBoolean();
                server.execute(() ->
                    amrac.gps.GpsService.serve(player, full));
            });
        ServerPlayNetworking.registerGlobalReceiver(GPS_PIN,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                int action = buf.readByte();
                java.util.UUID id = buf.readBoolean() ? buf.readUUID() : null;
                String name = buf.readUtf(64);
                double x = buf.readDouble();
                double z = buf.readDouble();
                server.execute(() -> {
                    switch (action) {
                        case PIN_ADD ->
                            amrac.gps.PinService
                                .add(player, name, x, z);
                        case PIN_REMOVE -> {
                            if (id != null) {
                                amrac.gps.PinService
                                    .remove(player, id);
                            }
                        }
                        case PIN_SHARE -> {
                            if (id != null) {
                                amrac.gps.PinService
                                    .toggleShare(player, id);
                            }
                        }
                        default -> { }
                    }
                    amrac.gps.GpsService.serve(player, true);
                });
            });
        ServerPlayNetworking.registerGlobalReceiver(ROTATION,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                float qx = buf.readFloat();
                float qy = buf.readFloat();
                float qz = buf.readFloat();
                float qw = buf.readFloat();
                double mx = buf.readDouble();
                double my = buf.readDouble();
                double mz = buf.readDouble();
                server.execute(() -> {
                    if (!(player.getVehicle() instanceof PlaneEntity plane) ||
                        plane.getControllingPassenger() != player) {
                        return;
                    }
                    Quaternionf attitude = new Quaternionf(qx, qy, qz, qw);
                    Vec3 motion = new Vec3(mx, my, mz);
                    if (!isValidAttitude(attitude) || !isFinite(motion)) {
                        return;
                    }
                    Quaternionf normalized =
                        MathUtil.normalizeQuaternion(attitude);
                    plane.setQ(normalized);
                    MathUtil.EulerAngles angles =
                        MathUtil.toEulerAngles(normalized);
                    plane.setYRot((float) angles.yaw);
                    plane.setXRot((float) angles.pitch);
                    plane.rotationRoll = (float) angles.roll;
                    plane.setQ_Client(normalized);
                    plane.setDeltaMovement(limitMotion(plane, motion));
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(CONTROL,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                int throttle = buf.readUnsignedByte();
                byte pitch = buf.readByte();
                byte yaw = buf.readByte();
                byte roll = buf.readByte();
                boolean groundReverse = buf.readBoolean();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.setControlInputs(throttle, fromAxis(pitch),
                            fromAxis(yaw), fromAxis(roll), groundReverse);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(RADAR_SWITCH,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean on = buf.readBoolean();
                server.execute(() ->
                    amrac.entities.PlaneRadarService
                        .setRadarEnabled(player, on));
            });

        ServerPlayNetworking.registerGlobalReceiver(MISSILE_SWITCH,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.cycleSelectedMissile();
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(CRASH,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.crash(Float.MAX_VALUE);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(SPEED_BRAKE,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean out = buf.readBoolean();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.setSpeedBrakeOut(out);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(WHEEL_BRAKE,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean on = buf.readBoolean();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.setWheelBrakeOn(on);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(FUEL_DUMP,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean open = buf.readBoolean();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.setDumpingFuel(open);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(AOA_LIMITER,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean enabled = buf.readBoolean();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.setAngleOfAttackLimiterEnabled(enabled);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(GEAR,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean down = buf.readBoolean();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.setGearDown(down);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(FLAPS,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean down = buf.readBoolean();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.setFlapsDown(down);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(TRIGGER,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                boolean firing = buf.readBoolean();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane) {
                        plane.setMachineGunTrigger(firing, player);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(RELEASE_CHAFF,
            (payload, context) -> {
                var player = context.player();
                context.server().execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane) {
                        plane.releaseCountermeasure(player,
                            amrac.weapons.CountermeasureService.Kind.CHAFF);
                    }
                });
            });
        ServerPlayNetworking.registerGlobalReceiver(RELEASE_FLARE,
            (payload, context) -> {
                var player = context.player();
                context.server().execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane) {
                        plane.releaseCountermeasure(player,
                            amrac.weapons.CountermeasureService.Kind.FLARE);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(BOMB,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane) {
                        plane.tryDropBombs(player);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(CONSOLE_ACTION,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                net.minecraft.core.BlockPos pos = buf.readBlockPos();
                int action = buf.readByte();
                String text = buf.readUtf(128);
                String value = buf.readUtf(256);
                double first = buf.readDouble();
                double second = buf.readDouble();
                amrac.display.AxisBounds bounds = new amrac.display.AxisBounds(buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
                server.execute(() ->
                    amrac.display.ConsoleService.handle(
                        player, pos, action, text, value, first, second,
                        bounds));
            });

        ServerPlayNetworking.registerGlobalReceiver(MISSILE_WARMUP,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                String round;
                try {
                    round = buf.isReadable()
                        ? buf.readUtf(MISSILE_WARMUP_ROUND_CHARS) : null;
                } catch (RuntimeException malformed) {
                    return;
                }
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity plane &&
                        plane.getControllingPassenger() == player) {
                        plane.missiles().beginWarmup(player, round);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(MISSILE,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                FriendlyByteBuf buf = payload.buffer();
                java.util.UUID targetId = buf.readUUID();
                server.execute(() -> {
                    if (!(player.getVehicle() instanceof PlaneEntity plane) ||
                        plane.getControllingPassenger() != player) {
                        return;
                    }
                    if (!(plane.level() instanceof net.minecraft.server.level.ServerLevel level)) {
                        return;
                    }
                    if (targetId.equals(plane.getUUID())) {
                        return;
                    }
                    amrac.entities.AircraftTargetSnapshot target =
                        amrac.entities.AircraftTargetSnapshot
                            .of(level, targetId);
                    if (target != null) {
                        plane.launchMissile(player, target);
                    } else {
                        amrac.AmracMod.sendOverlay(
                            player, net.minecraft.network.chat.Component
                                .translatable(
                                    "amrac.message.missile_no_target"),
                            true);
                    }
                });
            });

        ServerPlayNetworking.registerGlobalReceiver(DISMOUNT,
            (payload, context) -> {
                var server = context.server();
                var player = context.player();
                server.execute(() -> {
                    if (player.getVehicle() instanceof PlaneEntity) {
                        player.stopRiding();
                    }
                });
            });
    }

    private static boolean isValidAttitude(Quaternionf value) {
        if (!Float.isFinite(value.x()) || !Float.isFinite(value.y()) ||
            !Float.isFinite(value.z()) || !Float.isFinite(value.w())) {
            return false;
        }
        float lengthSqr = value.x() * value.x() + value.y() * value.y() +
            value.z() * value.z() + value.w() * value.w();
        return Float.isFinite(lengthSqr) && lengthSqr > 1.0E-6F;
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x()) && Double.isFinite(value.y()) &&
            Double.isFinite(value.z()) && Double.isFinite(value.lengthSqr());
    }

    private static Vec3 limitMotion(PlaneEntity plane, Vec3 value) {
        double maximum = plane.getMaximumPlausibleSpeed();
        double speedSqr = value.lengthSqr();
        return speedSqr > maximum * maximum
            ? value.scale(maximum / Math.sqrt(speedSqr))
            : value;
    }
}

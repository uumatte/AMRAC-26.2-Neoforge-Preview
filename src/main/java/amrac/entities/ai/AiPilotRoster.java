package amrac.entities.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AiPilotRoster extends SavedData {
    public record Entry(UUID pilotId, String callsign, int rank, int variant,
                        String team, Vec3 position, boolean missionActive,
                        Optional<UUID> aircraftId,
                        Optional<CompoundTag> saved) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                UUIDUtil.CODEC.fieldOf("pilot").forGetter(Entry::pilotId),
                Codec.STRING.fieldOf("callsign").forGetter(Entry::callsign),
                Codec.INT.fieldOf("rank").forGetter(Entry::rank),
                Codec.INT.fieldOf("variant").forGetter(Entry::variant),
                Codec.STRING.fieldOf("team").forGetter(Entry::team),
                Vec3.CODEC.fieldOf("position").forGetter(Entry::position),
                Codec.BOOL.fieldOf("mission").forGetter(Entry::missionActive),
                UUIDUtil.CODEC.optionalFieldOf("aircraft")
                    .forGetter(Entry::aircraftId),
                CompoundTag.CODEC.optionalFieldOf("saved")
                    .forGetter(Entry::saved)
            ).apply(i, Entry::new));

        public AiPilotRank rankValue() {
            return AiPilotRank.byOrdinal(rank);
        }
    }

    public record Parked(UUID aircraftId, Identifier type, Vec3 position,
                         float yaw, String flightModelId, boolean armed,
                         CompoundTag saved) {
        public static final Codec<Parked> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                UUIDUtil.CODEC.fieldOf("aircraft").forGetter(Parked::aircraftId),
                Identifier.CODEC.fieldOf("type").forGetter(Parked::type),
                Vec3.CODEC.fieldOf("position").forGetter(Parked::position),
                Codec.FLOAT.fieldOf("yaw").forGetter(Parked::yaw),
                Codec.STRING.fieldOf("model").forGetter(Parked::flightModelId),
                Codec.BOOL.optionalFieldOf("armed", false).forGetter(Parked::armed),
                CompoundTag.CODEC.fieldOf("saved").forGetter(Parked::saved)
            ).apply(i, Parked::new));
    }

    public static final Codec<AiPilotRoster> CODEC = RecordCodecBuilder.create(
        i -> i.group(
            Entry.CODEC.listOf().optionalFieldOf("pilots", List.of())
                .forGetter(AiPilotRoster::pilotList),
            Parked.CODEC.listOf().optionalFieldOf("parked", List.of())
                .forGetter(AiPilotRoster::parkedList),
            UUIDUtil.CODEC.listOf().optionalFieldOf("retired", List.of())
                .forGetter(AiPilotRoster::retiredList)
        ).apply(i, AiPilotRoster::new));

    public static final SavedDataType<AiPilotRoster> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("amrac", "ai_roster"),
        AiPilotRoster::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, Entry> pilots = new LinkedHashMap<>();
    private final Map<UUID, Parked> parked = new LinkedHashMap<>();
    private final java.util.Set<UUID> retired = new java.util.LinkedHashSet<>();

    public AiPilotRoster() {
    }

    private AiPilotRoster(List<Entry> pilots, List<Parked> parked,
                          List<UUID> retired) {
        for (Entry entry : pilots) {
            this.pilots.put(entry.pilotId(), entry);
        }
        for (Parked aircraft : parked) {
            this.parked.put(aircraft.aircraftId(), aircraft);
        }
        this.retired.addAll(retired);
    }

    public static AiPilotRoster of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    private List<Entry> pilotList() {
        return new ArrayList<>(pilots.values());
    }

    private List<Parked> parkedList() {
        return new ArrayList<>(parked.values());
    }

    private List<UUID> retiredList() {
        return new ArrayList<>(retired);
    }

    public void retire(UUID id) {
        if (id != null && retired.add(id)) {
            setDirty();
        }
    }

    public boolean claimRetired(UUID id) {
        if (retired.remove(id)) {
            setDirty();
            return true;
        }
        return false;
    }

    public void remember(Entry entry) {
        pilots.put(entry.pilotId(), entry);
        setDirty();
    }

    @Nullable
    public Entry pilot(UUID id) {
        return pilots.get(id);
    }

    public List<Entry> pilots() {
        return new ArrayList<>(pilots.values());
    }

    public void forgetPilot(UUID id) {
        if (pilots.remove(id) != null) {
            setDirty();
        }
    }

    public List<Entry> pilotsNear(Vec3 centre, double radius) {
        double radiusSquared = radius * radius;
        List<Entry> found = new ArrayList<>();
        for (Entry entry : pilots.values()) {
            if (entry.position().distanceToSqr(centre) <= radiusSquared) {
                found.add(entry);
            }
        }
        return found;
    }

    public void park(Parked aircraft) {
        parked.put(aircraft.aircraftId(), aircraft);
        setDirty();
    }

    public void unpark(UUID id) {
        if (parked.remove(id) != null) {
            setDirty();
        }
    }

    @Nullable
    public Parked parked(UUID id) {
        return parked.get(id);
    }

    public List<Parked> parked() {
        return new ArrayList<>(parked.values());
    }
}

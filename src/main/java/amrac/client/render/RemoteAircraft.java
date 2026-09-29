package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import amrac.platform.client.ClientTickEvents;
import amrac.platform.client.LevelExtractionEvents;
import amrac.platform.client.LevelRenderContext;
import amrac.platform.client.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import amrac.AmracMod;
import amrac.client.render.models.MeshAirframeModel;
import amrac.network.PlaneNetworking;

public final class RemoteAircraft {
    private static final int STALE_TICKS = 15;

    private static final int CONTACT_LIGHT = 0x00F000F0;

    private static final float MODEL_SCALE = 4.0F;

    private static final Map<UUID, Contact> CONTACTS = new LinkedHashMap<>();

    /**
     * A distant image yields to what PlaneMeshRenderer actually extracted (already culled by
     * vanilla and Sodium), not to whether the client has the entity. Both sides skip the Iris
     * shadow pass (IrisCompat), or images cast wrong shadows or count as visible.
     */
    private static final Set<UUID> DRAWN = new HashSet<>();

    private static final Set<UUID> EXTRACTING = new HashSet<>();

    private static volatile Vec3 eye = Vec3.ZERO;

    private RemoteAircraft() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                clear();
                return;
            }
            if (client.isPaused()) {
                return;
            }
            age();
        });
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            eye = context.camera().position();
            DRAWN.clear();
            DRAWN.addAll(EXTRACTING);
            EXTRACTING.clear();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (!CONTACTS.isEmpty() && !IrisCompat.renderingShadowPass()) {
                render(context);
            }
        });
    }

    static void noteDrawn(UUID id) {
        EXTRACTING.add(id);
    }

    public static void accept(List<PlaneNetworking.AircraftTrack> contacts) {
        for (PlaneNetworking.AircraftTrack track : contacts) {
            Vec3 position = new Vec3(track.x(), track.y(), track.z());
            Quaternionf attitude = new Quaternionf(track.qx(), track.qy(),
                track.qz(), track.qw());
            Contact contact = CONTACTS.get(track.id());
            if (contact == null) {
                CONTACTS.put(track.id(), new Contact(track.typeId(), position,
                    attitude, track.gearDown()));
            } else {
                contact.update(track.typeId(), position, attitude,
                    track.gearDown());
            }
        }
    }

    public static void clear() {
        CONTACTS.clear();
        DRAWN.clear();
        EXTRACTING.clear();
    }

    public static int count() {
        return CONTACTS.size();
    }

    private static void age() {
        CONTACTS.values().removeIf(contact -> ++contact.age > STALE_TICKS);
    }

    private static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        float partial = minecraft.getDeltaTracker()
            .getGameTimeDeltaPartialTick(false);
        Vec3 camera = eye;
        double radius = FarImagePolicy.drawRadius(
            minecraft.options.getEffectiveRenderDistance());
        PoseStack poseStack = context.poseStack();
        for (Map.Entry<UUID, Contact> entry : CONTACTS.entrySet()) {
            if (DRAWN.contains(entry.getKey())) {
                continue;
            }
            Contact contact = entry.getValue();
            Airframe airframe = airframeFor(contact.typeId);
            if (airframe == null) {
                continue;
            }
            MeshAirframeModel model = airframe.model();
            Vec3 offset = contact.position(partial).subtract(camera);
            double distance = offset.length();
            float pull = (float) FarImagePolicy.pull(distance, radius);
            int detail = LodPolicy.level(distance, false);
            float gear = contact.gearDown ? 1.0F : 0.0F;
            poseStack.pushPose();
            poseStack.translate(offset.x * pull, offset.y * pull,
                offset.z * pull);
            PlaneMeshRenderer.applyModelPose(poseStack, MODEL_SCALE * pull,
                contact.attitude(partial), false);
            context.submitNodeCollector().submitCustomGeometry(poseStack,
                model.renderType(airframe.texture),
                (rootPose, builder) -> {
                    model.setDetail(detail);
                    model.setGearPosition(gear);
                    model.setControlPose(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
                    model.renderToBuffer(PlaneMeshRenderer.poseFrom(rootPose),
                        builder, CONTACT_LIGHT, OverlayTexture.NO_OVERLAY,
                        1.0F, 1.0F, 1.0F, 1.0F);
                });
            poseStack.popPose();
        }
    }

    private static final class Contact {
        private int typeId;
        private Vec3 previous;
        private Vec3 current;
        private final Quaternionf previousAttitude = new Quaternionf();
        private final Quaternionf attitude = new Quaternionf();
        private final Quaternionf interpolated = new Quaternionf();
        private boolean gearDown;
        private int age;

        Contact(int typeId, Vec3 position, Quaternionf attitude,
                boolean gearDown) {
            this.typeId = typeId;
            this.previous = position;
            this.current = position;
            this.previousAttitude.set(attitude);
            this.attitude.set(attitude);
            this.gearDown = gearDown;
        }

        void update(int typeId, Vec3 position, Quaternionf attitude,
                    boolean gearDown) {
            this.typeId = typeId;
            this.previous = this.current;
            this.current = position;
            this.previousAttitude.set(this.attitude);
            this.attitude.set(attitude);
            this.gearDown = gearDown;
            this.age = 0;
        }

        Vec3 position(float partial) {
            return previous.lerp(current, partial);
        }

        Quaternionf attitude(float partial) {
            return previousAttitude.slerp(attitude, partial, interpolated);
        }
    }

    private static final class Airframe {
        private final Supplier<MeshAirframeModel> supplier;
        private final Identifier texture;
        @Nullable
        private MeshAirframeModel made;

        Airframe(Supplier<MeshAirframeModel> supplier, Identifier texture) {
            this.supplier = supplier;
            this.texture = texture;
        }

        MeshAirframeModel model() {
            if (made == null) {
                made = supplier.get();
            }
            return made;
        }
    }

    private static final Map<String, Airframe> AIRFRAMES = new HashMap<>();
    private static final Map<Integer, Airframe> BY_TYPE_ID = new HashMap<>();

    private static void airframe(String name, Supplier<MeshAirframeModel> model,
                                 Identifier texture) {
        AIRFRAMES.put(name, new Airframe(model, texture));
    }

    static {
        airframe("f16", amrac.client.render.models.F16bModel::new,
            F16Renderer.TEXTURE);
        airframe("f15", amrac.client.render.models.F15Model::new,
            F15Renderer.TEXTURE);
        airframe("f15e", amrac.client.render.models.F15EModel::new,
            F15ERenderer.TEXTURE);
        airframe("f18", amrac.client.render.models.F18Model::new,
            F18Renderer.TEXTURE);
        airframe("f4j", amrac.client.render.models.F4JModel::new,
            F4JRenderer.TEXTURE);
        airframe("su27", amrac.client.render.models.Su27Model::new,
            Su27Renderer.TEXTURE);
        airframe("su30", amrac.client.render.models.Su30Model::new,
            Su30Renderer.TEXTURE);
        airframe("mig21", amrac.client.render.models.Mig21Model::new,
            Mig21Renderer.TEXTURE);
        airframe("mig23", amrac.client.render.models.Mig23Model::new,
            Mig23Renderer.TEXTURE);
        airframe("mig29", amrac.client.render.models.Mig29Model::new,
            Mig29Renderer.TEXTURE);
        airframe("j10c", amrac.client.render.models.J10cModel::new,
            J10cRenderer.TEXTURE);
        airframe("j8ii", amrac.client.render.models.J8IIModel::new,
            J8IIRenderer.TEXTURE);
        airframe("rafale", amrac.client.render.models.RafaleModel::new,
            RafaleRenderer.TEXTURE);
        airframe("typhoon", amrac.client.render.models.TyphoonModel::new,
            TyphoonRenderer.TEXTURE);
    }

    @Nullable
    private static Airframe airframeFor(int typeId) {
        Airframe known = BY_TYPE_ID.get(typeId);
        if (known != null) {
            return known;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.byId(typeId);
        if (type == null) {
            return null;
        }
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null || !AmracMod.MODID.equals(id.getNamespace())) {
            return null;
        }
        Airframe airframe = AIRFRAMES.get(id.getPath());
        if (airframe != null) {
            BY_TYPE_ID.put(typeId, airframe);
        }
        return airframe;
    }
}

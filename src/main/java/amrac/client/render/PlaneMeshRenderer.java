package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import amrac.MathUtil;
import amrac.client.render.models.PlaneMeshModel;
import amrac.entities.AfterburnerPolicy;
import amrac.entities.F16Entity;

public abstract class PlaneMeshRenderer<T extends F16Entity>
    extends EntityRenderer<T, PlaneRenderState> {
    private static final double ENTITY_RENDER_Y_OFFSET = 0.375D;
    private static final double MODEL_ORIGIN_Y_OFFSET = -0.5D;
    private static final double FIRST_PERSON_PRE_ROTATION_Y_OFFSET = -0.7D;
    private static final double MODEL_POST_ROTATION_Y_OFFSET =
        -0.6D - amrac.entities.AirframeFrame.GROUND_LIFT;
    private static final int PLUME_RADIAL_SEGMENTS = 12;
    private static final int PLUME_LENGTH_STAGES = 12;
    private static final double SHOCK_DIAMOND_CYCLES = 4.0D;
    private static final float SHOCK_DIAMOND_RADIUS_MODULATION = 0.20F;
    private static final float CORE_RADIUS_FRACTION = 0.55F;
    private static final float FIRST_PERSON_PLUME_ALPHA = 0.55F;

    private final PlaneMeshModel model;
    private final Identifier texture;
    private final float[][] pylonStations;
    private final float nozzleY;
    private final float nozzleZ;
    private final float nozzleOffsetX;
    private final float nozzleRadius;
    private final float dryPlumeLength;
    private final float wetPlumeLength;

    protected PlaneMeshRenderer(EntityRendererProvider.Context context,
                                PlaneMeshModel model, Identifier texture,
                                float shadowRadius, float[][] pylonStations,
                                float nozzleY, float nozzleZ,
                                float nozzleOffsetX, float nozzleRadius,
                                float dryPlumeLength, float wetPlumeLength) {
        super(context);
        this.model = model;
        this.texture = texture;
        this.shadowRadius = shadowRadius;
        this.pylonStations = pylonStations;
        this.nozzleY = nozzleY;
        this.nozzleZ = nozzleZ;
        this.nozzleOffsetX = nozzleOffsetX;
        this.nozzleRadius = nozzleRadius;
        this.dryPlumeLength = dryPlumeLength;
        this.wetPlumeLength = wetPlumeLength;
    }

    @Override
    public PlaneRenderState createRenderState() {
        return new PlaneRenderState();
    }

    @Override
    public void extractRenderState(T plane, PlaneRenderState state,
                                   float partialTicks) {
        super.extractRenderState(plane, state, partialTicks);
        Quaternionf attitude = MathUtil.lerpQ(partialTicks, plane.getQ_Prev(),
            plane.getQ_Client());
        state.attitude.set(attitude);
        double rawScale = plane.getModelScale();
        state.modelScale = Double.isFinite(rawScale) && rawScale > 0.0D
            ? (float) rawScale : 1.0F;
        state.gearPosition = plane.getGearPosition(partialTicks);
        float[] pose = plane.getControlSurfacePose(partialTicks);
        state.controlPitch = pose[0];
        state.controlRoll = pose[1];
        state.controlYaw = pose[2];
        state.flapPosition = plane.getFlapPosition(partialTicks);
        state.speedBrakePosition = plane.getSpeedBrakePosition(partialTicks);
        state.afterburnerSpool = plane.getAfterburnerSpool(partialTicks);
        state.enginePower = plane.getEnginePower();
        state.engineRunning = plane.isEngineRunning();
        String[] loadout = plane.getLoadout();
        state.loadout = loadout == null ? new String[0] : loadout.clone();

        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = minecraft.options.getCameraType() ==
            CameraType.FIRST_PERSON;
        state.firstPersonRide = firstPerson && minecraft.player != null &&
            plane.hasPassenger(minecraft.player);
        state.firstPersonPilot = firstPerson && minecraft.player != null &&
            plane.getControllingPassenger() == minecraft.player;

        if (!IrisCompat.renderingShadowPass()) {
            RemoteAircraft.noteDrawn(plane.getUUID());
        }
    }

    @Override
    public Vec3 getRenderOffset(PlaneRenderState state) {
        return state.firstPersonPilot ? Vec3.ZERO : super.getRenderOffset(state);
    }

    @Override
    public void submit(PlaneRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        pullInside(state, poseStack, camera);
        applyModelPose(poseStack, state.modelScale, state.attitude,
            state.firstPersonRide);

        float gear = state.gearPosition;
        float pitch = state.controlPitch;
        float roll = state.controlRoll;
        float yaw = state.controlYaw;
        float flap = state.flapPosition;
        float brake = state.speedBrakePosition;
        int light = state.lightCoords;
        int detail = detailFor(state);
        collector.submitCustomGeometry(poseStack, model.renderType(texture),
            (rootPose, buffer) -> {
                PoseStack geometryPose = poseFrom(rootPose);
                model.setDetail(detail);
                model.setGearPosition(gear);
                model.setControlPose(pitch, roll, yaw, flap, brake);
                model.renderToBuffer(geometryPose, buffer, light,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            });

        if (LodPolicy.drawsStores(detail)) {
            PylonMissiles.submit(poseStack, collector, light, state.loadout,
                pylonStations);
        }
        submitExhaust(state, poseStack, collector);

        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void pullInside(PlaneRenderState state, PoseStack poseStack,
                                   CameraRenderState camera) {
        if (IrisCompat.renderingShadowPass()) {
            return;
        }
        double dx = state.x - camera.pos.x;
        double dy = state.y - camera.pos.y;
        double dz = state.z - camera.pos.z;
        double k = FarImagePolicy.pull(Math.sqrt(dx * dx + dy * dy + dz * dz),
            FarImagePolicy.drawRadius(
                Minecraft.getInstance().options.getEffectiveRenderDistance()));
        if (k >= 1.0D) {
            return;
        }
        poseStack.translate(dx * (k - 1.0D), dy * (k - 1.0D), dz * (k - 1.0D));
        poseStack.scale((float) k, (float) k, (float) k);
    }

    static int detailFor(PlaneRenderState state) {
        Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
        double dx = state.x - eye.x;
        double dy = state.y - eye.y;
        double dz = state.z - eye.z;
        return LodPolicy.level(Math.sqrt(dx * dx + dy * dy + dz * dz),
            IrisCompat.renderingShadowPass());
    }

    private void submitExhaust(PlaneRenderState state, PoseStack poseStack,
                               SubmitNodeCollector collector) {
        float spool = state.afterburnerSpool;
        if (!state.engineRunning && !AfterburnerPolicy.isEffective(spool)) {
            return;
        }
        float intensity = Mth.clamp(state.enginePower * 0.30F + spool * 0.90F,
            0.0F, 1.0F);
        if (intensity <= 1.0E-3F) {
            return;
        }
        float alpha = state.firstPersonRide
            ? intensity * FIRST_PERSON_PLUME_ALPHA : intensity;
        float length = Mth.lerp(spool, dryPlumeLength, wetPlumeLength);
        length *= 1.0F + 0.05F * Mth.sin(state.ageInTicks * 1.7F);
        float capturedLength = length;
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(),
            (rootPose, buffer) -> {
                Matrix4f matrix = rootPose.pose();
                if (Math.abs(nozzleOffsetX) < 1.0E-6F) {
                    renderPlume(buffer, matrix, 0.0F, capturedLength, spool,
                        alpha);
                } else {
                    renderPlume(buffer, matrix, -nozzleOffsetX, capturedLength,
                        spool, alpha);
                    renderPlume(buffer, matrix, nozzleOffsetX, capturedLength,
                        spool, alpha);
                }
            });
    }

    private void renderPlume(VertexConsumer buffer, Matrix4f matrix,
                             float centreX, float length, float spool,
                             float intensity) {
        renderPlumeCone(buffer, matrix, centreX, nozzleRadius, length, spool,
            intensity, false);
        renderPlumeCone(buffer, matrix, centreX,
            nozzleRadius * CORE_RADIUS_FRACTION, length * 0.70F, spool,
            intensity, true);
    }

    private void renderPlumeCone(VertexConsumer buffer, Matrix4f matrix,
                                 float centreX, float startRadius, float length,
                                 float spool, float intensity, boolean core) {
        if (length <= 1.0E-4F) {
            return;
        }
        float[][] rings = new float[PLUME_LENGTH_STAGES][];
        for (int stage = 0; stage < PLUME_LENGTH_STAGES; stage++) {
            rings[stage] = plumeRing(stage / (float) (PLUME_LENGTH_STAGES - 1),
                startRadius, length, spool, intensity, core);
        }
        for (int stage = 0; stage < PLUME_LENGTH_STAGES - 1; stage++) {
            float[] near = rings[stage];
            float[] far = rings[stage + 1];
            for (int segment = 0; segment < PLUME_RADIAL_SEGMENTS; segment++) {
                double a0 = Math.PI * 2.0D * segment / PLUME_RADIAL_SEGMENTS;
                double a1 = Math.PI * 2.0D * (segment + 1) /
                    PLUME_RADIAL_SEGMENTS;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                addPlumeVertex(buffer, matrix, centreX + c0 * near[0],
                    s0 * near[0], near);
                addPlumeVertex(buffer, matrix, centreX + c1 * near[0],
                    s1 * near[0], near);
                addPlumeVertex(buffer, matrix, centreX + c1 * far[0],
                    s1 * far[0], far);
                addPlumeVertex(buffer, matrix, centreX + c0 * far[0],
                    s0 * far[0], far);
            }
        }
    }

    private float[] plumeRing(float t, float startRadius, float length,
                              float spool, float intensity, boolean core) {
        double diamond = Math.sin(t * Math.PI * SHOCK_DIAMOND_CYCLES);
        float taper = 1.0F - 0.78F * t;
        float radius = startRadius * taper * (1.0F +
            SHOCK_DIAMOND_RADIUS_MODULATION * (float) diamond * spool);
        float red;
        float green;
        float blue;
        if (core) {
            float blueEnd = Mth.clamp(1.0F - t * 2.0F, 0.0F, 1.0F) * spool;
            red = 0.70F + 0.30F * t;
            green = 0.72F - 0.22F * t;
            blue = 0.55F + 0.45F * blueEnd;
        } else {
            red = 1.0F;
            green = Mth.clamp(0.78F - 0.55F * t, 0.10F, 1.0F);
            blue = Mth.clamp(0.42F - 0.42F * t, 0.02F, 1.0F) *
                (0.35F + 0.65F * spool);
        }
        float nodeBoost = 1.0F + 0.35F *
            (float) Math.max(0.0D, diamond) * spool;
        float fade = (1.0F - t) * (1.0F - t);
        float alpha = Mth.clamp(intensity * fade * nodeBoost *
            (core ? 0.85F : 0.55F), 0.0F, 1.0F);
        return new float[] {radius, nozzleZ + length * t, red, green, blue,
            alpha};
    }

    private void addPlumeVertex(VertexConsumer buffer, Matrix4f matrix,
                                float x, float y, float[] ring) {
        buffer.addVertex(matrix, x, y + nozzleY, ring[1])
            .setColor(ring[2], ring[3], ring[4], ring[5]);
    }

    static void applyModelPose(PoseStack poseStack, float modelScale,
                               org.joml.Quaternionfc attitude,
                               boolean firstPersonRide) {
        if (modelScale != 1.0F) {
            poseStack.scale(modelScale, modelScale, modelScale);
        }
        poseStack.translate(0.0D, ENTITY_RENDER_Y_OFFSET, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, MODEL_ORIGIN_Y_OFFSET, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        if (firstPersonRide) {
            poseStack.translate(0.0D, FIRST_PERSON_PRE_ROTATION_Y_OFFSET, 0.0D);
        }
        poseStack.mulPose(attitude);
        poseStack.translate(0.0D, MODEL_POST_ROTATION_Y_OFFSET, 0.0D);
        if (firstPersonRide) {
            poseStack.translate(0.0D, -FIRST_PERSON_PRE_ROTATION_Y_OFFSET, 0.0D);
        }
    }

    static PoseStack poseFrom(PoseStack.Pose rootPose) {
        PoseStack result = new PoseStack();
        result.last().set(rootPose);
        return result;
    }
}

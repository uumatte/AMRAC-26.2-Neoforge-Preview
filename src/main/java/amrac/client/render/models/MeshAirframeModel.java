package amrac.client.render.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loading negates X to undo the converter's Y mirror; normals, pivots, winding, retract angles and
 * pitch/flap/speed-brake angles flip with it, roll and yaw do not. Change the converter or the
 * loader and the other must follow.
 */
public class MeshAirframeModel implements PlaneMeshModel {
    private static final int MAGIC = 0x53553237;
    private static final int VERSION = 1;

    private static final int PITCH = 0;
    private static final int ROLL = 1;
    private static final int YAW = 2;

    private static final int[] MIRRORED_CORNERS = {0, 2, 1};
    private static final int FLAP = 3;
    private static final int BRAKE = 4;
    private static final int ROLL_RIGHT = 5;
    private static final int ROLL_LEFT = 6;

    private static final class Control {
        final String parentName;
        final float px, py, pz;
        final float ax, ay, az;
        final int[] channels;
        final float[] radians;
        Part parent;

        Control(String parentName, float px, float py, float pz,
                float ax, float ay, float az, int[] channels, float[] radians) {
            this.parentName = parentName;
            this.px = px / 16.0F;
            this.py = py / 16.0F;
            this.pz = pz / 16.0F;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.channels = channels;
            this.radians = radians;
        }
    }

    private static final class Part {
        final int start;
        final int count;
        final float px, py, pz;
        final float rx, ry, rz;
        final String name;
        final boolean moves;
        Control control;

        Part(String name, int start, int count, float px, float py, float pz,
             float rx, float ry, float rz) {
            this.name = name;
            this.start = start;
            this.count = count;
            this.px = px / 16.0F;
            this.py = py / 16.0F;
            this.pz = pz / 16.0F;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.moves = rx != 0.0F || ry != 0.0F || rz != 0.0F;
        }
    }

    private static final class Mesh {
        final float[] positions;
        final float[] normals;
        final float[] uvs;
        final Part[] parts;

        Mesh(float[] positions, float[] normals, float[] uvs, Part[] parts) {
            this.positions = positions;
            this.normals = normals;
            this.uvs = uvs;
            this.parts = parts;
        }
    }

    private static final Map<String, Mesh> CACHE = new ConcurrentHashMap<>();

    private final Mesh[] levels;
    private final String label;

    private int detail;

    private float gearPosition = 1.0F;

    private final float[] inputs = new float[7];

    protected MeshAirframeModel(String resource, String label) {
        this(resource, label, MAGIC);
    }

    protected MeshAirframeModel(String resource, String label, int expectedMagic) {
        this.label = label;
        this.levels = new Mesh[LEVELS];
        this.levels[0] = CACHE.computeIfAbsent(resource,
            path -> load(path, label, expectedMagic));
        for (int level = 1; level < LEVELS; level++) {
            String name = label + " level " + level;
            String reduced = resource.endsWith(".mesh")
                ? resource.substring(0, resource.length() - ".mesh".length())
                    + ".lod" + level + ".mesh"
                : null;
            this.levels[level] = reduced == null
                    || MeshAirframeModel.class.getResource(reduced) == null
                ? this.levels[level - 1]
                : CACHE.computeIfAbsent(reduced,
                    path -> load(path, name, expectedMagic));
        }
    }

    private static final int LEVELS = 4;

    private static Mesh load(String resource, String label, int expectedMagic) {
        try (InputStream in = MeshAirframeModel.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing " + resource);
            }
            DataInputStream data = new DataInputStream(
                new java.io.BufferedInputStream(in));
            int magic = data.readInt();
            int version = data.readInt();
            if (magic != expectedMagic || version != VERSION) {
                throw new IOException("not a " + label + " mesh: magic 0x"
                    + Integer.toHexString(magic) + " version " + version);
            }
            int partCount = data.readInt();
            Part[] parts = new Part[partCount];
            for (int i = 0; i < partCount; i++) {
                String name = data.readUTF();
                int start = data.readInt();
                int partTriangles = data.readInt();
                float px = data.readFloat(), py = data.readFloat(), pz = data.readFloat();
                float rx = data.readFloat(), ry = data.readFloat(), rz = data.readFloat();
                parts[i] = new Part(name, start, partTriangles, -px, py, pz, rx, -ry, -rz);
            }
            int count = data.readInt();
            float[] positions = new float[count * 9];
            float[] normals = new float[count * 9];
            float[] uvs = new float[count * 6];
            for (int t = 0; t < count; t++) {
                for (int corner : MIRRORED_CORNERS) {
                    positions[t * 9 + corner * 3] = -data.readFloat() / 16.0F;
                    positions[t * 9 + corner * 3 + 1] = data.readFloat() / 16.0F;
                    positions[t * 9 + corner * 3 + 2] = data.readFloat() / 16.0F;
                }
                for (int corner : MIRRORED_CORNERS) {
                    normals[t * 9 + corner * 3] = -data.readFloat();
                    normals[t * 9 + corner * 3 + 1] = data.readFloat();
                    normals[t * 9 + corner * 3 + 2] = data.readFloat();
                }
                for (int corner : MIRRORED_CORNERS) {
                    uvs[t * 6 + corner * 2] = data.readFloat();
                    uvs[t * 6 + corner * 2 + 1] = data.readFloat();
                }
            }
            readControls(data, parts);
            return new Mesh(positions, normals, uvs, parts);
        } catch (IOException e) {
            throw new IllegalStateException(
                "could not load the " + label + " mesh; the jar is incomplete", e);
        }
    }

    private static void readControls(DataInputStream data, Part[] parts)
        throws IOException {
        int controls;
        try {
            controls = data.readInt();
        } catch (EOFException end) {
            return;
        }
        Map<String, Part> byName = new HashMap<>();
        for (Part p : parts) {
            byName.put(p.name, p);
        }
        for (int i = 0; i < controls; i++) {
            String name = data.readUTF();
            String parent = data.readUTF();
            float px = data.readFloat();
            float py = data.readFloat();
            float pz = data.readFloat();
            float ax = data.readFloat();
            float ay = data.readFloat();
            float az = data.readFloat();
            int mixes = data.readInt();
            int[] channels = new int[mixes];
            float[] radians = new float[mixes];
            for (int m = 0; m < mixes; m++) {
                channels[m] = data.readInt();
                float angle = data.readFloat();
                radians[m] = channels[m] == ROLL || channels[m] == YAW
                    || channels[m] == ROLL_RIGHT || channels[m] == ROLL_LEFT ? angle : -angle;
            }
            Part part = byName.get(name);
            if (part == null) {
                throw new IOException("control surface " + name + " names no part");
            }
            part.control = new Control(parent, -px, py, pz, -ax, ay, az,
                channels, radians);
        }
        for (Part p : parts) {
            if (p.control != null && !p.control.parentName.isEmpty()) {
                p.control.parent = byName.get(p.control.parentName);
            }
        }
    }

    @Override
    public RenderType renderType(Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    @Override
    public void setDetail(int level) {
        this.detail = Mth.clamp(level, 0, LEVELS - 1);
    }

    @Override
    public void setGearPosition(float position) {
        this.gearPosition = Float.isFinite(position)
            ? Mth.clamp(position, 0.0F, 1.0F) : 1.0F;
    }

    @Override
    public void setControlPose(float pitchUp, float rollRight, float yawRight,
                               float flap, float brake) {
        inputs[PITCH] = axis(pitchUp);
        inputs[ROLL] = axis(rollRight);
        inputs[ROLL_RIGHT] = Math.max(0.0F, inputs[ROLL]);
        inputs[ROLL_LEFT] = Math.max(0.0F, -inputs[ROLL]);
        inputs[YAW] = axis(yawRight);
        inputs[FLAP] = travel(flap);
        inputs[BRAKE] = travel(brake);
    }

    private static float axis(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, -1.0F, 1.0F) : 0.0F;
    }

    private static float travel(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0F, 1.0F) : 0.0F;
    }

    private float deflection(Control control) {
        float angle = 0.0F;
        for (int i = 0; i < control.channels.length; i++) {
            int channel = control.channels[i];
            if (channel >= 0 && channel < inputs.length) {
                angle += control.radians[i] * inputs[channel];
            }
        }
        return angle;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue,
                               float alpha) {
        float stowed = 1.0F - gearPosition;
        Mesh mesh = levels[detail];
        for (Part part : mesh.parts) {
            if (part.count == 0) {
                continue;
            }
            if (part.moves && part.name.startsWith("gear") && stowed >= 1.0F - 1.0E-4F) {
                continue;
            }
            boolean rotate = part.moves && stowed > 1.0E-4F;
            Control control = part.control;
            boolean carried = control != null && control.parent != null
                && control.parent.moves && stowed > 1.0E-4F;
            float angle = control == null ? 0.0F : deflection(control);
            boolean swing = control != null && Math.abs(angle) > 1.0E-5F;
            boolean posed = rotate || carried || swing;
            if (posed) {
                poseStack.pushPose();
                if (rotate) {
                    stow(poseStack, part, stowed);
                }
                if (carried) {
                    stow(poseStack, control.parent, stowed);
                }
                if (swing) {
                    poseStack.translate(control.px, control.py, control.pz);
                    poseStack.mulPose(new Quaternionf().rotationAxis(angle,
                        control.ax, control.ay, control.az));
                    poseStack.translate(-control.px, -control.py, -control.pz);
                }
            }
            emit(mesh, poseStack, buffer, part.start, part.count, packedLight,
                packedOverlay, red, green, blue, alpha);
            if (posed) {
                poseStack.popPose();
            }
        }
    }

    private static void stow(PoseStack poseStack, Part part, float stowed) {
        poseStack.translate(part.px, part.py, part.pz);
        if (part.rz != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotation(part.rz * stowed));
        }
        if (part.ry != 0.0F) {
            poseStack.mulPose(Axis.YP.rotation(part.ry * stowed));
        }
        if (part.rx != 0.0F) {
            poseStack.mulPose(Axis.XP.rotation(part.rx * stowed));
        }
        poseStack.translate(-part.px, -part.py, -part.pz);
    }

    private static void emit(Mesh mesh, PoseStack poseStack,
                             VertexConsumer buffer, int from,
                             int howMany, int packedLight, int packedOverlay,
                             float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        float[] positions = mesh.positions;
        float[] normals = mesh.normals;
        float[] uvs = mesh.uvs;

        for (int t = from; t < from + howMany; t++) {
            int p = t * 9;
            int u = t * 6;

            for (int corner = 0; corner < 4; corner++) {
                int c = corner == 3 ? 2 : corner;
                buffer.addVertex(matrix, positions[p + c * 3],
                        positions[p + c * 3 + 1], positions[p + c * 3 + 2])
                    .setColor(red, green, blue, alpha)
                    .setUv(uvs[u + c * 2], uvs[u + c * 2 + 1])
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, normals[p + c * 3],
                        normals[p + c * 3 + 1], normals[p + c * 3 + 2]);
            }
        }
    }
}

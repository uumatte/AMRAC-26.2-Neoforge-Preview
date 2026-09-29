package amrac.client.render.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Linked by the BVR Replay mod: keep this class's name, package and public members, or replays
 * crash with NoClassDefFoundError.
 */
public class MissileModel {
    public static final String SPARROW =
        "/assets/amrac/models/missile.mesh";
    public static final String AIM120 =
        "/assets/amrac/models/aim120.mesh";
    public static final String AIM9 =
        "/assets/amrac/models/aim9.mesh";
    public static final String R27 =
        "/assets/amrac/models/r27.mesh";
    public static final String R73 =
        "/assets/amrac/models/r73.mesh";
    public static final String R77 =
        "/assets/amrac/models/r77.mesh";
    public static final String PL8 =
        "/assets/amrac/models/pl8.mesh";
    public static final String PL10 =
        "/assets/amrac/models/pl10.mesh";
    public static final String PL12 =
        "/assets/amrac/models/pl12.mesh";
    public static final String MICA =
        "/assets/amrac/models/mica.mesh";
    public static final String METEOR =
        "/assets/amrac/models/meteor.mesh";
    public static final String PL15 =
        "/assets/amrac/models/pl15.mesh";

    private static final int MAGIC = 0x4d534c31;
    private static final int VERSION = 1;

    private static final class Part {
        final String name;
        final int start;
        final int count;
        final float px, py, pz;
        final float rx, ry, rz;
        final boolean moves;

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

    private record Geometry(
        float[] positions,
        float[] normals,
        float[] uvs,
        int triangles,
        Part[] parts) {
    }

    private static final java.util.Map<String, Geometry> CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    private final Geometry geometry;

    public MissileModel() {
        this(SPARROW);
    }

    public MissileModel(String meshResource) {
        this.geometry = CACHE.computeIfAbsent(meshResource, MissileModel::load);
    }

    private static Geometry load(String meshResource) {
        float[] positions;
        float[] normals;
        float[] uvs;
        Part[] parts;
        int count;
        try (InputStream in = MissileModel.class.getResourceAsStream(meshResource)) {
            if (in == null) {
                throw new IOException("missing " + meshResource);
            }
            DataInputStream data = new DataInputStream(
                new java.io.BufferedInputStream(in));
            int magic = data.readInt();
            int version = data.readInt();
            if (magic != MAGIC || version != VERSION) {
                throw new IOException(meshResource + " is not a missile "
                    + "mesh: magic 0x"
                    + Integer.toHexString(magic) + " version " + version);
            }
            int partCount = data.readInt();
            parts = new Part[partCount];
            for (int i = 0; i < partCount; i++) {
                String name = data.readUTF();
                int start = data.readInt();
                int partTriangles = data.readInt();
                float px = data.readFloat(), py = data.readFloat(), pz = data.readFloat();
                float rx = data.readFloat(), ry = data.readFloat(), rz = data.readFloat();
                parts[i] = new Part(name, start, partTriangles, -px, py, pz, rx, -ry, -rz);
            }
            count = data.readInt();
            positions = new float[count * 9];
            normals = new float[count * 9];
            uvs = new float[count * 6];
            int[] corners = {0, 2, 1};
            for (int t = 0; t < count; t++) {
                for (int corner : corners) {
                    positions[t * 9 + corner * 3] = -data.readFloat() / 16.0F;
                    positions[t * 9 + corner * 3 + 1] = data.readFloat() / 16.0F;
                    positions[t * 9 + corner * 3 + 2] = data.readFloat() / 16.0F;
                }
                for (int corner : corners) {
                    normals[t * 9 + corner * 3] = -data.readFloat();
                    normals[t * 9 + corner * 3 + 1] = data.readFloat();
                    normals[t * 9 + corner * 3 + 2] = data.readFloat();
                }
                for (int corner : corners) {
                    uvs[t * 6 + corner * 2] = data.readFloat();
                    uvs[t * 6 + corner * 2 + 1] = data.readFloat();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                "could not load the F-15 mesh; the jar is incomplete", e);
        }
        return new Geometry(positions, normals, uvs, count, parts);
    }

    public RenderType renderType(Identifier texture) {
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture);
    }

    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue,
                               float alpha) {
        emit(poseStack, buffer, 0, geometry.triangles(), packedLight, packedOverlay,
            red, green, blue, alpha);
    }

    private void emit(PoseStack poseStack, VertexConsumer buffer, int from,
                      int howMany, int packedLight, int packedOverlay,
                      float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        for (int t = from; t < from + howMany; t++) {
            int p = t * 9;
            int u = t * 6;

            for (int corner = 0; corner < 4; corner++) {
                int c = corner == 3 ? 2 : corner;
                buffer.addVertex(matrix, geometry.positions()[p + c * 3],
                        geometry.positions()[p + c * 3 + 1], geometry.positions()[p + c * 3 + 2])
                    .setColor(red, green, blue, alpha)
                    .setUv(geometry.uvs()[u + c * 2], geometry.uvs()[u + c * 2 + 1])
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, geometry.normals()[p + c * 3],
                        geometry.normals()[p + c * 3 + 1], geometry.normals()[p + c * 3 + 2])
                    ;
            }
        }
    }
}

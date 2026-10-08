package amrac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import amrac.blocks.ScreenBlock;
import amrac.blocks.ScreenBlockEntity;
import amrac.display.ChartData;
import amrac.display.DisplayPolicy;
import amrac.display.ChartPolicy;
import amrac.display.ScreenContent;

import java.util.Locale;

public final class PanelRenderer
        implements BlockEntityRenderer<ScreenBlockEntity, PanelRenderer.State> {
    private static final float PIXELS = 160.0F;
    private static final float PROUD = (float) DisplayPolicy.PANEL_PROUD;

    private static final float Z_BACKGROUND = 0.0F;
    private static final float Z_GRID = 0.002F;
    private static final float Z_AXIS = 0.004F;
    private static final float Z_INK = 0.006F;
    private static final float Z_TEXT = 0.008F;

    private static final int BACKGROUND = 0xFF0B0F14;
    private static final int GRID = 0xFF1E2A36;
    private static final int AXIS = 0xFF54646F;
    private static final int CURVE = 0xFF56D68A;
    private static final int LABEL = 0xFF9FB0BE;
    private static final int TITLE = 0xFFD8E0E8;

    private static final float LEFT = 26.0F;
    private static final float RIGHT = 6.0F;
    private static final float TOP = 16.0F;
    private static final float BOTTOM = 18.0F;

    private final Font font;

    public static final class State extends BlockEntityRenderState {
        boolean draw;
        Direction facing = Direction.NORTH;
        ScreenContent.Kind kind = ScreenContent.Kind.NONE;
        ChartData chart = ChartData.EMPTY;
        int[] map = new int[0];
        String title = "";
        java.util.List<ScreenContent.Blip> blips = java.util.List.of();
        double centreX;
        double centreZ;
        double span;
    }

    public PanelRenderer(BlockEntityRendererProvider.Context context) {
        font = context.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ScreenBlockEntity panel, State state,
                                   float partialTicks, Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(panel, state, partialTicks,
            cameraPos, crumbling);
        state.draw = false;
        if (!panel.isCore()) {
            return;
        }
        ScreenContent content = panel.content();
        if (content.kind() == ScreenContent.Kind.NONE) {
            return;
        }
        state.draw = true;
        state.facing = panel.getBlockState().getValue(ScreenBlock.FACING);
        state.kind = content.kind();
        state.chart = content.chart();
        state.map = content.map();
        state.title = content.title();
        state.blips = content.blips();
        state.centreX = content.mapCentreX();
        state.centreZ = content.mapCentreZ();
        state.span = content.mapSpan();
    }

    @Override
    public void submit(State state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.draw) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        Direction facing = state.facing;
        double out = DisplayPolicy.panelFaceOffset(ScreenBlock.THICKNESS);
        poseStack.translate(facing.getStepX() * out, facing.getStepY() * out,
            facing.getStepZ() * out);
        poseStack.mulPose(orientation(facing));
        poseStack.scale(5.0F / PIXELS, -5.0F / PIXELS, 1.0F);
        poseStack.translate(-PIXELS / 2.0F, -PIXELS / 2.0F, 0.0F);

        State snapshot = state;
        collector.submitCustomGeometry(poseStack, RenderTypes.textBackground(),
            (pose, buffer) -> surface(buffer, pose.pose(), snapshot));

        labels(state, poseStack, collector);
        poseStack.popPose();
    }

    private static org.joml.Quaternionf orientation(Direction facing) {
        return switch (facing) {
            case SOUTH -> Axis.YP.rotationDegrees(0.0F);
            case NORTH -> Axis.YP.rotationDegrees(180.0F);
            case EAST -> Axis.YP.rotationDegrees(90.0F);
            case WEST -> Axis.YP.rotationDegrees(270.0F);
            case UP -> Axis.XP.rotationDegrees(-90.0F);
            case DOWN -> Axis.XP.rotationDegrees(90.0F);
        };
    }

    private static void surface(VertexConsumer buffer, Matrix4f matrix,
                                State state) {
        int light = state.lightCoords;
        quad(buffer, matrix, 0.0F, 0.0F, PIXELS, PIXELS, Z_BACKGROUND,
            BACKGROUND, light);
        switch (state.kind) {
            case CHART -> chart(buffer, matrix, state.chart, light);
            case MAP -> map(buffer, matrix, state.map, light);
            case GPS -> dial(buffer, matrix, state, light);
            default -> {
            }
        }
    }

    private static void dial(VertexConsumer buffer, Matrix4f matrix,
                             State state, int light) {
        float centre = PIXELS / 2.0F;
        float radius = centre - 12.0F;
        double range = state.span / 2.0D;
        if (!(range > 0.0D)) {
            return;
        }

        ring(buffer, matrix, centre, centre, radius, GRID, light);
        ring(buffer, matrix, centre, centre, radius * 2.0F / 3.0F, GRID, light);
        ring(buffer, matrix, centre, centre, radius / 3.0F, GRID, light);
        segment(buffer, matrix, centre, centre - radius, centre,
            centre + radius, Z_GRID, GRID, light);
        segment(buffer, matrix, centre - radius, centre, centre + radius,
            centre, Z_GRID, GRID, light);

        quad(buffer, matrix, centre - 1.5F, centre - 1.5F, centre + 1.5F,
            centre + 1.5F, Z_AXIS, AXIS, light);

        for (ScreenContent.Blip blip : state.blips) {
            double dx = blip.x() - state.centreX;
            double dz = blip.z() - state.centreZ;
            if (Math.hypot(dx, dz) > range) {
                continue;
            }
            float px = centre + (float) (dx / range) * radius;
            float py = centre + (float) (dz / range) * radius;
            float size = blipSize(blip.kind());
            quad(buffer, matrix, px - size, py - size, px + size, py + size,
                Z_INK, blipColour(blip.kind()), light);
        }
    }

    private static void ring(VertexConsumer buffer, Matrix4f matrix,
                             float cx, float cy, float radius, int colour,
                             int light) {
        int steps = 48;
        float previousX = cx + radius;
        float previousY = cy;
        for (int i = 1; i <= steps; i++) {
            double angle = i * 2.0D * Math.PI / steps;
            float x = cx + radius * (float) Math.cos(angle);
            float y = cy + radius * (float) Math.sin(angle);
            segment(buffer, matrix, previousX, previousY, x, y, Z_GRID, colour,
                light);
            previousX = x;
            previousY = y;
        }
    }

    private static int blipColour(int kind) {
        return switch (kind) {
            case 0 -> 0xFF6FC8FF;
            case 1 -> 0xFFE0574F;
            case 2 -> 0xFF8A94A0;
            case amrac.display.ScreenContent.ACTIVE_MISSILE_BLIP
                -> 0xFF4ADE6A;
            default -> 0xFFF0C24A;
        };
    }

    private static float blipSize(int kind) {
        return kind >= 3 ? 1.2F : 2.0F;
    }

    private static void chart(VertexConsumer buffer, Matrix4f matrix,
                              ChartData data, int light) {
        if (data.isEmpty()) {
            return;
        }
        double[] x = new double[3];
        double[] y = new double[3];
        data.xAxis(x);
        data.yAxis(y);

        float plotLeft = LEFT;
        float plotRight = PIXELS - RIGHT;
        float plotTop = TOP;
        float plotBottom = PIXELS - BOTTOM;

        for (double at = x[0]; at <= x[1] + 1.0E-9D; at += x[2]) {
            float px = plotLeft + (float) (ChartPolicy.fraction(at, x[0], x[1])
                * (plotRight - plotLeft));
            quad(buffer, matrix, px, plotTop, px + 0.5F, plotBottom, Z_GRID,
                GRID, light);
        }
        for (double at = y[0]; at <= y[1] + 1.0E-9D; at += y[2]) {
            float py = plotBottom - (float) (ChartPolicy.fraction(at, y[0], y[1])
                * (plotBottom - plotTop));
            quad(buffer, matrix, plotLeft, py, plotRight, py + 0.5F, Z_GRID,
                GRID, light);
        }
        quad(buffer, matrix, plotLeft, plotTop, plotLeft + 0.8F, plotBottom,
            Z_AXIS, AXIS, light);
        quad(buffer, matrix, plotLeft, plotBottom - 0.8F, plotRight, plotBottom,
            Z_AXIS, AXIS, light);

        int count = data.size();
        float previousX = 0.0F;
        float previousY = 0.0F;
        boolean started = false;
        for (int i = 0; i < count; i++) {
            if (!Double.isFinite(data.xs()[i]) || !Double.isFinite(data.ys()[i])) {
                started = false;
                continue;
            }
            float px = plotLeft + (float) (ChartPolicy.fraction(data.xs()[i],
                x[0], x[1]) * (plotRight - plotLeft));
            float py = plotBottom - (float) (ChartPolicy.fraction(data.ys()[i],
                y[0], y[1]) * (plotBottom - plotTop));
            if (started) {
                double[] visible = new double[4];
                if (ChartPolicy.clipSegment(previousX, previousY, px, py,
                        plotLeft, plotTop, plotRight, plotBottom, visible)) {
                    segment(buffer, matrix, (float) visible[0],
                        (float) visible[1], (float) visible[2],
                        (float) visible[3], Z_INK, CURVE, light);
                }
            }
            previousX = px;
            previousY = py;
            started = true;
        }
    }

    private static void map(VertexConsumer buffer, Matrix4f matrix,
                            int[] squares, int light) {
        int resolution = ScreenContent.MAP_RESOLUTION;
        if (squares.length < resolution * resolution) {
            return;
        }
        float inset = 8.0F;
        float size = (PIXELS - inset * 2.0F) / resolution;
        for (int row = 0; row < resolution; row++) {
            for (int column = 0; column < resolution; column++) {
                int colour = squares[row * resolution + column];
                if (colour == 0) {
                    continue;
                }
                float px = inset + column * size;
                float py = inset + row * size;
                quad(buffer, matrix, px, py, px + size, py + size, Z_GRID,
                    0xFF000000 | colour, light);
            }
        }
    }

    private static void segment(VertexConsumer buffer, Matrix4f matrix,
                                float ax, float ay, float bx, float by,
                                float z, int colour, int light) {
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-4F) {
            return;
        }
        float nx = -dy / length * 0.7F;
        float ny = dx / length * 0.7F;
        vertex(buffer, matrix, ax + nx, ay + ny, z, colour, light);
        vertex(buffer, matrix, bx + nx, by + ny, z, colour, light);
        vertex(buffer, matrix, bx - nx, by - ny, z, colour, light);
        vertex(buffer, matrix, ax - nx, ay - ny, z, colour, light);
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix,
                             float x0, float y0, float x1, float y1,
                             float z, int colour, int light) {
        vertex(buffer, matrix, x0, y0, z, colour, light);
        vertex(buffer, matrix, x0, y1, z, colour, light);
        vertex(buffer, matrix, x1, y1, z, colour, light);
        vertex(buffer, matrix, x1, y0, z, colour, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, int colour,
                               int light) {
        buffer.addVertex(matrix, x, y, z).setColor(colour).setLight(light);
    }

    private void labels(State state, PoseStack poseStack,
                        SubmitNodeCollector collector) {
        int light = state.lightCoords;
        text(collector, poseStack, state.title, 6.0F, 4.0F, TITLE, light);
        if (state.kind != ScreenContent.Kind.CHART || state.chart.isEmpty()) {
            return;
        }
        ChartData data = state.chart;
        double[] x = new double[3];
        double[] y = new double[3];
        data.xAxis(x);
        data.yAxis(y);
        int decimalsX = ChartPolicy.labelDecimals(x[2]);
        int decimalsY = ChartPolicy.labelDecimals(y[2]);

        float plotLeft = LEFT;
        float plotRight = PIXELS - RIGHT;
        float plotTop = TOP;
        float plotBottom = PIXELS - BOTTOM;

        for (double at = y[0]; at <= y[1] + 1.0E-9D; at += y[2]) {
            float py = plotBottom - (float) (ChartPolicy.fraction(at, y[0], y[1])
                * (plotBottom - plotTop));
            text(collector, poseStack, format(at, decimalsY), 2.0F, py - 3.0F,
                LABEL, light);
        }
        for (double at = x[0]; at <= x[1] + 1.0E-9D; at += x[2]) {
            float px = plotLeft + (float) (ChartPolicy.fraction(at, x[0], x[1])
                * (plotRight - plotLeft));
            text(collector, poseStack, format(at, decimalsX), px - 6.0F,
                plotBottom + 2.0F, LABEL, light);
        }
        text(collector, poseStack, data.xLabel(), plotLeft, PIXELS - 8.0F,
            LABEL, light);
        text(collector, poseStack, data.note(), plotLeft, 10.0F, LABEL, light);
    }

    private void text(SubmitNodeCollector collector, PoseStack poseStack,
                      String message, float x, float y, int colour, int light) {
        if (message == null || message.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, y, Z_TEXT);
        poseStack.scale(0.34F, 0.34F, 0.34F);
        collector.submitText(poseStack, 0.0F, 0.0F,
            amrac.display.SyncedText.component(message).getVisualOrderText(), false,
            Font.DisplayMode.POLYGON_OFFSET, light, colour, 0, 0);
        poseStack.popPose();
    }

    private static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}

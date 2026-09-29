package amrac.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import amrac.client.GpsData;
import amrac.client.GpsRange;
import amrac.client.PlaneKeyBindings;
import amrac.client.PlaneRadar;
import amrac.entities.PlaneEntity;
import amrac.gps.GpsContact;
import amrac.gps.GpsPin;
import amrac.gps.GpsPolicy;
import amrac.gps.PinService;
import amrac.network.PlaneNetworking;

public final class GpsScreen extends Screen {
    public enum Mode {
        TACTICAL,
        AIRBORNE
    }

    private static final int RINGS = 4;
    private static final int PANEL_WIDTH = 190;

    private static final int BACKDROP = 0xE0080E12;
    private static final int PANEL = 0xC00D151A;
    private static final int EDGE = 0xFF3C4650;
    private static final int RING = 0x3AFFFFFF;
    private static final int RING_BRIGHT = 0x66FFFFFF;
    private static final int AXIS = 0x2AFFFFFF;
    private static final int OWN = 0xFFEAF6F1;
    private static final int LABEL = 0xFFB9C4CE;
    private static final int DIM = 0xFF717D89;
    private static final int PLAYER = 0xFF4FC3F7;
    private static final int AI = 0xFFE8503C;
    private static final int EMPTY = 0xFF8A96A4;
    private static final int MISSILE = 0xFFE8C23E;
    private static final int MISSILE_ACTIVE = 0xFF4ADE6A;
    private static final int PIN = 0xFF63D6A0;
    private static final int PIN_SHARED = 0xFFC792EA;
    private static final int ACTION = 0xFF8FA0AD;

    private static boolean open;

    private final Mode mode;

    private int centreX;
    private int centreY;
    private int radius;
    private double range;
    private double heading;
    private Vec3 own = Vec3.ZERO;

    private final List<PinRow> pinRows = new ArrayList<>();

    private int contactScroll;
    private int contactRowsVisible = 1;
    private int contactCount;

    private EditBox nameField;
    private Button confirmPin;
    private Button cancelPin;
    private boolean placing;
    private double pendingX;
    private double pendingZ;

    private record PinRow(GpsPin pin, int y, int shareX, int deleteX) { }

    public GpsScreen(Mode mode) {
        super(Component.translatable(mode == Mode.TACTICAL
            ? "amrac.gui.gps.title" : "amrac.gui.gps.airborne"));
        this.mode = mode;
    }

    public static boolean isOpen() {
        return open;
    }

    @Override
    public void added() {
        super.added();
        open = true;
    }

    @Override
    public void removed() {
        super.removed();
        open = false;
    }

    @Override
    protected void init() {
        super.init();
        int dialWidth = width - PANEL_WIDTH;
        centreX = dialWidth / 2;
        centreY = height / 2;
        radius = Math.max(50, Math.min(dialWidth, height) / 2 - 30);

        nameField = new EditBox(font, 12, height - 52, 150, 18,
            Component.translatable("amrac.gui.gps.pin_name"));
        nameField.setMaxLength(PinService.MAX_NAME_LENGTH);
        addRenderableWidget(nameField);

        confirmPin = Button.builder(
                Component.translatable("amrac.gui.gps.pin_add"),
                button -> commitPin())
            .bounds(168, height - 52, 48, 18).build();
        addRenderableWidget(confirmPin);

        cancelPin = Button.builder(
                Component.translatable("amrac.gui.gps.pin_cancel"),
                button -> stopPlacing())
            .bounds(220, height - 52, 52, 18).build();
        addRenderableWidget(cancelPin);

        stopPlacing();
        GpsData.requestNow(true);
    }

    private void stopPlacing() {
        placing = false;
        if (nameField != null) {
            nameField.setValue("");
            nameField.visible = false;
        }
        if (confirmPin != null) {
            confirmPin.visible = false;
        }
        if (cancelPin != null) {
            cancelPin.visible = false;
        }
    }

    private void startPlacing(double worldX, double worldZ) {
        placing = true;
        pendingX = worldX;
        pendingZ = worldZ;
        nameField.visible = true;
        nameField.setValue("");
        setFocused(nameField);
        nameField.setFocused(true);
        confirmPin.visible = true;
        cancelPin.visible = true;
    }

    private void commitPin() {
        if (!placing) {
            return;
        }
        PlaneNetworking.sendPinEdit(PlaneNetworking.PIN_ADD, null,
            nameField.getValue(), pendingX, pendingZ);
        stopPlacing();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX,
                                 double scrollY) {
        if (mode == Mode.TACTICAL && x >= width - PANEL_WIDTH
                && scrollY != 0.0D) {
            int maximum = Math.max(0, contactCount - contactRowsVisible);
            contactScroll = Mth.clamp(
                contactScroll - (int) Math.signum(scrollY), 0, maximum);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (placing && nameField.isFocused()
                && (event.key() == 257 || event.key() == 335)) {
            commitPin();
            return true;
        }
        if (!placing && PlaneKeyBindings.GPS_RANGE.matches(event)) {
            cycleRange();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event,
                                boolean doubled) {
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();

        for (PinRow row : pinRows) {
            if (mouseY < row.y() - 1 || mouseY > row.y() + 9) {
                continue;
            }
            if (mouseX >= row.shareX() && mouseX < row.shareX() + 10) {
                PlaneNetworking.sendPinEdit(PlaneNetworking.PIN_SHARE,
                    row.pin().id(), "", 0.0D, 0.0D);
                return true;
            }
            if (mouseX >= row.deleteX() && mouseX < row.deleteX() + 10) {
                PlaneNetworking.sendPinEdit(PlaneNetworking.PIN_REMOVE,
                    row.pin().id(), "", 0.0D, 0.0D);
                return true;
            }
        }

        double dx = mouseX - centreX;
        double dy = mouseY - centreY;
        if (Math.hypot(dx, dy) <= radius) {
            double[] world = new double[2];
            GpsPolicy.unproject(dx, dy, range, radius, heading, world);
            startPlacing(own.x + world[0], own.z + world[1]);
            return true;
        }
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                   int mouseY, float partialTicks) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        graphics.fill(0, 0, width, height, BACKDROP);

        own = minecraft.player.position();
        heading = 0.0D;
        range = range();
        if (mode == Mode.AIRBORNE
                && minecraft.player.getVehicle() instanceof PlaneEntity plane) {
            own = plane.position();
            Vec3 forward = plane.getBodyDirection(0.0F, 0.0F, 1.0F);
            heading = GpsPolicy.headingRadians(forward.x, forward.z);
        }

        drawDial(graphics);
        List<GpsContact> shown = contactsToDraw();
        drawContacts(graphics, shown);
        drawPins(graphics);
        drawOwnShip(graphics);
        drawPanel(graphics, shown);

        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);

        if (placing) {
            graphics.text(font, Component.translatable(
                    "amrac.gui.gps.pin_at",
                    String.format(Locale.ROOT, "%.0f, %.0f", pendingX, pendingZ)),
                12, height - 66, LABEL);
        }
    }

    private double range() {
        return GpsRange.outerRing(mode == Mode.TACTICAL);
    }

    private void cycleRange() {
        GpsRange.cycle(mode == Mode.TACTICAL);
    }

    private List<GpsContact> contactsToDraw() {
        if (mode == Mode.TACTICAL) {
            return GpsData.contacts();
        }
        List<GpsContact> out = new ArrayList<>(GpsData.ownMissiles());
        for (PlaneRadar.Lock lock : PlaneRadar.locks()) {
            out.add(new GpsContact(GpsContact.Kind.AI_AIRCRAFT, "Target",
                lock.position.x, lock.position.y, lock.position.z,
                lock.velocity.length()));
        }
        return out;
    }

    private void drawDial(GuiGraphicsExtractor graphics) {
        drawCircle(graphics, radius, EDGE);
        for (int ring = 1; ring <= RINGS; ring++) {
            int pixels = radius * ring / RINGS;
            drawCircle(graphics, pixels, ring == RINGS ? RING_BRIGHT : RING);
            graphics.text(font, Component.literal(String.format(Locale.ROOT,
                    "%.0fKb", range * ring / RINGS / 1000.0D)),
                centreX + 3, centreY - pixels - 9, DIM);
        }
        graphics.horizontalLine(centreX - radius, centreX + radius, centreY, AXIS);
        graphics.verticalLine(centreX, centreY - radius, centreY + radius, AXIS);

        String top = mode == Mode.TACTICAL ? "N" : "NOSE";
        graphics.centeredText(font, Component.literal(top),
            centreX, centreY - radius - 20, LABEL);
        if (mode == Mode.TACTICAL) {
            graphics.centeredText(font, Component.literal("S"),
                centreX, centreY + radius + 10, LABEL);
            graphics.centeredText(font, Component.literal("E"),
                centreX + radius + 10, centreY - 4, LABEL);
            graphics.centeredText(font, Component.literal("W"),
                centreX - radius - 10, centreY - 4, LABEL);
        }
    }

    private void drawContacts(GuiGraphicsExtractor graphics, List<GpsContact> shown) {
        double[] out = new double[2];
        for (GpsContact contact : shown) {
            double dx = contact.x() - own.x;
            double dz = contact.z() - own.z;
            if (!GpsPolicy.onDial(dx, dz, range)) {
                continue;
            }
            GpsPolicy.projectRotated(dx, dz, range, radius, heading, out);
            int x = centreX + (int) Math.round(out[0]);
            int y = centreY + (int) Math.round(out[1]);
            int colour = colourOf(contact);
            if (GpsData.isMissile(contact)) {
                graphics.fill(x - 1, y - 1, x + 2, y + 2, colour);
            } else if (contact.live()) {
                graphics.fill(x - 2, y - 2, x + 3, y + 3, colour);
                graphics.text(font, Component.literal(contact.name()),
                    x + 6, y - 4, colour);
            } else {
                int faded = fade(colour);
                graphics.fill(x - 2, y - 2, x + 3, y - 1, faded);
                graphics.fill(x - 2, y + 2, x + 3, y + 3, faded);
                graphics.fill(x - 2, y - 1, x - 1, y + 2, faded);
                graphics.fill(x + 2, y - 1, x + 3, y + 2, faded);
                graphics.text(font, Component.literal(contact.name() + " ?"),
                    x + 6, y - 4, faded);
            }
        }
    }

    private void drawPins(GuiGraphicsExtractor graphics) {
        double[] out = new double[2];
        for (GpsPin pin : GpsData.pins()) {
            double dx = pin.x() - own.x;
            double dz = pin.z() - own.z;
            if (!GpsPolicy.onDial(dx, dz, range)) {
                continue;
            }
            GpsPolicy.projectRotated(dx, dz, range, radius, heading, out);
            int x = centreX + (int) Math.round(out[0]);
            int y = centreY + (int) Math.round(out[1]);
            int colour = pin.shared() ? PIN_SHARED : PIN;
            graphics.horizontalLine(x - 3, x + 3, y, colour);
            graphics.verticalLine(x, y - 3, y + 3, colour);
            graphics.text(font, Component.literal(pin.name()), x + 5, y + 2, colour);
        }
    }

    private void drawOwnShip(GuiGraphicsExtractor graphics) {
        graphics.fill(centreX - 1, centreY - 4, centreX + 2, centreY + 5, OWN);
        graphics.fill(centreX - 4, centreY - 1, centreX + 5, centreY + 2, OWN);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, List<GpsContact> shown) {
        int left = width - PANEL_WIDTH;
        graphics.fill(left, 0, width, height, PANEL);
        graphics.verticalLine(left, 0, height, EDGE);

        int y = 10;
        graphics.text(font, title, left + 8, y, LABEL);
        y += 14;

        if (mode == Mode.TACTICAL) {
            int listTop = y + 12;
            int listBottom = height - 120;
            int rows = Math.max(1, (listBottom - listTop) / 22);
            contactRowsVisible = rows;
            contactCount = shown.size();
            contactScroll = Mth.clamp(contactScroll, 0,
                Math.max(0, contactCount - rows));

            graphics.text(font, contactCount > rows
                    ? Component.translatable(
                        "amrac.gui.gps.contacts_scrolled",
                        String.valueOf(contactScroll + 1),
                        String.valueOf(Math.min(contactScroll + rows,
                            contactCount)),
                        String.valueOf(contactCount))
                    : Component.translatable("amrac.gui.gps.contacts",
                        String.valueOf(contactCount)),
                left + 8, y, DIM);
            y += 12;
            for (int index = contactScroll;
                 index < contactCount && index < contactScroll + rows;
                 index++) {
                GpsContact contact = shown.get(index);
                int colour = contact.live() ? colourOf(contact)
                    : fade(colourOf(contact));
                graphics.text(font, Component.literal(contact.live()
                        ? contact.name() : contact.name() + " ?"),
                    left + 8, y, colour);
                y += 10;
                graphics.text(font, Component.literal(String.format(Locale.ROOT,
                        "%.0f %.0f  alt %.0f  %.0f b/s%s",
                        contact.x(), contact.z(), contact.y(),
                        contact.speedBlocksPerSecond(),
                        contact.live() ? "" : "  last seen")),
                    left + 8, y, DIM);
                y += 12;
            }
            drawContactScrollbar(graphics, left, listTop, listBottom, rows);
        }

        y = Math.max(y + 6, height - 116);
        graphics.text(font, Component.translatable("amrac.gui.gps.pins"),
            left + 8, y, LABEL);
        y += 12;

        pinRows.clear();
        for (GpsPin pin : GpsData.pins()) {
            if (y > height - 20) {
                break;
            }
            boolean mine = GpsData.ownedByViewer(pin);
            int colour = pin.shared() ? PIN_SHARED : PIN;
            graphics.text(font, Component.literal(pin.name()), left + 8, y, colour);
            graphics.text(font, Component.literal(String.format(Locale.ROOT,
                    "%.0f %.0f", pin.x(), pin.z())), left + 8, y + 10, DIM);
            if (!mine) {
                graphics.text(font, Component.literal(pin.ownerName()),
                    left + 70, y + 10, DIM);
            }
            int shareX = width - 34;
            int deleteX = width - 18;
            if (mine) {
                graphics.text(font, Component.literal("S"), shareX, y, ACTION);
                graphics.text(font, Component.literal("X"), deleteX, y, ACTION);
                pinRows.add(new PinRow(pin, y, shareX, deleteX));
            }
            y += 22;
        }
        if (GpsData.pins().isEmpty()) {
            graphics.text(font, Component.translatable(
                "amrac.gui.gps.pin_hint"), left + 8, y, DIM);
        }
    }

    private void drawContactScrollbar(GuiGraphicsExtractor graphics, int left,
                                      int top, int bottom, int rows) {
        if (contactCount <= rows) {
            return;
        }
        int track = Math.max(1, bottom - top);
        int x = width - 4;
        graphics.fill(x, top, x + 2, bottom, EDGE);
        int thumb = Math.max(8, track * rows / contactCount);
        int travel = track - thumb;
        int offset = contactCount == rows ? 0
            : travel * contactScroll / Math.max(1, contactCount - rows);
        graphics.fill(x, top + offset, x + 2, top + offset + thumb, LABEL);
    }

    private static int fade(int colour) {
        int r = (colour >> 16 & 0xFF) * 45 / 100;
        int g = (colour >> 8 & 0xFF) * 45 / 100;
        int b = (colour & 0xFF) * 45 / 100;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int colourOf(GpsContact contact) {
        return contact.seekerActive() ? MISSILE_ACTIVE : colourOf(contact.kind());
    }

    private static int colourOf(GpsContact.Kind kind) {
        return switch (kind) {
            case PLAYER -> PLAYER;
            case AI_AIRCRAFT -> AI;
            case EMPTY_AIRCRAFT -> EMPTY;
            case OWN_MISSILE, MISSILE -> MISSILE;
        };
    }

    private void drawCircle(GuiGraphicsExtractor graphics, int r, int colour) {
        if (r <= 0) {
            return;
        }
        int steps = Math.max(64, r * 4);
        for (int i = 0; i < steps; i++) {
            double angle = i * 2.0D * Math.PI / steps;
            int x = centreX + (int) Math.round(Math.cos(angle) * r);
            int y = centreY + (int) Math.round(Math.sin(angle) * r);
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }
}

package amrac.display;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import amrac.blocks.ConsoleBlockEntity;
import amrac.blocks.ScreenBlockEntity;
import amrac.gps.GpsContact;
import amrac.gps.GpsPolicy;
import amrac.gps.GpsService;

import java.util.ArrayList;
import java.util.List;

public final class GpsMirror {
    public static final int REFRESH_TICKS = 20;

    private GpsMirror() {
    }

    public static boolean mirroring(ConsoleBlockEntity desk) {
        return desk.mode() == ConsoleMode.MAP
            && desk.slot().getItem(0).is(
                amrac.AmracItems.GPS);
    }

    public static void refresh(ServerLevel level, ConsoleBlockEntity desk,
                               ScreenBlockEntity panel) {
        BlockPos at = desk.getBlockPos();
        Vec3 centre = new Vec3(at.getX() + 0.5D, at.getY() + 0.5D,
            at.getZ() + 0.5D);
        List<GpsContact> contacts =
            GpsService.contacts(level, centre, null, true);
        List<ScreenContent.Blip> blips = new ArrayList<>(contacts.size());
        for (GpsContact contact : contacts) {
            int kind = contact.seekerActive()
                ? ScreenContent.ACTIVE_MISSILE_BLIP : contact.kind().ordinal();
            blips.add(new ScreenContent.Blip(kind,
                contact.x(), contact.z(), contact.name()));
        }
        panel.content().showGps("Tactical GPS", blips, centre.x, centre.z,
            GpsPolicy.RANGE * 2.0D);
        panel.contentChanged();
    }
}

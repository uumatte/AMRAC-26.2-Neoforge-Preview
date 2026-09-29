package amrac.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import amrac.gps.GpsContact;
import amrac.gps.GpsPin;
import amrac.network.PlaneNetworking;

public final class GpsData {
    private static final int SCREEN_INTERVAL_TICKS = 4;

    private static final int HUD_INTERVAL_TICKS = 10;

    private static volatile List<GpsContact> contacts = List.of();
    private static volatile List<GpsPin> pins = List.of();
    private static volatile Set<UUID> ownPins = Set.of();
    private static int sinceRequest;

    private GpsData() {
    }

    public static void accept(List<GpsContact> incoming, List<GpsPin> incomingPins,
                              Set<UUID> mine) {
        contacts = List.copyOf(incoming);
        pins = List.copyOf(incomingPins);
        ownPins = Set.copyOf(mine);
    }

    public static List<GpsContact> contacts() {
        return contacts;
    }

    public static List<GpsPin> pins() {
        return pins;
    }

    public static boolean ownedByViewer(GpsPin pin) {
        return ownPins.contains(pin.id());
    }

    public static List<GpsContact> ownMissiles() {
        return contacts.stream()
            .filter(contact -> contact.kind() == GpsContact.Kind.OWN_MISSILE)
            .toList();
    }

    public static boolean isMissile(GpsContact contact) {
        return contact.kind() == GpsContact.Kind.OWN_MISSILE
            || contact.kind() == GpsContact.Kind.MISSILE;
    }

    public static void tick(boolean screenOpen, boolean flying) {
        if (!screenOpen && !flying) {
            sinceRequest = Integer.MAX_VALUE / 2;
            return;
        }
        int interval = screenOpen ? SCREEN_INTERVAL_TICKS : HUD_INTERVAL_TICKS;
        if (++sinceRequest < interval) {
            return;
        }
        sinceRequest = 0;
        PlaneNetworking.requestGpsContacts(screenOpen);
    }

    public static void requestNow(boolean full) {
        sinceRequest = 0;
        PlaneNetworking.requestGpsContacts(full);
    }

    public static void clear() {
        contacts = List.of();
        pins = List.of();
        ownPins = new HashSet<>();
    }
}

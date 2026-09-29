package amrac.entities.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import amrac.entities.AircraftRegistry;
import amrac.entities.AircraftVirtualService;
import amrac.weapons.MissileProfile;
import amrac.weapons.MissileProfiles;
import amrac.weapons.VirtualMissileService;

public final class AiDebugReport {
    private AiDebugReport() {
    }

    public static List<String> lines(ServerLevel level,
                                     ServerPlayer viewer) {
        List<String> out = new ArrayList<>();
        List<AircraftRegistry.Record> records =
            new ArrayList<>(AircraftRegistry.all(level));
        List<AiPilotBrain> brains = AiPilotService.allBrains();

        out.add("=== AI pilot report ===");
        out.add(String.format("%d pilot(s), %d aircraft record(s), %d virtual"
                + " aircraft, %d virtual round(s) in the air",
            brains.size(), records.size(),
            AircraftVirtualService.activeCount(),
            VirtualMissileService.activeCount()));

        if (brains.isEmpty()) {
            out.add("no AI pilots exist at all -- nothing has been started,"
                + " or every pilot has been removed");
        }

        for (AiPilotBrain brain : brains) {
            out.add("");
            reportPilot(out, level, brain, records);
        }

        out.add("");
        out.add("-- aircraft the registry knows about --");
        if (records.isEmpty()) {
            out.add("  none. An AI can only find a target through this list,"
                + " so an empty one means nobody engages anybody.");
        }
        for (AircraftRegistry.Record record : records) {
            out.add(String.format(
                "  %s  %-11s team=%-8s crewed=%-5s  xz %.0f %.0f  alt %.0f"
                    + "  %.0f b/s%s",
                record.id.toString().substring(0, 8), record.presence,
                quoted(record.team), record.crewed,
                record.position.x, record.position.z, record.position.y,
                record.velocity.length() * 20.0D,
                record.simulated() ? "" : "   NOT SIMULATED: invisible as a"
                    + " target"));
        }
        return out;
    }

    private static void reportPilot(List<String> out, ServerLevel level,
                                    AiPilotBrain brain,
                                    List<AircraftRegistry.Record> records) {
        boolean bodyLoaded = level.getEntity(brain.id) != null;
        out.add(String.format("%s  %s  rank=%s  team=%s  phase=%s  body=%s",
            brain.callsign(), brain.id.toString().substring(0, 8),
            brain.rank().label(), quoted(brain.team()), brain.phase(),
            bodyLoaded ? "loaded" : "NOT LOADED"));
        if (!bodyLoaded && brain.flownAircraft() == null) {
            out.add("  ORPHAN: no body and no aeroplane. Something asked about"
                + " this UUID and a mind was created for it; nothing ever"
                + " started it. A pile of these is stale roster entries.");
        }

        if (!brain.missionActive()) {
            out.add("  STOPPED: no mission. It has not been started, or it was"
                + " stopped from the pilot's own screen.");
            return;
        }
        UUID aircraftId = brain.flownAircraft();
        if (aircraftId == null) {
            out.add("  ON FOOT: not in an aeroplane. Idle reason is shown over"
                + " its head in game; the usual causes are no empty aircraft"
                + " within 32m, an airframe this rank is not cleared on, or no"
                + " round aboard it can use.");
            return;
        }
        AircraftRegistry.Record own = find(records, aircraftId);
        out.add(String.format("  flying %s  %s", aircraftId.toString()
            .substring(0, 8), own == null ? "NOT IN THE REGISTRY"
                : own.presence + (own.simulated() ? "" : "  NOT SIMULATED")));

        if (!brain.departureCompleteForDebug()) {
            StringBuilder levels = new StringBuilder();
            for (AiPilotRank rank : AiPilotRank.values()) {
                levels.append(levels.length() == 0 ? "" : ", ")
                    .append(rank.label()).append(' ')
                    .append(String.format("%.0f", rank.cruiseAltitude()));
            }
            out.add("  CLIMBING: has not levelled off at its assigned altitude"
                + " yet. It still searches, but only turns to fight a contact"
                + " already inside its rank's engagement range, and a rank that"
                + " evades breaks away from a round chasing it; otherwise it"
                + " climbs on. Cruise altitudes: " + levels + " (never above"
                + " nine tenths of the airframe's ceiling).");
        }

        if (brain.team() == null || brain.team().isBlank()) {
            out.add("  NO TEAM: hostility needs both sides named, so a pilot"
                + " with no team is hostile to nobody and will never find a"
                + " target.");
        }

        int hostile = 0;
        int hostileSimulated = 0;
        for (AircraftRegistry.Record record : records) {
            if (record.id.equals(aircraftId)) {
                continue;
            }
            if (!AiTeamPolicy.hostile(brain.team(), record.team)) {
                continue;
            }
            hostile++;
            if (record.simulated()) {
                hostileSimulated++;
            }
        }
        out.add(String.format("  hostile aircraft on the level: %d, of which"
            + " %d are simulated and therefore findable", hostile,
            hostileSimulated));
        if (hostile > 0 && hostileSimulated == 0) {
            out.add("  NO FINDABLE TARGET: the enemy exists but nothing is"
                + " simulating it, so the search cannot see it.");
        }

        UUID enemy = brain.enemyAircraftForDebug();
        if (enemy == null) {
            out.add("  no target held right now");
        } else {
            AircraftRegistry.Record contact = find(records, enemy);
            double range = contact == null || own == null ? -1.0D
                : contact.position.distanceTo(own.position);
            out.add(String.format("  target %s at %s blocks",
                enemy.toString().substring(0, 8),
                range < 0.0D ? "unknown range" : String.format("%.0f", range)));
            if (range >= 0.0D) {
                double gate = brain.rank().engagementRange();
                out.add(String.format("  rank fires inside %.0f: %s",
                    gate, range <= gate ? "in range"
                        : "TOO FAR, closing"));
            }
        }

        String block = brain.fireBlockForDebug();
        out.add("  NOT FIRING BECAUSE: " + (block == null
            ? "nothing -- it fired, or is about to" : block));

        int wait = brain.launchIntervalForDebug();
        if (wait > 0) {
            out.add(String.format("  RELOADING: %d ticks (%.0f s) before this"
                + " pilot may fire again", wait, wait / 20.0D));
        }
        if (brain.threatenedForDebug()) {
            out.add(brain.rank().evades()
                ? "  EVADING: a round is chasing this aeroplane, so it is"
                    + " breaking away (also on the departure climb). It may"
                    + " still fire while it runs if a shot is lined up inside"
                    + " its rank's range."
                : "  THREATENED: a round is chasing this aeroplane, but this"
                    + " rank does not evade and flies on.");
        }

        String[] loadout = brain.loadoutForDebug();
        if (loadout == null || loadout.length == 0) {
            out.add("  RAILS UNKNOWN: no loadout could be read");
        } else {
            StringBuilder rails = new StringBuilder("  rails:");
            int armed = 0;
            for (String id : loadout) {
                if (id == null || id.isBlank()) {
                    rails.append(" [empty]");
                    continue;
                }
                armed++;
                MissileProfile profile = MissileProfiles.byId(id);
                rails.append(' ').append(id).append('(')
                    .append(profile == null ? "?"
                        : String.format("%.0f", profile.maxLaunchRange))
                    .append(')');
            }
            out.add(rails.toString());
            if (armed == 0) {
                out.add("  OUT OF MISSILES: every rail is empty, so there is"
                    + " nothing to fire whatever else is true.");
            }
        }
    }

    private static AircraftRegistry.Record find(
            List<AircraftRegistry.Record> records, UUID id) {
        for (AircraftRegistry.Record record : records) {
            if (record.id.equals(id)) {
                return record;
            }
        }
        return null;
    }

    private static String quoted(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }
}

package amrac.entities.ai;

import java.util.List;
import java.util.function.Predicate;
import amrac.weapons.LoadoutFit;
import amrac.weapons.SummonLoadout;

public final class AiLaunchCheck {
    public record Problem(String key, List<String> args) {
        static Problem of(String key, Object... args) {
            return new Problem(key, java.util.Arrays.stream(args)
                .map(String::valueOf).toList());
        }
    }

    private AiLaunchCheck() {
    }

    public static Problem problem(AiLaunchOrder order, AiLaunchCatalog catalog,
                                  Predicate<String> teamExists,
                                  boolean positionParses) {
        AiLaunchCatalog.Airframe airframe = catalog.airframe(order.aircraft());
        if (airframe == null) {
            return Problem.of("amrac.ai_command.unknown_aircraft",
                order.aircraft());
        }
        if (!catalog.cleared(order.rank(), airframe)) {
            return Problem.of("amrac.ai_command.not_for_rank");
        }
        if (order.fuelPercent() < 1 || order.fuelPercent() > 100) {
            return Problem.of("amrac.ai_command.fuel");
        }
        SummonLoadout.Parsed loadout;
        try {
            loadout = SummonLoadout.parse(order.loadout());
        } catch (SummonLoadout.Invalid invalid) {
            return Problem.of("amrac.ai_command." + invalid.reason(),
                invalid.token());
        }
        loadout = new SummonLoadout.Parsed(loadout.stations(), 0, 0);
        LoadoutFit.Refusal refusal = LoadoutFit.check(loadout,
            airframe.hasMissiles(), airframe.pylons(), airframe::carries,
            Integer.MAX_VALUE);
        if (refusal != null) {
            return switch (refusal.reason()) {
                case NO_MISSILES -> Problem.of("amrac.ai_command.no_missiles");
                case TOO_MANY -> Problem.of("amrac.ai_command.too_many",
                    refusal.capacity(), refusal.given());
                case NOT_CARRIED -> Problem.of("amrac.ai_command.not_carried",
                    refusal.store());
                case COUNTERMEASURES_FULL -> Problem.of(
                    "amrac.ai_command.countermeasures_full",
                    refusal.capacity(), refusal.given());
            };
        }
        if (order.rank().requiresMissiles()
                && loadout.stations().stream().noneMatch(java.util.Objects::nonNull)) {
            return Problem.of("amrac.ai_command.unarmed");
        }
        if (!order.team().isEmpty() && !teamExists.test(order.team())) {
            return Problem.of("amrac.ai_command.unknown_team", order.team());
        }
        if (!positionParses) {
            return Problem.of("amrac.ai_command.bad_position", order.position());
        }
        return null;
    }
}

package amrac.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import amrac.weapons.MissileProfile;
import amrac.weapons.MissileProfiles;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class MissileItem extends Item {
    private final String profileId;

    public MissileItem(Properties properties, String profileId) {
        super(properties);
        this.profileId = profileId;
    }

    public String profileId() {
        return profileId;
    }

    public MissileProfile profile() {
        return MissileProfiles.byId(profileId);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        MissileProfile p = profile();
        lines.accept(Component.translatable("amrac.tooltip.missile.guidance",
            Component.translatable(p.activeHoming
                ? "amrac.tooltip.missile.active"
                : "amrac.tooltip.missile.semi_active"))
            .withStyle(ChatFormatting.GRAY));

        var flightModel = amrac.weapons.MissilePolicy
            .flightModel(p);
        if (flightModel != null) {
            var atmosphere = amrac.physics.aircraft
                .FlightModelRegistry.instance().atmosphere();
            var measured = amrac.physics.missile
                .MissilePerformance.of(flightModel, atmosphere,
                    p.maxLifetimeTicks / 20.0D);
            lines.accept(Component.translatable(
                    "amrac.tooltip.missile.speed_at_altitude",
                    String.format("%.0f", measured.peakSpeed()),
                    formatCompact(measured.peakMach(), 2),
                    String.format("%.0f", measured.referenceAltitude()))
                .withStyle(ChatFormatting.DARK_GRAY));
            lines.accept(Component.translatable("amrac.tooltip.missile.load",
                    String.format("%.0f", measured.maxLoadG()))
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.accept(Component.translatable("amrac.tooltip.missile.fuse",
                formatCompact(p.proximityFuseRadius, 2))
            .withStyle(ChatFormatting.DARK_GRAY));
        lines.accept(Component.translatable("amrac.tooltip.missile.range",
                String.format("%.0f", p.maxLaunchRange))
            .withStyle(ChatFormatting.DARK_GRAY));
        lines.accept(Component.translatable("amrac.tooltip.missile.rail",
                Component.translatable(p.faction.displayKey()))
            .withStyle(ChatFormatting.GOLD));
        lines.accept(Component.translatable("amrac.tooltip.missile.mount")
            .withStyle(ChatFormatting.DARK_GREEN));
    }

    private static String formatCompact(double value, int decimals) {
        String formatted = String.format(Locale.ROOT, "%." + decimals + "f",
            value);
        int dot = formatted.indexOf('.');
        if (dot < 0) {
            return formatted;
        }
        int end = formatted.length();
        while (end > dot + 1 && formatted.charAt(end - 1) == '0') {
            --end;
        }
        if (end == dot + 1) {
            --end;
        }
        return formatted.substring(0, end);
    }
}

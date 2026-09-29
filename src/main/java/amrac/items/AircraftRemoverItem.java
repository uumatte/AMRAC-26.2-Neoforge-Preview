package amrac.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import amrac.AmracItems;
import amrac.entities.PlaneEntity;

public class AircraftRemoverItem extends Item {
    public AircraftRemoverItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("amrac.tooltip.aircraft_remover")
            .withStyle(ChatFormatting.GRAY));
    }

    public static InteractionResult remove(PlaneEntity plane, Player player) {
        if (plane.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(plane.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        boolean playerAboard = false;
        for (Entity passenger : plane.getPassengers()) {
            if (passenger instanceof Player) {
                playerAboard = true;
                break;
            }
        }
        AircraftRemoverPolicy.Refusal refusal = AircraftRemoverPolicy.refusal(
            plane.isAlive(), plane.isDying(),
            plane.getOnGround() || plane.isOnWater(), playerAboard);
        if (refusal != AircraftRemoverPolicy.Refusal.NONE) {
            if (refusal != AircraftRemoverPolicy.Refusal.WRECKED) {
                amrac.AmracMod.sendOverlay(player, Component.translatable(
                    refusal == AircraftRemoverPolicy.Refusal.AIRBORNE
                        ? "amrac.message.remover_airborne"
                        : "amrac.message.remover_occupied"), true);
            }
            return InteractionResult.CONSUME;
        }

        Component name = plane.getPickResult().isEmpty()
            ? plane.getDisplayName() : plane.getPickResult().getHoverName();
        List<Component> parts = new ArrayList<>();
        List<ItemStack> returned = returnedItems(plane, parts);

        for (Entity passenger : new ArrayList<>(plane.getPassengers())) {
            passenger.stopRiding();
        }
        for (ItemStack stack : returned) {
            plane.spawnAtLocation(level, stack, 0.5F);
        }

        amrac.entities.ai.AiPilotRoster.of(level).unpark(plane.getUUID());
        amrac.entities.AircraftRegistry.forget(plane.getUUID());
        double x = plane.getX();
        double y = plane.getY() + 1.0D;
        double z = plane.getZ();
        plane.discard();

        level.sendParticles(ParticleTypes.POOF, x, y, z, 24, 1.5D, 0.6D, 1.5D,
            0.02D);
        level.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_IRON,
            SoundSource.PLAYERS, 0.9F, 0.7F);
        level.playSound(null, x, y, z, SoundEvents.ITEM_PICKUP,
            SoundSource.PLAYERS, 0.6F, 0.8F);

        MutableComponent message = Component.translatable(
            "amrac.message.aircraft_removed", name);
        if (!parts.isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    list.append(", ");
                }
                list.append(parts.get(i));
            }
            message = Component.translatable(
                "amrac.message.aircraft_removed_stores", name, list);
        }
        amrac.AmracMod.sendOverlay(player, message, true);
        return InteractionResult.CONSUME;
    }

    private static List<ItemStack> returnedItems(PlaneEntity plane,
                                                 List<Component> parts) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack airframe = plane.getPickResult();
        if (!airframe.isEmpty()) {
            out.add(airframe.copyWithCount(1));
        }

        int rounds = 0;
        for (Map.Entry<String, Integer> round : AircraftRemoverPolicy
                .missiles(plane.getLoadout()).entrySet()) {
            Item item = AmracItems.missileItem(round.getKey());
            if (item == null) {
                continue;
            }
            rounds += round.getValue();
            add(out, item, round.getValue());
        }
        if (rounds > 0) {
            parts.add(Component.translatable("amrac.message.removed.missiles",
                rounds));
        }

        int drums = AircraftRemoverPolicy.fuelDrums(plane.getFuelLitres(),
            PlaneEntity.FUEL_ITEM_LITRES);
        if (drums > 0) {
            add(out, AmracItems.AVIATION_FUEL, drums);
            parts.add(Component.translatable("amrac.message.removed.fuel",
                drums));
        }
        if (plane.getChaffCount() > 0) {
            add(out, AmracItems.CHAFF, plane.getChaffCount());
            parts.add(Component.translatable("amrac.message.removed.chaff",
                plane.getChaffCount()));
        }
        if (plane.getFlareCount() > 0) {
            add(out, AmracItems.FLARE, plane.getFlareCount());
            parts.add(Component.translatable("amrac.message.removed.flare",
                plane.getFlareCount()));
        }
        int bullets = AircraftRemoverPolicy.bulletItems(
            plane.getMachineGunAmmoCount(), PlaneEntity.ROUNDS_PER_BULLET_ITEM);
        if (bullets > 0) {
            add(out, AmracItems.BULLET, bullets);
            parts.add(Component.translatable("amrac.message.removed.bullets",
                bullets));
        }
        return out;
    }

    private static void add(List<ItemStack> out, Item item, int count) {
        int maxStack = new ItemStack(item).getMaxStackSize();
        for (int size : AircraftRemoverPolicy.stacks(count, maxStack)) {
            out.add(new ItemStack(item, size));
        }
    }
}

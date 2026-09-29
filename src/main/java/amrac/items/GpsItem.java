package amrac.items;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import amrac.network.PlaneNetworking;

public class GpsItem extends Item {
    public GpsItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            PlaneNetworking.openGps();
        }
        return InteractionResult.SUCCESS;
    }
}

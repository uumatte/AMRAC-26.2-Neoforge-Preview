package amrac.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import amrac.entities.PlaneEntity;

// Replaces only the distance handleMoveVehicle expects (its one lengthSqr call)
// for this mod's aircraft. A value modifier, not a redirect, so another mod on
// the same check chains instead of failing.
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class VehicleMoveAllowanceMixin {

    @ModifyExpressionValue(method = "handleMoveVehicle",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D"))
    private double amrac$aircraftMoveAllowance(double expected) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        return player != null
            && player.getRootVehicle() instanceof PlaneEntity plane
            ? plane.clientMoveExpectedSqr(expected) : expected;
    }
}

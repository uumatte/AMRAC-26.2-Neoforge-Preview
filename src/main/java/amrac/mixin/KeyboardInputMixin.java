package amrac.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import amrac.client.PlaneViewState;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void amrac$blankWhileAboard(CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (PlaneViewState.ridingPlane(minecraft) == null) {
            return;
        }
        this.keyPresses = Input.EMPTY;
        this.moveVector = Vec2.ZERO;
    }
}

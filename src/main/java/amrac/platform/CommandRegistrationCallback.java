package amrac.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

@FunctionalInterface
public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = new Event<>();

    void register(CommandDispatcher<CommandSourceStack> dispatcher,
                  CommandBuildContext registryAccess,
                  Commands.CommandSelection environment);
}

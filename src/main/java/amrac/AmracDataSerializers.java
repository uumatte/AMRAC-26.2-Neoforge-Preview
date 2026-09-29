package amrac;

import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import org.joml.Quaternionfc;

public final class AmracDataSerializers {
    public static final EntityDataSerializer<Quaternionfc> QUATERNION =
        EntityDataSerializers.QUATERNION;

    private AmracDataSerializers() {
    }

    public static void register() {
    }
}

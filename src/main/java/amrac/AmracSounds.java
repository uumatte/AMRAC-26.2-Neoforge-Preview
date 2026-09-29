package amrac;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import amrac.client.SoundMixPolicy;

/**
 * Linked by the BVR Replay mod: keep this class's name, package and public members, or replays
 * crash with NoClassDefFoundError.
 */
public final class AmracSounds {
    public static final Identifier PLANE_LOOP_ID =
        AmracMod.id("plane_loop");

    public static final float AIRCRAFT_AUDIBLE_RANGE = 96.0F;

    public static final SoundEvent PLANE_LOOP = Registry.register(
        BuiltInRegistries.SOUND_EVENT, PLANE_LOOP_ID,
        SoundEvent.createFixedRangeEvent(PLANE_LOOP_ID, AIRCRAFT_AUDIBLE_RANGE));

    public static final Identifier PLANE_AIRFLOW_ID =
        AmracMod.id("plane_airflow");

    public static final SoundEvent PLANE_AIRFLOW = Registry.register(
        BuiltInRegistries.SOUND_EVENT, PLANE_AIRFLOW_ID,
        SoundEvent.createFixedRangeEvent(PLANE_AIRFLOW_ID, AIRCRAFT_AUDIBLE_RANGE));

    public static final Identifier PLANE_AFTERBURNER_ID =
        AmracMod.id("plane_afterburner");

    public static final SoundEvent PLANE_AFTERBURNER = Registry.register(
        BuiltInRegistries.SOUND_EVENT, PLANE_AFTERBURNER_ID,
        SoundEvent.createFixedRangeEvent(PLANE_AFTERBURNER_ID,
            AIRCRAFT_AUDIBLE_RANGE));

    public static final Identifier JET_ENGINE_LOOP_ID =
        AmracMod.id("jet_engine_loop");

    public static final SoundEvent JET_ENGINE_LOOP = Registry.register(
        BuiltInRegistries.SOUND_EVENT, JET_ENGINE_LOOP_ID,
        SoundEvent.createFixedRangeEvent(JET_ENGINE_LOOP_ID,
            AIRCRAFT_AUDIBLE_RANGE));

    public static final Identifier SUPERSONIC_RUMBLE_ID =
        AmracMod.id("supersonic_rumble");

    public static final SoundEvent SUPERSONIC_RUMBLE = Registry.register(
        BuiltInRegistries.SOUND_EVENT, SUPERSONIC_RUMBLE_ID,
        SoundEvent.createFixedRangeEvent(SUPERSONIC_RUMBLE_ID,
            AIRCRAFT_AUDIBLE_RANGE));

    public static final Identifier SONIC_BOOM_ID =
        AmracMod.id("sonic_boom");

    public static final SoundEvent SONIC_BOOM = Registry.register(
        BuiltInRegistries.SOUND_EVENT, SONIC_BOOM_ID,
        SoundEvent.createFixedRangeEvent(SONIC_BOOM_ID, 320.0F));

    public static final Identifier MISSILE_LAUNCH_ID =
        AmracMod.id("missile_launch");

    public static final SoundEvent MISSILE_LAUNCH = Registry.register(
        BuiltInRegistries.SOUND_EVENT, MISSILE_LAUNCH_ID,
        SoundEvent.createFixedRangeEvent(MISSILE_LAUNCH_ID, 160.0F));

    public static final Identifier MISSILE_LOCK_ID =
        AmracMod.id("missile_lock");

    public static final SoundEvent MISSILE_LOCK = Registry.register(
        BuiltInRegistries.SOUND_EVENT, MISSILE_LOCK_ID,
        SoundEvent.createFixedRangeEvent(MISSILE_LOCK_ID, 32.0F));

    public static final Identifier MISSILE_LAUNCH_COCKPIT_ID =
        AmracMod.id("missile_launch_cockpit");

    public static final SoundEvent MISSILE_LAUNCH_COCKPIT = Registry.register(
        BuiltInRegistries.SOUND_EVENT, MISSILE_LAUNCH_COCKPIT_ID,
        SoundEvent.createFixedRangeEvent(MISSILE_LAUNCH_COCKPIT_ID, 32.0F));

    public static final Identifier GUN_FIRE_ID = AmracMod.id("gun_fire");

    public static final SoundEvent GUN_FIRE = Registry.register(
        BuiltInRegistries.SOUND_EVENT, GUN_FIRE_ID,
        SoundEvent.createFixedRangeEvent(GUN_FIRE_ID, 96.0F));

    public static final Identifier RWR_WARNING_ID = AmracMod.id("rwr_warning");

    public static final SoundEvent RWR_WARNING = Registry.register(
        BuiltInRegistries.SOUND_EVENT, RWR_WARNING_ID,
        SoundEvent.createFixedRangeEvent(RWR_WARNING_ID, 32.0F));

    public static final Identifier BOUNDARY_WARNING_ID =
        AmracMod.id("boundary_warning");

    public static final SoundEvent BOUNDARY_WARNING = Registry.register(
        BuiltInRegistries.SOUND_EVENT, BOUNDARY_WARNING_ID,
        SoundEvent.createFixedRangeEvent(BOUNDARY_WARNING_ID, 32.0F));

    public static final Identifier AFTERBURNER_LIGHT_ID =
        AmracMod.id("afterburner_light");

    public static final SoundEvent AFTERBURNER_LIGHT = Registry.register(
        BuiltInRegistries.SOUND_EVENT, AFTERBURNER_LIGHT_ID,
        SoundEvent.createFixedRangeEvent(AFTERBURNER_LIGHT_ID,
            AIRCRAFT_AUDIBLE_RANGE));

    public static final Identifier AFTERBURNER_CUT_ID =
        AmracMod.id("afterburner_cut");

    public static final SoundEvent AFTERBURNER_CUT = Registry.register(
        BuiltInRegistries.SOUND_EVENT, AFTERBURNER_CUT_ID,
        SoundEvent.createFixedRangeEvent(AFTERBURNER_CUT_ID,
            AIRCRAFT_AUDIBLE_RANGE));

    public static final Identifier MISSILE_RELEASE_ID =
        AmracMod.id("missile_release");

    public static final SoundEvent MISSILE_RELEASE = Registry.register(
        BuiltInRegistries.SOUND_EVENT, MISSILE_RELEASE_ID,
        SoundEvent.createFixedRangeEvent(MISSILE_RELEASE_ID, 16.0F));

    private AmracSounds() {
    }

    public static void register() {
    }

    public static void playGunShot(Level level, @Nullable Player shooter,
                                   Vec3 muzzle, int shot) {
        level.playSound(shooter, muzzle.x, muzzle.y, muzzle.z,
            GUN_FIRE, SoundSource.PLAYERS,
            SoundMixPolicy.GUN_FIRE_VOLUME, SoundMixPolicy.gunSamplePitch(shot));
    }

    public static void playMissileLaunch(Level level, @Nullable Player pilot,
                                        Vec3 origin) {
        if (pilot instanceof ServerPlayer serverPlayer) {
            playInCockpit(serverPlayer, MISSILE_RELEASE,
                SoundMixPolicy.MISSILE_THUMP_VOLUME);
        }
    }

    public static void playInCockpit(ServerPlayer pilot, SoundEvent event,
                                     float volume) {
        pilot.connection.send(new ClientboundSoundEntityPacket(
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event),
            SoundSource.PLAYERS, pilot, volume, 1.0F,
            pilot.getRandom().nextLong()));
    }
}

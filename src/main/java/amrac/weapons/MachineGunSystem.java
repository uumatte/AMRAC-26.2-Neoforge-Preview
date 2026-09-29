package amrac.weapons;

import net.minecraft.network.chat.Component;
import amrac.AmracSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import amrac.entities.MachineGunBulletEntity;
import amrac.entities.PlaneEntity;
import amrac.upgrades.shooter.MachineGunFirePolicy;
import amrac.upgrades.shooter.MachineGunThermalPolicy;

public final class MachineGunSystem {
    private final PlaneEntity plane;
    private boolean triggerHeld;
    private boolean fireRightSide;
    private int fireCooldownTicks;

    private int shotsFired;
    private float heat;
    private boolean overheated;
    private boolean noAmmoMessageShown;

    public MachineGunSystem(PlaneEntity plane) {
        this.plane = plane;
    }

    public void setTriggerHeld(boolean held, LivingEntity pilot) {
        if (plane.level().isClientSide() ||
            plane.getControllingPassenger() != pilot) {
            return;
        }
        triggerHeld = held;
        if (!held) {
            noAmmoMessageShown = false;
        }
    }

    public void tick() {
        if (plane.level().isClientSide() || !plane.hasMachineGun()) {
            return;
        }

        Entity controller = plane.getControllingPassenger();
        if (overheated) {
            heat = MachineGunThermalPolicy.coolWhileOverheated(heat);
            if (heat <= 0.0F) {
                overheated = false;
            }
            publishState();
            if (overheated) {
                return;
            }
        }

        if (!triggerHeld || !(controller instanceof LivingEntity pilot) ||
            !controller.isAlive()) {
            if (!(controller instanceof LivingEntity) || controller == null ||
                !controller.isAlive()) {
                triggerHeld = false;
            }
            fireCooldownTicks = 0;
            noAmmoMessageShown = false;
            coolWhileReleased();
            return;
        }

        boolean creativeMode = pilot instanceof Player player && player.isCreative();
        if (!MachineGunFirePolicy.hasUsableAmmo(creativeMode,
            plane.getMachineGunAmmoCount())) {
            fireCooldownTicks = 0;
            coolWhileReleased();
            if (pilot instanceof Player player) showNoAmmoMessage(player);
            return;
        }
        noAmmoMessageShown = false;

        if (fireCooldownTicks > 0) {
            --fireCooldownTicks;
            return;
        }
        if (MachineGunFirePolicy.canFire(fireCooldownTicks)) {
            fireCooldownTicks = MachineGunFirePolicy.resetCooldown();
            if (fire(pilot)) {
                heat = MachineGunThermalPolicy.addProjectileHeat(heat);
                overheated = MachineGunThermalPolicy.reachedOverheatLimit(heat);
                publishState();
            }
        }
    }

    private void coolWhileReleased() {
        float cooled = MachineGunThermalPolicy.coolWhileReleased(heat);
        if (cooled != heat) {
            heat = cooled;
            publishState();
        }
    }

    private void publishState() {
        plane.setGunThermalState(heat, overheated);
    }

    private void showNoAmmoMessage(Player player) {
        if (noAmmoMessageShown) {
            return;
        }
        noAmmoMessageShown = true;
        amrac.AmracMod.sendOverlay(player,
            Component.translatable("amrac.message.no_ammo"), true);
    }

    private boolean fire(LivingEntity pilot) {
        if (!amrac.entities.AircraftUpkeepService
                .isTicking(plane)) {
            if (pilot instanceof Player player) {
                amrac.AmracMod.sendOverlay(player,
                    Component.translatable(
                        "amrac.message.gun_unavailable"), true);
            }
            return false;
        }
        boolean creative = pilot instanceof Player player && player.isCreative();
        if (MachineGunFirePolicy.shouldConsumeAmmo(creative) &&
            !plane.consumeMachineGunAmmo()) {
            if (pilot instanceof Player player) showNoAmmoMessage(player);
            return false;
        }

        Vec3 muzzle = muzzlePoint(fireRightSide);
        Vec3 velocity = muzzleVelocity(muzzle);

        MachineGunBulletEntity bullet = new MachineGunBulletEntity(
            plane.level(), pilot, plane, muzzle, velocity,
            MachineGunFirePolicy.DAMAGE_PER_PROJECTILE);
        plane.level().addFreshEntity(bullet);
        AmracSounds.playGunShot(plane.level(),
            pilot instanceof Player player ? player : null, muzzle, ++shotsFired);
        fireRightSide = !fireRightSide;
        return true;
    }

    public Vec3 muzzlePoint(boolean rightSide) {
        return worldPoint(plane.getMachineGunMuzzleSideOffset(rightSide),
            plane.getMachineGunMuzzleHeight(),
            plane.getMachineGunMuzzleForwardOffset());
    }

    public Vec3 muzzleVelocity(Vec3 muzzle) {
        Vec3 convergence = worldPoint(0.0D, plane.getMachineGunMuzzleHeight(),
            plane.getMachineGunConvergenceDistance());
        Vec3 direction = convergence.subtract(muzzle).normalize();
        double speed = MachineGunFirePolicy.muzzleSpeed(
            plane.getMachineGunMuzzleVelocity());
        return direction.scale(speed).add(plane.getDeltaMovement());
    }

    public Vec3 worldPoint(double x, double y, double z) {
        double pivot = plane.getAirframeRotationPivotHeight();
        double lifted = y + amrac.entities.AirframeFrame.GROUND_LIFT * plane.getModelScale();
        org.joml.Vector3f transformed = plane.transformPos(
            new org.joml.Vector3f((float) x, (float) (lifted - pivot), (float) z));
        return plane.position().add(transformed.x(),
            transformed.y() + pivot, transformed.z());
    }

    public float getHeat() {
        return heat;
    }

    public boolean isOverheated() {
        return overheated;
    }

    public void load(float storedHeat, boolean storedOverheated) {
        heat = MachineGunThermalPolicy.sanitizeHeat(storedHeat);
        overheated = (storedOverheated ||
            MachineGunThermalPolicy.reachedOverheatLimit(heat)) && heat > 0.0F;
        publishState();
    }
}

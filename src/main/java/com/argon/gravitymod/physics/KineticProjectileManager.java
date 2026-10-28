package com.argon.gravitymod.physics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages supersonic launched entities: collision tracking, impact damage,
 * block destruction physics, and explosion triggers for The Gravity Gauntlet.
 */
public class KineticProjectileManager {

    private record LaunchTracker(
        UUID entityUuid,
        UUID ownerPlayerUuid,
        Vec3 initialDirection,
        float speed,
        int startTick
    ) {}

    private static final Map<UUID, LaunchTracker> ACTIVE_PROJECTILES = new ConcurrentHashMap<>();
    private static int globalTickCounter = 0;

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            globalTickCounter++;
            if (ACTIVE_PROJECTILES.isEmpty()) {
                return;
            }

            for (ServerLevel level : server.getAllLevels()) {
                tickLevel(level);
            }
        });
    }

    public static void registerLaunchedEntity(Entity entity, ServerPlayer owner, float speed) {
        if (entity == null || owner == null) return;

        Vec3 look = owner.getLookAngle().normalize();
        ACTIVE_PROJECTILES.put(entity.getUUID(), new LaunchTracker(
            entity.getUUID(),
            owner.getUUID(),
            look,
            speed,
            globalTickCounter
        ));
    }

    private static void tickLevel(ServerLevel level) {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, LaunchTracker> entry : ACTIVE_PROJECTILES.entrySet()) {
            LaunchTracker tracker = entry.getValue();
            Entity entity = level.getEntity(tracker.entityUuid());

            // If entity is not in this level, skip
            if (entity == null) {
                continue;
            }

            int age = globalTickCounter - tracker.startTick();

            // Timeout after 3 seconds (60 ticks)
            if (age > 60 || !entity.isAlive() || entity.isRemoved()) {
                toRemove.add(tracker.entityUuid());
                continue;
            }

            Vec3 vel = entity.getDeltaMovement();

            // Spurt kinetic trail particles during flight
            level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                2, 0.1, 0.1, 0.1, 0.02
            );

            // Collision check with other entities along movement ray
            AABB flightBox = entity.getBoundingBox().expandTowards(vel).inflate(0.5);
            List<Entity> hitEntities = level.getEntities(entity, flightBox, e ->
                e.isAlive() && !e.getUUID().equals(tracker.ownerPlayerUuid()) && !e.getUUID().equals(entity.getUUID())
            );

            boolean collidedWithEntity = false;
            float kineticDamage = (float) (Math.max(14.0, tracker.speed() * 8.5));

            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(tracker.ownerPlayerUuid());

            if (!hitEntities.isEmpty()) {
                collidedWithEntity = true;
                for (Entity hit : hitEntities) {
                    if (ownerPlayer != null) {
                        hit.hurt(level.damageSources().playerAttack(ownerPlayer), kineticDamage);
                    } else {
                        hit.hurt(level.damageSources().generic(), kineticDamage);
                    }

                    // Knockback hit entity
                    Vec3 knock = tracker.initialDirection().scale(1.8).add(0, 0.35, 0);
                    hit.setDeltaMovement(hit.getDeltaMovement().add(knock));
                }
            }

            // Check block collision (hit wall or ground)
            boolean collidedWithWorld = entity.horizontalCollision || entity.verticalCollision;

            if (collidedWithEntity || (collidedWithWorld && age > 1)) {
                // Trigger Massive Kinetic Impact!
                triggerKineticImpact(level, entity, tracker, kineticDamage, ownerPlayer);
                toRemove.add(tracker.entityUuid());
            }
        }

        toRemove.forEach(ACTIVE_PROJECTILES::remove);
    }

    private static void triggerKineticImpact(ServerLevel level, Entity projectile, LaunchTracker tracker, float damage, ServerPlayer owner) {
        double px = projectile.getX();
        double py = projectile.getY() + projectile.getBbHeight() * 0.5;
        double pz = projectile.getZ();

        // 1. Audio: Heavy Mace Smash + Explosion
        level.playSound(null, px, py, pz, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 2.0f, 0.85f);
        level.playSound(null, px, py, pz, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.4f);

        // 2. Visual Effects: Shockwave burst
        level.sendParticles(ParticleTypes.EXPLOSION, px, py, pz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SONIC_BOOM, px, py, pz, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, px, py, pz, 25, 0.5, 0.5, 0.5, 0.2);

        // 3. Radius Impact on nearby entities
        AABB blastArea = projectile.getBoundingBox().inflate(3.0);
        List<Entity> surrounding = level.getEntities(projectile, blastArea, e ->
            e.isAlive() && !e.getUUID().equals(tracker.ownerPlayerUuid()) && !e.getUUID().equals(projectile.getUUID())
        );

        for (Entity victim : surrounding) {
            double dist = victim.distanceTo(projectile);
            float falloff = (float) Math.max(0.2, 1.0 - (dist / 3.5));
            float victimDamage = damage * falloff * 0.65f;

            if (owner != null) {
                victim.hurt(level.damageSources().playerAttack(owner), victimDamage);
            } else {
                victim.hurt(level.damageSources().generic(), victimDamage);
            }

            Vec3 push = victim.position().subtract(projectile.position()).normalize().scale(1.2 * falloff).add(0, 0.3, 0);
            victim.setDeltaMovement(victim.getDeltaMovement().add(push));
        }

        // 4. Special interactions: TNT & Anvil / Falling Blocks
        if (projectile instanceof PrimedTnt tnt) {
            // Instant detonation!
            level.explode(owner, px, py, pz, 4.0f, Level.ExplosionInteraction.BLOCK);
            tnt.discard();
        } else if (projectile instanceof FallingBlockEntity fallingBlock) {
            // Block smash and particulate disintegration
            BlockPos impactPos = BlockPos.containing(px, py, pz);
            level.levelEvent(2001, impactPos, Block.getId(fallingBlock.getBlockState()));
            level.playSound(null, px, py, pz, SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS, 1.4f, 0.95f);
            fallingBlock.discard();
        } else if (projectile instanceof LivingEntity living) {
            // Self-damage to the projectile from the devastating impact
            living.hurt(level.damageSources().flyIntoWall(), damage * 0.8f);
        }
    }
}

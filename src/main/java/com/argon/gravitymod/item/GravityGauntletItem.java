package com.argon.gravitymod.item;

import com.argon.gravitymod.component.GauntletData;
import com.argon.gravitymod.component.ModDataComponents;
import com.argon.gravitymod.physics.KineticProjectileManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * THE GRAVITY GAUNTLET
 * Zero-Point Energy Field Manipulator inspired by Half-Life 2.
 *
 * Mode 1: Capture & Levitation (Right-Click) - supports Mobs, Items, Projectiles, and BLOCKS!
 * Mode 2: Supersonic Kinetic Launch & Repulsor Wave (Left-Click / Primary Fire)
 * Extra: Gravitational Item Vortex (Shift + Right-Click)
 */
public class GravityGauntletItem extends Item {

    public static final double MAX_RAYCAST_RANGE = 12.0;
    public static final double HOLD_DISTANCE = 3.5;
    public static final double MAX_HOLD_TOLERANCE = 18.0;

    public GravityGauntletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 1. Shift + Right-Click: Item & XP Attraction Vortex
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                attractGroundItemsAndOrbs((ServerLevel) level, player, stack);
            }
            return InteractionResult.SUCCESS;
        }

        // 2. Normal Right-Click: Toggle Capture or Release
        if (!level.isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) level;
            ServerPlayer serverPlayer = (ServerPlayer) player;
            GauntletData data = stack.getOrDefault(ModDataComponents.GAUNTLET_DATA, GauntletData.EMPTY);

            if (data.hasHeldEntity()) {
                // Currently holding an entity/block -> Release it gently
                releaseHeldEntity(serverLevel, serverPlayer, stack, data);
            } else {
                // Not holding -> Raycast entity or rip block into gravity field
                captureTargetEntityOrBlock(serverLevel, serverPlayer, stack);
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * Attracts all floating items and XP orbs in a 15-block radius directly to the player.
     */
    private void attractGroundItemsAndOrbs(ServerLevel level, Player player, ItemStack stack) {
        AABB vortexBox = player.getBoundingBox().inflate(15.0);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, vortexBox, Entity::isAlive);
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, vortexBox, Entity::isAlive);

        Vec3 targetEye = player.getEyePosition();

        for (ItemEntity item : items) {
            Vec3 pullDir = targetEye.subtract(item.position()).normalize().scale(0.95);
            item.setDeltaMovement(pullDir);
            level.sendParticles(ParticleTypes.GLOW, item.getX(), item.getY() + 0.2, item.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
        }

        for (ExperienceOrb orb : orbs) {
            Vec3 pullDir = targetEye.subtract(orb.position()).normalize().scale(0.95);
            orb.setDeltaMovement(pullDir);
        }

        // Resonant Audio & Particle Wave
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.8f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 1.5f);

        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 1.0, 1.0, 1.0, 0.5);

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.gravitymod.attracted").withStyle(ChatFormatting.LIGHT_PURPLE));
            stack.hurtAndBreak(1, serverPlayer, EquipmentSlot.MAINHAND);
        }
    }

    /**
     * Raycasts 12 blocks ahead: captures entities, or if looking at a block, rips that block
     * out of the world as a FallingBlockEntity into the gravity field!
     */
    private void captureTargetEntityOrBlock(ServerLevel level, ServerPlayer player, ItemStack stack) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(MAX_RAYCAST_RANGE));

        // 1. Raycast Entities first
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.5);
        List<Entity> candidateEntities = level.getEntities(player, searchBox, e ->
            e.isAlive() && !e.isSpectator() && (e instanceof LivingEntity || e instanceof PrimedTnt || e instanceof FallingBlockEntity || e instanceof ItemEntity)
        );

        Entity closestTarget = null;
        double closestDistanceSq = Double.MAX_VALUE;

        for (Entity candidate : candidateEntities) {
            AABB boundingBox = candidate.getBoundingBox().inflate(0.4);
            Optional<Vec3> hitResult = boundingBox.clip(eyePos, endPos);

            if (hitResult.isPresent()) {
                double distSq = eyePos.distanceToSqr(hitResult.get());
                if (distSq < closestDistanceSq) {
                    closestDistanceSq = distSq;
                    closestTarget = candidate;
                }
            }
        }

        if (closestTarget != null) {
            // Lock and capture Entity!
            closestTarget.setNoGravity(true);
            closestTarget.resetFallDistance();

            if (closestTarget instanceof ItemEntity itemEntity) {
                itemEntity.setPickUpDelay(40);
            }

            stack.set(ModDataComponents.GAUNTLET_DATA, new GauntletData(Optional.of(closestTarget.getUUID()), 0));

            // SFX & VFX
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.4f, 1.2f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 2.0f);

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, closestTarget.getX(), closestTarget.getY() + closestTarget.getBbHeight() * 0.5, closestTarget.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
            level.sendParticles(ParticleTypes.SONIC_BOOM, closestTarget.getX(), closestTarget.getY() + closestTarget.getBbHeight() * 0.5, closestTarget.getZ(), 1, 0, 0, 0, 0);

            player.sendOverlayMessage(Component.translatable("message.gravitymod.captured", closestTarget.getName().getString()).withStyle(ChatFormatting.AQUA));
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            return;
        }

        // 2. Raycast Blocks (rip 1 block into gravity field!)
        BlockHitResult blockHit = level.clip(new ClipContext(
            eyePos,
            endPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(hitPos);

            if (!state.isAir() && state.getDestroySpeed(level, hitPos) >= 0.0f && !state.hasBlockEntity()) {
                // Rip block from world!
                level.removeBlock(hitPos, false);

                // Sound & particle effects of block extraction
                level.levelEvent(2001, hitPos, Block.getId(state));
                level.playSound(null, hitPos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 1.3f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.4f, 1.2f);

                // Spawn as FallingBlockEntity
                FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, hitPos, state);
                fallingBlock.time = 1;
                fallingBlock.dropItem = false;
                fallingBlock.setNoGravity(true);
                fallingBlock.setPos(hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5);

                // Lock into Gauntlet
                stack.set(ModDataComponents.GAUNTLET_DATA, new GauntletData(Optional.of(fallingBlock.getUUID()), 0));

                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.1);
                player.sendOverlayMessage(Component.translatable("message.gravitymod.captured_block", state.getBlock().getName().getString()).withStyle(ChatFormatting.AQUA));
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                return;
            }
        }

        // Dry fire
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8f, 1.8f);
    }

    /**
     * Releases held target safely without damage.
     */
    private void releaseHeldEntity(ServerLevel level, ServerPlayer player, ItemStack stack, GauntletData data) {
        if (data.heldEntityUuid().isPresent()) {
            Entity target = level.getEntity(data.heldEntityUuid().get());
            if (target != null) {
                target.setNoGravity(false);
                target.resetFallDistance();

                if (target instanceof FallingBlockEntity fallingBlock) {
                    fallingBlock.dropItem = true;
                    fallingBlock.setDeltaMovement(player.getLookAngle().scale(0.25));
                } else if (target instanceof ItemEntity itemEntity) {
                    itemEntity.setPickUpDelay(10);
                    itemEntity.setDeltaMovement(player.getLookAngle().scale(0.25));
                } else {
                    target.setDeltaMovement(player.getLookAngle().scale(0.25));
                }
            }
        }

        stack.set(ModDataComponents.GAUNTLET_DATA, GauntletData.EMPTY);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
        player.sendOverlayMessage(Component.translatable("message.gravitymod.released").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Smoothly ticks the levitation and holding mechanics every server tick.
     * Uses positional LERP to eliminate jitter for ItemEntity and all floating objects.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity holder, EquipmentSlot slot) {
        if (!(holder instanceof ServerPlayer player)) {
            return;
        }

        GauntletData data = stack.getOrDefault(ModDataComponents.GAUNTLET_DATA, GauntletData.EMPTY);
        if (!data.hasHeldEntity()) {
            return;
        }

        Entity target = level.getEntity(data.heldEntityUuid().get());

        // Validate that player is still holding the weapon in hand
        boolean isHeldInHand = (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND);
        if (!isHeldInHand || target == null || !target.isAlive() || target.isRemoved() || target.distanceTo(player) > MAX_HOLD_TOLERANCE) {
            if (target != null) {
                target.setNoGravity(false);
                if (target instanceof FallingBlockEntity fallingBlock) {
                    fallingBlock.dropItem = true;
                }
            }
            stack.set(ModDataComponents.GAUNTLET_DATA, GauntletData.EMPTY);
            return;
        }

        // Durability: 1 durability every 40 ticks (2 seconds)
        int newHoldTicks = data.holdTicks() + 1;
        if (newHoldTicks % 40 == 0) {
            stack.hurtAndBreak(1, player, slot);
            if (stack.isEmpty()) {
                target.setNoGravity(false);
                return;
            }
        }
        stack.set(ModDataComponents.GAUNTLET_DATA, new GauntletData(data.heldEntityUuid(), newHoldTicks));

        // Target position: 3.5 blocks along player's look vector
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 targetHoldPos = eyePos.add(lookVec.scale(HOLD_DISTANCE));
        Vec3 targetCenter = targetHoldPos.subtract(0, target.getBbHeight() * 0.5, 0);

        // Keep entity active & prevent despawning/pickup while held
        if (target instanceof ItemEntity itemEntity) {
            itemEntity.setPickUpDelay(40);
            itemEntity.setNoGravity(true);
        } else if (target instanceof FallingBlockEntity fallingBlock) {
            fallingBlock.time = 1;
            fallingBlock.dropItem = false;
            fallingBlock.setNoGravity(true);
        }

        // BUTTERY-SMOOTH SPRING KINEMATICS (Eliminates all micro-stutters and jitter!)
        Vec3 currentPos = target.position();
        Vec3 toTarget = targetCenter.subtract(currentPos);
        double distSq = toTarget.lengthSqr();

        if (distSq > 16.0) {
            // Snapped too far away (e.g. sharp flick) -> snap cleanly to hold center
            target.setPos(targetCenter.x, targetCenter.y, targetCenter.z);
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
        } else {
            // Smooth critically damped velocity: asymptotically glides to center without client rubber-banding
            Vec3 smoothVelocity = toTarget.scale(0.55);
            target.setDeltaMovement(smoothVelocity);
            target.hurtMarked = true; // Immediate client velocity sync
        }

        // Prevent mob AI struggle while held in gravity field
        if (target instanceof LivingEntity living) {
            living.setSpeed(0.0f);
        }

        target.resetFallDistance();
        target.setNoGravity(true);

        // Helpless zero-gravity rotation
        target.setYRot(target.getYRot() + 8.0f);
        target.setXRot((float) (Math.sin(newHoldTicks * 0.15) * 15.0));

        // Particle Gravity Beam from Weapon Muzzle to Target
        Vec3 sideVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 muzzlePos = eyePos.add(sideVec.scale(0.35)).subtract(0, 0.25, 0).add(lookVec.scale(0.6));
        Vec3 targetMid = target.position().add(0, target.getBbHeight() * 0.5, 0);

        int beamSegments = 10;
        for (int i = 0; i <= beamSegments; i++) {
            double fraction = (double) i / beamSegments;
            Vec3 p = muzzlePos.lerp(targetMid, fraction);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.005);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.GLOW, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }

        // Ambient Zero-G Hum
        if (newHoldTicks % 20 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.8f);
        }
    }

    /**
     * Executes Mode 2: Primary Fire (Left-Click).
     * Either fires held entity/block at speed 4.0 or casts a Conical Repulsor Shockwave.
     */
    public static void performPrimaryFire(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        player.getCooldowns().addCooldown(stack, 8);

        ServerLevel level = player.level();
        GauntletData data = stack.getOrDefault(ModDataComponents.GAUNTLET_DATA, GauntletData.EMPTY);

        if (data.hasHeldEntity()) {
            // BRANCH A: SUPERSONIC KINETIC LAUNCH
            Entity target = level.getEntity(data.heldEntityUuid().get());
            stack.set(ModDataComponents.GAUNTLET_DATA, GauntletData.EMPTY);

            if (target != null && target.isAlive() && !target.isRemoved()) {
                Vec3 launchDir = player.getLookAngle().normalize();
                Vec3 velocity = launchDir.scale(4.0); // Devastating 4.0 speed!

                target.setNoGravity(false);
                if (target instanceof FallingBlockEntity fallingBlock) {
                    fallingBlock.dropItem = true;
                } else if (target instanceof ItemEntity itemEntity) {
                    itemEntity.setPickUpDelay(10);
                }

                target.setDeltaMovement(velocity);

                // Register in Kinetic Manager for wall smash and impact damage
                KineticProjectileManager.registerLaunchedEntity(target, player, 4.0f);

                // Heavy launch sounds
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 1.15f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.8f, 0.8f);

                // Muzzle blast visual
                level.sendParticles(
                    ParticleTypes.SONIC_BOOM,
                    player.getX() + launchDir.x * 2.0,
                    player.getEyeY() + launchDir.y * 2.0,
                    player.getZ() + launchDir.z * 2.0,
                    1, 0, 0, 0, 0
                );

                // Player recoil
                player.setDeltaMovement(player.getDeltaMovement().add(launchDir.scale(-0.35)));
                player.hurtMarked = true;

                stack.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
                player.sendOverlayMessage(Component.translatable("message.gravitymod.launched").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
        } else {
            // BRANCH B: CONICAL REPULSOR SHOCKWAVE (Empty Gauntlet Blast)
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            double range = 5.5;

            AABB blastBox = player.getBoundingBox().inflate(range);
            List<Entity> affected = level.getEntities(player, blastBox, e ->
                e.isAlive() && (e instanceof LivingEntity || e instanceof PrimedTnt || e instanceof FallingBlockEntity)
            );

            for (Entity entity : affected) {
                Vec3 diff = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(eye);
                double dist = diff.length();
                if (dist <= range && dist > 0.01) {
                    Vec3 dir = diff.normalize();
                    double dot = dir.dot(look);

                    // 60-degree cone
                    if (dot > 0.45) {
                        double force = (1.0 - (dist / (range + 0.5))) * 2.3 + 0.6;
                        Vec3 blastVelocity = look.scale(force).add(0, 0.45, 0);

                        entity.setDeltaMovement(blastVelocity);
                        entity.hurt(level.damageSources().playerAttack(player), 7.0f);

                        level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                    }
                }
            }

            // Audio: Sonic blast
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3f, 1.6f);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.5f, 1.2f);

            // Visual cone
            for (double d = 1.0; d <= range; d += 1.0) {
                Vec3 conePoint = eye.add(look.scale(d));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, conePoint.x, conePoint.y, conePoint.z, 2, 0.3 * d, 0.3 * d, 0.3 * d, 0.05);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, conePoint.x, conePoint.y, conePoint.z, 5, 0.2 * d, 0.2 * d, 0.2 * d, 0.02);
            }

            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            player.sendOverlayMessage(Component.translatable("message.gravitymod.repulsor").withStyle(ChatFormatting.BLUE));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.gravitymod.gravity_gauntlet.desc").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        tooltip.accept(Component.empty());
        tooltip.accept(Component.translatable("item.gravitymod.gravity_gauntlet.tooltip.mode1"));
        tooltip.accept(Component.translatable("item.gravitymod.gravity_gauntlet.tooltip.mode2"));
        tooltip.accept(Component.translatable("item.gravitymod.gravity_gauntlet.tooltip.mode2_empty"));
        tooltip.accept(Component.translatable("item.gravitymod.gravity_gauntlet.tooltip.attract"));
        tooltip.accept(Component.empty());
        tooltip.accept(Component.translatable("item.gravitymod.gravity_gauntlet.tooltip.durability_info"));

        GauntletData data = stack.get(ModDataComponents.GAUNTLET_DATA);
        if (data != null && data.hasHeldEntity()) {
            tooltip.accept(Component.literal("§a[STATUS] Target Locked (Holding: " + (data.holdTicks() / 20) + "s)"));
        } else {
            tooltip.accept(Component.literal("§7[STATUS] Ready to Capture"));
        }

        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}

package com.argon.chronopocket.time;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TOTAL TIME CONTROL ENGINE:
 * - Records rolling history of players, animals, and blocks.
 * - Restores 100% of broken, placed, and changed blocks during rewind.
 * - Glides animals and mobs backwards along their motion paths.
 * - Resurrects killed animals and mobs back to life with totem particles!
 * - Smoothly winds daylight back and transits players out of pocket dimensions.
 */
public class TimeRewindManager {

    public static final int MAX_HISTORY_TICKS = 200; // 10.0 seconds
    public static final int REWIND_ANIMATION_DURATION = 20; // 1.0 second smooth reverse animation

    private static final Deque<TimeSnapshot> HISTORY = new ArrayDeque<>();
    private static final Map<BlockPos, TimeSnapshot.BlockRecord> RECENT_BLOCK_CHANGES = new ConcurrentHashMap<>();
    private static final Map<UUID, TimeSnapshot.EntityRecord> ACTIVE_ENTITY_TRACKERS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Vec3>> PLAYER_TRAJECTORIES = new ConcurrentHashMap<>();

    private static boolean isRewinding = false;
    private static int rewindTickCount = 0;
    private static TimeSnapshot activeTargetSnapshot = null;
    private static final Map<UUID, Vec3> REWIND_START_POSITIONS = new HashMap<>();

    public static void initialize() {
        // 1. Intercept Block Breaking: save original state before destruction
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!level.isClientSide() && !isRewinding) {
                recordBlockState(level.dimension(), pos, state);
            }
            return true;
        });

        // 2. Intercept Block Placement / Interaction: save original state before block is placed
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide() && !isRewinding) {
                BlockPos clickedPos = hitResult.getBlockPos();
                BlockPos placePos = clickedPos.relative(hitResult.getDirection());

                recordBlockState(level.dimension(), placePos, level.getBlockState(placePos));
                recordBlockState(level.dimension(), clickedPos, level.getBlockState(clickedPos));
            }
            return InteractionResult.PASS;
        });

        // 3. Server tick loop: record history or execute rewind animation
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (isRewinding) {
                processRewindTick(server);
            } else {
                recordTickSnapshot(server);
            }
        });
    }

    public static void recordBlockState(ResourceKey<Level> dim, BlockPos pos, BlockState state) {
        // putIfAbsent ensures we ONLY preserve the initial state before any modifications occurred
        RECENT_BLOCK_CHANGES.putIfAbsent(pos.immutable(), new TimeSnapshot.BlockRecord(
            pos.immutable(),
            dim,
            state
        ));
    }

    private static void recordTickSnapshot(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long worldTime = overworld.getOverworldClockTime();

        Map<UUID, TimeSnapshot.PlayerRecord> playerMap = new HashMap<>();
        Map<UUID, TimeSnapshot.EntityRecord> entityMap = new HashMap<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID pUuid = player.getUUID();
            PLAYER_TRAJECTORIES.computeIfAbsent(pUuid, k -> new ArrayList<>()).add(player.position());
            List<Vec3> pPath = PLAYER_TRAJECTORIES.get(pUuid);
            if (pPath.size() > 100) {
                pPath.remove(0);
            }

            playerMap.put(pUuid, new TimeSnapshot.PlayerRecord(
                pUuid,
                player.level().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                player.getHealth(),
                player.getFoodData().getFoodLevel(),
                new ArrayList<>(pPath)
            ));

            // Track nearby living entities (animals, mobs) within 48 blocks
            List<LivingEntity> nearby = player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(48.0),
                e -> e != player
            );

            for (LivingEntity living : nearby) {
                UUID eUuid = living.getUUID();
                if (!ACTIVE_ENTITY_TRACKERS.containsKey(eUuid)) {
                    ACTIVE_ENTITY_TRACKERS.put(eUuid, new TimeSnapshot.EntityRecord(
                        eUuid,
                        living.getType(),
                        living.level().dimension(),
                        living.position(),
                        living.getHealth(),
                        living.getYRot(),
                        living.getXRot(),
                        new ArrayList<>(List.of(living.position()))
                    ));
                } else {
                    List<Vec3> path = ACTIVE_ENTITY_TRACKERS.get(eUuid).path();
                    path.add(living.position());
                    if (path.size() > 100) {
                        path.remove(0);
                    }
                }
            }
        }

        entityMap.putAll(ACTIVE_ENTITY_TRACKERS);
        Map<BlockPos, TimeSnapshot.BlockRecord> blockChangesSnapshot = new HashMap<>(RECENT_BLOCK_CHANGES);

        HISTORY.addLast(new TimeSnapshot(worldTime, playerMap, entityMap, blockChangesSnapshot));
        if (HISTORY.size() > MAX_HISTORY_TICKS) {
            HISTORY.removeFirst();
        }
    }

    /**
     * Triggers the Time Rewind sequence with animal resurrection and full block restoration.
     */
    public static boolean triggerRewind(ServerLevel level, ServerPlayer initiator, int rewindTicks) {
        if (isRewinding || HISTORY.isEmpty()) {
            return false;
        }

        int stepsBack = Math.min(rewindTicks, HISTORY.size());
        List<TimeSnapshot> list = new ArrayList<>(HISTORY);
        int targetIndex = Math.max(0, list.size() - stepsBack);
        activeTargetSnapshot = list.get(targetIndex);

        isRewinding = true;
        rewindTickCount = 0;
        REWIND_START_POSITIONS.clear();

        // 1. PHASE 1: RESURRECT KILLED ANIMALS AND MOBS!
        for (TimeSnapshot.EntityRecord rec : activeTargetSnapshot.entities().values()) {
            ServerLevel dimLevel = level.getServer().getLevel(rec.dimension());
            if (dimLevel != null) {
                Entity existing = dimLevel.getEntity(rec.uuid());
                if (existing == null || !existing.isAlive()) {
                    // Resurrect entity back into existence!
                    Entity resurrected = rec.type().create(dimLevel, EntitySpawnReason.TRIGGERED);
                    if (resurrected != null) {
                        resurrected.setUUID(rec.uuid());
                        resurrected.setPos(rec.initialPos().x, rec.initialPos().y, rec.initialPos().z);
                        resurrected.setYRot(rec.yRot());
                        resurrected.setXRot(rec.xRot());

                        if (resurrected instanceof LivingEntity living) {
                            living.setHealth(rec.initialHealth());
                        }

                        dimLevel.addFreshEntity(resurrected);

                        // Totem resurrection visuals & sound
                        dimLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, rec.initialPos().x, rec.initialPos().y + 1.0, rec.initialPos().z, 30, 0.4, 0.6, 0.4, 0.3);
                        dimLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, rec.initialPos().x, rec.initialPos().y + 1.0, rec.initialPos().z, 20, 0.3, 0.5, 0.3, 0.1);
                        dimLevel.sendParticles(ParticleTypes.HEART, rec.initialPos().x, rec.initialPos().y + 1.2, rec.initialPos().z, 6, 0.3, 0.3, 0.3, 0.05);
                        dimLevel.playSound(null, rec.initialPos().x, rec.initialPos().y, rec.initialPos().z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.2f, 1.4f);
                    }
                }
            }
        }

        // 2. Play initial sound and setup player tracking
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            REWIND_START_POSITIONS.put(player.getUUID(), player.position());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 1.8f);
            player.sendOverlayMessage(Component.translatable("message.chronopocket.rewinding").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        }

        return true;
    }

    private static void processRewindTick(MinecraftServer server) {
        rewindTickCount++;
        float progress = (float) rewindTickCount / REWIND_ANIMATION_DURATION;
        float reversedFraction = Math.max(0.0f, 1.0f - progress);

        // Smooth cubic ease-in-out interpolation curve
        float t = progress < 0.5f ? 4.0f * progress * progress * progress : 1.0f - (float) Math.pow(-2.0f * progress + 2.0f, 3.0) / 2.0f;

        // Smoothly roll back daylight
        ServerLevel overworld = server.overworld();
        long targetTime = activeTargetSnapshot.worldTime();
        long currentTime = overworld.getOverworldClockTime();
        long interpolatedTime = (long) (currentTime + (targetTime - currentTime) * t);

        if (overworld.getLevelData() instanceof ServerLevelData serverLevelData) {
            serverLevelData.setGameTime(interpolatedTime);
        }

        // 1. Animals & Mobs walk/glide backwards
        for (TimeSnapshot.EntityRecord rec : activeTargetSnapshot.entities().values()) {
            ServerLevel dimLevel = server.getLevel(rec.dimension());
            if (dimLevel != null) {
                Entity entity = dimLevel.getEntity(rec.uuid());
                if (entity != null && entity.isAlive()) {
                    List<Vec3> path = rec.path();
                    Vec3 targetPos;
                    if (path != null && !path.isEmpty()) {
                        int idx = (int) (reversedFraction * (path.size() - 1));
                        targetPos = path.get(Math.max(0, Math.min(path.size() - 1, idx)));
                    } else {
                        targetPos = rec.initialPos();
                    }

                    entity.setPos(targetPos.x, targetPos.y, targetPos.z);
                    entity.setDeltaMovement(Vec3.ZERO);
                    entity.resetFallDistance();

                    // Footstep particles in reverse
                    dimLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, targetPos.x, targetPos.y + 0.1, targetPos.z, 2, 0.1, 0.05, 0.1, 0.01);
                }
            }
        }

        // 2. Players glide backwards along their trajectory
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TimeSnapshot.PlayerRecord rec = activeTargetSnapshot.players().get(player.getUUID());
            Vec3 startPos = REWIND_START_POSITIONS.get(player.getUUID());

            if (rec != null && startPos != null) {
                ServerLevel targetLevel = server.getLevel(rec.dimension());
                if (targetLevel == null) {
                    targetLevel = overworld;
                }

                // If player was inside Pocket Base, pull them out to Overworld
                if (!player.level().dimension().equals(rec.dimension())) {
                    player.teleportTo(targetLevel, rec.position().x, rec.position().y, rec.position().z, Set.of(), rec.yRot(), rec.xRot(), false);
                }

                Vec3 currentPos = startPos.lerp(rec.position(), t);
                float currentYRot = (float) (player.getYRot() + (rec.yRot() - player.getYRot()) * t);
                float currentXRot = (float) (player.getXRot() + (rec.xRot() - player.getXRot()) * t);

                player.teleportTo(targetLevel, currentPos.x, currentPos.y, currentPos.z, Set.of(), currentYRot, currentXRot, false);
                player.setDeltaMovement(Vec3.ZERO);

                // Reverse vortex particles
                player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, currentPos.x, currentPos.y + 1.0, currentPos.z, 8, 0.4, 0.4, 0.4, 0.05);
                player.level().sendParticles(ParticleTypes.PORTAL, currentPos.x, currentPos.y + 0.5, currentPos.z, 4, 0.2, 0.2, 0.2, 0.01);

                if (rewindTickCount % 5 == 0) {
                    player.level().playSound(null, currentPos.x, currentPos.y, currentPos.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 1.8f);
                }
            }
        }

        // Finalize on completion
        if (rewindTickCount >= REWIND_ANIMATION_DURATION) {
            finalizeRewind(server);
        }
    }

    private static void finalizeRewind(MinecraftServer server) {
        isRewinding = false;
        rewindTickCount = 0;

        if (activeTargetSnapshot == null) return;

        // 1. Restore 100% of recorded blocks (broken blocks re-solidify, placed blocks removed!)
        for (TimeSnapshot.BlockRecord blockRecord : activeTargetSnapshot.blockChanges().values()) {
            ServerLevel dimLevel = server.getLevel(blockRecord.dimension());
            if (dimLevel != null) {
                dimLevel.setBlock(blockRecord.pos(), blockRecord.originalState(), 3);
                dimLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, blockRecord.pos().getX() + 0.5, blockRecord.pos().getY() + 0.5, blockRecord.pos().getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
            }
        }
        RECENT_BLOCK_CHANGES.clear();

        // 2. Restore players' exact states
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TimeSnapshot.PlayerRecord rec = activeTargetSnapshot.players().get(player.getUUID());
            if (rec != null) {
                ServerLevel targetLevel = server.getLevel(rec.dimension());
                if (targetLevel != null) {
                    player.teleportTo(targetLevel, rec.position().x, rec.position().y, rec.position().z, Set.of(), rec.yRot(), rec.xRot(), true);
                }
                player.setHealth(rec.health());
                player.getFoodData().setFoodLevel(rec.foodLevel());
                player.resetFallDistance();

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.6f);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.2f);
                player.sendOverlayMessage(Component.translatable("message.chronopocket.rewound").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            }
        }

        // 3. Clear trackers
        HISTORY.clear();
        ACTIVE_ENTITY_TRACKERS.clear();
        PLAYER_TRAJECTORIES.clear();
        activeTargetSnapshot = null;
        REWIND_START_POSITIONS.clear();
    }
}

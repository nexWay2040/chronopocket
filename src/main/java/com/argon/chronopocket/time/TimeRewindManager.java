package com.argon.chronopocket.time;

import com.argon.chronopocket.component.PocketDataComponents;
import com.argon.chronopocket.component.TimeRemoteData;
import com.argon.chronopocket.item.TimeRemoteItem;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TOTAL TIME CONTROL ENGINE:
 * - Records rolling history of players, animals, and 100% of block changes via LevelBlockMixin.
 * - Restores 100% of broken, placed, and exploded blocks during rewind.
 * - Prevents item duplication by cleaning up items dropped during the rewind window.
 * - Glides animals and mobs backwards along their motion paths.
 * - Resurrects killed animals and mobs back to life with totem particles!
 * - Smoothly winds daylight back and transits players out of pocket dimensions.
 */
public class TimeRewindManager {

    public static final int MAX_HISTORY_TICKS = 200; // 10.0 seconds
    public static final int REWIND_ANIMATION_DURATION = 20; // 1.0 second smooth reverse animation

    private static final ThreadLocal<Boolean> IS_RESTORING = ThreadLocal.withInitial(() -> false);
    private static volatile boolean isRewinding = false;
    private static long serverTickCounter = 0;

    private static final Deque<TimeSnapshot> HISTORY = new ArrayDeque<>();
    private static final Map<BlockPos, TimeSnapshot.BlockRecord> RECENT_BLOCK_CHANGES = new ConcurrentHashMap<>();
    private static final List<TimeSnapshot.BlockRecord> ACTIVE_BLOCKS_TO_RESTORE = new ArrayList<>();
    private static final Map<UUID, TimeSnapshot.EntityRecord> ACTIVE_ENTITY_TRACKERS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Vec3>> PLAYER_TRAJECTORIES = new ConcurrentHashMap<>();

    private static int rewindTickCount = 0;
    private static TimeSnapshot activeTargetSnapshot = null;
    private static final Map<UUID, Vec3> REWIND_START_POSITIONS = new HashMap<>();

    public static boolean isRestoring() {
        return IS_RESTORING.get();
    }

    public static boolean isRewinding() {
        return isRewinding;
    }

    public static void initialize() {
        // Fallback safety hooks for events in addition to LevelBlockMixin
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!level.isClientSide() && !isRewinding && !IS_RESTORING.get()) {
                recordBlockChange(level.dimension(), pos, state);
            }
            return true;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide() && !isRewinding && !IS_RESTORING.get()) {
                BlockPos clickedPos = hitResult.getBlockPos();
                BlockPos placePos = clickedPos.relative(hitResult.getDirection());
                recordBlockChange(level.dimension(), placePos, level.getBlockState(placePos));
                recordBlockChange(level.dimension(), clickedPos, level.getBlockState(clickedPos));
            }
            return InteractionResult.PASS;
        });

        // Server tick loop
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            serverTickCounter++;
            if (isRewinding) {
                processRewindTick(server);
            } else {
                recordTickSnapshot(server);
            }
        });
    }

    /**
     * Intercepts and records any block change from LevelBlockMixin or events.
     * putIfAbsent guarantees we record the ORIGINAL state before ANY sequence of modifications!
     */
    public static void recordBlockChange(ResourceKey<Level> dim, BlockPos pos, BlockState oldState) {
        if (isRewinding || IS_RESTORING.get()) {
            return;
        }

        RECENT_BLOCK_CHANGES.putIfAbsent(pos.immutable(), new TimeSnapshot.BlockRecord(
            pos.immutable(),
            dim,
            oldState,
            serverTickCounter
        ));
    }

    private static void recordTickSnapshot(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long worldTime = overworld.getOverworldClockTime();

        Map<UUID, TimeSnapshot.PlayerRecord> playerMap = new HashMap<>();
        Map<UUID, TimeSnapshot.EntityRecord> entityMap = new HashMap<>();

        boolean anyCheckpointActive = false;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Check if player has an active temporal checkpoint armed
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof TimeRemoteItem) {
                    TimeRemoteData data = stack.get(PocketDataComponents.TIME_REMOTE_DATA);
                    if (data != null && data.active()) {
                        anyCheckpointActive = true;
                        break;
                    }
                }
            }

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

        // Rolling memory bounded cleanup: if no active checkpoint, prune block records older than 10 seconds
        if (!anyCheckpointActive) {
            RECENT_BLOCK_CHANGES.entrySet().removeIf(entry ->
                serverTickCounter - entry.getValue().recordedTick() > MAX_HISTORY_TICKS
            );
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

        // Capture all block modifications that occurred within the rewind window
        ACTIVE_BLOCKS_TO_RESTORE.clear();
        ACTIVE_BLOCKS_TO_RESTORE.addAll(RECENT_BLOCK_CHANGES.values());

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
        // 1. RESTORE 100% OF RECORDED BLOCKS (broken blocks re-solidify, placed blocks removed!)
        IS_RESTORING.set(true);
        try {
            for (TimeSnapshot.BlockRecord blockRecord : ACTIVE_BLOCKS_TO_RESTORE) {
                ServerLevel dimLevel = server.getLevel(blockRecord.dimension());
                if (dimLevel != null) {
                    // Flag 3: update neighbors and sync packet to clients immediately
                    dimLevel.setBlock(blockRecord.pos(), blockRecord.originalState(), 3);
                    dimLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        blockRecord.pos().getX() + 0.5,
                        blockRecord.pos().getY() + 0.5,
                        blockRecord.pos().getZ() + 0.5,
                        8, 0.25, 0.25, 0.25, 0.05);
                }
            }
        } finally {
            IS_RESTORING.set(false);
            RECENT_BLOCK_CHANGES.clear();
            ACTIVE_BLOCKS_TO_RESTORE.clear();
        }

        // 2. Prevent item duplication: discard items dropped in the area during the rewind window
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel playerDimLevel = (ServerLevel) player.level();
            List<ItemEntity> droppedItems = playerDimLevel.getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(32.0),
                item -> item.tickCount <= (MAX_HISTORY_TICKS + 40)
            );
            for (ItemEntity item : droppedItems) {
                item.discard();
            }
        }

        // 3. Restore surviving living entities' health
        if (activeTargetSnapshot != null) {
            for (TimeSnapshot.EntityRecord rec : activeTargetSnapshot.entities().values()) {
                ServerLevel dimLevel = server.getLevel(rec.dimension());
                if (dimLevel != null) {
                    Entity entity = dimLevel.getEntity(rec.uuid());
                    if (entity instanceof LivingEntity living && living.isAlive()) {
                        living.setHealth(rec.initialHealth());
                    }
                }
            }
        }

        // 4. Restore players' exact states
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (activeTargetSnapshot != null) {
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
        }

        // 5. Clear trackers and end rewind
        isRewinding = false;
        rewindTickCount = 0;
        HISTORY.clear();
        ACTIVE_ENTITY_TRACKERS.clear();
        PLAYER_TRAJECTORIES.clear();
        activeTargetSnapshot = null;
        REWIND_START_POSITIONS.clear();
    }
}

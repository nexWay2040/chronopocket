package com.argon.chronopocket.time;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the 10-second rolling history buffer, multi-player rewind interpolation,
 * block restoration, and daylight rollback (Rick and Morty style time reset).
 */
public class TimeRewindManager {

    public static final int MAX_HISTORY_TICKS = 200; // 10.0 seconds at 20 TPS
    public static final int REWIND_ANIMATION_DURATION = 20; // 1.0 second smooth reverse animation

    private static final Deque<TimeSnapshot> HISTORY = new ArrayDeque<>();
    private static final Map<BlockPos, TimeSnapshot.BlockRecord> RECENT_BLOCK_CHANGES = new ConcurrentHashMap<>();

    // Active rewind execution state
    private static boolean isRewinding = false;
    private static int rewindTickCount = 0;
    private static TimeSnapshot activeTargetSnapshot = null;
    private static final Map<UUID, Vec3> REWIND_START_POSITIONS = new HashMap<>();

    public static void initialize() {
        // 1. Record block breaks into the rollback buffer
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!level.isClientSide() && !isRewinding) {
                RECENT_BLOCK_CHANGES.putIfAbsent(pos.immutable(), new TimeSnapshot.BlockRecord(
                    pos.immutable(),
                    level.dimension(),
                    state
                ));
            }
            return true;
        });

        // 2. Server tick loop: record history or execute rewind animation
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (isRewinding) {
                processRewindTick(server);
            } else {
                recordTickSnapshot(server);
            }
        });
    }

    private static void recordTickSnapshot(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long worldTime = overworld.getOverworldClockTime();

        Map<UUID, TimeSnapshot.PlayerRecord> playerMap = new HashMap<>();
        Map<UUID, TimeSnapshot.EntityRecord> entityMap = new HashMap<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            playerMap.put(player.getUUID(), new TimeSnapshot.PlayerRecord(
                player.getUUID(),
                player.level().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                player.getHealth(),
                player.getFoodData().getFoodLevel()
            ));

            // Record nearby living entities around players (within 32 blocks)
            List<LivingEntity> nearby = player.level().getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(32.0),
                e -> e != player && e.isAlive()
            );

            for (LivingEntity living : nearby) {
                entityMap.put(living.getUUID(), new TimeSnapshot.EntityRecord(
                    living.getUUID(),
                    living.level().dimension(),
                    living.position(),
                    living.getHealth()
                ));
            }
        }

        Map<BlockPos, TimeSnapshot.BlockRecord> blockChangesSnapshot = new HashMap<>(RECENT_BLOCK_CHANGES);

        HISTORY.addLast(new TimeSnapshot(worldTime, playerMap, entityMap, blockChangesSnapshot));
        if (HISTORY.size() > MAX_HISTORY_TICKS) {
            HISTORY.removeFirst();
        }
    }

    /**
     * Triggers the 10-second Time Rewind sequence for all players on the server.
     */
    public static boolean triggerRewind(ServerLevel level, ServerPlayer initiator, int rewindTicks) {
        if (isRewinding || HISTORY.isEmpty()) {
            return false;
        }

        // Target snapshot from rewindTicks ago (capped at available history)
        int stepsBack = Math.min(rewindTicks, HISTORY.size());
        List<TimeSnapshot> list = new ArrayList<>(HISTORY);
        int targetIndex = Math.max(0, list.size() - stepsBack);
        activeTargetSnapshot = list.get(targetIndex);

        isRewinding = true;
        rewindTickCount = 0;
        REWIND_START_POSITIONS.clear();

        // Capture current start positions for all players
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            REWIND_START_POSITIONS.put(player.getUUID(), player.position());

            // Rewind initiation sound & visual effect
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 1.8f);
            player.sendOverlayMessage(Component.translatable("message.chronopocket.rewinding").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        }

        return true;
    }

    private static void processRewindTick(MinecraftServer server) {
        rewindTickCount++;
        float progress = (float) rewindTickCount / REWIND_ANIMATION_DURATION;

        // Smooth cubic ease-in-out interpolation curve
        float t = progress < 0.5f ? 4.0f * progress * progress * progress : 1.0f - (float) Math.pow(-2.0f * progress + 2.0f, 3.0) / 2.0f;

        // Smoothly roll back time
        ServerLevel overworld = server.overworld();
        long targetTime = activeTargetSnapshot.worldTime();
        long currentTime = overworld.getOverworldClockTime();
        long interpolatedTime = (long) (currentTime + (targetTime - currentTime) * t);

        if (overworld.getLevelData() instanceof ServerLevelData serverLevelData) {
            serverLevelData.setGameTime(interpolatedTime);
        }

        // Interpolate players backwards
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TimeSnapshot.PlayerRecord rec = activeTargetSnapshot.players().get(player.getUUID());
            Vec3 startPos = REWIND_START_POSITIONS.get(player.getUUID());

            if (rec != null && startPos != null) {
                // If player was in a different dimension 10s ago (e.g. inside pocket base), transit them back!
                ServerLevel targetLevel = server.getLevel(rec.dimension());
                if (targetLevel == null) {
                    targetLevel = overworld;
                }

                Vec3 currentPos = startPos.lerp(rec.position(), t);
                float currentYRot = (float) (player.getYRot() + (rec.yRot() - player.getYRot()) * t);
                float currentXRot = (float) (player.getXRot() + (rec.xRot() - player.getXRot()) * t);

                player.teleportTo(targetLevel, currentPos.x, currentPos.y, currentPos.z, Set.of(), currentYRot, currentXRot, false);
                player.setDeltaMovement(Vec3.ZERO);

                // Reverse vortex particles
                player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, currentPos.x, currentPos.y + 1.0, currentPos.z, 8, 0.4, 0.4, 0.4, 0.05);
                player.level().sendParticles(ParticleTypes.PORTAL, currentPos.x, currentPos.y + 0.5, currentPos.z, 4, 0.2, 0.2, 0.2, 0.01);

                // Continuous rewind hum
                if (rewindTickCount % 5 == 0) {
                    player.level().playSound(null, currentPos.x, currentPos.y, currentPos.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8f, 1.8f);
                }
            }
        }

        // On Final Tick: Finalize Snapshot & Revert Blocks & Health
        if (rewindTickCount >= REWIND_ANIMATION_DURATION) {
            finalizeRewind(server);
        }
    }

    private static void finalizeRewind(MinecraftServer server) {
        isRewinding = false;
        rewindTickCount = 0;

        if (activeTargetSnapshot == null) return;

        // 1. Revert recorded block changes
        for (TimeSnapshot.BlockRecord blockRecord : activeTargetSnapshot.blockChanges().values()) {
            ServerLevel dimLevel = server.getLevel(blockRecord.dimension());
            if (dimLevel != null) {
                dimLevel.setBlock(blockRecord.pos(), blockRecord.originalState(), 3);
                dimLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, blockRecord.pos().getX() + 0.5, blockRecord.pos().getY() + 0.5, blockRecord.pos().getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.05);
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

        // 3. Clear recent history forward from the reverted point
        HISTORY.clear();
        activeTargetSnapshot = null;
        REWIND_START_POSITIONS.clear();
    }
}

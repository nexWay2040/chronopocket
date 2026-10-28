package com.argon.chronopocket.pocket;

import com.argon.chronopocket.ChronoPocketMod;
import com.argon.chronopocket.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages chunk allocation, vault room generation, and player transit
 * between the Overworld and the Pocket Void Dimension.
 */
public class PocketDimensionManager {

    public static final ResourceKey<Level> POCKET_LEVEL_KEY = ResourceKey.create(
        Registries.DIMENSION,
        ChronoPocketMod.id("pocket")
    );

    public record ReturnAnchor(ResourceKey<Level> dimension, BlockPos position) {}

    // Tracks where each player entered from (player UUID -> return location)
    private static final Map<UUID, ReturnAnchor> PLAYER_RETURN_ANCHORS = new ConcurrentHashMap<>();

    public static BlockPos getRoomCenter(int roomIndex) {
        int spacing = 64;
        int x = (roomIndex % 1000) * spacing;
        int y = 64;
        int z = (roomIndex / 1000) * spacing;
        return new BlockPos(x, y, z);
    }

    public static void setPlayerReturnAnchor(UUID playerUuid, ResourceKey<Level> dim, BlockPos pos) {
        PLAYER_RETURN_ANCHORS.put(playerUuid, new ReturnAnchor(dim, pos));
    }

    public static ReturnAnchor getPlayerReturnAnchor(UUID playerUuid) {
        return PLAYER_RETURN_ANCHORS.get(playerUuid);
    }

    public static void enterPocketBase(ServerPlayer player, BlockPos basePos, ResourceKey<Level> originDim, int roomIndex) {
        ServerLevel currentLevel = player.level();
        ServerLevel pocketLevel = currentLevel.getServer().getLevel(POCKET_LEVEL_KEY);
        if (pocketLevel == null) {
            ChronoPocketMod.LOGGER.error("Pocket Dimension not found! Check dimension data pack.");
            return;
        }

        BlockPos center = getRoomCenter(roomIndex);

        // Generate the 9x9x9 Vault Room if it doesn't exist yet
        if (pocketLevel.getBlockState(center).isAir() || pocketLevel.getBlockState(center.above()).isAir()) {
            generateRoom(pocketLevel, center);
        }

        // Save return anchor for the player
        setPlayerReturnAnchor(player.getUUID(), originDim, basePos);

        // Teleport player into room in front of exit terminal
        double spawnX = center.getX() + 0.5;
        double spawnY = center.getY() + 1.0;
        double spawnZ = center.getZ() + 1.8;

        // Enter effects
        currentLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.8f, 1.4f);
        currentLevel.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.5, 0.5, 0.1);

        player.teleportTo(pocketLevel, spawnX, spawnY, spawnZ, Set.of(), 180.0f, 0.0f, true);

        pocketLevel.playSound(null, spawnX, spawnY, spawnZ, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
        pocketLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, spawnX, spawnY + 1.0, spawnZ, 25, 0.5, 0.5, 0.5, 0.05);
    }

    public static void exitPocketBase(ServerPlayer player) {
        ReturnAnchor anchor = getPlayerReturnAnchor(player.getUUID());
        ServerLevel currentLevel = player.level();
        ServerLevel targetLevel = null;
        BlockPos targetPos = null;

        if (anchor != null) {
            targetLevel = currentLevel.getServer().getLevel(anchor.dimension());
            targetPos = anchor.position();
        }

        // Fallback to Overworld spawn if anchor was lost
        if (targetLevel == null || targetPos == null) {
            targetLevel = currentLevel.getServer().overworld();
            targetPos = targetLevel.getRespawnData().pos();
        }

        // Place player safely near the base block (offset towards air if possible)
        BlockPos exitSpot = targetPos.above();
        if (!targetLevel.getBlockState(exitSpot).isAir() && !targetLevel.getBlockState(exitSpot).is(ModBlocks.POCKET_BASE_BLOCK)) {
            exitSpot = targetPos.north();
        }

        double exitX = exitSpot.getX() + 0.5;
        double exitY = exitSpot.getY();
        double exitZ = exitSpot.getZ() + 0.5;

        currentLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHORUS_FLOWER_GROW, SoundSource.PLAYERS, 1.0f, 1.2f);

        player.teleportTo(targetLevel, exitX, exitY, exitZ, Set.of(), player.getYRot(), player.getXRot(), true);

        targetLevel.playSound(null, exitX, exitY, exitZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        targetLevel.sendParticles(ParticleTypes.PORTAL, exitX, exitY + 1.0, exitZ, 30, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * Generates a 9x9x9 Quantum Vault Room:
     * - Walls: Reinforced deepslate corners, smooth quartz panels, tinted glass accents
     * - Floor: Polished deepslate frame, sea lantern lighting, chiseled deepslate center
     * - Center: Exit Console Terminal (PocketExitBlock)
     * - Interior: 7x7x7 completely open space for machines, chests, beds, etc.
     */
    public static void generateRoom(ServerLevel level, BlockPos center) {
        BlockState deepslateFrame = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState floorCenter = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        BlockState cornerPillars = Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
        BlockState quartzWalls = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState glassAccent = Blocks.TINTED_GLASS.defaultBlockState();
        BlockState lightBlock = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        for (int dy = 0; dy <= 8; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);

                    boolean isEdgeX = (dx == -4 || dx == 4);
                    boolean isEdgeZ = (dz == -4 || dz == 4);
                    boolean isWall = isEdgeX || isEdgeZ;
                    boolean isCorner = isEdgeX && isEdgeZ;

                    if (dy == 0) {
                        // Floor
                        if (isWall) {
                            level.setBlock(pos, deepslateFrame, 3);
                        } else if (Math.abs(dx) == 3 && Math.abs(dz) == 3) {
                            level.setBlock(pos, lightBlock, 3);
                        } else {
                            level.setBlock(pos, floorCenter, 3);
                        }
                    } else if (dy == 8) {
                        // Ceiling
                        if (dx == 0 && dz == 0) {
                            level.setBlock(pos, lightBlock, 3);
                        } else {
                            level.setBlock(pos, deepslateFrame, 3);
                        }
                    } else {
                        // Walls or Interior
                        if (isCorner) {
                            level.setBlock(pos, cornerPillars, 3);
                        } else if (isWall) {
                            if (dy == 4) {
                                level.setBlock(pos, glassAccent, 3);
                            } else {
                                level.setBlock(pos, quartzWalls, 3);
                            }
                        } else {
                            // Clear interior space (empty for player builds)
                            level.setBlock(pos, air, 3);
                        }
                    }
                }
            }
        }

        // Place Central Exit Terminal on the floor
        BlockPos terminalPos = new BlockPos(cx, cy + 1, cz);
        level.setBlock(terminalPos, ModBlocks.POCKET_EXIT_BLOCK.defaultBlockState(), 3);
    }
}

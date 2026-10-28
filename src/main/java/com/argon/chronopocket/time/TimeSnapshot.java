package com.argon.chronopocket.time;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

public record TimeSnapshot(
    long worldTime,
    Map<UUID, PlayerRecord> players,
    Map<UUID, EntityRecord> entities,
    Map<BlockPos, BlockRecord> blockChanges
) {

    public record PlayerRecord(
        UUID uuid,
        ResourceKey<Level> dimension,
        Vec3 position,
        float yRot,
        float xRot,
        float health,
        int foodLevel
    ) {}

    public record EntityRecord(
        UUID uuid,
        ResourceKey<Level> dimension,
        Vec3 position,
        float health
    ) {}

    public record BlockRecord(
        BlockPos pos,
        ResourceKey<Level> dimension,
        BlockState originalState
    ) {}
}

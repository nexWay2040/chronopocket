package com.argon.chronopocket.block;

import com.argon.chronopocket.ChronoPocketMod;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {

    public static final ResourceKey<Block> POCKET_BASE_KEY = ResourceKey.create(
        Registries.BLOCK,
        ChronoPocketMod.id("pocket_base")
    );

    public static final ResourceKey<Block> POCKET_EXIT_KEY = ResourceKey.create(
        Registries.BLOCK,
        ChronoPocketMod.id("pocket_exit")
    );

    public static final Block POCKET_BASE_BLOCK = Registry.register(
        BuiltInRegistries.BLOCK,
        POCKET_BASE_KEY,
        new PocketBaseBlock(BlockBehaviour.Properties.of()
            .setId(POCKET_BASE_KEY)
            .strength(5.0f, 1200.0f)
            .sound(SoundType.NETHERITE_BLOCK)
            .lightLevel(state -> 12)
            .requiresCorrectToolForDrops()
        )
    );

    public static final Block POCKET_EXIT_BLOCK = Registry.register(
        BuiltInRegistries.BLOCK,
        POCKET_EXIT_KEY,
        new PocketExitBlock(BlockBehaviour.Properties.of()
            .setId(POCKET_EXIT_KEY)
            .strength(100.0f, 3600000.0f) // Indestructible exit console
            .sound(SoundType.AMETHYST)
            .lightLevel(state -> 15)
        )
    );

    public static final BlockEntityType<PocketBaseBlockEntity> POCKET_BASE_BLOCK_ENTITY = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        ChronoPocketMod.id("pocket_base_be"),
        FabricBlockEntityTypeBuilder.create(PocketBaseBlockEntity::new, POCKET_BASE_BLOCK).build()
    );

    public static void initialize() {
        ChronoPocketMod.LOGGER.info("Registered ChronoPocket Blocks and BlockEntities");
    }
}

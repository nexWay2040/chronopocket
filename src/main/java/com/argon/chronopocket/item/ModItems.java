package com.argon.chronopocket.item;

import com.argon.chronopocket.ChronoPocketMod;
import com.argon.chronopocket.block.ModBlocks;
import com.argon.chronopocket.component.PocketBaseData;
import com.argon.chronopocket.component.PocketDataComponents;
import com.argon.chronopocket.component.TimeRemoteData;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ModItems {

    public static final ResourceKey<Item> POCKET_BASE_KEY = ResourceKey.create(
        Registries.ITEM,
        ChronoPocketMod.id("pocket_base")
    );

    public static final ResourceKey<Item> POCKET_EXIT_KEY = ResourceKey.create(
        Registries.ITEM,
        ChronoPocketMod.id("pocket_exit")
    );

    public static final ResourceKey<Item> TIME_REMOTE_KEY = ResourceKey.create(
        Registries.ITEM,
        ChronoPocketMod.id("time_remote")
    );

    public static final Item POCKET_BASE_ITEM = Registry.register(
        BuiltInRegistries.ITEM,
        POCKET_BASE_KEY,
        new PocketBaseBlockItem(ModBlocks.POCKET_BASE_BLOCK, new Item.Properties()
            .setId(POCKET_BASE_KEY)
            .rarity(Rarity.RARE)
            .component(PocketDataComponents.POCKET_BASE_DATA, PocketBaseData.EMPTY)
        )
    );

    public static final Item POCKET_EXIT_ITEM = Registry.register(
        BuiltInRegistries.ITEM,
        POCKET_EXIT_KEY,
        new BlockItem(ModBlocks.POCKET_EXIT_BLOCK, new Item.Properties()
            .setId(POCKET_EXIT_KEY)
            .rarity(Rarity.UNCOMMON)
        )
    );

    public static final Item TIME_REMOTE = Registry.register(
        BuiltInRegistries.ITEM,
        TIME_REMOTE_KEY,
        new TimeRemoteItem(new Item.Properties()
            .setId(TIME_REMOTE_KEY)
            .durability(100)
            .rarity(Rarity.EPIC)
            .component(PocketDataComponents.TIME_REMOTE_DATA, TimeRemoteData.IDLE)
        )
    );

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(POCKET_BASE_ITEM);
            output.accept(TIME_REMOTE);
            output.accept(POCKET_EXIT_ITEM);
        });

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
            output.accept(POCKET_BASE_ITEM);
            output.accept(POCKET_EXIT_ITEM);
        });

        ChronoPocketMod.LOGGER.info("Registered ChronoPocket Items");
    }
}

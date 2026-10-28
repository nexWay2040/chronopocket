package com.argon.gravitymod.item;

import com.argon.gravitymod.GravityMod;
import com.argon.gravitymod.component.GauntletData;
import com.argon.gravitymod.component.ModDataComponents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Item registry for The Gravity Gauntlet mod.
 */
public class ModItems {

    public static final ResourceKey<Item> GRAVITY_GAUNTLET_KEY = ResourceKey.create(
        Registries.ITEM,
        GravityMod.id("gravity_gauntlet")
    );

    public static final Item GRAVITY_GAUNTLET = Registry.register(
        BuiltInRegistries.ITEM,
        GRAVITY_GAUNTLET_KEY,
        new GravityGauntletItem(new Item.Properties()
            .setId(GRAVITY_GAUNTLET_KEY)
            .durability(250)
            .fireResistant()
            .rarity(Rarity.EPIC)
            .component(ModDataComponents.GAUNTLET_DATA, GauntletData.EMPTY)
        )
    );

    public static void initialize() {
        // Register to Combat and Tools & Utilities creative tabs using CreativeModeTabEvents
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            output.accept(GRAVITY_GAUNTLET);
        });

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
            output.accept(GRAVITY_GAUNTLET);
        });

        GravityMod.LOGGER.info("Registered item: {}", GRAVITY_GAUNTLET);
    }
}

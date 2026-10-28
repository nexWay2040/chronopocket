package com.argon.gravitymod.component;

import com.argon.gravitymod.GravityMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Registry for custom item Data Components in modern Minecraft.
 */
public class ModDataComponents {

    public static final DataComponentType<GauntletData> GAUNTLET_DATA = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        GravityMod.id("gauntlet_data"),
        DataComponentType.<GauntletData>builder()
            .persistent(GauntletData.CODEC)
            .networkSynchronized(GauntletData.STREAM_CODEC)
            .build()
    );

    public static void initialize() {
        GravityMod.LOGGER.info("Registered Data Components for The Gravity Gauntlet");
    }
}

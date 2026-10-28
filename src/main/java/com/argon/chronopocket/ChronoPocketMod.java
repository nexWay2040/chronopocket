package com.argon.chronopocket;

import com.argon.chronopocket.block.ModBlocks;
import com.argon.chronopocket.component.PocketDataComponents;
import com.argon.chronopocket.item.ModItems;
import com.argon.chronopocket.time.TimeRewindManager;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChronoPocketMod implements ModInitializer {

    public static final String MOD_ID = "chronopocket";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing ChronoPocket Mod (Minecraft 26.1)...");

        PocketDataComponents.initialize();
        ModBlocks.initialize();
        ModItems.initialize();
        TimeRewindManager.initialize();

        LOGGER.info("ChronoPocket Mod successfully initialized!");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

package com.argon.chronopocket;

import com.argon.chronopocket.block.ModBlocks;
import com.argon.chronopocket.component.PocketDataComponents;
import com.argon.chronopocket.item.ModItems;
import com.argon.chronopocket.time.TimeRewindManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

        // Automatically unlock recipes in the recipe book for players on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ResourceKey<Recipe<?>> pocketBaseKey = ResourceKey.create(Registries.RECIPE, ChronoPocketMod.id("pocket_base"));
            ResourceKey<Recipe<?>> timeRemoteKey = ResourceKey.create(Registries.RECIPE, ChronoPocketMod.id("time_remote"));
            handler.player.awardRecipesByKey(List.of(pocketBaseKey, timeRemoteKey));
        });

        LOGGER.info("ChronoPocket Mod successfully initialized!");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

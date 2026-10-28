package com.argon.gravitymod;

import com.argon.gravitymod.component.ModDataComponents;
import com.argon.gravitymod.item.GravityGauntletItem;
import com.argon.gravitymod.item.ModItems;
import com.argon.gravitymod.network.GravityPrimaryFirePayload;
import com.argon.gravitymod.physics.KineticProjectileManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Mod Initializer for "The Gravity Gauntlet".
 */
public class GravityMod implements ModInitializer {

    public static final String MOD_ID = "gravitymod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing The Gravity Gauntlet Mod (Minecraft 26.1)...");

        // 1. Initialize Registries
        ModDataComponents.initialize();
        ModItems.initialize();
        KineticProjectileManager.initialize();

        // 2. Register Network Payload (Client -> Server: serverboundPlay)
        PayloadTypeRegistry.serverboundPlay().register(GravityPrimaryFirePayload.TYPE, GravityPrimaryFirePayload.STREAM_CODEC);

        // 3. Register Server Packet Receiver for Primary Fire (Left-Click)
        ServerPlayNetworking.registerGlobalReceiver(GravityPrimaryFirePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                ItemStack mainStack = player.getMainHandItem();
                ItemStack offStack = player.getOffhandItem();

                if (mainStack.is(ModItems.GRAVITY_GAUNTLET)) {
                    GravityGauntletItem.performPrimaryFire(player, mainStack);
                } else if (offStack.is(ModItems.GRAVITY_GAUNTLET)) {
                    GravityGauntletItem.performPrimaryFire(player, offStack);
                }
            });
        });

        LOGGER.info("The Gravity Gauntlet Mod successfully initialized!");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

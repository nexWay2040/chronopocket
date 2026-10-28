package com.argon.gravitymod.client;

import com.argon.gravitymod.item.ModItems;
import com.argon.gravitymod.network.GravityPrimaryFirePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side initializer for The Gravity Gauntlet.
 * Intercepts attack actions (Left-Click) to trigger Primary Fire cleanly across all surfaces.
 */
public class GravityModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Attack Keybind Listener (Air clicks & general clicks)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (client.options.keyAttack.consumeClick()) {
                ItemStack main = client.player.getMainHandItem();
                ItemStack off = client.player.getOffhandItem();

                if (main.is(ModItems.GRAVITY_GAUNTLET) || off.is(ModItems.GRAVITY_GAUNTLET)) {
                    ClientPlayNetworking.send(new GravityPrimaryFirePayload());
                }
            }
        });

        // 2. Intercept left-clicking blocks while holding the gauntlet
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (player.getItemInHand(hand).is(ModItems.GRAVITY_GAUNTLET)) {
                if (level.isClientSide()) {
                    ClientPlayNetworking.send(new GravityPrimaryFirePayload());
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        // 3. Intercept left-clicking entities directly while holding the gauntlet
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (player.getItemInHand(hand).is(ModItems.GRAVITY_GAUNTLET)) {
                if (level.isClientSide()) {
                    ClientPlayNetworking.send(new GravityPrimaryFirePayload());
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}

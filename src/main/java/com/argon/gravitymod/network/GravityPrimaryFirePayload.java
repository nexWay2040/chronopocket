package com.argon.gravitymod.network;

import com.argon.gravitymod.GravityMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-to-Server packet sent when the player clicks Left-Click (Primary Fire)
 * while holding The Gravity Gauntlet.
 */
public record GravityPrimaryFirePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GravityPrimaryFirePayload> TYPE =
        new CustomPacketPayload.Type<>(GravityMod.id("primary_fire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GravityPrimaryFirePayload> STREAM_CODEC =
        StreamCodec.unit(new GravityPrimaryFirePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

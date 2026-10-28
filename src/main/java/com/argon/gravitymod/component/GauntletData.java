package com.argon.gravitymod.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * Immutable Data Component holding the current state of the Gravity Gauntlet.
 * Stores the target entity's UUID and ticks held.
 */
public record GauntletData(Optional<UUID> heldEntityUuid, int holdTicks) {

    public static final GauntletData EMPTY = new GauntletData(Optional.empty(), 0);

    public static final Codec<GauntletData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.optionalFieldOf("held_entity").forGetter(GauntletData::heldEntityUuid),
            Codec.INT.fieldOf("hold_ticks").orElse(0).forGetter(GauntletData::holdTicks)
        ).apply(instance, GauntletData::new)
    );

    public static final StreamCodec<ByteBuf, GauntletData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
        GauntletData::heldEntityUuid,
        ByteBufCodecs.VAR_INT,
        GauntletData::holdTicks,
        GauntletData::new
    );

    public boolean hasHeldEntity() {
        return heldEntityUuid.isPresent();
    }
}

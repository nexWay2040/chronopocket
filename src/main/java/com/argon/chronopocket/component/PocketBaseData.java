package com.argon.chronopocket.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

public record PocketBaseData(Optional<UUID> baseId, int roomIndex, String customName) {

    public static final PocketBaseData EMPTY = new PocketBaseData(Optional.empty(), -1, "");

    public static final Codec<PocketBaseData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.optionalFieldOf("base_id").forGetter(PocketBaseData::baseId),
            Codec.INT.optionalFieldOf("room_index", -1).forGetter(PocketBaseData::roomIndex),
            Codec.STRING.optionalFieldOf("custom_name", "").forGetter(PocketBaseData::customName)
        ).apply(instance, PocketBaseData::new)
    );

    public static final StreamCodec<ByteBuf, PocketBaseData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
        PocketBaseData::baseId,
        ByteBufCodecs.VAR_INT,
        PocketBaseData::roomIndex,
        ByteBufCodecs.STRING_UTF8,
        PocketBaseData::customName,
        PocketBaseData::new
    );

    public boolean hasAssignedRoom() {
        return roomIndex >= 0 && baseId.isPresent();
    }
}

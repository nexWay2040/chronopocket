package com.argon.chronopocket.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record TimeRemoteData(boolean active, int remainingTicks, long checkpointTime) {

    public static final TimeRemoteData IDLE = new TimeRemoteData(false, 0, 0L);

    public static final Codec<TimeRemoteData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(TimeRemoteData::active),
            Codec.INT.optionalFieldOf("remaining_ticks", 0).forGetter(TimeRemoteData::remainingTicks),
            Codec.LONG.optionalFieldOf("checkpoint_time", 0L).forGetter(TimeRemoteData::checkpointTime)
        ).apply(instance, TimeRemoteData::new)
    );

    public static final StreamCodec<ByteBuf, TimeRemoteData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        TimeRemoteData::active,
        ByteBufCodecs.VAR_INT,
        TimeRemoteData::remainingTicks,
        ByteBufCodecs.VAR_LONG,
        TimeRemoteData::checkpointTime,
        TimeRemoteData::new
    );
}

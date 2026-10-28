package com.argon.chronopocket.component;

import com.argon.chronopocket.ChronoPocketMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public class PocketDataComponents {

    public static final DataComponentType<PocketBaseData> POCKET_BASE_DATA = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        ChronoPocketMod.id("pocket_base_data"),
        DataComponentType.<PocketBaseData>builder()
            .persistent(PocketBaseData.CODEC)
            .networkSynchronized(PocketBaseData.STREAM_CODEC)
            .build()
    );

    public static final DataComponentType<TimeRemoteData> TIME_REMOTE_DATA = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        ChronoPocketMod.id("time_remote_data"),
        DataComponentType.<TimeRemoteData>builder()
            .persistent(TimeRemoteData.CODEC)
            .networkSynchronized(TimeRemoteData.STREAM_CODEC)
            .build()
    );

    public static void initialize() {
        ChronoPocketMod.LOGGER.info("Registered ChronoPocket Data Components");
    }
}

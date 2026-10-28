package com.argon.chronopocket.pocket;

import com.argon.chronopocket.ChronoPocketMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistently stores global room allocations and links between base UUIDs and room indices.
 * Fully compliant with Minecraft 26.1 Codec-based SavedDataType.
 */
public class PocketSavedData extends SavedData {

    private int nextRoomIndex;
    private final Map<UUID, Integer> uuidToRoomMap;

    public static final Codec<PocketSavedData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("next_room_index").forGetter(PocketSavedData::getNextRoomIndex),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)
                .fieldOf("rooms").forGetter(PocketSavedData::getUuidToRoomMap)
        ).apply(instance, PocketSavedData::new)
    );

    public static final SavedDataType<PocketSavedData> TYPE = new SavedDataType<>(
        ChronoPocketMod.id("chronopocket_rooms"),
        PocketSavedData::new,
        CODEC,
        null
    );

    public PocketSavedData() {
        this(0, new HashMap<>());
    }

    public PocketSavedData(int nextRoomIndex, Map<UUID, Integer> map) {
        this.nextRoomIndex = nextRoomIndex;
        this.uuidToRoomMap = new HashMap<>(map);
    }

    public synchronized int getOrCreateRoomIndex(UUID baseId) {
        if (uuidToRoomMap.containsKey(baseId)) {
            return uuidToRoomMap.get(baseId);
        }
        int index = nextRoomIndex++;
        uuidToRoomMap.put(baseId, index);
        setDirty();
        return index;
    }

    public int getNextRoomIndex() {
        return nextRoomIndex;
    }

    public Map<UUID, Integer> getUuidToRoomMap() {
        return uuidToRoomMap;
    }

    public static PocketSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }
}

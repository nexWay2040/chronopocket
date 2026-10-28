package com.argon.chronopocket.block;

import com.argon.chronopocket.component.PocketBaseData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;
import java.util.UUID;

public class PocketBaseBlockEntity extends BlockEntity {

    private UUID baseId;
    private int roomIndex = -1;
    private String customName = "";

    public PocketBaseBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlocks.POCKET_BASE_BLOCK_ENTITY, pos, blockState);
        this.baseId = UUID.randomUUID();
    }

    public UUID getBaseId() {
        if (baseId == null) {
            baseId = UUID.randomUUID();
        }
        return baseId;
    }

    public void setBaseId(UUID baseId) {
        this.baseId = baseId;
        setChanged();
    }

    public int getRoomIndex() {
        return roomIndex;
    }

    public void setRoomIndex(int roomIndex) {
        this.roomIndex = roomIndex;
        setChanged();
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
        setChanged();
    }

    public PocketBaseData toComponentData() {
        return new PocketBaseData(Optional.ofNullable(baseId), roomIndex, customName);
    }

    public void applyComponentData(PocketBaseData data) {
        if (data.baseId().isPresent()) {
            this.baseId = data.baseId().get();
        }
        if (data.roomIndex() >= 0) {
            this.roomIndex = data.roomIndex();
        }
        this.customName = data.customName();
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (baseId != null) {
            output.putString("base_id", baseId.toString());
        }
        output.putInt("room_index", roomIndex);
        output.putString("custom_name", customName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.getString("base_id").ifPresent(str -> {
            try {
                this.baseId = UUID.fromString(str);
            } catch (Exception ignored) {}
        });
        this.roomIndex = input.getIntOr("room_index", -1);
        this.customName = input.getStringOr("custom_name", "");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (baseId != null) {
            tag.putString("base_id", baseId.toString());
        }
        tag.putInt("room_index", roomIndex);
        tag.putString("custom_name", customName);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}

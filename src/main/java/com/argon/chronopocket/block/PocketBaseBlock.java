package com.argon.chronopocket.block;

import com.argon.chronopocket.component.PocketBaseData;
import com.argon.chronopocket.component.PocketDataComponents;
import com.argon.chronopocket.pocket.PocketDimensionManager;
import com.argon.chronopocket.pocket.PocketSavedData;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class PocketBaseBlock extends BaseEntityBlock {

    public static final MapCodec<PocketBaseBlock> CODEC = simpleCodec(PocketBaseBlock::new);

    public PocketBaseBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PocketBaseBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Disallow recursive nesting: cannot place a pocket base inside the pocket dimension!
        if (context.getLevel().dimension().equals(PocketDimensionManager.POCKET_LEVEL_KEY)) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(
                    Component.translatable("message.chronopocket.cannot_nest").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                );
            }
            return null; // Prevents placement
        }
        return defaultBlockState();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = (ServerLevel) level;
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof PocketBaseBlockEntity pocketBe) {
                PocketSavedData savedData = PocketSavedData.get(serverLevel);
                int roomIndex = pocketBe.getRoomIndex();
                if (roomIndex < 0) {
                    roomIndex = savedData.getOrCreateRoomIndex(pocketBe.getBaseId());
                    pocketBe.setRoomIndex(roomIndex);
                }

                PocketDimensionManager.enterPocketBase(serverPlayer, pos, serverLevel.dimension(), roomIndex);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            PocketBaseData data = stack.get(PocketDataComponents.POCKET_BASE_DATA);
            if (data != null && data.hasAssignedRoom()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof PocketBaseBlockEntity pocketBe) {
                    pocketBe.applyComponentData(data);
                }
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PocketBaseBlockEntity pocketBe) {
                ItemStack dropStack = new ItemStack(this);
                dropStack.set(PocketDataComponents.POCKET_BASE_DATA, pocketBe.toComponentData());

                ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, dropStack);
                itemEntity.setDefaultPickUpDelay();
                level.addFreshEntity(itemEntity);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}

package com.argon.chronopocket.item;

import com.argon.chronopocket.component.PocketDataComponents;
import com.argon.chronopocket.component.TimeRemoteData;
import com.argon.chronopocket.time.TimeRewindManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class TimeRemoteItem extends Item {

    public static final int MAX_CHECKPOINT_TICKS = 200; // 10.0 seconds

    public TimeRemoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = (ServerLevel) level;

            // Shift + Right-Click: Instant Emergency 10-Second Rewind
            if (player.isShiftKeyDown()) {
                boolean success = TimeRewindManager.triggerRewind(serverLevel, serverPlayer, MAX_CHECKPOINT_TICKS);
                if (success) {
                    stack.set(PocketDataComponents.TIME_REMOTE_DATA, TimeRemoteData.IDLE);
                    stack.hurtAndBreak(2, serverPlayer, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
                return InteractionResult.SUCCESS;
            }

            TimeRemoteData data = stack.getOrDefault(PocketDataComponents.TIME_REMOTE_DATA, TimeRemoteData.IDLE);

            if (!data.active()) {
                // 1. ARM TEMPORAL CHECKPOINT
                stack.set(PocketDataComponents.TIME_REMOTE_DATA, new TimeRemoteData(true, MAX_CHECKPOINT_TICKS, level.getOverworldClockTime()));

                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 2.0f);
                serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 1.8f);

                serverLevel.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 35, 0.4, 0.6, 0.4, 0.2);
                serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.5, 0.3, 0.05);

                serverPlayer.sendOverlayMessage(Component.translatable("message.chronopocket.checkpoint_set").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            } else {
                // 2. TRIGGER REWIND TO CHECKPOINT!
                int elapsed = MAX_CHECKPOINT_TICKS - data.remainingTicks();
                int rewindTicks = Math.max(10, elapsed);

                boolean success = TimeRewindManager.triggerRewind(serverLevel, serverPlayer, rewindTicks);
                if (success) {
                    stack.set(PocketDataComponents.TIME_REMOTE_DATA, TimeRemoteData.IDLE);
                    stack.hurtAndBreak(1, serverPlayer, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity holder, EquipmentSlot slot) {
        if (!(holder instanceof ServerPlayer player)) {
            return;
        }

        TimeRemoteData data = stack.getOrDefault(PocketDataComponents.TIME_REMOTE_DATA, TimeRemoteData.IDLE);
        if (!data.active()) {
            return;
        }

        int remaining = data.remainingTicks() - 1;

        if (remaining <= 0) {
            // Checkpoint expired
            stack.set(PocketDataComponents.TIME_REMOTE_DATA, TimeRemoteData.IDLE);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f);
            player.sendOverlayMessage(Component.translatable("message.chronopocket.checkpoint_expired").withStyle(ChatFormatting.RED));
        } else {
            stack.set(PocketDataComponents.TIME_REMOTE_DATA, new TimeRemoteData(true, remaining, data.checkpointTime()));

            // Update action bar countdown every 5 ticks
            if (remaining % 5 == 0 && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) {
                float seconds = remaining / 20.0f;
                player.sendOverlayMessage(Component.translatable("message.chronopocket.checkpoint_ticking", String.format("%.1f", seconds)).withStyle(ChatFormatting.GOLD));
            }

            // Subtle ticking particle at player feet
            if (remaining % 10 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);

        TimeRemoteData data = stack.getOrDefault(PocketDataComponents.TIME_REMOTE_DATA, TimeRemoteData.IDLE);
        if (data.active()) {
            float seconds = data.remainingTicks() / 20.0f;
            tooltip.accept(Component.translatable("tooltip.chronopocket.time_remote.active", String.format("%.1f", seconds)).withStyle(ChatFormatting.YELLOW));
        }

        tooltip.accept(Component.translatable("tooltip.chronopocket.time_remote.desc1").withStyle(ChatFormatting.AQUA));
        tooltip.accept(Component.translatable("tooltip.chronopocket.time_remote.desc2").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.chronopocket.time_remote.desc3").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}

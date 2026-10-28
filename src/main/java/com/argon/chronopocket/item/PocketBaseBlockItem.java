package com.argon.chronopocket.item;

import com.argon.chronopocket.component.PocketBaseData;
import com.argon.chronopocket.component.PocketDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class PocketBaseBlockItem extends BlockItem {

    public PocketBaseBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);

        PocketBaseData data = stack.get(PocketDataComponents.POCKET_BASE_DATA);
        if (data != null && data.hasAssignedRoom()) {
            tooltip.accept(Component.translatable("tooltip.chronopocket.bound_room", data.roomIndex() + 1).withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.translatable("tooltip.chronopocket.preserved").withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.accept(Component.translatable("tooltip.chronopocket.empty_room").withStyle(ChatFormatting.DARK_AQUA));
        }

        tooltip.accept(Component.translatable("tooltip.chronopocket.pocket_base.desc1").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.chronopocket.pocket_base.desc2").withStyle(ChatFormatting.YELLOW));
    }
}

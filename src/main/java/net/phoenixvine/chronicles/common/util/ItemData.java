package net.phoenixvine.chronicles.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

public final class ItemData {

    private ItemData() {}

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        stack.setTag(tag == null || tag.isEmpty() ? null : tag.copy());
    }

    @Nullable
    public static CompoundTag capture(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || tag.isEmpty() ? null : tag.copy();
    }

    public static void applyReward(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty() || stack.isEmpty()) return;
        setTag(stack, tag);
    }
}

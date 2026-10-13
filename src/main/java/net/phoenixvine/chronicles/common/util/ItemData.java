package net.phoenixvine.chronicles.common.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class ItemData {

    private ItemData() {}

    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : data.copyTag();
    }

    @Nullable
    public static CompoundTag getTag(FluidStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : data.copyTag();
    }

    public static net.minecraft.core.component.DataComponentPatch patchOf(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return net.minecraft.core.component.DataComponentPatch.EMPTY;
        return net.minecraft.core.component.DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy())).build();
    }

    public static boolean hasTag(ItemStack stack) {
        return getTag(stack) != null;
    }

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
        }
    }

    public static void edit(ItemStack stack, Consumer<CompoundTag> editor) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }

    public static void removeKey(ItemStack stack, String key) {
        if (stack.has(DataComponents.CUSTOM_DATA)) edit(stack, tag -> tag.remove(key));
    }
}

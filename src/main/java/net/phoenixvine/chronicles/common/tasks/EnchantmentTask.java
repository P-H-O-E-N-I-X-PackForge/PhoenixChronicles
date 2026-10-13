package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.integration.curios.CuriosCompat;

public class EnchantmentTask extends QuestTask {

    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

    private ResourceLocation enchantmentId;
    private int requiredLevel;

    public EnchantmentTask(ResourceLocation taskId, Component description,
                           ResourceLocation enchantmentId, int requiredLevel) {
        super(taskId, description);
        this.enchantmentId = enchantmentId;
        this.requiredLevel = Math.max(1, requiredLevel);
    }

    public ResourceLocation getEnchantmentId() {
        return enchantmentId;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        net.minecraft.core.Holder<Enchantment> ench = player.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,
                        enchantmentId))
                .orElse(null);
        if (ench == null) return false;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(ench, stack) >= requiredLevel)
                return true;
        }
        for (ItemStack stack : CuriosCompat.getEquippedCurios(player)) {
            if (!stack.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(ench, stack) >= requiredLevel)
                return true;
        }
        return false;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "enchantment");
        tag.putString("enchantment_id", enchantmentId != null ? enchantmentId.toString() : "");
        tag.putInt("required_level", requiredLevel);
        return tag;
    }

    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("enchantment_id")) {
            String raw = nbt.getString("enchantment_id");
            try {
                enchantmentId = ResourceLocation.parse(raw);
            } catch (Exception ignored) {}
        }
        if (nbt.contains("required_level")) requiredLevel = Math.max(1, nbt.getInt("required_level"));
    }
}

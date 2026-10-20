package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.filter.NbtMatchUtil;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.util.ItemData;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.chronicles.integration.curios.CuriosCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FluidRequirementTask extends QuestTask {

    public enum Source {
        ITEMS,
        BLOCK,
        BOTH
    }

    private static final Map<UUID, Map<ResourceLocation, Integer>> blockFluids = new HashMap<>();

    public static void onBlockRightClicked(Player player, net.minecraft.world.level.Level level,
                                           net.minecraft.core.BlockPos pos) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) return;
        Map<ResourceLocation, Integer> amounts = new HashMap<>();
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
            amounts.merge(id, stack.getAmount(), Integer::sum);
        }
        blockFluids.put(player.getUUID(), amounts);
    }

    public static void clearBlockCache(UUID playerId) {
        blockFluids.remove(playerId);
    }

    private ResourceLocation fluidId;
    private int requiredAmount;
    private boolean consume;

    private CompoundTag nbtFilter = null;

    private boolean sticky = true;

    private boolean checkAe2Storage = AE2Compat.isAvailable();

    private Source source = Source.ITEMS;

    public FluidRequirementTask(ResourceLocation taskId, Component description, ResourceLocation fluidId,
                                int requiredAmount, boolean consume) {
        super(taskId, description);
        this.fluidId = fluidId;
        this.requiredAmount = requiredAmount;
        this.consume = consume;
    }

    public ResourceLocation getFluidId() {
        return fluidId;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public boolean shouldConsume() {
        return consume;
    }

    public CompoundTag getNbtFilter() {
        return nbtFilter;
    }

    public void setNbtFilter(CompoundTag filter) {
        this.nbtFilter = filter;
    }

    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    public boolean isCheckAe2Storage() {
        return checkAe2Storage;
    }

    public void setCheckAe2Storage(boolean checkAe2Storage) {
        this.checkAe2Storage = checkAe2Storage;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source != null ? source : Source.ITEMS;
    }

    private boolean countsBlocks() {
        return source != Source.ITEMS;
    }

    private boolean countsItems() {
        return source != Source.BLOCK;
    }

    private int blockAmount(Player player) {
        if (!countsBlocks() || fluidId == null) return 0;
        Map<ResourceLocation, Integer> amounts = blockFluids.get(player.getUUID());
        return amounts == null ? 0 : amounts.getOrDefault(fluidId, 0);
    }

    private boolean fluidMatches(FluidStack fs) {
        if (fs.isEmpty()) return false;
        ResourceLocation candidateId = BuiltInRegistries.FLUID.getKey(fs.getFluid());
        if (candidateId == null || !candidateId.equals(fluidId)) return false;
        return nbtFilter == null || nbtFilter.isEmpty() || NbtMatchUtil.matches(ItemData.matchView(fs), nbtFilter);
    }

    private boolean checksAe2Storage() {
        return checkAe2Storage && (nbtFilter == null || nbtFilter.isEmpty()) && AE2Compat.isAvailable();
    }

    @Override
    public boolean dependsOnInventory() {
        return !checksAe2Storage() && !countsBlocks();
    }

    @Override
    public boolean isCompletedFor(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed")) return true;
        if (fluidId == null || requiredAmount <= 0) return false;
        long found = checksAe2Storage() ? AE2Compat.getStoredAmount(player, getFluid()) : 0;
        if (found >= requiredAmount) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        if (found + blockAmount(player) + itemFluid(player) >= requiredAmount) {
            if (sticky) TaskProgressAccess.with(player, getTaskId(), nbt -> nbt.putBoolean("completed", true));
            return true;
        }
        return false;
    }

    private net.minecraft.world.level.material.Fluid getFluid() {
        return fluidId == null ? null : BuiltInRegistries.FLUID.get(fluidId);
    }

    private Iterable<ItemStack> fluidContainerSlots(Player player) {
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(CuriosCompat.getEquippedCurios(player));
        return all;
    }

    private int itemFluid(Player player) {
        return countsItems() ? getTotalFluidInInventory(player) : 0;
    }

    private int getTotalFluidInInventory(Player player) {
        int totalFound = 0;
        for (ItemStack stack : fluidContainerSlots(player)) {
            if (stack.isEmpty()) continue;

            IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler != null) {
                for (int i = 0; i < handler.getTanks(); i++) {
                    FluidStack fluidStack = handler.getFluidInTank(i);

                    if (fluidMatches(fluidStack)) {
                        totalFound += fluidStack.getAmount();
                        if (totalFound >= requiredAmount) return totalFound;
                    }
                }
            }
        }
        return totalFound;
    }

    @Override
    public void tryConsume(Player player) {
        if (fluidId == null || !consume || requiredAmount <= 0) return;

        int remainingToDrain = requiredAmount;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || remainingToDrain <= 0) break;

            IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler != null) {

                FluidStack simulatedDrain = handler.drain(remainingToDrain, IFluidHandler.FluidAction.SIMULATE);
                if (simulatedDrain.isEmpty()) continue;

                if (fluidMatches(simulatedDrain)) {
                    FluidStack actualDrain = handler.drain(remainingToDrain, IFluidHandler.FluidAction.EXECUTE);
                    remainingToDrain -= actualDrain.getAmount();

                    player.getInventory().setItem(i, handler.getContainer());
                }
            }
        }
        player.getInventory().setChanged();

        if (remainingToDrain > 0 && checksAe2Storage()) {
            AE2Compat.tryConsume(player, getFluid(), remainingToDrain);
        }
    }

    @Override
    public String getProgressString(Player player) {
        if (sticky && TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed"))
            return String.format("%,d / %,d mB", requiredAmount, requiredAmount);
        long found = checksAe2Storage() ? AE2Compat.getStoredAmount(player, getFluid()) : 0;
        found = Math.min(found + blockAmount(player) + itemFluid(player), requiredAmount);
        return String.format("%,d / %,d mB", found, requiredAmount);
    }

    @Override
    public ResourceLocation getDisplayItemId() {
        if (fluidId == null) return null;
        var fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null) return null;
        var bucket = fluid.getBucket();
        if (bucket == null || bucket == net.minecraft.world.item.Items.AIR) return null;
        return BuiltInRegistries.ITEM.getKey(bucket);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "fluid_check");
        tag.putString("fluid_id", fluidId != null ? fluidId.toString() : "minecraft:empty");
        tag.putInt("amount", requiredAmount);
        tag.putBoolean("consume", consume);
        tag.putBoolean("sticky", sticky);
        tag.putBoolean("check_ae2_storage", checkAe2Storage);
        if (source != Source.ITEMS) tag.putString("fluid_source", source.name());
        if (nbtFilter != null && !nbtFilter.isEmpty()) tag.put("nbt_filter", nbtFilter);
        return tag;
    }

    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("nbt_filter")) this.nbtFilter = nbt.getCompound("nbt_filter");
        if (nbt.contains("fluid_id")) {
            this.fluidId = ResourceLocation.parse(nbt.getString("fluid_id"));
        }
        this.requiredAmount = nbt.getInt("amount");
        this.consume = nbt.getBoolean("consume");
        this.sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
        this.checkAe2Storage = !nbt.contains("check_ae2_storage") || nbt.getBoolean("check_ae2_storage");
        this.source = Source.ITEMS;
        if (nbt.contains("fluid_source")) {
            try {
                this.source = Source.valueOf(nbt.getString("fluid_source"));
            } catch (IllegalArgumentException ignored) {}
        }
    }
}

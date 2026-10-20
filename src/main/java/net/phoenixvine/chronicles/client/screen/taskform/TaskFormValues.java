package net.phoenixvine.chronicles.client.screen.taskform;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.filter.IFluidFilter;
import net.phoenixvine.chronicles.common.filter.IItemFilter;

import org.jetbrains.annotations.Nullable;

public final class TaskFormValues {

    public ResourceLocation taskId;
    public Component description;

    public String target = "";
    public String second = "";
    public String nbt = "";
    public long count = 1;
    public boolean consume;
    public boolean sticky = true;
    public boolean checkAe2;
    public net.phoenixvine.chronicles.common.tasks.FluidRequirementTask.Source fluidSource = net.phoenixvine.chronicles.common.tasks.FluidRequirementTask.Source.ITEMS;

    @Nullable
    public IItemFilter itemFilter;
    @Nullable
    public IFluidFilter fluidFilter;

    public int intCount() {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, count));
    }
}

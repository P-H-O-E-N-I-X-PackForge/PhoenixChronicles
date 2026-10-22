package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

import org.jetbrains.annotations.Nullable;

public class MultiblockFormedTask extends QuestTask {

    public static final String ANY = "*";

    @Nullable
    private ResourceLocation machineId;
    private int required;

    public MultiblockFormedTask(ResourceLocation taskId, Component description, @Nullable ResourceLocation machineId,
                                int required) {
        super(taskId, description);
        this.machineId = machineId;
        this.required = Math.max(1, required);
    }

    @Nullable
    public ResourceLocation getMachineId() {
        return machineId;
    }

    public int getRequired() {
        return required;
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getInt("current") >= required;
    }

    @Override
    public String getProgressString(Player player) {
        int current = TaskProgressAccess.getOrEmpty(player, getTaskId()).getInt("current");
        return Math.min(current, required) + "/" + required;
    }

    public boolean matches(ResourceLocation formedMachineId) {
        return machineId == null || machineId.equals(formedMachineId);
    }

    public void onFormed(Player player, int times) {
        TaskProgressAccess.with(player, getTaskId(), nbt -> {
            int current = nbt.getInt("current");
            if (current >= required) return;
            nbt.putInt("current", Math.min(required, current + Math.max(1, times)));
        });
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "gt_multiblock");
        tag.putString("machine_id", machineId == null ? ANY : machineId.toString());
        tag.putInt("required", required);
        return tag;
    }

    public void deserializeNBT(CompoundTag nbt) {
        String id = nbt.getString("machine_id").trim();
        this.machineId = id.isEmpty() || id.equals(ANY) ? null : ResourceLocation.tryParse(id);
        if (nbt.contains("required")) this.required = Math.max(1, nbt.getInt("required"));
    }
}

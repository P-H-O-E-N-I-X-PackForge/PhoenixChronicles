package net.phoenixvine.chronicles.common.tasks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestTask;

public class ScreenOpenedTask extends QuestTask {

    private String screenId = "";

    public ScreenOpenedTask(ResourceLocation taskId, Component description) {
        super(taskId, description);
    }

    public ScreenOpenedTask(ResourceLocation taskId, Component description, String screenId) {
        super(taskId, description);
        this.screenId = screenId == null ? "" : screenId.trim();
    }

    public String getScreenId() {
        return screenId;
    }

    public boolean matchesScreen(String openedScreenId) {
        return screenId.isEmpty() || screenId.equals(openedScreenId);
    }

    public static void complete(Player player, ResourceLocation taskId) {
        TaskProgressAccess.with(player, taskId, nbt -> nbt.putBoolean("completed", true));
    }

    @Override
    public boolean isCompletedFor(Player player) {
        return TaskProgressAccess.getOrEmpty(player, getTaskId()).getBoolean("completed");
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", "screen_opened");
        if (!screenId.isEmpty()) tag.putString("screen_id", screenId);
        return tag;
    }

    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("screen_id")) screenId = nbt.getString("screen_id");
    }
}

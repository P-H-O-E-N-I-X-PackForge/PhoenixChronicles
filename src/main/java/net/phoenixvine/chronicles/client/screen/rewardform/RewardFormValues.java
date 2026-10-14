package net.phoenixvine.chronicles.client.screen.rewardform;

import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.common.model.QuestReward;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RewardFormValues {

    public long count = 1;
    public String text = "";
    public String eventData = "";
    @Nullable
    public ItemStack item;
    public boolean pushAe2 = true;
    public int permLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
    public QuestReward.QuestActionReward.Action questAction = QuestReward.QuestActionReward.Action.COMPLETE;
    public QuestReward.ChoiceBoxReward.Mode boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
    public List<QuestReward> boxOptions = new ArrayList<>();
    
    public Map<String, String> externalFields = new LinkedHashMap<>();

    public int intCount() {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, count));
    }
}

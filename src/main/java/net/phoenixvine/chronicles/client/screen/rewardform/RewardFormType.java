package net.phoenixvine.chronicles.client.screen.rewardform;

import net.phoenixvine.chronicles.common.model.QuestReward;

import org.jetbrains.annotations.Nullable;

public final class RewardFormType {

    private final String id;
    private final String label;
    private final String tooltip;
    private final boolean listed;
    private final boolean allowedInEmergencyKit;
    private final Class<? extends QuestReward> rewardClass;

    RewardFormType(String id, String label, String tooltip, boolean listed, boolean allowedInEmergencyKit,
                   Class<? extends QuestReward> rewardClass) {
        this.id = id;
        this.label = label;
        this.tooltip = tooltip;
        this.listed = listed;
        this.allowedInEmergencyKit = allowedInEmergencyKit;
        this.rewardClass = rewardClass;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String tooltip() {
        return tooltip;
    }

    public boolean listed() {
        return listed;
    }

    public boolean allowedInEmergencyKit() {
        return allowedInEmergencyKit;
    }

    public boolean handles(QuestReward reward) {
        return rewardClass.isInstance(reward);
    }

    @Nullable
    public QuestReward create(RewardFormValues v) {
        return RewardFormTypes.create(id, v);
    }

    public void load(QuestReward reward, RewardFormValues v) {
        RewardFormTypes.load(id, reward, v);
    }
}

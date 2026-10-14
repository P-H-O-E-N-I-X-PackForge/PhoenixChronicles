package net.phoenixvine.chronicles.client.screen.rewardform;

import net.phoenixvine.chronicles.common.model.QuestReward;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public final class RewardFormType<T extends QuestReward> {

    @FunctionalInterface
    public interface Factory {

        @Nullable
        QuestReward create(RewardFormValues v);
    }

    private final String id;
    private final String label;
    private final String tooltip;
    private final boolean listed;
    private final boolean allowedInEmergencyKit;
    private final Class<T> rewardClass;
    private final Factory factory;
    private final BiConsumer<T, RewardFormValues> loader;

    RewardFormType(String id, String label, String tooltip, boolean listed, boolean allowedInEmergencyKit,
                   Class<T> rewardClass, Factory factory, BiConsumer<T, RewardFormValues> loader) {
        this.id = id;
        this.label = label;
        this.tooltip = tooltip;
        this.listed = listed;
        this.allowedInEmergencyKit = allowedInEmergencyKit;
        this.rewardClass = rewardClass;
        this.factory = factory;
        this.loader = loader;
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
        return factory.create(v);
    }

    public void load(QuestReward reward, RewardFormValues v) {
        loader.accept(rewardClass.cast(reward), v);
    }
}

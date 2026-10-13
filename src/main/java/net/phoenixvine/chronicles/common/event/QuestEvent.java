package net.phoenixvine.chronicles.common.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;

public class QuestEvent extends Event {

    private final Player player;
    private final QuestNode node;

    public QuestEvent(Player player, QuestNode node) {
        this.player = player;
        this.node = node;
    }

    public Player getPlayer() {
        return player;
    }

    public QuestNode getNode() {
        return node;
    }

    public static class PlayerTick extends QuestEvent implements ICancellableEvent {

        public PlayerTick(Player player, QuestNode node) {
            super(player, node);
        }

        public static boolean hasAnyListeners() {
            return true;
        }
    }

    public static class StateChanged extends QuestEvent {

        private final QuestState oldState;
        private final QuestState newState;

        public StateChanged(Player player, QuestNode node, QuestState oldState, QuestState newState) {
            super(player, node);
            this.oldState = oldState;
            this.newState = newState;
        }

        public QuestState getOldState() {
            return oldState;
        }

        public QuestState getNewState() {
            return newState;
        }
    }

    public static class RewardClaimed extends QuestEvent implements ICancellableEvent {

        private final ServerPlayer serverPlayer;

        public RewardClaimed(ServerPlayer player, QuestNode node) {
            super(player, node);
            this.serverPlayer = player;
        }

        public ServerPlayer getServerPlayer() {
            return serverPlayer;
        }
    }

    public static class ExternalEvent extends QuestEvent implements ICancellableEvent {

        private final String triggerId;
        private final net.minecraft.nbt.CompoundTag data;

        public ExternalEvent(Player player, QuestNode node, String triggerId, net.minecraft.nbt.CompoundTag data) {
            super(player, node);
            this.triggerId = triggerId;
            this.data = data != null ? data : new net.minecraft.nbt.CompoundTag();
        }

        public String getTriggerId() {
            return triggerId;
        }

        public net.minecraft.nbt.CompoundTag getData() {
            return data;
        }
    }

    public static class PinChanged extends QuestEvent {

        private final boolean pinned;

        public PinChanged(Player player, QuestNode node, boolean pinned) {
            super(player, node);
            this.pinned = pinned;
        }

        public boolean isPinned() {
            return pinned;
        }
    }

    public static class TreeReloaded extends net.neoforged.bus.api.Event {}
}

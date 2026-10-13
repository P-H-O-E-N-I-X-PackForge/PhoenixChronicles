package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import java.util.ArrayList;
import java.util.List;

public class C2SBulkQuestActionPacket implements CustomPacketPayload {

    public static final Type<C2SBulkQuestActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_bulk_quest_action"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SBulkQuestActionPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SBulkQuestActionPacket::encode, C2SBulkQuestActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        FORCE_COMPLETE,
        RESET
    }

    private final List<ResourceLocation> questIds;
    private final Action action;

    public C2SBulkQuestActionPacket(List<ResourceLocation> questIds, Action action) {
        this.questIds = questIds;
        this.action = action;
    }

    public C2SBulkQuestActionPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.questIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) questIds.add(buf.readResourceLocation());
        this.action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(questIds.size());
        for (ResourceLocation id : questIds) buf.writeResourceLocation(id);
        buf.writeEnum(action);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null || !player.hasPermissions(2) || questIds.isEmpty()) return;

            QuestCapabilityProvider.of(player).ifPresent(data -> {
                for (ResourceLocation questId : questIds) {
                    QuestNode node = QuestTreeRegistry.getQuest(questId);
                    if (node == null) continue;

                    if (action == Action.FORCE_COMPLETE) {
                        if (data.getQuestState(questId, QuestState.LOCKED) != QuestState.COMPLETED) {
                            data.clearClaimedRewards(questId);
                        }
                        data.setQuestState(questId, QuestState.COMPLETED);
                        QuestProgressTracker.updateActiveTracking(player.getUUID(), node, QuestState.COMPLETED);
                    } else {
                        List<ResourceLocation> taskIds = new ArrayList<>();
                        for (QuestTask t : node.getTasks()) taskIds.add(t.getTaskId());
                        data.resetQuestProgress(questId, taskIds);
                        if (QuestProgressTracker.prereqsSatisfied(node, data, player.getServer())) {
                            QuestProgressTracker.changeQuestState(player, node, QuestState.UNLOCKED);
                        }
                    }
                }

                if (action == Action.FORCE_COMPLETE) QuestProgressTracker.autoClaimSweep(player);
                else QuestProgressTracker.relockUnsatisfied(player);

                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new S2CSyncPlayerProgressPacket(data));
            });
        });
    }
}

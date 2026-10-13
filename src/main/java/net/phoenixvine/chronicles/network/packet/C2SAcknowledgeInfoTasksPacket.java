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
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.InfoTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SAcknowledgeInfoTasksPacket implements CustomPacketPayload {

    public static final Type<C2SAcknowledgeInfoTasksPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_acknowledge_info_tasks"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SAcknowledgeInfoTasksPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SAcknowledgeInfoTasksPacket::encode, C2SAcknowledgeInfoTasksPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation questId;

    public C2SAcknowledgeInfoTasksPacket(ResourceLocation questId) {
        this.questId = questId;
    }

    public C2SAcknowledgeInfoTasksPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null || questId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            if (QuestProgressTracker.getQuestState(player, node) == QuestState.COMPLETED) return;

            QuestCapabilityProvider.of(player).ifPresent(data -> {
                boolean anyChanged = false;
                if (node.getTasks() != null) {
                    for (var task : node.getTasks()) {
                        if (task instanceof InfoTask) {
                            boolean wasDone = task.isCompletedFor(player);
                            if (!wasDone) {
                                InfoTask.acknowledge(player, task.getTaskId());
                                anyChanged = true;
                            }
                        }
                    }
                }
                if (anyChanged) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    QuestProgressTracker.sendProgressSync(player);
                }
            });
        });
    }
}

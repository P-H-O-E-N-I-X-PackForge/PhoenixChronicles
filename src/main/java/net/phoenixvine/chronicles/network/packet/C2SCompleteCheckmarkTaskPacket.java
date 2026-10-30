package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.CheckmarkTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SCompleteCheckmarkTaskPacket implements CustomPacketPayload {

    public static final Type<C2SCompleteCheckmarkTaskPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_complete_checkmark_task"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SCompleteCheckmarkTaskPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SCompleteCheckmarkTaskPacket::encode, C2SCompleteCheckmarkTaskPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation taskId;

    public C2SCompleteCheckmarkTaskPacket(ResourceLocation taskId) {
        this.taskId = taskId;
    }

    public C2SCompleteCheckmarkTaskPacket(FriendlyByteBuf buf) {
        this.taskId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(taskId);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player != null && !net.phoenixvine.chronicles.common.ChroniclesGates.check(player, net.phoenixvine.chronicles.common.ChroniclesGates.SUBMIT_TASKS, null)) return;
            if (player == null || taskId == null) return;

            QuestNode node = QuestTreeRegistry.getTaskOwner(taskId);
            if (node == null || node.getTasks() == null) return;

            QuestTask task = node.getTasks().stream()
                    .filter(t -> taskId.equals(t.getTaskId()))
                    .findFirst().orElse(null);
            if (!(task instanceof CheckmarkTask)) return;
            if (task.isCompletedFor(player)) return;

            QuestState state = QuestProgressTracker.getQuestState(player, node);
            if (state != QuestState.UNLOCKED && state != QuestState.ACTIVE) return;

            CheckmarkTask.complete(player, taskId);
            QuestProgressTracker.checkAndTryComplete(player, node);
            QuestProgressTracker.sendProgressSync(player);
        });
    }
}

package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.ScreenOpenedTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SScreenOpenedTaskPacket implements CustomPacketPayload {

    public static final Type<C2SScreenOpenedTaskPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_screen_opened_task"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SScreenOpenedTaskPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SScreenOpenedTaskPacket::encode, C2SScreenOpenedTaskPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation taskId;

    public C2SScreenOpenedTaskPacket(ResourceLocation taskId) {
        this.taskId = taskId;
    }

    public C2SScreenOpenedTaskPacket(FriendlyByteBuf buf) {
        this.taskId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(taskId);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null || taskId == null) return;

            QuestNode node = QuestTreeRegistry.getTaskOwner(taskId);
            if (node == null || node.getTasks() == null) return;

            QuestTask task = node.getTasks().stream()
                    .filter(t -> taskId.equals(t.getTaskId()))
                    .findFirst().orElse(null);
            if (!(task instanceof ScreenOpenedTask)) return;
            if (task.isCompletedFor(player)) return;

            ScreenOpenedTask.complete(player, taskId);
            QuestProgressTracker.checkAndTryComplete(player, node);
            QuestProgressTracker.sendProgressSync(player);
        });
    }
}

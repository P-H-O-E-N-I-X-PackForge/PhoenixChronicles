package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.ArchiveEntryTask;
import net.phoenixvine.chronicles.common.tasks.ViewGuideTask;
import net.phoenixvine.chronicles.common.tasks.ViewMachineTask;
import net.phoenixvine.chronicles.common.tasks.ViewSceneTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SPhantasiaTaskCompletePacket implements CustomPacketPayload {

    public static final Type<C2SPhantasiaTaskCompletePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_phantasia_task_complete"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SPhantasiaTaskCompletePacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SPhantasiaTaskCompletePacket::encode, C2SPhantasiaTaskCompletePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation questId;
    private final ResourceLocation taskId;

    public C2SPhantasiaTaskCompletePacket(ResourceLocation questId, ResourceLocation taskId) {
        this.questId = questId;
        this.taskId = taskId;
    }

    public C2SPhantasiaTaskCompletePacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.taskId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeResourceLocation(taskId);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null || questId == null || taskId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null || node.getTasks() == null) return;

            QuestState state = QuestProgressTracker.getQuestState(player, node);
            if (state == QuestState.COMPLETED || state == QuestState.LOCKED) return;

            for (QuestTask task : node.getTasks()) {
                if (!taskId.equals(task.getTaskId())) continue;

                if (!(task instanceof ViewMachineTask) && !(task instanceof ViewSceneTask) &&
                        !(task instanceof ViewGuideTask) && !(task instanceof ArchiveEntryTask))
                    break;
                if (task.isCompletedFor(player)) break;

                TaskProgressAccess.with(player, taskId, nbt -> nbt.putBoolean("completed", true));
                QuestProgressTracker.checkAndTryComplete(player, node);
                QuestProgressTracker.sendProgressSync(player);
                break;
            }
        });
    }
}

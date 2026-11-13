package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.ItemRequirementTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;
import net.phoenixvine.chronicles.common.util.ChroniclesLang;

import java.util.function.Supplier;

public class C2SSubmitItemTaskPacket {

    private final ResourceLocation taskId;

    public C2SSubmitItemTaskPacket(ResourceLocation taskId) {
        this.taskId = taskId;
    }

    public C2SSubmitItemTaskPacket(FriendlyByteBuf buf) {
        this.taskId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(taskId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && !net.phoenixvine.chronicles.common.ChroniclesGates.check(player,
                    net.phoenixvine.chronicles.common.ChroniclesGates.SUBMIT_TASKS, null))
                return;
            if (player == null || taskId == null) return;

            QuestNode node = QuestTreeRegistry.getTaskOwner(taskId);
            if (node == null) return;

            QuestTask task = node.getEffectiveTasks(player.getServer(), player).stream()
                    .filter(t -> taskId.equals(t.getTaskId()))
                    .findFirst().orElse(null);
            if (!(task instanceof ItemRequirementTask itemTask) || !itemTask.shouldConsume()) return;
            if (itemTask.isCompletedFor(player)) return;

            QuestState state = QuestProgressTracker.getQuestState(player, node);
            if (state != QuestState.UNLOCKED && state != QuestState.ACTIVE) return;

            if (!itemTask.submit(player)) {
                ItemStack shown = itemTask.getItem() != null ? itemTask.getItem().getDefaultInstance() :
                        ItemStack.EMPTY;
                player.sendSystemMessage(ChroniclesLang.tr("phoenix_chronicles.c2_s_submit_item_task_packet.you_need_x",
                        itemTask.getRequiredCount(),
                        shown.isEmpty() ?
                                ChroniclesLang.tr("phoenix_chronicles.c2_s_submit_item_task_packet.the_required_item") :
                                shown.getHoverName().copy().withStyle(s -> s.withColor(0xFF5555))));
                return;
            }

            QuestProgressTracker.checkAndTryComplete(player, node);
            QuestProgressTracker.sendProgressSync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}

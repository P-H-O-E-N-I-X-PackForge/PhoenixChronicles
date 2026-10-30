package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.ItemRequirementTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SSubmitItemTaskPacket implements CustomPacketPayload {

    public static final Type<C2SSubmitItemTaskPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_submit_item_task"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SSubmitItemTaskPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SSubmitItemTaskPacket::encode, C2SSubmitItemTaskPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

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

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player != null && !net.phoenixvine.chronicles.common.ChroniclesGates.check(player, net.phoenixvine.chronicles.common.ChroniclesGates.SUBMIT_TASKS, null)) return;
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
                player.sendSystemMessage(Component.literal("§cYou need " + itemTask.getRequiredCount() + "x ")
                        .append(shown.isEmpty() ? Component.literal("the required item") : shown.getHoverName()
                                .copy().withStyle(s -> s.withColor(0xFF5555)))
                        .append(Component.literal("§c to hand this in.")));
                return;
            }

            QuestProgressTracker.checkAndTryComplete(player, node);
            QuestProgressTracker.sendProgressSync(player);
        });
    }
}

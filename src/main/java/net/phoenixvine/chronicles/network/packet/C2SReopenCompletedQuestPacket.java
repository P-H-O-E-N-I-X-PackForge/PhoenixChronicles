package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SReopenCompletedQuestPacket implements CustomPacketPayload {

    public static final Type<C2SReopenCompletedQuestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_reopen_completed_quest"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SReopenCompletedQuestPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SReopenCompletedQuestPacket::encode, C2SReopenCompletedQuestPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation questId;

    public C2SReopenCompletedQuestPacket(ResourceLocation questId) {
        this.questId = questId;
    }

    public C2SReopenCompletedQuestPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (net.minecraft.server.level.ServerPlayer) context.player();
            if (sender == null || !sender.hasPermissions(2)) return;
            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null || sender.getServer() == null) return;

            for (ServerPlayer player : sender.getServer().getPlayerList().getPlayers()) {
                PlayerQuestData data = QuestCapabilityProvider.of(player).orElse(null);
                if (data == null || data.getQuestState(questId, QuestState.LOCKED) != QuestState.COMPLETED) continue;
                QuestProgressTracker.changeQuestState(player, node, QuestState.UNLOCKED);
                QuestProgressTracker.relockUnsatisfied(player);
                QuestProgressTracker.sendProgressSync(player);
            }
        });
    }
}

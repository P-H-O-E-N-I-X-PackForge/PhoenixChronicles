package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SClaimQuestRewardPacket implements CustomPacketPayload {

    public static final Type<C2SClaimQuestRewardPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_claim_quest_reward"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SClaimQuestRewardPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SClaimQuestRewardPacket::encode, C2SClaimQuestRewardPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation questId;
    private final int choiceIndex;

    public C2SClaimQuestRewardPacket(ResourceLocation questId, int choiceIndex) {
        this.questId = questId;
        this.choiceIndex = choiceIndex;
    }

    public C2SClaimQuestRewardPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.choiceIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeInt(choiceIndex);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player != null && !net.phoenixvine.chronicles.common.ChroniclesGates.check(player, net.phoenixvine.chronicles.common.ChroniclesGates.CLAIM_REWARDS, questId)) return;
            if (player == null || questId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            QuestState currentState = QuestProgressTracker.getQuestState(player, node);
            if (currentState != QuestState.COMPLETED) return;

            if (choiceIndex >= 0) {
                QuestProgressTracker.grantChosenReward(player, node, choiceIndex);
            } else {
                QuestProgressTracker.grantRewards(player, node);
            }
        });
    }
}

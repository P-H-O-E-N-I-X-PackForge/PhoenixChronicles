package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import java.util.ArrayList;
import java.util.List;

public class C2SClaimAllRewardsPacket implements CustomPacketPayload {

    public static final Type<C2SClaimAllRewardsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_claim_all_rewards"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SClaimAllRewardsPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SClaimAllRewardsPacket::encode, C2SClaimAllRewardsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public C2SClaimAllRewardsPacket() {}

    public C2SClaimAllRewardsPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null) return;

            PlayerQuestData data = QuestCapabilityProvider.of(player).orElse(null);
            if (data == null) return;

            List<QuestNode> candidates = new ArrayList<>(QuestTreeRegistry.getAllQuests().values());
            for (QuestNode node : candidates) {
                try {
                    if (node.isFlagDisabled(player.getServer())) continue;
                    if (data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
                    if (data.hasClaimedRewards(node.getId())) continue;
                    if (node.isRewardChoice()) continue;
                    if (node.getEffectiveRewards(player.getServer(), player).isEmpty()) continue;

                    QuestProgressTracker.grantRewards(player, node);
                } catch (Exception e) {
                    PhoenixChronicles.LOGGER.error("[ClaimAll] Failed to claim rewards for quest {}", node.getId(), e);
                }
            }

            QuestProgressTracker.sendProgressSync(player);
        });
    }
}

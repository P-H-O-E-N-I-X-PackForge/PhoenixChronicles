package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SRequestEmergencyItemsPacket implements CustomPacketPayload {

    public static final Type<C2SRequestEmergencyItemsPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_request_emergency_items"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SRequestEmergencyItemsPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SRequestEmergencyItemsPacket::encode, C2SRequestEmergencyItemsPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation questId;

    public C2SRequestEmergencyItemsPacket(ResourceLocation questId) {
        this.questId = questId;
    }

    public C2SRequestEmergencyItemsPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null || questId == null) return;

            QuestProgressTracker.EmergencyResult result;
            if (net.phoenixvine.chronicles.common.registry.ChapterEmergencyItems.isStation(questId)) {
                result = QuestProgressTracker.claimEmergencyStation(player, questId);
            } else {
                QuestNode node = QuestTreeRegistry.getQuest(questId);
                if (node == null) return;
                result = QuestProgressTracker.claimEmergencyItems(player, node);
            }
            player.sendSystemMessage(Component.literal((result.success() ? "§a" : "§c") + result.message()));
        });
    }
}

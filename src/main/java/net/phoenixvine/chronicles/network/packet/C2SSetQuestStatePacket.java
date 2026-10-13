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

public class C2SSetQuestStatePacket implements CustomPacketPayload {

    public static final Type<C2SSetQuestStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_set_quest_state"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SSetQuestStatePacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SSetQuestStatePacket::encode, C2SSetQuestStatePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation questId;
    private final boolean activate;

    public C2SSetQuestStatePacket(ResourceLocation questId, boolean activate) {
        this.questId = questId;
        this.activate = activate;
    }

    public C2SSetQuestStatePacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.activate = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeBoolean(activate);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null || questId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            QuestState current = QuestProgressTracker.getQuestState(player, node);
            if (activate && current == QuestState.UNLOCKED) {
                QuestProgressTracker.changeQuestState(player, node, QuestState.ACTIVE);
                QuestProgressTracker.sendProgressSync(player);
            } else if (!activate && current == QuestState.ACTIVE) {
                QuestProgressTracker.changeQuestState(player, node, QuestState.UNLOCKED);
                QuestProgressTracker.sendProgressSync(player);
            }
        });
    }
}

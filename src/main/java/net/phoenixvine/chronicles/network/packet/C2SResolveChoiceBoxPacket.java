package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SResolveChoiceBoxPacket implements CustomPacketPayload {

    public static final Type<C2SResolveChoiceBoxPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_resolve_choice_box"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SResolveChoiceBoxPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SResolveChoiceBoxPacket::encode, C2SResolveChoiceBoxPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation questId;
    private final int boxIndex;
    private final int optionIndex;

    public C2SResolveChoiceBoxPacket(ResourceLocation questId, int boxIndex, int optionIndex) {
        this.questId = questId;
        this.boxIndex = boxIndex;
        this.optionIndex = optionIndex;
    }

    public C2SResolveChoiceBoxPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
        this.boxIndex = buf.readInt();
        this.optionIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
        buf.writeInt(boxIndex);
        buf.writeInt(optionIndex);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player != null && !net.phoenixvine.chronicles.common.ChroniclesGates.check(player, net.phoenixvine.chronicles.common.ChroniclesGates.CLAIM_REWARDS, questId)) return;
            if (player == null || questId == null) return;

            QuestNode node = QuestTreeRegistry.getQuest(questId);
            if (node == null) return;

            QuestProgressTracker.resolveChoiceBox(player, node, boxIndex, optionIndex);
        });
    }
}

package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.event.QuestEvent;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2STogglePinPacket implements CustomPacketPayload {

    public static final Type<C2STogglePinPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_toggle_pin"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2STogglePinPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2STogglePinPacket::encode, C2STogglePinPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final ResourceLocation questId;

    public C2STogglePinPacket(ResourceLocation questId) {
        this.questId = questId;
    }

    public C2STogglePinPacket(FriendlyByteBuf buf) {
        this.questId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(questId);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null || questId == null) return;

            QuestCapabilityProvider.of(player).ifPresent(data -> {
                data.togglePin(questId);
                boolean nowPinned = data.isPinned(questId);
                QuestNode node = QuestTreeRegistry.getQuest(questId);
                if (node != null) {
                    NeoForge.EVENT_BUS.post(new QuestEvent.PinChanged(player, node, nowPinned));
                }
                QuestProgressTracker.sendProgressSync(player);
            });
        });
    }
}

package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.model.AutoClaimCategory;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

public class C2SSetAutoClaimPacket implements CustomPacketPayload {

    public static final Type<C2SSetAutoClaimPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_set_auto_claim"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SSetAutoClaimPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SSetAutoClaimPacket::encode, C2SSetAutoClaimPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final boolean enabled;
    private final int categoryMask;

    public C2SSetAutoClaimPacket(boolean enabled, int categoryMask) {
        this.enabled = enabled;
        this.categoryMask = categoryMask;
    }

    public C2SSetAutoClaimPacket(FriendlyByteBuf buf) {
        this.enabled = buf.readBoolean();
        this.categoryMask = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(categoryMask);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null) return;
            PlayerQuestData data = QuestCapabilityProvider.of(player).orElse(null);
            if (data == null) return;

            data.setAutoClaimEnabled(enabled);
            data.setAutoClaimCategories(AutoClaimCategory.fromMask(categoryMask));
            if (enabled) QuestProgressTracker.autoClaimSweep(player);
            QuestProgressTracker.sendProgressSync(player);
        });
    }
}

package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.common.codec.QuestFileLoader;

public class S2CReloadQuestsFromDiskPacket implements CustomPacketPayload {

    public static final Type<S2CReloadQuestsFromDiskPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "s2_c_reload_quests_from_disk"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, S2CReloadQuestsFromDiskPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CReloadQuestsFromDiskPacket::encode, S2CReloadQuestsFromDiskPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public S2CReloadQuestsFromDiskPacket() {}

    public S2CReloadQuestsFromDiskPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> QuestFileLoader.reloadAllQuestsFromDisk());
    }
}

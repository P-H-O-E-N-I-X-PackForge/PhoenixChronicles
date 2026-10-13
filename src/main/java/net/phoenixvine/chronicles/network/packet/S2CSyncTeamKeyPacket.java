package net.phoenixvine.chronicles.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class S2CSyncTeamKeyPacket implements CustomPacketPayload {

    public static final Type<S2CSyncTeamKeyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "s2_c_sync_team_key"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, S2CSyncTeamKeyPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CSyncTeamKeyPacket::encode, S2CSyncTeamKeyPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final String teamKey;

    public S2CSyncTeamKeyPacket(String teamKey) {
        this.teamKey = teamKey == null ? "" : teamKey;
    }

    public S2CSyncTeamKeyPacket(FriendlyByteBuf buf) {
        this.teamKey = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(teamKey);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> net.phoenixvine.chronicles.client.util.ClientTeamCache.set(teamKey));
    }
}

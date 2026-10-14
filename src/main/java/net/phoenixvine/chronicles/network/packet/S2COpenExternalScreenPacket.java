package net.phoenixvine.chronicles.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.Nullable;

public class S2COpenExternalScreenPacket implements CustomPacketPayload {

    public static final Type<S2COpenExternalScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "s2_c_open_external_screen"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, S2COpenExternalScreenPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2COpenExternalScreenPacket::encode, S2COpenExternalScreenPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Nullable
    private final ResourceLocation screenId;

    public S2COpenExternalScreenPacket(@Nullable ResourceLocation screenId) {
        this.screenId = screenId;
    }

    public S2COpenExternalScreenPacket(FriendlyByteBuf buf) {
        this.screenId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(
                screenId != null ? screenId : ResourceLocation.fromNamespaceAndPath("minecraft", "empty"));
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (screenId == null) return;
            net.minecraft.client.gui.screens.Screen screen = net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry
                    .open(screenId, null);
            if (screen != null) Minecraft.getInstance().setScreen(screen);
        });
    }
}

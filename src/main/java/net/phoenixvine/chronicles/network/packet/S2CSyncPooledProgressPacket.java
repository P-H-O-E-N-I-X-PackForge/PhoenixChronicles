package net.phoenixvine.chronicles.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.client.util.ClientPooledProgress;

import java.util.HashMap;
import java.util.Map;

public class S2CSyncPooledProgressPacket implements CustomPacketPayload {

    public static final Type<S2CSyncPooledProgressPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "s2_c_sync_pooled_progress"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, S2CSyncPooledProgressPacket> STREAM_CODEC = StreamCodec
            .ofMember(S2CSyncPooledProgressPacket::encode, S2CSyncPooledProgressPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final Map<ResourceLocation, CompoundTag> progress;

    public S2CSyncPooledProgressPacket(Map<ResourceLocation, CompoundTag> progress) {
        this.progress = progress;
    }

    public S2CSyncPooledProgressPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        this.progress = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            progress.put(buf.readResourceLocation(), buf.readNbt());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(progress.size());
        for (Map.Entry<ResourceLocation, CompoundTag> entry : progress.entrySet()) {
            buf.writeResourceLocation(entry.getKey());
            buf.writeNbt(entry.getValue());
        }
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            for (Map.Entry<ResourceLocation, CompoundTag> entry : progress.entrySet()) {
                ClientPooledProgress.put(entry.getKey(), entry.getValue());
            }
        });
    }
}

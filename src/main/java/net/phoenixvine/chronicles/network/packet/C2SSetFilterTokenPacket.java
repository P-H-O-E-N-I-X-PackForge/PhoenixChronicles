package net.phoenixvine.chronicles.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.phoenixvine.chronicles.common.item.FluidFilterTokenItem;
import net.phoenixvine.chronicles.common.item.ItemFilterTokenItem;
import net.phoenixvine.chronicles.common.util.ItemData;

public class C2SSetFilterTokenPacket implements CustomPacketPayload {

    public static final Type<C2SSetFilterTokenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "c2_s_set_filter_token"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, C2SSetFilterTokenPacket> STREAM_CODEC = StreamCodec
            .ofMember(C2SSetFilterTokenPacket::encode, C2SSetFilterTokenPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private final boolean clear;
    private final CompoundTag filterTag;

    public C2SSetFilterTokenPacket(CompoundTag filterTag) {
        this.clear = filterTag == null;
        this.filterTag = filterTag;
    }

    public C2SSetFilterTokenPacket(FriendlyByteBuf buf) {
        this.clear = buf.readBoolean();
        this.filterTag = this.clear ? null : buf.readNbt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(clear);
        if (!clear) buf.writeNbt(filterTag);
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (net.minecraft.server.level.ServerPlayer) context.player();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof ItemFilterTokenItem) &&
                    !(stack.getItem() instanceof FluidFilterTokenItem)) {
                stack = player.getOffhandItem();
            }
            String key = stack.getItem() instanceof ItemFilterTokenItem ? ItemFilterTokenItem.TAG_FILTER :
                    stack.getItem() instanceof FluidFilterTokenItem ? FluidFilterTokenItem.TAG_FILTER : null;
            if (key == null) return;

            if (clear) {
                ItemData.removeKey(stack, key);
            } else {
                ItemData.edit(stack, t -> t.put(key, filterTag.copy()));
            }
        });
    }
}

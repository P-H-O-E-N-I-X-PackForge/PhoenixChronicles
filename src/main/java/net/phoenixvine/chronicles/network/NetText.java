package net.phoenixvine.chronicles.network;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class NetText {

    private NetText() {}

    public static void writeComponent(FriendlyByteBuf buf, Component component) {
        buf.writeUtf(Component.Serializer.toJson(component, RegistryAccess.EMPTY), 262144);
    }

    public static Component readComponent(FriendlyByteBuf buf) {
        Component c = Component.Serializer.fromJson(buf.readUtf(262144), RegistryAccess.EMPTY);
        return c != null ? c : Component.empty();
    }
}

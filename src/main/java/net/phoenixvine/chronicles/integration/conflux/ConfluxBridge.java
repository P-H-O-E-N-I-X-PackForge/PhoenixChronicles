package net.phoenixvine.chronicles.integration.conflux;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.phoenix.core.api.ConfluxApi;

/**
 * The only class that touches PhoenixCore directly. It is never loaded unless {@link ConfluxCompat} has confirmed the
 * mod is present, so a pack without PhoenixCore never resolves {@code ConfluxApi}.
 */
final class ConfluxBridge {

    private ConfluxBridge() {}

    static boolean hasFlag(ServerPlayer player, String flag) {
        return ConfluxApi.hasFlag(player, flag);
    }

    static boolean isUnlocked(ServerPlayer player, ResourceLocation nodeId) {
        return ConfluxApi.isUnlocked(player, nodeId);
    }

    static void grantFlag(ServerPlayer player, String flag) {
        ConfluxApi.grantFlag(player, flag);
    }

    static void grantUnlock(ServerPlayer player, ResourceLocation nodeId) {
        ConfluxApi.grantUnlock(player, nodeId);
    }
}

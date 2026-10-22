package net.phoenixvine.chronicles.integration.conflux;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.phoenix.core.api.ConfluxApi;

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

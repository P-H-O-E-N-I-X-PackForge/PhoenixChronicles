package net.phoenixvine.chronicles.integration.conflux;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.NotNull;

public final class ConfluxCompat {

    public static final String PHOENIXCORE_MOD_ID = "phoenixcore";

    public static boolean isAvailable() {
        return false;
    }

    public static boolean isUnlocked(Player player, ResourceLocation nodeId) {
        if (!isAvailable() || !(player instanceof ServerPlayer sp)) return false;
        return false;
    }

    public static boolean hasFlag(Player player, String flag) {
        if (!isAvailable() || !(player instanceof ServerPlayer sp)) return false;
        return false;
    }

    public static void grantUnlock(@NotNull ServerPlayer player, ResourceLocation nodeId) {
        if (!isAvailable()) return;
    }

    public static void grantFlag(@NotNull ServerPlayer player, String flag) {
        if (!isAvailable()) return;
    }

    private ConfluxCompat() {}
}

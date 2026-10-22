package net.phoenixvine.chronicles.integration.conflux;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import org.jetbrains.annotations.NotNull;

public final class ConfluxCompat {

    public static final String PHOENIXCORE_MOD_ID = "phoenixcore";

    private static Boolean available;

    public static boolean isAvailable() {
        if (available == null) available = ModList.get() != null && ModList.get().isLoaded(PHOENIXCORE_MOD_ID);
        return available;
    }

    public static boolean isUnlocked(Player player, ResourceLocation nodeId) {
        return isAvailable() && player instanceof ServerPlayer sp && ConfluxBridge.isUnlocked(sp, nodeId);
    }

    public static boolean hasFlag(Player player, String flag) {
        return isAvailable() && player instanceof ServerPlayer sp && ConfluxBridge.hasFlag(sp, flag);
    }

    public static void grantUnlock(@NotNull ServerPlayer player, ResourceLocation nodeId) {
        if (isAvailable()) ConfluxBridge.grantUnlock(player, nodeId);
    }

    public static void grantFlag(@NotNull ServerPlayer player, String flag) {
        if (isAvailable()) ConfluxBridge.grantFlag(player, flag);
    }

    private ConfluxCompat() {}
}

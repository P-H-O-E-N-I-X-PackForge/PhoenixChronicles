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
        if (!isAvailable()) return false;
        if (player instanceof ServerPlayer sp) return ConfluxBridge.isUnlocked(sp, nodeId);
        // a client sees the research the server sent it
        return player != null && player.level().isClientSide() && ConfluxBridge.clientIsUnlocked(nodeId);
    }

    public static boolean hasFlag(Player player, String flag) {
        if (!isAvailable()) return false;
        if (player instanceof ServerPlayer sp) return ConfluxBridge.hasFlag(sp, flag);
        return player != null && player.level().isClientSide() && ConfluxBridge.clientHasFlag(flag);
    }

    /** The discipline the player's Conflux team chose, or null. */
    public static String discipline(Player player) {
        if (!isAvailable() || player == null) return null;
        if (player instanceof ServerPlayer sp) return ConfluxBridge.discipline(sp);
        return player.level().isClientSide() ? ConfluxBridge.clientDiscipline() : null;
    }

    public static void grantUnlock(@NotNull ServerPlayer player, ResourceLocation nodeId) {
        if (isAvailable()) ConfluxBridge.grantUnlock(player, nodeId);
    }

    public static void grantFlag(@NotNull ServerPlayer player, String flag) {
        if (isAvailable()) ConfluxBridge.grantFlag(player, flag);
    }

    private ConfluxCompat() {}
}

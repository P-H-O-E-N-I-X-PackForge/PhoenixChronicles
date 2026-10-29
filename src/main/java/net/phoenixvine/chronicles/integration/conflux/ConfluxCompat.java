package net.phoenixvine.chronicles.integration.conflux;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.phoenix.core.integration.conflux.research.ResearchTeamHelper;
import net.phoenix.core.integration.conflux.research.WorldResearchData;

import org.jetbrains.annotations.NotNull;

public final class ConfluxCompat {

    public static final String PHOENIXCORE_MOD_ID = "phoenixcore";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(PHOENIXCORE_MOD_ID);
    }

    public static boolean isUnlocked(Player player, ResourceLocation nodeId) {
        if (!isAvailable() || !(player instanceof ServerPlayer sp)) return false;
        var team = ResearchTeamHelper.getTeamId(sp);
        return WorldResearchData.get(sp.serverLevel()).isUnlocked(team, nodeId);
    }

    public static boolean hasFlag(Player player, String flag) {
        if (!isAvailable() || !(player instanceof ServerPlayer sp)) return false;
        var team = ResearchTeamHelper.getTeamId(sp);
        return WorldResearchData.get(sp.serverLevel()).hasFlag(team, flag);
    }

    /** The discipline the player's Conflux team chose, or null (also null on a client: the server alone knows it here). */
    public static String discipline(Player player) {
        if (!isAvailable() || !(player instanceof ServerPlayer sp)) return null;
        return WorldResearchData.get(sp.serverLevel()).getDiscipline(ResearchTeamHelper.getTeamId(sp));
    }

    public static void grantUnlock(@NotNull ServerPlayer player, ResourceLocation nodeId) {
        if (!isAvailable()) return;
        var team = ResearchTeamHelper.getTeamId(player);
        WorldResearchData.get(player.serverLevel()).grantUnlock(team, nodeId);
    }

    public static void grantFlag(@NotNull ServerPlayer player, String flag) {
        if (!isAvailable()) return;
        var team = ResearchTeamHelper.getTeamId(player);
        WorldResearchData.get(player.serverLevel()).grantFlag(team, flag);
    }

    private ConfluxCompat() {}
}

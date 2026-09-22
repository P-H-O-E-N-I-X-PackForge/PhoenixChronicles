package net.phoenixvine.chronicles.common.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import net.minecraftforge.fml.ModList;
import net.phoenixvine.chronicles.client.util.ClientTeamCache;

import java.util.Optional;

public final class TeamKeyResolver {

    private TeamKeyResolver() {}

    public static Optional<String> resolveAny(Player player) {
        if (player instanceof ServerPlayer sp) return resolve(sp);
        String clientKey = ClientTeamCache.get();
        return Optional.ofNullable(clientKey);
    }

    public static boolean anyTeamModLoaded() {
        var modList = ModList.get();
        if (modList == null) return false;
        return modList.isLoaded("phoenix_guilds") || modList.isLoaded("ftbteams");
    }

    public static Optional<String> resolve(ServerPlayer player) {
        if (ModList.get().isLoaded("phoenix_guilds")) {
            Optional<String> guildKey = PhoenixGuildsCompat.getGuildIdentifier(player);
            if (guildKey.isPresent()) return guildKey;
        }

        if (ModList.get().isLoaded("ftbteams")) {
            Optional<String> ftbKey = FTBTeamsCompat.getTeamIdentifier(player);
            if (ftbKey.isPresent()) return ftbKey;
        }

        Team team = player.getTeam();
        if (team != null) return Optional.of("sb:" + team.getName());

        return Optional.empty();
    }

    private static class PhoenixGuildsCompat {
        static Optional<String> getGuildIdentifier(ServerPlayer player) {
            return net.phoenixvine.guilds.GuildAPI.getGuildIdentifierString(player);
        }
    }

    private static class FTBTeamsCompat {
        static Optional<String> getTeamIdentifier(ServerPlayer player) {
            if (dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().isManagerLoaded()) {
                var opt = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager().getTeamForPlayerID(player.getUUID());
                if (opt.isPresent()) {
                    var team = opt.get();
                    if (team.isPartyTeam() || team.isServerTeam()) {
                        return Optional.of("ftbteam:" + team.getId());
                    }
                }
            }
            return Optional.empty();
        }
    }
}
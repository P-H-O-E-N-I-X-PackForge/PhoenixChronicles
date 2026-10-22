package net.phoenixvine.chronicles.common.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

import java.util.UUID;
import java.util.function.Function;

public final class FtbTeamsSharedCompletion {

    private FtbTeamsSharedCompletion() {}

    static boolean propagate(ServerPlayer source, QuestNode node) {
        return propagate(source, node, uuid -> source.getServer().getPlayerList().getPlayer(uuid));
    }

    public static boolean propagate(ServerPlayer source, QuestNode node,
                                    Function<UUID, ServerPlayer> memberByUuid) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return false;
        var opt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(source.getUUID());
        if (opt.isEmpty()) return false;

        var team = opt.get();
        if (!team.isPartyTeam() && !team.isServerTeam()) return false;

        for (UUID memberId : team.getMembers()) {
            ServerPlayer member = memberByUuid.apply(memberId);
            if (member == null || member.getUUID().equals(source.getUUID())) continue;
            PlayerQuestData data = QuestProgressTracker.resolveData(member);
            if (data != null && data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) {
                QuestProgressTracker.changeQuestState(member, node, QuestState.COMPLETED);
            }
        }
        return true;
    }
}

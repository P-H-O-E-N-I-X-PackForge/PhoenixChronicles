package net.phoenixvine.chronicles.common.tracker;

import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.guilds.data.GuildManager;

import java.util.UUID;
import java.util.function.Function;

public final class GuildsSharedCompletion {

    private GuildsSharedCompletion() {}

    static boolean propagate(ServerPlayer source, QuestNode node) {
        return propagate(source, node, uuid -> source.getServer().getPlayerList().getPlayer(uuid));
    }

    public static boolean propagate(ServerPlayer source, QuestNode node,
                                    Function<UUID, ServerPlayer> memberByUuid) {
        GuildManager guildMgr = GuildManager.get(source.getServer().overworld());
        var pTeam = guildMgr.getGuildFor(source.getUUID());
        if (pTeam.isEmpty()) return false;

        for (UUID memberUUID : pTeam.get().getMembers()) {
            if (memberUUID.equals(source.getUUID())) continue;
            ServerPlayer member = memberByUuid.apply(memberUUID);
            if (member == null) continue;
            PlayerQuestData data = QuestProgressTracker.resolveData(member);
            if (data != null && data.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) {
                QuestProgressTracker.changeQuestState(member, node, QuestState.COMPLETED);
            }
        }
        return true;
    }
}

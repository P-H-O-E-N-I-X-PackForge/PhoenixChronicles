package net.phoenixvine.chronicles.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.ScreenOpenedTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

import static net.phoenixvine.chronicles.common.gametest.QuestTestSupport.*;

@GameTestHolder(PhoenixChronicles.MOD_ID)
@PrefixGameTestTemplate(false)
public class TeamModGameTests {

    private static final String TEMPLATE = "gametest_empty";

    private static QuestNode sharedQuest(String path) {
        QuestNode node = new QuestNode(id(path), text(path), text(""));
        node.addTask(new ScreenOpenedTask(id(path + "_task"), text(path)));
        QuestTreeRegistry.registerBareQuestNode(node);
        return node;
    }

    private static ServerPlayer byUuid(UUID uuid, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (player.getUUID().equals(uuid)) return player;
        }
        return null;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void guildCompletionReachesGuildMembersOnly(@NotNull GameTestHelper helper) {
        if (!ModList.get().isLoaded("phoenix_guilds")) {
            helper.succeed();
            return;
        }
        GuildChecks.run(helper);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void ftbTeamsFallsThroughForPlayersWithoutAParty(@NotNull GameTestHelper helper) {
        if (!ModList.get().isLoaded("ftbteams")) {
            helper.succeed();
            return;
        }
        FtbChecks.run(helper);
    }

    private static final class GuildChecks {

        static void run(GameTestHelper helper) {
            QuestTreeRegistry.clear();
            var guilds = net.phoenixvine.guilds.data.GuildManager.get(helper.getLevel().getServer().overworld());
            ServerPlayer alice = newPlayer(helper);
            ServerPlayer bob = newPlayer(helper);
            ServerPlayer outsider = newPlayer(helper);
            var guild = guilds.createGuild("GT-" + UUID.randomUUID(), alice.getUUID());
            guilds.addMember(guild.getId(), bob.getUUID());
            try {
                QuestNode node = sharedQuest("guild_shared");
                QuestProgressTracker.changeQuestState(alice, node, QuestState.COMPLETED);

                boolean handled = net.phoenixvine.chronicles.common.tracker.GuildsSharedCompletion.propagate(alice,
                        node, uuid -> byUuid(uuid, alice, bob, outsider));

                helper.assertTrue(handled, "a player in a guild is handled by the guild path");
                assertState(helper, bob, node, QuestState.COMPLETED, "a guild member completes the quest too");
                assertState(helper, outsider, node, QuestState.LOCKED, "a player outside the guild is not affected");

                boolean outsiderHandled = net.phoenixvine.chronicles.common.tracker.GuildsSharedCompletion
                        .propagate(outsider, node, uuid -> byUuid(uuid, alice, bob, outsider));
                helper.assertTrue(!outsiderHandled, "a player with no guild falls through to the next team system");
            } finally {
                guilds.disbandGuild(guild.getId());
                QuestTreeRegistry.clear();
            }
            helper.succeed();
        }
    }

    private static final class FtbChecks {

        static void run(GameTestHelper helper) {
            QuestTreeRegistry.clear();
            ServerPlayer loner = newPlayer(helper);
            try {
                QuestNode node = sharedQuest("ftb_no_party");
                QuestProgressTracker.changeQuestState(loner, node, QuestState.COMPLETED);

                boolean handled = net.phoenixvine.chronicles.common.tracker.FtbTeamsSharedCompletion.propagate(loner,
                        node, uuid -> null);

                helper.assertTrue(!handled, "a player in no FTB party falls through to the scoreboard-team path");
            } finally {
                QuestTreeRegistry.clear();
            }
            helper.succeed();
        }
    }
}

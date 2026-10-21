package net.phoenixvine.chronicles.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
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
public class TeamGameTests {

    private static final String TEMPLATE = "gametest_empty";

    private interface TeamScenario {

        void run(GameTestHelper helper, ServerPlayer alice, ServerPlayer bob, ServerPlayer outsider);
    }

    private static void withTeam(GameTestHelper helper, TeamScenario scenario) {
        QuestTreeRegistry.clear();
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("gt_" + UUID.randomUUID().toString().substring(0, 8));
        ServerPlayer alice = newPlayer(helper);
        ServerPlayer bob = newPlayer(helper);
        ServerPlayer outsider = newPlayer(helper);
        scoreboard.addPlayerToTeam(alice.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(bob.getScoreboardName(), team);
        try {
            scenario.run(helper, alice, bob, outsider);
        } finally {
            scoreboard.removePlayerTeam(team);
            QuestTreeRegistry.clear();
        }
        helper.succeed();
    }

    private static QuestNode quest(String path, ScreenOpenedTask task) {
        QuestNode node = new QuestNode(id(path), text(path), text(""));
        node.addTask(task);
        QuestTreeRegistry.registerBareQuestNode(node);
        return node;
    }

    @GameTest(template = TEMPLATE)
    public static void pooledProgressReachesTeammatesOnly(@NotNull GameTestHelper helper) {
        withTeam(helper, (h, alice, bob, outsider) -> {
            ScreenOpenedTask task = new ScreenOpenedTask(id("pooled_task"), text("pooled"));
            QuestNode node = quest("pooled_quest", task);
            node.setPooledProgress(true);

            ScreenOpenedTask.complete(alice, task.getTaskId());

            h.assertTrue(task.isCompletedFor(alice), "the player who did the task has it done");
            h.assertTrue(task.isCompletedFor(bob), "a teammate sees pooled progress as done");
            h.assertTrue(!task.isCompletedFor(outsider), "a player on no team must not see the pooled progress");
            TaskProgressAccess.clear(alice, task.getTaskId());
        });
    }

    @GameTest(template = TEMPLATE)
    public static void unpooledProgressStaysWithThePlayer(@NotNull GameTestHelper helper) {
        withTeam(helper, (h, alice, bob, outsider) -> {
            ScreenOpenedTask task = new ScreenOpenedTask(id("solo_task"), text("solo"));
            quest("solo_quest", task).setPooledProgress(false);

            ScreenOpenedTask.complete(alice, task.getTaskId());

            h.assertTrue(task.isCompletedFor(alice), "the player who did the task has it done");
            h.assertTrue(!task.isCompletedFor(bob), "without pooling, teammates keep their own progress");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void questsAreSharedWithTheTeamByDefault(@NotNull GameTestHelper helper) {
        withTeam(helper, (h, alice, bob, outsider) -> {
            QuestNode node = quest("shared_quest", new ScreenOpenedTask(id("shared_task"), text("shared")));
            h.assertTrue(node.isShared(), "quests are shared with the team by default");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void unsharedCompletionStaysWithThePlayer(@NotNull GameTestHelper helper) {
        withTeam(helper, (h, alice, bob, outsider) -> {
            QuestNode node = quest("unshared_quest", new ScreenOpenedTask(id("unshared_task"), text("unshared")));
            node.setShared(false);

            QuestProgressTracker.changeQuestState(alice, node, QuestState.COMPLETED);

            assertState(h, bob, node, QuestState.LOCKED, "an unshared quest does not complete for teammates");
        });
    }
}

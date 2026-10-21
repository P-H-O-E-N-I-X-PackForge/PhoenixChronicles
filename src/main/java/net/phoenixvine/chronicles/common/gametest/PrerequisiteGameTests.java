package net.phoenixvine.chronicles.common.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import org.jetbrains.annotations.NotNull;

import static net.phoenixvine.chronicles.common.gametest.QuestTestSupport.*;

@GameTestHolder(PhoenixChronicles.MOD_ID)
@PrefixGameTestTemplate(false)
public class PrerequisiteGameTests {

    private static final String TEMPLATE = "gametest_empty";

    private static QuestNode register(String path) {
        QuestNode node = new QuestNode(id(path), text(path), text(""));
        QuestTreeRegistry.registerBareQuestNode(node);
        return node;
    }

    @GameTest(template = TEMPLATE)
    public static void deletingAPrerequisiteDoesNotLockItsDependents(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode prereq = register("gate");
            QuestNode dependent = register("after_gate");
            dependent.addPrerequisite(prereq);

            QuestProgressTracker.autoUnlockSatisfiedQuests(p);
            assertState(h, p, dependent, QuestState.LOCKED, "the dependent waits for its prerequisite");

            QuestTreeRegistry.removeQuest(prereq.getId());
            QuestProgressTracker.autoUnlockSatisfiedQuests(p);
            assertState(h, p, dependent, QuestState.UNLOCKED, "with the prerequisite gone, the dependent is open");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void recreatingADeletedPrerequisiteRelinksItsDependents(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode prereq = register("gate_again");
            QuestNode dependent = register("after_gate_again");
            dependent.addPrerequisite(prereq);

            QuestTreeRegistry.removeQuest(prereq.getId());
            h.assertTrue(dependent.getPrerequisites().isEmpty(), "deleting a quest detaches it from dependents");

            QuestNode recreated = register("gate_again");
            h.assertTrue(dependent.getPrerequisites().contains(recreated),
                    "a new quest with the same id takes the old one's place as the prerequisite");
        });
    }
}

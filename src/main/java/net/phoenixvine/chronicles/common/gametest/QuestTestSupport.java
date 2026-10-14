package net.phoenixvine.chronicles.common.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import com.mojang.authlib.GameProfile;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class QuestTestSupport {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private QuestTestSupport() {}

    @FunctionalInterface
    interface Scenario {

        void run(GameTestHelper helper, ServerPlayer player) throws Exception;
    }

    static void run(GameTestHelper helper, Scenario scenario) {
        QuestTreeRegistry.clear();
        try {
            scenario.run(helper, newPlayer(helper));
        } catch (GameTestAssertException e) {
            throw e;
        } catch (Exception e) {
            helper.fail("Unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            QuestTreeRegistry.clear();
        }
        helper.succeed();
    }

    static ServerPlayer newPlayer(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        return FakePlayerFactory.get(overworld,
                new GameProfile(UUID.randomUUID(), "gt" + COUNTER.incrementAndGet()));
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation(PhoenixChronicles.MOD_ID, path);
    }

    static Component text(String s) {
        return Component.literal(s);
    }

    static QuestNode unlockedQuest(ServerPlayer player, String path, QuestTask[] tasks, QuestReward... rewards) {
        QuestNode node = new QuestNode(id(path), text(path), text(""));
        for (QuestTask task : tasks) node.addTask(task);
        for (QuestReward reward : rewards) node.addReward(reward);
        QuestTreeRegistry.registerBareQuestNode(node);
        QuestProgressTracker.changeQuestState(player, node, QuestState.UNLOCKED);
        return node;
    }

    static QuestNode unlockedQuest(ServerPlayer player, String path, QuestTask task) {
        return unlockedQuest(player, path, new QuestTask[] { task });
    }

    static void assertState(GameTestHelper helper, ServerPlayer player, QuestNode node, QuestState expected,
                            String message) {
        QuestState actual = QuestProgressTracker.getQuestState(player, node);
        helper.assertTrue(actual == expected, message + " (expected " + expected + ", was " + actual + ")");
    }

    static void assertCompletes(GameTestHelper helper, ServerPlayer player, QuestNode node, String message) {
        QuestProgressTracker.checkAndTryComplete(player, node);
        assertState(helper, player, node, QuestState.COMPLETED, message);
    }

    static void assertStaysOpen(GameTestHelper helper, ServerPlayer player, QuestNode node, String message) {
        QuestProgressTracker.checkAndTryComplete(player, node);
        helper.assertTrue(QuestProgressTracker.getQuestState(player, node) != QuestState.COMPLETED, message);
    }

    static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    static BlockPos testPos(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 2, 1));
    }
}

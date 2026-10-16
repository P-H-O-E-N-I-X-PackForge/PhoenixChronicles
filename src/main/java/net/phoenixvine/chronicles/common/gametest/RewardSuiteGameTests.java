package net.phoenixvine.chronicles.common.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.event.PhoenixQuestScriptRewardEvent;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.model.RewardTable;
import net.phoenixvine.chronicles.common.registry.PhoenixRewardRegistry;
import net.phoenixvine.chronicles.common.tasks.CheckmarkTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;
import net.phoenixvine.chronicles.common.util.ItemData;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static net.phoenixvine.chronicles.common.gametest.QuestTestSupport.*;

@GameTestHolder(PhoenixChronicles.MOD_ID)
@PrefixGameTestTemplate(false)
public class RewardSuiteGameTests {

    private static final String TEMPLATE = "gametest_empty";

    private static void grant(net.minecraft.server.level.ServerPlayer p, QuestReward reward) {
        reward.grant(p);
    }

    @GameTest(template = TEMPLATE)
    public static void itemRewardGivesTheItems(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            grant(p, new QuestReward.ItemReward(Items.DIAMOND, 70, null, false));
            h.assertTrue(count(p, Items.DIAMOND) == 70, "70 diamonds (more than one stack) expected, got " +
                    count(p, Items.DIAMOND));
        });
    }

    @GameTest(template = TEMPLATE)
    public static void itemRewardKeepsEnchantmentsAndNames(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            var sharpness = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SHARPNESS);
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(sharpness, 5);
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(sharpness, 3);
            sword.set(DataComponents.CUSTOM_NAME, text("Reward Blade"));

            for (ItemStack source : List.of(book, sword)) {

                QuestReward.ItemReward captured = new QuestReward.ItemReward(source.getItem(), 1,
                        ItemData.capture(source), false);
                QuestReward reloaded = QuestReward.deserializeNBT(captured.serializeWithMeta());
                h.assertTrue(reloaded instanceof QuestReward.ItemReward, "item reward should reload as an item reward");
                p.getInventory().clearContent();
                grant(p, reloaded);

                ItemStack given = p.getInventory().items.stream().filter(s -> s.is(source.getItem())).findFirst()
                        .orElse(ItemStack.EMPTY);
                h.assertTrue(!given.isEmpty(), "the " + source.getItem() + " reward should arrive");
                ItemEnchantments expected = EnchantmentHelper.getEnchantmentsForCrafting(source);
                ItemEnchantments actual = EnchantmentHelper.getEnchantmentsForCrafting(given);
                h.assertTrue(!expected.isEmpty() && expected.equals(actual),
                        source.getItem() + " lost its enchantments: expected " + expected + " got " + actual);
                h.assertTrue(source.getHoverName().getString().equals(given.getHoverName().getString()),
                        source.getItem() + " lost its name");
            }
        });
    }

    @GameTest(template = TEMPLATE)
    public static void itemRewardUpgradesLegacyNbt(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {

            CompoundTag legacy = new CompoundTag();
            ListTag stored = new ListTag();
            CompoundTag entry = new CompoundTag();
            entry.putString("id", "minecraft:sharpness");
            entry.putShort("lvl", (short) 5);
            stored.add(entry);
            legacy.put("StoredEnchantments", stored);

            grant(p, new QuestReward.ItemReward(Items.ENCHANTED_BOOK, 1, legacy, false));
            ItemStack given = p.getInventory().items.stream().filter(s -> s.is(Items.ENCHANTED_BOOK)).findFirst()
                    .orElse(ItemStack.EMPTY);
            h.assertTrue(!given.isEmpty(), "the book should arrive");
            h.assertTrue(!EnchantmentHelper.getEnchantmentsForCrafting(given).isEmpty(),
                    "a legacy StoredEnchantments tag should become a real enchantment, not a blank book");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void xpRewardGivesLevels(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            grant(p, new QuestReward.XPReward(4));
            h.assertTrue(p.experienceLevel == 4, "expected level 4, got " + p.experienceLevel);
        });
    }

    @GameTest(template = TEMPLATE)
    public static void commandRewardRunsWithPlaceholders(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            grant(p, new QuestReward.CommandReward("tag @s add gametest_command_ran", 4));
            h.assertTrue(p.getTags().contains("gametest_command_ran"), "the command should have tagged the player");

            grant(p, new QuestReward.CommandReward("tag @s add gametest_{p}_{uuid}_tagged".replace("-", "_"), 4));
            h.assertTrue(p.getTags().stream().anyMatch(t -> t.startsWith("gametest_" + p.getName().getString())),
                    "{p} and {uuid} should be replaced with the player's name and id");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void commandRewardHonoursItsPermissionLevel(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            grant(p, new QuestReward.CommandReward("gamemode creative @s", 0));
            h.assertTrue(!p.isCreative(), "a permission level 0 command must not be able to change the game mode");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void lootTableRewardRollsTheTable(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            grant(p, new QuestReward.LootTableReward(
                    ResourceLocation.withDefaultNamespace("chests/spawn_bonus_chest")));
            int stacks = (int) p.getInventory().items.stream().filter(s -> !s.isEmpty()).count();
            h.assertTrue(stacks > 0, "the spawn bonus chest table should give at least one stack");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void scriptEventRewardFiresAnEventWithItsData(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            AtomicReference<PhoenixQuestScriptRewardEvent> seen = new AtomicReference<>();
            Object listener = new Object() {

                @SubscribeEvent
                public void on(PhoenixQuestScriptRewardEvent event) {
                    seen.set(event);
                }
            };
            NeoForge.EVENT_BUS.register(listener);
            try {
                CompoundTag data = new CompoundTag();
                data.putInt("power", 9);
                grant(p, new QuestReward.ScriptEventReward("gametest_event", data));
            } finally {
                NeoForge.EVENT_BUS.unregister(listener);
            }
            h.assertTrue(seen.get() != null, "the script event should have been posted");
            h.assertTrue(seen.get().getEventId().equals("gametest_event"), "wrong event id");
            h.assertTrue(seen.get().getData().getInt("power") == 9, "the event should carry the reward's data");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void questActionRewardCompletesAndResetsAnotherQuest(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode target = unlockedQuest(p, "action_target", new CheckmarkTask(id("target_task"), text("t")));
            grant(p, new QuestReward.QuestActionReward(target.getId().toString(),
                    QuestReward.QuestActionReward.Action.COMPLETE));
            assertState(h, p, target, QuestState.COMPLETED, "the action should complete the target quest");
            grant(p, new QuestReward.QuestActionReward(target.getId().toString(),
                    QuestReward.QuestActionReward.Action.RESET));
            h.assertTrue(QuestProgressTracker.getQuestState(p, target) != QuestState.COMPLETED,
                    "the reset action should reopen the target quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void questActionRewardNeverActsOnItsOwnQuest(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            CheckmarkTask check = new CheckmarkTask(id("self_task"), text("t"));
            QuestNode self = unlockedQuest(p, "action_self", new QuestTask[] { check },
                    new QuestReward.QuestActionReward(id("action_self").toString(),
                            QuestReward.QuestActionReward.Action.RESET));
            CheckmarkTask.complete(p, check.getTaskId());
            assertCompletes(h, p, self, "the checkmark was done");
            QuestProgressTracker.grantRewards(p, self);
            assertState(h, p, self, QuestState.COMPLETED, "a reset reward must not reopen the quest that granted it");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void rewardTableRewardGrantsFromTheTable(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            RewardTable all = new RewardTable("gametest_all", "All",
                    List.of(new QuestReward.WeightedReward(new QuestReward.XPReward(2), 1),
                            new QuestReward.WeightedReward(new QuestReward.ItemReward(Items.EMERALD, 3, null, false),
                                    1)),
                    0);
            all.grant(p);
            h.assertTrue(p.experienceLevel == 2 && count(p, Items.EMERALD) == 3,
                    "a pick-all table should grant every entry");

            RewardTable pickOne = new RewardTable("gametest_pick", "Pick",
                    List.of(new QuestReward.WeightedReward(new QuestReward.ItemReward(Items.COAL, 1, null, false), 1),
                            new QuestReward.WeightedReward(new QuestReward.ItemReward(Items.COAL, 1, null, false), 1)),
                    1);
            pickOne.grant(p);
            h.assertTrue(count(p, Items.COAL) == 1, "a pick-1 table should grant exactly one entry");

            grant(p, new QuestReward.RewardTableReward("gametest_missing_table"));
        });
    }

    @GameTest(template = TEMPLATE)
    public static void fluidRewardGivesBuckets(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            grant(p, new QuestReward.FluidReward(Fluids.WATER, 2000, null, false));
            h.assertTrue(count(p, Items.WATER_BUCKET) == 2, "2000 mB of water should arrive as 2 buckets, got " +
                    count(p, Items.WATER_BUCKET));
        });
    }

    @GameTest(template = TEMPLATE)
    public static void externalRewardCallsItsRegisteredHandler(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            AtomicReference<CompoundTag> received = new AtomicReference<>();
            PhoenixRewardRegistry.register("gametest:external").label("GameTest")
                    .onGrant((player, data) -> received.set(data)).register();
            CompoundTag data = new CompoundTag();
            data.putString("note", "hello");
            grant(p, new QuestReward.ExternalReward("gametest:external", data));
            h.assertTrue(received.get() != null && "hello".equals(received.get().getString("note")),
                    "the registered handler should receive the reward's data");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void openScreenRewardDoesNotBreakAClaim(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> grant(p, new QuestReward.OpenScreenReward(id("some_screen"))));
    }

    private static QuestReward.ChoiceBoxReward box(QuestReward.ChoiceBoxReward.Mode mode) {
        List<QuestReward> options = new ArrayList<>();
        options.add(new QuestReward.ItemReward(Items.IRON_INGOT, 1, null, false));
        options.add(new QuestReward.ItemReward(Items.GOLD_INGOT, 1, null, false));
        return new QuestReward.ChoiceBoxReward(options, mode);
    }

    private static QuestNode completedQuestWith(GameTestHelper h, net.minecraft.server.level.ServerPlayer p,
                                                String path, QuestReward reward) {
        CheckmarkTask check = new CheckmarkTask(id(path + "_task"), text("t"));
        QuestNode quest = unlockedQuest(p, path, new QuestTask[] { check }, reward);
        CheckmarkTask.complete(p, check.getTaskId());
        assertCompletes(h, p, quest, "the checkmark was done");
        return quest;
    }

    @GameTest(template = TEMPLATE)
    public static void choiceBoxAllModeGrantsEveryOption(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = completedQuestWith(h, p, "box_all", box(QuestReward.ChoiceBoxReward.Mode.ALL));
            QuestProgressTracker.grantRewards(p, quest);
            h.assertTrue(count(p, Items.IRON_INGOT) == 1 && count(p, Items.GOLD_INGOT) == 1,
                    "ALL mode should grant both options");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void choiceBoxLootboxModeGrantsExactlyOneOption(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = completedQuestWith(h, p, "box_loot", box(QuestReward.ChoiceBoxReward.Mode.LOOTBOX));
            QuestProgressTracker.grantRewards(p, quest);
            h.assertTrue(count(p, Items.IRON_INGOT) + count(p, Items.GOLD_INGOT) == 1,
                    "LOOTBOX mode should grant exactly one random option");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void choiceBoxMenuModeWaitsForThePlayersChoice(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = completedQuestWith(h, p, "box_menu", box(QuestReward.ChoiceBoxReward.Mode.MENU));
            QuestProgressTracker.grantRewards(p, quest);
            h.assertTrue(count(p, Items.IRON_INGOT) + count(p, Items.GOLD_INGOT) == 0,
                    "MENU mode must not grant anything until the player picks");
            QuestProgressTracker.resolveChoiceBox(p, quest, 0, 1);
            h.assertTrue(count(p, Items.GOLD_INGOT) == 1 && count(p, Items.IRON_INGOT) == 0,
                    "picking option 1 should grant only the gold");
            QuestProgressTracker.resolveChoiceBox(p, quest, 0, 0);
            h.assertTrue(count(p, Items.IRON_INGOT) == 0, "a box can only be resolved once");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void rewardsAreGrantedOnlyOncePerQuest(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = completedQuestWith(h, p, "once",
                    new QuestReward.ItemReward(Items.DIAMOND, 2, null, false));
            QuestProgressTracker.grantRewards(p, quest);
            QuestProgressTracker.grantRewards(p, quest);
            h.assertTrue(count(p, Items.DIAMOND) == 2, "claiming twice must not duplicate rewards, got " +
                    count(p, Items.DIAMOND));
        });
    }

    @GameTest(template = TEMPLATE)
    public static void everyRewardOfAQuestIsGrantedTogether(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            CheckmarkTask check = new CheckmarkTask(id("multi_task"), text("t"));
            QuestNode quest = unlockedQuest(p, "multi", new QuestTask[] { check },
                    new QuestReward.XPReward(3),
                    new QuestReward.ItemReward(Items.EMERALD, 5, null, false),
                    new QuestReward.FluidReward(Fluids.LAVA, 1000, null, false),
                    new QuestReward.CommandReward("tag @s add gametest_multi", 4));
            CheckmarkTask.complete(p, check.getTaskId());
            assertCompletes(h, p, quest, "the checkmark was done");
            QuestProgressTracker.grantRewards(p, quest);
            h.assertTrue(p.experienceLevel == 3, "xp missing");
            h.assertTrue(count(p, Items.EMERALD) == 5, "emeralds missing");
            h.assertTrue(count(p, Items.LAVA_BUCKET) == 1, "lava bucket missing");
            h.assertTrue(p.getTags().contains("gametest_multi"), "command did not run");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void everyRewardTypeSurvivesSerialisation(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            CompoundTag externalData = new CompoundTag();
            externalData.putString("note", "x");
            List<QuestReward> rewards = List.of(
                    new QuestReward.ItemReward(Items.DIAMOND, 3, null, false),
                    new QuestReward.XPReward(5),
                    new QuestReward.CommandReward("say hi", 2),
                    new QuestReward.LootTableReward(ResourceLocation.withDefaultNamespace("chests/spawn_bonus_chest")),
                    new QuestReward.ScriptEventReward("evt", new CompoundTag()),
                    new QuestReward.RewardTableReward("some_table"),
                    new QuestReward.FluidReward(Fluids.WATER, 1500, null, false),
                    new QuestReward.QuestActionReward("phoenix_chronicles:some_quest",
                            QuestReward.QuestActionReward.Action.RESET),
                    new QuestReward.OpenScreenReward(id("screen")),
                    new QuestReward.ExternalReward("gametest:external", externalData),
                    box(QuestReward.ChoiceBoxReward.Mode.MENU),
                    box(QuestReward.ChoiceBoxReward.Mode.LOOTBOX));

            for (QuestReward reward : rewards) {
                reward.setHiddenParts(QuestReward.HIDE_AMOUNT);
                CompoundTag saved = reward.serializeWithMeta();
                QuestReward loaded = QuestReward.deserializeNBT(saved);
                String name = reward.getClass().getSimpleName();
                h.assertTrue(loaded != null, name + " should load back from its own save");
                h.assertTrue(loaded.getClass() == reward.getClass(), name + " loaded as " + loaded.getClass());
                h.assertTrue(loaded.getHiddenParts() == QuestReward.HIDE_AMOUNT, name + " lost its hidden parts");
                h.assertTrue(loaded.serializeWithMeta().equals(saved),
                        name + " changed on the round trip: " + saved + " -> " + loaded.serializeWithMeta());
            }
        });
    }
}

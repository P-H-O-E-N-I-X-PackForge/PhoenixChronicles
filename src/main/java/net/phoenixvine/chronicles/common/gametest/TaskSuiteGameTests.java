package net.phoenixvine.chronicles.common.gametest;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.QuestAPI;
import net.phoenixvine.chronicles.capability.TaskProgressAccess;
import net.phoenixvine.chronicles.common.filter.FluidFilters;
import net.phoenixvine.chronicles.common.filter.ItemFilters;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.common.tasks.TimerTask;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static net.phoenixvine.chronicles.common.gametest.QuestTestSupport.*;

@GameTestHolder(PhoenixChronicles.MOD_ID)
@PrefixGameTestTemplate(false)
public class TaskSuiteGameTests {

    private static final String TEMPLATE = "gametest_empty";

    @GameTest(template = TEMPLATE)
    public static void itemTaskCountsTheInventory(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "item_task",
                    new ItemRequirementTask(id("t"), text("iron"), Items.IRON_INGOT, 3, false));
            p.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
            assertStaysOpen(h, p, quest, "2 of 3 iron must not complete the quest");
            p.getInventory().add(new ItemStack(Items.IRON_INGOT, 1));
            assertCompletes(h, p, quest, "3 iron should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void itemTaskWithConsumeCompletesOnHandIn(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            ItemRequirementTask task = new ItemRequirementTask(id("t"), text("iron"), Items.IRON_INGOT, 2, true);
            QuestNode quest = unlockedQuest(p, "item_consume", new QuestTask[] { task },
                    new QuestReward.XPReward(1));
            p.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
            assertStaysOpen(h, p, quest, "a consume task is shop style: holding the items is not enough");
            h.assertTrue(task.submit(p), "handing in 2 iron should succeed");
            h.assertTrue(count(p, Items.IRON_INGOT) == 0, "the hand-in should take the iron, left " +
                    count(p, Items.IRON_INGOT));
            assertCompletes(h, p, quest, "the hand-in should complete the quest");
            QuestProgressTracker.grantRewards(p, quest);
            h.assertTrue(p.experienceLevel == 1, "the reward should still be granted");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void itemTaskNbtFilterMatchesComponents(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            var sharpness = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SHARPNESS);
            ItemRequirementTask task = new ItemRequirementTask(id("t"), text("book"), Items.ENCHANTED_BOOK, 1, false);
            task.setNbtFilter(TagParser.parseTag(
                    "{components:{\"minecraft:stored_enchantments\":{levels:{\"minecraft:sharpness\":5}}}}"));
            QuestNode quest = unlockedQuest(p, "item_nbt", task);

            p.getInventory().add(new ItemStack(Items.ENCHANTED_BOOK));
            assertStaysOpen(h, p, quest, "a blank enchanted book must not match a Sharpness V filter");

            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(sharpness, 5);
            p.getInventory().add(book);
            assertCompletes(h, p, quest, "Sharpness V book should match the component filter");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void tagItemTaskCountsAnyItemInTheTag(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "tag_item",
                    new TagItemTask(id("t"), text("planks"), ItemTags.PLANKS, 3));
            p.getInventory().add(new ItemStack(Items.OAK_PLANKS, 2));
            assertStaysOpen(h, p, quest, "2 planks is not enough");
            p.getInventory().add(new ItemStack(Items.SPRUCE_PLANKS, 1));
            assertCompletes(h, p, quest, "planks of different woods should add up");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void filterItemTaskAcceptsAnyOfTheFilters(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "filter_item",
                    new FilterItemTask(id("t"), text("ingots"),
                            ItemFilters.anyOf(ItemFilters.exact(Items.IRON_INGOT), ItemFilters.exact(Items.GOLD_INGOT)),
                            2, false));
            p.getInventory().add(new ItemStack(Items.IRON_INGOT, 1));
            assertStaysOpen(h, p, quest, "one matching ingot is not enough");
            p.getInventory().add(new ItemStack(Items.GOLD_INGOT, 1));
            assertCompletes(h, p, quest, "iron plus gold should satisfy an any-of filter of count 2");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void fluidTaskCountsFluidContainers(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            FluidRequirementTask task = new FluidRequirementTask(id("t"), text("water"),
                    ResourceLocation.withDefaultNamespace("water"), 1000, false);
            task.setCheckAe2Storage(false);
            QuestNode quest = unlockedQuest(p, "fluid", task);
            assertStaysOpen(h, p, quest, "no water yet");
            p.getInventory().add(new ItemStack(Items.WATER_BUCKET));
            assertCompletes(h, p, quest, "a water bucket holds 1000 mB");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void filterFluidTaskCountsFluidContainers(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            FilterFluidTask task = new FilterFluidTask(id("t"), text("liquids"),
                    FluidFilters.anyOf(FluidFilters.exact(ResourceLocation.withDefaultNamespace("water")),
                            FluidFilters.exact(ResourceLocation.withDefaultNamespace("lava"))),
                    1000, false);
            task.setCheckAe2Storage(false);
            QuestNode quest = unlockedQuest(p, "filter_fluid", task);
            p.getInventory().add(new ItemStack(Items.LAVA_BUCKET));
            assertCompletes(h, p, quest, "a lava bucket satisfies a water-or-lava filter");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void craftItemTaskListensForCraftingEvents(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "craft",
                    new CraftItemTask(id("t"), text("pickaxes"), ResourceLocation.withDefaultNamespace("iron_pickaxe"),
                            2));
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(p, new ItemStack(Items.STONE_PICKAXE),
                    new SimpleContainer(1)));
            assertStaysOpen(h, p, quest, "crafting a different item must not count");
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(p, new ItemStack(Items.IRON_PICKAXE),
                    new SimpleContainer(1)));
            assertStaysOpen(h, p, quest, "1 of 2 crafts is not enough");
            NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(p, new ItemStack(Items.IRON_PICKAXE),
                    new SimpleContainer(1)));
            assertCompletes(h, p, quest, "2 crafts should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void killEntityTaskListensForKills(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "kill",
                    new KillEntityTask(id("t"), text("pig"), "minecraft:pig", 2, false));
            ServerLevel level = h.getLevel();
            DamageSource bySource = p.damageSources().playerAttack(p);
            Pig pig = EntityType.PIG.create(level);
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(pig, bySource));
            assertStaysOpen(h, p, quest, "1 of 2 kills is not enough");
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(EntityType.PIG.create(level), bySource));
            assertCompletes(h, p, quest, "2 pig kills should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void killEntityTaskIgnoresOtherMobsAndOtherKillers(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "kill_other",
                    new KillEntityTask(id("t"), text("pig"), "minecraft:pig", 1, false));
            ServerLevel level = h.getLevel();
            NeoForge.EVENT_BUS
                    .post(new LivingDeathEvent(EntityType.COW.create(level), p.damageSources().playerAttack(p)));
            NeoForge.EVENT_BUS.post(new LivingDeathEvent(EntityType.PIG.create(level), p.damageSources().generic()));
            assertStaysOpen(h, p, quest, "a cow, or a pig killed by nobody, must not count");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void blockBreakTaskListensForBreakEvents(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "break",
                    new BlockBreakTask(id("t"), text("stone"), Blocks.STONE, 2));
            BlockPos pos = testPos(h);
            ServerLevel level = h.getLevel();
            NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.DIRT.defaultBlockState(), p));
            assertStaysOpen(h, p, quest, "breaking dirt must not count");
            NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.STONE.defaultBlockState(), p));
            NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.STONE.defaultBlockState(), p));
            assertCompletes(h, p, quest, "2 stone breaks should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void blockInteractTaskHandlesPlacing(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "place",
                    new BlockInteractTask(id("t"), text("place"), Blocks.FURNACE, "PLACE", 1));
            BlockPos pos = testPos(h);
            ServerLevel level = h.getLevel();
            level.setBlock(pos, Blocks.FURNACE.defaultBlockState(), 3);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                    BlockSnapshot.create(level.dimension(), level, pos), Blocks.AIR.defaultBlockState(), p));
            assertCompletes(h, p, quest, "placing the furnace should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void blockInteractTaskHandlesRightClicking(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "rclick",
                    new BlockInteractTask(id("t"), text("click"), Blocks.LEVER, "RIGHT_CLICK", 1));
            BlockPos pos = testPos(h);
            h.getLevel().setBlock(pos, Blocks.LEVER.defaultBlockState(), 3);
            p.setPos(Vec3.atCenterOf(pos).add(0, 1, 0));
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, pos,
                    new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false)));
            assertCompletes(h, p, quest, "right-clicking the lever should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void advancementTaskListensForAdvancements(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            ResourceLocation rootId = ResourceLocation.withDefaultNamespace("story/root");
            AdvancementHolder holder = h.getLevel().getServer().getAdvancements().get(rootId);
            h.assertTrue(holder != null, "vanilla advancement story/root should exist");
            QuestNode quest = unlockedQuest(p, "advancement", new AdvancementTask(id("t"), text("root"), rootId));
            assertStaysOpen(h, p, quest, "not earned yet");
            NeoForge.EVENT_BUS.post(new AdvancementEvent.AdvancementEarnEvent(p, holder));
            assertCompletes(h, p, quest, "earning the advancement should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void dimensionTaskListensForDimensionChanges(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "dimension",
                    new DimensionTask(id("t"), text("nether"), "minecraft:the_nether"));
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.NETHER, Level.END));
            assertStaysOpen(h, p, quest, "arriving in the End must not count");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(p, Level.OVERWORLD, Level.NETHER));
            assertCompletes(h, p, quest, "arriving in the Nether should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void experienceTaskReadsThePlayersLevel(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "xp", new ExperienceTask(id("t"), text("levels"), 3));
            p.giveExperienceLevels(2);
            assertStaysOpen(h, p, quest, "level 2 is not enough");
            p.giveExperienceLevels(1);
            assertCompletes(h, p, quest, "level 3 should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void statTaskReadsTheStatistic(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "stat",
                    new StatTrackerTask(id("t"), text("jumps"), ResourceLocation.withDefaultNamespace("jump"), 5,
                            false));
            assertStaysOpen(h, p, quest, "no jumps yet");
            
            p.getStats().increment(p, Stats.CUSTOM.get(Stats.JUMP), 5);
            assertCompletes(h, p, quest, "5 jumps should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void enchantmentTaskReadsWornEnchantments(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "enchant",
                    new EnchantmentTask(id("t"), text("protection"),
                            ResourceLocation.withDefaultNamespace("protection"),
                            3));
            var protection = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.PROTECTION);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            helmet.enchant(protection, 2);
            p.setItemSlot(EquipmentSlot.HEAD, helmet);
            assertStaysOpen(h, p, quest, "Protection II is below the required level 3");
            ItemStack better = new ItemStack(Items.IRON_HELMET);
            better.enchant(protection, 3);
            p.setItemSlot(EquipmentSlot.HEAD, better);
            assertCompletes(h, p, quest, "Protection III should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void biomeTaskPollsThePlayersBiome(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            ServerLevel level = h.getLevel();
            p.setPos(Vec3.atCenterOf(testPos(h)));
            String here = level.getBiome(p.blockPosition()).unwrapKey().orElseThrow().location().toString();
            BiomeTask right = new BiomeTask(id("here"), text("here"), here);
            BiomeTask wrong = new BiomeTask(id("nowhere"), text("nowhere"), "minecraft:the_void_that_is_not_here");
            QuestNode quest = unlockedQuest(p, "biome", new QuestTask[] { right });
            QuestNode other = unlockedQuest(p, "biome_wrong", new QuestTask[] { wrong });
            right.onTick(p);
            wrong.onTick(p);
            assertCompletes(h, p, quest, "standing in the biome should complete the quest");
            assertStaysOpen(h, p, other, "a different biome must not complete");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void structureTaskStaysOpenOutsideStructures(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            p.setPos(Vec3.atCenterOf(testPos(h)));
            StructureTask task = new StructureTask(id("t"), text("village"), "minecraft:village_plains");
            QuestNode quest = unlockedQuest(p, "structure", task);
            task.onTick(p);
            assertStaysOpen(h, p, quest, "an empty test arena is not inside a village");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void timerTaskCompletesOnceTheDurationHasPassed(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            TimerTask task = new TimerTask(id("t"), text("wait"), 2);
            QuestNode quest = unlockedQuest(p, "timer", task);
            task.onTick(p);
            assertStaysOpen(h, p, quest, "the timer has only just started");
            TaskProgressAccess.with(p, task.getTaskId(),
                    nbt -> nbt.putLong("start_time", System.currentTimeMillis() - 5000));
            task.onTick(p);
            assertCompletes(h, p, quest, "5 seconds have passed on a 2 second timer");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void checkmarkInfoScreenAndTerminalTasksCompleteOnTheirAction(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            CheckmarkTask check = new CheckmarkTask(id("check"), text("check"));
            InfoTask info = new InfoTask(id("info"), text("info"), "read this");
            ScreenOpenedTask screen = new ScreenOpenedTask(id("screen"), text("screen"));
            ResourceLocation terminalId = id("terminal_a");
            LocationOrTerminalTask terminal = new LocationOrTerminalTask(id("terminal"), text("terminal"), terminalId,
                    false);
            QuestNode quest = unlockedQuest(p, "click_tasks", new QuestTask[] { check, info, screen, terminal });

            CheckmarkTask.complete(p, check.getTaskId());
            InfoTask.acknowledge(p, info.getTaskId());
            ScreenOpenedTask.complete(p, screen.getTaskId());
            assertStaysOpen(h, p, quest, "the terminal task is still open");
            terminal.checkTerminalInteraction(p, id("some_other_terminal"));
            assertStaysOpen(h, p, quest, "a different terminal must not count");
            terminal.checkTerminalInteraction(p, terminalId);
            assertCompletes(h, p, quest, "all four tasks done should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void clientDrivenViewTasksCompleteWhenMarked(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            ViewMachineTask machine = new ViewMachineTask(id("machine"), text("machine"), "gtceu:x", 0f);
            ViewSceneTask scene = new ViewSceneTask(id("scene"), text("scene"), "scene_a", 0f);
            ViewGuideTask guide = new ViewGuideTask(id("guide"), text("guide"), "guide_a");
            ArchiveEntryTask entry = new ArchiveEntryTask(id("entry"), text("entry"), "phoenix_archive:log_001");
            QuestNode quest = unlockedQuest(p, "view_tasks", new QuestTask[] { machine, scene, guide, entry });
            machine.markCompletedClient(p);
            scene.markCompletedClient(p);
            guide.markCompletedClient(p);
            assertStaysOpen(h, p, quest, "the archive entry is still unviewed");
            entry.markCompletedClient(p);
            assertCompletes(h, p, quest, "all four views done should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void externalTriggerTaskCountsFiredEvents(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            QuestNode quest = unlockedQuest(p, "external",
                    new ExternalTriggerTask(id("t"), text("trigger"), "gametest:thing", 2));
            QuestAPI.fireExternalEvent(p, "gametest:other", null);
            QuestAPI.fireExternalEvent(p, "gametest:thing", null);
            assertState(h, p, quest, QuestState.UNLOCKED, "one matching event of two is not enough");
            QuestAPI.fireExternalEvent(p, "gametest:thing", new CompoundTag());
            assertState(h, p, quest, QuestState.COMPLETED, "two matching events should complete the quest");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void recipeTaskCountsFinishedRecipes(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            ResourceLocation macerator = ResourceLocation.fromNamespaceAndPath("gtceu", "macerator");
            QuestNode anyRecipe = unlockedQuest(p, "recipe_any",
                    new RecipeTask(id("any"), text("any"), macerator, null, 2));
            QuestNode oneRecipe = unlockedQuest(p, "recipe_one",
                    new RecipeTask(id("one"), text("one"), macerator, ResourceLocation.parse("gtceu:macerator/a"), 1));

            QuestAPI.fireRecipeCompleted(p, macerator, ResourceLocation.parse("gtceu:macerator/b"), 1);
            assertState(h, p, anyRecipe, QuestState.UNLOCKED, "1 of 2 runs");
            assertState(h, p, oneRecipe, QuestState.UNLOCKED, "the specific recipe has not run");
            QuestAPI.fireRecipeCompleted(p, macerator, ResourceLocation.parse("gtceu:macerator/a"), 1);
            assertState(h, p, anyRecipe, QuestState.COMPLETED, "2 runs of the type complete the any-recipe task");
            assertState(h, p, oneRecipe, QuestState.COMPLETED, "the named recipe ran");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void multiblockTaskMatchesTheControllerOrAny(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            ResourceLocation ebf = ResourceLocation.fromNamespaceAndPath("gtceu", "electric_blast_furnace");
            QuestNode specific = unlockedQuest(p, "mb_specific", new MultiblockFormedTask(id("s"), text("s"), ebf, 1));
            QuestNode any = unlockedQuest(p, "mb_any", new MultiblockFormedTask(id("a"), text("a"), null, 2));

            QuestAPI.fireMultiblockFormed(p, ResourceLocation.fromNamespaceAndPath("gtceu", "large_boiler"), 1);
            assertState(h, p, specific, QuestState.UNLOCKED, "a different controller must not match");
            assertState(h, p, any, QuestState.UNLOCKED, "1 of 2 for the any-multiblock task");
            QuestAPI.fireMultiblockFormed(p, ebf, 1);
            assertState(h, p, specific, QuestState.COMPLETED, "the named controller formed");
            assertState(h, p, any, QuestState.COMPLETED, "2 multiblocks of any kind formed");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void optionalTasksDoNotBlockCompletion(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            CheckmarkTask required = new CheckmarkTask(id("required"), text("required"));
            CheckmarkTask optional = new CheckmarkTask(id("optional"), text("optional"));
            optional.setOptional(true);
            QuestNode quest = unlockedQuest(p, "optional", new QuestTask[] { required, optional });
            assertStaysOpen(h, p, quest, "the required task is not done");
            CheckmarkTask.complete(p, required.getTaskId());
            assertCompletes(h, p, quest, "an unfinished optional task must not block completion");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void minimumTaskCountCompletesWithoutAllTasks(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            CheckmarkTask a = new CheckmarkTask(id("a"), text("a"));
            CheckmarkTask b = new CheckmarkTask(id("b"), text("b"));
            CheckmarkTask c = new CheckmarkTask(id("c"), text("c"));
            QuestNode quest = unlockedQuest(p, "min_count", new QuestTask[] { a, b, c });
            quest.setTaskMinCount(2);
            CheckmarkTask.complete(p, a.getTaskId());
            assertStaysOpen(h, p, quest, "1 of the required 2 tasks");
            CheckmarkTask.complete(p, c.getTaskId());
            assertCompletes(h, p, quest, "2 of 3 tasks should satisfy a minimum of 2");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void progressIsPerPlayer(@NotNull GameTestHelper helper) {
        run(helper, (h, alice) -> {
            ServerPlayer bob = newPlayer(h);
            CheckmarkTask task = new CheckmarkTask(id("t"), text("check"));
            QuestNode quest = unlockedQuest(alice, "per_player", task);
            QuestProgressTracker.changeQuestState(bob, quest, QuestState.UNLOCKED);
            CheckmarkTask.complete(alice, task.getTaskId());
            assertCompletes(h, alice, quest, "alice completed her checkmark");
            assertStaysOpen(h, bob, quest, "bob's progress must not follow alice's");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void everyTaskTypeSurvivesSerialisation(@NotNull GameTestHelper helper) {
        run(helper, (h, p) -> {
            PhoenixTaskRegistry.registerBuiltins();
            ResourceLocation machine = ResourceLocation.fromNamespaceAndPath("gtceu", "electric_blast_furnace");
            List<QuestTask> tasks = new ArrayList<>();
            ItemRequirementTask item = new ItemRequirementTask(id("item_check"), text("item"), Items.IRON_INGOT, 3,
                    true);
            item.setNbtFilter(TagParser.parseTag("{components:{\"minecraft:custom_name\":'\"x\"'}}"));
            tasks.add(item);
            tasks.add(new FluidRequirementTask(id("fluid_check"), text("fluid"),
                    ResourceLocation.withDefaultNamespace("water"), 500, true));
            tasks.add(new TagItemTask(id("tag_item"), text("tag"), ItemTags.PLANKS, 4));
            tasks.add(new FilterItemTask(id("filter_item"), text("fi"),
                    ItemFilters.anyOf(ItemFilters.exact(Items.IRON_INGOT), ItemFilters.exact(Items.GOLD_INGOT)), 2,
                    false));
            tasks.add(new FilterFluidTask(id("filter_fluid"), text("ff"),
                    FluidFilters.exact(ResourceLocation.withDefaultNamespace("lava")), 250, false));
            tasks.add(new CraftItemTask(id("craft_item"), text("craft"), ResourceLocation.withDefaultNamespace("stick"),
                    2));
            tasks.add(new KillEntityTask(id("kill_entity"), text("kill"), "minecraft:zombie", 3, false));
            tasks.add(new BlockBreakTask(id("block_break"), text("break"), Blocks.STONE, 2));
            tasks.add(new BlockInteractTask(id("block_interact"), text("interact"), Blocks.LEVER, "RIGHT_CLICK", 2));
            tasks.add(new AdvancementTask(id("advancement"), text("adv"),
                    ResourceLocation.withDefaultNamespace("story/root")));
            tasks.add(new DimensionTask(id("dimension"), text("dim"), "minecraft:the_nether"));
            tasks.add(new BiomeTask(id("biome"), text("biome"), "minecraft:plains"));
            tasks.add(new StructureTask(id("structure"), text("structure"), "minecraft:village_plains"));
            tasks.add(new ExperienceTask(id("experience"), text("xp"), 7));
            tasks.add(new StatTrackerTask(id("stat"), text("stat"), ResourceLocation.withDefaultNamespace("jump"), 9,
                    false));
            tasks.add(new EnchantmentTask(id("enchantment"), text("ench"),
                    ResourceLocation.withDefaultNamespace("sharpness"), 4));
            tasks.add(new TimerTask(id("timer"), text("timer"), 30));
            tasks.add(new CheckmarkTask(id("checkmark"), text("check")));
            tasks.add(new InfoTask(id("info"), text("info"), "body text"));
            tasks.add(new ScreenOpenedTask(id("screen_opened"), text("screen")));
            tasks.add(new LocationOrTerminalTask(id("location_terminal"), text("terminal"), id("term"), true));
            tasks.add(new EnergyStorageTask(id("energy_check"), text("energy"), 1000,
                    EnergyStorageTask.EnergyType.EU, EnergyStorageTask.Source.HELD));
            tasks.add(new RecipeTask(id("recipe"), text("recipe"), ResourceLocation.parse("gtceu:macerator"),
                    ResourceLocation.parse("gtceu:macerator/x"), 3));
            tasks.add(new ExternalTriggerTask(id("external_trigger"), text("ext"), "mod:thing", 2));
            tasks.add(new MultiblockFormedTask(id("gt_multiblock"), text("mb"), machine, 2));
            tasks.add(new MultiblockFormedTask(id("gt_multiblock_any"), text("mbany"), null, 1));
            tasks.add(new ViewMachineTask(id("view_machine"), text("vm"), "gtceu:x", 4f));
            tasks.add(new ViewSceneTask(id("view_scene"), text("vs"), "scene_a", 4f));
            tasks.add(new ViewGuideTask(id("view_guide"), text("vg"), "guide_a"));
            tasks.add(new ArchiveEntryTask(id("archive_entry"), text("ae"), "phoenix_archive:log_001"));

            for (QuestTask task : tasks) {
                task.setOptional(false);
                task.setHiddenParts(QuestTask.HIDE_AMOUNT);
                CompoundTag saved = task.serializeWithMeta();
                saved.putString("task_id", task.getTaskId().toString());
                QuestTask loaded = PhoenixTaskRegistry.deserialize(saved);
                String name = task.getClass().getSimpleName();
                h.assertTrue(loaded != null, name + " should load back from its own save");
                h.assertTrue(loaded.getClass() == task.getClass(), name + " loaded as " + loaded.getClass());
                h.assertTrue(loaded.getHiddenParts() == QuestTask.HIDE_AMOUNT, name + " lost its hidden parts");
                h.assertTrue(loaded.serializeNBT().equals(task.serializeNBT()),
                        name + " changed on the round trip: " + task.serializeNBT() + " -> " + loaded.serializeNBT());
            }
            
            ItemRequirementTask reloaded = (ItemRequirementTask) PhoenixTaskRegistry.deserialize(withId(item));
            h.assertTrue(reloaded.getNbtFilter() != null && reloaded.getNbtFilter().equals(item.getNbtFilter()),
                    "the item task's NBT filter should survive");
        });
    }

    private static CompoundTag withId(QuestTask task) {
        CompoundTag tag = task.serializeWithMeta();
        tag.putString("task_id", task.getTaskId().toString());
        return tag;
    }
}

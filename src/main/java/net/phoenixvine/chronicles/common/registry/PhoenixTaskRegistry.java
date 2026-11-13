package net.phoenixvine.chronicles.common.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.filter.FluidFilters;
import net.phoenixvine.chronicles.common.filter.ItemFilters;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.common.tasks.TimerTask;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class PhoenixTaskRegistry {

    public record FieldDef(String id, String label, FieldType type, @Nullable String hint) {

        public enum FieldType {
            TEXT,
            INTEGER,
            BOOLEAN,
            ITEM_ID,
            ENTITY_ID,
            FLUID_ID,
            ITEM_FILTER,
            FLUID_FILTER
        }

        public static FieldDef text(String id, String label) {
            return new FieldDef(id, label, FieldType.TEXT, null);
        }

        public static FieldDef text(String id, String label, String hint) {
            return new FieldDef(id, label, FieldType.TEXT, hint);
        }

        public static FieldDef integer(String id, String label) {
            return new FieldDef(id, label, FieldType.INTEGER, null);
        }

        public static FieldDef itemId(String id, String label) {
            return new FieldDef(id, label, FieldType.ITEM_ID, null);
        }

        public static FieldDef entityId(String id, String label) {
            return new FieldDef(id, label, FieldType.ENTITY_ID, null);
        }

        public static FieldDef fluidId(String id, String label) {
            return new FieldDef(id, label, FieldType.FLUID_ID, null);
        }

        public static FieldDef bool(String id, String label) {
            return new FieldDef(id, label, FieldType.BOOLEAN, null);
        }

        public static FieldDef itemFilter(String id, String label) {
            return new FieldDef(id, label, FieldType.ITEM_FILTER, null);
        }

        public static FieldDef fluidFilter(String id, String label) {
            return new FieldDef(id, label, FieldType.FLUID_FILTER, null);
        }
    }

    public static final class ScriptTaskHandler {

        public BiPredicate<ScriptTask, Player> isCompletedFor = (t, p) -> false;
        public BiConsumer<ScriptTask, Player> tryConsume = (t, p) -> {};
        public BiFunction<ScriptTask, Player, String> progressString = (t, p) -> null;
        public boolean dependsOnInventory = true;
    }

    public record TaskEntry(
                            String typeId,
                            Function<CompoundTag, Object> deserializer,
                            @Nullable String editorIcon,
                            @Nullable String editorLabel,
                            @Nullable String editorTooltip,
                            List<FieldDef> fields,
                            @Nullable String requiredModId) {

        public boolean hasEditorMeta() {
            return editorLabel != null;
        }

        public boolean isAvailable() {
            return requiredModId == null || net.neoforged.fml.ModList.get().isLoaded(requiredModId);
        }
    }

    private static final Map<String, TaskEntry> REGISTRY = new LinkedHashMap<>();

    private static final List<TaskEntry> EDITOR_ORDER = new ArrayList<>();
    private static final Map<String, ScriptTaskHandler> SCRIPT_HANDLERS = new HashMap<>();

    public static Builder register(String typeId, Function<CompoundTag, Object> deserializer) {
        return new Builder(typeId, deserializer);
    }

    public static Builder registerScripted(String typeId) {
        ScriptTaskHandler handler = new ScriptTaskHandler();
        SCRIPT_HANDLERS.put(typeId, handler);
        Builder b = register(typeId, tag -> {
            ScriptTask t = new ScriptTask(taskId(tag), desc(tag), typeId);
            t.deserializeNBT(tag);
            return t;
        });
        b.scriptHandler = handler;
        return b;
    }

    @Nullable
    public static ScriptTaskHandler getScriptHandler(String typeId) {
        return SCRIPT_HANDLERS.get(typeId);
    }

    public static final class Builder {

        private final String typeId;
        private final Function<CompoundTag, Object> deserializer;
        private String icon = null;
        private String label = null;
        private String tooltip = null;
        private final List<FieldDef> fields = new ArrayList<>();
        private ScriptTaskHandler scriptHandler = null;
        private String requiredModId = null;

        private Builder(String typeId, Function<CompoundTag, Object> deserializer) {
            this.typeId = typeId;
            this.deserializer = deserializer;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder tooltip(String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public Builder field(FieldDef f) {
            this.fields.add(f);
            return this;
        }

        public Builder requiresMod(String modId) {
            this.requiredModId = modId;
            return this;
        }

        public Builder onCompleted(BiPredicate<ScriptTask, Player> fn) {
            requireScriptHandler().isCompletedFor = fn;
            return this;
        }

        public Builder onConsume(BiConsumer<ScriptTask, Player> fn) {
            requireScriptHandler().tryConsume = fn;
            return this;
        }

        public Builder progressString(BiFunction<ScriptTask, Player, String> fn) {
            requireScriptHandler().progressString = fn;
            return this;
        }

        public Builder dependsOnInventory(boolean v) {
            requireScriptHandler().dependsOnInventory = v;
            return this;
        }

        private ScriptTaskHandler requireScriptHandler() {
            if (scriptHandler == null) {
                throw new IllegalStateException(
                        "onCompleted/onConsume/progressString/dependsOnInventory can only be used on a " +
                                "Builder from registerScripted(...), not register(...)");
            }
            return scriptHandler;
        }

        public void register() {
            TaskEntry entry = new TaskEntry(typeId, deserializer, icon, label, tooltip,
                    Collections.unmodifiableList(new ArrayList<>(fields)), requiredModId);
            REGISTRY.put(typeId, entry);
            if (entry.hasEditorMeta()) EDITOR_ORDER.add(entry);
        }
    }

    @Nullable
    public static TaskEntry get(String typeId) {
        return REGISTRY.get(typeId);
    }

    public static List<TaskEntry> getEditorTypes() {
        List<TaskEntry> out = new ArrayList<>();
        for (TaskEntry e : EDITOR_ORDER) {
            if (e.isAvailable()) out.add(e);
        }
        return Collections.unmodifiableList(out);
    }

    @Nullable
    public static QuestTask deserialize(CompoundTag tag) {
        String typeId = tag.getString("type");
        TaskEntry entry = REGISTRY.get(typeId);
        if (entry == null) return null;
        try {

            QuestTask task = (QuestTask) entry.deserializer().apply(tag);
            if (task != null && tag.contains("hide")) task.setHiddenParts(tag.getInt("hide"));
            return task;
        } catch (Exception e) {
            PhoenixChronicles.LOGGER.error("Failed to deserialize task type '{}' (task_id={})", typeId,
                    tag.contains("task_id") ? tag.getString("task_id") : "?", e);
            return null;
        }
    }

    private static boolean builtinsRegistered = false;

    public static void registerBuiltins() {
        if (builtinsRegistered) return;
        builtinsRegistered = true;
        registerPlayerActionTasks();
        registerWorldAndStatusTasks();
        registerStorageAndFilterTasks();
        registerViewAndIntegrationTasks();
    }

    private static void registerPlayerActionTasks() {
        register("kill_entity", tag -> {
            KillEntityTask t = new KillEntityTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "pig"), 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§c☠").label("phoenix_chronicles.task_type.kill_entity.label")
                .tooltip("phoenix_chronicles.task_type.kill_entity.tooltip")
                .register();

        register("item_check", tag -> {
            ItemRequirementTask t = new ItemRequirementTask(taskId(tag), desc(tag),
                    net.minecraft.world.item.Items.DIRT, 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§e■").label("phoenix_chronicles.task_type.item_check.label")
                .tooltip("phoenix_chronicles.task_type.item_check.tooltip")
                .register();

        register("craft_item", tag -> {
            CraftItemTask t = new CraftItemTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "dirt"), 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§6⚒").label("phoenix_chronicles.task_type.craft_item.label")
                .tooltip("phoenix_chronicles.task_type.craft_item.tooltip")
                .register();

        register("recipe", tag -> {
            RecipeTask t = new RecipeTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "smelting"), null, 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§b⚙").label("phoenix_chronicles.task_type.recipe.label")
                .tooltip("phoenix_chronicles.task_type.recipe.tooltip")
                .register();

        register("gt_multiblock", tag -> {
            MultiblockFormedTask t = new MultiblockFormedTask(taskId(tag), desc(tag), null, 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§6⬡").label("phoenix_chronicles.task_type.gt_multiblock.label")
                .tooltip("phoenix_chronicles.task_type.gt_multiblock.tooltip")
                .requiresMod("gtceu")
                .register();

        register("experience", tag -> {
            ExperienceTask t = new ExperienceTask(taskId(tag), desc(tag), 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§a✦").label("phoenix_chronicles.task_type.experience.label")
                .tooltip("phoenix_chronicles.task_type.experience.tooltip")
                .register();

        register("location_terminal", tag -> {
            LocationOrTerminalTask t = new LocationOrTerminalTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "air"), false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§b◎").label("phoenix_chronicles.task_type.location_terminal.label")
                .tooltip("phoenix_chronicles.task_type.location_terminal.tooltip")
                .register();

        register("advancement", tag -> {
            AdvancementTask t = new AdvancementTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "story/root"));
            t.deserializeNBT(tag);
            return t;
        }).icon("§d★").label("phoenix_chronicles.task_type.advancement.label")
                .tooltip("phoenix_chronicles.task_type.advancement.tooltip")
                .register();

        register("block_interact", tag -> {
            BlockInteractTask t = new BlockInteractTask(taskId(tag), desc(tag),
                    net.minecraft.world.level.block.Blocks.STONE, "PLACE");
            t.deserializeNBT(tag);
            return t;
        }).icon("§7□").label("phoenix_chronicles.task_type.block_interact.label")
                .tooltip("phoenix_chronicles.task_type.block_interact.tooltip")
                .register();
    }

    private static void registerWorldAndStatusTasks() {
        register("fluid_check", tag -> {
            FluidRequirementTask t = new FluidRequirementTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "water"), 1000, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§3≋").label("phoenix_chronicles.task_type.fluid_check.label")
                .tooltip("phoenix_chronicles.task_type.fluid_check.tooltip")
                .register();

        register("stat", tag -> {
            StatTrackerTask t = new StatTrackerTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "jump"), 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§9≡").label("phoenix_chronicles.task_type.stat.label")
                .tooltip("phoenix_chronicles.task_type.stat.tooltip")
                .register();

        register("dimension", tag -> {
            DimensionTask t = new DimensionTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")));
            t.deserializeNBT(tag);
            return t;
        }).icon("§5⊕").label("phoenix_chronicles.task_type.dimension.label")
                .tooltip("phoenix_chronicles.task_type.dimension.tooltip")
                .register();

        register("biome", tag -> {
            BiomeTask t = new BiomeTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "plains"));
            t.deserializeNBT(tag);
            return t;
        }).icon("§2⛰").label("phoenix_chronicles.task_type.biome.label")
                .tooltip("phoenix_chronicles.task_type.biome.tooltip")
                .register();

        register("structure", tag -> {
            StructureTask t = new StructureTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "village"));
            t.deserializeNBT(tag);
            return t;
        }).icon("§6⌂").label("phoenix_chronicles.task_type.structure.label")
                .tooltip("phoenix_chronicles.task_type.structure.tooltip")
                .register();

        register("checkmark", tag -> {
            CheckmarkTask t = new CheckmarkTask(taskId(tag), desc(tag));
            t.deserializeNBT(tag);
            return t;
        }).icon("§a✓").label("phoenix_chronicles.task_type.checkmark.label")
                .tooltip("phoenix_chronicles.task_type.checkmark.tooltip")
                .register();

        register("tag_item", tag -> {
            TagItemTask t = new TagItemTask(taskId(tag), desc(tag),
                    net.minecraft.tags.ItemTags
                            .create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "gems")),
                    1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§e◈").label("phoenix_chronicles.task_type.tag_item.label")
                .tooltip("phoenix_chronicles.task_type.tag_item.tooltip")
                .register();

        register("info", tag -> {
            InfoTask t = new InfoTask(taskId(tag), desc(tag), "");
            t.deserializeNBT(tag);
            return t;
        }).icon("§7§l!").label("phoenix_chronicles.task_type.info.label")
                .tooltip("phoenix_chronicles.task_type.info.tooltip")
                .register();

        register("timer", tag -> {
            TimerTask t = new TimerTask(taskId(tag),
                    desc(tag), 300);
            t.deserializeNBT(tag);
            return t;
        }).icon("§b⏱").label("phoenix_chronicles.task_type.timer.label")
                .tooltip("phoenix_chronicles.task_type.timer.tooltip")
                .register();

        register("energy_check", tag -> {
            EnergyStorageTask t = new EnergyStorageTask(taskId(tag), desc(tag),
                    10000L, EnergyStorageTask.EnergyType.FE, EnergyStorageTask.Source.INVENTORY);
            t.deserializeNBT(tag);
            return t;
        }).icon("§6⚡").label("phoenix_chronicles.task_type.energy_check.label")
                .tooltip("phoenix_chronicles.task_type.energy_check.tooltip")
                .register();
    }

    private static void registerStorageAndFilterTasks() {
        register("ae2_item_storage", tag -> {
            ItemRequirementTask t = new ItemRequirementTask(taskId(tag), desc(tag),
                    net.minecraft.world.item.Items.DIRT, 1, false);
            t.deserializeNBT(tag);
            t.setCheckAe2Storage(true);
            return t;
        }).register();

        register("ae2_fluid_storage", tag -> {
            FluidRequirementTask t = new FluidRequirementTask(taskId(tag), desc(tag),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "water"), 1000, false);
            t.deserializeNBT(tag);
            t.setCheckAe2Storage(true);
            return t;
        }).register();

        register("filter_item", tag -> {
            FilterItemTask t = new FilterItemTask(taskId(tag), desc(tag),
                    ItemFilters.tag("c:gems"), 1, false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§e◇").label("phoenix_chronicles.task_type.filter_item.label")
                .tooltip("phoenix_chronicles.task_type.filter_item.tooltip")
                .register();

        register("filter_fluid", tag -> {
            FilterFluidTask t = new FilterFluidTask(taskId(tag), desc(tag),
                    FluidFilters.exact(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "water")),
                    1000,
                    false);
            t.deserializeNBT(tag);
            return t;
        }).icon("§3≋").label("phoenix_chronicles.task_type.filter_fluid.label")
                .tooltip("phoenix_chronicles.task_type.filter_fluid.tooltip")
                .register();

        register("block_break", tag -> {
            BlockBreakTask t = new BlockBreakTask(taskId(tag), desc(tag),
                    net.minecraft.world.level.block.Blocks.STONE, 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§7✕").label("phoenix_chronicles.task_type.block_break.label")
                .tooltip("phoenix_chronicles.task_type.block_break.tooltip")
                .register();

        register("enchantment", tag -> {
            EnchantmentTask t = new EnchantmentTask(
                    taskId(tag), desc(tag), net.minecraft.resources.ResourceLocation.parse("minecraft:sharpness"), 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§b✦").label("phoenix_chronicles.task_type.enchantment.label")
                .tooltip("phoenix_chronicles.task_type.enchantment.tooltip")
                .register();
    }

    private static void registerViewAndIntegrationTasks() {
        register("view_machine", tag -> {
            ViewMachineTask t = new ViewMachineTask(taskId(tag), desc(tag), "", 3.0f);
            t.deserializeNBT(tag);
            return t;
        }).icon("§b⬡").label("phoenix_chronicles.task_type.view_machine.label")
                .tooltip("phoenix_chronicles.task_type.view_machine.tooltip")
                .requiresMod("phantasia")
                .register();

        register("view_scene", tag -> {
            ViewSceneTask t = new ViewSceneTask(taskId(tag), desc(tag), "", 5.0f);
            t.deserializeNBT(tag);
            return t;
        }).icon("§b⬢").label("phoenix_chronicles.task_type.view_scene.label")
                .tooltip("phoenix_chronicles.task_type.view_scene.tooltip")
                .requiresMod("phantasia")
                .register();

        register("view_guide", tag -> {
            ViewGuideTask t = new ViewGuideTask(taskId(tag), desc(tag), "");
            t.deserializeNBT(tag);
            return t;
        }).icon("§b📖").label("phoenix_chronicles.task_type.view_guide.label")
                .tooltip("phoenix_chronicles.task_type.view_guide.tooltip")
                .requiresMod("phantasia")
                .register();

        register("archive_entry", tag -> {
            ArchiveEntryTask t = new ArchiveEntryTask(
                    taskId(tag), desc(tag), "");
            t.deserializeNBT(tag);
            return t;
        }).icon("§b📖").label("phoenix_chronicles.task_type.archive_entry.label")
                .tooltip("phoenix_chronicles.task_type.archive_entry.tooltip")
                .requiresMod("phoenix_archive")
                .register();

        register("external_trigger", tag -> {
            ExternalTriggerTask t = new ExternalTriggerTask(taskId(tag), desc(tag), "", 1);
            t.deserializeNBT(tag);
            return t;
        }).icon("§d⚡").label("phoenix_chronicles.task_type.external_trigger.label")
                .tooltip("phoenix_chronicles.task_type.external_trigger.tooltip")
                .register();

        register("conflux_research", tag -> {
            ConfluxResearchTask t = new ConfluxResearchTask(
                    taskId(tag), desc(tag));
            t.deserializeNBT(tag);
            return t;
        }).icon("§b☉").label("phoenix_chronicles.task_type.conflux_research.label")
                .tooltip("phoenix_chronicles.task_type.conflux_research.tooltip")
                .field(FieldDef.bool("flag_mode", "Check Flag Instead of Node"))
                .field(FieldDef.text("node_id", "Research Node ID", "e.g. phoenixcore:some_node"))
                .field(FieldDef.text("flag", "Research Flag"))
                .requiresMod("phoenixcore")
                .register();

        register("screen_opened", tag -> {
            ScreenOpenedTask t = new ScreenOpenedTask(
                    taskId(tag), desc(tag));
            t.deserializeNBT(tag);
            return t;
        }).icon("§d▣").label("phoenix_chronicles.task_type.screen_opened.label")
                .tooltip("phoenix_chronicles.task_type.screen_opened.tooltip")
                .register();
    }

    private static net.minecraft.resources.ResourceLocation taskId(CompoundTag tag) {
        try {
            return net.minecraft.resources.ResourceLocation.parse(tag.getString("task_id"));
        } catch (Exception e) {
            return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(PhoenixChronicles.MOD_ID,
                    "task_" + UUID.randomUUID().toString().substring(0, 8));
        }
    }

    private static net.minecraft.network.chat.Component desc(CompoundTag tag) {
        try {
            if (tag.contains("description")) {
                net.minecraft.network.chat.Component c = net.minecraft.network.chat.Component.Serializer
                        .fromJson(tag.getString("description"), net.minecraft.core.RegistryAccess.EMPTY);
                if (c != null) return c;
            }
        } catch (Exception ignored) {}
        return net.minecraft.network.chat.Component.literal(tag.getString("type"));
    }

    private PhoenixTaskRegistry() {}
}

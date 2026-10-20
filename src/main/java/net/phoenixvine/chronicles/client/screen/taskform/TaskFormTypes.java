package net.phoenixvine.chronicles.client.screen.taskform;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.phoenixvine.chronicles.QuestAPI;
import net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormType.SecondPicker;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormType.TargetPicker;
import net.phoenixvine.chronicles.common.filter.FluidFilters;
import net.phoenixvine.chronicles.common.filter.IFluidFilter;
import net.phoenixvine.chronicles.common.filter.IItemFilter;
import net.phoenixvine.chronicles.common.filter.ItemFilters;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.common.tasks.TimerTask;
import net.phoenixvine.chronicles.integration.archive.ArchiveLoreCompat;
import net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat;
import net.phoenixvine.chronicles.integration.gtm.GtmMachines;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TaskFormTypes {

    private static final Map<String, TaskFormType> BY_ID = new LinkedHashMap<>();

    private TaskFormTypes() {}

    public static TaskFormType get(String typeId) {
        TaskFormType type = BY_ID.get(typeId);
        return type != null ? type : generic(typeId);
    }

    public static String idFor(QuestTask task) {
        if (task instanceof ExternalTriggerTask ext && ext.getKjsTypeId() != null) return ext.getKjsTypeId();
        for (TaskFormType type : BY_ID.values()) {
            if (type.handles(task)) return type.id();
        }
        return "checkmark";
    }

    @Nullable
    public static String detail(QuestTask task) {
        TaskFormType type = BY_ID.get(idFor(task));
        return type == null ? null : detail(type, task);
    }

    public static void applySticky(QuestTask task, boolean sticky) {
        if (BY_ID.containsKey(idFor(task))) setSticky(task, sticky);
    }

    private static TaskFormType generic(String typeId) {
        PhoenixTaskRegistry.TaskEntry entry = PhoenixTaskRegistry.get(typeId);
        TaskFormType form = new TaskFormType(typeId, ExternalTriggerTask.class);
        form.generic = true;
        if (entry == null) {
            form.target("§8Target id", TargetPicker.NONE);
            form.canCreate = false;
            return form;
        }

        String targetLabel = null;
        String countLabel = null;
        for (PhoenixTaskRegistry.FieldDef f : entry.fields()) {
            if (f.type() == PhoenixTaskRegistry.FieldDef.FieldType.INTEGER) {
                if (countLabel == null) countLabel = f.label();
            } else if (f.type() != PhoenixTaskRegistry.FieldDef.FieldType.BOOLEAN && targetLabel == null) {
                targetLabel = "§8" + f.label() + (f.hint() != null ? "  (" + f.hint() + ")" : "");
            }
        }
        if (entry.fields().isEmpty() || targetLabel != null)
            form.target(targetLabel != null ? targetLabel : "§8Target id", TargetPicker.NONE);
        if (countLabel != null) form.count("§8" + countLabel);
        return form;
    }

    private static TaskFormType define(String id, Class<? extends QuestTask> taskClass) {
        TaskFormType type = new TaskFormType(id, taskClass);
        BY_ID.put(id, type);
        return type;
    }

    @Nullable
    private static <T> Collection<ResourceLocation> registryKeys(ResourceKey<? extends Registry<T>> key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var reg = mc.level.registryAccess().registry(key);
            if (reg.isPresent()) return reg.get().keySet();
        }
        if (mc.getSingleplayerServer() != null) {
            var reg = mc.getSingleplayerServer().registryAccess().registry(key);
            if (reg.isPresent()) return reg.get().keySet();
        }
        return null;
    }

    @Nullable
    private static Collection<ResourceLocation> advancementIds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return null;
        return mc.player.connection.getAdvancements().getTree().nodes().stream()
                .map(node -> node.holder().id()).toList();
    }

    private static String itemName(Item item) {
        return item.getDefaultInstance().getHoverName().getString();
    }

    private static String blockId(net.minecraft.world.level.block.Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null ? id.toString() : "";
    }

    private static boolean isPlainItemIdList(IItemFilter f) {
        if (f instanceof ItemFilters.ExactItem ex) return ex.nbt() == null || ex.nbt().isEmpty();
        if (f instanceof ItemFilters.AnyOf any) {
            for (IItemFilter child : any.children()) {
                if (!isPlainItemIdList(child)) return false;
            }
            return true;
        }
        return false;
    }

    private static boolean isPlainFluidIdList(IFluidFilter f) {
        if (f instanceof FluidFilters.ExactFluid ex) return ex.nbt() == null || ex.nbt().isEmpty();
        if (f instanceof FluidFilters.AnyOf any) {
            for (IFluidFilter child : any.children()) {
                if (!isPlainFluidIdList(child)) return false;
            }
            return true;
        }
        return false;
    }

    private static String itemFilterAsIdList(IItemFilter f) {
        if (f instanceof ItemFilters.ExactItem ex) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(ex.item());
            return id != null ? id.toString() : "";
        }
        if (f instanceof ItemFilters.AnyOf any) {
            List<String> ids = new ArrayList<>();
            for (IItemFilter child : any.children()) {
                String s = itemFilterAsIdList(child);
                if (!s.isEmpty()) ids.add(s);
            }
            return String.join(";", ids);
        }
        return f.describe();
    }

    private static String fluidFilterAsIdList(IFluidFilter f) {
        if (f instanceof FluidFilters.ExactFluid ex) return ex.fluidId().toString();
        if (f instanceof FluidFilters.AnyOf any) {
            List<String> ids = new ArrayList<>();
            for (var child : any.children()) {
                String s = fluidFilterAsIdList(child);
                if (!s.isEmpty()) ids.add(s);
            }
            return String.join(";", ids);
        }
        return f.describe();
    }

    @Nullable
    private static IItemFilter parseItemFilter(String target) {
        List<IItemFilter> alts = new ArrayList<>();
        for (String part : target.split(";")) {
            String id = part.trim();
            if (id.isEmpty()) continue;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item != null && item != Items.AIR) alts.add(ItemFilters.exact(item));
        }
        if (alts.isEmpty()) return null;
        return alts.size() == 1 ? alts.get(0) : ItemFilters.anyOf(alts.toArray(new IItemFilter[0]));
    }

    @Nullable
    private static IFluidFilter parseFluidFilter(String target) {
        List<IFluidFilter> alts = new ArrayList<>();
        for (String part : target.split(";")) {
            String id = part.trim();
            if (id.isEmpty()) continue;
            var fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
            if (fluid != null && fluid != Fluids.EMPTY) alts.add(FluidFilters.exact(ResourceLocation.parse(id)));
        }
        if (alts.isEmpty()) return null;
        return alts.size() == 1 ? alts.get(0) : FluidFilters.anyOf(alts.toArray(new IFluidFilter[0]));
    }

    @Nullable
    static Collection<ResourceLocation> registryIds(TaskFormType type) {
        return switch (type.id()) {
            case "advancement" -> advancementIds();
            case "screen_opened" -> ExternalScreenRegistry.registeredIds();
            case "enchantment" -> registryKeys(Registries.ENCHANTMENT);
            case "stat" -> BuiltInRegistries.CUSTOM_STAT.keySet();
            case "biome" -> registryKeys(Registries.BIOME);
            case "structure" -> registryKeys(Registries.STRUCTURE);
            case "tag_item" -> BuiltInRegistries.ITEM.getTagNames().map(TagKey::location).toList();
            default -> null;
        };
    }

    @Nullable
    static Collection<String> stringIds(TaskFormType type) {
        return switch (type.id()) {
            case "gt_multiblock" -> GTCEuCompat.isAvailable() ? GtmMachines.multiblockControllerIds() : null;
            case "external_trigger" -> QuestAPI.getRegisteredExternalTriggers().keySet();
            case "view_machine" -> PhantasiaCompat.isAvailable() ? PhantasiaCompat.allMachineIds() : null;
            case "view_scene" -> PhantasiaCompat.isAvailable() ? PhantasiaCompat.allSceneIds() : null;
            case "view_guide" -> PhantasiaCompat.isAvailable() ? PhantasiaCompat.allGuideIds() : null;
            case "archive_entry" -> ArchiveLoreCompat.isAvailable() ? ArchiveLoreCompat.allEntryIds() : null;
            default -> null;
        };
    }

    private static boolean isSticky(QuestTask task) {
        if (task instanceof ItemRequirementTask t) return t.isSticky();
        if (task instanceof FluidRequirementTask t) return t.isSticky();
        if (task instanceof TagItemTask t) return t.isSticky();
        if (task instanceof EnergyStorageTask t) return t.isSticky();
        if (task instanceof FilterItemTask t) return t.isSticky();
        if (task instanceof FilterFluidTask t) return t.isSticky();
        return true;
    }

    static void setSticky(QuestTask task, boolean sticky) {
        if (task instanceof ItemRequirementTask t) t.setSticky(sticky);
        else if (task instanceof FluidRequirementTask t) t.setSticky(sticky);
        else if (task instanceof TagItemTask t) t.setSticky(sticky);
        else if (task instanceof EnergyStorageTask t) t.setSticky(sticky);
        else if (task instanceof FilterItemTask t) t.setSticky(sticky);
        else if (task instanceof FilterFluidTask t) t.setSticky(sticky);
    }

    private static boolean isCheckAe2(QuestTask task) {
        if (task instanceof ItemRequirementTask t) return t.isCheckAe2Storage();
        if (task instanceof FluidRequirementTask t) return t.isCheckAe2Storage();
        if (task instanceof FilterItemTask t) return t.isCheckAe2Storage();
        if (task instanceof FilterFluidTask t) return t.isCheckAe2Storage();
        return false;
    }

    @Nullable
    static QuestTask create(TaskFormType type, TaskFormValues v) throws Exception {
        if (type.generic) return type.canCreate ? createGeneric(type, v) : null;
        return switch (type.id()) {
            case "kill_entity" -> {
                var m = TaskIdMatcher.parse(v.target);
                yield m.isEmpty() ? null : new KillEntityTask(v.taskId, v.description, m.spec(), v.intCount(),
                        v.consume);
            }
            case "item_check" -> createItemCheck(v);
            case "craft_item" -> new CraftItemTask(v.taskId, v.description, ResourceLocation.parse(v.target),
                    v.intCount());
            case "recipe" -> {
                ResourceLocation recipeType = ResourceLocation.tryParse(v.target);
                ResourceLocation recipe = v.second.isEmpty() ? null : ResourceLocation.tryParse(v.second);
                yield recipeType != null ?
                        new RecipeTask(v.taskId, v.description, recipeType, recipe, v.intCount()) : null;
            }
            case "gt_multiblock" -> {
                String machine = v.target.trim();
                boolean any = machine.equals(MultiblockFormedTask.ANY);
                ResourceLocation id = any ? null : ResourceLocation.tryParse(machine);
                yield id != null || any ? new MultiblockFormedTask(v.taskId, v.description, id, v.intCount()) :
                        null;
            }
            case "experience" -> new ExperienceTask(v.taskId, v.description, v.intCount());
            case "location_terminal" -> new LocationOrTerminalTask(v.taskId, v.description,
                    ResourceLocation.parse(v.target), v.consume);
            case "advancement" -> new AdvancementTask(v.taskId, v.description, ResourceLocation.parse(v.target));
            case "block_interact" -> {
                var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(v.target));
                String mode = v.second.isEmpty() ? "PLACE" : v.second.toUpperCase();
                yield block != null ? new BlockInteractTask(v.taskId, v.description, block, mode, v.intCount()) :
                        null;
            }
            case "block_break" -> {
                var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(v.target));
                yield block != null ? new BlockBreakTask(v.taskId, v.description, block, v.intCount()) : null;
            }
            case "enchantment" -> new EnchantmentTask(v.taskId, v.description, ResourceLocation.parse(v.target),
                    v.intCount());
            case "fluid_check" -> {
                FluidRequirementTask task = new FluidRequirementTask(v.taskId, v.description,
                        ResourceLocation.parse(v.target), v.intCount(), v.consume);
                task.setCheckAe2Storage(v.checkAe2);
                task.setSource(v.fluidSource);
                yield task;
            }
            case "stat" -> new StatTrackerTask(v.taskId, v.description, ResourceLocation.parse(v.target),
                    v.intCount(), v.consume);
            case "dimension" -> {
                String dim = v.second.isEmpty() ? "minecraft:overworld" : v.second;
                var m = TaskIdMatcher.parse(dim, false);
                yield m.isEmpty() ? null : new DimensionTask(v.taskId, v.description, m.spec());
            }
            case "biome" -> {
                var m = TaskIdMatcher.parse(v.target);
                yield m.isEmpty() ? null : new BiomeTask(v.taskId, v.description, m.spec());
            }
            case "structure" -> {
                var m = TaskIdMatcher.parse(v.target);
                yield m.isEmpty() ? null : new StructureTask(v.taskId, v.description, m.spec());
            }
            case "checkmark" -> new CheckmarkTask(v.taskId, v.description);
            case "timer" -> new TimerTask(v.taskId, v.description, v.intCount());
            case "tag_item" -> new TagItemTask(v.taskId, v.description,
                    ItemTags.create(ResourceLocation.parse(v.target)),
                    v.intCount());
            case "info" -> new InfoTask(v.taskId, v.description, v.target);
            case "external_trigger" -> new ExternalTriggerTask(v.taskId, v.description, v.target, v.intCount());
            case "view_machine" -> new ViewMachineTask(v.taskId, v.description, v.target, (float) v.intCount());
            case "view_scene" -> new ViewSceneTask(v.taskId, v.description, v.target, (float) v.intCount());
            case "view_guide" -> new ViewGuideTask(v.taskId, v.description, v.target);
            case "archive_entry" -> new ArchiveEntryTask(v.taskId, v.description, v.target);
            case "screen_opened" -> new ScreenOpenedTask(v.taskId, v.description, v.target);
            case "energy_check" -> createEnergy(v);
            case "filter_item" -> createFilterItem(v);
            case "filter_fluid" -> createFilterFluid(v);
            default -> null;
        };
    }

    private static QuestTask createGeneric(TaskFormType type, TaskFormValues v) {
        ExternalTriggerTask ext = new ExternalTriggerTask(v.taskId, v.description, v.target, v.intCount());
        ext.setKjsTypeId(type.id());
        return ext;
    }

    @Nullable
    private static QuestTask createItemCheck(TaskFormValues v) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(v.target));
        if (item == null) return null;
        ItemRequirementTask task = new ItemRequirementTask(v.taskId, v.description, item, v.intCount(), v.consume);
        String nbtText = v.nbt.trim();
        if (!nbtText.isEmpty()) {
            try {
                task.setNbtFilter(TagParser.parseTag(nbtText));
            } catch (Exception ignored) {}
        }
        task.setCheckAe2Storage(v.checkAe2);
        return task;
    }

    private static QuestTask createEnergy(TaskFormValues v) {
        var type = EnergyStorageTask.EnergyType.FE;
        if (!v.target.isBlank()) {
            try {
                type = EnergyStorageTask.EnergyType.valueOf(v.target.trim().toUpperCase());
            } catch (Exception ignored) {}
        }
        var source = EnergyStorageTask.Source.INVENTORY;
        if (!v.second.isBlank()) {
            try {
                source = EnergyStorageTask.Source.valueOf(v.second.trim().toUpperCase());
            } catch (Exception ignored) {}
        }
        return new EnergyStorageTask(v.taskId, v.description, v.intCount(), type, source);
    }

    @Nullable
    private static QuestTask createFilterItem(TaskFormValues v) {
        IItemFilter filter = v.itemFilter != null ? v.itemFilter : parseItemFilter(v.target);
        if (filter == null) return null;
        FilterItemTask task = new FilterItemTask(v.taskId, v.description, filter, v.intCount(), v.consume);
        task.setCheckAe2Storage(v.checkAe2);
        return task;
    }

    @Nullable
    private static QuestTask createFilterFluid(TaskFormValues v) {
        IFluidFilter filter = v.fluidFilter != null ? v.fluidFilter : parseFluidFilter(v.target);
        if (filter == null) return null;
        FilterFluidTask task = new FilterFluidTask(v.taskId, v.description, filter, v.intCount(), v.consume);
        task.setCheckAe2Storage(v.checkAe2);
        return task;
    }

    static void load(TaskFormType type, QuestTask task, TaskFormValues v) {
        if (type.showSticky) v.sticky = isSticky(task);
        if (type.showAe2) v.checkAe2 = isCheckAe2(task);

        if (type.generic) {
            ExternalTriggerTask ext = (ExternalTriggerTask) task;
            v.target = ext.getTriggerId();
            if (type.canCreate) v.count = ext.getRequired();
            return;
        }

        switch (type.id()) {
            case "kill_entity" -> {
                KillEntityTask t = (KillEntityTask) task;
                v.target = t.getSpec();
                v.count = t.getRequiredCount();
                v.consume = t.shouldConsume();
            }
            case "item_check" -> {
                ItemRequirementTask t = (ItemRequirementTask) task;
                ResourceLocation id = t.getItem() != null ? BuiltInRegistries.ITEM.getKey(t.getItem()) : null;
                v.target = id != null ? id.toString() : "";
                v.count = t.getRequiredCount();
                v.consume = t.shouldConsume();
                v.nbt = t.getNbtFilter() != null ? t.getNbtFilter().toString() : "";
            }
            case "craft_item" -> {
                CraftItemTask t = (CraftItemTask) task;
                v.target = t.getItemId().toString();
                v.count = t.getRequiredCount();
            }
            case "recipe" -> {
                RecipeTask t = (RecipeTask) task;
                v.target = t.getRecipeType().toString();
                v.second = t.getRecipeId() != null ? t.getRecipeId().toString() : "";
                v.count = t.getRequired();
            }
            case "gt_multiblock" -> {
                MultiblockFormedTask t = (MultiblockFormedTask) task;
                v.target = t.getMachineId() != null ? t.getMachineId().toString() : MultiblockFormedTask.ANY;
                v.count = t.getRequired();
            }
            case "experience" -> v.count = ((ExperienceTask) task).getRequiredLevel();
            case "location_terminal" -> {
                LocationOrTerminalTask t = (LocationOrTerminalTask) task;
                v.target = t.getTargetTerminalId().toString();
                v.consume = t.shouldConsume();
            }
            case "advancement" -> v.target = ((AdvancementTask) task).getAdvancementId().toString();
            case "block_interact" -> {
                BlockInteractTask t = (BlockInteractTask) task;
                v.target = blockId(t.getTargetBlock());
                v.second = t.getMode();
                v.count = t.getRequired();
            }
            case "block_break" -> {
                BlockBreakTask t = (BlockBreakTask) task;
                v.target = blockId(t.getTargetBlock());
                v.count = t.getRequired();
            }
            case "enchantment" -> {
                EnchantmentTask t = (EnchantmentTask) task;
                v.target = t.getEnchantmentId().toString();
                v.count = t.getRequiredLevel();
            }
            case "fluid_check" -> {
                FluidRequirementTask t = (FluidRequirementTask) task;
                v.target = t.getFluidId().toString();
                v.count = t.getRequiredAmount();
                v.consume = t.shouldConsume();
                v.fluidSource = t.getSource();
            }
            case "stat" -> {
                StatTrackerTask t = (StatTrackerTask) task;
                v.target = t.getStatId().toString();
                v.count = t.getTargetValue();
                v.consume = t.shouldConsume();
            }
            case "dimension" -> v.second = ((DimensionTask) task).getSpec();
            case "biome" -> v.target = ((BiomeTask) task).getSpec();
            case "structure" -> v.target = ((StructureTask) task).getSpec();
            case "timer" -> v.count = ((TimerTask) task).getDurationSeconds();
            case "tag_item" -> {
                TagItemTask t = (TagItemTask) task;
                v.target = t.getTag().location().toString();
                v.count = t.getRequired();
            }
            case "info" -> v.target = ((InfoTask) task).getBody();
            case "external_trigger" -> {
                ExternalTriggerTask t = (ExternalTriggerTask) task;
                v.target = t.getTriggerId();
                v.count = t.getRequired();
            }
            case "view_machine" -> {
                ViewMachineTask t = (ViewMachineTask) task;
                v.target = t.getMachineId();
                v.count = (int) t.getMinSeconds();
            }
            case "view_scene" -> {
                ViewSceneTask t = (ViewSceneTask) task;
                v.target = t.getSceneId();
                v.count = (int) t.getMinSeconds();
            }
            case "view_guide" -> v.target = ((ViewGuideTask) task).getGuideId();
            case "archive_entry" -> v.target = ((ArchiveEntryTask) task).getArchiveEntryId();
            case "screen_opened" -> v.target = ((ScreenOpenedTask) task).getScreenId();
            case "energy_check" -> {
                EnergyStorageTask t = (EnergyStorageTask) task;
                v.target = t.getEnergyType().name();
                v.second = t.getSource().name();
                v.count = t.getRequiredEnergy();
            }
            case "filter_item" -> {
                FilterItemTask t = (FilterItemTask) task;
                v.target = itemFilterAsIdList(t.getFilter());
                if (!isPlainItemIdList(t.getFilter())) v.itemFilter = t.getFilter();
                v.count = t.getCount();
                v.consume = t.isConsume();
            }
            case "filter_fluid" -> {
                FilterFluidTask t = (FilterFluidTask) task;
                v.target = fluidFilterAsIdList(t.getFilter());
                if (!isPlainFluidIdList(t.getFilter())) v.fluidFilter = t.getFilter();
                v.count = t.getAmount();
                v.consume = t.isConsume();
            }
            default -> {}
        }
    }

    @Nullable
    static String detail(TaskFormType type, QuestTask task) {
        return switch (type.id()) {
            case "kill_entity" -> {
                KillEntityTask t = (KillEntityTask) task;
                yield t.getMatcher().displayName() + " ×" + t.getRequiredCount();
            }
            case "item_check" -> {
                ItemRequirementTask t = (ItemRequirementTask) task;
                yield t.getItem() != null ? itemName(t.getItem()) + " ×" + t.getRequiredCount() : null;
            }
            case "craft_item" -> {
                CraftItemTask t = (CraftItemTask) task;
                Item item = BuiltInRegistries.ITEM.get(t.getItemId());
                yield item != null ? itemName(item) + " ×" + t.getRequiredCount() : t.getItemId().toString();
            }
            case "recipe" -> {
                RecipeTask t = (RecipeTask) task;
                yield (t.getRecipeId() != null ? t.getRecipeId().toString() : t.getRecipeType().toString()) +
                        " ×" + t.getRequired();
            }
            case "gt_multiblock" -> {
                MultiblockFormedTask t = (MultiblockFormedTask) task;
                yield (t.getMachineId() != null ? t.getMachineId().toString() : "any multiblock") + " ×" +
                        t.getRequired();
            }
            case "experience" -> "Level " + ((ExperienceTask) task).getRequiredLevel();
            case "fluid_check" -> {
                FluidRequirementTask t = (FluidRequirementTask) task;
                yield t.getFluidId().getPath().replace('_', ' ') + "  " + t.getRequiredAmount() + " mB";
            }
            case "tag_item" -> {
                TagItemTask t = (TagItemTask) task;
                yield "#" + t.getTag().location().getPath() + " ×" + t.getRequired();
            }
            default -> null;
        };
    }

    static {
        TaskFormType killEntity = define("kill_entity", KillEntityTask.class);
        killEntity.target("§8Entity id, list, or #tag  (e.g. minecraft:zombie, #minecraft:skeletons)",
                TargetPicker.ENTITY);
        killEntity.count("§8Count");
        killEntity.showConsume = true;

        TaskFormType itemCheck = define("item_check", ItemRequirementTask.class);
        itemCheck.target("§8Item id  (e.g. minecraft:iron_ingot)", TargetPicker.ITEM);
        itemCheck.count("§8Count");
        itemCheck.nbtBox = true;
        itemCheck.showConsume = true;
        itemCheck.showSticky = true;
        itemCheck.showAe2 = true;

        TaskFormType craftItem = define("craft_item", CraftItemTask.class);
        craftItem.target("§8Item id  (e.g. minecraft:iron_ingot)", TargetPicker.ITEM);
        craftItem.count("§8Count");
        craftItem.showConsume = true;

        TaskFormType recipe = define("recipe", RecipeTask.class);
        recipe.target("§8Recipe type  (e.g. gtceu:macerator, minecraft:smelting)", TargetPicker.RECIPE_TYPE);
        recipe.second("§8Specific recipe id  (optional, any recipe of the type if empty)", SecondPicker.RECIPE);
        recipe.count("§8Count");

        TaskFormType multiblock = define("gt_multiblock", MultiblockFormedTask.class);
        multiblock.target("§8Controller id  (e.g. gtceu:electric_blast_furnace, or * for any)", TargetPicker.STRING);
        multiblock.count("§8Times formed");

        TaskFormType experience = define("experience", ExperienceTask.class);
        experience.count("§8XP level");

        TaskFormType locationTerminal = define("location_terminal", LocationOrTerminalTask.class);
        locationTerminal.target("§8Terminal id", TargetPicker.NONE);
        locationTerminal.showConsume = true;

        TaskFormType advancement = define("advancement", AdvancementTask.class);
        advancement.target("§8Advancement id", TargetPicker.REGISTRY);

        TaskFormType blockInteract = define("block_interact", BlockInteractTask.class);
        blockInteract.target("§8Block id", TargetPicker.BLOCK);
        blockInteract.second("§8PLACE or RIGHT_CLICK", SecondPicker.MODE_TOGGLE);
        blockInteract.count("§8Count");
        blockInteract.showConsume = true;

        TaskFormType blockBreak = define("block_break", BlockBreakTask.class);
        blockBreak.target("§8Block ID", TargetPicker.BLOCK);
        blockBreak.count("§8Count");

        TaskFormType enchantment = define("enchantment", EnchantmentTask.class);
        enchantment.target("§8Enchantment ID", TargetPicker.REGISTRY);
        enchantment.count("§8Min Level");

        TaskFormType fluidCheck = define("fluid_check", FluidRequirementTask.class);
        fluidCheck.target("§8Fluid id", TargetPicker.FLUID);
        fluidCheck.count("§8mB amount");
        fluidCheck.showConsume = true;
        fluidCheck.showSticky = true;
        fluidCheck.showAe2 = true;
        fluidCheck.showBlock = true;

        TaskFormType stat = define("stat", StatTrackerTask.class);
        stat.target("§8Stat id  (e.g. minecraft:jump)", TargetPicker.REGISTRY);
        stat.second("§8Secondary value", SecondPicker.NONE);
        stat.count("§8Target value");
        stat.showConsume = true;

        TaskFormType dimension = define("dimension", DimensionTask.class);
        dimension.second("§8Dimension id  (e.g. minecraft:the_nether)", SecondPicker.DIMENSION);

        TaskFormType biome = define("biome", BiomeTask.class);
        biome.target("§8Biome id, list, or #tag  (e.g. minecraft:plains, #minecraft:is_forest)",
                TargetPicker.REGISTRY);

        TaskFormType structure = define("structure", StructureTask.class);
        structure.target("§8Structure id, list, or #tag  (e.g. minecraft:village_plains, #minecraft:village)",
                TargetPicker.REGISTRY);

        define("checkmark", CheckmarkTask.class);

        TaskFormType timer = define("timer", TimerTask.class);
        timer.count("§8Duration (seconds)");

        TaskFormType tagItem = define("tag_item", TagItemTask.class);
        tagItem.target("§8Item tag  (e.g. c:ores/iron)", TargetPicker.REGISTRY);
        tagItem.count("§8Count");
        tagItem.showSticky = true;

        TaskFormType info = define("info", InfoTask.class);
        info.target("§8Body text shown to the player", TargetPicker.NONE);
        info.targetOptional = true;
        info.maxTargetLength = 512;

        TaskFormType externalTrigger = define("external_trigger", ExternalTriggerTask.class);
        externalTrigger.target("§8Trigger id", TargetPicker.STRING);
        externalTrigger.count("§8Times fired");

        TaskFormType viewMachine = define("view_machine", ViewMachineTask.class);
        viewMachine.target("§8Machine id  (Phantasia multiblock definition id)", TargetPicker.STRING);
        viewMachine.count("§8Min seconds");

        TaskFormType viewScene = define("view_scene", ViewSceneTask.class);
        viewScene.target("§8Scene id  (Phantasia scene definition id)", TargetPicker.STRING);
        viewScene.count("§8Min seconds");

        TaskFormType viewGuide = define("view_guide", ViewGuideTask.class);
        viewGuide.target("§8Guide id  (Phantasia guide definition id)", TargetPicker.STRING);

        TaskFormType archiveEntry = define("archive_entry", ArchiveEntryTask.class);
        archiveEntry.target("§8Archive entry id  (e.g. phoenix_archive:log_001)", TargetPicker.STRING);

        TaskFormType screenOpened = define("screen_opened", ScreenOpenedTask.class);
        screenOpened.target("§8Screen id  (empty = any screen set on this quest)", TargetPicker.REGISTRY);
        screenOpened.targetOptional = true;

        TaskFormType energy = define("energy_check", EnergyStorageTask.class);
        energy.target("§8FE / EU / ANY", TargetPicker.ENERGY_TYPE);
        energy.second("§8INVENTORY / HELD / BLOCK / ALL", SecondPicker.ENERGY_SOURCE);
        energy.count("§8FE required");
        energy.showSticky = true;

        TaskFormType filterItem = define("filter_item", FilterItemTask.class);
        filterItem.target("§8Item id(s), semicolon-separated: ANY match  (e.g. wire;cable)", TargetPicker.ITEM_LIST);
        filterItem.count("§8Count");
        filterItem.showConsume = true;
        filterItem.showSticky = true;
        filterItem.showAe2 = true;

        TaskFormType filterFluid = define("filter_fluid", FilterFluidTask.class);
        filterFluid.target("§8Fluid id(s), semicolon-separated: ANY match  (e.g. water;lava)",
                TargetPicker.FLUID_LIST);
        filterFluid.count("§8Amount (mB)");
        filterFluid.showConsume = true;
        filterFluid.showSticky = true;
        filterFluid.showAe2 = true;
    }
}

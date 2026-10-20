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
import net.phoenixvine.chronicles.integration.gtceu.GtmMachines;
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
        return type == null ? null : type.detail(task);
    }

    public static void applySticky(QuestTask task, boolean sticky) {
        TaskFormType type = BY_ID.get(idFor(task));
        if (type != null) type.applySticky(task, sticky);
    }

    private static TaskFormType generic(String typeId) {
        PhoenixTaskRegistry.TaskEntry entry = PhoenixTaskRegistry.get(typeId);
        var builder = TaskFormType.of(typeId, ExternalTriggerTask.class);
        if (entry == null) {
            return builder.target("§8Target id").load((t, v) -> v.target = t.getTriggerId()).build();
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
            builder.target(targetLabel != null ? targetLabel : "§8Target id");
        if (countLabel != null) builder.count("§8" + countLabel);
        return builder
                .create(v -> {
                    ExternalTriggerTask ext = new ExternalTriggerTask(v.taskId, v.description, v.target,
                            v.intCount());
                    ext.setKjsTypeId(typeId);
                    return ext;
                })
                .load((t, v) -> {
                    v.target = t.getTriggerId();
                    v.count = t.getRequired();
                })
                .build();
    }

    private static void add(TaskFormType type) {
        BY_ID.put(type.id(), type);
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
        return mc.player.connection.getAdvancements().getAdvancements().getAllAdvancements().stream()
                .map(net.minecraft.advancements.Advancement::getId).toList();
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
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
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
            var fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(id));
            if (fluid != null && fluid != Fluids.EMPTY) alts.add(FluidFilters.exact(new ResourceLocation(id)));
        }
        if (alts.isEmpty()) return null;
        return alts.size() == 1 ? alts.get(0) : FluidFilters.anyOf(alts.toArray(new IFluidFilter[0]));
    }

    static {
        add(TaskFormType.of("kill_entity", KillEntityTask.class)
                .target("§8Entity id, list, or #tag  (e.g. minecraft:zombie, #minecraft:skeletons)")
                .targetPicker(TargetPicker.ENTITY)
                .count("§8Count")
                .consume()
                .create(v -> {
                    var m = TaskIdMatcher.parse(v.target);
                    return m.isEmpty() ? null : new KillEntityTask(v.taskId, v.description, m.spec(), v.intCount(),
                            v.consume);
                })
                .load((t, v) -> {
                    v.target = t.getSpec();
                    v.count = t.getRequiredCount();
                    v.consume = t.shouldConsume();
                })
                .detail(t -> t.getMatcher().displayName() + " ×" + t.getRequiredCount())
                .build());

        add(TaskFormType.of("item_check", ItemRequirementTask.class)
                .target("§8Item id  (e.g. minecraft:iron_ingot)")
                .targetPicker(TargetPicker.ITEM)
                .count("§8Count")
                .nbtBox()
                .consume()
                .sticky(ItemRequirementTask::isSticky, ItemRequirementTask::setSticky)
                .ae2(ItemRequirementTask::isCheckAe2Storage)
                .create(v -> {
                    Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(v.target));
                    if (item == null) return null;
                    ItemRequirementTask task = new ItemRequirementTask(v.taskId, v.description, item, v.intCount(),
                            v.consume);
                    String nbtText = v.nbt.trim();
                    if (!nbtText.isEmpty()) {
                        try {
                            task.setNbtFilter(TagParser.parseTag(nbtText));
                        } catch (Exception ignored) {}
                    }
                    task.setCheckAe2Storage(v.checkAe2);
                    return task;
                })
                .load((t, v) -> {
                    ResourceLocation id = t.getItem() != null ? BuiltInRegistries.ITEM.getKey(t.getItem()) : null;
                    v.target = id != null ? id.toString() : "";
                    v.count = t.getRequiredCount();
                    v.consume = t.shouldConsume();
                    v.nbt = t.getNbtFilter() != null ? t.getNbtFilter().toString() : "";
                })
                .detail(t -> t.getItem() != null ? itemName(t.getItem()) + " ×" + t.getRequiredCount() : null)
                .build());

        add(TaskFormType.of("craft_item", CraftItemTask.class)
                .target("§8Item id  (e.g. minecraft:iron_ingot)")
                .targetPicker(TargetPicker.ITEM)
                .count("§8Count")
                .consume()
                .create(v -> new CraftItemTask(v.taskId, v.description, new ResourceLocation(v.target),
                        v.intCount()))
                .load((t, v) -> {
                    v.target = t.getItemId().toString();
                    v.count = t.getRequiredCount();
                })
                .detail(t -> {
                    Item item = BuiltInRegistries.ITEM.get(t.getItemId());
                    return item != null ? itemName(item) + " ×" + t.getRequiredCount() : t.getItemId().toString();
                })
                .build());

        add(TaskFormType.of("recipe", RecipeTask.class)
                .target("§8Recipe type  (e.g. gtceu:macerator, minecraft:smelting)")
                .targetPicker(TargetPicker.RECIPE_TYPE)
                .second("§8Specific recipe id  (optional, any recipe of the type if empty)", SecondPicker.RECIPE)
                .count("§8Count")
                .create(v -> {
                    ResourceLocation type = ResourceLocation.tryParse(v.target);
                    ResourceLocation recipe = v.second.isEmpty() ? null : ResourceLocation.tryParse(v.second);
                    return type != null ? new RecipeTask(v.taskId, v.description, type, recipe, v.intCount()) : null;
                })
                .load((t, v) -> {
                    v.target = t.getRecipeType().toString();
                    v.second = t.getRecipeId() != null ? t.getRecipeId().toString() : "";
                    v.count = t.getRequired();
                })
                .detail(t -> (t.getRecipeId() != null ? t.getRecipeId().toString() : t.getRecipeType().toString()) +
                        " ×" + t.getRequired())
                .build());

        add(TaskFormType.of("gt_multiblock", MultiblockFormedTask.class)
                .target("§8Controller id  (e.g. gtceu:electric_blast_furnace, or * for any)")
                .targetPicker(TargetPicker.STRING)
                .stringIds(() -> GTCEuCompat.isAvailable() ? GtmMachines.multiblockControllerIds() : null)
                .count("§8Times formed")
                .create(v -> {
                    String machine = v.target.trim();
                    boolean any = machine.equals(MultiblockFormedTask.ANY);
                    ResourceLocation id = any ? null : ResourceLocation.tryParse(machine);
                    return id != null || any ? new MultiblockFormedTask(v.taskId, v.description, id, v.intCount()) :
                            null;
                })
                .load((t, v) -> {
                    v.target = t.getMachineId() != null ? t.getMachineId().toString() : MultiblockFormedTask.ANY;
                    v.count = t.getRequired();
                })
                .detail(t -> (t.getMachineId() != null ? t.getMachineId().toString() : "any multiblock") + " ×" +
                        t.getRequired())
                .build());

        add(TaskFormType.of("experience", ExperienceTask.class)
                .count("§8XP level")
                .create(v -> new ExperienceTask(v.taskId, v.description, v.intCount()))
                .load((t, v) -> v.count = t.getRequiredLevel())
                .detail(t -> "Level " + t.getRequiredLevel())
                .build());

        add(TaskFormType.of("location_terminal", LocationOrTerminalTask.class)
                .target("§8Terminal id")
                .consume()
                .create(v -> new LocationOrTerminalTask(v.taskId, v.description, new ResourceLocation(v.target),
                        v.consume))
                .load((t, v) -> {
                    v.target = t.getTargetTerminalId().toString();
                    v.consume = t.shouldConsume();
                })
                .build());

        add(TaskFormType.of("advancement", AdvancementTask.class)
                .target("§8Advancement id")
                .targetPicker(TargetPicker.REGISTRY)
                .registryIds(TaskFormTypes::advancementIds)
                .create(v -> new AdvancementTask(v.taskId, v.description, new ResourceLocation(v.target)))
                .load((t, v) -> v.target = t.getAdvancementId().toString())
                .build());

        add(TaskFormType.of("block_interact", BlockInteractTask.class)
                .target("§8Block id")
                .targetPicker(TargetPicker.BLOCK)
                .second("§8PLACE or RIGHT_CLICK", SecondPicker.MODE_TOGGLE)
                .count("§8Count")
                .consume()
                .create(v -> {
                    var block = BuiltInRegistries.BLOCK.get(new ResourceLocation(v.target));
                    String mode = v.second.isEmpty() ? "PLACE" : v.second.toUpperCase();
                    return block != null ? new BlockInteractTask(v.taskId, v.description, block, mode, v.intCount()) :
                            null;
                })
                .load((t, v) -> {
                    v.target = blockId(t.getTargetBlock());
                    v.second = t.getMode();
                    v.count = t.getRequired();
                })
                .build());

        add(TaskFormType.of("block_break", BlockBreakTask.class)
                .target("§8Block ID")
                .targetPicker(TargetPicker.BLOCK)
                .count("§8Count")
                .create(v -> {
                    var block = BuiltInRegistries.BLOCK.get(new ResourceLocation(v.target));
                    return block != null ? new BlockBreakTask(v.taskId, v.description, block, v.intCount()) : null;
                })
                .load((t, v) -> {
                    v.target = blockId(t.getTargetBlock());
                    v.count = t.getRequired();
                })
                .build());

        add(TaskFormType.of("enchantment", EnchantmentTask.class)
                .target("§8Enchantment ID")
                .targetPicker(TargetPicker.REGISTRY)
                .registryIds(() -> net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getKeys())
                .count("§8Min Level")
                .create(v -> new EnchantmentTask(v.taskId, v.description, new ResourceLocation(v.target),
                        v.intCount()))
                .load((t, v) -> {
                    v.target = t.getEnchantmentId().toString();
                    v.count = t.getRequiredLevel();
                })
                .build());

        add(TaskFormType.of("fluid_check", FluidRequirementTask.class)
                .target("§8Fluid id")
                .targetPicker(TargetPicker.FLUID)
                .count("§8mB amount")
                .consume()
                .sticky(FluidRequirementTask::isSticky, FluidRequirementTask::setSticky)
                .ae2(FluidRequirementTask::isCheckAe2Storage)
                .blockSource()
                .create(v -> {
                    FluidRequirementTask task = new FluidRequirementTask(v.taskId, v.description,
                            new ResourceLocation(v.target), v.intCount(), v.consume);
                    task.setCheckAe2Storage(v.checkAe2);
                    task.setSource(v.fluidSource);
                    return task;
                })
                .load((t, v) -> {
                    v.target = t.getFluidId().toString();
                    v.count = t.getRequiredAmount();
                    v.consume = t.shouldConsume();
                    v.fluidSource = t.getSource();
                })
                .detail(t -> t.getFluidId().getPath().replace('_', ' ') + "  " + t.getRequiredAmount() + " mB")
                .build());

        add(TaskFormType.of("stat", StatTrackerTask.class)
                .target("§8Stat id  (e.g. minecraft:jump)")
                .targetPicker(TargetPicker.REGISTRY)
                .registryIds(() -> BuiltInRegistries.CUSTOM_STAT.keySet())
                .second("§8Secondary value", SecondPicker.NONE)
                .count("§8Target value")
                .consume()
                .create(v -> new StatTrackerTask(v.taskId, v.description, new ResourceLocation(v.target),
                        v.intCount(), v.consume))
                .load((t, v) -> {
                    v.target = t.getStatId().toString();
                    v.count = t.getTargetValue();
                    v.consume = t.shouldConsume();
                })
                .build());

        add(TaskFormType.of("dimension", DimensionTask.class)
                .second("§8Dimension id  (e.g. minecraft:the_nether)", SecondPicker.DIMENSION)
                .create(v -> {
                    String dim = v.second.isEmpty() ? "minecraft:overworld" : v.second;
                    var m = TaskIdMatcher.parse(dim, false);
                    return m.isEmpty() ? null : new DimensionTask(v.taskId, v.description, m.spec());
                })
                .load((t, v) -> v.second = t.getSpec())
                .build());

        add(TaskFormType.of("biome", BiomeTask.class)
                .target("§8Biome id, list, or #tag  (e.g. minecraft:plains, #minecraft:is_forest)")
                .targetPicker(TargetPicker.REGISTRY)
                .registryIds(() -> registryKeys(Registries.BIOME))
                .create(v -> {
                    var m = TaskIdMatcher.parse(v.target);
                    return m.isEmpty() ? null : new BiomeTask(v.taskId, v.description, m.spec());
                })
                .load((t, v) -> v.target = t.getSpec())
                .build());

        add(TaskFormType.of("structure", StructureTask.class)
                .target("§8Structure id, list, or #tag  (e.g. minecraft:village_plains, #minecraft:village)")
                .targetPicker(TargetPicker.REGISTRY)
                .registryIds(() -> registryKeys(Registries.STRUCTURE))
                .create(v -> {
                    var m = TaskIdMatcher.parse(v.target);
                    return m.isEmpty() ? null : new StructureTask(v.taskId, v.description, m.spec());
                })
                .load((t, v) -> v.target = t.getSpec())
                .build());

        add(TaskFormType.of("checkmark", CheckmarkTask.class)
                .create(v -> new CheckmarkTask(v.taskId, v.description))
                .build());

        add(TaskFormType.of("timer", TimerTask.class)
                .count("§8Duration (seconds)")
                .create(v -> new TimerTask(v.taskId, v.description, v.intCount()))
                .load((t, v) -> v.count = t.getDurationSeconds())
                .build());

        add(TaskFormType.of("tag_item", TagItemTask.class)
                .target("§8Item tag  (e.g. c:ores/iron)")
                .targetPicker(TargetPicker.REGISTRY)
                .registryIds(() -> net.minecraftforge.registries.ForgeRegistries.ITEMS.tags().getTagNames()
                        .map(TagKey::location).toList())
                .count("§8Count")
                .sticky(TagItemTask::isSticky, TagItemTask::setSticky)
                .create(v -> new TagItemTask(v.taskId, v.description, ItemTags.create(new ResourceLocation(v.target)),
                        v.intCount()))
                .load((t, v) -> {
                    v.target = t.getTag().location().toString();
                    v.count = t.getRequired();
                })
                .detail(t -> "#" + t.getTag().location().getPath() + " ×" + t.getRequired())
                .build());

        add(TaskFormType.of("info", InfoTask.class)
                .target("§8Body text shown to the player")
                .targetOptional()
                .maxTargetLength(512)
                .create(v -> new InfoTask(v.taskId, v.description, v.target))
                .load((t, v) -> v.target = t.getBody())
                .build());

        add(TaskFormType.of("external_trigger", ExternalTriggerTask.class)
                .target("§8Trigger id")
                .targetPicker(TargetPicker.STRING)
                .stringIds(() -> QuestAPI.getRegisteredExternalTriggers().keySet())
                .count("§8Times fired")
                .create(v -> new ExternalTriggerTask(v.taskId, v.description, v.target, v.intCount()))
                .load((t, v) -> {
                    v.target = t.getTriggerId();
                    v.count = t.getRequired();
                })
                .build());

        add(TaskFormType.of("view_machine", ViewMachineTask.class)
                .target("§8Machine id  (Phantasia multiblock definition id)")
                .targetPicker(TargetPicker.STRING)
                .stringIds(() -> PhantasiaCompat.isAvailable() ?
                        net.phoenixvine.phantasia.api.PhantasiaAPI.getAllMachineIds() : null)
                .count("§8Min seconds")
                .create(v -> new ViewMachineTask(v.taskId, v.description, v.target, (float) v.intCount()))
                .load((t, v) -> {
                    v.target = t.getMachineId();
                    v.count = (int) t.getMinSeconds();
                })
                .build());

        add(TaskFormType.of("view_scene", ViewSceneTask.class)
                .target("§8Scene id  (Phantasia scene definition id)")
                .targetPicker(TargetPicker.STRING)
                .stringIds(() -> PhantasiaCompat.isAvailable() ?
                        net.phoenixvine.phantasia.api.PhantasiaAPI.getAllSceneIds() : null)
                .count("§8Min seconds")
                .create(v -> new ViewSceneTask(v.taskId, v.description, v.target, (float) v.intCount()))
                .load((t, v) -> {
                    v.target = t.getSceneId();
                    v.count = (int) t.getMinSeconds();
                })
                .build());

        add(TaskFormType.of("view_guide", ViewGuideTask.class)
                .target("§8Guide id  (Phantasia guide definition id)")
                .targetPicker(TargetPicker.STRING)
                .stringIds(() -> PhantasiaCompat.isAvailable() ?
                        net.phoenixvine.phantasia.api.PhantasiaAPI.getAllGuideIds() : null)
                .create(v -> new ViewGuideTask(v.taskId, v.description, v.target))
                .load((t, v) -> v.target = t.getGuideId())
                .build());

        add(TaskFormType.of("screen_opened", ScreenOpenedTask.class)
                .target("§8Screen id  (empty = any screen set on this quest)")
                .targetOptional()
                .targetPicker(TargetPicker.REGISTRY)
                .registryIds(net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry::registeredIds)
                .create(v -> new ScreenOpenedTask(v.taskId, v.description, v.target))
                .load((t, v) -> v.target = t.getScreenId())
                .build());

        add(TaskFormType.of("archive_entry", ArchiveEntryTask.class)
                .target("§8Archive entry id  (e.g. phoenix_archive:log_001)")
                .targetPicker(TargetPicker.STRING)
                .stringIds(() -> ArchiveLoreCompat.isAvailable() ?
                        net.phoenix_archives.phoenix_archive.api.LoreDataLoader.LORE_ENTRIES.keySet().stream()
                                .map(ResourceLocation::toString).toList() :
                        null)
                .create(v -> new ArchiveEntryTask(v.taskId, v.description, v.target))
                .load((t, v) -> v.target = t.getArchiveEntryId())
                .build());

        add(TaskFormType.of("energy_check", EnergyStorageTask.class)
                .target("§8FE / EU / ANY")
                .targetPicker(TargetPicker.ENERGY_TYPE)
                .second("§8INVENTORY / HELD / BLOCK / ALL", SecondPicker.ENERGY_SOURCE)
                .count("§8FE required")
                .sticky(EnergyStorageTask::isSticky, EnergyStorageTask::setSticky)
                .create(v -> {
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
                })
                .load((t, v) -> {
                    v.target = t.getEnergyType().name();
                    v.second = t.getSource().name();
                    v.count = t.getRequiredEnergy();
                })
                .build());

        add(TaskFormType.of("filter_item", FilterItemTask.class)
                .target("§8Item id(s), semicolon-separated: ANY match  (e.g. wire;cable)")
                .targetPicker(TargetPicker.ITEM_LIST)
                .count("§8Count")
                .consume()
                .sticky(FilterItemTask::isSticky, FilterItemTask::setSticky)
                .ae2(FilterItemTask::isCheckAe2Storage)
                .create(v -> {
                    IItemFilter filter = v.itemFilter != null ? v.itemFilter : parseItemFilter(v.target);
                    if (filter == null) return null;
                    FilterItemTask task = new FilterItemTask(v.taskId, v.description, filter, v.intCount(),
                            v.consume);
                    task.setCheckAe2Storage(v.checkAe2);
                    return task;
                })
                .load((t, v) -> {
                    v.target = itemFilterAsIdList(t.getFilter());
                    if (!isPlainItemIdList(t.getFilter())) v.itemFilter = t.getFilter();
                    v.count = t.getCount();
                    v.consume = t.isConsume();
                })
                .build());

        add(TaskFormType.of("filter_fluid", FilterFluidTask.class)
                .target("§8Fluid id(s), semicolon-separated: ANY match  (e.g. water;lava)")
                .targetPicker(TargetPicker.FLUID_LIST)
                .count("§8Amount (mB)")
                .consume()
                .sticky(FilterFluidTask::isSticky, FilterFluidTask::setSticky)
                .ae2(FilterFluidTask::isCheckAe2Storage)
                .create(v -> {
                    IFluidFilter filter = v.fluidFilter != null ? v.fluidFilter : parseFluidFilter(v.target);
                    if (filter == null) return null;
                    FilterFluidTask task = new FilterFluidTask(v.taskId, v.description, filter, v.intCount(),
                            v.consume);
                    task.setCheckAe2Storage(v.checkAe2);
                    return task;
                })
                .load((t, v) -> {
                    v.target = fluidFilterAsIdList(t.getFilter());
                    if (!isPlainFluidIdList(t.getFilter())) v.fluidFilter = t.getFilter();
                    v.count = t.getAmount();
                    v.consume = t.isConsume();
                })
                .build());
    }
}

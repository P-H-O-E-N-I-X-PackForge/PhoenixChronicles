package net.phoenixvine.chronicles.common.codec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.phoenixvine.chronicles.common.event.ChronicleEvents;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChronicleDataLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<ResourceLocation, QuestNode> loadedNodes = new HashMap<>();
    private final Map<ResourceLocation, ResourceLocation> parentRelations = new HashMap<>();

    public ChronicleDataLoader() {
        super(GSON, "phoenix_chronicles");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        LOGGER.info("Loading Phoenix Chronicles quests from datapacks...");

        QuestTreeRegistry.clear();
        loadedNodes.clear();
        parentRelations.clear();

        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation questId = entry.getKey();
            try {
                loadedNodes.put(questId, parseQuest(questId, entry.getValue().getAsJsonObject()));
            } catch (Exception e) {
                LOGGER.error("Failed to parse chronicle quest entry JSON for {}", questId, e);
            }
        }

        List<QuestNode> rootNodes = linkChildrenToParents();
        for (QuestNode root : rootNodes) {
            QuestTreeRegistry.registerRootChapter(root);
        }

        LOGGER.info("Successfully loaded {} chronicle branches into memory.", rootNodes.size());
        loadDiskQuestsIfServerReady();
    }

    private QuestNode parseQuest(ResourceLocation questId, JsonObject json) {
        Component title = Component.Serializer.fromJson(json.get("title"), net.minecraft.core.RegistryAccess.EMPTY);
        Component desc = Component.Serializer.fromJson(json.get("description"),
                net.minecraft.core.RegistryAccess.EMPTY);
        QuestNode node = new QuestNode(questId, title, desc);

        readNodeSettings(node, json);

        if (json.has("parent")) {
            parentRelations.put(questId, ResourceLocation.parse(json.get("parent").getAsString()));
        }

        if (json.has("tasks")) {
            for (JsonElement element : json.getAsJsonArray("tasks")) {
                readTask(node, questId, element.getAsJsonObject());
            }
        }
        return node;
    }

    private static void readNodeSettings(QuestNode node, JsonObject json) {
        String chapter = "MAIN";
        if (json.has("chapter")) {
            chapter = json.get("chapter").getAsString().trim().toUpperCase();
        } else if (json.has("category")) {
            chapter = json.get("category").getAsString().trim().toUpperCase();
        }
        node.setChapter(chapter);

        if (json.has("layout_x") && json.has("layout_y")) {
            node.setCustomX(json.get("layout_x").getAsInt());
            node.setCustomY(json.get("layout_y").getAsInt());
        }

        String shape = "SQUARE";
        if (json.has("shape")) {
            shape = json.get("shape").getAsString().trim().toUpperCase();
        }
        node.setShapeType(shape);

        if (json.has("subtitle")) node.setSubtitle(json.get("subtitle").getAsString());
        if (json.has("visibility")) {
            try {
                node.setVisibility(QuestNode.Visibility.valueOf(
                        json.get("visibility").getAsString().toUpperCase()));
            } catch (Exception ignored) {}
        }
        if (json.has("task_min_count")) node.setTaskMinCount(json.get("task_min_count").getAsInt());
        if (json.has("require_all_prerequisites"))
            node.setRequireAllPrerequisites(json.get("require_all_prerequisites").getAsBoolean());

        if (json.has("repeat_mode")) {
            try {
                node.setRepeatMode(QuestNode.RepeatMode.valueOf(
                        json.get("repeat_mode").getAsString().toUpperCase()));
            } catch (Exception ignored) {}
        }
        if (json.has("repeat_cooldown_hours"))
            node.setRepeatCooldownHours(json.get("repeat_cooldown_hours").getAsInt());
    }

    private static void readTask(QuestNode node, ResourceLocation questId, JsonObject taskJson) {
        ResourceLocation taskId = ResourceLocation.parse(taskJson.get("id").getAsString());
        Component taskDesc = Component.Serializer.fromJson(taskJson.get("description"),
                net.minecraft.core.RegistryAccess.EMPTY);

        String type = taskJson.get("type").getAsString();
        boolean isOptional = taskJson.has("optional") && taskJson.get("optional").getAsBoolean();

        QuestTask addedTask = createTask(type, questId, taskId, taskDesc, taskJson);
        if (addedTask != null) {
            addedTask.setOptional(isOptional);
            node.addTask(addedTask);
        }
    }

    private static QuestTask createTask(String type, ResourceLocation questId, ResourceLocation taskId,
                                        Component taskDesc, JsonObject taskJson) {
        boolean defaultConsume = !taskJson.has("consume") || taskJson.get("consume").getAsBoolean();
        boolean consumeOnlyIfSet = taskJson.has("consume") && taskJson.get("consume").getAsBoolean();

        switch (type) {
            case "terminal_check" -> {
                ResourceLocation terminalId = ResourceLocation.parse(taskJson.get("target_terminal").getAsString());
                return new LocationOrTerminalTask(taskId, taskDesc, terminalId, consumeOnlyIfSet);
            }
            case "item_check" -> {
                return createItemCheckTask(taskId, taskDesc, taskJson, defaultConsume);
            }
            case "fluid_check" -> {
                ResourceLocation fluidId = ResourceLocation.parse(taskJson.get("fluid_id").getAsString());
                int amount = taskJson.has("amount") ? taskJson.get("amount").getAsInt() : 1000;
                return new FluidRequirementTask(taskId, taskDesc, fluidId, amount, defaultConsume);
            }
            case "craft_item" -> {
                ResourceLocation itemId = ResourceLocation.parse(taskJson.get("item_id").getAsString());
                int count = taskJson.has("count") ? taskJson.get("count").getAsInt() : 1;
                return new CraftItemTask(taskId, taskDesc, itemId, count);
            }
            case "kill_entity" -> {
                String entityId = targetSpec(taskJson.get("entity_id"));
                int required = taskJson.has("required") ? taskJson.get("required").getAsInt() : 1;
                return new KillEntityTask(taskId, taskDesc, entityId, required, consumeOnlyIfSet);
            }
            case "stat" -> {
                ResourceLocation statId = ResourceLocation.parse(taskJson.get("stat_id").getAsString());
                int target = taskJson.has("target_value") ? taskJson.get("target_value").getAsInt() : 1000;
                return new StatTrackerTask(taskId, taskDesc, statId, target, consumeOnlyIfSet);
            }
            case "xp_check" -> {
                return new ExperienceTask(taskId, taskDesc, taskJson.get("level").getAsInt());
            }
            case "biome" -> {
                return new BiomeTask(taskId, taskDesc, targetSpec(taskJson.get("biome_id")));
            }
            case "structure" -> {
                return new StructureTask(taskId, taskDesc, targetSpec(taskJson.get("structure_id")));
            }
            case "checkmark" -> {
                return new CheckmarkTask(taskId, taskDesc);
            }
            case "tag_item" -> {
                net.minecraft.tags.TagKey<Item> tag = net.minecraft.tags.ItemTags.create(
                        ResourceLocation.parse(taskJson.get("tag").getAsString()));
                int required = taskJson.has("required") ? taskJson.get("required").getAsInt() : 1;
                return new TagItemTask(taskId, taskDesc, tag, required);
            }
            case "info" -> {
                String body = taskJson.has("body") ? taskJson.get("body").getAsString() : "";
                return new InfoTask(taskId, taskDesc, body);
            }
            case "energy_check" -> {
                return createEnergyTask(taskId, taskDesc, taskJson);
            }
            default -> {
                LOGGER.warn("Unknown chronicle task type '{}' discovered on quest '{}'", type, questId);
                return null;
            }
        }
    }

    private static QuestTask createItemCheckTask(ResourceLocation taskId, Component taskDesc, JsonObject taskJson,
                                                 boolean consume) {
        ResourceLocation itemId = ResourceLocation.parse(taskJson.get("item_id").getAsString());
        Item item = BuiltInRegistries.ITEM.get(itemId);
        int amount = taskJson.has("amount") ? taskJson.get("amount").getAsInt() : 1;
        if (item == null) {
            LOGGER.error("Skipping item_check task '{}' - Item '{}' cannot be found in Registry!", taskId, itemId);
            return null;
        }
        return new ItemRequirementTask(taskId, taskDesc, item, amount, consume);
    }

    private static QuestTask createEnergyTask(ResourceLocation taskId, Component taskDesc, JsonObject taskJson) {
        long required = taskJson.has("required_energy") ? taskJson.get("required_energy").getAsLong() :
                taskJson.has("amount") ? taskJson.get("amount").getAsLong() : 10000L;
        String typeStr = taskJson.has("energy_type") ? taskJson.get("energy_type").getAsString() : "FE";
        String srcStr = taskJson.has("source") ? taskJson.get("source").getAsString() : "INVENTORY";
        EnergyStorageTask.EnergyType eType;
        EnergyStorageTask.Source eSrc;
        try {
            eType = EnergyStorageTask.EnergyType.valueOf(typeStr.toUpperCase());
        } catch (Exception ignored) {
            eType = EnergyStorageTask.EnergyType.FE;
        }
        try {
            eSrc = EnergyStorageTask.Source.valueOf(srcStr.toUpperCase());
        } catch (Exception ignored) {
            eSrc = EnergyStorageTask.Source.INVENTORY;
        }
        return new EnergyStorageTask(taskId, taskDesc, required, eType, eSrc);
    }

    private List<QuestNode> linkChildrenToParents() {
        List<QuestNode> rootNodes = new ArrayList<>();
        for (QuestNode node : loadedNodes.values()) {
            ResourceLocation parentId = parentRelations.get(node.getId());
            if (parentId == null) {
                rootNodes.add(node);
                continue;
            }
            QuestNode parentNode = loadedNodes.get(parentId);
            if (parentNode != null) {
                parentNode.addChild(node);
            } else {
                LOGGER.warn("Quest '{}' defined parent '{}' but that parent does not exist!", node.getId(),
                        parentId);
                rootNodes.add(node);
            }
        }
        return rootNodes;
    }

    private static void loadDiskQuestsIfServerReady() {
        MinecraftServer server = ChronicleEvents.getCachedServer();
        if (server != null && ChronicleEvents.hasServerFullyStarted()) {
            java.nio.file.Path configDir = ChronicleEvents.resolveConfigDir(server);
            QuestFileLoader.loadAdditiveFromDisk(configDir);
        }
    }

    private static String targetSpec(com.google.gson.JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonArray()) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (com.google.gson.JsonElement e : el.getAsJsonArray()) parts.add(e.getAsString());
            return String.join(", ", parts);
        }
        return el.getAsString();
    }
}

package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.registry.LangSyncScheduler;
import net.phoenixvine.chronicles.client.render.ChroniclesThemeRenderer;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.rich.CodeEditBox;
import net.phoenixvine.chronicles.client.screen.rewardform.RewardFormTypes;
import net.phoenixvine.chronicles.client.screen.rewardform.RewardFormValues;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormType;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormType.SecondPicker;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormType.TargetPicker;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormTypes;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormValues;
import net.phoenixvine.chronicles.client.screen.utils.UndoRedoManager;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.filter.IFluidFilter;
import net.phoenixvine.chronicles.common.filter.IItemFilter;
import net.phoenixvine.chronicles.common.item.FluidFilterTokenItem;
import net.phoenixvine.chronicles.common.item.ItemFilterTokenItem;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.model.RewardTable;
import net.phoenixvine.chronicles.common.registry.PhoenixRewardRegistry;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.registry.RewardTableRegistry;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TaskRewardEditorScreen extends Screen {

    private int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_OK;
    private static final int C_ROW_HOVER = 0x22FFFFFF;
    private static final int C_FORM_BG = 0x33000000;
    private static final int C_SPLIT = 0xFF2A2A3A;
    private static final int C_TOOLTIP_BG = 0xFF0E0E16;

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    private static final int MARGIN = 10;
    private static final int COL_GAP = 6;
    private static final int ROW_H = 26;
    private static final int FIELD_H = 15;
    private static final int FIELD_GAP = 5;

    private static final int FORM_ROWS = 5;

    private static final int MIN_W = 560;
    private static final int MIN_H = 380;
    private float uiScale = 1f;
    private int vw, vh;

    private int splitX;
    private int colW;
    private int listTop;
    private int listBottom;
    private int formTop;
    private int formBottom;

    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;

    private final Screen parent;

    Screen getParentScreen() {
        return parent;
    }

    @Nullable
    private final QuestNode questNode;

    private final boolean emergencyMode;
    @Nullable
    private final Consumer<List<QuestReward>> emergencySink;
    private final String emergencyLabel;

    @Nullable
    private final QuestNode.QuestVariant variantTarget;

    private final List<QuestTask> tasks = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();

    private String taskType = "item_check";
    private boolean taskConsume = false;
    private boolean taskOptional = false;
    private int taskHiddenParts = 0;
    private int rewardHiddenParts = 0;

    private boolean taskSticky = true;
    private boolean taskCheckAe2Storage = AE2Compat.isAvailable();
    private boolean taskTypeDropOpen = false;
    private int taskTypeDropScroll = 0;
    private EditBox taskDescBox, taskTargetBox, taskCountBox, taskSecondaryBox, taskNbtBox;

    private int editingTaskIndex = -1;
    private int editingRewardIndex = -1;

    private IItemFilter pendingPickedItemFilter = null;
    private IFluidFilter pendingPickedFluidFilter = null;

    private boolean forcePendingTaskValues = false;
    private String pendingTaskDesc = "", pendingTaskTarget = "", pendingTaskSecondary = "", pendingTaskCount = "",
            pendingTaskNbt = "";
    private boolean forcePendingRewardValues = false;
    private String pendingRewardCount = "", pendingRewardCommand = "", pendingRewardEventData = "";

    private String rewardType = "item";
    private boolean rewardPushAe2 = true;
    private int rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
    private QuestReward.QuestActionReward.Action rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;
    private boolean rewardTypeDropOpen = false;
    private ItemStack rewardPickedItem = null;
    private EditBox rewardCountBox, rewardCommandBox;

    private QuestReward.ChoiceBoxReward.Mode boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
    private final List<QuestReward> boxOptions = new ArrayList<>();
    private static final int BOX_OPTION_ROW_H = 14;
    private static final int CHOICE_ADD_ITEM_W = 44;
    private static final int CHOICE_ADD_TABLE_W = 48;
    private int boxOptionsListX, boxOptionsListY, boxOptionsListW, boxOptionsListBottom;
    private int boxOptionsScroll = 0;
    private final List<int[]> boxOptionRowRects = new ArrayList<>();

    private int editingBoxOptionIndex = -1;
    private ItemStack boxOptionPickedItem = null;
    private EditBox boxOptionCountBox, boxOptionNbtBox;
    private boolean forcePendingBoxOptionValues = false;
    private String pendingBoxOptionCount = "1", pendingBoxOptionNbt = "", pendingBoxOptionTable = "";
    private EditBox boxOptionTableBox;

    private int hoveredTaskRow = -1;
    private int hoveredRewardRow = -1;
    private int taskListScroll = 0;
    private int rewardListScroll = 0;
    private int hoveredDropRow = -1;

    private int draggingTaskIndex = -1;
    private int draggingRewardIndex = -1;
    private boolean dragMovedTask = false;
    private boolean dragMovedReward = false;

    private static final int ROW_HEADER_H = 14;
    private List<Integer> rewardDisplayOrder = List.of();

    private static CompoundTag copiedTaskNBT = null;
    private static CompoundTag copiedRewardNBT = null;

    private final UndoRedoManager undoRedo = new UndoRedoManager(msg -> {});

    private EditBox rewardEventDataBox;
    private final Map<String, String> externalFieldValues = new LinkedHashMap<>();
    private PhoenixRewardRegistry.Entry externalEntryAtBuild;

    public TaskRewardEditorScreen(Screen parent, QuestNode questNode) {
        this(parent, questNode, null);
    }

    public TaskRewardEditorScreen(Screen parent, QuestNode questNode, @Nullable QuestNode.QuestVariant variantTarget) {
        super(ChroniclesUIKit.lit(variantTarget != null ? "Tasks & Rewards (variant)" : "Tasks & Rewards"));
        this.parent = parent;
        this.questNode = questNode;
        this.variantTarget = variantTarget;
        this.emergencyMode = false;
        this.emergencySink = null;
        this.emergencyLabel = "";
        if (variantTarget != null) {
            this.tasks.addAll(variantTarget.tasks != null ? variantTarget.tasks : questNode.getTasks());
            this.rewards.addAll(variantTarget.rewards != null ? variantTarget.rewards : questNode.getRewards());
        } else {
            this.tasks.addAll(questNode.getTasks());
            this.rewards.addAll(questNode.getRewards());
        }
    }

    public TaskRewardEditorScreen(Screen parent, @Nullable QuestNode questNode, List<QuestReward> emergencyRewards,
                                  Consumer<List<QuestReward>> sink, String label) {
        super(ChroniclesUIKit.lit("Emergency Rewards"));
        this.parent = parent;
        this.questNode = questNode;
        this.variantTarget = null;
        this.emergencyMode = true;
        this.emergencySink = sink;
        this.emergencyLabel = label;
        this.rewards.addAll(emergencyRewards);
    }

    private String[] rewardTypes() {
        return RewardFormTypes.listedIds(emergencyMode);
    }

    @Override
    protected void init() {
        PhoenixTheme th = PhoenixTheme.current();
        C_BG = th.bg.getColor();
        C_PANEL = th.panel.getColor();
        C_HEADER = th.header.getColor();
        C_BORDER = th.border.getColor();
        C_ACCENT = th.accent.getColor();
        C_TEXT = th.text.getColor();
        C_TEXT_DIM = th.textDim.getColor();
        C_TEXT_FAINT = th.textFaint.getColor();
        C_OK = th.done.getColor();

        uiScale = (width < MIN_W || height < MIN_H) ? Math.min(width / (float) MIN_W, height / (float) MIN_H) : 1f;
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        colW = (vw - MARGIN * 2 - COL_GAP) / 2;
        splitX = MARGIN + colW + COL_GAP;
        formBottom = vh - FOOTER_H;
        formTop = formBottom - MARGIN - FORM_ROWS * (FIELD_H + FIELD_GAP) - 8;
        listTop = HEADER_H + 22;
        listBottom = formTop - 22;

        lastLayoutWidth = width;
        lastLayoutHeight = height;

        rebuildWidgets();
    }

    private void setHintFitted(EditBox box, String hint) {
        String plain = hint.replaceAll("§.", "");
        int maxW = Math.max(10, box.getWidth() - 10);
        boolean cut = font.width(plain) > maxW;
        String shown = cut ? font.plainSubstrByWidth(plain, Math.max(0, maxW - font.width("…"))) + "…" : plain;
        box.setHint(ChroniclesUIKit.lit("§8" + shown));
        if (cut) box.setTooltip(Tooltip.create(ChroniclesUIKit.lit(plain)));
    }

    private void setTableIdHint(EditBox box) {
        box.setHint(ChroniclesUIKit.lit("§8Table ID"));
        List<String> known = new ArrayList<>(RewardTableRegistry.getAll().keySet());
        Collections.sort(known);
        String text = known.isEmpty() ? "No reward tables are loaded yet.\n(config/phoenix_chronicles/reward_tables/)" :
                "Existing reward tables:\n" + String.join("\n", known);
        box.setTooltip(Tooltip.create(ChroniclesUIKit.lit(text)));
    }

    protected void rebuildWidgets() {
        String descVal = forcePendingTaskValues ? pendingTaskDesc : (taskDescBox != null ? taskDescBox.getValue() : "");
        String targetVal = forcePendingTaskValues ? pendingTaskTarget :
                (taskTargetBox != null ? taskTargetBox.getValue() : "");
        String secondVal = forcePendingTaskValues ? pendingTaskSecondary :
                (taskSecondaryBox != null ? taskSecondaryBox.getValue() : "");
        String countVal = forcePendingTaskValues ? pendingTaskCount :
                (taskCountBox != null ? taskCountBox.getValue() : "1");
        String nbtVal = forcePendingTaskValues ? pendingTaskNbt : (taskNbtBox != null ? taskNbtBox.getValue() : "");
        forcePendingTaskValues = false;

        String rCountVal = forcePendingRewardValues ? pendingRewardCount :
                (rewardCountBox != null ? rewardCountBox.getValue() : "");
        String rCommandVal = forcePendingRewardValues ? pendingRewardCommand :
                (rewardCommandBox != null ? rewardCommandBox.getValue() : "");
        String rEventDataVal = forcePendingRewardValues ? pendingRewardEventData :
                (rewardEventDataBox != null ? rewardEventDataBox.getValue() : "");
        forcePendingRewardValues = false;

        String boCountVal = forcePendingBoxOptionValues ? pendingBoxOptionCount :
                (boxOptionCountBox != null ? boxOptionCountBox.getValue() : "1");
        String boNbtVal = forcePendingBoxOptionValues ? pendingBoxOptionNbt :
                (boxOptionNbtBox != null ? boxOptionNbtBox.getValue() : "");
        String boTableVal = forcePendingBoxOptionValues ? pendingBoxOptionTable :
                (boxOptionTableBox != null ? boxOptionTableBox.getValue() : "");
        forcePendingBoxOptionValues = false;

        clearWidgets();

        addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7‹ Done"), b -> {
            flushToQuestNode();
            if (!emergencyMode) ChronicleOverviewScreen.invalidateNodeCachesUpChain(parent, questNode);
            if (minecraft != null) minecraft.setScreen(parent);
        }).bounds(vw / 2 - 40, vh - FOOTER_H + (FOOTER_H - 14) / 2, 80, 14)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Save changes and return to quest editor"))).build());

        int taskFormStart = children().size();
        int tx = MARGIN;
        int fy = formTop + 8;

        PhoenixTaskRegistry.TaskEntry curMeta = getTaskMeta(taskType);
        String typeTooltip = curMeta != null && curMeta.editorTooltip() != null ?
                curMeta.editorTooltip().split("\n")[0] : "Choose the type of task to add";
        addRenderableWidget(Button.builder(
                ChroniclesUIKit.lit("§8Type: §7" + (curMeta != null ? curMeta.editorLabel() : taskType) + " §8▾"),
                b -> {
                    taskTypeDropOpen = !taskTypeDropOpen;
                    taskTypeDropScroll = 0;
                    rewardTypeDropOpen = false;
                })
                .bounds(tx, fy, colW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(typeTooltip))).build());
        fy += FIELD_H + FIELD_GAP;

        TaskFormType form = TaskFormTypes.get(taskType);
        boolean needsTarget = form.needsTarget();
        boolean needsSecond = form.needsSecond();
        boolean needsCount = form.needsCount();
        boolean showConsume = form.showConsume();
        boolean showSticky = form.showSticky();
        boolean showAe2Toggle = AE2Compat.isAvailable() && form.showAe2();

        taskDescBox = new CodeEditBox(font, tx, fy, colW, FIELD_H, Component.empty());
        setHintFitted(taskDescBox, "§8Task label shown to player");
        taskDescBox.setMaxLength(128);
        taskDescBox.setValue(descVal);
        addRenderableWidget(taskDescBox);
        fy += FIELD_H + FIELD_GAP;

        if (needsTarget) {
            String hint = form.targetHint();
            TargetPicker picker = form.targetPicker();
            boolean hasItemPicker = picker == TargetPicker.ITEM;
            boolean hasItemListPicker = picker == TargetPicker.ITEM_LIST;
            boolean hasFluidPicker = picker == TargetPicker.FLUID;
            boolean hasFluidListPicker = picker == TargetPicker.FLUID_LIST;
            boolean hasBlockPicker = picker == TargetPicker.BLOCK;
            boolean hasEntityPicker = picker == TargetPicker.ENTITY;
            boolean hasRegistryIdPicker = picker == TargetPicker.REGISTRY;
            boolean hasStringIdPicker = picker == TargetPicker.STRING;
            boolean hasRecipeTypePicker = picker == TargetPicker.RECIPE_TYPE;
            boolean hasEnergyTypeCycle = picker == TargetPicker.ENERGY_TYPE;
            boolean hasAnyItemPicker = hasItemPicker || hasItemListPicker;
            boolean hasAnyFluidPicker = hasFluidPicker || hasFluidListPicker;
            int tw = (hasAnyItemPicker || hasAnyFluidPicker || hasBlockPicker || hasEntityPicker ||
                    hasRegistryIdPicker || hasStringIdPicker || hasEnergyTypeCycle || hasRecipeTypePicker) ? colW - 36 :
                            colW;
            int tmaxLen = form.maxTargetLength();
            taskTargetBox = new EditBox(font, tx, fy, tw, FIELD_H, Component.empty());
            setHintFitted(taskTargetBox, hint);
            taskTargetBox.setMaxLength(tmaxLen);
            taskTargetBox.setValue(targetVal);
            addRenderableWidget(taskTargetBox);
            if (hasItemPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        if (applyPickedItemFilter(stack)) return;
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (id != null && taskTargetBox != null) taskTargetBox.setValue(id.toString());
                    }).withBulk(this::bulkAddItemTasks));
                }).bounds(tx + tw, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                                "Pick an item. Right-click several in the picker to add one task per item at once " +
                                        "(uses the count / consume / optional settings below).")))
                        .build());
            } else if (hasItemListPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        if (applyPickedItemFilter(stack)) return;
                        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        if (id == null || taskTargetBox == null) return;
                        String cur = taskTargetBox.getValue().trim();
                        taskTargetBox.setValue(cur.isEmpty() ? id.toString() : cur + ";" + id);
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit("Add another item to the ANY-match list")))
                        .build());
            } else if (hasFluidPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§3⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new FluidPickerScreen(this, fluidId -> {
                        if (taskTargetBox != null) taskTargetBox.setValue(fluidId);
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H).build());
            } else if (hasFluidListPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§3⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new FluidPickerScreen(this, fluidId -> {
                        if (taskTargetBox == null) return;
                        String cur = taskTargetBox.getValue().trim();
                        taskTargetBox.setValue(cur.isEmpty() ? fluidId : cur + ";" + fluidId);
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit("Add another fluid to the ANY-match list")))
                        .build());
            } else if (hasBlockPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        Block block = Block.byItem(stack.getItem());
                        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                        if (id != null && taskTargetBox != null) taskTargetBox.setValue(id.toString());
                    }));
                }).bounds(tx + tw, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(
                                ChroniclesUIKit.lit("Browse items - picking one uses its block form")))
                        .build());
            } else if (hasEntityPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    java.util.Collection<ResourceLocation> ids = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                            .getKeys();
                    if (minecraft != null) {
                        minecraft.setScreen(new EntityIdPickerScreen(this, "Pick entity", ids, id -> {
                            if (taskTargetBox != null) taskTargetBox.setValue(id.toString());
                        }));
                    }
                }).bounds(tx + tw, fy, 16, FIELD_H).build());
            } else if (hasRegistryIdPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    java.util.Collection<ResourceLocation> ids = form.registryIds();
                    if (minecraft != null && ids != null) {
                        minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick " + taskType, ids, id -> {
                            if (taskTargetBox != null) taskTargetBox.setValue(id.toString());
                        }));
                    }
                }).bounds(tx + tw, fy, 16, FIELD_H).build());
            } else if (hasRecipeTypePicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick recipe type",
                                ForgeRegistries.RECIPE_TYPES.getKeys(), id -> {
                                    if (taskTargetBox != null) taskTargetBox.setValue(id.toString());
                                }));
                    }
                }).bounds(tx + tw, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse recipe types, including every machine")))
                        .build());
            } else if (hasEnergyTypeCycle) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§6⟳"), b -> {
                    if (taskTargetBox == null) return;
                    String[] options = { "FE", "EU", "ANY" };
                    String cur = taskTargetBox.getValue().trim().toUpperCase();
                    int idx = java.util.Arrays.asList(options).indexOf(cur);
                    taskTargetBox.setValue(options[(idx + 1) % options.length]);
                }).bounds(tx + tw, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit("Cycle FE / EU / ANY")))
                        .build());
            } else if (hasStringIdPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    java.util.Collection<String> ids = form.stringIds();
                    if (minecraft != null && ids != null) {
                        minecraft.setScreen(new StringIdPickerScreen(this, "Pick " + taskType, ids, id -> {
                            if (taskTargetBox != null) taskTargetBox.setValue(id);
                        }));
                    }
                }).bounds(tx + tw, fy, 16, FIELD_H).build());
            }
            if (hasAnyItemPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§d⚡"), b -> applyHeldItemFilter())
                        .bounds(tx + tw + 18, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                                "Use the configured filter token held in your hand\n(from the Item/Fluid Filter items) as this task's match rule")))
                        .build());
            } else if (hasAnyFluidPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§d⚡"), b -> applyHeldFluidFilter())
                        .bounds(tx + tw + 18, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                                "Use the configured filter token held in your hand\n(from the Item/Fluid Filter items) as this task's match rule")))
                        .build());
            }
            fy += FIELD_H + FIELD_GAP;
        }

        taskNbtBox = null;
        if (form.hasNbtBox()) {
            taskNbtBox = new EditBox(font, tx, fy, colW, FIELD_H, Component.empty());
            setHintFitted(taskNbtBox, "§8NBT filter (optional)");
            taskNbtBox.setMaxLength(512);
            taskNbtBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    ChroniclesUIKit.lit(
                            "Subset NBT match. Item must contain ALL keys listed here.\nLeave blank to match any stack of the item.")));
            taskNbtBox.setValue(nbtVal);
            addRenderableWidget(taskNbtBox);
            fy += FIELD_H + FIELD_GAP;
        }

        if (needsSecond) {
            String hint2 = form.secondHint();
            SecondPicker secondPicker = form.secondPicker();
            boolean hasDimensionPicker = secondPicker == SecondPicker.DIMENSION;
            boolean hasSourceCycle = secondPicker == SecondPicker.ENERGY_SOURCE;
            boolean hasRecipePicker = secondPicker == SecondPicker.RECIPE;
            int secondW = (hasDimensionPicker || hasSourceCycle || hasRecipePicker) ? colW - 18 : colW;
            taskSecondaryBox = new EditBox(font, tx, fy, secondW, FIELD_H, Component.empty());
            setHintFitted(taskSecondaryBox, hint2);
            taskSecondaryBox.setMaxLength(128);
            taskSecondaryBox.setValue(secondVal);
            if (secondPicker == SecondPicker.MODE_TOGGLE) {

                String curMode = secondVal.trim().equalsIgnoreCase("RIGHT_CLICK") ? "RIGHT_CLICK" : "PLACE";
                taskSecondaryBox.setValue(curMode);
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§fMode: §e" + curMode), b -> {
                    String next = taskSecondaryBox.getValue().equalsIgnoreCase("PLACE") ? "RIGHT_CLICK" : "PLACE";
                    taskSecondaryBox.setValue(next);
                    b.setMessage(ChroniclesUIKit.lit("§fMode: §e" + next));
                }).bounds(tx, fy, colW, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                                "PLACE: the player places the block.\nRIGHT_CLICK: the player right-clicks it.\n" +
                                        "Click to switch.")))
                        .build());
            } else {
                addRenderableWidget(taskSecondaryBox);
            }
            if (hasDimensionPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    if (minecraft == null || minecraft.getConnection() == null) return;
                    java.util.Collection<ResourceLocation> ids = minecraft.getConnection().levels().stream()
                            .map(k -> k.location()).toList();
                    minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick dimension", ids, id -> {
                        if (taskSecondaryBox != null) taskSecondaryBox.setValue(id.toString());
                    }));
                }).bounds(tx + secondW, fy, 16, FIELD_H).build());
            } else if (hasRecipePicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    if (minecraft == null || minecraft.level == null) return;
                    ResourceLocation wantedType = taskTargetBox != null ?
                            ResourceLocation.tryParse(taskTargetBox.getValue().trim()) : null;
                    java.util.List<ResourceLocation> ids = new ArrayList<>();
                    for (net.minecraft.world.item.crafting.Recipe<?> recipe : minecraft.level.getRecipeManager()
                            .getRecipes()) {
                        ResourceLocation typeKey = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());
                        if (wantedType == null || wantedType.equals(typeKey)) ids.add(recipe.getId());
                    }
                    minecraft.setScreen(new RegistryIdPickerScreen(this,
                            wantedType != null ? "Pick " + wantedType + " recipe" : "Pick recipe", ids, id -> {
                                if (taskSecondaryBox != null) taskSecondaryBox.setValue(id.toString());
                            }));
                }).bounds(tx + secondW, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                                "Browse the recipes of the type above (fill the type in first to narrow the list)")))
                        .build());
            } else if (hasSourceCycle) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§6⟳"), b -> {
                    if (taskSecondaryBox == null) return;
                    String[] options = { "INVENTORY", "HELD", "BLOCK" };
                    String cur = taskSecondaryBox.getValue().trim().toUpperCase();
                    int idx = java.util.Arrays.asList(options).indexOf(cur);
                    taskSecondaryBox.setValue(options[(idx + 1) % options.length]);
                }).bounds(tx + secondW, fy, 16, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit("Cycle INVENTORY / HELD / BLOCK")))
                        .build());
            }
            fy += FIELD_H + FIELD_GAP;
        }

        int rowY = formBottom - FIELD_H - 4;
        int flexGap = 4;
        int flexX = tx;
        int flexAvail = colW;
        if (needsCount) {
            String countHint = form.countHint();
            taskCountBox = new EditBox(font, tx, rowY, 52, FIELD_H, Component.empty());
            setHintFitted(taskCountBox, countHint);
            taskCountBox.setMaxLength(8);
            taskCountBox.setValue(countVal);
            addRenderableWidget(taskCountBox);
            flexX = tx + 52 + flexGap;
            flexAvail = colW - 52 - flexGap;
        }

        record FlexBtn(int idealW, java.util.function.Supplier<Button.Builder> factory) {}
        List<FlexBtn> flexBtns = new ArrayList<>();
        if (showConsume) {
            flexBtns.add(new FlexBtn(54, () -> Button.builder(
                    ChroniclesUIKit.lit(taskConsume ? "§aConsume" : "§8Consume"),
                    b -> {
                        taskConsume = !taskConsume;
                        rebuildWidgets();
                    }).tooltip(Tooltip.create(
                            ChroniclesUIKit.lit(
                                    "Item tasks: the player clicks the task to hand the items in (shop style).\nOther types: taken when rewards are claimed.")))));
        }
        if (showAe2Toggle) {
            flexBtns.add(new FlexBtn(40, () -> Button.builder(
                    ChroniclesUIKit.lit(taskCheckAe2Storage ? "§bAE2" : "§8AE2"),
                    b -> {
                        taskCheckAe2Storage = !taskCheckAe2Storage;
                        rebuildWidgets();
                    }).tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "ON (default when AE2 is installed): also count/withdraw matching items or\n" +
                                    "fluid stored in your linked Applied Energistics 2 ME network, in addition\n" +
                                    "to the player's inventory.")))));
        }
        if (showSticky) {
            flexBtns.add(new FlexBtn(56, () -> Button.builder(
                    ChroniclesUIKit.lit(taskSticky ? "§bSticky" : "§8Sticky"),
                    b -> {
                        taskSticky = !taskSticky;
                        rebuildWidgets();
                    }).tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "ON (default): once satisfied, stays satisfied - placing/using the item\n" +
                                    "later won't un-complete this task.\n" +
                                    "OFF: re-checked live - task un-completes if you stop holding enough.")))));
        }
        flexBtns.add(new FlexBtn(50, () -> Button.builder(
                ChroniclesUIKit.lit(taskOptional ? "§eOptional" : "§8Optional"),
                b -> {
                    taskOptional = !taskOptional;
                    rebuildWidgets();
                }).tooltip(Tooltip.create(ChroniclesUIKit.lit("Task is optional: won't block quest completion")))));
        flexBtns.add(new FlexBtn(62, () -> Button.builder(hideLabel(taskHiddenParts), b -> {
            taskHiddenParts = (taskHiddenParts + 1) % 4;
            rebuildWidgets();
        }).tooltip(Tooltip.create(ChroniclesUIKit.lit(HIDE_TOOLTIP.formatted("task is completed"))))));
        flexBtns.add(new FlexBtn(46, () -> Button.builder(
                ChroniclesUIKit.lit(editingTaskIndex >= 0 ? "§b✎ Update" : "§a✔ Add"),
                b -> commitTaskFromForm())
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(editingTaskIndex >= 0 ?
                        "Save changes to this task (right-click it again to cancel)" :
                        "Add this task to the quest (Ctrl+Z to undo)")))));

        int idealTotal = flexBtns.stream().mapToInt(FlexBtn::idealW).sum() + flexGap * (flexBtns.size() - 1);
        int minFlexBtnW = 26;
        double scale = idealTotal > flexAvail && idealTotal > 0 ? Math.max(
                minFlexBtnW * flexBtns.size() / (double) idealTotal, flexAvail / (double) idealTotal) : 1.0;
        int flexCursor = flexX;
        for (FlexBtn fb : flexBtns) {
            int w = Math.max(minFlexBtnW, (int) Math.round(fb.idealW() * scale));
            addRenderableWidget(fb.factory().get().bounds(flexCursor, rowY, w, FIELD_H).build());
            flexCursor += w + flexGap;
        }

        if (emergencyMode) {
            taskTypeDropOpen = false;
            for (var widget : new ArrayList<>(children().subList(taskFormStart, children().size()))) {
                removeWidget(widget);
            }
        }

        int rx = splitX;
        int rfy = formTop + 8;

        String rewardTypeTooltip = RewardFormTypes.get(rewardType).tooltip();
        addRenderableWidget(Button.builder(
                ChroniclesUIKit.lit("§8Type: §7" + RewardFormTypes.get(rewardType).label() + " §8▾"),
                b -> {
                    rewardTypeDropOpen = !rewardTypeDropOpen;
                    taskTypeDropOpen = false;
                })
                .bounds(rx, rfy, colW, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(rewardTypeTooltip))).build());
        rfy += FIELD_H + FIELD_GAP;

        boolean editingBoxOption = rewardType.equals("choice_box") && editingBoxOptionIndex >= 0;
        boolean editingTableOption = editingBoxOption && editingBoxOptionIndex < boxOptions.size() &&
                boxOptions.get(editingBoxOptionIndex) instanceof QuestReward.RewardTableReward;
        boolean editingItemOption = editingBoxOption && !editingTableOption;

        if (rewardType.equals("item")) {
            String itemLabel = rewardPickedItem != null ? "§f" + rewardPickedItem.getHoverName().getString() :
                    "§8Pick Item";
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit(itemLabel), b -> {
                if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    rewardPickedItem = stack;
                    rebuildWidgets();
                }).withBulk(this::bulkAddItemRewards));
            }).bounds(rx, rfy, colW - 44, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Pick an item. Right-click several in the picker to add one reward per item at once.")))
                    .build());
            rewardCountBox = new EditBox(font, rx + colW - 42, rfy, 42, FIELD_H, Component.empty());
            setHintFitted(rewardCountBox, "§8Qty");
            rewardCountBox.setMaxLength(4);
            rewardCountBox.setValue(rCountVal);
            addRenderableWidget(rewardCountBox);
            if (AE2Compat.isAvailable()) {
                rfy += FIELD_H + FIELD_GAP;
                addRenderableWidget(rewardAe2Button(rx, rfy, colW, "items"));
            }
        } else if (rewardType.equals("fluid")) {
            rewardCommandBox = new EditBox(font, rx, rfy, colW - 62, FIELD_H, Component.empty());
            setHintFitted(rewardCommandBox, "§8Fluid id  (e.g. gtceu:oxygen)");
            rewardCommandBox.setMaxLength(128);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§3⊞"), b -> {
                if (minecraft != null) minecraft.setScreen(new FluidPickerScreen(this, fluidId -> {
                    pendingRewardCommand = fluidId;
                    pendingRewardCount = rewardCountBox != null ? rewardCountBox.getValue() : pendingRewardCount;
                    forcePendingRewardValues = true;
                    rebuildWidgets();
                }));
            }).bounds(rx + colW - 60, rfy, 16, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit("Pick a fluid")))
                    .build());
            rewardCountBox = new EditBox(font, rx + colW - 42, rfy, 42, FIELD_H, Component.empty());
            setHintFitted(rewardCountBox, "§8mB");
            rewardCountBox.setMaxLength(8);
            rewardCountBox.setValue(rCountVal);
            addRenderableWidget(rewardCountBox);
            rfy += FIELD_H + FIELD_GAP;
            if (AE2Compat.isAvailable()) {
                addRenderableWidget(rewardAe2Button(rx, rfy, colW, "fluid"));
            } else {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§8No AE2: whole buckets only"), b -> {})
                        .bounds(rx, rfy, colW, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                                "Without Applied Energistics 2 installed, fluid rewards are delivered as whole\n" +
                                        "buckets (if the fluid has a bucket item).")))
                        .build());
            }
        } else if (rewardType.equals("xp")) {
            rewardCountBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            setHintFitted(rewardCountBox, "§8XP levels to award");
            rewardCountBox.setMaxLength(5);
            rewardCountBox.setValue(rCountVal);
            addRenderableWidget(rewardCountBox);
        } else if (rewardType.equals("script_event")) {
            rewardCommandBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            setHintFitted(rewardCommandBox, "§8Event ID  (e.g. unlock_end)");
            rewardCommandBox.setMaxLength(128);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
            rfy += FIELD_H + FIELD_GAP;
            rewardEventDataBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            setHintFitted(rewardEventDataBox, "§8NBT data  {key:\"val\"}  (optional)");
            rewardEventDataBox.setMaxLength(256);
            rewardEventDataBox.setValue(rEventDataVal);
            addRenderableWidget(rewardEventDataBox);
        } else if (rewardType.equals("reward_table")) {
            rewardCommandBox = new EditBox(font, rx, rfy, colW - 20, FIELD_H, Component.empty());
            setTableIdHint(rewardCommandBox);
            rewardCommandBox.setMaxLength(128);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("🎲"), b -> {
                String tid = rewardCommandBox.getValue().trim();
                if (minecraft != null && !tid.isEmpty()) minecraft.setScreen(new RewardTableSimulatorScreen(this, tid));
            }).bounds(rx + colW - 18, rfy, 18, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit("Simulate 1000 rolls against this table")))
                    .build());
        } else if (editingTableOption) {
            boxOptionTableBox = new EditBox(font, rx, rfy, colW - 20, FIELD_H, Component.empty());
            setTableIdHint(boxOptionTableBox);
            boxOptionTableBox.setMaxLength(128);
            boxOptionTableBox.setValue(boTableVal);
            addRenderableWidget(boxOptionTableBox);
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("🎲"), b -> {
                String tid = boxOptionTableBox.getValue().trim();
                if (minecraft != null && !tid.isEmpty())
                    minecraft.setScreen(new RewardTableSimulatorScreen(this, tid));
            }).bounds(rx + colW - 18, rfy, 18, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit("Simulate 1000 rolls against this table")))
                    .build());
            rfy += FIELD_H + FIELD_GAP;

            addRenderableWidget(
                    Button.builder(ChroniclesUIKit.lit("§a✔ Save Option"), b -> commitBoxOptionEdit())
                            .bounds(rx, rfy, colW / 2 - 2, FIELD_H)
                            .tooltip(Tooltip.create(ChroniclesUIKit.lit("Save changes to this option")))
                            .build());
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7Cancel"), b -> cancelBoxOptionEdit())
                    .bounds(rx + colW / 2 + 2, rfy, colW / 2 - 2, FIELD_H).build());
        } else if (editingItemOption) {
            String itemLabel = boxOptionPickedItem != null ?
                    "§f" + boxOptionPickedItem.getHoverName().getString() : "§8Pick Item";
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit(itemLabel), b -> {
                if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    boxOptionPickedItem = stack;
                    rebuildWidgets();
                }));
            }).bounds(rx, rfy, colW - 44, FIELD_H).build());
            boxOptionCountBox = new EditBox(font, rx + colW - 42, rfy, 42, FIELD_H, Component.empty());
            setHintFitted(boxOptionCountBox, "§8Qty");
            boxOptionCountBox.setMaxLength(4);
            boxOptionCountBox.setValue(boCountVal);
            addRenderableWidget(boxOptionCountBox);
            rfy += FIELD_H + FIELD_GAP;

            boxOptionNbtBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
            setHintFitted(boxOptionNbtBox, "§8NBT  {display:{Name:'...'}}  (optional)");
            boxOptionNbtBox.setMaxLength(256);
            boxOptionNbtBox.setValue(boNbtVal);
            addRenderableWidget(boxOptionNbtBox);
            rfy += FIELD_H + FIELD_GAP;

            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§a✔ Save Option"), b -> commitBoxOptionEdit())
                    .bounds(rx, rfy, colW / 2 - 2, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit("Save changes to this option")))
                    .build());
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7Cancel"), b -> cancelBoxOptionEdit())
                    .bounds(rx + colW / 2 + 2, rfy, colW / 2 - 2, FIELD_H).build());
        } else if (rewardType.equals("choice_box")) {
            addRenderableWidget(Button.builder(
                    ChroniclesUIKit.lit("§8Mode: §7" +
                            (switch (boxMode) {
                                case LOOTBOX -> "Lootbox";
                                case ALL -> "All";
                                default -> "Menu";
                            }) + " §8▾"),
                    b -> {
                        boxMode = switch (boxMode) {
                            case MENU -> QuestReward.ChoiceBoxReward.Mode.LOOTBOX;
                            case LOOTBOX -> QuestReward.ChoiceBoxReward.Mode.ALL;
                            default -> QuestReward.ChoiceBoxReward.Mode.MENU;
                        };
                        rebuildWidgets();
                    })
                    .bounds(rx, rfy, colW - CHOICE_ADD_ITEM_W - CHOICE_ADD_TABLE_W - 4, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Menu: player clicks the box and picks which option they get.\n" +
                                    "Lootbox: player clicks the box and the server grants a random option.\n" +
                                    "All: every option is granted when the quest is claimed.")))
                    .build());
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§a+ Item"), b -> {
                if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                    boxOptions.add(new QuestReward.ItemReward(stack.getItem(), Math.max(1, stack.getCount())));
                    rebuildWidgets();
                }));
            }).bounds(rx + colW - CHOICE_ADD_ITEM_W - CHOICE_ADD_TABLE_W - 2, rfy, CHOICE_ADD_ITEM_W, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Add an item option to this choice box (right-click an option to edit its qty/NBT)")))
                    .build());
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§6+ Table"), b -> {
                boxOptions.add(new QuestReward.RewardTableReward(""));
                startEditingBoxOption(boxOptions.size() - 1);
            }).bounds(rx + colW - CHOICE_ADD_TABLE_W, rfy, CHOICE_ADD_TABLE_W, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Add a reward table option: picking it rolls that table (right-click it to change the table)")))
                    .build());
            rfy += FIELD_H + FIELD_GAP;

            if (boxMode == QuestReward.ChoiceBoxReward.Mode.LOOTBOX && !boxOptions.isEmpty()) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("🎲 Simulate rolls"), b -> {
                    java.util.List<QuestReward.WeightedReward> weighted = boxOptions.stream()
                            .map(r -> new QuestReward.WeightedReward(r, 1)).toList();
                    RewardTable preview = new RewardTable("preview", "Lootbox preview", weighted, 1);
                    if (minecraft != null) minecraft.setScreen(new RewardTableSimulatorScreen(this, preview));
                }).bounds(rx, rfy, colW, FIELD_H)
                        .tooltip(Tooltip.create(
                                ChroniclesUIKit
                                        .lit("Roll this lootbox many times to see how often each option comes up")))
                        .build());
                rfy += FIELD_H + FIELD_GAP;
            }

            boxOptionsListX = rx;
            boxOptionsListY = rfy;
            boxOptionsListW = colW;
            boxOptionsListBottom = formBottom - FIELD_H - 4 - FIELD_GAP;
            int visibleRows = Math.max(1, (boxOptionsListBottom - boxOptionsListY) / BOX_OPTION_ROW_H);
            int maxScroll = Math.max(0, boxOptions.size() - visibleRows);
            boxOptionsScroll = Math.max(0, Math.min(boxOptionsScroll, maxScroll));
        } else if (rewardType.equals("external")) {
            PhoenixRewardRegistry.Entry externalEntry = PhoenixRewardRegistry.get(rCommandVal.trim());
            externalEntryAtBuild = externalEntry;
            rewardCommandBox = new EditBox(font, rx, rfy, colW - 20, FIELD_H, Component.empty());
            setHintFitted(rewardCommandBox, "§8Registered external reward id");
            rewardCommandBox.setMaxLength(128);
            rewardCommandBox.setValue(rCommandVal);
            rewardCommandBox.setResponder(v -> {
                if (PhoenixRewardRegistry.get(v.trim()) != externalEntryAtBuild) {
                    pendingRewardCommand = v;
                    pendingRewardEventData = rewardEventDataBox != null ? rewardEventDataBox.getValue() :
                            pendingRewardEventData;
                    forcePendingRewardValues = true;
                    rebuildWidgets();
                }
            });
            addRenderableWidget(rewardCommandBox);
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                java.util.List<ResourceLocation> ids = new ArrayList<>();
                for (PhoenixRewardRegistry.Entry e : PhoenixRewardRegistry.all()) {
                    ResourceLocation id = ResourceLocation.tryParse(e.typeId());
                    if (id != null) ids.add(id);
                }
                if (minecraft != null && !ids.isEmpty()) {
                    minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick external reward", ids, id -> {
                        pendingRewardCommand = id.toString();
                        forcePendingRewardValues = true;
                        rebuildWidgets();
                    }));
                }
            }).bounds(rx + colW - 18, rfy, 18, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered external reward types")))
                    .build());
            rfy += FIELD_H + FIELD_GAP;

            if (externalEntry != null && !externalEntry.fields().isEmpty()) {
                for (PhoenixTaskRegistry.FieldDef field : externalEntry.fields()) {
                    String current = externalFieldValues.getOrDefault(field.id(), "");
                    if (field.type() == PhoenixTaskRegistry.FieldDef.FieldType.BOOLEAN) {
                        boolean on = Boolean.parseBoolean(current);
                        addRenderableWidget(Button.builder(
                                ChroniclesUIKit.lit("§8" + field.label() + ": " + (on ? "§aON" : "§7OFF")), b -> {
                                    externalFieldValues.put(field.id(), String.valueOf(!on));
                                    rebuildWidgets();
                                }).bounds(rx, rfy, colW, FIELD_H).build());
                    } else {
                        EditBox box = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
                        setHintFitted(box, "§8" + field.label() +
                                (field.hint() != null ? "  (" + field.hint() + ")" : ""));
                        box.setMaxLength(256);
                        box.setValue(current);
                        box.setResponder(v -> externalFieldValues.put(field.id(), v));
                        addRenderableWidget(box);
                    }
                    rfy += FIELD_H + FIELD_GAP;
                }
            } else {
                rewardEventDataBox = new EditBox(font, rx, rfy, colW, FIELD_H, Component.empty());
                setHintFitted(rewardEventDataBox, "§8NBT data  {key:\"val\"}  (optional)");
                rewardEventDataBox.setMaxLength(256);
                rewardEventDataBox.setValue(rEventDataVal);
                addRenderableWidget(rewardEventDataBox);
            }
        } else if (rewardType.equals("open_screen")) {
            rewardCommandBox = new EditBox(font, rx, rfy, colW - 20, FIELD_H, Component.empty());
            setHintFitted(rewardCommandBox, "§8Registered external screen id");
            rewardCommandBox.setMaxLength(128);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
            addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                java.util.Collection<ResourceLocation> ids = net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry
                        .registeredIds();
                if (minecraft != null && !ids.isEmpty()) {
                    minecraft.setScreen(new RegistryIdPickerScreen(this, "Pick external screen", ids, id -> {
                        pendingRewardCommand = id.toString();
                        forcePendingRewardValues = true;
                        rebuildWidgets();
                    }));
                }
            }).bounds(rx + colW - 18, rfy, 18, FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered external screens")))
                    .build());
        } else {

            String hint = rewardType.equals("loot_table") ?
                    "§8Loot table id  (e.g. minecraft:chests/simple_dungeon)" :
                    rewardType.equals("quest_action") ?
                            "§8Quest id  (e.g. my_quest or phoenix_chronicles:my_quest)" :
                            "§8/give {p} …";
            boolean questPicker = rewardType.equals("quest_action");
            rewardCommandBox = new EditBox(font, rx, rfy, questPicker ? colW - 22 : colW, FIELD_H,
                    Component.empty());
            setHintFitted(rewardCommandBox, hint);
            rewardCommandBox.setMaxLength(256);
            rewardCommandBox.setValue(rCommandVal);
            addRenderableWidget(rewardCommandBox);
            if (questPicker) {
                addRenderableWidget(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                    if (rewardCommandBox != null) pendingRewardCommand = rewardCommandBox.getValue();
                    if (minecraft != null && questNode != null) {
                        minecraft.setScreen(ParentSelectorScreen.singleSelect(this, questNode, picked -> {
                            pendingRewardCommand = picked.getId().toString();
                            forcePendingRewardValues = true;
                            rebuildWidgets();
                        }));
                    }
                }).bounds(rx + colW - 18, rfy, 18, FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                                "Pick a quest (starts in this quest's chapter; use the dropdown for others)")))
                        .build());
            }
            if (rewardType.equals("command")) {
                rfy += FIELD_H + FIELD_GAP;
                addRenderableWidget(rewardPermButton(rx, rfy, colW));
            } else if (rewardType.equals("quest_action")) {
                rfy += FIELD_H + FIELD_GAP;
                addRenderableWidget(rewardQuestActionButton(rx, rfy, colW));
            }
        }

        addRenderableWidget(Button.builder(hideLabel(rewardHiddenParts), b -> {
            rewardHiddenParts = (rewardHiddenParts + 1) % 4;
            rebuildWidgets();
        }).bounds(rx + colW - 80 - 4 - 62, formBottom - FIELD_H - 4, 62, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(HIDE_TOOLTIP.formatted("quest is completed"))))
                .build());
        addRenderableWidget(Button.builder(
                ChroniclesUIKit.lit(editingRewardIndex >= 0 ? "§b✎ Update Reward" : "§a✔ Add Reward"),
                b -> commitRewardFromForm())
                .bounds(rx + colW - 80, formBottom - FIELD_H - 4, 80, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(editingRewardIndex >= 0 ?
                        "Save changes to this reward (right-click it again to cancel)" :
                        "Add this reward to the quest (Ctrl+Z to undo)")))
                .build());
    }

    private static final String HIDE_TOOLTIP = "Hide parts of this entry in the quest viewer until the %s.\n" +
            "Click to cycle: off > amount > target > amount + target.\n" +
            "Example: show that iron is needed but not how much.";

    private static net.minecraft.network.chat.Component hideLabel(int parts) {
        return ChroniclesUIKit.lit(switch (parts) {
            case 1 -> "§dHide: amount";
            case 2 -> "§dHide: target";
            case 3 -> "§dHide: both";
            default -> "§8Hide: off";
        });
    }

    private static String permLabel(int level) {
        return switch (level) {
            case 0 -> "0 · everyone";
            case 1 -> "1 · moderator";
            case 2 -> "2 · gamemaster";
            case 3 -> "3 · admin";
            default -> "4 · owner (full)";
        };
    }

    private Button rewardQuestActionButton(int x, int y, int w) {
        boolean complete = rewardQuestAction == QuestReward.QuestActionReward.Action.COMPLETE;
        return Button.builder(
                ChroniclesUIKit.lit("§7Action: §f" + (complete ? "Complete quest" : "Reset progress")),
                b -> {
                    if (rewardCommandBox != null) pendingRewardCommand = rewardCommandBox.getValue();
                    forcePendingRewardValues = true;
                    rewardQuestAction = complete ? QuestReward.QuestActionReward.Action.RESET :
                            QuestReward.QuestActionReward.Action.COMPLETE;
                    rebuildWidgets();
                })
                .bounds(x, y, w, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Complete: marks the quest done for the player (children unlock as normal).\n" +
                                "Reset: clears the quest's progress so it can be done again.")))
                .build();
    }

    private Button rewardPermButton(int x, int y, int w) {
        return Button.builder(
                ChroniclesUIKit.lit("§7Runs as: §f" + permLabel(rewardPermLevel)),
                b -> {
                    if (rewardCommandBox != null) pendingRewardCommand = rewardCommandBox.getValue();
                    forcePendingRewardValues = true;
                    rewardPermLevel = (rewardPermLevel + 1) % 5;
                    rebuildWidgets();
                })
                .bounds(x, y, w, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Permission level the command runs with (click to cycle).\n" +
                                "Lower levels limit what a quest author can make the server run.\n" +
                                "Note: target selectors (@p, @a, @e, @s) need level 2 or higher;\n" +
                                "use {p} for the claiming player at levels 0-1.")))
                .build();
    }

    private Button rewardAe2Button(int x, int y, int w, String what) {
        return Button.builder(
                ChroniclesUIKit.lit(rewardPushAe2 ? "§bAE2: push to network" : "§8AE2: off (inventory)"),
                b -> {
                    if (rewardCountBox != null) pendingRewardCount = rewardCountBox.getValue();
                    if (rewardCommandBox != null) pendingRewardCommand = rewardCommandBox.getValue();
                    forcePendingRewardValues = true;
                    rewardPushAe2 = !rewardPushAe2;
                    rebuildWidgets();
                })
                .bounds(x, y, w, FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "ON (default when AE2 is installed): insert the " + what + " straight into the player's\n" +
                                "linked Applied Energistics 2 ME network. Anything that doesn't fit, or a player\n" +
                                "with no linked terminal, gets it in their inventory instead.")))
                .build();
    }

    private List<QuestTask> dragBeforeTasks;
    private List<QuestReward> dragBeforeRewards;

    private void pushUndo(Runnable mutation) {
        List<QuestTask> beforeTasks = new ArrayList<>(tasks);
        List<QuestReward> beforeRewards = new ArrayList<>(rewards);

        mutation.run();

        registerUndoRedo(beforeTasks, beforeRewards, new ArrayList<>(tasks), new ArrayList<>(rewards));
    }

    private void beginDragUndo() {
        dragBeforeTasks = new ArrayList<>(tasks);
        dragBeforeRewards = new ArrayList<>(rewards);
    }

    private void finishDragUndo() {
        if (dragBeforeTasks == null) return;
        List<QuestTask> beforeTasks = dragBeforeTasks;
        List<QuestReward> beforeRewards = dragBeforeRewards;
        dragBeforeTasks = null;
        dragBeforeRewards = null;
        registerUndoRedo(beforeTasks, beforeRewards, new ArrayList<>(tasks), new ArrayList<>(rewards));
    }

    private void registerUndoRedo(List<QuestTask> beforeTasks, List<QuestReward> beforeRewards,
                                  List<QuestTask> afterTasks, List<QuestReward> afterRewards) {
        undoRedo.push(
                () -> {
                    tasks.clear();
                    tasks.addAll(beforeTasks);
                    rewards.clear();
                    rewards.addAll(beforeRewards);
                    editingTaskIndex = -1;
                    editingRewardIndex = -1;
                    rebuildWidgets();
                },
                () -> {
                    tasks.clear();
                    tasks.addAll(afterTasks);
                    rewards.clear();
                    rewards.addAll(afterRewards);
                    editingTaskIndex = -1;
                    editingRewardIndex = -1;
                    rebuildWidgets();
                });
    }

    private void undoLastChange() {
        undoRedo.undo();
    }

    private void redoLastChange() {
        undoRedo.redo();
    }

    private boolean applyPickedItemFilter(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFilterTokenItem)) return false;
        IItemFilter filter = ItemFilterTokenItem.getFilter(stack);
        if (filter == null) return false;
        taskType = "filter_item";
        pendingPickedItemFilter = filter;
        pendingTaskTarget = filter.describe();
        forcePendingTaskValues = true;
        rebuildWidgets();
        return true;
    }

    private void applyHeldItemFilter() {
        if (minecraft == null || minecraft.player == null) return;
        if (applyPickedItemFilter(minecraft.player.getMainHandItem())) return;
        applyPickedItemFilter(minecraft.player.getOffhandItem());
    }

    private void applyHeldFluidFilter() {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack held = minecraft.player.getMainHandItem();
        IFluidFilter filter = held.getItem() instanceof FluidFilterTokenItem ?
                FluidFilterTokenItem.getFilter(held) : null;
        if (filter == null) {
            held = minecraft.player.getOffhandItem();
            filter = held.getItem() instanceof FluidFilterTokenItem ? FluidFilterTokenItem.getFilter(held) : null;
        }
        if (filter == null) return;
        taskType = "filter_fluid";
        pendingPickedFluidFilter = filter;
        pendingTaskTarget = filter.describe();
        forcePendingTaskValues = true;
        rebuildWidgets();
    }

    private void bulkAddItemTasks(List<ItemStack> picked) {
        if (editingTaskIndex >= 0 || taskTargetBox == null) return;
        String descTemplate = taskDescBox != null ? taskDescBox.getValue().trim() : "";
        String countVal = taskCountBox != null ? taskCountBox.getValue() : "1";
        boolean optional = taskOptional, consume = taskConsume, sticky = taskSticky;
        boolean ae2 = taskCheckAe2Storage;
        int hidden = taskHiddenParts;
        for (ItemStack stack : picked) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null || taskTargetBox == null) continue;
            String name = stack.getHoverName().getString();
            String verb = taskType.equals("craft_item") ? "Craft " : "Collect ";
            taskTargetBox.setValue(id.toString());
            if (taskDescBox != null) {
                taskDescBox.setValue(descTemplate.isEmpty() ? verb + name : descTemplate.replace("{item}", name));
            }
            if (taskCountBox != null) taskCountBox.setValue(countVal);
            taskOptional = optional;
            taskConsume = consume;
            taskSticky = sticky;
            taskCheckAe2Storage = ae2;
            taskHiddenParts = hidden;
            commitTaskFromForm();
        }
    }

    private void bulkAddItemRewards(List<ItemStack> picked) {
        if (editingRewardIndex >= 0) return;
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(rewardCountBox != null ? rewardCountBox.getValue().trim() : "1"));
        } catch (NumberFormatException ignored) {}
        final int qty = count;
        final int hidden = rewardHiddenParts;
        final List<QuestReward> added = new ArrayList<>();
        for (ItemStack stack : picked) {
            QuestReward r = new QuestReward.ItemReward(stack.getItem(), qty, stack.getTag(), rewardPushAe2);
            r.setHiddenParts(hidden);
            added.add(r);
        }
        pushUndo(() -> {
            rewards.addAll(added);
            rewardListScroll = Integer.MAX_VALUE;
        });
        rewardPickedItem = null;
        rebuildWidgets();
    }

    private void commitTaskFromForm() {
        String desc = taskDescBox != null ? taskDescBox.getValue().trim() : "";
        String target = taskTargetBox != null ? taskTargetBox.getValue().trim() : "";
        String second = taskSecondaryBox != null ? taskSecondaryBox.getValue().trim() : "";
        String countS = taskCountBox != null ? taskCountBox.getValue().trim() : "1";
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countS));
        } catch (NumberFormatException ignored) {}

        if (desc.isEmpty() && taskType.equals("item_check") && !target.isEmpty()) {
            try {
                Item targetItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(target));
                if (targetItem != null) {
                    String itemName = new net.minecraft.world.item.ItemStack(targetItem).getHoverName().getString();
                    desc = count > 1 ? "Collect " + count + "x " + itemName : "Collect " + itemName;
                    if (taskDescBox != null) taskDescBox.setValue(desc);
                }
            } catch (Exception ignored) {}
        }

        TaskFormType form = TaskFormTypes.get(taskType);
        if (desc.isEmpty() || (form.needsTarget() && !form.targetOptional() && target.isEmpty())) return;

        ResourceLocation taskId = (editingTaskIndex >= 0 && editingTaskIndex < tasks.size()) ?
                tasks.get(editingTaskIndex).getTaskId() :
                ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "task_" + taskType + "_" +
                        java.util.UUID.randomUUID().toString().replace("-", ""));
        TaskFormValues values = new TaskFormValues();
        values.taskId = taskId;
        values.description = ChroniclesUIKit.lit(desc);
        values.target = target;
        values.second = second;
        values.nbt = taskNbtBox != null ? taskNbtBox.getValue().trim() : "";
        values.count = count;
        values.consume = taskConsume;
        values.sticky = taskSticky;
        values.checkAe2 = taskCheckAe2Storage;
        values.itemFilter = pendingPickedItemFilter;
        values.fluidFilter = pendingPickedFluidFilter;
        QuestTask task = null;
        try {
            task = form.create(values);
        } catch (Exception ignored) {}

        if (task != null) {
            task.setOptional(taskOptional);
            task.setHiddenParts(taskHiddenParts);
            form.applySticky(task, taskSticky);
            QuestTask finalTask = task;
            pushUndo(() -> {
                if (editingTaskIndex >= 0 && editingTaskIndex < tasks.size()) {
                    tasks.set(editingTaskIndex, finalTask);
                } else {
                    tasks.add(finalTask);
                    taskListScroll = Integer.MAX_VALUE;
                }
                editingTaskIndex = -1;
            });
            taskTypeDropOpen = false;
            taskOptional = false;
            taskHiddenParts = 0;
            taskSticky = true;
            taskConsume = false;
            taskCheckAe2Storage = AE2Compat.isAvailable();
            pendingTaskDesc = pendingTaskTarget = pendingTaskSecondary = pendingTaskNbt = "";
            pendingTaskCount = "1";
            pendingPickedItemFilter = null;
            pendingPickedFluidFilter = null;
            forcePendingTaskValues = true;
            rebuildWidgets();
        }
    }

    private void startEditingTask(int idx) {
        if (idx < 0 || idx >= tasks.size()) return;
        pendingPickedItemFilter = null;
        pendingPickedFluidFilter = null;
        QuestTask t = tasks.get(idx);
        editingTaskIndex = idx;
        taskType = TaskFormTypes.idFor(t);
        taskOptional = t.isOptional();
        taskHiddenParts = t.getHiddenParts();

        TaskFormValues values = new TaskFormValues();
        values.consume = true;
        values.sticky = true;
        values.checkAe2 = AE2Compat.isAvailable();
        TaskFormTypes.get(taskType).load(t, values);
        taskConsume = values.consume;
        taskSticky = values.sticky;
        taskCheckAe2Storage = values.checkAe2;
        pendingTaskDesc = t.getDescriptionRaw().getString();
        pendingTaskTarget = values.target;
        pendingTaskSecondary = values.second;
        pendingTaskCount = String.valueOf(values.count);
        pendingTaskNbt = values.nbt;

        forcePendingTaskValues = true;
        taskTypeDropOpen = false;
        rewardTypeDropOpen = false;
        rebuildWidgets();
    }

    private void cancelTaskEdit() {
        editingTaskIndex = -1;
        taskOptional = false;
        taskHiddenParts = 0;
        taskCheckAe2Storage = AE2Compat.isAvailable();
        pendingTaskDesc = pendingTaskTarget = pendingTaskSecondary = pendingTaskCount = pendingTaskNbt = "";
        pendingPickedItemFilter = null;
        pendingPickedFluidFilter = null;
        forcePendingTaskValues = true;
        rebuildWidgets();
    }

    private void commitRewardFromForm() {
        String countS = rewardCountBox != null ? rewardCountBox.getValue().trim() : "1";
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countS));
        } catch (NumberFormatException ignored) {}

        RewardFormValues values = new RewardFormValues();
        values.count = count;
        values.text = rewardCommandBox != null ? rewardCommandBox.getValue().trim() : "";
        values.eventData = rewardEventDataBox != null ? rewardEventDataBox.getValue() : "";
        values.item = rewardPickedItem;
        values.pushAe2 = rewardPushAe2;
        values.permLevel = rewardPermLevel;
        values.questAction = rewardQuestAction;
        values.boxMode = boxMode;
        values.boxOptions = boxOptions;
        values.externalFields = externalFieldValues;
        QuestReward reward = RewardFormTypes.get(rewardType).create(values);

        if (reward != null) {
            reward.setHiddenParts(rewardHiddenParts);
            pushUndo(() -> {
                if (editingRewardIndex >= 0 && editingRewardIndex < rewards.size()) {
                    rewards.set(editingRewardIndex, reward);
                } else {
                    rewards.add(reward);
                    rewardListScroll = Integer.MAX_VALUE;
                }
                editingRewardIndex = -1;
            });
            rewardPickedItem = null;
            rewardHiddenParts = 0;
            rewardPushAe2 = true;
            rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
            rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;
            rewardTypeDropOpen = false;
            pendingRewardCount = pendingRewardCommand = pendingRewardEventData = "";
            externalFieldValues.clear();
            boxOptions.clear();
            boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
            editingBoxOptionIndex = -1;
            boxOptionPickedItem = null;
            forcePendingRewardValues = true;
            rebuildWidgets();
        }
    }

    private void startEditingReward(int idx) {
        if (idx < 0 || idx >= rewards.size()) return;
        QuestReward r = rewards.get(idx);
        rewardPickedItem = null;
        rewardHiddenParts = r.getHiddenParts();
        rewardPushAe2 = true;
        rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
        rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;
        pendingRewardCount = "1";
        pendingRewardCommand = "";
        pendingRewardEventData = "";
        externalFieldValues.clear();
        boxOptions.clear();
        boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
        editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;

        String typeId = RewardFormTypes.idFor(r);
        if (typeId == null) return;
        RewardFormValues values = new RewardFormValues();
        RewardFormTypes.get(typeId).load(r, values);
        rewardType = typeId;
        rewardPickedItem = values.item;
        rewardPushAe2 = values.pushAe2;
        rewardPermLevel = values.permLevel;
        rewardQuestAction = values.questAction;
        pendingRewardCount = String.valueOf(values.count);
        pendingRewardCommand = values.text;
        pendingRewardEventData = values.eventData;
        externalFieldValues.putAll(values.externalFields);
        boxMode = values.boxMode;
        boxOptions.addAll(values.boxOptions);

        editingRewardIndex = idx;
        forcePendingRewardValues = true;
        taskTypeDropOpen = false;
        rewardTypeDropOpen = false;
        rebuildWidgets();
    }

    private void cancelRewardEdit() {
        editingRewardIndex = -1;
        rewardPickedItem = null;
        rewardPushAe2 = true;
        rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
        rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;
        pendingRewardCount = pendingRewardCommand = pendingRewardEventData = "";
        externalFieldValues.clear();
        boxOptions.clear();
        boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
        editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;
        forcePendingRewardValues = true;
        rebuildWidgets();
    }

    private void startEditingBoxOption(int idx) {
        if (idx < 0 || idx >= boxOptions.size()) return;
        if (boxOptions.get(idx) instanceof QuestReward.RewardTableReward table) {
            editingBoxOptionIndex = idx;
            pendingBoxOptionTable = table.getTableId();
            forcePendingBoxOptionValues = true;
            rebuildWidgets();
            return;
        }
        if (!(boxOptions.get(idx) instanceof QuestReward.ItemReward ir)) return;

        editingBoxOptionIndex = idx;
        boxOptionPickedItem = new ItemStack(ir.getItem(), ir.getCount());
        if (ir.getNbt() != null) boxOptionPickedItem.setTag(ir.getNbt().copy());
        pendingBoxOptionCount = String.valueOf(ir.getCount());
        pendingBoxOptionNbt = ir.getNbt() != null && !ir.getNbt().isEmpty() ? ir.getNbt().toString() : "";
        forcePendingBoxOptionValues = true;
        rebuildWidgets();
    }

    private void commitBoxOptionEdit() {
        if (editingBoxOptionIndex >= 0 && editingBoxOptionIndex < boxOptions.size() &&
                boxOptions.get(editingBoxOptionIndex) instanceof QuestReward.RewardTableReward) {
            String tid = boxOptionTableBox != null ? boxOptionTableBox.getValue().trim() : "";
            if (tid.isEmpty()) boxOptions.remove(editingBoxOptionIndex);
            else boxOptions.set(editingBoxOptionIndex, new QuestReward.RewardTableReward(tid));
            cancelBoxOptionEdit();
            return;
        }
        if (editingBoxOptionIndex < 0 || editingBoxOptionIndex >= boxOptions.size() || boxOptionPickedItem == null) {
            cancelBoxOptionEdit();
            return;
        }

        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(boxOptionCountBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}

        net.minecraft.nbt.CompoundTag nbt = null;
        String nbtStr = boxOptionNbtBox != null ? boxOptionNbtBox.getValue().trim() : "";
        if (!nbtStr.isEmpty()) {
            try {
                nbt = net.minecraft.nbt.TagParser.parseTag(nbtStr);
            } catch (Exception ignored) {}
        }

        boolean keepPush = !(boxOptions.get(editingBoxOptionIndex) instanceof QuestReward.ItemReward old) ||
                old.isPushToAe2();
        boxOptions.set(editingBoxOptionIndex,
                new QuestReward.ItemReward(boxOptionPickedItem.getItem(), count, nbt, keepPush));
        cancelBoxOptionEdit();
    }

    private void cancelBoxOptionEdit() {
        if (editingBoxOptionIndex >= 0 && editingBoxOptionIndex < boxOptions.size() &&
                boxOptions.get(editingBoxOptionIndex) instanceof QuestReward.RewardTableReward table &&
                table.getTableId().isBlank()) {
            boxOptions.remove(editingBoxOptionIndex);
        }
        pendingBoxOptionTable = "";
        editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;
        pendingBoxOptionCount = "1";
        pendingBoxOptionNbt = "";
        forcePendingBoxOptionValues = true;
        rebuildWidgets();
    }

    private void flushToQuestNode() {
        if (emergencyMode) {
            if (emergencySink != null) emergencySink.accept(new ArrayList<>(rewards));
            return;
        }
        if (variantTarget != null) {
            variantTarget.tasks = new ArrayList<>(tasks);
            variantTarget.rewards = new ArrayList<>(rewards);
        } else {
            java.util.Set<ResourceLocation> existingTaskIds = new java.util.HashSet<>();
            for (QuestTask old : questNode.getTasks()) existingTaskIds.add(old.getTaskId());
            boolean addedRequiredTask = false;
            for (QuestTask t : tasks) {
                if (!t.isOptional() && !existingTaskIds.contains(t.getTaskId())) addedRequiredTask = true;
            }

            questNode.clearTasks();
            for (QuestTask t : tasks) questNode.addTask(t);
            questNode.clearRewards();
            for (QuestReward r : rewards) questNode.addReward(r);

            if (addedRequiredTask && QuestTreeRegistry.getQuest(questNode.getId()) == questNode) {
                net.phoenixvine.chronicles.network.ChronicleNetwork.CHANNEL.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2SReopenCompletedQuestPacket(
                                questNode.getId()));
            }
        }

        if (QuestTreeRegistry.getQuest(questNode.getId()) == questNode) {
            QuestFileSaver.saveOneQuestToDisk(questNode);
            LangSyncScheduler.markDirty();
        }
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int rawMx, int rawMy, float partial) {
        if (width != lastLayoutWidth || height != lastLayoutHeight) init();

        int mx = Math.round(rawMx / uiScale);
        int my = Math.round(rawMy / uiScale);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        g.fill(0, 0, vw, vh, C_BG);

        g.fill(0, 0, vw, HEADER_H, C_HEADER);
        g.fill(0, 0, vw, 2, C_ACCENT);
        g.fill(0, HEADER_H - 1, vw, HEADER_H, C_BORDER);
        String repeatBadge = emergencyMode ? "" : switch (questNode.getRepeatMode()) {
            case DAILY -> "  §b[Daily]";
            case COOLDOWN -> "  §e[Cooldown " + questNode.getRepeatCooldownHours() + "h]";
            case INFINITE -> "  §a[∞]";
            default -> "";
        };
        String variantBadge = variantTarget != null ? "  §d[variant: " + variantTarget.condition + "]" : "";
        String headerTitle = emergencyMode ? "§6⚠ §fEmergency Rewards  §8: §7" + emergencyLabel :
                "§fTasks & Rewards  §8: §7" + questNode.getId().getPath() + repeatBadge + variantBadge;
        ChroniclesUIKit.drawCenteredString(g, font, headerTitle, vw / 2, (HEADER_H - 8) / 2, C_TEXT);

        g.fill(0, HEADER_H, vw, listTop - 1, C_PANEL);
        g.fill(0, listTop - 1, vw, listTop, C_BORDER);
        String taskSubHeader;
        if (emergencyMode) {
            taskSubHeader = "§6⚠ §8Emergency kit: no tasks, rewards only";
        } else if (tasks.isEmpty()) {
            taskSubHeader = "§c⚠ No tasks: quest auto-completes on unlock";
        } else {
            long optCount = tasks.stream().filter(QuestTask::isOptional).count();
            long reqCount = tasks.size() - optCount;
            taskSubHeader = "§8Tasks  §7" + reqCount + (optCount > 0 ? "  §8+  §e" + optCount + " opt" : "");
        }
        ChroniclesUIKit.drawString(g, font, taskSubHeader, MARGIN + 4, HEADER_H + 6, C_TEXT_FAINT, false);
        if (copiedTaskNBT != null)
            ChroniclesUIKit.drawString(g, font, "§b[Ctrl+V]", MARGIN + colW - font.width("[Ctrl+V]") - 4, HEADER_H + 6,
                    0xFF55BBFF,
                    false);
        ChroniclesUIKit.drawString(g, font, "§8Rewards  §7" + rewards.size(), splitX + 4, HEADER_H + 6, C_TEXT_FAINT,
                false);
        if (copiedRewardNBT != null)
            ChroniclesUIKit.drawString(g, font, "§b[Ctrl+V]", splitX + colW - font.width("[Ctrl+V]") - 4,
                    HEADER_H + 6, 0xFF55BBFF, false);

        g.fill(splitX - COL_GAP / 2, HEADER_H, splitX - COL_GAP / 2 + 1, vh - FOOTER_H, C_SPLIT);

        int formPanelTop = formTop - 20;
        g.fill(0, formPanelTop, vw, formBottom, C_PANEL);
        g.fill(0, formPanelTop, vw, formPanelTop + 1, C_BORDER);

        g.fill(MARGIN, formPanelTop + 2, MARGIN + colW, formBottom - 2, C_FORM_BG);
        drawBorder(g, MARGIN, formPanelTop + 2, colW, formBottom - 2 - (formPanelTop + 2), C_BORDER);
        g.fill(splitX, formPanelTop + 2, splitX + colW, formBottom - 2, C_FORM_BG);
        drawBorder(g, splitX, formPanelTop + 2, colW, formBottom - 2 - (formPanelTop + 2), C_BORDER);
        ChroniclesUIKit.drawString(g, font,
                emergencyMode ? "§8Emergency kit" :
                        editingTaskIndex >= 0 ? "§b✎ Editing Task (right-click to cancel)" : "§8Add Task",
                MARGIN + 6, formPanelTop + 6, C_TEXT_FAINT, false);
        if (emergencyMode) {
            int helpY = formPanelTop + 20;
            for (String line : font.getSplitter().splitLines(
                    "Players claim these from the emergency screen while the quest is active - " +
                            "use it for a lost required item or a stuck quest. Any reward type except Choice Box " +
                            "works. How often it can be claimed is set on the previous screen.",
                    colW - 14, net.minecraft.network.chat.Style.EMPTY).stream().map(t -> t.getString()).toList()) {
                ChroniclesUIKit.drawString(g, font, "§8" + line, MARGIN + 6, helpY, C_TEXT_FAINT, false);
                helpY += 10;
            }
        }
        ChroniclesUIKit.drawString(g, font,
                editingRewardIndex >= 0 ? "§b✎ Editing Reward (right-click to cancel)" : "§8Add Reward",
                splitX + 6, formPanelTop + 6, C_TEXT_FAINT, false);

        if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0) renderBoxOptionsList(g, mx, my);

        g.fill(0, vh - FOOTER_H, vw, vh, C_HEADER);
        g.fill(0, vh - FOOTER_H, vw, vh - FOOTER_H + 1, C_BORDER);

        hoveredTaskRow = -1;
        int listH = Math.max(0, listBottom - listTop);
        int taskTotalH = tasks.size() * ROW_H;
        taskListScroll = Math.max(0, Math.min(taskListScroll, Math.max(0, taskTotalH - listH)));

        g.enableScissor(Math.round(MARGIN * uiScale), Math.round(listTop * uiScale),
                Math.round((splitX - COL_GAP) * uiScale), Math.round(listBottom * uiScale));
        int ty = listTop - taskListScroll;
        for (int i = 0; i < tasks.size(); i++) {
            QuestTask task = tasks.get(i);
            if (ty + ROW_H <= listTop || ty >= listBottom) {
                ty += ROW_H;
                continue;
            }
            boolean hov = mx >= MARGIN && mx < splitX - COL_GAP && my >= Math.max(ty, listTop) &&
                    my < Math.min(ty + ROW_H, listBottom);
            if (hov) {
                g.fill(MARGIN, ty, splitX - COL_GAP, ty + ROW_H, C_ROW_HOVER);
                hoveredTaskRow = i;
            }
            if (draggingTaskIndex == i) drawBorder(g, MARGIN, ty, splitX - COL_GAP - MARGIN, ROW_H, 0xFF55DD55);

            g.fill(MARGIN, ty + 2, MARGIN + 2, ty + ROW_H - 2,
                    task.isOptional() ? 0xFF22AA55 : C_ACCENT);
            PhoenixTaskRegistry.TaskEntry meta = getTaskMetaByClass(task);
            ItemStack taskIcon = getTaskIconStack(task);
            int textX = MARGIN + 5;
            if (!taskIcon.isEmpty()) {
                g.renderItem(taskIcon, textX, ty + 4);
                textX += 18;
            } else if (meta != null && meta.editorIcon() != null) {
                ChroniclesUIKit.drawString(g, font, meta.editorIcon(), textX, ty + 9, 0xFFFFFFFF, false);
                textX += 10;
            }
            int maxW = (splitX - COL_GAP) - textX - (hov ? 34 : 6);
            String rawLabel = task.getDescription().getString();
            if (task.isOptional()) rawLabel = "[opt] " + rawLabel;
            String detail = getTaskDetailString(task);
            String[] wrapped = wordWrap(rawLabel, maxW);
            String line1Color = task.isOptional() ? "§8" : "§7";
            ChroniclesUIKit.drawString(g, font, line1Color + wrapped[0], textX, ty + 4, C_TEXT_DIM, false);
            if (wrapped[1] != null) {

                ChroniclesUIKit.drawString(g, font, "§8" + wrapped[1], textX, ty + 15, C_TEXT_FAINT, false);
            } else if (detail != null) {
                String dl = detail;
                if (font.width(dl) > maxW) dl = font.plainSubstrByWidth(dl, maxW - 4) + "…";
                ChroniclesUIKit.drawString(g, font, "§8" + dl, textX, ty + 15, C_TEXT_FAINT, false);
            }
            if (hov) {
                ChroniclesUIKit.drawString(g, font, "§b⧉", splitX - COL_GAP - 26, ty + 9, 0xFF55BBFF, false);
                ChroniclesUIKit.drawString(g, font, "§c×", splitX - COL_GAP - 12, ty + 9, 0xFFFF5555, false);
            }
            ty += ROW_H;
        }
        g.disableScissor();
        ChroniclesThemeRenderer.drawScrollbar(g, splitX - COL_GAP, listTop, listBottom, taskListScroll, taskTotalH);
        if (tasks.isEmpty())
            ChroniclesUIKit.drawString(g, font, "§8No tasks yet: add one below.", MARGIN + 6, listTop + 5, C_TEXT_FAINT,
                    false);

        hoveredRewardRow = -1;
        rewardDisplayOrder = computeRewardDisplayOrder();
        int tableSectionStart = rewardTableSectionStart(rewardDisplayOrder);
        int rewardTotalH = rewardListContentH(rewardDisplayOrder, tableSectionStart);
        rewardListScroll = Math.max(0, Math.min(rewardListScroll, Math.max(0, rewardTotalH - listH)));
        g.enableScissor(Math.round(splitX * uiScale), Math.round(listTop * uiScale),
                Math.round((vw - MARGIN) * uiScale), Math.round(listBottom * uiScale));
        int ry = listTop - rewardListScroll;
        for (int pos = 0; pos < rewardDisplayOrder.size(); pos++) {
            if (pos == tableSectionStart) {
                if (ry + ROW_HEADER_H > listTop && ry < listBottom) {
                    ChroniclesUIKit.drawString(g, font, "§6⊞ §8Reward Tables", splitX + 5,
                            ry + (ROW_HEADER_H / 2) - 4, C_TEXT_FAINT, false);
                    g.fill(splitX, ry + ROW_HEADER_H - 1, vw - MARGIN, ry + ROW_HEADER_H, C_BORDER);
                }
                ry += ROW_HEADER_H;
            }
            int i = rewardDisplayOrder.get(pos);
            QuestReward reward = rewards.get(i);
            if (ry + ROW_H <= listTop || ry >= listBottom) {
                ry += ROW_H;
                continue;
            }
            boolean hov = mx >= splitX && mx < vw - MARGIN && my >= Math.max(ry, listTop) &&
                    my < Math.min(ry + ROW_H, listBottom);
            if (hov) {
                g.fill(splitX, ry, vw - MARGIN, ry + ROW_H, C_ROW_HOVER);
                hoveredRewardRow = i;
            }
            if (draggingRewardIndex == i) drawBorder(g, splitX, ry, vw - MARGIN - splitX, ROW_H, 0xFF55DD55);

            g.fill(splitX, ry + 2, splitX + 2, ry + ROW_H - 2, C_ACCENT);
            int rewardTextX = splitX + 5;
            if (reward instanceof QuestReward.ItemReward ir) {
                ItemStack stack = new ItemStack(ir.getItem(), ir.getCount());
                g.renderItem(stack, rewardTextX, ry + 4);
                rewardTextX += 18;
                int rmaxW = (vw - MARGIN - (hov ? 16 : 6)) - rewardTextX;
                String rl = "§f" + stack.getHoverName().getString();
                if (font.width(rl) > rmaxW) rl = font.plainSubstrByWidth(rl, rmaxW - 4) + "…";
                ChroniclesUIKit.drawString(g, font, rl, rewardTextX, ry + 4, C_TEXT_DIM, false);
                ChroniclesUIKit.drawString(g, font, "§8×" + ir.getCount(), rewardTextX, ry + 15, C_TEXT_FAINT, false);
            } else {
                String icon = switch (reward.getType()) {
                    case XP -> "§a✦";
                    case COMMAND -> "§b◆";
                    case LOOT_TABLE -> "§d❋";
                    case SCRIPT_EVENT -> "§e⚡";
                    case REWARD_TABLE -> "§6⊞";
                    case FLUID -> "§3💧";
                    case QUEST_ACTION -> "§b✔";
                    case EXTERNAL -> "§5⌘";
                    default -> "§8?";
                };
                String typeLine = switch (reward.getType()) {
                    case XP -> "§8XP";
                    case COMMAND -> "§8command";
                    case LOOT_TABLE -> "§8loot table";
                    case SCRIPT_EVENT -> "§8script event";
                    case REWARD_TABLE -> "§8reward table";
                    case FLUID -> "§8fluid";
                    case QUEST_ACTION -> "§8quest action";
                    case EXTERNAL -> "§8external";
                    default -> "§8reward";
                };
                int rmaxW = (vw - MARGIN - (hov ? 16 : 6)) - rewardTextX - font.width(icon) - 4;
                String rl = reward.getSummary().getString();
                String[] rwrapped = wordWrap(rl, rmaxW);
                ChroniclesUIKit.drawString(g, font, icon + " §7" + rwrapped[0], rewardTextX, ry + 4, C_TEXT_DIM, false);
                ChroniclesUIKit.drawString(g, font, rwrapped[1] != null ? "§8" + rwrapped[1] : typeLine,
                        rewardTextX, ry + 15, C_TEXT_FAINT, false);
            }
            if (hov) ChroniclesUIKit.drawString(g, font, "§c×", vw - MARGIN - 12, ry + 9, 0xFFFF5555, false);
            ry += ROW_H;
        }
        g.disableScissor();
        ChroniclesThemeRenderer.drawScrollbar(g, vw - MARGIN, listTop, listBottom, rewardListScroll, rewardTotalH);
        if (rewards.isEmpty())
            ChroniclesUIKit.drawString(g, font, "§8No rewards yet: add one below.", splitX + 6, listTop + 5,
                    C_TEXT_FAINT, false);

        super.render(g, mx, my, partial);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.flush();

        if (taskTypeDropOpen) {
            List<PhoenixTaskRegistry.TaskEntry> editorTypes = PhoenixTaskRegistry.getEditorTypes();
            int rowH = FIELD_H;
            int totalH = editorTypes.size() * rowH;
            int maxDropH = Math.max(rowH, (formTop - 2) - listTop);
            int dropH = Math.min(totalH, maxDropH);
            int dy = Math.max(listTop, formTop - dropH - 2);
            int maxScroll = Math.max(0, totalH - dropH);
            taskTypeDropScroll = Math.max(0, Math.min(taskTypeDropScroll, maxScroll));

            g.fill(MARGIN, dy, MARGIN + colW, dy + dropH, C_PANEL);
            drawBorder(g, MARGIN, dy, colW, dropH, C_ACCENT);

            hoveredDropRow = -1;

            g.enableScissor(Math.round(MARGIN * uiScale), Math.round(dy * uiScale),
                    Math.round((MARGIN + colW) * uiScale), Math.round((dy + dropH) * uiScale));
            for (int i = 0; i < editorTypes.size(); i++) {
                int dropRowY = dy - taskTypeDropScroll + i * rowH;
                if (dropRowY + rowH <= dy || dropRowY >= dy + dropH) continue;
                PhoenixTaskRegistry.TaskEntry m = editorTypes.get(i);
                boolean hov = mx >= MARGIN && mx < MARGIN + colW && my >= dropRowY && my < dropRowY + rowH &&
                        my >= dy && my < dy + dropH;
                if (hov) {
                    g.fill(MARGIN + 1, dropRowY, MARGIN + colW - 1, dropRowY + rowH, 0xFF1E1E2A);
                    hoveredDropRow = i;
                }
                ChroniclesUIKit.drawString(g, font, m.editorIcon() + " §7" + m.editorLabel(), MARGIN + 5, dropRowY + 3,
                        hov ? C_TEXT : C_TEXT_DIM, false);
            }
            g.disableScissor();
            ChroniclesThemeRenderer.drawScrollbar(g, MARGIN + colW, dy, dy + dropH, taskTypeDropScroll, totalH);

            if (hoveredDropRow >= 0 && hoveredDropRow < editorTypes.size()) {
                PhoenixTaskRegistry.TaskEntry hm = editorTypes.get(hoveredDropRow);
                String tooltip = hm.editorTooltip() != null ? hm.editorTooltip() : hm.editorLabel();
                int maxTipTextW = 220;
                String[] rawLines = tooltip.split("\n");
                List<net.minecraft.util.FormattedCharSequence> wrappedLines = new ArrayList<>();
                for (int rli = 0; rli < rawLines.length; rli++) {
                    Component lineComp = ChroniclesUIKit.lit((rli == 0 ? "§f" : "§8") + rawLines[rli]);
                    wrappedLines.addAll(font.split(lineComp, maxTipTextW));
                }
                int maxLw = 0;
                for (var l : wrappedLines) maxLw = Math.max(maxLw, font.width(l));
                int tipW = maxLw + 10, tipH = wrappedLines.size() * 10 + 6;
                int tipX = MARGIN + colW + 4;
                int dropRowY = dy - taskTypeDropScroll + hoveredDropRow * rowH;
                int tipY = Math.min(Math.max(dropRowY, 2), vh - tipH - 2);
                if (tipX + tipW > vw - 2) tipX = Math.max(2, vw - 2 - tipW);
                g.fill(tipX, tipY, tipX + tipW, tipY + tipH, C_TOOLTIP_BG);
                drawBorder(g, tipX, tipY, tipW, tipH, C_ACCENT);
                for (int li = 0; li < wrappedLines.size(); li++)
                    ChroniclesUIKit.drawString(g, font, wrappedLines.get(li), tipX + 5, tipY + 3 + li * 10, 0xFFFFFFFF,
                            false);
            }
        }

        if (rewardTypeDropOpen) {
            int rowH = FIELD_H;
            int dropH = rewardTypes().length * rowH;
            int dy = Math.max(listTop, formTop - dropH - 2);
            g.fill(splitX, dy, splitX + colW, dy + dropH, C_PANEL);
            drawBorder(g, splitX, dy, colW, dropH, C_ACCENT);
            for (int i = 0; i < rewardTypes().length; i++) {
                int dropRowY = dy + i * rowH;
                boolean hov = mx >= splitX && mx < splitX + colW && my >= dropRowY && my < dropRowY + rowH;
                if (hov) g.fill(splitX + 1, dropRowY, splitX + colW - 1, dropRowY + rowH, 0xFF1E1E2A);
                ChroniclesUIKit.drawString(g, font, "§7" + RewardFormTypes.get(rewardTypes()[i]).label(), splitX + 5,
                        dropRowY + 3,
                        hov ? C_TEXT : C_TEXT_DIM, false);
            }
        }

        g.pose().popPose();
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = (mods & 2) != 0;
        boolean shift = (mods & 1) != 0;
        if (ctrl && key == 90 && !shift) {
            undoLastChange();
            return true;
        }
        if (ctrl && (key == 89 || (key == 90 && shift))) {
            redoLastChange();
            return true;
        }
        if (ctrl && key == 67) {
            if (hoveredRewardRow >= 0 && hoveredRewardRow < rewards.size()) {
                copiedRewardNBT = rewards.get(hoveredRewardRow).serializeWithMeta();
                return true;
            }
            if (hoveredTaskRow >= 0 && hoveredTaskRow < tasks.size()) {
                copiedTaskNBT = tasks.get(hoveredTaskRow).serializeWithMeta();
                return true;
            }
        }
        boolean pasteReward = copiedRewardNBT != null && (hoveredRewardRow >= 0 || copiedTaskNBT == null);
        if (ctrl && key == 86 && pasteReward) {
            QuestReward pastedReward = QuestReward.deserializeNBT(copiedRewardNBT.copy());
            if (pastedReward != null) {
                pushUndo(() -> {
                    rewards.add(pastedReward);
                    rewardListScroll = Integer.MAX_VALUE;
                });
            }
            return true;
        }
        if (ctrl && key == 86 && copiedTaskNBT != null) {
            QuestTask pasted = deserializeTask(copiedTaskNBT.copy());
            if (pasted != null) {
                pasted = retaskId(pasted, "task_paste_" + java.util.UUID.randomUUID().toString().replace("-", ""));
                tasks.add(pasted);
                taskListScroll = Integer.MAX_VALUE;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double rawMx, double rawMy, double delta) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (taskTypeDropOpen) {
            List<PhoenixTaskRegistry.TaskEntry> edTypes = PhoenixTaskRegistry.getEditorTypes();
            int totalH = edTypes.size() * FIELD_H;
            int maxDropH = Math.max(FIELD_H, (formTop - 2) - listTop);
            int dropH = Math.min(totalH, maxDropH);
            int maxScroll = Math.max(0, totalH - dropH);
            taskTypeDropScroll = Math.max(0, Math.min(maxScroll, (int) (taskTypeDropScroll - delta * FIELD_H)));
            return true;
        }
        if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0 && mx >= boxOptionsListX &&
                mx < boxOptionsListX + boxOptionsListW && my >= boxOptionsListY && my < boxOptionsListBottom) {
            int visibleRows = Math.max(1, (boxOptionsListBottom - boxOptionsListY) / BOX_OPTION_ROW_H);
            int maxScroll = Math.max(0, boxOptions.size() - visibleRows);
            boxOptionsScroll = Math.max(0, Math.min(maxScroll, boxOptionsScroll - (int) Math.signum(delta)));
            return true;
        }
        if (!rewardTypeDropOpen && my >= listTop && my < listBottom) {
            int listH = Math.max(0, listBottom - listTop);
            int step = ROW_H;
            if (mx >= MARGIN && mx < splitX - COL_GAP) {
                int maxScroll = Math.max(0, tasks.size() * ROW_H - listH);
                taskListScroll = Math.max(0, Math.min(maxScroll, (int) (taskListScroll - Math.signum(delta) * step)));
                return true;
            }
            if (mx >= splitX && mx < vw - MARGIN) {
                int total = rewardListContentH(rewardDisplayOrder, rewardTableSectionStart(rewardDisplayOrder));
                int maxScroll = Math.max(0, total - listH);
                rewardListScroll = Math.max(0, Math.min(maxScroll,
                        (int) (rewardListScroll - Math.signum(delta) * step)));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double rawMx, double rawMy, int btn) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (btn == 0) {
            if (taskTypeDropOpen) {
                List<PhoenixTaskRegistry.TaskEntry> edTypes = PhoenixTaskRegistry.getEditorTypes();
                int totalH = edTypes.size() * FIELD_H;
                int maxDropH = Math.max(FIELD_H, (formTop - 2) - listTop);
                int dropH = Math.min(totalH, maxDropH);
                int dy = Math.max(listTop, formTop - dropH - 2);
                if (my >= dy && my < dy + dropH) {
                    for (int i = 0; i < edTypes.size(); i++) {
                        int ry2 = dy - taskTypeDropScroll + i * FIELD_H;
                        if (ry2 + FIELD_H <= dy || ry2 >= dy + dropH) continue;
                        if (mx >= MARGIN && mx < MARGIN + colW && my >= ry2 && my < ry2 + FIELD_H) {
                            taskType = edTypes.get(i).typeId();
                            pendingPickedItemFilter = null;
                            pendingPickedFluidFilter = null;
                            taskTypeDropOpen = false;
                            rebuildWidgets();
                            return true;
                        }
                    }
                }
                taskTypeDropOpen = false;
                return true;
            }
            if (rewardTypeDropOpen) {
                int dropH = rewardTypes().length * FIELD_H;
                int dy = Math.max(listTop, formTop - dropH - 2);
                for (int i = 0; i < rewardTypes().length; i++) {
                    int ry2 = dy + i * FIELD_H;
                    if (mx >= splitX && mx < splitX + colW && my >= ry2 && my < ry2 + FIELD_H) {
                        rewardType = rewardTypes()[i];
                        rewardTypeDropOpen = false;
                        rebuildWidgets();
                        return true;
                    }
                }
                rewardTypeDropOpen = false;
                return true;
            }
            if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0) {
                for (int i = 0; i < boxOptionRowRects.size(); i++) {
                    int[] r = boxOptionRowRects.get(i);
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        boxOptions.remove(boxOptionsScroll + i);
                        rebuildWidgets();
                        return true;
                    }
                }
            }

            if (hoveredTaskRow >= 0 && mx >= splitX - COL_GAP - 28 && mx < splitX - COL_GAP - 14) {
                copiedTaskNBT = tasks.get(hoveredTaskRow).serializeWithMeta();
                return true;
            }

            if (hoveredTaskRow >= 0 && mx >= splitX - COL_GAP - 14 && mx < splitX - COL_GAP) {
                int removeIdx = hoveredTaskRow;
                pushUndo(() -> tasks.remove(removeIdx));
                hoveredTaskRow = -1;
                if (editingTaskIndex >= 0) cancelTaskEdit();
                return true;
            }

            if (hoveredRewardRow >= 0 && mx >= vw - MARGIN - 14 && mx < vw - MARGIN) {
                int removeIdx = hoveredRewardRow;
                pushUndo(() -> rewards.remove(removeIdx));
                hoveredRewardRow = -1;
                if (editingRewardIndex >= 0) cancelRewardEdit();
                return true;
            }

            if (hoveredTaskRow >= 0) {
                draggingTaskIndex = hoveredTaskRow;
                dragMovedTask = false;
                return true;
            }
            if (hoveredRewardRow >= 0) {
                draggingRewardIndex = hoveredRewardRow;
                dragMovedReward = false;
                return true;
            }
        } else if (btn == 1) {

            if (hoveredTaskRow >= 0) {
                if (editingTaskIndex == hoveredTaskRow) cancelTaskEdit();
                else startEditingTask(hoveredTaskRow);
                return true;
            }
            if (hoveredRewardRow >= 0) {
                if (editingRewardIndex == hoveredRewardRow) cancelRewardEdit();
                else startEditingReward(hoveredRewardRow);
                return true;
            }
            if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0) {
                for (int i = 0; i < boxOptionRowRects.size(); i++) {
                    int[] r = boxOptionRowRects.get(i);
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        startEditingBoxOption(boxOptionsScroll + i);
                        return true;
                    }
                }
            } else if (rewardType.equals("choice_box")) {
                cancelBoxOptionEdit();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private int rowIndexAtY(double my, int count) {
        if (my < listTop || my >= listBottom) return -1;
        int idx = (int) ((my - listTop + taskListScroll) / ROW_H);
        return idx >= 0 && idx < count ? idx : -1;
    }

    private int rewardListContentH(List<Integer> order, int tableSectionStart) {
        return order.size() * ROW_H + (tableSectionStart < order.size() ? ROW_HEADER_H : 0);
    }

    private int dragAutoScroll(double my, int scroll, int totalH) {
        int listH = Math.max(0, listBottom - listTop);
        int maxScroll = Math.max(0, totalH - listH);
        if (my < listTop + 6) return Math.max(0, scroll - 6);
        if (my >= listBottom - 6) return Math.min(maxScroll, scroll + 6);
        return scroll;
    }

    private List<Integer> computeRewardDisplayOrder() {
        List<Integer> order = new ArrayList<>(rewards.size());
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i).getType() != QuestReward.RewardType.REWARD_TABLE) order.add(i);
        }
        for (int i = 0; i < rewards.size(); i++) {
            if (rewards.get(i).getType() == QuestReward.RewardType.REWARD_TABLE) order.add(i);
        }
        return order;
    }

    private int rewardTableSectionStart(List<Integer> order) {
        for (int pos = 0; pos < order.size(); pos++) {
            if (rewards.get(order.get(pos)).getType() == QuestReward.RewardType.REWARD_TABLE) return pos;
        }
        return order.size();
    }

    private int rewardRealIndexAtY(double my) {
        if (my < listTop || my >= listBottom || rewardDisplayOrder.isEmpty()) return -1;
        int tableSectionStart = rewardTableSectionStart(rewardDisplayOrder);
        int y = listTop - rewardListScroll;
        for (int pos = 0; pos < rewardDisplayOrder.size(); pos++) {
            if (pos == tableSectionStart) y += ROW_HEADER_H;
            if (my >= y && my < y + ROW_H) return rewardDisplayOrder.get(pos);
            y += ROW_H;
        }
        return -1;
    }

    @Override
    public boolean mouseDragged(double rawMx, double rawMy, int btn, double rawDx, double rawDy) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        double dx = rawDx / uiScale;
        double dy = rawDy / uiScale;
        if (btn == 0 && draggingTaskIndex >= 0) {
            taskListScroll = dragAutoScroll(my, taskListScroll, tasks.size() * ROW_H);
            int target = rowIndexAtY(my, tasks.size());
            if (target >= 0 && target != draggingTaskIndex) {
                if (!dragMovedTask) {
                    beginDragUndo();
                    dragMovedTask = true;
                }
                Collections.swap(tasks, draggingTaskIndex, target);
                if (editingTaskIndex == draggingTaskIndex) editingTaskIndex = target;
                else if (editingTaskIndex == target) editingTaskIndex = draggingTaskIndex;
                draggingTaskIndex = target;
            }
            return true;
        }
        if (btn == 0 && draggingRewardIndex >= 0) {
            rewardListScroll = dragAutoScroll(my, rewardListScroll,
                    rewardListContentH(rewardDisplayOrder, rewardTableSectionStart(rewardDisplayOrder)));
            int target = rewardRealIndexAtY(my);
            if (target >= 0 && target != draggingRewardIndex) {
                if (!dragMovedReward) {
                    beginDragUndo();
                    dragMovedReward = true;
                }
                Collections.swap(rewards, draggingRewardIndex, target);
                if (editingRewardIndex == draggingRewardIndex) editingRewardIndex = target;
                else if (editingRewardIndex == target) editingRewardIndex = draggingRewardIndex;
                draggingRewardIndex = target;
            }
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double rawMx, double rawMy, int btn) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (btn == 0 && (draggingTaskIndex >= 0 || draggingRewardIndex >= 0)) {
            if (dragMovedTask || dragMovedReward) finishDragUndo();
            draggingTaskIndex = -1;
            draggingRewardIndex = -1;
            dragMovedTask = false;
            dragMovedReward = false;
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public void onClose() {
        flushToQuestNode();
        LangSyncScheduler.flushNow();
        if (!emergencyMode) ChronicleOverviewScreen.invalidateNodeCachesUpChain(parent, questNode);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Nullable
    private static QuestTask deserializeTask(CompoundTag nbt) {
        QuestTask t = PhoenixTaskRegistry.deserialize(nbt);
        if (t != null) t.setOptional(nbt.getBoolean("optional"));
        return t;
    }

    private static QuestTask retaskId(QuestTask task, String newId) {
        CompoundTag nbt = task.serializeWithMeta();
        nbt.putString("task_id", "phoenix_chronicles:" + newId);
        QuestTask copy = deserializeTask(nbt);
        return copy != null ? copy : task;
    }

    private PhoenixTaskRegistry.TaskEntry getTaskMeta(String typeId) {
        PhoenixTaskRegistry.TaskEntry e = PhoenixTaskRegistry.get(typeId);
        List<PhoenixTaskRegistry.TaskEntry> all = PhoenixTaskRegistry.getEditorTypes();
        return e != null ? e : (all.isEmpty() ? null : all.get(0));
    }

    private PhoenixTaskRegistry.TaskEntry getTaskMetaByClass(QuestTask task) {
        try {
            String typeId = task.serializeNBT().getString("type");
            return getTaskMeta(typeId);
        } catch (Exception ignored) {}
        List<PhoenixTaskRegistry.TaskEntry> all = PhoenixTaskRegistry.getEditorTypes();
        return all.isEmpty() ? null : all.get(0);
    }

    private ItemStack getTaskIconStack(QuestTask task) {
        ResourceLocation id = task.getDisplayItemId();
        if (id == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);

        if (task instanceof ItemRequirementTask t && t.getNbtFilter() != null && !t.getNbtFilter().isEmpty()) {
            stack.setTag(t.getNbtFilter().copy());
        }
        return stack;
    }

    @Nullable
    private String getTaskDetailString(QuestTask task) {
        return TaskFormTypes.detail(task);
    }

    private String[] wordWrap(String text, int maxW) {
        if (font.width(text) <= maxW) return new String[] { text, null };
        String sub = font.plainSubstrByWidth(text, maxW);
        int lastSpace = sub.lastIndexOf(' ');
        String line1 = lastSpace > 0 ? sub.substring(0, lastSpace) : sub;
        String rest = text.substring(line1.length()).trim();
        if (rest.isEmpty()) return new String[] { line1, null };
        if (font.width(rest) > maxW) rest = font.plainSubstrByWidth(rest, maxW - 4) + "…";
        return new String[] { line1, rest };
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        ChroniclesUIKit.drawBorder(g, x, y, w, h, color);
    }

    private void renderBoxOptionsList(GuiGraphics g, int mx, int my) {
        boxOptionRowRects.clear();
        if (boxOptions.isEmpty()) {
            ChroniclesUIKit.drawString(g, font, "§8No options yet - use §7+ Item §8or §7+ Table", boxOptionsListX,
                    boxOptionsListY,
                    C_TEXT_FAINT,
                    false);
            return;
        }

        int visibleRows = Math.max(1, (boxOptionsListBottom - boxOptionsListY) / BOX_OPTION_ROW_H);

        int ry = boxOptionsListY;
        int end = Math.min(boxOptions.size(), boxOptionsScroll + visibleRows);
        int hoveredRowY = -1;
        for (int i = boxOptionsScroll; i < end; i++) {
            QuestReward opt = boxOptions.get(i);
            boolean hov = mx >= boxOptionsListX && mx < boxOptionsListX + boxOptionsListW &&
                    my >= ry && my < ry + BOX_OPTION_ROW_H;
            if (hov) {
                g.fill(boxOptionsListX, ry, boxOptionsListX + boxOptionsListW, ry + BOX_OPTION_ROW_H,
                        C_ROW_HOVER);
                hoveredRowY = ry;
            }

            String label = opt.getSummary().getString();
            int maxW = boxOptionsListW - 12;
            if (font.width(label) > maxW) label = font.plainSubstrByWidth(label, Math.max(0, maxW - 6)) + "…";
            ChroniclesUIKit.drawString(g, font, "§7" + label, boxOptionsListX + 1, ry + 3, C_TEXT_DIM, false);
            ChroniclesUIKit.drawString(g, font, "§c✕", boxOptionsListX + boxOptionsListW - 9, ry + 3, 0xFFFF5555,
                    false);

            boxOptionRowRects.add(new int[] { boxOptionsListX, ry, boxOptionsListW, BOX_OPTION_ROW_H });
            ry += BOX_OPTION_ROW_H;
        }

        int maxScroll = Math.max(0, boxOptions.size() - visibleRows);
        if (maxScroll > 0) {
            if (boxOptionsScroll > 0)
                ChroniclesUIKit.drawString(g, font, "§8▲", boxOptionsListX + boxOptionsListW - 9, boxOptionsListY - 8,
                        C_TEXT_FAINT,
                        false);
            if (boxOptionsScroll < maxScroll)
                ChroniclesUIKit.drawString(g, font, "§8▼", boxOptionsListX + boxOptionsListW - 9,
                        boxOptionsListBottom + 1,
                        C_TEXT_FAINT, false);
        }

        if (hoveredRowY >= 0) {
            String tip = "Right-click to edit";
            int tipW = font.width(tip) + 8;
            int tipH = 14;
            int tipX = Math.min(mx + 10, boxOptionsListX + boxOptionsListW - tipW);
            tipX = Math.max(tipX, 0);
            int tipY = my + 12;
            g.fill(tipX, tipY, tipX + tipW, tipY + tipH, C_TOOLTIP_BG);
            drawBorder(g, tipX, tipY, tipW, tipH, C_ACCENT);
            ChroniclesUIKit.drawString(g, font, "§f" + tip, tipX + 4, tipY + 3, 0xFFFFFFFF, false);
        }
    }
}

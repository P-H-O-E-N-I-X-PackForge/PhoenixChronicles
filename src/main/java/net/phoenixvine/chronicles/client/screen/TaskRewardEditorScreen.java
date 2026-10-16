package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.registry.LangSyncScheduler;
import net.phoenixvine.chronicles.client.render.ChroniclesThemeRenderer;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.screen.rewardform.RewardFormTypes;
import net.phoenixvine.chronicles.client.screen.utils.UndoRedoManager;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.filter.IFluidFilter;
import net.phoenixvine.chronicles.common.filter.IItemFilter;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.registry.RewardTableRegistry;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class TaskRewardEditorScreen extends Screen {

    final EditorRewardForm rewardForm = new EditorRewardForm(this);

    final EditorTaskForm taskForm = new EditorTaskForm(this);

    <T extends net.minecraft.client.gui.components.events.GuiEventListener & net.minecraft.client.gui.components.Renderable & net.minecraft.client.gui.narration.NarratableEntry> T addWidgetBridge(T widget) {
        return addRenderableWidget(widget);
    }

    void removeWidgetBridge(net.minecraft.client.gui.components.events.GuiEventListener widget) {
        removeWidget(widget);
    }

    int C_BG, C_PANEL, C_HEADER, C_BORDER, C_ACCENT, C_TEXT, C_TEXT_DIM, C_TEXT_FAINT, C_OK;
    static final int C_ROW_HOVER = 0x22FFFFFF;
    private static final int C_FORM_BG = 0x33000000;
    private static final int C_SPLIT = 0xFF2A2A3A;
    static final int C_TOOLTIP_BG = 0xFF0E0E16;

    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 28;
    static final int MARGIN = 10;
    private static final int COL_GAP = 6;
    private static final int ROW_H = 26;
    static final int FIELD_H = 15;
    static final int FIELD_GAP = 5;

    private static final int FORM_ROWS = 5;

    private static final int MIN_W = 560;
    private static final int MIN_H = 380;
    private float uiScale = 1f;
    private int vw, vh;

    int splitX;
    int colW;
    private int listTop;
    private int listBottom;
    int formTop;
    int formBottom;

    private int lastLayoutWidth = -1;
    private int lastLayoutHeight = -1;

    private final Screen parent;

    Screen getParentScreen() {
        return parent;
    }

    @Nullable
    final QuestNode questNode;

    final boolean emergencyMode;
    @Nullable
    private final Consumer<List<QuestReward>> emergencySink;
    private final String emergencyLabel;

    @Nullable
    private final QuestNode.QuestVariant variantTarget;

    final List<QuestTask> tasks = new ArrayList<>();
    final List<QuestReward> rewards = new ArrayList<>();

    String taskType = "item_check";

    boolean taskTypeDropOpen = false;
    int taskTypeDropScroll = 0;
    EditBox taskDescBox, taskTargetBox, taskCountBox, taskSecondaryBox, taskNbtBox;

    int editingTaskIndex = -1;
    int editingRewardIndex = -1;

    IItemFilter pendingPickedItemFilter = null;
    IFluidFilter pendingPickedFluidFilter = null;

    boolean forcePendingTaskValues = false;
    String pendingTaskDesc = "", pendingTaskTarget = "", pendingTaskSecondary = "", pendingTaskCount = "",
            pendingTaskNbt = "";
    boolean forcePendingRewardValues = false;
    String pendingRewardCount = "", pendingRewardCommand = "", pendingRewardEventData = "";

    String rewardType = "item";
    boolean rewardTypeDropOpen = false;
    EditBox rewardCountBox, rewardCommandBox;

    final List<QuestReward> boxOptions = new ArrayList<>();
    static final int BOX_OPTION_ROW_H = 14;
    static final int CHOICE_ADD_ITEM_W = 44;
    static final int CHOICE_ADD_TABLE_W = 48;
    int boxOptionsListX, boxOptionsListY, boxOptionsListW, boxOptionsListBottom;
    int boxOptionsScroll = 0;
    final List<int[]> boxOptionRowRects = new ArrayList<>();

    int editingBoxOptionIndex = -1;
    EditBox boxOptionCountBox, boxOptionNbtBox;
    boolean forcePendingBoxOptionValues = false;
    String pendingBoxOptionCount = "1", pendingBoxOptionNbt = "", pendingBoxOptionTable = "";
    EditBox boxOptionTableBox;

    private int hoveredTaskRow = -1;
    private int hoveredRewardRow = -1;
    int taskListScroll = 0;
    int rewardListScroll = 0;
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

    EditBox rewardEventDataBox;

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

    void setHintFitted(EditBox box, String hint) {
        String plain = hint.replaceAll("§.", "");
        int maxW = Math.max(10, box.getWidth() - 10);
        boolean cut = font.width(plain) > maxW;
        String shown = cut ? font.plainSubstrByWidth(plain, Math.max(0, maxW - font.width("…"))) + "…" : plain;
        box.setHint(ChroniclesUIKit.lit("§8" + shown));
        if (cut) box.setTooltip(Tooltip.create(ChroniclesUIKit.lit(plain)));
    }

    void setTableIdHint(EditBox box) {
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

        taskForm.buildTaskForm(descVal, targetVal, secondVal, countVal, nbtVal);
        rewardForm.buildRewardForm(rCountVal, rCommandVal, rEventDataVal, boCountVal, boNbtVal, boTableVal);
    }

    static final String HIDE_TOOLTIP = "Hide parts of this entry in the quest viewer until the %s.\n" +
            "Click to cycle: off > amount > target > amount + target.\n" +
            "Example: show that iron is needed but not how much.";

    static net.minecraft.network.chat.Component hideLabel(int parts) {
        return ChroniclesUIKit.lit(switch (parts) {
            case 1 -> "§dHide: amount";
            case 2 -> "§dHide: target";
            case 3 -> "§dHide: both";
            default -> "§8Hide: off";
        });
    }

    static String permLabel(int level) {
        return switch (level) {
            case 0 -> "0 · everyone";
            case 1 -> "1 · moderator";
            case 2 -> "2 · gamemaster";
            case 3 -> "3 · admin";
            default -> "4 · owner (full)";
        };
    }

    private List<QuestTask> dragBeforeTasks;
    private List<QuestReward> dragBeforeRewards;

    void pushUndo(Runnable mutation) {
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
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
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
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(@NotNull GuiGraphics g, int rawMx, int rawMy, float partial) {
        if (width != lastLayoutWidth || height != lastLayoutHeight) init();

        int mx = Math.round(rawMx / uiScale);
        int my = Math.round(rawMy / uiScale);

        g.pose().pushPose();
        g.pose().scale(uiScale, uiScale, 1f);

        drawBackgroundAndTitle(g);
        drawColumnHeaders(g);
        drawFormPanels(g, mx, my);
        g.fill(0, vh - FOOTER_H, vw, vh, C_HEADER);
        g.fill(0, vh - FOOTER_H, vw, vh - FOOTER_H + 1, C_BORDER);
        drawTaskList(g, mx, my);
        drawRewardList(g, mx, my);

        super.render(g, mx, my, partial);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.flush();

        if (taskTypeDropOpen) drawTaskTypeDropdown(g, mx, my);
        if (rewardTypeDropOpen) drawRewardTypeDropdown(g, mx, my);

        g.pose().popPose();
        g.pose().popPose();
    }

    private void drawBackgroundAndTitle(GuiGraphics g) {
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
    }

    private void drawColumnHeaders(GuiGraphics g) {
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
    }

    private void drawFormPanels(GuiGraphics g, int mx, int my) {
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

        if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0) rewardForm.renderBoxOptionsList(g, mx, my);
    }

    private void drawTaskList(GuiGraphics g, int mx, int my) {
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
            PhoenixTaskRegistry.TaskEntry meta = taskForm.getTaskMetaByClass(task);
            ItemStack taskIcon = taskForm.getTaskIconStack(task);
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
            String detail = taskForm.getTaskDetailString(task);
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
    }

    private void drawRewardList(GuiGraphics g, int mx, int my) {
        int listH = Math.max(0, listBottom - listTop);
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
    }

    private void drawTaskTypeDropdown(GuiGraphics g, int mx, int my) {
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

    private void drawRewardTypeDropdown(GuiGraphics g, int mx, int my) {
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

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        boolean ctrl = hasControlDown();
        boolean shift = hasShiftDown();
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
                QuestTask pastedTask = pasted;
                pushUndo(() -> {
                    tasks.add(pastedTask);
                    taskListScroll = Integer.MAX_VALUE;
                });
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double rawMx, double rawMy, double scrollX, double delta) {
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
        return super.mouseScrolled(mx, my, 0.0, delta);
    }

    @Override
    public boolean mouseClicked(double rawMx, double rawMy, int btn) {
        double mx = rawMx / uiScale;
        double my = rawMy / uiScale;
        if (btn == 0 && handleLeftClick(mx, my)) return true;
        if (btn == 1 && handleRightClick(mx, my)) return true;
        return super.mouseClicked(mx, my, btn);
    }

    private boolean handleLeftClick(double mx, double my) {
        if (taskTypeDropOpen) return clickTaskTypeDropdown(mx, my);
        if (rewardTypeDropOpen) return clickRewardTypeDropdown(mx, my);
        if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0 && removeClickedBoxOption(mx, my)) {
            return true;
        }
        return clickListRowButtons(mx);
    }

    private boolean clickTaskTypeDropdown(double mx, double my) {
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
                    String chosenType = edTypes.get(i).typeId();
                    if (!chosenType.equals(taskType)) taskForm.resetFieldsForTypeChange();
                    taskType = chosenType;
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

    private boolean clickRewardTypeDropdown(double mx, double my) {
        int dropH = rewardTypes().length * FIELD_H;
        int dy = Math.max(listTop, formTop - dropH - 2);
        for (int i = 0; i < rewardTypes().length; i++) {
            int ry2 = dy + i * FIELD_H;
            if (mx >= splitX && mx < splitX + colW && my >= ry2 && my < ry2 + FIELD_H) {
                String chosenType = rewardTypes()[i];
                if (!chosenType.equals(rewardType)) rewardForm.resetFieldsForTypeChange();
                rewardType = chosenType;
                rewardTypeDropOpen = false;
                rebuildWidgets();
                return true;
            }
        }
        rewardTypeDropOpen = false;
        return true;
    }

    private boolean removeClickedBoxOption(double mx, double my) {
        for (int i = 0; i < boxOptionRowRects.size(); i++) {
            int[] r = boxOptionRowRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                boxOptions.remove(boxOptionsScroll + i);
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    private boolean clickListRowButtons(double mx) {
        if (hoveredTaskRow >= 0 && mx >= splitX - COL_GAP - 28 && mx < splitX - COL_GAP - 14) {
            copiedTaskNBT = tasks.get(hoveredTaskRow).serializeWithMeta();
            return true;
        }

        if (hoveredTaskRow >= 0 && mx >= splitX - COL_GAP - 14 && mx < splitX - COL_GAP) {
            int removeIdx = hoveredTaskRow;
            pushUndo(() -> tasks.remove(removeIdx));
            hoveredTaskRow = -1;
            if (editingTaskIndex >= 0) taskForm.cancelTaskEdit();
            return true;
        }

        if (hoveredRewardRow >= 0 && mx >= vw - MARGIN - 14 && mx < vw - MARGIN) {
            int removeIdx = hoveredRewardRow;
            pushUndo(() -> rewards.remove(removeIdx));
            hoveredRewardRow = -1;
            if (editingRewardIndex >= 0) rewardForm.cancelRewardEdit();
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
        return false;
    }

    private boolean handleRightClick(double mx, double my) {
        if (hoveredTaskRow >= 0) {
            if (editingTaskIndex == hoveredTaskRow) taskForm.cancelTaskEdit();
            else taskForm.startEditingTask(hoveredTaskRow);
            return true;
        }
        if (hoveredRewardRow >= 0) {
            if (editingRewardIndex == hoveredRewardRow) rewardForm.cancelRewardEdit();
            else rewardForm.startEditingReward(hoveredRewardRow);
            return true;
        }
        if (rewardType.equals("choice_box") && editingBoxOptionIndex < 0) {
            return editClickedBoxOption(mx, my);
        }
        if (rewardType.equals("choice_box")) {
            rewardForm.cancelBoxOptionEdit();
            return true;
        }
        return false;
    }

    private boolean editClickedBoxOption(double mx, double my) {
        for (int i = 0; i < boxOptionRowRects.size(); i++) {
            int[] r = boxOptionRowRects.get(i);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                rewardForm.startEditingBoxOption(boxOptionsScroll + i);
                return true;
            }
        }
        return false;
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

    void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        ChroniclesUIKit.drawBorder(g, x, y, w, h, color);
    }
}

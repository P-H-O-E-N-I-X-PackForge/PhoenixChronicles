package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.client.event.ChronicleKeyBindings;
import net.phoenixvine.chronicles.client.registry.LangSyncScheduler;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.network.packet.C2SAcknowledgeInfoTasksPacket;
import net.phoenixvine.chronicles.network.packet.C2SResolveChoiceBoxPacket;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import java.util.ArrayList;
import java.util.List;

public class QuestTasksScreen extends Screen
                              implements net.phoenixvine.chronicles.client.audio.ChroniclesAudio.AudioSource {

    final TasksFullscreenView fullscreen = new TasksFullscreenView(this);

    final TasksCompactView compact = new TasksCompactView(this);

    final TaskPopups popups = new TaskPopups(this);

    final TaskDescriptions descriptions = new TaskDescriptions(this);

    final TaskPresentation present = new TaskPresentation(this);

    private boolean voiceAutoStarted = false;

    @Override
    public net.phoenixvine.chronicles.common.model.QuestAudio desiredAudio() {
        return node.getAudio().hasMusic() ? node.getAudio() :
                net.phoenixvine.chronicles.client.util.ChapterConfig.musicFor(node.getChapter());
    }

    boolean voiceShown() {
        return node.getAudio().hasVoice();
    }

    int voiceOffset() {
        return emergencyShown() ? 26 : 0;
    }

    int linkOffset() {
        return voiceOffset() + (voiceShown() ? 26 : 0);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    int C_BG = 0xFF0B0B0F;
    int C_PANEL = 0xFF14141A;
    int C_HEADER = 0xFF0C0C10;
    int C_BORDER = 0xFF353548;
    int C_DONE = 0xFF44CC88;
    int C_ACTIVE = 0xFFFFBB33;
    private int C_LOCKED = 0xFF606070;
    int C_TEXT = 0xFFD8D8E4;
    int C_TEXT_DIM = 0xFF7A7A8A;
    int C_TEXT_FAINT = 0xFF404050;

    static final int C_SLOT_BG = 0xFF0E0E14;
    static final int C_SLOT_HI = 0xFF5A4200;

    static final int HEADER_H_BASE = 38;
    static final int SUBTITLE_LINE_H = 10;
    static final int SUBTITLE_MAX_LINES = 3;
    static final int ICON_SZ = 18;
    private static final int ICON_STRIP_H = ICON_SZ + 8;
    static final int STRIP_GAP = 3;
    static final int FOOTER_H = 22;
    static final int MARGIN = 6;
    static final int REWARD_W = 185;
    private static final int TASK_ICON_SZ = 24;

    static final int CARD_MAX_WIDTH = 310;

    static final float MAX_CARD_WIDTH_FRACTION = 0.82f;
    static final float MAX_CARD_HEIGHT_FRACTION = 0.82f;

    static final float CARD_FRACTION_FLOOR = 0.55f;
    static final float CARD_WIDTH_FULL_AT = 520f;
    static final float CARD_HEIGHT_FULL_AT = 330f;

    static final int CARD_PAD = 6;
    private static final int CARD_TASK_ROW_H = 22;
    private static final int CARD_MAX_TASKS = 6;

    static final int CARD_MAX_DESC = 24;

    final Screen parent;

    Screen getParentScreen() {
        return parent;
    }

    final QuestNode node;

    FullQuestData content;
    final PlayerQuestData playerData;
    final Player player;

    int descScrollY = 0;
    java.util.List<net.phoenixvine.wiki.client.rich.RichSpan.Region> richRegions = java.util.List.of();

    static final java.util.regex.Pattern DESC_PAGE_BREAK = java.util.regex.Pattern
            .compile("(?m)^[ \\t]*-{3,}[ \\t]*$");
    int descPage = 0;

    int descPagerX, descPagerY, descPagerW, descPagerH, descPagerPageCount;

    private long openTimeMs = -1;
    private static final long OPEN_FADE_MS = 100;
    int inspectorScrollY = 0;
    boolean isFullscreen = false;

    Object phantasiaPreview;
    int previewX, previewY, previewW, previewH;

    boolean draggingInspDivider = false;
    boolean draggingRewardDivider = false;
    int dividerDragStartMx = 0;
    int dividerDragStartInspW = 0;
    int dividerDragStartRewardW = 0;
    static final int DIVIDER_HIT_PAD = 2;

    public QuestTasksScreen(Screen parent, QuestNode node, FullQuestData content, PlayerQuestData playerData) {
        this(parent, node, content, playerData, false);
    }

    public QuestTasksScreen(Screen parent, QuestNode node, FullQuestData content, PlayerQuestData playerData,
                            boolean startFullscreen) {
        super(Component.translatable("phoenix_chronicles.screen.quest_tasks.title"));
        this.parent = parent;
        this.node = node;
        this.content = content;
        this.playerData = playerData;
        this.player = net.minecraft.client.Minecraft.getInstance().player;
        this.isFullscreen = startFullscreen;
    }

    @Override
    protected void init() {
        super.init();
        openTimeMs = System.currentTimeMillis();
        if (!voiceAutoStarted) {
            voiceAutoStarted = true;
            QuestState openState = playerData != null ? playerData.getQuestState(node.getId(), QuestState.LOCKED) :
                    QuestState.LOCKED;
            if (node.getAudio().hasVoice() && node.getAudio().voiceAuto() && openState != QuestState.LOCKED) {
                net.phoenixvine.chronicles.client.audio.ChroniclesAudio.playVoice(node.getAudio().voiceId());
            }
        }

        isEditMode = player != null && player.hasPermissions(2) &&
                parent instanceof ChronicleOverviewScreen overview && overview.isDevMode();
        phantasiaPreview = net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.createPreviewForNode(node);

        boolean hasInfoTasks = content.effectiveTasks().stream().anyMatch(t2 -> t2 instanceof InfoTask);
        if (hasInfoTasks && playerData != null) {
            for (var task : content.effectiveTasks()) {
                if (task instanceof InfoTask) {
                    InfoTask.acknowledge(player, task.getTaskId());
                }
            }
            net.neoforged.neoforge.network.PacketDistributor
                    .sendToServer(new C2SAcknowledgeInfoTasksPacket(node.getId()));
        }

        PhoenixTheme t = PhoenixTheme.current();
        C_BG = t.bg.getColor();
        C_PANEL = t.panel.getColor();
        C_HEADER = t.header.getColor();
        C_BORDER = t.border.getColor();
        C_TEXT = t.text.getColor();
        C_TEXT_DIM = t.textDim.getColor();
        C_TEXT_FAINT = t.textFaint.getColor();
        C_DONE = t.done.getColor();
        C_ACTIVE = t.activeColor.getColor();
        C_LOCKED = t.locked.getColor();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        hoveredHeaderTooltip = null;

        descriptions.refreshEffectiveContent();

        if (isFullscreen) {
            fullscreen.renderFullscreen(g, mx, my, partial);
        } else {
            compact.renderCompact(g, mx, my, partial);
        }

        if (popups.openChoiceBox != null) popups.renderChoiceBoxPopup(g, mx, my);

        if (openTimeMs > 0) {
            long elapsed = System.currentTimeMillis() - openTimeMs;
            if (elapsed < OPEN_FADE_MS) {
                float frac = 1f - (float) elapsed / OPEN_FADE_MS;
                int alpha = (int) (frac * frac * 0xFF) & 0xFF;
                if (alpha > 0) g.fill(0, 0, width, height, (alpha << 24));
            }
        }

        for (net.phoenixvine.wiki.client.rich.RichSpan.Region r : richRegions) {
            if (!r.contains(mx, my)) continue;
            if (r.span() instanceof net.phoenixvine.wiki.client.rich.RichSpan.Tip t) {
                g.pose().pushPose();
                g.pose().translate(0f, 0f, 500f);
                g.flush();
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(ChroniclesUIKit.lit(t.tooltip()),
                        240);
                g.renderTooltip(font, lines, mx, my);
                g.flush();
                g.pose().popPose();
                break;
            }
            if (r.span() instanceof net.phoenixvine.wiki.client.rich.RichSpan.ItemIcon icon) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(icon.itemId());
                if (item != null) {
                    g.pose().pushPose();
                    g.pose().translate(0f, 0f, 500f);
                    g.flush();
                    g.renderTooltip(font, new net.minecraft.world.item.ItemStack(item).getHoverName(), mx, my);
                    g.flush();
                    g.pose().popPose();
                }
                break;
            }
        }

        if (hoveredHeaderTooltip != null) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 500f);
            g.flush();
            g.renderTooltip(font, ChroniclesUIKit.lit(hoveredHeaderTooltip), mx, my);
            g.flush();
            g.pose().popPose();
        }
    }

    static final int TASK_LIST_ROW_H = 24;

    private static final int REWARD_MINI_SZ = 14;

    static final int PREVIEW_H_COMPACT = 46;

    boolean isEditMode = false;
    int descBoxX, descBoxY, descBoxW, descBoxH;

    int compactDescScrollLine = 0;
    int compactDescTotalLines = 0;
    int compactDescFittedLines = 0;

    int fsDescMaxScrollY = 0;

    int openChoiceBoxRewardIndex = -1;
    final List<int[]> choiceBoxRowRects = new ArrayList<>();

    String hoveredHeaderTooltip = null;

    boolean emergencyShown() {
        if (playerData == null) return false;
        return playerData.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.ACTIVE &&
                net.phoenixvine.chronicles.client.util.ClientEmergencyState.hasEmergencyItems(node.getId());
    }

    boolean emergencyUsedUp() {
        return playerData != null && playerData.hasUsedEmergency(node.getId()) &&
                !net.phoenixvine.chronicles.client.util.ClientEmergencyState.isRepeatable(node.getId());
    }

    record StripLayout(boolean single, int perRow, int taskRows, int rows) {}

    enum InspTab {
        INFO,
        TASKS
    }

    static final int INSP_TAB_H = 16;

    static boolean lineFullyVisible(int lineY, int viewTop, int viewBot) {
        return lineY >= viewTop && lineY + 9 <= viewBot;
    }

    List<QuestNode> dependentsOf(QuestNode target) {
        List<QuestNode> out = new ArrayList<>();
        for (QuestNode candidate : QuestTreeRegistry.getAllQuests().values()) {
            if (candidate != target && candidate.getPrerequisites().contains(target)) out.add(candidate);
        }
        return out;
    }

    int renderRichTaskRow(GuiGraphics g, int x, int y, int w, QuestTask task, int mx, int my) {
        boolean done = isTaskDone(task);
        String progress = present.taskProgressString(task);
        String detailForRowH = present.getTaskDetail(task);
        int rowH = detailForRowH != null ? 30 : 22;

        boolean rowHov = mx >= x && mx < x + w && my >= y && my < y + rowH;
        if (rowHov) g.fill(x, y, x + w, y + rowH, 0x14FFFFFF);

        ChroniclesUIKit.drawString(g, font, done ? "§a✔" : (task.isOptional() ? "§8○" : "§c✗"), x, y + 1, 0xFFFFFFFF,
                false);

        int cx = x + 10;
        ItemStack icon = present.getTaskIcon(task);
        if (!icon.isEmpty()) {
            g.renderItem(icon, cx, y);
            g.renderItemDecorations(font, icon, cx, y);
            if (done) g.fill(cx, y, cx + 16, y + 16, 0x5500AA44);
            cx += 18;
        } else {
            ChroniclesUIKit.drawString(g, font, present.getTaskGlyph(task), cx, y + 1, 0xFFFFFFFF, false);
            cx += 10;
        }

        float taskTs = QuestChroniclesSettings.get().getTextScaleMultiplier();

        int ellipsisW = font.width("…");

        String desc = task.getDescription().getString();
        int progW = (progress != null && !done) ? Math.round(font.width(progress) * taskTs) + 4 : 0;
        int descAvailW = w - (cx - x) - progW;
        float descAvailPreScale = descAvailW / taskTs;
        if (font.width(desc) > descAvailPreScale)
            desc = font.plainSubstrByWidth(desc, Math.max(0, (int) descAvailPreScale - ellipsisW)) + "…";
        ChroniclesUIKit.drawScaledString(g, font, "§f" + desc, cx, y + 1, done ? C_DONE : C_TEXT, taskTs);

        String detail = detailForRowH;
        if (detail != null) {
            int detailAvailW = w - (cx - x);
            float detailAvailPreScale = detailAvailW / taskTs;
            if (font.width(detail) > detailAvailPreScale)
                detail = font.plainSubstrByWidth(detail, Math.max(0, (int) detailAvailPreScale - ellipsisW)) + "…";
            ChroniclesUIKit.drawScaledString(g, font, "§8" + detail, cx, y + 11, C_TEXT_FAINT, taskTs);
        }

        float pct = done ? 1f : present.parseProgress(progress);
        int barY = y + (detail != null ? 22 : 14);
        int barW = w - 12;
        g.fill(x + 10, barY, x + 10 + barW, barY + 3, 0xFF1A1A22);
        if (pct > 0) g.fill(x + 10, barY, x + 10 + (int) (barW * pct), barY + 3, done ? C_DONE : C_ACTIVE);
        if (progress != null) {
            int progTextW = Math.round(font.width(progress) * taskTs);
            ChroniclesUIKit.drawScaledString(g, font, "§8" + progress, x + w - progTextW, barY - 1, C_TEXT_FAINT,
                    taskTs);
        }

        return barY + 3 + 5;
    }

    void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        ChroniclesUIKit.drawBorder(g, x, y, w, h, C_BORDER);
    }

    boolean rewardsRevealed() {
        return playerData != null &&
                playerData.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED;
    }

    boolean superMouseClicked(double mx, double my, int btn) {
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (popups.openChoiceBox != null) {
            if (btn == 0) {
                for (int i = 0; i < choiceBoxRowRects.size(); i++) {
                    int[] r = choiceBoxRowRects.get(i);
                    if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new C2SResolveChoiceBoxPacket(node.getId(), openChoiceBoxRewardIndex, i));
                        popups.openChoiceBox = null;
                        openChoiceBoxRewardIndex = -1;
                        return true;
                    }
                }
            }
            popups.openChoiceBox = null;
            openChoiceBoxRewardIndex = -1;
            return true;
        }
        if (phantasiaPreview != null && previewW > 0 &&
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.previewMouseClicked(phantasiaPreview,
                        mx, my, previewX, previewY, previewW, previewH, this)) {
            return true;
        }
        if (isFullscreen && btn == 0 && fullscreen.tryStartDividerDrag(mx, my)) return true;
        return isFullscreen ? fullscreen.handleFullscreenClick(mx, my, btn) : compact.handleCompactClick(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (draggingInspDivider) {
            int delta = (int) mx - dividerDragStartMx;
            int rewardW = fullscreen.calcRewardW();
            int maxInspW = Math.max(MIN_INSP_W, width - MARGIN * 3 - MIN_CONTENT_W - rewardW);
            int newInspW = Math.max(MIN_INSP_W, Math.min(maxInspW, dividerDragStartInspW - delta));
            QuestChroniclesSettings.get().setTaskInspectorW(newInspW);
            return true;
        }
        if (draggingRewardDivider) {
            int delta = (int) mx - dividerDragStartMx;
            int pairTotal = dividerDragStartInspW + dividerDragStartRewardW;
            int newInspW = Math.max(MIN_INSP_W, Math.min(pairTotal - MIN_REWARD_W, dividerDragStartInspW + delta));
            int newRewardW = pairTotal - newInspW;
            var settings = QuestChroniclesSettings.get();
            settings.setTaskInspectorW(newInspW);
            settings.setTaskRewardW(newRewardW);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (draggingInspDivider || draggingRewardDivider) {
            draggingInspDivider = false;
            draggingRewardDivider = false;
            QuestChroniclesSettings.get().save();
            return true;
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (descPagerPageCount > 1 && mx >= descPagerX && mx < descPagerX + descPagerW &&
                my >= descPagerY && my < descPagerY + descPagerH) {
            if (delta > 0 && descPage > 0) descPage--;
            else if (delta < 0 && descPage < descPagerPageCount - 1) descPage++;
            if (isFullscreen) descScrollY = 0;
            else compactDescScrollLine = 0;
            return true;
        }
        if (!isFullscreen) {
            if (mx < descBoxX || mx >= descBoxX + descBoxW || my < descBoxY || my >= descBoxY + descBoxH) {
                return false;
            }
            int maxScrollLine = Math.max(0, compactDescTotalLines - compactDescFittedLines);

            if (delta < 0 && compactDescScrollLine >= maxScrollLine && descPagerPageCount > 1 &&
                    descPage < descPagerPageCount - 1) {
                descPage++;
                compactDescScrollLine = 0;
                return true;
            }
            if (delta > 0 && compactDescScrollLine <= 0 && descPagerPageCount > 1 && descPage > 0) {
                descPage--;
                compactDescScrollLine = 0;
                return true;
            }

            if (maxScrollLine == 0) return false;
            compactDescScrollLine = Math.max(0, Math.min(maxScrollLine, compactDescScrollLine - (int) (delta * 2)));
            return true;
        }
        int contentTop = fullscreen.headerH() + fullscreen.reqBarH() + MARGIN;
        int[] pw3 = fullscreen.fullscreenPanelWidths();
        int inspW3 = pw3[0];
        int contentRight = width - inspW3 - pw3[1] - MARGIN * 3;
        int rightX = contentRight + MARGIN;
        if (mx >= rightX && mx < rightX + inspW3 && my >= contentTop && my < height - FOOTER_H - MARGIN) {
            inspectorScrollY = Math.max(0, (int) (inspectorScrollY - delta * 12));
        } else {

            if (delta < 0 && descScrollY >= fsDescMaxScrollY && descPagerPageCount > 1 &&
                    descPage < descPagerPageCount - 1) {
                descPage++;
                descScrollY = 0;
            } else if (delta > 0 && descScrollY <= 0 && descPagerPageCount > 1 && descPage > 0) {
                descPage--;
                descScrollY = 0;
            } else {
                descScrollY = Math.max(0, Math.min(fsDescMaxScrollY, (int) (descScrollY - delta * 12)));
            }
        }
        return true;
    }

    private boolean toggleViewKeyDown = false;

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (popups.openChoiceBox != null) {
                popups.openChoiceBox = null;
                openChoiceBoxRewardIndex = -1;
                return true;
            }
            if (isFullscreen) {
                isFullscreen = false;
                return true;
            }
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        if (ChronicleKeyBindings.TOGGLE_QUEST_VIEW.matches(key, scan)) {
            if (!toggleViewKeyDown) {
                isFullscreen = !isFullscreen;
                toggleViewKeyDown = true;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (ChronicleKeyBindings.TOGGLE_QUEST_VIEW.matches(key, scan)) {
            toggleViewKeyDown = false;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public void onClose() {
        net.phoenixvine.chronicles.client.audio.ChroniclesAudio.stopVoice();
        if (phantasiaPreview != null) {
            net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.closePreview(phantasiaPreview);
            phantasiaPreview = null;
        }
        LangSyncScheduler.flushNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static final int MIN_INSP_W = 90;
    static final int MIN_REWARD_W = 70;
    static final int MIN_CONTENT_W = 150;

    boolean isTaskDone(QuestTask task) {
        if (playerData != null && playerData.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED) {
            return true;
        }
        return player != null && task.isCompletedFor(player);
    }

    boolean rewardsClaimed() {
        return playerData != null && playerData.hasClaimedRewards(node.getId());
    }

    boolean isRewardPicked(int rewardIndex) {
        if (playerData == null) return false;
        if (playerData.getChosenRewardIndices(node.getId()).contains(rewardIndex)) return true;
        return playerData.isChoiceBoxResolved(node.getId(), rewardIndex);
    }

    boolean isSlotPicked(int ri, boolean wholeNodeClaimed) {
        return fullscreen.boxAt(ri) != null ? isRewardPicked(ri) : (wholeNodeClaimed || isRewardPicked(ri));
    }
}

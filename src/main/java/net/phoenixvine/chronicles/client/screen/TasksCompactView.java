package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.registry.LangSyncScheduler;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.network.packet.C2SClaimQuestRewardPacket;

import java.util.List;

final class TasksCompactView {

    private final QuestTasksScreen screen;

    TasksCompactView(QuestTasksScreen screen) {
        this.screen = screen;
    }

    int cardW() {
        float fraction = Math.max(QuestTasksScreen.CARD_FRACTION_FLOOR,
                Math.min(QuestTasksScreen.MAX_CARD_WIDTH_FRACTION, screen.width / QuestTasksScreen.CARD_WIDTH_FULL_AT));
        int fractionCap = Math.round(screen.width * fraction);
        return Math.min(QuestTasksScreen.CARD_MAX_WIDTH, Math.max(120, Math.min(screen.width - 24, fractionCap)));
    }

    private int maxCardH() {
        float fraction = Math.max(QuestTasksScreen.CARD_FRACTION_FLOOR,
                Math.min(QuestTasksScreen.MAX_CARD_HEIGHT_FRACTION,
                        screen.height / QuestTasksScreen.CARD_HEIGHT_FULL_AT));
        return Math.max(60, Math.round(screen.height * fraction));
    }

    private int compactHeaderH() {
        int lines = screen.descriptions.wrapSubtitle(screen.node.getSubtitle(), cardW() - 12).size();
        return lines == 0 ? 20 : 20 + lines * QuestTasksScreen.SUBTITLE_LINE_H;
    }

    private record DescriptionContent(int pageCount, List<net.phoenixvine.wiki.client.rich.RichBlock> blocks,
                                      List<net.minecraft.util.FormattedCharSequence> questLines,
                                      List<net.minecraft.util.FormattedCharSequence> allLines, float textScale,
                                      int lineHeight) {}

    private record CardLayout(int x, int y, int height, int headerHeight, int descHeight, int fittedDescLines,
                              int pagerHeight) {}

    void renderCompact(GuiGraphics g, int mx, int my, float partial) {
        screen.fullscreen.hoveredTask = null;
        screen.fullscreen.hoveredReward = null;
        screen.fullscreen.hoveredRewardIndex = -1;

        drawBackdrop(g, partial);
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.flush();
        g.fill(0, 0, screen.width, screen.height, 0x88000000);

        List<QuestTask> tasks = screen.content.effectiveTasks();
        List<QuestReward> rewards = screen.content.effectiveRewards();
        DescriptionContent description = loadDescription(tasks);
        CardLayout layout = layoutCard(description);

        drawCardFrame(g, layout);
        drawCardHeader(g, layout, mx, my);

        int y = layout.y() + layout.headerHeight();
        y = drawIconStrip(g, layout, y, mx, my, tasks, rewards);
        y = drawPreview(g, layout, y, partial);
        y = drawDescription(g, layout, description, y, mx, my);
        renderCompactFooter(g, layout.x(), y, cardW(), 18, mx, my);

        g.disableScissor();
        drawHoverTooltips(g, mx, my);
        g.pose().popPose();
    }

    private void drawBackdrop(GuiGraphics g, float partial) {
        if (screen.parent instanceof ChronicleOverviewScreen overview) {
            overview.renderForChildScreen(g);
        } else if (screen.parent != null) {
            screen.parent.render(g, -9999, -9999, partial);
            g.flush();
            com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        } else {
            g.fill(0, 0, screen.width, screen.height, screen.C_BG);
        }
    }

    private DescriptionContent loadDescription(List<QuestTask> tasks) {
        int compactDescPageCount = screen.descriptions
                .splitDescPages(screen.descriptions.currentDisplayDescriptionText()).size();
        String descText = screen.descriptions
                .currentDescriptionPageText(screen.descriptions.currentDisplayDescriptionText());

        if (screen.fullscreen.richSpansPage != screen.descPage ||
                !descText.equals(screen.fullscreen.descBlocksSourceText)) {
            screen.fullscreen.descBlocks = descText.isEmpty() ? java.util.List.of() :
                    net.phoenixvine.chronicles.client.rich.ChroniclesMarkdown.parse(descText);
            screen.fullscreen.richSpansPage = screen.descPage;
            screen.fullscreen.descBlocksSourceText = descText;
        }
        java.util.List<net.phoenixvine.wiki.client.rich.RichBlock> resolvedDescBlocks = screen.descriptions
                .resolveConditionals(
                        screen.fullscreen.descBlocks);

        float compactTextScale = QuestChroniclesSettings.get()
                .getTextScaleMultiplier();
        int compactLineH = Math.max(1, Math.round(10 * compactTextScale));

        int questDescLineCount = resolvedDescBlocks.isEmpty() ? 0 :
                (net.phoenixvine.wiki.client.rich.WikiRichTextRenderer.measureBlocksHeight(
                        Minecraft.getInstance().font, resolvedDescBlocks, cardW() - QuestTasksScreen.CARD_PAD * 2,
                        compactTextScale, screen.fullscreen.descExpandedKeys) +
                        compactLineH - 1) / compactLineH;

        java.util.List<net.minecraft.util.FormattedCharSequence> questDescLines = java.util.Collections
                .nCopies(questDescLineCount, net.minecraft.util.FormattedCharSequence.EMPTY);
        java.util.List<net.minecraft.util.FormattedCharSequence> descLines = screen.descriptions.buildAllDescLines(
                tasks,
                questDescLines);
        return new DescriptionContent(compactDescPageCount, resolvedDescBlocks, questDescLines, descLines,
                compactTextScale, compactLineH);
    }

    private CardLayout layoutCard(DescriptionContent description) {
        int compactDescPageCount = description.pageCount();
        int compactLineH = description.lineHeight();
        List<net.minecraft.util.FormattedCharSequence> descLines = description.allLines();
        int compactHeaderH = compactHeaderH();

        int fixedH = compactHeaderH + 1 + compactStripH() + 2 + 1 + 18 + screen.fullscreen.previewBlockH();
        int rawDesc = Math.min(descLines.size(), QuestTasksScreen.CARD_MAX_DESC);
        int fittedDesc = Math.max(0, Math.min(rawDesc, ((maxCardH() - compactHeaderH) - fixedH - 9) / compactLineH));

        int descH = fittedDesc > 0 ? 4 + fittedDesc * compactLineH + 6 : (screen.isEditMode ? 24 : 0);

        int pagerStripH = compactDescPageCount > 1 ? 17 : 0;
        int cardH = fixedH + descH + (descH > 0 ? 1 : 0) + pagerStripH + (pagerStripH > 0 ? 1 : 0);
        cardH = Math.min(cardH, maxCardH());

        int cardX = (screen.width - cardW()) / 2;
        int cardY = Math.max(10, (screen.height - cardH) / 2);
        return new CardLayout(cardX, cardY, cardH, compactHeaderH, descH, fittedDesc, pagerStripH);
    }

    private void drawCardFrame(GuiGraphics g, CardLayout layout) {
        int cardX = layout.x();
        int cardY = layout.y();
        int cardH = layout.height();
        g.fill(cardX + 3, cardY + 3, cardX + cardW() + 3, cardY + cardH + 3, 0x66000000);
        g.fill(cardX, cardY, cardX + cardW(), cardY + cardH, screen.C_BG);
        screen.drawBorder(g, cardX, cardY, cardW(), cardH);

        g.enableScissor(cardX, cardY, cardX + cardW(), cardY + cardH);
    }

    private void drawCardHeader(GuiGraphics g, CardLayout layout, int mx, int my) {
        int cardX = layout.x();
        int cy = layout.y();
        int compactHeaderH = layout.headerHeight();
        java.util.List<String> compactSubtitleLines = screen.descriptions.wrapSubtitle(screen.node.getSubtitle(),
                cardW() - 12);
        g.fill(cardX, cy, cardX + cardW(), cy + compactHeaderH, screen.C_HEADER);
        String title = (screen.node.isOptional() ? "§d[Optional] §f" : "") + screen.content.title().getString();
        if (net.phoenixvine.chronicles.client.util.EffectText.width(Minecraft.getInstance().font, title) > cardW() - 50)
            title = net.phoenixvine.chronicles.client.util.EffectText.cut(Minecraft.getInstance().font, title,
                    cardW() - 50 - Minecraft.getInstance().font.width("…")) +
                    "…";
        ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§f" + title, cardX + QuestTasksScreen.CARD_PAD,
                cy + 6, screen.C_TEXT, false);

        for (int i = 0; i < compactSubtitleLines.size(); i++) {
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, compactSubtitleLines.get(i),
                    cardX + QuestTasksScreen.CARD_PAD,
                    cy + 18 + i * QuestTasksScreen.SUBTITLE_LINE_H,
                    screen.C_TEXT_DIM, false);
        }

        boolean fsHov = mx >= cardX + cardW() - 18 && mx < cardX + cardW() - 4 && my >= cy + 3 && my < cy + 17;
        if (fsHov) {
            g.fill(cardX + cardW() - 18, cy + 3, cardX + cardW() - 4, cy + 17, 0x33FFFFFF);
            screen.hoveredHeaderTooltip = "Expand to fullscreen";
        }
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, fsHov ? "§b[+]" : "§8[+]",
                cardX + cardW() - 11, cy + 6,
                fsHov ? screen.C_ACTIVE : screen.C_TEXT_FAINT);

        boolean closeHov = mx >= cardX + cardW() - 34 && mx < cardX + cardW() - 20 && my >= cy + 3 && my < cy + 17;
        if (closeHov) {
            g.fill(cardX + cardW() - 34, cy + 3, cardX + cardW() - 20, cy + 17, 0x33FFFFFF);
            screen.hoveredHeaderTooltip = "Close";
        }
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, closeHov ? "§c✕" : "§8✕",
                cardX + cardW() - 27, cy + 6,
                closeHov ? 0xFFFF6666 : screen.C_TEXT_FAINT);

        boolean compactPinned = screen.playerData != null && screen.playerData.isPinned(screen.node.getId());
        boolean pinHovCompact = mx >= cardX + cardW() - 50 && mx < cardX + cardW() - 36 && my >= cy + 3 &&
                my < cy + 17;
        if (pinHovCompact) {
            g.fill(cardX + cardW() - 50, cy + 3, cardX + cardW() - 36, cy + 17, 0x33FFFFFF);
            screen.hoveredHeaderTooltip = compactPinned ? "Unpin quest" : "Pin quest";
        }
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, compactPinned ? "§d📌" : "§8📌",
                cardX + cardW() - 43, cy + 6,
                compactPinned ? 0xFFAA44FF : screen.C_TEXT_FAINT);
    }

    private int drawIconStrip(GuiGraphics g, CardLayout layout, int cy, int mx, int my, List<QuestTask> tasks,
                              List<QuestReward> rewards) {
        int cardX = layout.x();
        g.fill(cardX, cy, cardX + cardW(), cy + 1, screen.C_BORDER);
        cy += 1;

        screen.fullscreen.renderIconStrip(g, cardX, cy, mx, my, tasks, rewards);
        cy += compactStripH();

        cy += 2;
        g.fill(cardX, cy, cardX + cardW(), cy + 1, screen.C_BORDER);
        cy += 1;
        return cy;
    }

    private int drawPreview(GuiGraphics g, CardLayout layout, int cy, float partial) {
        int cardX = layout.x();
        if (screen.phantasiaPreview != null) {
            int pvSz = QuestTasksScreen.PREVIEW_H_COMPACT;
            int pvX = cardX + QuestTasksScreen.CARD_PAD;
            int pvY = cy + 1;
            g.fill(pvX, pvY, pvX + pvSz, pvY + pvSz - 2, 0xFF0A0A10);
            screen.drawBorder(g, pvX, pvY, pvSz, pvSz - 2);
            screen.previewX = pvX + 1;
            screen.previewY = pvY + 1;
            screen.previewW = pvSz - 2;
            screen.previewH = pvSz - 4;
            net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.tickPreview(screen.phantasiaPreview);
            net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.renderPreview(screen.phantasiaPreview, g,
                    screen.previewX, screen.previewY, screen.previewW, screen.previewH, partial);
            cy += QuestTasksScreen.PREVIEW_H_COMPACT;
            g.fill(cardX, cy, cardX + cardW(), cy + 1, screen.C_BORDER);
            cy += 1;
        }
        return cy;
    }

    private int drawDescription(GuiGraphics g, CardLayout layout, DescriptionContent description, int cy, int mx,
                                int my) {
        int cardX = layout.x();
        int descH = layout.descHeight();
        int fittedDesc = layout.fittedDescLines();
        int pagerStripH = layout.pagerHeight();
        int compactDescPageCount = description.pageCount();
        float compactTextScale = description.textScale();
        List<net.phoenixvine.wiki.client.rich.RichBlock> resolvedDescBlocks = description.blocks();
        List<net.minecraft.util.FormattedCharSequence> questDescLines = description.questLines();
        List<net.minecraft.util.FormattedCharSequence> descLines = description.allLines();
        hoveredDescBox = false;
        if (descH > 0) {
            screen.descBoxX = cardX;
            screen.descBoxY = cy;
            screen.descBoxW = cardW();
            screen.descBoxH = descH;
            hoveredDescBox = screen.isEditMode && mx >= screen.descBoxX && mx < screen.descBoxX + screen.descBoxW &&
                    my >= screen.descBoxY &&
                    my < screen.descBoxY + screen.descBoxH;

            if (screen.isEditMode) {
                g.fill(cardX + 2, cy + 1, cardX + cardW() - 2, cy + descH - 1,
                        hoveredDescBox ? 0xFF1C1C26 : 0xFF15151C);
                int dashColor = hoveredDescBox ? screen.C_ACTIVE : screen.C_TEXT_FAINT;
                for (int dx = cardX + 2; dx < cardX + cardW() - 2; dx += 4) {
                    g.fill(dx, cy + 1, dx + 2, cy + 2, dashColor);
                    g.fill(dx, cy + descH - 2, dx + 2, cy + descH - 1, dashColor);
                }
            }

            screen.compactDescTotalLines = descLines.size();
            screen.compactDescFittedLines = fittedDesc;
            int maxScrollLine = Math.max(0, screen.compactDescTotalLines - screen.compactDescFittedLines);
            screen.compactDescScrollLine = Math.max(0, Math.min(screen.compactDescScrollLine, maxScrollLine));

            int dy = cy + 4;
            if (fittedDesc == 0 && screen.isEditMode) {
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8Click to add a description",
                        cardX + QuestTasksScreen.CARD_PAD, dy, screen.C_TEXT_FAINT,
                        false);
            }

            g.enableScissor(cardX, cy + 1, cardX + cardW(), cy + descH - 1);

            screen.richRegions = net.phoenixvine.wiki.client.rich.WikiRichTextRenderer.renderBlocks(
                    g, Minecraft.getInstance().font, resolvedDescBlocks,
                    cardX + QuestTasksScreen.CARD_PAD, cy + 4, cardW() - QuestTasksScreen.CARD_PAD * 2,

                    screen.compactDescScrollLine * Math.round(10 * compactTextScale),
                    cy + 1, cy + descH - 1,

                    compactTextScale, screen.C_ACTIVE, screen.fullscreen.descExpandedKeys);

            if (descLines.size() > questDescLines.size()) {
                for (int i = questDescLines.size(); i < descLines.size(); i++) {
                    int renderY = cy + 4 + (i * 10) - (screen.compactDescScrollLine * 10);
                    if (renderY >= cy + 4 && renderY + 10 <= cy + descH - 4) {
                        ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, descLines.get(i),
                                cardX + QuestTasksScreen.CARD_PAD, renderY, screen.C_TEXT_DIM,
                                false);
                    }
                }
            }

            g.disableScissor();

            if (maxScrollLine > 0) {
                String hint = screen.compactDescScrollLine < maxScrollLine ? "§7▼ scroll for more" : "§7▲ scroll up";
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, hint,
                        cardX + cardW() - Minecraft.getInstance().font.width(hint) - QuestTasksScreen.CARD_PAD - 2,
                        cy + descH - 11,
                        screen.C_TEXT_FAINT, false);
            } else if (screen.isEditMode) {
                String hint = "§7✎ edit";
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, hint,
                        cardX + cardW() - Minecraft.getInstance().font.width(hint) - QuestTasksScreen.CARD_PAD - 2,
                        cy + descH - 11,
                        hoveredDescBox ? screen.C_ACTIVE : screen.C_TEXT_FAINT, false);
            }
            cy += descH;
            g.fill(cardX, cy, cardX + cardW(), cy + 1, screen.C_BORDER);
            cy += 1;

            if (pagerStripH > 0) {
                screen.descriptions.renderDescPager(g, cardX, cy, cardW(), pagerStripH, mx, my, compactDescPageCount);
                cy += pagerStripH;
                g.fill(cardX, cy, cardX + cardW(), cy + 1, screen.C_BORDER);
                cy += 1;
            }
        }
        return cy;
    }

    private void drawHoverTooltips(GuiGraphics g, int mx, int my) {
        if (screen.fullscreen.hoveredTask != null) {
            g.pose().translate(0f, 0f, 200f);
            g.renderComponentTooltip(Minecraft.getInstance().font,
                    screen.present.buildTaskTooltip(screen.fullscreen.hoveredTask),
                    mx, my);
        } else if (screen.fullscreen.hoveredReward != null) {
            g.pose().translate(0f, 0f, 200f);
            g.renderComponentTooltip(Minecraft.getInstance().font,
                    screen.present.buildRewardTooltip(screen.fullscreen.hoveredReward), mx, my);
        }
    }

    private void renderCompactTaskRow(GuiGraphics g, int x, int y, int w, QuestTask task, int mx, int my) {
        boolean done = screen.isTaskDone(task);
        String progress = screen.present.taskProgressString(task);
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + QuestTasksScreen.TASK_LIST_ROW_H - 6;
        if (hov) {
            screen.fullscreen.hoveredTask = task;
            screen.fullscreen.hoveredSlotX = x;
            screen.fullscreen.hoveredSlotY = y;
        }

        if (hov) g.fill(x, y, x + w, y + QuestTasksScreen.TASK_LIST_ROW_H - 6, 0x18FFFFFF);

        int accent = done ? screen.C_DONE : (task.isOptional() ? screen.C_TEXT_FAINT : screen.C_ACTIVE);
        g.fill(x, y + 1, x + 2, y + QuestTasksScreen.TASK_LIST_ROW_H - 7, accent);

        String mark = done ? "§a✔" : (task.isOptional() ? "§8○" : "§c✗");
        ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, mark, x + 4, y + 3, 0xFFFFFFFF, false);

        int textX = x + 16;
        ItemStack icon = screen.present.getTaskIcon(task);
        if (!icon.isEmpty()) {
            g.renderItem(icon, textX, y);
            g.renderItemDecorations(Minecraft.getInstance().font, icon, textX, y);
            textX += 18;
        } else {
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, screen.present.getTaskGlyph(task), textX, y + 3,
                    0xFFFFFFFF, false);
            textX += 10;
        }

        String desc = task.getDescription().getString();
        String detail = screen.present.getTaskDetail(task);

        boolean descIsId = desc.isEmpty() || desc.matches("[a-z0-9_]+");
        String primary = (descIsId && detail != null) ? detail : desc;

        float rowTs = QuestChroniclesSettings.get().getTextScaleMultiplier();

        String prog = done ? "§a✔" : (progress != null ? "§8" + progress : "");
        int progW = prog.isEmpty() ? 0 : Math.round(Minecraft.getInstance().font.width(prog) * rowTs) + 2;
        int labelW = w - (textX - x) - progW - 2;

        float labelAvailPreScale = labelW / rowTs;
        if (Minecraft.getInstance().font.width(primary) > labelAvailPreScale)
            primary = Minecraft.getInstance().font.plainSubstrByWidth(primary,
                    (int) labelAvailPreScale - Minecraft.getInstance().font.width("…")) + "…";
        ChroniclesUIKit.drawScaledString(g, Minecraft.getInstance().font, (done ? "§7" : "§f") + primary, textX, y + 3,
                done ? screen.C_TEXT_DIM : screen.C_TEXT, rowTs);
        if (!prog.isEmpty()) {
            ChroniclesUIKit.drawScaledString(g, Minecraft.getInstance().font, prog, x + w - progW, y + 3,
                    screen.C_TEXT_FAINT, rowTs);
        }

        float pct = done ? 1f : screen.present.parseProgress(progress);
        int barY = y + 14;
        g.fill(x, barY, x + w, barY + 3, 0xFF1A1A22);
        if (pct > 0) g.fill(x, barY, x + (int) (w * pct), barY + 3, done ? screen.C_DONE : screen.C_ACTIVE);
    }

    private void renderCompactFooter(GuiGraphics g, int cardX, int cy, int cardW, int h, int mx, int my) {
        screen.fullscreen.drawEmergencyButton(g, cardX + 4, cy + 1, 22, h - 2, mx, my);
        screen.fullscreen.drawVoiceButton(g, cardX + 4 + screen.voiceOffset(), cy + 1, 22, h - 2, mx, my);
        screen.fullscreen.drawLinkButton(g, cardX + 4 + screen.linkOffset(), cy + 1, 22, h - 2, mx, my);
        QuestState state = screen.playerData != null ?
                screen.playerData.getQuestState(screen.node.getId(), QuestState.LOCKED) :
                QuestState.LOCKED;
        boolean canClaim = state == QuestState.COMPLETED && !screen.rewardsClaimed() &&
                !screen.content.effectiveRewards().isEmpty();

        if (canClaim) {
            int btnW = 120;
            int btnX = cardX + (cardW - btnW) / 2;
            int btnY = cy + 1;
            boolean hov = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + h - 2;
            g.fill(btnX, btnY, btnX + btnW, btnY + h - 2, hov ? 0xFF2A4A2A : 0xFF1A2A1A);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, hov ? screen.C_DONE : 0xFF333333);
            ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§a✓ Claim Rewards", btnX + btnW / 2,
                    btnY + 4,
                    hov ? screen.C_DONE : screen.C_TEXT);
        } else if (screen.rewardsClaimed()) {
            ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§8Rewards Claimed", cardX + cardW / 2,
                    cy + 5, screen.C_TEXT_FAINT);
        } else {
            ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§8Complete Tasks to Claim",
                    cardX + cardW / 2, cy + 5,
                    screen.C_TEXT_FAINT);
        }
    }

    static QuestTasksScreen.StripLayout stripLayout(int innerW, int taskCount, int rewardCount) {
        int step = QuestTasksScreen.ICON_SZ + QuestTasksScreen.STRIP_GAP;
        int perRow = Math.max(1, (innerW + QuestTasksScreen.STRIP_GAP) / step);
        int groupGap = taskCount > 0 && rewardCount > 0 ? 1 : 0;
        if (taskCount + rewardCount + groupGap <= perRow) return new QuestTasksScreen.StripLayout(true, perRow, 1, 1);

        int taskRows = (taskCount + perRow - 1) / perRow;
        int rewardRows = (rewardCount + perRow - 1) / perRow;
        return new QuestTasksScreen.StripLayout(false, perRow, taskRows, Math.max(1, taskRows + rewardRows));
    }

    static int stripHeight(QuestTasksScreen.StripLayout l) {
        return l.rows() * QuestTasksScreen.ICON_SZ + (l.rows() - 1) * QuestTasksScreen.STRIP_GAP + 8;
    }

    static int stripTaskX(QuestTasksScreen.StripLayout l, int left, int i) {
        return left + (l.single() ? i : i % l.perRow()) * (QuestTasksScreen.ICON_SZ + QuestTasksScreen.STRIP_GAP);
    }

    static int stripTaskRow(QuestTasksScreen.StripLayout l, int i) {
        return l.single() ? 0 : i / l.perRow();
    }

    static int stripRewardRow(QuestTasksScreen.StripLayout l, int i) {
        return l.single() ? 0 : l.taskRows() + i / l.perRow();
    }

    private int compactStripH() {
        int innerW = cardW() - QuestTasksScreen.CARD_PAD * 2 - 2;
        return stripHeight(
                stripLayout(innerW, screen.content.effectiveTasks().size(), screen.content.effectiveRewards().size()));
    }

    boolean handleCompactClick(double mx, double my, int btn) {
        if (btn == 1 && screen.isEditMode && Minecraft.getInstance() != null &&
                (screen.fullscreen.hoveredTask != null || screen.fullscreen.hoveredReward != null)) {
            Minecraft.getInstance().setScreen(new TaskRewardEditorScreen(screen, screen.node));
            return true;
        }
        if (btn == 2 && screen.isEditMode && screen.fullscreen.hoveredTask != null) {
            if (Screen.hasShiftDown()) screen.fullscreen.actions.resetTaskProgress(screen.fullscreen.hoveredTask);
            else screen.fullscreen.actions.forceCompleteTask(screen.fullscreen.hoveredTask);
            return true;
        }
        if (btn != 0) return screen.superMouseClicked(mx, my, btn);

        List<QuestTask> tasks = screen.content.effectiveTasks();
        List<QuestReward> rewards = screen.content.effectiveRewards();

        String fullDescText = screen.descriptions.currentDisplayDescriptionText();
        int pageCount = screen.descriptions.splitDescPages(fullDescText).size();
        String descText = screen.descriptions.currentDescriptionPageText(fullDescText);

        if (screen.fullscreen.richSpansPage != screen.descPage ||
                !descText.equals(screen.fullscreen.descBlocksSourceText)) {
            screen.fullscreen.descBlocks = descText.isEmpty() ? java.util.List.of() :
                    net.phoenixvine.chronicles.client.rich.ChroniclesMarkdown.parse(descText);
            screen.fullscreen.richSpansPage = screen.descPage;
            screen.fullscreen.descBlocksSourceText = descText;
        }
        java.util.List<net.phoenixvine.wiki.client.rich.RichBlock> resolvedDescBlocks = screen.descriptions
                .resolveConditionals(
                        screen.fullscreen.descBlocks);

        float compactTextScale = QuestChroniclesSettings.get().getTextScaleMultiplier();
        int compactLineH = Math.max(1, Math.round(10 * compactTextScale));

        int questDescLineCount = resolvedDescBlocks.isEmpty() ? 0 :
                (net.phoenixvine.wiki.client.rich.WikiRichTextRenderer.measureBlocksHeight(
                        Minecraft.getInstance().font, resolvedDescBlocks, cardW() - QuestTasksScreen.CARD_PAD * 2,
                        compactTextScale, screen.fullscreen.descExpandedKeys) +
                        compactLineH - 1) / compactLineH;

        java.util.List<net.minecraft.util.FormattedCharSequence> questDescLines = java.util.Collections
                .nCopies(questDescLineCount, net.minecraft.util.FormattedCharSequence.EMPTY);
        java.util.List<net.minecraft.util.FormattedCharSequence> descLines = screen.descriptions.buildAllDescLines(
                tasks,
                questDescLines);

        int compactHeaderH = compactHeaderH();
        int fixedH = compactHeaderH + 1 + compactStripH() + 2 + 1 + 18 + screen.fullscreen.previewBlockH();
        int rawDesc = Math.min(descLines.size(), QuestTasksScreen.CARD_MAX_DESC);
        int fittedDesc = Math.max(0, Math.min(rawDesc, ((maxCardH() - compactHeaderH) - fixedH - 9) / compactLineH));
        int descH = fittedDesc > 0 ? 4 + fittedDesc * compactLineH + 6 : (screen.isEditMode ? 24 : 0);

        int pagerStripH = pageCount > 1 ? 17 : 0;
        int cardH = fixedH + descH + (descH > 0 ? 1 : 0) + pagerStripH + (pagerStripH > 0 ? 1 : 0);
        cardH = Math.min(cardH, maxCardH());
        int cardX = (screen.width - cardW()) / 2;
        int cardY = Math.max(10, (screen.height - cardH) / 2);

        if (mx < cardX || mx >= cardX + cardW() || my < cardY || my >= cardY + cardH) {
            if (Minecraft.getInstance() != null) Minecraft.getInstance().setScreen(screen.parent);
            return true;
        }

        if (mx >= cardX + cardW() - 50 && mx < cardX + cardW() - 36 && my >= cardY + 3 && my < cardY + 17) {
            if (screen.playerData != null) {
                screen.playerData.togglePin(screen.node.getId());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2STogglePinPacket(screen.node.getId()));
            }
            return true;
        }

        if (mx >= cardX + cardW() - 34 && mx < cardX + cardW() - 20 && my >= cardY + 3 && my < cardY + 17) {
            if (Minecraft.getInstance() != null) Minecraft.getInstance().setScreen(screen.parent);
            return true;
        }

        if (mx >= cardX + cardW() - 18 && mx < cardX + cardW() - 4 && my >= cardY + 3 && my < cardY + 17) {
            screen.isFullscreen = true;
            return true;
        }

        for (net.phoenixvine.wiki.client.rich.RichSpan.Region r : screen.richRegions) {
            if (r.contains(mx, my)) {
                if (r.span() instanceof net.phoenixvine.wiki.client.rich.RichSpan.Link l) {
                    screen.fullscreen.actions.openLink(l.url());
                    return true;
                }
                if (r.span() instanceof net.phoenixvine.wiki.client.rich.RichSpan.TypewriterReveal reveal) {
                    net.phoenixvine.wiki.client.rich.render.TypewriterSupport.start(reveal.key());
                    return true;
                }
                if (r.span() instanceof net.phoenixvine.wiki.client.rich.RichSpan.CodeCopy cc) {
                    if (Minecraft.getInstance() != null)
                        Minecraft.getInstance().keyboardHandler.setClipboard(cc.code());
                    return true;
                }
                if (r.span() instanceof net.phoenixvine.wiki.client.rich.RichSpan.DetailsToggle dt) {
                    if (!screen.fullscreen.descExpandedKeys.remove(dt.key()))
                        screen.fullscreen.descExpandedKeys.add(dt.key());
                    return true;
                }
                if (r.span() instanceof net.phoenixvine.wiki.client.rich.RichSpan.ChecklistToggle ct) {
                    boolean current = screen.fullscreen.descExpandedKeys.contains("CL1:" + ct.key()) ||
                            !screen.fullscreen.descExpandedKeys.contains("CL0:" + ct.key()) && ct.checkedDefault();
                    boolean next = !current;
                    screen.fullscreen.descExpandedKeys.remove("CL1:" + ct.key());
                    screen.fullscreen.descExpandedKeys.remove("CL0:" + ct.key());
                    screen.fullscreen.descExpandedKeys.add((next ? "CL1:" : "CL0:") + ct.key());
                    return true;
                }
            }
        }

        if (hoveredDescBox && screen.isEditMode && Minecraft.getInstance() != null) {
            Minecraft.getInstance().setScreen(new QuestTextInputScreen(screen, "Description",
                    screen.descriptions.currentFullDescriptionRaw(), 8192,
                    v -> {
                        screen.node.setDescription(Component.literal(v));
                        screen.fullscreen.liveDescOverride = v;
                        screen.fullscreen.richSpansPage = -1;
                        QuestFileSaver.saveOneQuestToDisk(screen.node);
                        LangSyncScheduler.markDirty();
                    }));
            return true;
        }

        if (screen.fullscreen.hoveredTask != null) {
            if (screen.fullscreen.actions.tryCompleteCheckmark(screen.fullscreen.hoveredTask) ||
                    (btn == 0 && screen.fullscreen.actions.trySubmitItemTask(screen.fullscreen.hoveredTask)))
                return true;
            if (net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat
                    .canOpenForTask(screen.fullscreen.hoveredTask)) {
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.openForTask(
                        screen.fullscreen.hoveredTask,
                        screen);
                return true;
            }
            if (screen.fullscreen.actions.tryOpenArchiveEntry(screen.fullscreen.hoveredTask)) return true;
            ItemStack icon = screen.present.getTaskIcon(screen.fullscreen.hoveredTask);
            if (!icon.isEmpty()) screen.fullscreen.actions.tryOpenInRecipeViewer(icon);
            else screen.isFullscreen = true;
            return true;
        }
        if (screen.fullscreen.hoveredReward != null) {
            if (screen.fullscreen.actions.tryClaimReward(screen.fullscreen.hoveredRewardIndex)) return true;
            if (screen.fullscreen.hoveredReward instanceof QuestReward.ItemReward ir) {
                screen.fullscreen.actions.tryOpenInRecipeViewer(new ItemStack(ir.getItem(), ir.getCount()));
            }
            return true;
        }

        int footerY = cardY + cardH - 18;
        if (screen.fullscreen.actions.clickEmergencyButton(cardX + 4, footerY + 1, 22, 16, mx, my)) return true;
        if (screen.fullscreen.actions.clickVoiceButton(cardX + 4 + screen.voiceOffset(), footerY + 1, 22, 16, mx, my))
            return true;
        if (screen.fullscreen.actions.clickLinkButton(cardX + 4 + screen.linkOffset(), footerY + 1, 22, 16, mx, my))
            return true;
        if (my >= footerY && my < footerY + 18) {
            QuestState state = screen.playerData != null ?
                    screen.playerData.getQuestState(screen.node.getId(), QuestState.LOCKED) :
                    QuestState.LOCKED;
            if (state == QuestState.COMPLETED && !screen.rewardsClaimed() && !rewards.isEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor
                        .sendToServer(new C2SClaimQuestRewardPacket(screen.node.getId(), -1));
            }
        }
        return true;
    }

    private boolean hoveredDescBox = false;
}

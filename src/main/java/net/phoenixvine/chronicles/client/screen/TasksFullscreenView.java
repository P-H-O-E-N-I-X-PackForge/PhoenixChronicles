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

import java.util.ArrayList;
import java.util.List;

final class TasksFullscreenView {

    private final QuestTasksScreen screen;

    TasksFullscreenView(QuestTasksScreen screen) {
        this.screen = screen;
        this.actions = new TaskActions(screen);
    }

    void drawLinkButton(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? 0xFF1E2A3A : 0xFF141C28);
        g.fill(x, y, x + w, y + 1, 0xFF3A6A9A);
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§b🔗", x + w / 2, y + (h - 8) / 2,
                screen.C_TEXT);
        if (hov) screen.hoveredHeaderTooltip = "Copy a link to this quest to share in chat";
    }

    void drawVoiceButton(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        if (!screen.voiceShown()) return;
        boolean playing = net.phoenixvine.chronicles.client.audio.ChroniclesAudio.isVoicePlaying();
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, playing ? 0xFF1A3A2A : hov ? 0xFF1E2A3A : 0xFF141C28);
        g.fill(x, y, x + w, y + 1, playing ? screen.C_DONE : 0xFF3A6A9A);
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, playing ? "§a■" : "§b♪", x + w / 2,
                y + (h - 8) / 2, screen.C_TEXT);
        if (hov) screen.hoveredHeaderTooltip = playing ? "Stop narration" : "Play narration";
    }

    private int headerTitleMaxW() {
        int pinX = screen.width - 20;
        int editX = pinX - 18;
        int fsX = editX - 20;
        int usesX = fsX - 20;
        return usesX - 32;
    }

    int headerH() {
        int lines = screen.descriptions.wrapSubtitle(screen.node.getSubtitle(), headerTitleMaxW()).size();
        return lines <= 1 ? QuestTasksScreen.HEADER_H_BASE :
                QuestTasksScreen.HEADER_H_BASE + (lines - 1) * QuestTasksScreen.SUBTITLE_LINE_H;
    }

    int previewBlockH() {
        return screen.phantasiaPreview != null ? QuestTasksScreen.PREVIEW_H_COMPACT + 1 : 0;
    }

    void renderIconStrip(GuiGraphics g, int cardX, int cy, int mx, int my, List<QuestTask> tasks,
                         List<QuestReward> rewards) {
        int sz = QuestTasksScreen.ICON_SZ;
        int gap = QuestTasksScreen.STRIP_GAP;
        int left = cardX + QuestTasksScreen.CARD_PAD + 2;
        int right = cardX + screen.compact.cardW() - QuestTasksScreen.CARD_PAD;
        QuestTasksScreen.StripLayout layout = screen.compact.stripLayout(right - left, tasks.size(), rewards.size());

        for (int ti = 0; ti < tasks.size(); ti++) {
            QuestTask task = tasks.get(ti);
            int ix = screen.compact.stripTaskX(layout, left, ti);
            int iy = cy + 4 + screen.compact.stripTaskRow(layout, ti) * (sz + gap);
            boolean done = screen.isTaskDone(task);
            boolean hov = mx >= ix && mx < ix + sz && my >= iy && my < iy + sz;
            int border = done ? screen.C_DONE : (task.isOptional() ? screen.C_TEXT_FAINT : screen.C_ACTIVE);
            int bg = done ? 0xFF0A1A0E : 0xFF0F0F18;
            if (hov) {
                hoveredTask = task;
                hoveredSlotX = ix;
                hoveredSlotY = iy;
            }
            drawIconSlot(g, ix, iy, sz, bg, border, hov);
            ItemStack icon = screen.present.getTaskIcon(task);
            int off = (sz - 16) / 2;
            if (!icon.isEmpty()) {
                g.renderItem(icon, ix + off, iy + off);
                g.renderItemDecorations(Minecraft.getInstance().font, icon, ix + off, iy + off);
                if (done) g.fill(ix, iy, ix + sz, iy + sz, 0x5500CC55);
            } else {
                ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, done ? "§a✔" : "§c✗", ix + sz / 2,
                        iy + sz / 2 - 4,
                        0xFFFFFFFF);
            }
            if (done) {
                g.fill(ix + sz - 7, iy + sz - 8, ix + sz, iy + sz, 0xFF0A2210);
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§a✔", ix + sz - 7, iy + sz - 8, 0xFFFFFFFF,
                        false);
            }
        }

        boolean claimed = screen.rewardsClaimed();
        for (int ri = 0; ri < rewards.size(); ri++) {
            int rix = layout.single() ? right - sz - ri * (sz + gap) : left + (ri % layout.perRow()) * (sz + gap);
            int iy = cy + 4 + screen.compact.stripRewardRow(layout, ri) * (sz + gap);
            QuestReward reward = rewards.get(ri);
            QuestReward display = screen.present.displayRewardFor(ri, reward);
            boolean picked = screen.isSlotPicked(ri, claimed);
            boolean hov = mx >= rix && mx < rix + sz && my >= iy && my < iy + sz;
            int border = picked ? screen.C_DONE : screen.C_TEXT_FAINT;
            int bg = picked ? 0xFF1A140A : 0xFF0F0F18;
            if (hov) {
                hoveredReward = reward;
                hoveredRewardIndex = ri;
                hoveredSlotX = rix;
                hoveredSlotY = iy;
            }
            drawIconSlot(g, rix, iy, sz, bg, border, hov);
            int off = (sz - 16) / 2;
            ItemStack shownStack = display instanceof QuestReward.ItemReward ir0 ?
                    screen.present.shownRewardStack(ir0) :
                    ItemStack.EMPTY;
            if (!shownStack.isEmpty()) {
                g.renderItem(shownStack, rix + off, iy + off);
                g.renderItemDecorations(Minecraft.getInstance().font, shownStack, rix + off, iy + off);
                if (picked) g.fill(rix, iy, rix + sz, iy + sz, 0x55CC8800);
            } else {
                ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font,
                        "§7" + screen.present.rewardGlyph(display), rix + sz / 2,
                        iy + sz / 2 - 4,
                        screen.C_TEXT_DIM);
                if (picked) g.fill(rix, iy, rix + sz, iy + sz, 0x55CC8800);
            }
            if (picked) {
                g.fill(rix + sz - 7, iy + sz - 8, rix + sz, iy + sz, 0xFF1A1000);
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§6✔", rix + sz - 7, iy + sz - 8,
                        0xFFFFFFFF, false);
            }
        }
    }

    void drawEmergencyButton(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        if (!screen.emergencyShown()) return;
        boolean used = screen.emergencyUsedUp();
        long cooldownMs = actions.emergencyCooldownRemainingMs();
        boolean blocked = used || cooldownMs > 0;
        boolean armed = System.currentTimeMillis() < emergencyArmedUntilMs;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        int fill = blocked ? 0xFF1A1A1A : armed ? 0xFF4A3A1A : hov ? 0xFF3A3220 : 0xFF2A2418;
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, blocked ? 0xFF333333 : 0xFFB08A2E);
        String glyph = cooldownMs > 0 ?
                "§7" + net.phoenixvine.chronicles.client.util.ClientEmergencyState.compactDuration(cooldownMs) :
                blocked ? "§8⚠" : armed ? "§e⚠?" : "§6⚠";
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, glyph, x + w / 2, y + (h - 8) / 2,
                screen.C_TEXT);
        if (hov) {
            screen.hoveredHeaderTooltip = used ? "Emergency items already used" :
                    cooldownMs > 0 ? "Emergency items available again in " +
                            net.phoenixvine.chronicles.common.tracker.QuestProgressTracker.formatDuration(cooldownMs) :
                            armed ? "Click again to confirm" :
                                    net.phoenixvine.chronicles.client.util.ClientEmergencyState
                                            .isRepeatable(screen.node.getId()) ?
                                                    "Lost a required item? Get fallback items" :
                                                    "Lost a required item? Get fallback items (one use)";
        }
    }

    void renderFullscreen(GuiGraphics g, int mx, int my, float partial) {
        hoveredTaskFs = null;
        hoveredRewardFs = null;

        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        g.fill(0, 0, screen.width, screen.height, 0xFF000000);
        renderHeader(g, mx, my);
        renderRequirementsBar(g, mx, my);

        int[] pw = fullscreenPanelWidths();
        int inspW = pw[0];
        int rewardW = pw[1];
        int contentTop = headerH() + reqBarH() + QuestTasksScreen.MARGIN;
        int contentRight = screen.width - inspW - rewardW - QuestTasksScreen.MARGIN * 3;
        int contentH = screen.height - contentTop - QuestTasksScreen.FOOTER_H - QuestTasksScreen.MARGIN;

        g.fill(QuestTasksScreen.MARGIN, contentTop, contentRight, contentTop + contentH, screen.C_PANEL);
        screen.drawBorder(g, QuestTasksScreen.MARGIN, contentTop, contentRight - QuestTasksScreen.MARGIN, contentH);
        renderContent(g, QuestTasksScreen.MARGIN + 6, contentTop + 6, contentRight - QuestTasksScreen.MARGIN - 12,
                contentH - 12, mx, my, partial);

        int inspX = contentRight + QuestTasksScreen.MARGIN;
        renderInspector(g, inspX, contentTop, inspW, contentH, mx, my);

        int rewardX = inspX + inspW + QuestTasksScreen.MARGIN;
        renderRewardsPanel(g, rewardX, contentTop, rewardW, contentH, mx, my);

        renderPanelDividers(g, contentRight, inspX + inspW, contentTop, contentH, mx, my);

        renderFooter(g, mx, my);

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);
        if (hoveredStripTask != null) {
            g.renderComponentTooltip(Minecraft.getInstance().font, screen.present.buildTaskTooltip(hoveredStripTask),
                    mx, my);
        } else if (hoveredStripReward != null) {
            g.renderComponentTooltip(Minecraft.getInstance().font,
                    screen.present.buildRewardTooltip(hoveredStripReward), mx, my);
        } else if (hoveredTaskFs != null) {
            g.renderComponentTooltip(Minecraft.getInstance().font, screen.present.buildTaskTooltip(hoveredTaskFs), mx,
                    my);
        } else if (hoveredRewardFs != null) {
            g.renderComponentTooltip(Minecraft.getInstance().font, screen.present.buildRewardTooltip(hoveredRewardFs),
                    mx, my);
        }
        g.pose().popPose();
    }

    private void renderHeader(GuiGraphics g, int mx, int my) {
        int headerH = headerH();
        g.fill(0, 0, screen.width, headerH, screen.C_HEADER);
        g.fill(0, headerH - 1, screen.width, headerH, screen.C_BORDER);

        boolean backHov = mx >= 4 && mx < 20 && my >= 6 && my < 22;
        if (backHov) {
            g.fill(4, 6, 20, 22, 0x22FFFFFF);
            screen.hoveredHeaderTooltip = "Close";
        }
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§7←", 12, 10, screen.C_TEXT_DIM);

        boolean pinned = screen.playerData != null && screen.playerData.isPinned(screen.node.getId());
        int pinX = screen.width - 20;

        int editX = pinX - (screen.isEditMode ? 18 : 0);
        int fsX = editX - 20;
        int usesX = fsX - 20;
        int titleMaxW = headerTitleMaxW();

        float headerTextScale = QuestChroniclesSettings.get().getTextScaleMultiplier();

        float titleAvailPreScale = titleMaxW / headerTextScale;
        String titleStr = (screen.node.isOptional() ? "§d[Optional] §f" : "") + screen.content.title().getString();
        if (net.phoenixvine.chronicles.client.util.EffectText.width(Minecraft.getInstance().font, titleStr) >
                titleAvailPreScale)
            titleStr = net.phoenixvine.chronicles.client.util.EffectText.cut(Minecraft.getInstance().font, titleStr,
                    (int) titleAvailPreScale - Minecraft.getInstance().font.width("…")) + "…";
        ChroniclesUIKit.drawScaledString(g, Minecraft.getInstance().font, "§f" + titleStr, 28, 10, screen.C_TEXT,
                headerTextScale);

        java.util.List<String> subtitleLines = screen.descriptions.wrapSubtitle(screen.node.getSubtitle(),
                titleMaxW);
        for (int i = 0; i < subtitleLines.size(); i++) {
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, subtitleLines.get(i), 28,
                    24 + i * QuestTasksScreen.SUBTITLE_LINE_H, screen.C_TEXT_DIM, false);
        }

        boolean usesHasAny = !screen.node.getPrerequisites().isEmpty() || !screen.node.getChildren().isEmpty();
        boolean usesHov = usesHasAny && mx >= usesX && mx < usesX + 16 && my >= 6 && my < 22;
        if (usesHov || usesPopupOpen) g.fill(usesX, 6, usesX + 16, 22, 0x22FFFFFF);
        if (usesHov) screen.hoveredHeaderTooltip = "Show prerequisites/unlocks";

        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§b≡", usesX + 8, 10,
                !usesHasAny ? screen.C_TEXT_FAINT : (usesHov || usesPopupOpen) ? 0xFF55CCFF : screen.C_TEXT_DIM);

        boolean fsToggleHov = mx >= fsX && mx < fsX + 16 && my >= 6 && my < 22;
        if (fsToggleHov) {
            g.fill(fsX, 6, fsX + 16, 22, 0x22FFFFFF);
            screen.hoveredHeaderTooltip = "Shrink to compact view";
        }
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§d[-]", fsX + 8, 10, 0xFFAA44FF);

        if (screen.isEditMode) {
            boolean editHov = mx >= editX && mx < editX + 14 && my >= 6 && my < 22;
            if (editHov) {
                g.fill(editX, 6, editX + 14, 22, 0x22FFFFFF);
                screen.hoveredHeaderTooltip = "Edit quest";
            }
            ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, editHov ? "§e✎" : "§8✎", editX + 7, 10,
                    editHov ? 0xFFFFDD44 : screen.C_TEXT_FAINT);
        }

        boolean pinHov = mx >= pinX && mx < screen.width - 4 && my >= 6 && my < 22;
        if (pinHov) {
            g.fill(pinX, 6, screen.width - 4, 22, 0x22FFFFFF);
            screen.hoveredHeaderTooltip = pinned ? "Unpin quest" : "Pin quest";
        }
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, pinned ? "§d📌" : "§8📌", screen.width - 12,
                10,
                pinned ? 0xFFAA44FF : screen.C_TEXT_FAINT);

        if (usesPopupOpen) screen.popups.renderUsesPopup(g, usesX, mx, my);
    }

    int reqBarH() {
        List<QuestTask> tasks = screen.content.effectiveTasks();
        List<QuestReward> rewards = screen.content.effectiveRewards();
        if (tasks.isEmpty() && rewards.isEmpty()) return 1;
        return screen.compact.stripHeight(screen.compact.stripLayout(screen.width - QuestTasksScreen.MARGIN * 2 - 4,
                tasks.size(), rewards.size()));
    }

    private void renderRequirementsBar(GuiGraphics g, int mx, int my) {
        hoveredStripTask = null;
        hoveredStripReward = null;
        hoveredStripRewardIdx = -1;

        int barH = reqBarH();
        int headerH = headerH();
        g.fill(0, headerH, screen.width, headerH + barH, screen.C_PANEL);
        g.fill(0, headerH, screen.width, headerH + 1, screen.C_BORDER);
        g.fill(0, headerH + barH - 1, screen.width, headerH + barH, screen.C_BORDER);
        if (barH <= 1) return;

        int sz = QuestTasksScreen.ICON_SZ;
        int gap = QuestTasksScreen.STRIP_GAP;
        int stripLeft = QuestTasksScreen.MARGIN + 2;
        int stripRight = screen.width - QuestTasksScreen.MARGIN - 2;

        List<QuestTask> tasks = screen.content.effectiveTasks();
        List<QuestReward> rewards = screen.content.effectiveRewards();
        QuestTasksScreen.StripLayout layout = screen.compact.stripLayout(stripRight - stripLeft, tasks.size(),
                rewards.size());

        for (int ti = 0; ti < tasks.size(); ti++) {
            QuestTask task = tasks.get(ti);
            int iconX = screen.compact.stripTaskX(layout, stripLeft, ti);
            int iconY = headerH + 4 + screen.compact.stripTaskRow(layout, ti) * (sz + gap);
            boolean done = screen.isTaskDone(task);
            boolean hov = mx >= iconX && mx < iconX + sz && my >= iconY && my < iconY + sz;
            int border = done ? screen.C_DONE : (task.isOptional() ? screen.C_TEXT_FAINT : screen.C_ACTIVE);
            int bg = done ? 0xFF0A1A0E : 0xFF0F0F18;

            if (hov) {
                hoveredStripTask = task;
                hoveredStripX = iconX;
                hoveredStripY = iconY;
            }

            drawIconSlot(g, iconX, iconY, sz, bg, border, hov);

            ItemStack icon = screen.present.getTaskIcon(task);
            int off = (sz - 16) / 2;
            if (!icon.isEmpty()) {
                g.renderItem(icon, iconX + off, iconY + off);

                g.renderItemDecorations(Minecraft.getInstance().font, icon, iconX + off, iconY + off);
                if (done) g.fill(iconX, iconY, iconX + sz, iconY + sz, 0x5500CC55);
            } else {
                ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, done ? "§a✔" : "§c✗",
                        iconX + sz / 2, iconY + sz / 2 - 4,
                        0xFFFFFFFF);
            }

            if (done) {
                g.fill(iconX + sz - 7, iconY + sz - 8, iconX + sz, iconY + sz, 0xFF0A2210);
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§a✔", iconX + sz - 7, iconY + sz - 8,
                        0xFFFFFFFF, false);
            }
        }

        boolean claimed = screen.rewardsClaimed();
        for (int ri = 0; ri < rewards.size(); ri++) {
            int rIconX = layout.single() ?
                    stripRight - rewards.size() * sz - Math.max(0, rewards.size() - 1) * gap + ri * (sz + gap) :
                    stripLeft + (ri % layout.perRow()) * (sz + gap);
            int iconY = headerH + 4 + screen.compact.stripRewardRow(layout, ri) * (sz + gap);
            QuestReward reward = rewards.get(ri);
            QuestReward display = screen.present.displayRewardFor(ri, reward);
            boolean picked = screen.isSlotPicked(ri, claimed);
            boolean hov = mx >= rIconX && mx < rIconX + sz && my >= iconY && my < iconY + sz;
            int border = picked ? screen.C_DONE : screen.C_TEXT_FAINT;
            int bg = picked ? 0xFF1A140A : 0xFF0F0F18;

            if (hov) {
                hoveredStripReward = reward;
                hoveredStripRewardIdx = ri;
                hoveredStripX = rIconX;
                hoveredStripY = iconY;
            }

            drawIconSlot(g, rIconX, iconY, sz, bg, border, hov);

            int off = (sz - 16) / 2;
            ItemStack shownStack = display instanceof QuestReward.ItemReward ir0 ?
                    screen.present.shownRewardStack(ir0) :
                    ItemStack.EMPTY;
            if (!shownStack.isEmpty()) {
                g.renderItem(shownStack, rIconX + off, iconY + off);
                g.renderItemDecorations(Minecraft.getInstance().font, shownStack, rIconX + off, iconY + off);
                if (picked) g.fill(rIconX, iconY, rIconX + sz, iconY + sz, 0x55CC8800);
            } else {
                ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font,
                        "§7" + screen.present.rewardGlyph(display), rIconX + sz / 2,
                        iconY + sz / 2 - 4,
                        screen.C_TEXT_DIM);
                if (picked) g.fill(rIconX, iconY, rIconX + sz, iconY + sz, 0x55CC8800);
            }

            if (picked) {
                g.fill(rIconX + sz - 7, iconY + sz - 8, rIconX + sz, iconY + sz, 0xFF1A1000);
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§6✔", rIconX + sz - 7, iconY + sz - 8,
                        0xFFFFFFFF, false);
            }
        }
    }

    private void drawIconSlot(GuiGraphics g, int x, int y, int sz, int bg, int border, boolean hov) {
        g.fill(x, y, x + sz, y + sz, bg);
        if (hov) g.fill(x, y, x + sz, y + sz, 0x22FFFFFF);
        g.fill(x, y, x + sz, y + 1, border);
        g.fill(x, y + sz - 1, x + sz, y + sz, border);
        g.fill(x, y, x + 1, y + sz, border);
        g.fill(x + sz - 1, y, x + sz, y + sz, border);
    }

    private int renderAddBox(GuiGraphics g, int x, int y, int w, String label, int mx, int my) {
        int boxH = 16;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + boxH;
        int border = hov ? screen.C_ACTIVE : screen.C_BORDER;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + boxH - 1, x + w, y + boxH, border);
        g.fill(x, y, x + 1, y + boxH, border);
        g.fill(x + w - 1, y, x + w, y + boxH, border);
        if (hov) g.fill(x + 1, y + 1, x + w - 1, y + boxH - 1, 0x14FFFFFF);
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, (hov ? "§f" : "§8") + label, x + w / 2,
                y + 4,
                hov ? screen.C_TEXT : screen.C_TEXT_FAINT);
        return boxH;
    }

    private void renderContent(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float partial) {
        prereqRowRects.clear();
        prereqRowTargets.clear();
        addTaskBoxRect = null;
        addRewardBoxRect = null;

        fsDescBoxX = x - 6;
        fsDescBoxY = y - 6;
        fsDescBoxW = w + 12;
        fsDescBoxH = h + 12;
        hoveredFsDescBox = screen.isEditMode && mx >= fsDescBoxX && mx < fsDescBoxX + fsDescBoxW && my >= fsDescBoxY &&
                my < fsDescBoxY + fsDescBoxH;
        if (screen.isEditMode) {
            int dashColor = hoveredFsDescBox ? screen.C_ACTIVE : screen.C_TEXT_FAINT;
            for (int dx = fsDescBoxX; dx < fsDescBoxX + fsDescBoxW; dx += 4) {
                g.fill(dx, fsDescBoxY, dx + 2, fsDescBoxY + 1, dashColor);
                g.fill(dx, fsDescBoxY + fsDescBoxH - 1, dx + 2, fsDescBoxY + fsDescBoxH, dashColor);
            }
            String hint = "§7✎ click to edit";
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, hint,
                    fsDescBoxX + fsDescBoxW - Minecraft.getInstance().font.width(hint) - 2, fsDescBoxY - 9,
                    hoveredFsDescBox ? screen.C_ACTIVE : screen.C_TEXT_FAINT, false);
        }

        String descRawFull = screen.descriptions.currentDisplayDescriptionText();
        java.util.List<String> descPages = screen.descriptions.splitDescPages(descRawFull);
        String descRaw = screen.descriptions.currentDescriptionPageText(descRawFull);

        int pagerH = descPages.size() > 1 ? 17 : 0;
        int textBottom = y + h - pagerH;

        g.enableScissor(x - 8, y - 8, x + w + 8, textBottom + 8);

        if (richSpansPage != screen.descPage || !descRaw.equals(descBlocksSourceText)) {
            descBlocks = descRaw.isEmpty() ? java.util.List.of() :
                    net.phoenixvine.chronicles.client.rich.ChroniclesMarkdown.parse(descRaw);
            richSpansPage = screen.descPage;
            descBlocksSourceText = descRaw;
        }

        java.util.List<net.phoenixvine.wiki.client.rich.RichBlock> resolvedDescBlocks = screen.descriptions
                .resolveConditionals(
                        descBlocks);
        if (screen.isEditMode && descRaw.isEmpty() && descBlocks.isEmpty()) {
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8Click to add a description", x, y,
                    screen.C_TEXT_FAINT, false);
        }

        float textScale = QuestChroniclesSettings.get().getTextScaleMultiplier();

        int descContentH = net.phoenixvine.wiki.client.rich.WikiRichTextRenderer.measureBlocksHeight(
                Minecraft.getInstance().font,
                resolvedDescBlocks, w, textScale, descExpandedKeys);
        screen.fsDescMaxScrollY = Math.max(0, descContentH - (textBottom - y));
        if (screen.descScrollY > screen.fsDescMaxScrollY) screen.descScrollY = screen.fsDescMaxScrollY;

        screen.richRegions = net.phoenixvine.wiki.client.rich.WikiRichTextRenderer.renderBlocks(
                g, Minecraft.getInstance().font, resolvedDescBlocks, x, y, w, screen.descScrollY, y, textBottom,
                textScale, screen.C_ACTIVE,
                descExpandedKeys);

        int ly = y + descContentH - screen.descScrollY;

        if (screen.phantasiaPreview != null) {
            if (ly > y) ly += 8;
            int pvSz = 64;
            if (ly + pvSz >= y - 10 && ly < textBottom) {
                g.fill(x, ly, x + pvSz, ly + pvSz, 0xFF0A0A10);
                screen.drawBorder(g, x, ly, pvSz, pvSz);
                screen.previewX = x + 1;
                screen.previewY = ly + 1;
                screen.previewW = pvSz - 2;
                screen.previewH = pvSz - 2;
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.tickPreview(screen.phantasiaPreview);
                net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.renderPreview(screen.phantasiaPreview,
                        g,
                        screen.previewX, screen.previewY, screen.previewW, screen.previewH, partial);
            }
            ly += pvSz + 8;
        }

        g.disableScissor();

        if (pagerH > 0) screen.descriptions.renderDescPager(g, x, textBottom, w, pagerH, mx, my, descPages.size());
    }

    private QuestTasksScreen.InspTab[] visibleInspTabs() {
        return screen.isEditMode ?
                new QuestTasksScreen.InspTab[] { QuestTasksScreen.InspTab.INFO, QuestTasksScreen.InspTab.TASKS } :
                new QuestTasksScreen.InspTab[] { QuestTasksScreen.InspTab.TASKS };
    }

    private String inspTabLabel(QuestTasksScreen.InspTab t) {
        return switch (t) {
            case INFO -> "Info";
            case TASKS -> "Tasks";
        };
    }

    private void renderInspector(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, screen.C_PANEL);
        screen.drawBorder(g, x, y, w, h);

        QuestTasksScreen.InspTab[] tabs = visibleInspTabs();
        if (!java.util.Arrays.asList(tabs).contains(inspectorTab)) inspectorTab = tabs[tabs.length - 1];

        int cY;
        if (tabs.length > 1) {
            int tabX = x + 6;
            int tabY = y + 4;
            for (QuestTasksScreen.InspTab t : tabs) {
                String label = inspTabLabel(t);
                int tabW = Minecraft.getInstance().font.width(label) + 6;
                boolean active = inspectorTab == t;
                boolean hov = mx >= tabX && mx < tabX + tabW && my >= tabY && my < tabY + QuestTasksScreen.INSP_TAB_H;
                if (active) {
                    g.fill(tabX, tabY, tabX + tabW, tabY + QuestTasksScreen.INSP_TAB_H, 0xFF1A1A26);
                    g.fill(tabX, tabY + QuestTasksScreen.INSP_TAB_H - 1, tabX + tabW,
                            tabY + QuestTasksScreen.INSP_TAB_H, screen.C_DONE);
                } else if (hov) {
                    g.fill(tabX, tabY, tabX + tabW, tabY + QuestTasksScreen.INSP_TAB_H, 0x22FFFFFF);
                }
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, (active ? "§a" : "§8") + label, tabX + 3,
                        tabY + 3, screen.C_TEXT_DIM,
                        false);
                tabX += tabW + 2;
            }
            cY = y + QuestTasksScreen.INSP_TAB_H + 6;
        } else {
            cY = y + 6;
        }
        int cH = (y + h - 6) - cY;

        int maxScroll = Math.max(0, inspectorContentH - cH + 8);
        screen.inspectorScrollY = Math.max(0, Math.min(screen.inspectorScrollY, maxScroll));

        g.enableScissor(x, cY, x + w, cY + cH);
        switch (inspectorTab) {
            case INFO -> renderInfoTab(g, x, cY, w, cH);
            case TASKS -> renderTasksTab(g, x, cY, w, cH, mx, my);
        }
        g.disableScissor();
        g.disableScissor();
    }

    private void renderInfoTab(GuiGraphics g, int x, int y, int w, int h) {
        int m = 6;
        int cy = y - screen.inspectorScrollY + 4;
        int viewBot = y + h;
        if (screen.lineFullyVisible(cy, y, viewBot))
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8Chapter:", x + m, cy, screen.C_TEXT_FAINT,
                    false);
        if (screen.lineFullyVisible(cy + 10, y, viewBot))
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font,
                    "§7" + (screen.node.getChapter() != null ? screen.node.getChapter() : "(none)"),
                    x + m, cy + 10,
                    screen.C_TEXT, false);
        cy += 24;
        if (screen.lineFullyVisible(cy, y, viewBot))
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8Visibility:", x + m, cy, screen.C_TEXT_FAINT,
                    false);
        if (screen.lineFullyVisible(cy + 10, y, viewBot))
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font,
                    "§7" + (screen.node.getVisibility() != null ? screen.node.getVisibility() : "NORMAL"),
                    x + m,
                    cy + 10, screen.C_TEXT, false);
        cy += 24;
        if (screen.lineFullyVisible(cy, y, viewBot))
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8ID:", x + m, cy, screen.C_TEXT_FAINT, false);
        if (screen.lineFullyVisible(cy + 10, y, viewBot))
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font,
                    "§7" + (screen.node.getId() != null ? screen.node.getId() : "unknown"), x + m,
                    cy + 10, screen.C_TEXT,
                    false);
        cy += 24;

        for (QuestTask task : screen.content.effectiveTasks()) {
            if (!(task instanceof InfoTask info)) continue;
            String body = info.getBody();
            if (body == null || body.isBlank()) continue;
            if (cy > y + h) break;
            if (cy >= y) g.fill(x + m, cy, x + w - m, cy + 1, screen.C_BORDER);
            cy += 6;
            for (var line : screen.descriptions.splitRespectingNewlines(Minecraft.getInstance().font, body,
                    w - m * 2)) {
                if (cy > y + h) break;
                if (screen.lineFullyVisible(cy, y, viewBot))
                    ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, line, x + m, cy, screen.C_TEXT_DIM,
                            false);
                cy += 10;
            }
            cy += 4;
        }
    }

    private void renderTasksTab(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        List<QuestTask> tasks = screen.content.effectiveTasks();
        List<QuestNode> prereqs = screen.node.getPrerequisites();
        List<QuestNode> dependents = screen.dependentsOf(screen.node);
        int m = 6;
        int cy = y - screen.inspectorScrollY + 4;
        int viewBot = y + h;
        if (tasks.isEmpty() && prereqs.isEmpty() && dependents.isEmpty()) {
            if (screen.lineFullyVisible(cy, y, viewBot))
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8(none)", x + m, cy, screen.C_TEXT_FAINT,
                        false);
            if (screen.isEditMode) {
                int addY = screen.lineFullyVisible(cy, y, viewBot) ? cy + 12 : cy;
                if (screen.lineFullyVisible(addY, y, viewBot)) {
                    int boxH = renderAddBox(g, x + m, addY, w - m * 2, "+ Add Task", mx, my);
                    addTaskBoxRect = new int[] { x + m, addY, w - m * 2, boxH };
                }
                cy = addY + 16;
            }
            inspectorContentH = Math.max(0, cy - (y - screen.inspectorScrollY + 4));
            return;
        }
        for (QuestTask task : tasks) {
            if (task instanceof InfoTask) continue;

            String detail = screen.present.getTaskDetail(task);
            int rowH = (detail != null ? 22 : 14) + 3 + 5;
            if (cy < y) {
                cy += rowH;
                continue;
            }
            int rowEndY = screen.renderRichTaskRow(g, x + m, cy, w - m * 2, task, mx, my);
            if (mx >= x + m && mx < x + w - m && my >= cy && my < rowEndY - 5) {
                hoveredTaskFs = task;
                hoveredFsX = mx;
                hoveredFsY = my;
            }
            cy = rowEndY;
        }

        if (screen.isEditMode) {
            cy += 3;
            if (screen.lineFullyVisible(cy, y, viewBot)) {
                int boxH = renderAddBox(g, x + m, cy, w - m * 2, "+ Add Task", mx, my);
                addTaskBoxRect = new int[] { x + m, cy, w - m * 2, boxH };
            }
            cy += 16 + 3;
        }

        if (!prereqs.isEmpty()) {
            cy += 6;
            if (screen.lineFullyVisible(cy, y, viewBot)) g.fill(x + m, cy, x + w - m, cy + 1, screen.C_BORDER);
            cy += 8;
            if (screen.lineFullyVisible(cy, y, viewBot))
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8PREREQUISITES:", x + m, cy,
                        screen.C_TEXT_FAINT, false);
            cy += 12;
            for (QuestNode req : prereqs) {
                QuestState state = screen.playerData != null ?
                        screen.playerData.getQuestState(req.getId(), QuestState.LOCKED) :
                        QuestState.LOCKED;
                String reqTitle = req.getTitle().getString();
                int titleMaxW = w - m * 2 - 12;
                if (net.phoenixvine.chronicles.client.util.EffectText.width(Minecraft.getInstance().font, reqTitle) >
                        titleMaxW)
                    reqTitle = net.phoenixvine.chronicles.client.util.EffectText.cut(Minecraft.getInstance().font,
                            reqTitle,
                            Math.max(0, titleMaxW - Minecraft.getInstance().font.width("…"))) + "…";
                boolean reqHov = mx >= x + m && mx < x + w - m && my >= cy && my < cy + 10;
                if (screen.lineFullyVisible(cy, y, viewBot)) {
                    if (reqHov) g.fill(x + m - 2, cy - 1, x + w - m, cy + 9, 0x22FFFFFF);
                    ChroniclesUIKit.drawString(g, Minecraft.getInstance().font,
                            (state == QuestState.COMPLETED ? "§a●" : "§8○") + " " +
                                    (reqHov ? "§f" : "§7") + reqTitle,
                            x + m, cy, reqHov ? screen.C_TEXT : screen.C_TEXT_DIM, false);
                    prereqRowRects.add(new int[] { x + m, cy - 1, w - m * 2, 10 });
                    prereqRowTargets.add(req);
                }
                cy += 10;
            }
        }

        if (!dependents.isEmpty()) {
            cy += 6;
            if (screen.lineFullyVisible(cy, y, viewBot)) g.fill(x + m, cy, x + w - m, cy + 1, screen.C_BORDER);
            cy += 8;
            if (screen.lineFullyVisible(cy, y, viewBot))
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8UNLOCKS:", x + m, cy,
                        screen.C_TEXT_FAINT, false);
            cy += 12;
            for (QuestNode dep : dependents) {
                QuestState state = screen.playerData != null ?
                        screen.playerData.getQuestState(dep.getId(), QuestState.LOCKED) :
                        QuestState.LOCKED;
                String depTitle = dep.getTitle().getString();
                int titleMaxW = w - m * 2 - 12;
                if (net.phoenixvine.chronicles.client.util.EffectText.width(Minecraft.getInstance().font, depTitle) >
                        titleMaxW)
                    depTitle = net.phoenixvine.chronicles.client.util.EffectText.cut(Minecraft.getInstance().font,
                            depTitle,
                            Math.max(0, titleMaxW - Minecraft.getInstance().font.width("…"))) + "…";
                boolean depHov = mx >= x + m && mx < x + w - m && my >= cy && my < cy + 10;
                if (screen.lineFullyVisible(cy, y, viewBot)) {
                    if (depHov) g.fill(x + m - 2, cy - 1, x + w - m, cy + 9, 0x22FFFFFF);
                    ChroniclesUIKit.drawString(g, Minecraft.getInstance().font,
                            (state == QuestState.COMPLETED ? "§a●" : "§8○") + " " +
                                    (depHov ? "§f" : "§7") + depTitle,
                            x + m, cy, depHov ? screen.C_TEXT : screen.C_TEXT_DIM, false);
                    prereqRowRects.add(new int[] { x + m, cy - 1, w - m * 2, 10 });
                    prereqRowTargets.add(dep);
                }
                cy += 10;
            }
        }
        inspectorContentH = (cy - (y - screen.inspectorScrollY + 4));
    }

    private void renderRewardsPanel(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        g.enableScissor(x, y, x + w, y + h);
        g.fill(x, y, x + w, y + h, screen.C_PANEL);
        screen.drawBorder(g, x, y, w, h);

        int m = 6;
        ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8Rewards", x + m, y + 5, screen.C_TEXT_FAINT,
                false);
        g.fill(x + m, y + 15, x + w - m, y + 16, screen.C_BORDER);

        List<QuestReward> rewards = screen.content.effectiveRewards();
        int cy = y + 21;
        if (rewards.isEmpty()) {
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8(none)", x + m, cy, screen.C_TEXT_FAINT,
                    false);
            if (screen.isEditMode) {
                int addY = cy + 12;
                int boxH = renderAddBox(g, x + m, addY, w - m * 2, "+ Add Reward", mx, my);
                addRewardBoxRect = new int[] { x + m, addY, w - m * 2, boxH };
            }
            g.disableScissor();
            return;
        }

        if (screen.node.isRewardChoice()) {
            String choiceHdr = screen.node.getRewardChoiceCount() == 1 ? "§6Pick 1 reward:" :
                    "§6Pick " + screen.node.getRewardChoiceCount() + " rewards:";
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, choiceHdr, x + m, cy, screen.C_TEXT, false);
            cy += 12;
        }

        int slotSz = 18;
        for (int ri = 0; ri < rewards.size(); ri++) {
            QuestReward reward = rewards.get(ri);
            QuestReward display = screen.present.displayRewardFor(ri, reward);
            if (cy > y + h) break;
            renderRewardSlot(g, x + m, cy, slotSz, display, mx, my);

            boolean rowHov = mx >= x + m && mx < x + m + w - m * 2 && my >= cy && my < cy + slotSz;
            if (rowHov) {
                hoveredRewardFs = reward;
                hoveredRewardFsIndex = ri;
                hoveredFsX = mx;
                hoveredFsY = my;
            }

            String label;
            if (display instanceof QuestReward.ItemReward ir) {
                label = (screen.present.rewardTargetMasked(ir) ? "???" :
                        screen.present.rewardStack(ir).getHoverName().getString()) +
                        " ×" +
                        (screen.present.rewardAmountMasked(ir) ? "?" : String.valueOf(ir.getCount()));
            } else if (screen.present.rewardTargetMasked(display)) {
                label = "???";
            } else if (reward instanceof QuestReward.ChoiceBoxReward) {
                label = reward.getSummary().getString();
            } else {
                label = switch (display.getType()) {
                    case XP -> "XP Reward";
                    case COMMAND -> "Command";
                    case LOOT_TABLE -> "Loot Table";
                    case SCRIPT_EVENT -> "Script Event";
                    case REWARD_TABLE -> "Reward Table";
                    case FLUID -> display.getSummary().getString();
                    default -> display.getType().name();
                };
            }
            int labelMaxW = w - m * 2 - slotSz - 8;
            if (Minecraft.getInstance().font.width(label.replaceAll("§.", "")) > labelMaxW)
                label = Minecraft.getInstance().font.plainSubstrByWidth(label,
                        Math.max(0, labelMaxW - Minecraft.getInstance().font.width("…"))) + "…";
            String prefix;
            if (boxAt(ri) != null) {
                prefix = screen.isRewardPicked(ri) ? "§a✔ §7" : (rowHov ? "§e► §f" : "§e○ §7");
            } else if (screen.node.isRewardChoice() && screen.isRewardPicked(ri)) {
                prefix = "§a✔ §7";
            } else if (screen.node.isRewardChoice()) {
                prefix = rowHov ? "§e► §f" : "§e○ §7";
            } else {
                prefix = rowHov ? "§f" : "§7";
            }
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, prefix + label, x + m + slotSz + 4, cy + 4,
                    rowHov ? screen.C_TEXT : screen.C_TEXT_DIM, false);
            cy += slotSz + 4;
        }
        if (screen.isEditMode && cy + 16 <= y + h) {
            int boxH = renderAddBox(g, x + m, cy, w - m * 2, "+ Add Reward", mx, my);
            addRewardBoxRect = new int[] { x + m, cy, w - m * 2, boxH };
        }
        g.disableScissor();
    }

    private void renderRewardSlot(GuiGraphics g, int x, int y, int sz, QuestReward reward, int mx, int my) {
        boolean hov = mx >= x && mx < x + sz && my >= y && my < y + sz;
        g.fill(x, y, x + sz, y + sz, hov ? QuestTasksScreen.C_SLOT_HI : QuestTasksScreen.C_SLOT_BG);
        g.fill(x, y, x + sz, y + 1, screen.C_BORDER);
        g.fill(x, y + sz - 1, x + sz, y + sz, screen.C_BORDER);
        g.fill(x, y, x + 1, y + sz, screen.C_BORDER);
        g.fill(x + sz - 1, y, x + sz, y + sz, screen.C_BORDER);

        ItemStack stack = reward instanceof QuestReward.ItemReward ir ? screen.present.shownRewardStack(ir) :
                ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            int off = (sz - 16) / 2;
            g.renderItem(stack, x + off, y + off);
            if (sz >= 18) g.renderItemDecorations(Minecraft.getInstance().font, stack, x + off, y + off);
        } else {
            ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font,
                    "§7" + screen.present.rewardGlyph(reward), x + sz / 2, y + sz / 2 - 4,
                    screen.C_TEXT_DIM);
        }
    }

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int footerY = screen.height - QuestTasksScreen.FOOTER_H;
        g.fill(0, footerY, screen.width, screen.height, screen.C_HEADER);
        g.fill(0, footerY, screen.width, footerY + 1, screen.C_BORDER);
        drawEmergencyButton(g, 6, footerY + 2, 22, QuestTasksScreen.FOOTER_H - 4, mx, my);
        drawVoiceButton(g, 6 + screen.voiceOffset(), footerY + 2, 22, QuestTasksScreen.FOOTER_H - 4, mx, my);
        drawLinkButton(g, 6 + screen.linkOffset(), footerY + 2, 22, QuestTasksScreen.FOOTER_H - 4, mx, my);

        QuestState state = screen.playerData != null ?
                screen.playerData.getQuestState(screen.node.getId(), QuestState.LOCKED) :
                QuestState.LOCKED;
        boolean canClaim = state == QuestState.COMPLETED && !screen.rewardsClaimed() &&
                !screen.content.effectiveRewards().isEmpty();

        if (canClaim) {
            int btnW = 140, btnX = (screen.width - btnW) / 2, btnY = footerY + 2;
            if (screen.node.isRewardChoice()) {
                String msg = screen.node.getRewardChoiceCount() == 1 ? "§6Pick a reward ↑" :
                        "§6Pick " + screen.node.getRewardChoiceCount() + " rewards ↑";
                ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, msg, screen.width / 2, btnY + 6,
                        screen.C_TEXT);
            } else {
                boolean hov = mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + 18;
                g.fill(btnX, btnY, btnX + btnW, btnY + 18, hov ? 0xFF2A4A2A : 0xFF1A2A1A);
                g.fill(btnX, btnY, btnX + btnW, btnY + 1, hov ? screen.C_DONE : 0xFF333333);
                ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§a✓ Claim Rewards",
                        btnX + btnW / 2, btnY + 6,
                        hov ? screen.C_DONE : screen.C_TEXT);
            }
        } else if (screen.rewardsClaimed()) {
            ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§8Rewards claimed", screen.width / 2,
                    footerY + 10, screen.C_TEXT_FAINT);
        } else {
            String footerMsg = screen.width < 220 ? "§8Complete tasks first" : "§8Complete all tasks to claim rewards";
            ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, footerMsg, screen.width / 2,
                    footerY + 10, screen.C_TEXT_FAINT);
        }
    }

    boolean handleFullscreenClick(double mx, double my, int btn) {
        if (btn == 0 && !usesPopupOpen && clickPrereqRow(mx, my)) return true;
        if (usesPopupOpen && btn == 0) return clickUsesPopup(mx, my);
        if (clickDescriptionPager(mx, my, btn)) return true;
        if (clickEditShortcuts(btn)) return true;
        if (clickStripEntries(btn)) return true;
        if (clickHeaderButtons(mx, my, btn)) return true;
        if (clickInspectorTabs(mx, my)) return true;
        if (clickAddBoxes(mx, my, btn)) return true;
        if (hoveredTaskFs != null) return clickHoveredTaskRow(btn);
        if (hoveredRewardFs != null) return clickHoveredRewardRow();
        if (clickFooter(mx, my)) return true;
        if (clickRichRegions(mx, my)) return true;
        if (clickDescriptionBox()) return true;
        return screen.superMouseClicked(mx, my, btn);
    }

    private static boolean inRect(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private void navigateOverviewTo(QuestNode target) {
        if (Minecraft.getInstance() != null && screen.parent instanceof ChronicleOverviewScreen overview) {
            overview.navigateToNode(target);
        }
    }

    private boolean clickPrereqRow(double mx, double my) {
        for (int i = 0; i < prereqRowRects.size(); i++) {
            if (inRect(mx, my, prereqRowRects.get(i))) {
                navigateOverviewTo(prereqRowTargets.get(i));
                return true;
            }
        }
        return false;
    }

    private boolean clickUsesPopup(double mx, double my) {
        for (int i = 0; i < usesPopupRowRects.size(); i++) {
            if (inRect(mx, my, usesPopupRowRects.get(i))) {
                QuestNode target = usesPopupRowTargets.get(i);
                usesPopupOpen = false;
                navigateOverviewTo(target);
                return true;
            }
        }
        usesPopupOpen = false;
        return true;
    }

    private boolean clickDescriptionPager(double mx, double my, int btn) {
        if (btn != 0 || screen.descPagerPageCount <= 1) return false;
        if (!(mx >= screen.descPagerX && mx < screen.descPagerX + screen.descPagerW &&
                my >= screen.descPagerY && my < screen.descPagerY + screen.descPagerH)) {
            return false;
        }
        boolean leftHalf = mx < screen.descPagerX + screen.descPagerW / 2.0;
        if (leftHalf && screen.descPage > 0) screen.descPage--;
        else if (!leftHalf && screen.descPage < screen.descPagerPageCount - 1) screen.descPage++;
        screen.descScrollY = 0;
        return true;
    }

    private boolean clickEditShortcuts(int btn) {
        if (btn == 1 && screen.isEditMode && Minecraft.getInstance() != null &&
                (hoveredStripTask != null || hoveredStripReward != null ||
                        hoveredTaskFs != null || hoveredRewardFs != null)) {
            Minecraft.getInstance().setScreen(new TaskRewardEditorScreen(screen, screen.node));
            return true;
        }
        if (btn == 2 && screen.isEditMode && (hoveredStripTask != null || hoveredTaskFs != null)) {
            QuestTask target = hoveredStripTask != null ? hoveredStripTask : hoveredTaskFs;
            if (Screen.hasShiftDown()) actions.resetTaskProgress(target);
            else actions.forceCompleteTask(target);
            return true;
        }
        return false;
    }

    private boolean clickStripEntries(int btn) {
        if (btn == 0 && hoveredStripTask != null) {
            if (actions.tryCompleteCheckmark(hoveredStripTask) || actions.trySubmitItemTask(hoveredStripTask))
                return true;
            inspectorTab = QuestTasksScreen.InspTab.TASKS;
            screen.inspectorScrollY = 0;
            return true;
        }
        if (hoveredStripReward != null) {
            actions.tryClaimReward(hoveredStripRewardIdx);
            return true;
        }
        return false;
    }

    private boolean clickHeaderButtons(double mx, double my, int btn) {
        if (mx >= 4 && mx < 20 && my >= 6 && my < 22) {
            if (Minecraft.getInstance() != null) Minecraft.getInstance().setScreen(screen.parent);
            return true;
        }

        int pinX2 = screen.width - 20;
        int editX2 = pinX2 - (screen.isEditMode ? 18 : 0);
        int fsX2 = editX2 - 20;
        int usesX2 = fsX2 - 20;

        boolean usesHasAny = !screen.node.getPrerequisites().isEmpty() || !screen.node.getChildren().isEmpty();
        if (usesHasAny && mx >= usesX2 && mx < usesX2 + 16 && my >= 6 && my < 22 && btn == 0) {
            usesPopupOpen = true;
            return true;
        }

        if (mx >= fsX2 && mx < fsX2 + 16 && my >= 6 && my < 22 && btn == 0) {
            screen.isFullscreen = false;
            return true;
        }

        if (screen.isEditMode && mx >= editX2 && mx < editX2 + 14 && my >= 6 && my < 22 && btn == 0) {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new QuestCreatorScreen(screen, screen.node));
            return true;
        }

        if (mx >= pinX2 && mx < screen.width - 4 && my >= 6 && my < 22 && btn == 0) {
            if (screen.playerData != null) {
                screen.playerData.togglePin(screen.node.getId());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2STogglePinPacket(screen.node.getId()));
            }
            return true;
        }
        return false;
    }

    private boolean clickInspectorTabs(double mx, double my) {
        int contentTop = headerH() + reqBarH() + QuestTasksScreen.MARGIN;
        int[] pw2 = fullscreenPanelWidths();
        int inspW2 = pw2[0];
        int contentRight = screen.width - inspW2 - pw2[1] - QuestTasksScreen.MARGIN * 3;
        int rightX = contentRight + QuestTasksScreen.MARGIN;
        if (!(mx >= rightX && mx < rightX + inspW2 && my >= contentTop &&
                my < contentTop + QuestTasksScreen.INSP_TAB_H + 8)) {
            return false;
        }
        int tabX = rightX + 6;
        for (QuestTasksScreen.InspTab t : visibleInspTabs()) {
            int tabW = Minecraft.getInstance().font.width(inspTabLabel(t)) + 6;
            if (mx >= tabX && mx < tabX + tabW && my >= contentTop + 4 &&
                    my < contentTop + 4 + QuestTasksScreen.INSP_TAB_H) {
                inspectorTab = t;
                screen.inspectorScrollY = 0;
                return true;
            }
            tabX += tabW + 2;
        }
        return false;
    }

    private boolean clickAddBoxes(double mx, double my, int btn) {
        if (btn != 0 || !screen.isEditMode || Minecraft.getInstance() == null) return false;
        boolean onAddTask = addTaskBoxRect != null && inRect(mx, my, addTaskBoxRect);
        boolean onAddReward = addRewardBoxRect != null && inRect(mx, my, addRewardBoxRect);
        if (!onAddTask && !onAddReward) return false;
        Minecraft.getInstance().setScreen(new TaskRewardEditorScreen(screen, screen.node));
        return true;
    }

    private boolean clickHoveredTaskRow(int btn) {
        if (actions.tryCompleteCheckmark(hoveredTaskFs) || (btn == 0 && actions.trySubmitItemTask(hoveredTaskFs)))
            return true;
        if (net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.canOpenForTask(hoveredTaskFs)) {
            net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat.openForTask(hoveredTaskFs, screen);
            return true;
        }
        if (actions.tryOpenArchiveEntry(hoveredTaskFs)) return true;
        ItemStack icon = screen.present.getTaskIcon(hoveredTaskFs);
        if (!icon.isEmpty()) actions.tryOpenInRecipeViewer(icon);
        return true;
    }

    private boolean clickHoveredRewardRow() {
        if (actions.tryClaimReward(hoveredRewardFsIndex)) return true;
        if (hoveredRewardFs instanceof QuestReward.ItemReward ir) {
            actions.tryOpenInRecipeViewer(new ItemStack(ir.getItem(), ir.getCount()));
        }
        return true;
    }

    private boolean clickFooter(double mx, double my) {
        int footerY = screen.height - QuestTasksScreen.FOOTER_H;
        if (actions.clickEmergencyButton(6, footerY + 2, 22, QuestTasksScreen.FOOTER_H - 4, mx, my)) return true;
        if (actions.clickVoiceButton(6 + screen.voiceOffset(), footerY + 2, 22, QuestTasksScreen.FOOTER_H - 4, mx, my))
            return true;
        if (actions.clickLinkButton(6 + screen.linkOffset(), footerY + 2, 22, QuestTasksScreen.FOOTER_H - 4, mx, my))
            return true;
        if (my >= footerY + 2 && my < footerY + 20) {
            QuestState state = screen.playerData != null ?
                    screen.playerData.getQuestState(screen.node.getId(), QuestState.LOCKED) :
                    QuestState.LOCKED;
            if (state == QuestState.COMPLETED && !screen.rewardsClaimed() &&
                    !screen.content.effectiveRewards().isEmpty() &&
                    !screen.node.isRewardChoice()) {
                net.neoforged.neoforge.network.PacketDistributor
                        .sendToServer(new C2SClaimQuestRewardPacket(screen.node.getId(), -1));
                return true;
            }
        }
        return false;
    }

    private boolean clickRichRegions(double mx, double my) {
        for (net.phoenixvine.wiki.client.rich.RichSpan.Region r : screen.richRegions) {
            if (r.contains(mx, my) && clickRichSpan(r.span())) return true;
        }
        return false;
    }

    private boolean clickRichSpan(net.phoenixvine.wiki.client.rich.RichSpan span) {
        if (span instanceof net.phoenixvine.wiki.client.rich.RichSpan.Link l) {
            actions.openLink(l.url());
            return true;
        }
        if (span instanceof net.phoenixvine.wiki.client.rich.RichSpan.TypewriterReveal reveal) {
            net.phoenixvine.wiki.client.rich.render.TypewriterSupport.start(reveal.key());
            return true;
        }
        if (span instanceof net.phoenixvine.wiki.client.rich.RichSpan.CodeCopy cc) {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().keyboardHandler.setClipboard(cc.code());
            return true;
        }
        if (span instanceof net.phoenixvine.wiki.client.rich.RichSpan.DetailsToggle dt) {
            if (!descExpandedKeys.remove(dt.key())) descExpandedKeys.add(dt.key());
            return true;
        }
        if (span instanceof net.phoenixvine.wiki.client.rich.RichSpan.ChecklistToggle ct) {
            boolean current = descExpandedKeys.contains("CL1:" + ct.key()) ||
                    !descExpandedKeys.contains("CL0:" + ct.key()) && ct.checkedDefault();
            boolean next = !current;
            descExpandedKeys.remove("CL1:" + ct.key());
            descExpandedKeys.remove("CL0:" + ct.key());
            descExpandedKeys.add((next ? "CL1:" : "CL0:") + ct.key());
            return true;
        }
        return false;
    }

    private boolean clickDescriptionBox() {
        if (!(hoveredFsDescBox && screen.isEditMode && Minecraft.getInstance() != null)) return false;
        Minecraft.getInstance().setScreen(new QuestTextInputScreen(screen, "Description",
                screen.descriptions.currentFullDescriptionRaw(), 8192,
                v -> {
                    screen.node.setDescription(Component.literal(v));
                    liveDescOverride = v;
                    richSpansPage = -1;
                    QuestFileSaver.saveOneQuestToDisk(screen.node);
                    LangSyncScheduler.markDirty();
                }));
        return true;
    }

    private int calcInspW() {
        int stored = QuestChroniclesSettings.get().getTaskInspectorW();
        if (stored > 0) return Math.max(QuestTasksScreen.MIN_INSP_W, stored);
        int budget = Math.max(140, screen.width - 180 - QuestTasksScreen.MARGIN * 4);
        return Math.max(QuestTasksScreen.MIN_INSP_W, Math.min(160, budget * 3 / 5));
    }

    int calcRewardW() {
        int stored = QuestChroniclesSettings.get().getTaskRewardW();
        if (stored > 0) return Math.max(QuestTasksScreen.MIN_REWARD_W, stored);
        int budget = Math.max(140, screen.width - 180 - QuestTasksScreen.MARGIN * 4);
        return Math.max(QuestTasksScreen.MIN_REWARD_W, Math.min(QuestTasksScreen.REWARD_W, budget * 2 / 5));
    }

    int[] fullscreenPanelWidths() {
        int inspW = calcInspW();
        int rewardW = calcRewardW();
        int avail = screen.width - QuestTasksScreen.MARGIN * 3 - QuestTasksScreen.MIN_CONTENT_W;
        if (avail > 0 && inspW + rewardW > avail) {
            float scale = (float) avail / (inspW + rewardW);
            inspW = Math.max(QuestTasksScreen.MIN_INSP_W, Math.round(inspW * scale));
            rewardW = Math.max(QuestTasksScreen.MIN_REWARD_W, Math.round(rewardW * scale));
        }
        return new int[] { inspW, rewardW };
    }

    private void renderPanelDividers(GuiGraphics g, int d1x, int d2x, int contentTop, int contentH, int mx, int my) {
        boolean hovD1 = !screen.draggingRewardDivider && isOverDivider(d1x, contentTop, contentH, mx, my);
        boolean hovD2 = !screen.draggingInspDivider && isOverDivider(d2x, contentTop, contentH, mx, my);

        int c1 = screen.draggingInspDivider ? 0xFFFFCC33 : hovD1 ? 0xFF88CCFF : 0;
        int c2 = screen.draggingRewardDivider ? 0xFFFFCC33 : hovD2 ? 0xFF88CCFF : 0;
        if (c1 != 0) {
            int hx = d1x + QuestTasksScreen.MARGIN / 2;
            g.fill(hx - 1, contentTop, hx + 1, contentTop + contentH, c1);
        }
        if (c2 != 0) {
            int hx = d2x + QuestTasksScreen.MARGIN / 2;
            g.fill(hx - 1, contentTop, hx + 1, contentTop + contentH, c2);
        }
    }

    private boolean isOverDivider(int dx, int contentTop, int contentH, int mx, int my) {
        return mx >= dx - QuestTasksScreen.DIVIDER_HIT_PAD &&
                mx < dx + QuestTasksScreen.MARGIN + QuestTasksScreen.DIVIDER_HIT_PAD &&
                my >= contentTop && my < contentTop + contentH;
    }

    boolean tryStartDividerDrag(double mx, double my) {
        int contentTop = headerH() + reqBarH() + QuestTasksScreen.MARGIN;
        int contentH = screen.height - contentTop - QuestTasksScreen.FOOTER_H - QuestTasksScreen.MARGIN;
        int[] pw = fullscreenPanelWidths();
        int inspW = pw[0], rewardW = pw[1];
        int contentRight = screen.width - inspW - rewardW - QuestTasksScreen.MARGIN * 3;
        int inspX = contentRight + QuestTasksScreen.MARGIN;
        int d1x = contentRight;
        int d2x = inspX + inspW;

        if (isOverDivider(d1x, contentTop, contentH, (int) mx, (int) my)) {
            screen.draggingInspDivider = true;
            screen.dividerDragStartMx = (int) mx;
            screen.dividerDragStartInspW = inspW;
            return true;
        }
        if (isOverDivider(d2x, contentTop, contentH, (int) mx, (int) my)) {
            screen.draggingRewardDivider = true;
            screen.dividerDragStartMx = (int) mx;
            screen.dividerDragStartInspW = inspW;
            screen.dividerDragStartRewardW = rewardW;
            return true;
        }
        return false;
    }

    QuestReward.ChoiceBoxReward boxAt(int rewardIndex) {
        List<QuestReward> rewards = screen.content.effectiveRewards();
        if (rewardIndex < 0 || rewardIndex >= rewards.size()) return null;
        return rewards.get(rewardIndex) instanceof QuestReward.ChoiceBoxReward box ? box : null;
    }

    final TaskActions actions;

    java.util.List<net.phoenixvine.wiki.client.rich.RichBlock> descBlocks = java.util.List.of();

    final java.util.Set<String> descExpandedKeys = new java.util.HashSet<>();

    int richSpansPage = -1;

    String descBlocksSourceText = null;

    String liveDescOverride = null;

    private QuestTasksScreen.InspTab inspectorTab = QuestTasksScreen.InspTab.TASKS;

    private int inspectorContentH = 0;

    QuestTask hoveredTask = null;

    QuestReward hoveredReward = null;

    int hoveredRewardIndex = -1;

    int hoveredSlotX, hoveredSlotY;

    private boolean hoveredFsDescBox = false;

    private int fsDescBoxX, fsDescBoxY, fsDescBoxW, fsDescBoxH;

    private boolean usesPopupOpen = false;

    final List<int[]> usesPopupRowRects = new ArrayList<>();

    final List<QuestNode> usesPopupRowTargets = new ArrayList<>();

    private final List<int[]> prereqRowRects = new ArrayList<>();

    private final List<QuestNode> prereqRowTargets = new ArrayList<>();

    private QuestTask hoveredTaskFs = null;

    private QuestReward hoveredRewardFs = null;

    private int hoveredRewardFsIndex = -1;

    private int hoveredFsX, hoveredFsY;

    private int[] addTaskBoxRect = null;

    private int[] addRewardBoxRect = null;

    private QuestTask hoveredStripTask = null;

    private QuestReward hoveredStripReward = null;

    private int hoveredStripRewardIdx = -1;

    private int hoveredStripX, hoveredStripY;

    long emergencyArmedUntilMs = 0;
}

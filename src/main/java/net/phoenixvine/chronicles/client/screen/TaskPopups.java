package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.tasks.*;

import java.util.List;

final class TaskPopups {

    private final QuestTasksScreen screen;

    TaskPopups(QuestTasksScreen screen) {
        this.screen = screen;
    }

    QuestReward.ChoiceBoxReward openChoiceBox = null;

    void renderUsesPopup(GuiGraphics g, int anchorX, int mx, int my) {
        screen.fullscreen.usesPopupRowRects.clear();
        screen.fullscreen.usesPopupRowTargets.clear();

        List<QuestNode> requires = screen.node.getPrerequisites();
        List<QuestNode> unlocks = screen.node.getChildren();

        int rowH = 12;
        int headerH = 12;
        int padW = 6, padTop = 4, padBot = 6;
        int innerW = 160;
        int contentRows = requires.size() + unlocks.size();
        int sectionHeaders = (requires.isEmpty() ? 0 : 1) + (unlocks.isEmpty() ? 0 : 1);
        int popupH = padTop + sectionHeaders * headerH + contentRows * rowH + padBot;
        if (contentRows == 0) popupH = padTop + 14 + padBot;

        int popupX = Math.max(4, Math.min(anchorX + 16 - innerW, screen.width - innerW - 4));
        int popupY = screen.fullscreen.headerH() + 2;

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 250f);
        g.fill(popupX, popupY, popupX + innerW, popupY + popupH, 0xF00A0A0E);
        screen.drawBorder(g, popupX, popupY, innerW, popupH);

        int ry = popupY + padTop;
        if (contentRows == 0) {
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8(no connections)", popupX + padW, ry + 2,
                    screen.C_TEXT_FAINT, false);
        } else {
            ry = renderUsesPopupSection(g, popupX, ry, innerW, padW, rowH, "§8Requires:", requires, mx, my);
            ry = renderUsesPopupSection(g, popupX, ry, innerW, padW, rowH, "§8Unlocks:", unlocks, mx, my);
        }
        g.pose().popPose();
    }

    private int renderUsesPopupSection(GuiGraphics g, int popupX, int ry, int innerW, int padW, int rowH,
                                       String header, List<QuestNode> list, int mx, int my) {
        if (list.isEmpty()) return ry;
        ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, header, popupX + padW, ry + 2, screen.C_TEXT_FAINT,
                false);
        ry += rowH;
        for (QuestNode target : list) {
            boolean hov = mx >= popupX && mx < popupX + innerW && my >= ry && my < ry + rowH;
            if (hov) g.fill(popupX + 2, ry, popupX + innerW - 2, ry + rowH, 0x22FFFFFF);
            QuestState state = screen.playerData != null ?
                    screen.playerData.getQuestState(target.getId(), QuestState.LOCKED) :
                    QuestState.LOCKED;
            String title = target.getTitle().getString();
            int maxW = innerW - padW * 2 - 10;
            if (net.phoenixvine.chronicles.client.util.EffectText.width(Minecraft.getInstance().font, title) > maxW)
                title = net.phoenixvine.chronicles.client.util.EffectText.cut(Minecraft.getInstance().font, title,
                        Math.max(0, maxW - Minecraft.getInstance().font.width("…"))) + "…";
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font,
                    (state == QuestState.COMPLETED ? "§a●" : "§8○") + " " + (hov ? "§f" : "§7") + title,
                    popupX + padW + 8, ry + 2, hov ? screen.C_TEXT : screen.C_TEXT_DIM, false);
            screen.fullscreen.usesPopupRowRects.add(new int[] { popupX, ry, innerW, rowH });
            screen.fullscreen.usesPopupRowTargets.add(target);
            ry += rowH;
        }
        return ry;
    }

    void openChoiceBox(QuestReward.ChoiceBoxReward box, int rewardIndex) {
        openChoiceBox = box;
        screen.openChoiceBoxRewardIndex = rewardIndex;
    }

    void renderChoiceBoxPopup(GuiGraphics g, int mx, int my) {
        screen.choiceBoxRowRects.clear();
        List<QuestReward> options = openChoiceBox.getOptions();

        int rowH = 20, padW = 8, padTop = 24, padBot = 8;
        int innerW = 200;
        int popupH = padTop + options.size() * rowH + padBot;
        int popupX = (screen.width - innerW) / 2;
        int popupY = (screen.height - popupH) / 2;

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f);
        g.fill(0, 0, screen.width, screen.height, 0x99000000);
        g.fill(popupX, popupY, popupX + innerW, popupY + popupH, 0xF00A0A0E);
        screen.drawBorder(g, popupX, popupY, innerW, popupH);
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§6Choose your reward", popupX + innerW / 2,
                popupY + 8, screen.C_TEXT);

        int ry = popupY + padTop;
        for (QuestReward option : options) {
            boolean hov = mx >= popupX + padW && mx < popupX + innerW - padW && my >= ry && my < ry + rowH;
            if (hov) g.fill(popupX + padW, ry, popupX + innerW - padW, ry + rowH, 0x22FFFFFF);

            int iconX = popupX + padW + 2;
            if (option instanceof QuestReward.ItemReward ir) {
                ItemStack stack = screen.present.rewardStack(ir);
                g.renderItem(stack, iconX, ry + 2);
                g.renderItemDecorations(Minecraft.getInstance().font, stack, iconX, ry + 2);
            } else {
                ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "§7?", iconX + 8, ry + 6,
                        screen.C_TEXT_DIM);
            }

            String label = option.getSummary().getString();
            int labelMaxW = innerW - padW * 2 - 22;
            if (Minecraft.getInstance().font.width(label) > labelMaxW)
                label = Minecraft.getInstance().font.plainSubstrByWidth(label,
                        labelMaxW - Minecraft.getInstance().font.width("…")) + "…";
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, (hov ? "§f" : "§7") + label, iconX + 20, ry + 6,
                    hov ? screen.C_TEXT : screen.C_TEXT_DIM, false);

            screen.choiceBoxRowRects.add(new int[] { popupX + padW, ry, innerW - padW * 2, rowH });
            ry += rowH;
        }

        g.pose().popPose();
    }
}

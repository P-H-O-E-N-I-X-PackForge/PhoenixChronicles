package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.client.util.ClientEmergencyState;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;

import java.util.*;

final class OverviewHeader {

    private final ChronicleOverviewScreen screen;

    OverviewHeader(ChronicleOverviewScreen screen) {
        this.screen = screen;
    }

    boolean tryHandleQuestbookTitleClick(double mx, double my) {
        if (questbookTitleHovered((int) mx, (int) my)) {
            if (Minecraft.getInstance() != null) Minecraft.getInstance().setScreen(new QuestbookTitleScreen(screen));
            return true;
        }
        return false;
    }

    boolean tryHandleHeaderBarClick(double mx, double my, int cr) {
        if (my < 0 || my >= ChronicleOverviewScreen.TOOLBAR_Y) return false;

        int[][] layout = computeHeaderBarLayout(cr);
        int[] claimBtn = layout[0], gridBtn = layout[1], subgraphBtn = layout[2], emergencyBtn = layout[3];
        int[] autoClaimBtn = layout[4];

        if (claimBtn != null && hitsRect(claimBtn, mx, my)) {
            if (Minecraft.getInstance() != null) Minecraft.getInstance().setScreen(new ClaimRewardsScreen(screen));
            return true;
        }

        if (emergencyBtn != null && hitsRect(emergencyBtn, mx, my)) {
            if (Minecraft.getInstance() != null) Minecraft.getInstance().setScreen(new EmergencyClaimScreen(screen));
            return true;
        }

        if (autoClaimBtn != null && hitsRect(autoClaimBtn, mx, my)) {
            if (Minecraft.getInstance() != null) Minecraft.getInstance().setScreen(new AutoClaimSettingsScreen(screen));
            return true;
        }

        if (hitsRect(gridBtn, mx, my)) {
            for (int gi = 0; gi < ChronicleOverviewScreen.GRID_SNAP_CYCLE.length; gi++) {
                if (ChronicleOverviewScreen.GRID_SNAP_CYCLE[gi] == screen.gridSnap) {
                    screen.gridSnap = ChronicleOverviewScreen.GRID_SNAP_CYCLE[(gi + 1) %
                            ChronicleOverviewScreen.GRID_SNAP_CYCLE.length];
                    break;
                }
            }
            return true;
        }

        if (subgraphBtn != null && hitsRect(subgraphBtn, mx, my)) {
            screen.editorState.subgraphMode = !screen.editorState.subgraphMode;
            if (screen.editorState.subgraphMode) screen.rebuildSubgraph();
            return true;
        }

        return false;
    }

    private static boolean hitsRect(int[] b, double mx, double my) {
        return mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
    }

    public int[][] computeHeaderBarLayout(int cr) {
        String zoomStr2 = Math.round(screen.zoom * 100) + "%";
        int zw2 = screen.font().width(zoomStr2);
        int zx2 = cr - zw2 - 10;

        int[] claimBtn = null;
        int unclaimedCount2 = screen.unclaimedRewardCount();
        if (unclaimedCount2 > 0) {
            String claimLabel2 = "🎁 " + unclaimedCount2;
            int cw2 = screen.font().width(claimLabel2);
            int cpx2 = zx2 - cw2 - 18;
            claimBtn = new int[] { cpx2 - 3, 3, cpx2 + cw2 + 5, 16 };
            zx2 = cpx2;
        }

        int[] emergencyBtn = null;
        ChronicleOverviewScreen.EmergencyPill emergency2 = emergencyPill();
        if (emergencyPillVisible(emergency2)) {
            int ew2 = screen.font().width(emergencyPillLabel(emergency2));
            int epx2 = zx2 - ew2 - 18;
            emergencyBtn = new int[] { epx2 - 3, 3, epx2 + ew2 + 5, 16 };
            zx2 = epx2;
        }

        int[] autoClaimBtn = null;
        if (screen.playerData != null) {
            int aw2 = screen.font().width(autoClaimPillLabel());
            int apx2 = zx2 - aw2 - 18;
            autoClaimBtn = new int[] { apx2 - 3, 3, apx2 + aw2 + 5, 16 };
            zx2 = apx2;
        }

        String gridLabel2 = !screen.gridSnapEnabled ? "Grid: off" :
                (screen.gridSnap == 1) ? "Grid: free" : "Grid: " + screen.gridSnap;
        int gw2 = screen.font().width(gridLabel2);
        int gpx2 = zx2 - gw2 - 18;
        int[] gridBtn = { gpx2 - 3, 3, gpx2 + gw2 + 5, 16 };

        int[] subgraphBtn = null;
        if (screen.isDevMode) {
            String sgLabel2 = screen.editorState.subgraphMode ? "Subgraph: " + screen.subgraphNodes.size() : "Subgraph";
            int sgw2 = screen.font().width(sgLabel2);
            int sgx2 = gpx2 - sgw2 - 18;
            subgraphBtn = new int[] { sgx2 - 3, 3, sgx2 + sgw2 + 5, 16 };
        }

        return new int[][] { claimBtn, gridBtn, subgraphBtn, emergencyBtn, autoClaimBtn };
    }

    boolean tryHandleToolbarButtonClick(double mx, double my) {
        if (my >= ChronicleOverviewScreen.TOOLBAR_Y && my < ChronicleOverviewScreen.HEADER_H) {
            if (hitsToolbarBtn("fit", mx, my)) {
                screen.fitToCanvas();
                return true;
            }
            if (screen.isDevMode && hitsToolbarBtn("toolSelect", mx, my)) {
                screen.editorState.activeTool = GraphEditorState.EditorTool.SELECT;
                return true;
            }
            if (screen.isDevMode && hitsToolbarBtn("toolPlace", mx, my)) {
                boolean enteringPlace = screen.editorState.activeTool != GraphEditorState.EditorTool.PLACE;
                screen.editorState.activeTool = enteringPlace ?
                        GraphEditorState.EditorTool.PLACE : GraphEditorState.EditorTool.SELECT;

                if (enteringPlace) screen.editorState.multiSelection.clear();
                return true;
            }
            if (screen.isDevMode && hitsToolbarBtn("toolConnect", mx, my)) {
                screen.editorState.activeTool = screen.editorState.activeTool == GraphEditorState.EditorTool.CONNECT ?
                        GraphEditorState.EditorTool.SELECT : GraphEditorState.EditorTool.CONNECT;
                return true;
            }
            if (hitsToolbarBtn("settings", mx, my) && Minecraft.getInstance() != null) {
                Minecraft.getInstance().setScreen(new SettingsScreen(screen));
                return true;
            }
            if (screen.isDevMode && hitsToolbarBtn("wiki", mx, my) && Minecraft.getInstance() != null) {
                screen.openWiki();
                return true;
            }
            if (hitsToolbarBtn("hideDone", mx, my)) {
                screen.hideCompleted = !screen.hideCompleted;
                screen.softRebuild();
                return true;
            }
            if (hitsToolbarBtn("map", mx, my)) {
                screen.minimapOpen = !screen.minimapOpen;
                return true;
            }
            if (hitsToolbarBtn("stats", mx, my)) {
                screen.toggleStatsPanel();
                return true;
            }
        }
        return false;
    }

    boolean tryHandleFilterPillClick(double mx, double my, int cl, int cr) {
        int[][] pills = filterPillBounds(cl, cr);
        for (int i = 0; i < screen.toolbarPanel.filterKeyCount(); i++) {
            int[] b = pills[i];
            if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                screen.stateFilter = screen.toolbarPanel.filterKey(i);
                screen.editorState.selectedNode = null;
                screen.softRebuild();
                return true;
            }
        }
        return false;
    }

    void renderHeaderAndBaseLayout(GuiGraphics g, int mx, int my, int cl, int cr) {
        screen.renderBackground(g, mx, my, 0f);

        int visW = screen.sidebar.sidebarVisualW();
        g.fill(0, 0, visW, screen.height, screen.palette.panelDark);

        ChapterConfig canvasCfg = screen.selectedChapter.isEmpty() ? new ChapterConfig() :
                ChapterConfig.getEffective(screen.selectedChapter);
        if (canvasCfg.getColorAlpha() > 0) {
            g.fill(visW, 0, cr, screen.height, screen.palette.bg);
        }

        g.fill(cr, 0, screen.width, screen.height, screen.palette.panelDark);
        g.fill(cr, 0, cr + 1, screen.height, screen.palette.border);

        g.fill(0, 0, screen.width, ChronicleOverviewScreen.TOOLBAR_Y, screen.palette.header);
        g.fill(0, ChronicleOverviewScreen.TOOLBAR_Y - 1, screen.width, ChronicleOverviewScreen.TOOLBAR_Y,
                screen.palette.border);
        String titlePrefix = screen.testMode ? "§c⏵ PLAYER  §8⟫  §7" : "§8Chronicles  §8⟫  §7";

        int pillReserve = screen.font().width(Math.round(screen.zoom * 100) + "%") + 10;
        if (screen.unclaimedRewardCount() > 0)
            pillReserve += screen.font().width("🎁 " + screen.unclaimedRewardCount()) + 18;
        ChronicleOverviewScreen.EmergencyPill emergencyReserve = emergencyPill();
        if (emergencyPillVisible(emergencyReserve))
            pillReserve += screen.font().width(emergencyPillLabel(emergencyReserve)) + 18;
        if (screen.playerData != null) pillReserve += screen.font().width(autoClaimPillLabel()) + 18;
        pillReserve += screen.font().width("Grid: off") + 18;
        if (screen.isDevMode) pillReserve += screen.font().width("⊛ Subgraph: 000") + 18;
        int titleMaxW = Math.max(20, (cr - pillReserve) - (cl + 8));
        String titleFull = titlePrefix + screen.chapters.chapterBreadcrumb(screen.selectedChapter);
        String titleToDraw = titleFull;
        if (screen.font().width(StringUtil.stripColor(titleFull)) > titleMaxW) {
            titleToDraw = screen.font().plainSubstrByWidth(titleFull, titleMaxW - screen.font().width("…")) + "…";
        }
        ChroniclesUIKit.drawString(g, screen.font(), titleToDraw, cl + 8, 7, screen.palette.text);
        if (screen.testMode)
            g.fill(cl, ChronicleOverviewScreen.TOOLBAR_Y - 1, cr, ChronicleOverviewScreen.TOOLBAR_Y, 0xFFCC2222);
        if (screen.pictureEditMode != null) {
            g.fill(cl, ChronicleOverviewScreen.TOOLBAR_Y - 1, cr, ChronicleOverviewScreen.TOOLBAR_Y, 0xFFFFCC33);
            String hint = "§e🖼 Editing picture. Scroll to resize, Alt+scroll to rotate (shift = fine), drag to move, right-click/Esc to finish";
            ChroniclesUIKit.drawCenteredString(g, screen.font(), hint, (cl + cr) / 2, 7, 0xFFFFEEAA);
        }

        g.enableScissor(0, 0, screen.sidebar.sidebarVisualW(), ChronicleOverviewScreen.TOOLBAR_Y);
        renderQuestbookTitle(g, mx, my);
        g.disableScissor();

        String zoomStr = Math.round(screen.zoom * 100) + "%";
        int zw = screen.font().width(zoomStr);
        int zx = cr - zw - 10, zy = 3;
        g.fill(zx - 3, zy, zx + zw + 3, zy + 13, 0x22FFFFFF);
        ChroniclesUIKit.drawString(g, screen.font(), "§7" + zoomStr, zx, zy + 3, screen.palette.textDim);

        int unclaimedCount = screen.unclaimedRewardCount();
        if (unclaimedCount > 0) {
            String claimLabel = "§d🎁 " + unclaimedCount;
            int cw = screen.font().width(StringUtil.stripColor(claimLabel));
            int cpx = zx - cw - 18, cpy = 3;
            boolean claimHov = mx >= cpx - 3 && mx < cpx + cw + 5 && my >= cpy && my < cpy + 13;
            g.fill(cpx - 3, cpy, cpx + cw + 5, cpy + 13, claimHov ? 0x44FFFFFF : 0x33AA4488);
            ChroniclesUIKit.drawString(g, screen.font(), claimLabel, cpx, cpy + 3, screen.palette.textDim, false);
            if (claimHov) {
                screen.pendingDeferredDraws.add(() -> g.renderTooltip(screen.font(),
                        ChroniclesUIKit.lit("§7" + unclaimedCount + " quest(s) with unclaimed rewards - click to open"),
                        mx, my));
            }
            zx = cpx;
        }

        ChronicleOverviewScreen.EmergencyPill emergency = emergencyPill();
        if (emergencyPillVisible(emergency)) {
            boolean anyReady = emergency.ready() > 0;
            String plain = emergencyPillLabel(emergency);
            int ew = screen.font().width(plain);
            int epx = zx - ew - 18, epy = 3;
            boolean emergencyHov = mx >= epx - 3 && mx < epx + ew + 5 && my >= epy && my < epy + 13;
            g.fill(epx - 3, epy, epx + ew + 5, epy + 13,
                    emergencyHov ? 0x44FFFFFF : anyReady ? 0x44B08A2E : 0x22FFFFFF);
            ChroniclesUIKit.drawString(g, screen.font(), (anyReady ? "§6" : "§8") + plain, epx, epy + 3,
                    screen.palette.textDim,
                    false);
            if (emergencyHov) {
                String tip = anyReady ?
                        "§7" + emergency.ready() + " quest(s) with emergency items ready - click to open" :
                        "§7Emergency items ready again in " +
                                QuestProgressTracker.formatDuration(emergency.soonestCooldownMs()) +
                                " - click to open";
                screen.pendingDeferredDraws.add(() -> g.renderTooltip(screen.font(), ChroniclesUIKit.lit(tip), mx, my));
            }
            zx = epx;
        }

        if (screen.playerData != null) {
            boolean autoOn = screen.playerData.isAutoClaimEnabled();
            String autoPlain = autoClaimPillLabel();
            int aw = screen.font().width(autoPlain);
            int apx = zx - aw - 18, apy = 3;
            boolean autoHov = mx >= apx - 3 && mx < apx + aw + 5 && my >= apy && my < apy + 13;
            g.fill(apx - 3, apy, apx + aw + 5, apy + 13, autoHov ? 0x44FFFFFF : autoOn ? 0x3355CC77 : 0x22FFFFFF);
            ChroniclesUIKit.drawString(g, screen.font(), (autoOn ? "§a" : "§8") + autoPlain, apx, apy + 3,
                    screen.palette.textDim,
                    false);
            if (autoHov) {
                screen.pendingDeferredDraws.add(() -> g.renderTooltip(screen.font(), ChroniclesUIKit.lit(autoOn ?
                        "§7Auto-claim is on - click to choose what it claims" :
                        "§7Auto-claim is off - click to set it up"), mx, my));
            }
            zx = apx;
        }

        String gridLabel = !screen.gridSnapEnabled ? "§8Grid: §c§loff" :
                (screen.gridSnap == 1) ? "§8Grid: §afree" : "§8Grid: §7" + screen.gridSnap;
        int gw = screen.font().width(StringUtil.stripColor(gridLabel));
        int gpx = zx - gw - 18, gpy = 3;
        boolean gridHov = mx >= gpx - 3 && mx < gpx + gw + 5 && my >= gpy && my < gpy + 13;
        g.fill(gpx - 3, gpy, gpx + gw + 5, gpy + 13, gridHov ? 0x44FFFFFF : 0x22FFFFFF);
        ChroniclesUIKit.drawString(g, screen.font(), gridLabel, gpx, gpy + 3, screen.palette.textDim, false);
        if (gridHov) {

            screen.pendingDeferredDraws.add(() -> g.renderTooltip(screen.font(),
                    ChroniclesUIKit.lit("§7Click to cycle canvas snap grid size"), mx, my));
        }

        if (screen.isDevMode) {
            String sgLabel = screen.editorState.subgraphMode ? "§b⊛ Subgraph: " + screen.subgraphNodes.size() :
                    "§8⊛ Subgraph";
            int sgw = screen.font().width(StringUtil.stripColor(sgLabel));
            int sgx = gpx - sgw - 18, sgy = 3;
            boolean sgHov = mx >= sgx - 3 && mx < sgx + sgw + 5 && my >= sgy && my < sgy + 13;
            g.fill(sgx - 3, sgy, sgx + sgw + 5, sgy + 13,
                    screen.editorState.subgraphMode ? 0x4444CCFF : (sgHov ? 0x44FFFFFF : 0x22FFFFFF));
            ChroniclesUIKit.drawString(g, screen.font(), sgLabel, sgx, sgy + 3, screen.palette.textDim, false);
            if (sgHov) {
                screen.pendingDeferredDraws.add(() -> g.renderComponentTooltip(screen.font(), List.of(
                        ChroniclesUIKit.lit("§b⊛ Subgraph mode"),
                        ChroniclesUIKit.lit("§7Dims every quest that isn't an ancestor or"),
                        ChroniclesUIKit.lit("§7descendant of the currently selected one,"),
                        ChroniclesUIKit.lit("§7isolating just its dependency chain."),
                        ChroniclesUIKit.lit("§8Click a quest to select it, then click this"),
                        ChroniclesUIKit.lit("§8pill (or press G) to toggle it on/off.")), mx, my));
            }

        }

        g.enableScissor(0, ChronicleOverviewScreen.TOOLBAR_Y, screen.width, ChronicleOverviewScreen.HEADER_H);
        renderToolbar(g, mx, my, cl, cr);
        g.disableScissor();
    }

    private boolean questbookTitleHovered(int mx, int my) {
        return !screen.sidebar.isSidebarNarrow() && mx >= 0 && mx < screen.sidebarW() - 1 && my >= 0 &&
                my < ChronicleOverviewScreen.TOOLBAR_Y;
    }

    private void renderQuestbookTitle(GuiGraphics g, int mx, int my) {
        if (screen.sidebar.isSidebarNarrow()) return;
        boolean hov = questbookTitleHovered(mx, my);
        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        if (hov) g.fill(0, 0, screen.sidebarW() - 1, ChronicleOverviewScreen.TOOLBAR_Y - 1, 0x14FFFFFF);

        Item iconItem = s.getQuestbookIconItem();
        int iconY = (ChronicleOverviewScreen.TOOLBAR_Y - 1 - 16) / 2;
        g.renderItem(new ItemStack(iconItem), 3, iconY);

        String name = s.getQuestbookName();
        int maxW = screen.sidebarW() - 22;
        if (screen.font().width(name) > maxW) name = screen.font().plainSubstrByWidth(name, maxW - 4) + "…";
        ChroniclesUIKit.drawString(g, screen.font(), (hov ? "§f" : "§7") + name, 21, 7,
                hov ? screen.palette.text : screen.palette.textDim,
                false);
    }

    private ToolbarPanel.Colors toolbarColors() {
        return new ToolbarPanel.Colors(screen.palette.panelDark, screen.palette.border, screen.palette.text,
                screen.palette.textDim);
    }

    private int[][] filterPillBounds(int cl, int cr) {
        return screen.toolbarPanel.filterPillBounds(cl, cr, ChronicleOverviewScreen.TOOLBAR_Y,
                ChronicleOverviewScreen.TOOLBAR_H, screen.font(), screen.isDevMode);
    }

    private void renderToolbar(GuiGraphics g, int mx, int my, int cl, int cr) {
        screen.toolbarPanel.render(g, screen.font(), mx, my, screen.width, cl, cr, ChronicleOverviewScreen.TOOLBAR_Y,
                ChronicleOverviewScreen.TOOLBAR_H, toolbarColors(), screen.stateFilter,
                screen.hideCompleted, screen.minimapOpen, screen.statsPanel.isOpen(), screen.isDevMode,
                screen.editorState.activeTool,
                screen.pendingDeferredDraws::add);
    }

    private boolean hitsToolbarBtn(String key, double mx, double my) {
        return screen.toolbarPanel.hits(key, mx, my);
    }

    private String autoClaimPillLabel() {
        return screen.playerData != null && screen.playerData.isAutoClaimEnabled() ? "⚡ On" : "⚡ Off";
    }

    private ChronicleOverviewScreen.EmergencyPill emergencyPill() {
        long now = System.currentTimeMillis();
        if (now - emergencyPillStampMs < 250) return cachedEmergencyPill;
        emergencyPillStampMs = now;

        int ready = 0;
        long soonest = 0;
        if (screen.playerData != null) {
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                if (node.isFlagDisabled(null)) continue;
                if (screen.playerData.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.ACTIVE) continue;
                if (!ClientEmergencyState.hasEmergencyItems(node.getId())) continue;
                ClientEmergencyState.Availability av = ClientEmergencyState.availability(screen.playerData,
                        node.getId());
                if (av.state() == ClientEmergencyState.State.READY) {
                    ready++;
                } else if (av.state() == ClientEmergencyState.State.COOLDOWN) {
                    soonest = soonest == 0 ? av.remainingMs() : Math.min(soonest, av.remainingMs());
                }
            }
            for (ResourceLocation stationId : ClientEmergencyState.stationIds()) {
                ClientEmergencyState.Availability av = ClientEmergencyState.availability(screen.playerData, stationId);
                if (av.state() == ClientEmergencyState.State.READY) {
                    ready++;
                } else if (av.state() == ClientEmergencyState.State.COOLDOWN) {
                    soonest = soonest == 0 ? av.remainingMs() : Math.min(soonest, av.remainingMs());
                }
            }
        }
        cachedEmergencyPill = new ChronicleOverviewScreen.EmergencyPill(ready, soonest);
        return cachedEmergencyPill;
    }

    private static boolean emergencyPillVisible(ChronicleOverviewScreen.EmergencyPill pill) {
        return pill.ready() > 0 || pill.soonestCooldownMs() > 0;
    }

    private static String emergencyPillLabel(ChronicleOverviewScreen.EmergencyPill pill) {
        return pill.ready() > 0 ? "⚠ " + pill.ready() : "⚠ " + ClientEmergencyState.compactDuration(
                pill.soonestCooldownMs());
    }

    private ChronicleOverviewScreen.EmergencyPill cachedEmergencyPill = new ChronicleOverviewScreen.EmergencyPill(0, 0);

    private long emergencyPillStampMs = 0;
}

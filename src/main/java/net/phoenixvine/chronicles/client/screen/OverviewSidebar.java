package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.client.registry.ChroniclesLangPack;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.CategoryRegistry;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;

final class OverviewSidebar {

    private final ChronicleOverviewScreen screen;

    OverviewSidebar(ChronicleOverviewScreen screen) {
        this.screen = screen;
        this.panCanvasFn = screen::panCanvas;
    }

    public int sidebarW() {
        return screen.sidebarPanel.width();
    }

    boolean isSidebarNarrow() {
        return screen.sidebarPanel.isNarrow();
    }

    int sidebarVisualW() {
        return screen.sidebarPanel.visualWidth();
    }

    void updateSidebarHoverPeek(int mx, int my) {
        screen.sidebarPanel.updateHoverPeek(mx, my, panCanvasFn);
    }

    private void openSidebarContextMenu(SidebarRow row, int mx, int my) {
        screen.chapters.chapterActions.openSidebarContextMenu(row, mx, my);
    }

    public List<SidebarRow> buildSidebarRows() {
        return screen.sidebarPanel.buildRows(screen::friendly, screen.progressLookup(), screen.buildChapterList());
    }

    int sidebarScrollAreaHeight() {
        return screen.sidebarPanel.scrollAreaHeight(screen.height);
    }

    int sidebarContentHeight() {
        return screen.sidebarPanel.contentHeight(screen.height, screen::friendly, screen.progressLookup(),
                screen.buildChapterList());
    }

    boolean tryHandleSidebarContextMenuClick(double mx, double my, int btn) {
        if (screen.sidebarPanel.contextMenuOpen()) {
            if (btn == 0) screen.sidebarPanel.handleContextMenuClick((int) mx, (int) my);
            else screen.sidebarPanel.closeContextMenu();
            return true;
        }
        return false;
    }

    boolean tryHandleSidebarCollapseToggleClick(double mx, double my) {
        if (!screen.sidebarPanel.isHoverSidebar() && sidebarCollapseToggleHovered((int) mx, (int) my)) {
            screen.sidebarPanel.setCollapsed(!screen.sidebarPanel.collapsed());
            screen.sidebarPanel.resetScroll();
            screen.rebuild();
            return true;
        }
        return false;
    }

    boolean tryHandleSidebarPanelClick(double mx, double my) {
        if (!isSidebarNarrow()) {

            if (gearHovered((int) mx, (int) my) && Minecraft.getInstance() != null) {
                Minecraft.getInstance().setScreen(new LangEditorScreen(screen));
                return true;
            }

            if (newCatButtonHovered((int) mx, (int) my)) {
                if (Minecraft.getInstance() != null)
                    Minecraft.getInstance().setScreen(new NewChapterChoiceScreen(screen));
                return true;
            }

            int scrollTop = ChronicleOverviewScreen.HEADER_H + 1 + SidebarPanel.SIDEBAR_COLLAPSE_TOGGLE_H;
            int scrollBottom = scrollTop + sidebarScrollAreaHeight();
            if (mx < sidebarW() - 1 && my >= scrollTop && my < scrollBottom) {
                for (SidebarRow row : buildSidebarRows()) {
                    if (my < row.y() || my >= row.y() + row.height()) continue;

                    if (screen.isDevMode) {
                        screen.sidebarPanel.setDragRow(row);
                        screen.sidebarPanel.setDragStart((int) mx, (int) my);
                        screen.sidebarPanel.setDragMoved(false);
                        return true;
                    }
                    if (row.isFolder()) {
                        CategoryRegistry.toggleCollapsed(row.id());
                        CategoryRegistry.save();
                        screen.rebuild();
                    } else if (row.locked()) {

                        screen.setFeedback("§7Locked. Complete a quest in the parent chapter first");
                    } else {
                        screen.selectedChapter = row.id();
                        screen.editorState.selectedNode = null;
                        PhantasiaCompat.closePreview(screen.phantasiaPreview);
                        screen.phantasiaPreview = null;

                        screen.ctxOpen = false;
                        screen.ctxMoveCatOpen = false;
                        screen.rebuild();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    boolean tryHandleLangExportClick(double mx, double my, int btn) {
        if (btn == 1 && gearHovered((int) mx, (int) my) && screen.isDevMode) {
            Path base = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("phoenix_chronicles");
            LangEditorScreen.writeEnUsJson(base);

            ChroniclesLangPack.reload();
            screen.setFeedback("§aExported lang/en_us.json");
            return true;
        }
        return false;
    }

    boolean tryHandleSidebarRowContextMenuClick(double mx, double my, int btn) {
        if (btn == 1 && screen.isDevMode && !isSidebarNarrow()) {
            SidebarRow hitRow = sidebarRowAt(buildSidebarRows(), (int) mx, (int) my);
            if (hitRow != null) {
                openSidebarContextMenu(hitRow, (int) mx, (int) my);
                return true;
            }
        }
        return false;
    }

    boolean tryHandleSidebarRowDragThreshold(double mx, double my, int btn) {
        if (btn == 0 && screen.sidebarPanel.dragRow() != null) {
            if (!screen.sidebarPanel.dragMoved() &&
                    (Math.abs(mx - screen.sidebarPanel.dragStartX()) > SidebarPanel.SIDEBAR_DRAG_THRESHOLD ||
                            Math.abs(my - screen.sidebarPanel.dragStartY()) > SidebarPanel.SIDEBAR_DRAG_THRESHOLD)) {
                screen.sidebarPanel.setDragMoved(true);
            }
            return true;
        }
        return false;
    }

    boolean tryHandleSidebarRowDrop(double mx, double my, int btn) {
        if (btn == 0 && screen.sidebarPanel.dragRow() != null) {
            SidebarRow source = screen.sidebarPanel.dragRow();
            boolean moved = screen.sidebarPanel.dragMoved();
            screen.sidebarPanel.setDragRow(null);
            screen.sidebarPanel.setDragMoved(false);
            if (!moved) {

                if (source.isFolder()) {
                    CategoryRegistry.toggleCollapsed(source.id());
                    CategoryRegistry.save();
                    screen.rebuild();
                } else {
                    screen.selectedChapter = source.id();
                    screen.editorState.selectedNode = null;
                    PhantasiaCompat.closePreview(screen.phantasiaPreview);
                    screen.phantasiaPreview = null;
                    screen.ctxOpen = false;
                    screen.ctxMoveCatOpen = false;
                    screen.rebuild();
                }
                return true;
            }
            handleSidebarDrop(source, (int) mx, (int) my);
            return true;
        }
        return false;
    }

    private boolean sidebarCollapseToggleHovered(int mx, int my) {
        return screen.sidebarPanel.collapseToggleHovered(mx, my);
    }

    SidebarPanel.Colors sidebarColors() {
        return new SidebarPanel.Colors(screen.palette.border, screen.palette.borderLit, screen.palette.text,
                screen.palette.textDim,
                screen.palette.textFaint, screen.palette.panelDark,
                screen.palette.selTab, screen.palette.progFill, ChronicleOverviewScreen.C_PROG_ACT);
    }

    void renderSidebarPanel(GuiGraphics g, int mx, int my) {
        screen.sidebarPanel.renderPanel(g, screen.font(), mx, my, screen.width, screen.height, sidebarColors(),
                screen.isDevMode, screen.selectedChapter,
                screen::friendly, screen.progressLookup(), screen.attentionLookup(), screen.rewardsLookup(),
                screen.pendingDeferredDraws::add,
                screen.buildChapterList());
    }

    private SidebarRow sidebarRowAt(List<SidebarRow> rows, int mx, int my) {
        return screen.sidebarPanel.rowAt(rows, mx, my);
    }

    private void handleSidebarDrop(SidebarRow source, int mx, int my) {
        screen.sidebarPanel.handleDrop(source, mx, my, screen::friendly, screen.progressLookup(),
                screen::setFeedback, screen::rebuild, screen.buildChapterList());
    }

    private boolean gearHovered(int mx, int my) {
        return screen.sidebarPanel.gearHovered(mx, my, screen.height);
    }

    private boolean newCatButtonHovered(int mx, int my) {
        return screen.sidebarPanel.newCatButtonHovered(mx, my, screen.height, screen.isDevMode);
    }

    public void openNewChapterForm() {
        if (Minecraft.getInstance() != null) {
            Minecraft.getInstance().setScreen(new NewChapterScreen(screen, id -> {
                screen.selectedChapter = id;
                screen.rebuild();
                screen.setFeedback("Chapter '%s' created", screen.friendly(id));
            }));
        }
    }

    void renderSidebarNewChapterButton(GuiGraphics g, int mx, int my) {
        screen.sidebarPanel.renderNewChapterButton(g, screen.font(), mx, my, screen.height, screen.isDevMode,
                sidebarColors());
    }

    void renderSidebarGear(GuiGraphics g, int mx, int my) {
        screen.sidebarPanel.renderGear(g, screen.font(), mx, my, screen.width, screen.height, screen.isDevMode,
                sidebarColors(), screen.pendingDeferredDraws::add);
    }

    private final BiConsumer<Integer, Integer> panCanvasFn;
}

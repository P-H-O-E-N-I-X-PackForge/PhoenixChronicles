package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.client.util.BackgroundPictureConfig;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class OverviewContextMenus {

    private final ChronicleOverviewScreen screen;

    OverviewContextMenus(ChronicleOverviewScreen screen) {
        this.screen = screen;
        this.ctxMenuBuilder = new NodeContextMenuBuilder(screen, screen, screen, screen.editorState);
    }

    public void openContextMenuFor(int mx, int my, List<SidebarPanel.MenuAction> actions) {
        screen.sidebarPanel.openContextMenu(mx, my, actions, screen.width, screen.height);
    }

    boolean tryHandleMoveCatDropdownScroll(double mx, double my, double delta) {
        if (screen.ctxMoveCatOpen && screen.ctxNode != null) {
            List<String> cats = screen.buildChapterList();
            cats.remove("ALL");
            int subX = ctxMoveCatX(cats.size());
            int subY = ctxMoveCatYClamped(buildCtxItems(), cats.size());
            int visibleRows = Math.min(cats.size(), ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS);
            int subH = visibleRows * ChronicleOverviewScreen.CTX_ROW + 4;
            if (mx >= subX && mx <= subX + ChronicleOverviewScreen.CTX_W && my >= subY && my <= subY + subH) {
                int maxScroll = Math.max(0, cats.size() - ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS);
                screen.ctxMoveCatScroll = Math.max(0,
                        Math.min(maxScroll, screen.ctxMoveCatScroll - (int) Math.signum(delta)));
                return true;
            }
        }
        return false;
    }

    boolean tryHandleDepLineContextMenuClick(double mx, double my, int btn) {
        if (screen.depLineRenderer.isContextMenuOpen() && btn == 0) {

            screen.depLineRenderer.handleContextMenuClick((int) mx, (int) my, screen.width, screen.height,
                    screen::buildLineCache, screen::setFeedback, screen::openLineSettingsFor, screen::pushUndo);
            screen.depLineRenderer.closeContextMenu();
            return true;
        }
        if (screen.depLineRenderer.isContextMenuOpen()) {
            screen.depLineRenderer.closeContextMenu();
            return true;
        }
        return false;
    }

    boolean tryHandlePictureContextMenuClick(double mx, double my, int btn) {
        if (screen.pictureCtxMenu.isOpen() && btn == 0) {
            screen.pictureCtxMenu.mouseClicked(screen, mx, my, btn);
            return true;
        }
        if (screen.pictureCtxMenu.isOpen()) {
            screen.pictureCtxMenu.close();
            return true;
        }
        return false;
    }

    boolean tryHandleNodeContextMenuClick(double mx, double my, int btn) {
        if (screen.ctxOpen && btn == 0) {
            if (handleCtxClick((int) mx, (int) my)) return true;
            screen.ctxOpen = false;
            screen.ctxMoveCatOpen = false;
            return true;
        }
        return false;
    }

    boolean tryHandleDevRightClickContextMenu(double mx, double my, int btn, int cl, int cr) {
        if (btn == 1 && screen.isDevMode && mx > cl && mx < cr) {

            if (screen.ctxOpen) {
                screen.ctxOpen = false;
                return true;
            }
            QuestNode hit = null;
            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    hit = QuestTreeRegistry.getQuest(e.getKey());
                    break;
                }
            }
            QuestGroup hitGrp = (hit == null) ? screen.groupAtLabelBar(mx, my, cl) : null;

            if (hit == null && hitGrp == null) {
                BackgroundPictureConfig.Picture hitPic = screen.pictureAt(mx, my, cl);
                if (hitPic != null) {
                    screen.ctxOpen = false;
                    screen.pictureCtxMenu.open((int) mx, (int) my, hitPic);
                    return true;
                }
            }

            if (hit == null && hitGrp == null && screen.depLineRenderer.tryOpenContextMenuAt((int) mx, (int) my, 6)) {

                return true;
            }
            openCtx((int) mx, (int) my, hit, hitGrp);
            return true;
        }
        return false;
    }

    void handleNonDevRightClick(double mx, double my, int btn, int cl, int cr) {
        if (btn == 1 && !screen.isDevMode && mx > cl && mx < cr) {
            boolean hitNode = false;
            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    QuestNode node = QuestTreeRegistry.getQuest(e.getKey());
                    if (node != null && screen.getState(node) == QuestState.LOCKED) {
                        screen.nonDevCtxNode = node;
                        nonDevCtxX = (int) mx;
                        nonDevCtxY = (int) my;
                        hitNode = true;
                    }
                    break;
                }
            }
            if (!hitNode) {
                screen.unlockPathHighlight.clear();
                screen.nonDevCtxNode = null;
            }
        }
    }

    private List<ChronicleOverviewScreen.CtxItem> buildNonDevCtxItems() {
        List<ChronicleOverviewScreen.CtxItem> items = new ArrayList<>();
        if (screen.nonDevCtxNode == null) return items;
        items.add(new ChronicleOverviewScreen.CtxItem("👁 Show unlock path", "§b", false, false,
                () -> screen.chapters.computeUnlockPath(screen.nonDevCtxNode)));
        QuestNode linkTarget = screen.nonDevCtxNode.isLinkStub() ? screen.resolveLinkTarget(screen.nonDevCtxNode) :
                null;
        if (linkTarget != null) {
            items.add(new ChronicleOverviewScreen.CtxItem("🔗 Jump to linked quest", "§b", false, false,
                    () -> screen.navigateToNode(linkTarget)));
        }
        return items;
    }

    boolean tryHandleNonDevCtxMenuClick(double mx, double my, int btn) {
        if (screen.nonDevCtxNode != null && btn == 0) {
            List<ChronicleOverviewScreen.CtxItem> items = buildNonDevCtxItems();
            int iy = nonDevCtxY;
            for (ChronicleOverviewScreen.CtxItem item : items) {
                if (mx >= nonDevCtxX && mx <= nonDevCtxX + ChronicleOverviewScreen.CTX_W && my >= iy &&
                        my <= iy + ChronicleOverviewScreen.CTX_ROW) {
                    item.action().run();
                    screen.nonDevCtxNode = null;
                    return true;
                }
                iy += ChronicleOverviewScreen.CTX_ROW;
            }
            screen.nonDevCtxNode = null;
            return true;
        }
        return false;
    }

    void renderNonDevCtxMenu(GuiGraphics g, int mx, int my) {
        if (screen.nonDevCtxNode == null) return;
        List<ChronicleOverviewScreen.CtxItem> items = buildNonDevCtxItems();
        int menuH = items.size() * ChronicleOverviewScreen.CTX_ROW + 2;
        int x = Math.min(nonDevCtxX, screen.width - ChronicleOverviewScreen.CTX_W - 4);
        int y = Math.min(nonDevCtxY, screen.height - menuH - 4);

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.flush();
        RenderSystem.disableDepthTest();

        g.fill(x, y, x + ChronicleOverviewScreen.CTX_W, y + menuH, ChronicleOverviewScreen.C_CTX_BG);
        g.fill(x, y, x + ChronicleOverviewScreen.CTX_W, y + 1, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x, y + menuH - 1, x + ChronicleOverviewScreen.CTX_W, y + menuH, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x, y, x + 1, y + menuH, ChronicleOverviewScreen.C_CTX_BORDER);
        g.fill(x + ChronicleOverviewScreen.CTX_W - 1, y, x + ChronicleOverviewScreen.CTX_W, y + menuH,
                ChronicleOverviewScreen.C_CTX_BORDER);

        int iy = y + 1;
        for (ChronicleOverviewScreen.CtxItem item : items) {
            boolean hov = mx >= x && mx <= x + ChronicleOverviewScreen.CTX_W && my >= iy &&
                    my <= iy + ChronicleOverviewScreen.CTX_ROW;
            if (hov) g.fill(x + 1, iy, x + ChronicleOverviewScreen.CTX_W - 1, iy + ChronicleOverviewScreen.CTX_ROW,
                    ChronicleOverviewScreen.C_CTX_HOVER);
            ChroniclesUIKit.drawString(g, screen.font(), item.color() + item.label(), x + 6, iy + 4,
                    ChronicleOverviewScreen.C_CTX_TEXT);
            iy += ChronicleOverviewScreen.CTX_ROW;
        }

        RenderSystem.enableDepthTest();
        g.flush();
        g.pose().popPose();
    }

    private boolean handleCtxClick(int mx, int my) {
        List<ChronicleOverviewScreen.CtxItem> items = buildCtxItems();

        int lmx = Math.round(screen.ctxX + (mx - screen.ctxX) / screen.ctxScale);
        int lmy = Math.round(screen.ctxY + (my - screen.ctxY) / screen.ctxScale);
        int x = screen.ctxX, y = screen.ctxY + 2;
        if (screen.ctxNode != null) y += ChronicleOverviewScreen.CTX_ROW;

        for (ChronicleOverviewScreen.CtxItem item : items) {
            if (item.isSep()) {
                y += ChronicleOverviewScreen.CTX_SEP;
                continue;
            }
            if (lmx >= x && lmx <= x + ChronicleOverviewScreen.CTX_W && lmy >= y &&
                    lmy <= y + ChronicleOverviewScreen.CTX_ROW) {
                item.action().run();
                return true;
            }
            y += ChronicleOverviewScreen.CTX_ROW;
        }

        if (screen.ctxMoveCatOpen) {
            List<String> cats = screen.buildChapterList();
            cats.remove("ALL");
            int subX = ctxMoveCatX(cats.size());
            int subY = ctxMoveCatYClamped(items, cats.size());
            int visibleRows = Math.min(cats.size(), ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS);
            for (int i = screen.ctxMoveCatScroll; i <
                    Math.min(cats.size(), screen.ctxMoveCatScroll + visibleRows); i++) {
                int ry = subY + (i - screen.ctxMoveCatScroll) * ChronicleOverviewScreen.CTX_ROW;
                if (mx >= subX && mx <= subX + ChronicleOverviewScreen.CTX_W && my >= ry &&
                        my <= ry + ChronicleOverviewScreen.CTX_ROW) {
                    String newCat = cats.get(i);
                    if (screen.ctxNode != null) {
                        QuestNode movedNode = screen.ctxNode;
                        String oldCat = movedNode.getChapter();
                        movedNode.setChapter(newCat);
                        screen.saveNodeChapterToDisk(movedNode, newCat);
                        screen.setFeedback("Moved to %s", screen.friendly(newCat));
                        screen.pushUndo("Undo: quest moved back to " + screen.friendly(oldCat), () -> {
                            movedNode.setChapter(oldCat);
                            screen.saveNodeChapterToDisk(movedNode, oldCat);
                            screen.rebuild();
                        }, () -> {
                            movedNode.setChapter(newCat);
                            screen.saveNodeChapterToDisk(movedNode, newCat);
                            screen.rebuild();
                        });
                    }
                    screen.ctxOpen = false;
                    screen.ctxMoveCatOpen = false;
                    screen.rebuild();
                    return true;
                }
            }
        }
        return false;
    }

    public List<ChronicleOverviewScreen.CtxItem> buildCtxItems() {
        return ctxMenuBuilder.buildCtxItems();
    }

    private void openCtx(int x, int y, QuestNode node) {
        ctxMenuBuilder.openCtx(x, y, node);
    }

    private void openCtx(int x, int y, QuestNode node, @Nullable QuestGroup group) {
        ctxMenuBuilder.openCtx(x, y, node, group);
    }

    public int menuHeight(List<ChronicleOverviewScreen.CtxItem> items) {
        return ctxMenuBuilder.menuHeight(items);
    }

    public int ctxMoveCatX(int catCount) {
        return ctxMenuBuilder.ctxMoveCatX(catCount);
    }

    public int ctxMoveCatYClamped(List<ChronicleOverviewScreen.CtxItem> items, int catCount) {
        return ctxMenuBuilder.ctxMoveCatYClamped(items, catCount);
    }

    private int nonDevCtxX, nonDevCtxY;

    private final NodeContextMenuBuilder ctxMenuBuilder;
}

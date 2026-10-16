package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.event.ChronicleKeyBindings;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.client.util.BackgroundPictureConfig;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import org.jetbrains.annotations.Nullable;

import java.util.*;

final class OverviewCanvasInput {

    private final ChronicleOverviewScreen screen;

    OverviewCanvasInput(ChronicleOverviewScreen screen) {
        this.screen = screen;
    }

    boolean handleCanvasZoomScroll(double mx, double my, double delta, int cl, int cr) {
        float oldPosZoom = screen.posZoom();
        float oldZoom = screen.zoom;
        screen.zoom = Math.max(ChronicleOverviewScreen.ZOOM_MIN, Math.min(ChronicleOverviewScreen.ZOOM_MAX,
                screen.zoom + (float) delta * ChronicleOverviewScreen.ZOOM_STEP));
        if (screen.zoom == oldZoom) return true;
        float newPosZoom = screen.posZoom();

        if (oldPosZoom != newPosZoom) {
            int canvasW = cr - cl, canvasH = screen.height - ChronicleOverviewScreen.HEADER_H;

            boolean cursorAnchored = !ChronicleKeyBindings.CURSOR_ZOOM.isDown();
            float anchorX = cursorAnchored ? (float) mx - cl : canvasW / 2f;
            float anchorY = cursorAnchored ? (float) my - ChronicleOverviewScreen.HEADER_H : canvasH / 2f;
            float worldCx = (anchorX - screen.viewOffX) / oldPosZoom;
            float worldCy = (anchorY - screen.viewOffY) / oldPosZoom;
            screen.viewOffX = (int) (anchorX - worldCx * newPosZoom);
            screen.viewOffY = (int) (anchorY - worldCy * newPosZoom);
        }

        screen.rescaleForZoom();
        return true;
    }

    boolean tryHandleLeftClickUi(double mx, double my, int cl, int cr) {
        if (screen.header.tryHandleQuestbookTitleClick(mx, my)) return true;
        if (screen.header.tryHandleHeaderBarClick(mx, my, cr)) return true;
        if (screen.header.tryHandleToolbarButtonClick(mx, my)) return true;
        if (screen.header.tryHandleFilterPillClick(mx, my, cl, cr)) return true;
        if (screen.sidebar.tryHandleSidebarCollapseToggleClick(mx, my)) return true;
        if (screen.sidebar.tryHandleSidebarPanelClick(mx, my)) return true;
        return false;
    }

    boolean tryHandleMultiSelectToggleClick(double mx, double my, int btn) {
        if (btn == 0 && screen.isDevMode && Screen.hasControlDown() && !Screen.hasShiftDown()) {
            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    if (screen.editorState.multiSelection.contains(e.getKey()))
                        screen.editorState.multiSelection.remove(e.getKey());
                    else screen.editorState.multiSelection.add(e.getKey());
                    return true;
                }
            }

            if (mx > screen.sidebarW()) {
                screen.boxSelecting = true;
                screen.boxSelectStartX = screen.boxSelectCurX = (int) mx;
                screen.boxSelectStartY = screen.boxSelectCurY = (int) my;
                screen.boxSelectBaseSelection = new HashSet<>(screen.editorState.multiSelection);
            } else {
                screen.editorState.multiSelection.clear();
            }
            return true;
        }
        return false;
    }

    void updateBoxSelection() {
        int x0 = Math.min(screen.boxSelectStartX, screen.boxSelectCurX),
                x1 = Math.max(screen.boxSelectStartX, screen.boxSelectCurX);
        int y0 = Math.min(screen.boxSelectStartY, screen.boxSelectCurY),
                y1 = Math.max(screen.boxSelectStartY, screen.boxSelectCurY);
        screen.editorState.multiSelection.clear();
        screen.editorState.multiSelection.addAll(screen.boxSelectBaseSelection);
        for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
            ChronicleOverviewScreen.NodeHitbox hb = e.getValue();
            if (!hb.visible) continue;
            boolean intersects = hb.x < x1 && hb.x + hb.w > x0 && hb.y < y1 && hb.y + hb.h > y0;
            if (intersects) screen.editorState.multiSelection.add(e.getKey());
        }
    }

    boolean tryHandlePickupPlaceReleaseClick(int btn) {
        if (btn == 0 && screen.pickupPlaceActive && screen.editorState.draggedNode != null) {
            boolean wasBulk = screen.editorState.bulkDragOrigPositions != null;
            screen.dragController.finalizeDragRelease();
            screen.editorState.draggedNode = null;
            screen.pickupPlaceActive = false;
            screen.dragForceSnap = false;
            screen.softRebuild();
            if (!wasBulk) screen.setFeedback("Placed");
            return true;
        }
        return false;
    }

    boolean tryHandleMiddleDragReleaseClick(int btn) {
        if (btn == 2 && screen.middleDragPickupActive && screen.editorState.draggedNode != null) {
            boolean wasBulk = screen.editorState.bulkDragOrigPositions != null;
            screen.dragController.finalizeDragRelease();
            screen.editorState.draggedNode = null;
            screen.middleDragPickupActive = false;
            screen.dragForceSnap = false;
            screen.softRebuild();
            if (!wasBulk) screen.setFeedback("Placed");
            return true;
        }
        return false;
    }

    boolean tryHandleQuickDepDragStart(double mx, double my, int btn) {
        if (btn == 0 && screen.isDevMode &&
                (screen.quickDepKeyDown || (Screen.hasAltDown() && !Screen.hasShiftDown()) ||
                        screen.editorState.activeTool == GraphEditorState.EditorTool.CONNECT)) {
            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    screen.linkDragSource = QuestTreeRegistry.getQuest(e.getKey());
                    screen.linkDragX = (int) mx;
                    screen.linkDragY = (int) my;
                    return true;
                }
            }
        }
        return false;
    }

    boolean tryHandleShiftDragStart(double mx, double my, int btn, int cl) {
        if (btn == 0 && screen.isDevMode && Screen.hasShiftDown()) {

            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    screen.editorState.draggedNode = QuestTreeRegistry.getQuest(e.getKey());
                    if (screen.editorState.draggedNode != null) {
                        screen.pickupPlaceActive = QuestChroniclesSettings.get()
                                .getNodeMoveMode() ==
                                QuestChroniclesSettings.NodeMoveMode.PICKUP_PLACE;
                        final int preX = screen.editorState.draggedNode.getCustomX(),
                                preY = screen.editorState.draggedNode.getCustomY();
                        screen.dragOrigX = preX;
                        screen.dragOrigY = preY;
                        final QuestNode capturedNode = screen.editorState.draggedNode;
                        screen.dragController.beginDragUndo(capturedNode, preX, preY);
                        screen.dragGrabX = (int) mx - e.getValue().getX();
                        screen.dragGrabY = (int) my - e.getValue().getY();
                        screen.editorState.selectedNode = screen.editorState.draggedNode;
                        if (screen.editorState.subgraphMode) screen.rebuildSubgraph();
                    }
                    return true;
                }
            }

            QuestGroup hitGrp = screen.groupAtLabelBar(mx, my, cl);
            if (hitGrp != null) {
                screen.editorState.draggedGroup = hitGrp;
                int sx = (int) (hitGrp.getX() * screen.posZoom()) + screen.viewOffX + cl;
                int sy = (int) (hitGrp.getY() * screen.posZoom()) + screen.viewOffY + ChronicleOverviewScreen.HEADER_H;
                groupDragGrabX = (int) mx - sx;
                groupDragGrabY = (int) my - sy;
                return true;
            }

            BackgroundPictureConfig.Picture hitPic = screen.pictureAt(mx, my, cl);
            if (hitPic != null) {

                pictureDragStartX = hitPic.x;
                pictureDragStartY = hitPic.y;
                draggedPicture = hitPic;
                int[] rect = BackgroundPictureRenderer.screenRect(hitPic, cl, ChronicleOverviewScreen.HEADER_H,
                        screen.posZoom(), screen.viewOffX, screen.viewOffY);
                pictureDragGrabX = (int) mx - rect[0];
                pictureDragGrabY = (int) my - rect[1];
                return true;
            }
        }
        return false;
    }

    boolean tryHandleMiddleClickNodeDragStart(double mx, double my, int btn) {
        if (btn == 2 && screen.isDevMode) {
            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    screen.editorState.draggedNode = QuestTreeRegistry.getQuest(e.getKey());
                    if (screen.editorState.draggedNode != null) {
                        final int preX = screen.editorState.draggedNode.getCustomX(),
                                preY = screen.editorState.draggedNode.getCustomY();
                        screen.dragOrigX = preX;
                        screen.dragOrigY = preY;
                        final QuestNode capturedNode = screen.editorState.draggedNode;
                        screen.dragController.beginDragUndo(capturedNode, preX, preY);
                        screen.dragGrabX = (int) mx - e.getValue().getX();
                        screen.dragGrabY = (int) my - e.getValue().getY();
                        screen.editorState.selectedNode = screen.editorState.draggedNode;
                        screen.dragForceSnap = true;
                        screen.middleDragPickupActive = QuestChroniclesSettings.get()
                                .isMiddleClickPickupPlace();
                        if (screen.editorState.subgraphMode) screen.rebuildSubgraph();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    boolean tryHandleShiftRightClickForceComplete(double mx, double my, int btn, int cl, int cr) {
        if (btn == 1 && Screen.hasShiftDown() && mx > cl && mx < cr) {
            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    QuestNode node = QuestTreeRegistry.getQuest(e.getKey());
                    if (node != null && (screen.getState(node) != QuestState.LOCKED || screen.isDevMode)) {
                        screen.onNodeClicked(node);
                        return true;
                    }
                }
            }
            return true;
        }
        return false;
    }

    boolean tryHandleCanvasClick(double mx, double my, int btn, int cl, int cr) {
        if (btn == 0 && mx > cl && mx < cr && my > ChronicleOverviewScreen.HEADER_H) {

            boolean handled = false;
            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                ChronicleOverviewScreen.NodeHitbox hb = e.getValue();
                if (hb.active && hb.isMouseOver(mx, my)) {
                    QuestNode node = QuestTreeRegistry.getQuest(e.getKey());
                    if (node != null) screen.onNodeClicked(node);
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                if (screen.isDevMode && screen.editorState.activeTool == GraphEditorState.EditorTool.PLACE) {
                    int canvasX = (int) ((mx - cl - screen.viewOffX) / screen.posZoom());
                    int canvasY = (int) ((my - ChronicleOverviewScreen.HEADER_H - screen.viewOffY) / screen.posZoom());
                    placeQuickQuest(canvasX, canvasY);
                    return true;
                }
                if (screen.isDevMode && Minecraft.getInstance() != null) {
                    long now = System.currentTimeMillis();
                    int imx = (int) mx, imy = (int) my;
                    if (Screen.hasShiftDown() && now - lastCanvasClickTime < 350 &&
                            Math.abs(imx - lastCanvasClickX) < 10 &&
                            Math.abs(imy - lastCanvasClickY) < 10) {

                        int canvasX = (int) ((imx - cl - screen.viewOffX) / screen.posZoom());
                        int canvasY = (int) ((imy - ChronicleOverviewScreen.HEADER_H - screen.viewOffY) /
                                screen.posZoom());
                        lastCanvasClickTime = 0;
                        Minecraft.getInstance()
                                .setScreen(new QuestCreatorScreen(screen, canvasX, canvasY, screen.selectedChapter));
                        return true;
                    }
                    lastCanvasClickTime = System.currentTimeMillis();
                    lastCanvasClickX = imx;
                    lastCanvasClickY = imy;
                }
                isPanning = true;
            }
            return true;
        }
        return false;
    }

    private void placeQuickQuest(int canvasX, int canvasY) {
        String idBase = "new_quest_";
        int n = 1;
        ResourceLocation questId;
        do {
            questId = ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", idBase + n);
            n++;
        } while (QuestTreeRegistry.getAllQuests().containsKey(questId));

        QuestNode node = new QuestNode(questId, Component.literal("New Quest"), Component.literal(""));
        node.setChapter(screen.selectedChapter);
        node.setCustomPosition(canvasX, canvasY);

        QuestTreeRegistry.injectDynamicQuestNode(node, null);
        QuestFileSaver.saveOneQuestToDisk(node, false);
        screen.rebuild();
        screen.setFeedback("Placed quest -- click again to place more");

        ResourceLocation placedId = questId;

        screen.editorState.multiSelection.add(placedId);
        screen.pushUndo("Undo: quest placed", () -> {
            QuestTreeRegistry.removeQuest(placedId);
            QuestFileSaver.deleteQuestFiles(node);
            screen.editorState.multiSelection.remove(placedId);
            screen.rebuild();
        }, () -> {
            QuestTreeRegistry.injectDynamicQuestNode(node, null);
            QuestFileSaver.saveOneQuestToDisk(node, false);
            screen.editorState.multiSelection.add(placedId);
            screen.rebuild();
        });
    }

    boolean tryHandleLeftButtonDragMove(double mx, double my, double dx, double dy) {
        if (screen.editorState.draggedGroup != null) {
            int cl = screen.sidebarW();
            int screenX = (int) mx - groupDragGrabX;
            int screenY = (int) my - groupDragGrabY;
            screen.editorState.draggedGroup.setX((int) ((screenX - cl - screen.viewOffX) / screen.posZoom()));
            screen.editorState.draggedGroup
                    .setY((int) ((screenY - ChronicleOverviewScreen.HEADER_H - screen.viewOffY) / screen.posZoom()));
            return true;
        }
        if (draggedPicture != null) {
            int cl = screen.sidebarW();

            int screenX = (int) mx - pictureDragGrabX;
            int screenY = (int) my - pictureDragGrabY;
            float dragZoom = BackgroundPictureRenderer.parallaxZoom(draggedPicture, screen.posZoom());
            float canvasX = (screenX - cl - BackgroundPictureRenderer.parallaxOffset(draggedPicture, screen.viewOffX)) /
                    dragZoom + draggedPicture.w / 2f;
            float canvasY = (screenY - ChronicleOverviewScreen.HEADER_H -
                    BackgroundPictureRenderer.parallaxOffset(draggedPicture, screen.viewOffY)) /
                    dragZoom + draggedPicture.h / 2f;
            draggedPicture.x = canvasX;
            draggedPicture.y = canvasY;
            return true;
        }
        if (screen.editorState.draggedNode != null) {
            screen.dragController.updateDraggedNodeScreenPos(mx, my, screen.dragController.currentDragSnap());
            screen.depLineRenderer.refreshEdgeEndpoints(screen.editorState.draggedNode.getId(),
                    screen::nodeCenterForLine,
                    screen.posZoom(),
                    QuestChroniclesSettings.get());
            return true;
        }
        if (isPanning) {
            screen.viewOffX += (int) dx;
            screen.viewOffY += (int) dy;
            screen.pendingPanDX += (int) dx;
            screen.pendingPanDY += (int) dy;
            return true;
        }
        return false;
    }

    boolean tryHandleLinkDragEnd(double mx, double my, int btn) {
        if (btn == 0 && screen.linkDragSource != null) {
            QuestNode src = screen.linkDragSource;
            screen.linkDragSource = null;
            for (Map.Entry<ResourceLocation, ChronicleOverviewScreen.NodeHitbox> e : screen.nodeButtons.entrySet()) {
                if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                    QuestNode target = QuestTreeRegistry.getQuest(e.getKey());
                    if (target != null && target != src && !target.getPrerequisites().contains(src) &&
                            !src.getPrerequisites().contains(target)) {
                        target.addPrerequisite(src);
                        target.setPrereqLink(src.getId(), true);
                        screen.saveNodePrereqsToDisk(target);
                        screen.setFeedback("§aLinked: %s → prereq of %s", src.getId().getPath(),
                                target.getId().getPath());
                        screen.buildLineCache();
                        screen.rebuild();
                        QuestNode undoTarget = target;
                        QuestNode undoSrc = src;
                        screen.pushUndo("Undo: dependency link removed", () -> {
                            undoTarget.removePrerequisite(undoSrc);
                            undoTarget.setPrereqLink(undoSrc.getId(), false);
                            screen.saveNodePrereqsToDisk(undoTarget);
                            screen.buildLineCache();
                            screen.rebuild();
                        }, () -> {
                            undoTarget.addPrerequisite(undoSrc);
                            undoTarget.setPrereqLink(undoSrc.getId(), true);
                            screen.saveNodePrereqsToDisk(undoTarget);
                            screen.buildLineCache();
                            screen.rebuild();
                        });
                    } else if (target != null && src.getPrerequisites().contains(target)) {
                        screen.setFeedback("§cCan't link. Would create a dependency cycle");
                    } else if (target != null && target.getPrerequisites().contains(src)) {
                        screen.setFeedback("§eAlready a prerequisite");
                    }
                    return true;
                }
            }
            return true;
        }
        return false;
    }

    boolean tryHandleLeftButtonDragRelease() {
        if (screen.editorState.draggedGroup != null) {
            QuestGroupManager.save(screen.groupsConfigPath());
            screen.editorState.draggedGroup = null;
            return true;
        }
        if (draggedPicture != null) {
            BackgroundPictureConfig.save();
            BackgroundPictureConfig.Picture movedPic = draggedPicture;
            float startX = pictureDragStartX, startY = pictureDragStartY;
            float endX = movedPic.x, endY = movedPic.y;
            if (startX != endX || startY != endY) {
                screen.pushUndo("Undo: picture moved back", () -> {
                    movedPic.x = startX;
                    movedPic.y = startY;
                    BackgroundPictureConfig.save();
                }, () -> {
                    movedPic.x = endX;
                    movedPic.y = endY;
                    BackgroundPictureConfig.save();
                });
            }
            draggedPicture = null;
            return true;
        }
        if (screen.editorState.draggedNode != null && !screen.pickupPlaceActive) {
            screen.dragController.finalizeDragRelease();
            screen.editorState.draggedNode = null;
            screen.dragForceSnap = false;
            screen.softRebuild();
            return true;
        }
        if (isPanning) {

            screen.buildLineCache();
        }
        isPanning = false;
        return false;
    }

    boolean tryHandleMiddleButtonDragRelease(int btn) {
        if (btn == 2 && screen.editorState.draggedNode != null && !screen.middleDragPickupActive) {
            screen.dragController.finalizeDragRelease();
            screen.editorState.draggedNode = null;
            screen.dragForceSnap = false;
            screen.softRebuild();
            return true;
        }
        return false;
    }

    private boolean isPanning = false;

    private long lastCanvasClickTime = 0;

    private int lastCanvasClickX = 0;

    private int lastCanvasClickY = 0;

    private int groupDragGrabX = 0, groupDragGrabY = 0;

    @Nullable
    private BackgroundPictureConfig.Picture draggedPicture = null;

    private int pictureDragGrabX = 0, pictureDragGrabY = 0;

    private float pictureDragStartX, pictureDragStartY;
}

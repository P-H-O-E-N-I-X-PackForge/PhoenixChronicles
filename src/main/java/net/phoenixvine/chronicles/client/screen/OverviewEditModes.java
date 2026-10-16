package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.client.util.BackgroundPictureConfig;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.*;

import java.util.*;

final class OverviewEditModes {

    private final ChronicleOverviewScreen screen;

    OverviewEditModes(ChronicleOverviewScreen screen) {
        this.screen = screen;
    }

    boolean tryHandleNodeSizeEditScroll(double delta) {
        if (screen.nodeSizeEditMode != null) {
            float step = 1.1f;
            if (Screen.hasShiftDown()) step = 1f + (step - 1f) * 0.3f;
            if (Screen.hasControlDown()) step = 1f + (step - 1f) * 0.3f;
            float factor = delta > 0 ? step : (1f / step);
            int newPx = Math.round(screen.nodeSizeEditMode.getNodePixelSize() * factor);
            screen.nodeSizeEditMode.setSizeOverridePx(newPx);
            screen.dragController.refreshNodeScreenPos(screen.nodeSizeEditMode);
            screen.setFeedback("Size: %dpx  (scroll to resize - shift/ctrl for finer steps, drag to move, " +
                    "right-click/Esc to finish)", screen.nodeSizeEditMode.getNodePixelSize());
            return true;
        }
        return false;
    }

    boolean tryHandlePictureEditScroll(double delta) {
        if (screen.pictureEditMode != null) {
            if (Screen.hasAltDown()) {
                screen.pictureEditMode.rotateBy((Screen.hasShiftDown() ? 1f : 5f) * (delta > 0 ? 1f : -1f));
                return true;
            }

            float step = Screen.hasShiftDown() ? 1.05f : 1.2f;
            float factor = delta > 0 ? step : (1f / step);
            screen.pictureEditMode.w = Math.max(ChronicleOverviewScreen.PIC_EDIT_MIN_SIZE,
                    Math.min(ChronicleOverviewScreen.PIC_EDIT_MAX_SIZE, screen.pictureEditMode.w * factor));
            screen.pictureEditMode.h = Math.max(ChronicleOverviewScreen.PIC_EDIT_MIN_SIZE,
                    Math.min(ChronicleOverviewScreen.PIC_EDIT_MAX_SIZE, screen.pictureEditMode.h * factor));
            return true;
        }
        return false;
    }

    boolean tryHandleNodeResizeModeClick(int btn) {
        if (screen.nodeSizeEditMode != null) {
            if (btn == 1) {
                finalizeNodeSizeEdit();
                screen.setFeedback("Node resize finished  (Ctrl+Z to undo the whole edit)");
                screen.softRebuild();
            }
            return true;
        }
        return false;
    }

    boolean tryHandlePictureEditModeClick(int btn) {
        if (screen.pictureEditMode != null) {
            if (btn == 1) {
                finalizePictureEdit();
                screen.setFeedback("Picture edit finished  (Ctrl+Z to undo the whole edit)");
            }
            return true;
        }
        return false;
    }

    boolean tryHandleNodeSizeEditDrag(int btn, double dx, double dy) {
        if (screen.nodeSizeEditMode != null) {
            if (btn == 0) {
                int snap = screen.dragController.currentDragSnap();
                screen.nodeSizeDragAccX += dx / screen.posZoom();
                screen.nodeSizeDragAccY += dy / screen.posZoom();
                int stepX = (int) Math.round(screen.nodeSizeDragAccX / snap) * snap;
                int stepY = (int) Math.round(screen.nodeSizeDragAccY / snap) * snap;
                if (stepX != 0 || stepY != 0) {
                    screen.nodeSizeEditMode.setCustomPosition(
                            screen.nodeSizeEditMode.getCustomX() + stepX,
                            screen.nodeSizeEditMode.getCustomY() + stepY);
                    screen.nodeSizeDragAccX -= stepX;
                    screen.nodeSizeDragAccY -= stepY;
                    screen.dragController.refreshNodeScreenPos(screen.nodeSizeEditMode);
                    screen.depLineRenderer.refreshEdgeEndpoints(screen.nodeSizeEditMode.getId(),
                            screen::nodeCenterForLine, screen.posZoom(),
                            QuestChroniclesSettings.get());
                }
            }
            return true;
        }
        return false;
    }

    boolean tryHandlePictureEditDrag(int btn, double dx, double dy) {
        if (screen.pictureEditMode != null) {
            if (btn == 0) {
                screen.pictureEditMode.x += (float) (dx / screen.posZoom());
                screen.pictureEditMode.y += (float) (dy / screen.posZoom());
            }
            return true;
        }
        return false;
    }

    void resetNodeSizeDragAccumulator(int btn) {
        if (btn == 0 && screen.nodeSizeEditMode != null) {
            screen.nodeSizeDragAccX = 0;
            screen.nodeSizeDragAccY = 0;
        }
    }

    void finalizePictureEdit() {
        BackgroundPictureConfig.Picture pic = screen.pictureEditMode;
        if (pic == null) return;
        float startX = screen.pictureEditStartX, startY = screen.pictureEditStartY;
        float startW = screen.pictureEditStartW, startH = screen.pictureEditStartH;
        float endX = pic.x, endY = pic.y, endW = pic.w, endH = pic.h;
        BackgroundPictureConfig.save();
        if (startX != endX || startY != endY || startW != endW || startH != endH) {
            screen.pushUndo("Undo: picture edit reverted", () -> {
                pic.x = startX;
                pic.y = startY;
                pic.w = startW;
                pic.h = startH;
                BackgroundPictureConfig.save();
            }, () -> {
                pic.x = endX;
                pic.y = endY;
                pic.w = endW;
                pic.h = endH;
                BackgroundPictureConfig.save();
            });
        }
        screen.pictureEditMode = null;
    }

    void finalizeNodeSizeEdit() {
        QuestNode node = screen.nodeSizeEditMode;
        if (node == null) return;
        QuestNode.NodeSize startSize = screen.nodeSizeEditStartSize;
        int startOverridePx = screen.nodeSizeEditStartOverridePx, startX = screen.nodeSizeEditStartX,
                startY = screen.nodeSizeEditStartY;
        int endOverridePx = node.getSizeOverridePx(), endX = node.getCustomX(), endY = node.getCustomY();

        QuestFileSaver.saveOneQuestToDisk(node, false);
        if (startOverridePx != endOverridePx || startX != endX || startY != endY) {
            screen.pushUndo("Undo: node resize reverted", () -> {
                node.setNodeSize(startSize);
                if (startOverridePx > 0) node.setSizeOverridePx(startOverridePx);
                node.setCustomPosition(startX, startY);
                QuestFileSaver.saveOneQuestToDisk(node, false);
                screen.softRebuild();
            }, () -> {
                node.setNodeSize(startSize);
                if (endOverridePx > 0) node.setSizeOverridePx(endOverridePx);
                node.setCustomPosition(endX, endY);
                QuestFileSaver.saveOneQuestToDisk(node, false);
                screen.softRebuild();
            });
        }
        screen.nodeSizeEditMode = null;
        screen.nodeSizeTypedBuffer = null;
    }
}

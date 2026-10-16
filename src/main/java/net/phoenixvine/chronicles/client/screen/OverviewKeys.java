package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.event.ChronicleKeyBindings;
import net.phoenixvine.chronicles.client.profiler.FrameProfiler;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.network.packet.C2STogglePinPacket;

import java.util.*;

final class OverviewKeys {

    private final ChronicleOverviewScreen screen;

    OverviewKeys(ChronicleOverviewScreen screen) {
        this.screen = screen;
    }

    boolean tryHandleSmokeTestKeybind(int key) {
        if (screen.isDevMode && key == 298) {
            ScreenClickSmokeTest.run(screen);
            return true;
        }
        return false;
    }

    boolean tryHandleNodeSizeTypedInput(int key) {
        if (screen.nodeSizeEditMode == null) return false;
        if (key >= 48 && key <= 57) {
            if (screen.nodeSizeTypedBuffer == null) screen.nodeSizeTypedBuffer = new StringBuilder();
            if (screen.nodeSizeTypedBuffer.length() < 3) screen.nodeSizeTypedBuffer.append((char) key);
            return true;
        }
        if (key == 259) {
            if (screen.nodeSizeTypedBuffer == null || screen.nodeSizeTypedBuffer.isEmpty()) return false;
            screen.nodeSizeTypedBuffer.deleteCharAt(screen.nodeSizeTypedBuffer.length() - 1);
            return true;
        }
        if (key == 257 || key == 335) {
            if (screen.nodeSizeTypedBuffer == null || screen.nodeSizeTypedBuffer.isEmpty()) return false;
            try {
                screen.nodeSizeEditMode.setSizeOverridePx(Integer.parseInt(screen.nodeSizeTypedBuffer.toString()));
                screen.dragController.refreshNodeScreenPos(screen.nodeSizeEditMode);
                screen.depLineRenderer.refreshEdgeEndpoints(screen.nodeSizeEditMode.getId(), screen::nodeCenterForLine,
                        screen.posZoom(),
                        QuestChroniclesSettings.get());
                screen.setFeedback("Size: %dpx  (scroll to resize - shift/ctrl for finer steps, drag to move, " +
                        "right-click/Esc to finish)", screen.nodeSizeEditMode.getNodePixelSize());
            } catch (NumberFormatException ignored) {}
            screen.nodeSizeTypedBuffer = null;
            return true;
        }
        return false;
    }

    boolean tryHandleDragCancelEscape(int key) {
        if (key == 256 && screen.editorState.draggedNode != null) {
            if (screen.editorState.bulkDragOrigPositions != null) {
                int count = screen.editorState.bulkDragOrigPositions.size();
                for (Map.Entry<ResourceLocation, int[]> e : screen.editorState.bulkDragOrigPositions.entrySet()) {
                    QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
                    if (n != null) {
                        n.setCustomPosition(e.getValue()[0], e.getValue()[1]);
                        screen.saveNodeToDisk(n);
                    }
                }
                screen.editorState.bulkDragOrigPositions = null;
                screen.setFeedback("Move cancelled (%d quests)", count);
            } else {
                screen.editorState.draggedNode.setCustomPosition(screen.dragOrigX, screen.dragOrigY);
                screen.saveNodeToDisk(screen.editorState.draggedNode);
                screen.setFeedback("Move cancelled");
            }
            screen.editorState.draggedNode = null;
            screen.pickupPlaceActive = false;
            screen.middleDragPickupActive = false;
            screen.dragForceSnap = false;
            screen.rebuild();
            return true;
        }
        return false;
    }

    boolean tryHandleQuickKeybinds(int key, int scan, boolean ctrl, int mods) {
        if (ChronicleKeyBindings.SEARCH.matches(key, scan)) {
            screen.openSearchOverlay();
            return true;
        }

        if (key == 80 && ctrl && (mods & 1) == 0) {
            FrameProfiler.setEnabled(!FrameProfiler.isEnabled());
            screen.setFeedback(FrameProfiler.isEnabled() ? "§aProfiler ON" : "§7Profiler OFF");
            return true;
        }

        if (ChronicleKeyBindings.PIN_QUEST.matches(key, scan)) {
            if (screen.lastHoveredNodeId != null && screen.playerData != null) {
                screen.playerData.togglePin(screen.lastHoveredNodeId);
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new C2STogglePinPacket(screen.lastHoveredNodeId));
                QuestNode hovered = QuestTreeRegistry.getQuest(screen.lastHoveredNodeId);
                boolean nowPinned = screen.playerData.isPinned(screen.lastHoveredNodeId);
                String pinPrefix = nowPinned ? "§dPinned" : "§7Unpinned";
                if (hovered != null) {
                    screen.setFeedback("%s: %s", pinPrefix,
                            hovered.getEffectiveTitleRaw(Minecraft.getInstance().getSingleplayerServer(),
                                    Minecraft.getInstance().player)
                                    .getString());
                } else {
                    screen.setFeedback(pinPrefix);
                }
            }
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_LINE_STYLE.matches(key, scan)) {
            QuestChroniclesSettings s = QuestChroniclesSettings.get();
            boolean nowSpline = s.isSplineLines();
            s.setLineStyle(
                    nowSpline ? QuestChroniclesSettings.LineStyle.STRAIGHT : QuestChroniclesSettings.LineStyle.SPLINE);
            s.save();
            screen.setFeedback("Line style: %s", nowSpline ? "Straight" : "Spline");
            return true;
        }
        return false;
    }

    boolean tryHandleEscapeDispatch() {
        if (screen.sidebarPanel.contextMenuOpen()) {
            screen.sidebarPanel.closeContextMenu();
            return true;
        }
        if (screen.depLineRenderer.isContextMenuOpen()) {
            screen.depLineRenderer.closeContextMenu();
            return true;
        }
        if (!screen.unlockPathHighlight.isEmpty()) {
            screen.unlockPathHighlight.clear();
            return true;
        }
        for (TogglePanel p : List.of(screen.validationPanel, screen.statsPanel)) {
            if (p.isOpen()) {
                p.close();
                return true;
            }
        }
        if (screen.ctxOpen) {
            screen.ctxOpen = false;
            screen.ctxMoveCatOpen = false;
            return true;
        }
        if (screen.pictureCtxMenu.isOpen()) {
            screen.pictureCtxMenu.close();
            return true;
        }
        if (screen.pictureEditMode != null) {
            screen.editModes.finalizePictureEdit();
            screen.setFeedback("Picture edit finished  (Ctrl+Z to undo the whole edit)");
            return true;
        }
        if (screen.nodeSizeEditMode != null) {
            screen.editModes.finalizeNodeSizeEdit();
            screen.setFeedback("Node resize finished  (Ctrl+Z to undo the whole edit)");
            screen.softRebuild();
            return true;
        }
        if (screen.editorState.activeTool != GraphEditorState.EditorTool.SELECT) {
            screen.editorState.activeTool = GraphEditorState.EditorTool.SELECT;
            screen.setFeedback("Select mode");
            return true;
        }
        return false;
    }

    boolean tryHandleUndoRedoKeybind(int key, boolean ctrl, boolean shift) {
        if (ctrl && screen.isDevMode) {
            if (key == 90 && !shift) {
                screen.undoRedo.undo();
                return true;
            }
            if (key == 89 || (key == 90 && shift)) {
                screen.undoRedo.redo();
                return true;
            }
        }
        return false;
    }

    boolean tryHandleActionKeybinds(int key, int scan, boolean ctrl, boolean shift) {
        if (ChronicleKeyBindings.FIT_TO_CANVAS.matches(key, scan)) {
            screen.fitToCanvas();
            return true;
        }

        if (ChronicleKeyBindings.OPEN_DEV_WIKI.matches(key, scan) && screen.isDevMode) {
            screen.openWiki();
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_VALIDATION.matches(key, scan) && !ctrl && screen.isDevMode) {
            screen.validationPanel.toggle();
            return true;
        }

        if (ChronicleKeyBindings.IMPORT_FTB.matches(key, scan) && screen.isDevMode) {
            screen.runFtbImport();
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_SUBGRAPH.matches(key, scan) && screen.isDevMode) {
            screen.editorState.subgraphMode = !screen.editorState.subgraphMode;
            if (screen.editorState.subgraphMode) screen.rebuildSubgraph();
            return true;
        }

        if (key == 67 && ctrl && !shift && screen.isDevMode && screen.editorState.selectedNode != null) {
            screen.questCopy(screen.editorState.selectedNode);
            return true;
        }

        if (key == 86 && ctrl && !shift && screen.isDevMode) {
            screen.questPaste();
            return true;
        }

        if (key == 68 && ctrl && !shift && screen.isDevMode && screen.editorState.selectedNode != null) {
            screen.duplicateQuest(screen.editorState.selectedNode);
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_MINIMAP.matches(key, scan)) {
            screen.minimapOpen = !screen.minimapOpen;
            return true;
        }

        if (ChronicleKeyBindings.TOGGLE_STATS.matches(key, scan)) {
            screen.toggleStatsPanel();
            return true;
        }
        return false;
    }
}

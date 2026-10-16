package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.client.screen.*;
import net.phoenixvine.chronicles.client.util.BackgroundPictureConfig;
import net.phoenixvine.chronicles.client.util.CustomTextureCache;
import net.phoenixvine.chronicles.common.codec.QuestFileLoader;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.QuestGroup;
import net.phoenixvine.chronicles.common.model.QuestGroupManager;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.integration.archive.ArchiveLoreCompat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NodeContextMenuBuilder {

    private final ScreenContext ctx;
    private final NodeCtxMenuState ctxState;
    private final NodeContextMenuBuilderState state;
    private final GraphEditorState editorState;

    public NodeContextMenuBuilder(ScreenContext ctx, NodeCtxMenuState ctxState, NodeContextMenuBuilderState state,
                                  GraphEditorState editorState) {
        this.ctx = ctx;
        this.ctxState = ctxState;
        this.state = state;
        this.editorState = editorState;
    }

    public @NotNull List<ChronicleOverviewScreen.CtxItem> buildCtxItems() {
        List<ChronicleOverviewScreen.CtxItem> items = new ArrayList<>();
        QuestNode node = ctxState.ctxNode();
        QuestGroup group = ctxState.ctxGroup();
        boolean canEdit = ctx.isDevMode() && !state.testMode();

        if (node == null && group == null) {
            addEmptySpaceItems(items, canEdit);
        }
        if (group != null && canEdit) {
            addGroupItems(items, group);
        }
        if (node != null) {
            addNodeItems(items, node, canEdit);
        }
        return items;
    }

    private void addEmptySpaceItems(List<ChronicleOverviewScreen.CtxItem> items, boolean canEdit) {
        if (canEdit) {
            addNewQuestItems(items);
        }
        addDependencyLinesItem(items);
        if (canEdit && editorState.multiSelection.size() >= 2) {
            addMultiSelectItems(items);
        }
        if (canEdit) {
            addPasteItem(items);
        }
        if (ctx.isDevMode()) {
            addPlayerModeAndGridItems(items);
        }
        if (canEdit) {
            addChapterToolItems(items);
        }
    }

    private void addNewQuestItems(List<ChronicleOverviewScreen.CtxItem> items) {
        items.add(new ChronicleOverviewScreen.CtxItem("+ New quest", "§a", false, false,
                () -> {
                    state.setCtxOpen(false);
                    int cl = ctx.sidebarW();
                    int canvasX = (int) ((state.ctxRawX() - cl - state.viewOffX()) / ctx.posZoom());
                    int canvasY = (int) ((state.ctxRawY() - ChronicleOverviewScreen.HEADER_H - state.viewOffY()) /
                            ctx.posZoom());
                    Minecraft.getInstance().setScreen(
                            new QuestCreatorScreen(state.thisScreen(), canvasX, canvasY, ctx.selectedChapter()));
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("🔗 Link quest here…", "§b", false, false,
                () -> {
                    state.setCtxOpen(false);
                    int cl = ctx.sidebarW();
                    int canvasX = (int) ((state.ctxRawX() - cl - state.viewOffX()) / ctx.posZoom());
                    int canvasY = (int) ((state.ctxRawY() - ChronicleOverviewScreen.HEADER_H - state.viewOffY()) /
                            ctx.posZoom());
                    Minecraft.getInstance().setScreen(ParentSelectorScreen.singleSelect(state.thisScreen(), null,
                            target -> state.createLinkStubAt(canvasX, canvasY, target)));
                }));
    }

    private void addDependencyLinesItem(List<ChronicleOverviewScreen.CtxItem> items) {
        final String cat = ctx.selectedChapter();
        items.add(new ChronicleOverviewScreen.CtxItem("Dependency Lines", "§b", false, false,
                () -> {
                    state.setCtxOpen(false);
                    Minecraft.getInstance().setScreen(new DepLineSettingsScreen(state.thisScreen(), cat));
                }));
    }

    private void addMultiSelectItems(List<ChronicleOverviewScreen.CtxItem> items) {
        items.add(ChronicleOverviewScreen.CtxItem.sep());
        items.add(new ChronicleOverviewScreen.CtxItem("→ Chain selected (left→right)", "§b", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.chainMultiSelection();
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("★ Fan from leftmost", "§b", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.fanFromLeftmost();
                }));
    }

    private void addPasteItem(List<ChronicleOverviewScreen.CtxItem> items) {
        String label = editorState.questClipboard != null ? "⎘ Paste quest" : "⎘ Paste quest §8(clipboard)";
        items.add(new ChronicleOverviewScreen.CtxItem(label, "§7", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.questPaste();
                }));
    }

    private void addPlayerModeAndGridItems(List<ChronicleOverviewScreen.CtxItem> items) {
        items.add(ChronicleOverviewScreen.CtxItem.sep());
        items.add(new ChronicleOverviewScreen.CtxItem(
                (state.testMode() ? "§c⏵ Exit Player Mode" : "⏵ Enter Player Mode"), "§7", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.setTestMode(!state.testMode());
                    if (!state.testMode()) state.setTestModeData(new PlayerQuestData());
                    rebuildScreen();
                }));
        if (state.testMode()) {
            items.add(new ChronicleOverviewScreen.CtxItem("↺ Reset Player Mode Data", "§7", false, false,
                    () -> {
                        state.setCtxOpen(false);
                        state.setTestModeData(new PlayerQuestData());
                        rebuildScreen();
                    }));
        }
        items.add(new ChronicleOverviewScreen.CtxItem(
                (editorState.subgraphMode ? "§b⊛ Exit subgraph" : "⊛ Subgraph mode"), "§7", false, false,
                () -> {
                    state.setCtxOpen(false);
                    editorState.subgraphMode = !editorState.subgraphMode;

                    if (editorState.subgraphMode && editorState.selectedNode != null) {
                        state.rebuildSubgraph();
                    }
                }));
        items.add(new ChronicleOverviewScreen.CtxItem(
                (state.statsPanelOpen() ? "§b∑ Hide stats" : "∑ Show stats"), "§7", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.toggleStatsPanel();
                }));

        items.add(new ChronicleOverviewScreen.CtxItem(
                (state.gridSnapEnabled() ? "⊞ Grid Snap: ON" : "§7⊞ Grid Snap: OFF"), "§7", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.setGridSnapEnabled(!state.gridSnapEnabled());
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("⊞ Show Grid: " + state.gridDisplayMode().label, "§7",
                false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.setGridDisplayMode(state.gridDisplayMode().next());
                }));
    }

    private void addChapterToolItems(List<ChronicleOverviewScreen.CtxItem> items) {
        int cl = ctx.sidebarW();
        items.add(new ChronicleOverviewScreen.CtxItem("+ New group here", "§b", false, false,
                () -> {
                    state.setCtxOpen(false);
                    int lx = (int) ((ctxState.ctxX() - cl - state.viewOffX()) / ctx.posZoom());
                    int ly = (int) ((ctxState.ctxY() - ChronicleOverviewScreen.HEADER_H - state.viewOffY()) /
                            ctx.posZoom());
                    Minecraft.getInstance().setScreen(
                            new QuestGroupEditorScreen(state.thisScreen(), ctx.selectedChapter(), null, lx, ly));
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("Edit chapter theme…", "§d", false, false,
                () -> {
                    state.setCtxOpen(false);
                    Minecraft.getInstance()
                            .setScreen(new CanvasThemeScreen(state.thisScreen(), ctx.selectedChapter()));
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("🖼 Add picture…", "§d", false, false,
                () -> {
                    state.setCtxOpen(false);
                    final float px = (ctxState.ctxX() - cl - state.viewOffX()) / ctx.posZoom();
                    final float py = (ctxState.ctxY() - ChronicleOverviewScreen.HEADER_H - state.viewOffY()) /
                            ctx.posZoom();
                    final String cat = ctx.selectedChapter();
                    Minecraft.getInstance().setScreen(new TextureBrowserScreen(state.thisScreen(), rl -> {
                        BackgroundPictureConfig.Picture pic = new BackgroundPictureConfig.Picture();
                        pic.texture = rl;
                        pic.x = px;
                        pic.y = py;
                        ResourceLocation loc;
                        try {
                            loc = CustomTextureCache.resolve(ResourceLocation.parse(rl));
                        } catch (Exception e) {
                            loc = null;
                        }

                        int[] nativeSz = loc != null ? CustomTextureCache.nativeSize(loc) : null;
                        if (nativeSz != null && nativeSz[0] > 0 && nativeSz[1] > 0) {
                            float maxDim = 128f;
                            float scale = maxDim / Math.max(nativeSz[0], nativeSz[1]);
                            pic.w = nativeSz[0] * scale;
                            pic.h = nativeSz[1] * scale;
                        }
                        BackgroundPictureConfig.add(cat, pic);
                        ctx.setFeedback("Picture placed: shift+drag to move, right-click to remove");
                    }));
                }));
        items.add(ChronicleOverviewScreen.CtxItem.sep());
        items.add(new ChronicleOverviewScreen.CtxItem("⊞ Auto-arrange chapter", "§e", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.autoArrangeChapter();
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("↻ Rotate chapter 90°", "§e", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.rotateChapter90();
                }));
    }

    private void addGroupItems(List<ChronicleOverviewScreen.CtxItem> items, QuestGroup group) {
        items.add(ChronicleOverviewScreen.CtxItem.sep());
        QuestGroup grp = group;
        items.add(new ChronicleOverviewScreen.CtxItem("Edit group…", "§b", false, false,
                () -> {
                    state.setCtxOpen(false);
                    Minecraft.getInstance().setScreen(new QuestGroupEditorScreen(state.thisScreen(),
                            ctx.selectedChapter(), grp, grp.getX(), grp.getY()));
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("Delete group", "§c", false, true,
                () -> {
                    QuestGroupManager.remove(grp.getId());
                    QuestGroupManager.save(state.groupsConfigPath());
                    state.setCtxOpen(false);
                    state.setCtxGroup(null);
                    ctx.setFeedback("Group deleted");
                }));
    }

    private void addNodeItems(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node, boolean canEdit) {
        items.add(ChronicleOverviewScreen.CtxItem.sep());
        addLoreItems(items, node);
        addLinkJumpItems(items, node);
        addCollapseItem(items, node);
        if (canEdit) {
            addNodeEditItems(items, node);
        }
        items.add(ChronicleOverviewScreen.CtxItem.sep());
        addNodeDependencyLinesItem(items, node);
        if (canEdit) {
            addNodeActionItems(items, node);
        }
    }

    private void addLoreItems(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        String archiveQuestId = node.getId().toString();
        if (ArchiveLoreCompat.hasLoreFor(archiveQuestId)) {
            items.add(new ChronicleOverviewScreen.CtxItem("📖 View Lore", "§d", false, false,
                    () -> {
                        state.setCtxOpen(false);
                        ArchiveLoreCompat.openLoreFor(state.thisScreen(), archiveQuestId);
                    }));
            items.add(ChronicleOverviewScreen.CtxItem.sep());
        }
    }

    private void addLinkJumpItems(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        QuestNode ctxLinkTarget = state.resolveLinkTarget(node);
        if (node.isLinkStub() && ctxLinkTarget != null) {
            final QuestNode jumpTarget = ctxLinkTarget;
            items.add(new ChronicleOverviewScreen.CtxItem("🔗 Jump to linked quest", "§b", false, false,
                    () -> {
                        state.setCtxOpen(false);
                        state.navigateToNode(jumpTarget);
                    }));
            items.add(ChronicleOverviewScreen.CtxItem.sep());
        }
    }

    private void addCollapseItem(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        if (!node.getChildren().isEmpty()) {
            boolean nowCollapsed = ChronicleOverviewScreen.collapsedSubtreeRoots.contains(node.getId());
            items.add(new ChronicleOverviewScreen.CtxItem(
                    nowCollapsed ? "▶ Expand Subtree" : "▼ Collapse Subtree", "§7", false, false,
                    () -> {
                        final QuestNode target = node;
                        state.setCtxOpen(false);
                        state.toggleSubtreeCollapse(target);
                    }));
        }
    }

    private void addNodeEditItems(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        items.add(new ChronicleOverviewScreen.CtxItem("Edit Quest", "§7", false, false,
                () -> {
                    state.setCtxOpen(false);
                    Minecraft.getInstance().setScreen(new QuestCreatorScreen(state.thisScreen(), node));
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("Edit Texts...", "§d", false, false,
                () -> {
                    final QuestNode target = node;
                    state.setCtxOpen(false);
                    Minecraft.getInstance().setScreen(new LangEditorScreen(state.thisScreen(), target));
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("Design Pop-Up", "§6", false, false,
                () -> {
                    final QuestNode target = node;
                    state.setCtxOpen(false);
                    Minecraft.getInstance().setScreen(new ToastDesignerScreen(state.thisScreen(), target));
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("Set Icon…", "§7", false, false,
                () -> {
                    final QuestNode target = node;
                    state.setCtxOpen(false);
                    Minecraft.getInstance().setScreen(new SetIconScreen(state.thisScreen(), target));
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("Resize (scroll + drag)…", "§7", false, false,
                () -> {

                    node.setSizeOverridePx(node.getNodePixelSize());
                    state.setNodeSizeEditMode(node);
                    state.setNodeSizeDragAccX(0);
                    state.setNodeSizeDragAccY(0);
                    ctx.setFeedback("§eScroll to resize, drag to move - right-click or Esc to finish");
                    state.setCtxOpen(false);
                }));
        items.add(ChronicleOverviewScreen.CtxItem.sep());

        items.add(new ChronicleOverviewScreen.CtxItem("Move to Chapter  ▸", "§7", false, false, () -> {}));
        items.add(ChronicleOverviewScreen.CtxItem.sep());
        items.add(new ChronicleOverviewScreen.CtxItem("Shift + drag to move", "§8", false, false,
                () -> {
                    state.setCtxOpen(false);
                    ctx.setFeedback("Shift-click and drag the node");
                }));
    }

    private void addNodeDependencyLinesItem(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        items.add(new ChronicleOverviewScreen.CtxItem("Dependency Lines", "§b", false, false,
                () -> {
                    state.setCtxOpen(false);
                    final String cat = ctx.selectedChapter();
                    Minecraft.getInstance().setScreen(new DepLineSettingsScreen(state.thisScreen(), cat, node));
                }));
    }

    private void addNodeActionItems(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        items.add(ChronicleOverviewScreen.CtxItem.sep());
        addCopyAndDuplicateItems(items, node);
        addForceCompleteItem(items, node);
        addResetProgressItem(items, node);
        addDeleteQuestItem(items, node);
    }

    private void addCopyAndDuplicateItems(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        items.add(new ChronicleOverviewScreen.CtxItem("Copy Quest §8(Ctrl+C)", "§7", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.questCopy(node);
                }));
        items.add(new ChronicleOverviewScreen.CtxItem("Duplicate Quest §8(Ctrl+D)", "§b", false, false,
                () -> {
                    state.setCtxOpen(false);
                    state.duplicateQuest(node);
                }));
    }

    private void addForceCompleteItem(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        items.add(new ChronicleOverviewScreen.CtxItem("Force Complete Quest", "§e", false, false,
                () -> {
                    final QuestNode target = node;
                    state.setCtxOpen(false);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {

                        QuestState preState = state.playerData() != null ?
                                state.playerData().getQuestState(target.getId(), QuestState.LOCKED) :
                                QuestState.LOCKED;
                        ctx.pushUndo("Undo: force-complete reverted", () -> {
                            Minecraft mc2 = Minecraft.getInstance();
                            if (mc2.player == null) return;
                            String cmd = switch (preState) {
                                case UNLOCKED -> "chronicles unlock " + target.getId().getPath();
                                case ACTIVE -> "chronicles active " + target.getId().getPath();
                                default -> "chronicles reset " + target.getId().getPath();
                            };
                            mc2.player.connection.sendCommand(cmd);
                        }, () -> {
                            Minecraft mc2 = Minecraft.getInstance();
                            if (mc2.player == null) return;
                            mc2.player.connection
                                    .sendCommand("chronicles complete " + target.getId().getPath());
                        });

                        mc.player.connection.sendCommand("chronicles complete " + target.getId().getPath());
                        ctx.setFeedbackDone("Force-completed: %s", target.getTitle().getString());
                    }
                }));
    }

    private void addResetProgressItem(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        items.add(new ChronicleOverviewScreen.CtxItem("Reset Progress", "§7", false, false,
                () -> {
                    final QuestNode target = node;
                    state.setCtxOpen(false);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {

                        mc.player.connection.sendCommand("chronicles reset " + target.getId().getPath());
                        ctx.setFeedback("Progress reset: %s", target.getTitle().getString());
                    }
                }));
    }

    private void addDeleteQuestItem(List<ChronicleOverviewScreen.CtxItem> items, QuestNode node) {
        items.add(new ChronicleOverviewScreen.CtxItem("Delete Quest", "§c", false, true,
                () -> {
                    final QuestNode deleted = node;

                    final String savedContent = QuestFileSaver.readRawSnbt(deleted);
                    final Path savedSnbtPath = QuestFileSaver.getQuestSnbtPath(deleted);
                    ctx.pushUndo("Undo: quest restored", () -> {

                        QuestFileSaver.restoreRawSnbt(deleted, savedContent);
                        QuestFileLoader.loadOneFromDisk(savedSnbtPath);
                        rebuildScreen();
                    }, () -> {
                        QuestTreeRegistry.removeQuest(deleted.getId());
                        state.deleteQuestFiles(deleted);
                        rebuildScreen();
                    });
                    QuestTreeRegistry.removeQuest(deleted.getId());
                    state.deleteQuestFiles(deleted);
                    if (editorState.selectedNode == deleted) editorState.selectedNode = null;
                    state.setCtxOpen(false);
                    rebuildScreen();
                    ctx.setFeedbackDone("Quest deleted");
                }));
    }

    private void rebuildScreen() {
        state.rebuild();
    }

    public void openCtx(int x, int y, QuestNode node) {
        openCtx(x, y, node, null);
    }

    public void openCtx(int x, int y, QuestNode node, @Nullable QuestGroup group) {
        state.setCtxOpen(true);
        state.setCtxOpenTimeMs(System.currentTimeMillis());
        ctxState.setCtxMoveCatOpen(false);
        int cx = x, cy = y;
        state.setCtxX(cx);
        state.setCtxY(cy);
        state.setCtxRawX(x);
        state.setCtxRawY(y);
        state.setCtxNode(node);
        state.setCtxGroup(group);
        List<ChronicleOverviewScreen.CtxItem> items = buildCtxItems();
        int menuH = menuHeight(items);

        int availH = ctx.height() - 8;
        float scale = menuH > availH && availH > 0 ? Math.max(0.55f, availH / (float) menuH) : 1f;
        state.setCtxScale(scale);
        int scaledW = Math.round(ChronicleOverviewScreen.CTX_W * scale);
        int scaledH = Math.round(menuH * scale);

        if (cy + scaledH > ctx.height() - 4) cy = ctx.height() - scaledH - 4;
        if (cx + scaledW > ctx.width() - 4) cx = ctx.width() - scaledW - 4;

        cx = Math.max(4, cx);
        cy = Math.max(4, cy);
        state.setCtxX(cx);
        state.setCtxY(cy);
    }

    public int menuHeight(@NotNull List<ChronicleOverviewScreen.CtxItem> items) {
        int h = 4;
        if (ctxState.ctxNode() != null) h += ChronicleOverviewScreen.CTX_ROW;
        for (ChronicleOverviewScreen.CtxItem i : items) h += i.isSep() ? ChronicleOverviewScreen.CTX_SEP :
                ChronicleOverviewScreen.CTX_ROW;
        return h;
    }

    private int ctxMoveCatY(@NotNull List<ChronicleOverviewScreen.CtxItem> items) {
        int y = ctxState.ctxY() + 2;
        if (ctxState.ctxNode() != null) y += ChronicleOverviewScreen.CTX_ROW;
        for (ChronicleOverviewScreen.CtxItem item : items) {
            if (!item.isSep() && item.label().contains("Move to Chapter")) return y;
            y += item.isSep() ? ChronicleOverviewScreen.CTX_SEP : ChronicleOverviewScreen.CTX_ROW;
        }
        return y;
    }

    public int ctxMoveCatX(int catCount) {
        int subW = ChronicleOverviewScreen.CTX_W +
                (catCount > ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS ? 6 : 0);
        int x = ctxState.ctxX() + ChronicleOverviewScreen.CTX_W + 2;
        if (x + subW > ctx.width() - 4) x = ctxState.ctxX() - subW - 2;
        return Math.max(4, x);
    }

    public int ctxMoveCatYClamped(@NotNull List<ChronicleOverviewScreen.CtxItem> items, int catCount) {
        int y = ctxMoveCatY(items);
        int visibleRows = Math.min(catCount, ChronicleOverviewScreen.CTX_MOVE_CAT_MAX_ROWS);
        int subH = visibleRows * ChronicleOverviewScreen.CTX_ROW + 4;
        if (y + subH > ctx.height() - 4) y = ctx.height() - subH - 4;
        return Math.max(4, y);
    }
}

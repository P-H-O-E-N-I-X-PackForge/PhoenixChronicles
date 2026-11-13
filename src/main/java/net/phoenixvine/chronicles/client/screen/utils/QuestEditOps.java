package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter;
import net.phoenixvine.chronicles.client.registry.ChroniclesLangPack;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.codec.QuestFileLoader;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class QuestEditOps {

    private final ScreenContext ctx;
    private final QuestEditOpsState state;
    private final GraphEditorState editorState;

    private volatile boolean ftbImportInProgress = false;

    public QuestEditOps(ScreenContext ctx, QuestEditOpsState state, GraphEditorState editorState) {
        this.ctx = ctx;
        this.state = state;
        this.editorState = editorState;
    }

    public void questCopy(@NotNull QuestNode node) {
        String content = QuestFileSaver.readRawSnbt(node);
        if (content == null || content.isBlank()) {
            ctx.setFeedback(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.copy_failed_quest_file_not"));
            return;
        }
        editorState.questClipboard = content;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.keyboardHandler.setClipboard(content);
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.copied_snbt_for_ctrl_v"),
                node.getId().getPath());
    }

    public void questPaste() {
        String src = editorState.questClipboard;
        Minecraft mc = Minecraft.getInstance();
        if (src == null || src.isBlank()) {
            src = mc != null ? mc.keyboardHandler.getClipboard() : null;
        }
        if (src == null || src.isBlank()) {
            ctx.setFeedback(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.nothing_to_paste_ctrl_c"));
            return;
        }

        if (!src.contains("id:")) {
            ctx.setFeedback(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.clipboard_doesn_t_look_like"));
            return;
        }
        try {
            String newPath = QuestFileSaver.pasteQuestFromSnbt(src, ctx.selectedChapter());
            state.rebuild();
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.pasted"), newPath);

            ResourceLocation newId = new ResourceLocation("phoenix_chronicles", newPath);
            QuestNode pasted = QuestTreeRegistry.getQuest(newId);
            if (pasted != null) {
                String pastedSnbt = QuestFileSaver.readRawSnbt(pasted);
                Path pastedPath = QuestFileSaver.getQuestSnbtPath(pasted);
                ctx.pushUndo(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.undo_pasted_quest_removed"),
                        () -> {
                            QuestTreeRegistry.removeQuest(newId);
                            state.deleteQuestFiles(pasted);
                            if (editorState.selectedNode == pasted) editorState.selectedNode = null;
                            state.rebuild();
                        }, () -> {
                            QuestFileSaver.restoreRawSnbt(pasted, pastedSnbt);
                            QuestFileLoader.loadOneFromDisk(pastedPath);
                            state.rebuild();
                        });
            }
        } catch (IOException e) {
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.paste_error"),
                    e.getMessage());
        }
    }

    public void chainMultiSelection() {
        List<QuestNode> ordered = editorState.multiSelection.stream()
                .map(QuestTreeRegistry::getQuest)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(QuestNode::getCustomX))
                .collect(java.util.stream.Collectors.toList());
        if (ordered.size() < 2) {
            ctx.setFeedback(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.select_2_quests_to_chain"));
            return;
        }
        int wired = 0;
        List<QuestNode[]> newLinks = new java.util.ArrayList<>();
        for (int i = 1; i < ordered.size(); i++) {
            QuestNode child = ordered.get(i);
            QuestNode parent = ordered.get(i - 1);
            if (!child.getPrerequisites().contains(parent)) {
                child.addPrerequisite(parent);
                state.saveNodePrereqsToDisk(child);
                newLinks.add(new QuestNode[] { child, parent });
                wired++;
            }
        }
        state.buildLineCache();
        state.rebuild();
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.chained_d_quests_d_new"),
                ordered.size(), wired, wired == 1 ? "" : "s");
        if (!newLinks.isEmpty()) {
            ctx.pushUndo(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.undo_chain_links_removed"),
                    () -> {
                        for (QuestNode[] link : newLinks) {
                            link[0].removePrerequisite(link[1]);
                            state.saveNodePrereqsToDisk(link[0]);
                        }
                        state.buildLineCache();
                        state.rebuild();
                    }, () -> {
                        for (QuestNode[] link : newLinks) {
                            link[0].addPrerequisite(link[1]);
                            state.saveNodePrereqsToDisk(link[0]);
                        }
                        state.buildLineCache();
                        state.rebuild();
                    });
        }
    }

    public void fanFromLeftmost() {
        List<QuestNode> nodes = editorState.multiSelection.stream()
                .map(QuestTreeRegistry::getQuest)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(QuestNode::getCustomX))
                .collect(java.util.stream.Collectors.toList());
        if (nodes.size() < 2) {
            ctx.setFeedback(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.select_2_quests_to_fan"));
            return;
        }
        QuestNode root = nodes.get(0);
        int wired = 0;
        List<QuestNode> newlyLinked = new java.util.ArrayList<>();
        for (int i = 1; i < nodes.size(); i++) {
            QuestNode child = nodes.get(i);
            if (!child.getPrerequisites().contains(root)) {
                child.addPrerequisite(root);
                state.saveNodePrereqsToDisk(child);
                newlyLinked.add(child);
                wired++;
            }
        }
        state.buildLineCache();
        state.rebuild();
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.fanned_from_to_d_quest"),
                root.getId().getPath(), wired, wired == 1 ? "" : "s");
        if (!newlyLinked.isEmpty()) {
            ctx.pushUndo(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.undo_fan_links_removed"), () -> {
                for (QuestNode child : newlyLinked) {
                    child.removePrerequisite(root);
                    state.saveNodePrereqsToDisk(child);
                }
                state.buildLineCache();
                state.rebuild();
            }, () -> {
                for (QuestNode child : newlyLinked) {
                    child.addPrerequisite(root);
                    state.saveNodePrereqsToDisk(child);
                }
                state.buildLineCache();
                state.rebuild();
            });
        }
    }

    public void runFtbImport() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (ftbImportInProgress) {
            ctx.setFeedback(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.ftb_import_already_in_progress"));
            return;
        }
        Path base = mc.gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        Path importDir = resolveFtbImportDir(mc, base);

        ftbImportInProgress = true;
        ctx.setFeedback(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.importing_ftb_quests_this_may"));

        Thread worker = new Thread(() -> {
            FtbQuestsImporter.ImportResult result = null;
            Exception error = null;
            try {
                java.nio.file.Files.createDirectories(importDir);
                result = FtbQuestsImporter.importDirectory(importDir, base);
            } catch (Exception e) {
                error = e;
            }
            FtbQuestsImporter.ImportResult finalResult = result;
            Exception finalError = error;
            mc.execute(() -> finishFtbImport(finalResult, finalError, importDir));
        }, "phoenix-chronicles-ftb-import");
        worker.setDaemon(true);
        worker.start();
    }

    private static Path resolveFtbImportDir(Minecraft mc, Path phoenixConfigBase) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            try {
                Path worldSpecific = mc.getSingleplayerServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("ftbquests").resolve("quests").resolve("chapters");
                if (hasSnbtFiles(worldSpecific)) return worldSpecific;
            } catch (Exception ignored) {}
        }
        Path global = mc.gameDirectory.toPath().resolve("config").resolve("ftbquests").resolve("quests")
                .resolve("chapters");
        if (hasSnbtFiles(global)) return global;

        return phoenixConfigBase.resolve("ftb_import");
    }

    private static boolean hasSnbtFiles(Path dir) {
        if (!java.nio.file.Files.isDirectory(dir)) return false;
        try (var stream = java.nio.file.Files.list(dir)) {
            return stream.anyMatch(p -> !java.nio.file.Files.isDirectory(p) &&
                    p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".snbt"));
        } catch (IOException e) {
            return false;
        }
    }

    private void finishFtbImport(FtbQuestsImporter.@NotNull ImportResult r, @Nullable Exception error,
                                 Path importDir) {
        ftbImportInProgress = false;
        if (error != null) {
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.ftb_import_error"),
                    error.getMessage());
            return;
        }
        if (r.imported() == 0 && r.skipped() == 0) {
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.no_snbt_files_found_in"),
                    importDir);
        } else {
            String skippedPart = r.skipped() > 0 ? " §c(%d skipped)".formatted(r.skipped()) : "";
            String warningsPart = r.warnings().isEmpty() ? "" :
                    " §8%d warnings".formatted(r.warnings().size());
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.imported_d_quests"),
                    r.imported(), skippedPart, warningsPart);
            if (r.imported() > 0) {
                QuestFileLoader.reloadAllQuestsFromDisk();

                ChroniclesLangPack.reload();
                state.rebuild();
            }
        }
    }

    public void duplicateQuest(@NotNull QuestNode source) {
        if (!QuestFileSaver.doesQuestFileExist(source)) {
            ctx.setFeedback(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.cannot_duplicate_source_file_not"));
            return;
        }
        try {
            String newPath = QuestFileSaver.duplicateQuestOnDisk(source);
            ResourceLocation newId = new ResourceLocation(source.getId().getNamespace(), newPath);
            QuestNode duplicated = QuestTreeRegistry.getQuest(newId);
            if (duplicated != null) {
                String duplicatedSnbt = QuestFileSaver.readRawSnbt(duplicated);
                Path duplicatedPath = QuestFileSaver.getQuestSnbtPath(duplicated);
                ctx.pushUndo(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.undo_duplicate_removed"),
                        () -> {
                            QuestTreeRegistry.removeQuest(newId);
                            state.deleteQuestFiles(duplicated);
                            if (editorState.selectedNode == duplicated) editorState.selectedNode = null;
                            state.rebuild();
                        }, () -> {
                            QuestFileSaver.restoreRawSnbt(duplicated, duplicatedSnbt);
                            QuestFileLoader.loadOneFromDisk(duplicatedPath);
                            state.rebuild();
                        });
            }
            state.rebuild();
            ctx.setFeedbackDone(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.duplicated"), newPath);
        } catch (IOException e) {
            e.printStackTrace();
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.duplicate_failed"),
                    e.getMessage());
        }
    }

    public void createLinkStubAt(int canvasX, int canvasY, @Nullable QuestNode target) {
        if (target == null) return;

        String base = ("link_" + target.getId().getPath())
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9/._-]", "");
        if (base.isBlank()) base = "link_quest";

        String path = base;
        int suffix = 2;
        while (QuestTreeRegistry.getQuest(new ResourceLocation("phoenix_chronicles", path)) != null) {
            path = base + "_" + suffix;
            suffix++;
        }

        ResourceLocation newId = new ResourceLocation("phoenix_chronicles", path);
        QuestNode node = new QuestNode(newId, Component.literal(""), Component.literal(""));
        node.setChapter(ctx.selectedChapter());
        node.setCustomPosition(canvasX, canvasY);
        node.setLinkTarget(target.getId());

        QuestTreeRegistry.injectDynamicQuestNode(node, null);
        QuestFileSaver.saveOneQuestToDisk(node);
        state.rebuild();
        ctx.setFeedbackDone(ChroniclesUIKit.label("phoenix_chronicles.screen.quest_edit_ops.linked"),
                target.getId().getPath());

        ctx.pushUndo(ChroniclesUIKit.str("phoenix_chronicles.screen.quest_edit_ops.undo_link_quest_removed"), () -> {
            QuestTreeRegistry.removeQuest(newId);
            state.deleteQuestFiles(node);
            if (editorState.selectedNode == node) editorState.selectedNode = null;
            state.rebuild();
        }, () -> {
            QuestTreeRegistry.injectDynamicQuestNode(node, null);
            QuestFileSaver.saveOneQuestToDisk(node);
            state.rebuild();
        });
    }
}

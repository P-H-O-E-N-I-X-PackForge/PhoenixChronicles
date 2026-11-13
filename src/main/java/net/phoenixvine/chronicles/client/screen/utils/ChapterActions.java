package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.screen.*;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.screen.widgets.SidebarPanel;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.common.codec.QuestFileLoader;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.CategoryDefinition;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.CategoryRegistry;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.chronicles.network.packet.C2SBulkQuestActionPacket;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChapterActions {

    private final ScreenContext ctx;
    private final ChronicleOverviewScreen state;
    private final GraphEditorState editorState;

    private @Nullable String armedDeleteCategoryId = null;
    private @Nullable String armedDeleteChapterId = null;

    public ChapterActions(ScreenContext ctx, ChronicleOverviewScreen state, GraphEditorState editorState) {
        this.ctx = ctx;
        this.state = state;
        this.editorState = editorState;
    }

    public void forceCompleteChapterOnRightClick(String chapter) {
        List<ResourceLocation> ids = state.questIdsInChapter(chapter);
        if (ids.isEmpty()) {
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.no_quests_in"),
                    ctx.friendly(chapter));
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(
                new C2SBulkQuestActionPacket(ids, C2SBulkQuestActionPacket.Action.FORCE_COMPLETE));
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.force_completed_d_quest_s"),
                ids.size(), ctx.friendly(chapter));
    }

    public void resetChapterOnRightClick(String chapter) {
        List<ResourceLocation> ids = state.questIdsInChapter(chapter);
        if (ids.isEmpty()) {
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.no_quests_in"),
                    ctx.friendly(chapter));
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(
                new C2SBulkQuestActionPacket(ids, C2SBulkQuestActionPacket.Action.RESET));
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.reset_d_quest_s_in"),
                ids.size(), ctx.friendly(chapter));
    }

    public void forceCompleteCategoryOnRightClick(@NotNull String categoryId) {
        List<ResourceLocation> ids = state.questIdsInCategory(categoryId);
        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        String label = cat != null ? cat.displayName() : categoryId;
        if (ids.isEmpty()) {
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.no_quests_in"), label);
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(
                new C2SBulkQuestActionPacket(ids, C2SBulkQuestActionPacket.Action.FORCE_COMPLETE));
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.force_completed_d_quest_s"),
                ids.size(), label);
    }

    public void resetCategoryOnRightClick(@NotNull String categoryId) {
        List<ResourceLocation> ids = state.questIdsInCategory(categoryId);
        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        String label = cat != null ? cat.displayName() : categoryId;
        if (ids.isEmpty()) {
            ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.no_quests_in"), label);
            return;
        }
        ChronicleNetwork.CHANNEL.sendToServer(
                new C2SBulkQuestActionPacket(ids, C2SBulkQuestActionPacket.Action.RESET));
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.reset_d_quest_s_in"),
                ids.size(), label);
    }

    public void deleteChapter(@NotNull String chapter) {
        String upper = chapter.toUpperCase(Locale.ROOT);

        List<QuestNode> questsInChapter = new ArrayList<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (upper.equalsIgnoreCase(n.getChapter())) questsInChapter.add(n);
        }
        Map<QuestNode, String> savedSnbt = new LinkedHashMap<>();
        for (QuestNode n : questsInChapter) {
            String raw = QuestFileSaver.readRawSnbt(n);
            if (!raw.isBlank()) savedSnbt.put(n, raw);
        }
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");

        boolean wasStub = false;
        try {
            Path f = ChronicleOverviewScreen.chaptersFile();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (line.trim().equalsIgnoreCase(upper)) {
                        wasStub = true;
                        break;
                    }
                }
            }
        } catch (IOException ignored) {}

        ChapterConfig savedConfig = ChapterConfig.get(chapter);
        CategoryDefinition owningCategory = CategoryRegistry.categoryFor(chapter);
        String owningCategoryId = owningCategory != null ? owningCategory.id() : null;

        int questCount = questsInChapter.size();
        boolean finalWasStub = wasStub;

        Runnable applyDelete = () -> {
            for (QuestNode n : questsInChapter) {
                QuestTreeRegistry.removeQuest(n.getId());
                QuestFileSaver.deleteQuestFiles(n);
                if (editorState.selectedNode == n) editorState.selectedNode = null;
            }

            try {
                Path f = ChronicleOverviewScreen.chaptersFile();
                if (Files.exists(f)) {
                    List<String> remaining = new ArrayList<>();
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String cat = line.trim().toUpperCase(Locale.ROOT);
                        if (!cat.isEmpty() && !cat.equals(upper)) remaining.add(cat);
                    }
                    Files.writeString(f, String.join("\n", remaining), StandardCharsets.UTF_8);
                }
            } catch (IOException ignored) {}

            if (owningCategory != null) {
                CategoryRegistry.removeChapterFromCategory(owningCategory.id(), chapter);
                CategoryRegistry.save();
            }

            ChapterConfig.remove(upper);
            ChapterConfig.save();

            state.invalidateChapterCaches();

            if (ctx.selectedChapter().equalsIgnoreCase(chapter)) {
                List<String> remainingChapters = ctx.buildChapterList();
                state.setSelectedChapter(remainingChapters.isEmpty() ? "" : remainingChapters.get(0));
            }
            state.rebuild();
        };

        ctx.pushUndo("Undo: chapter restored (%d quest(s))".formatted(questCount), () -> {
            for (Map.Entry<QuestNode, String> e : savedSnbt.entrySet()) {
                QuestFileSaver.restoreRawSnbt(e.getKey(), e.getValue());
            }
            QuestFileLoader.loadAdditiveFromDisk(base);

            if (finalWasStub) {
                try {
                    Path f = ChronicleOverviewScreen.chaptersFile();
                    List<String> lines = new ArrayList<>();
                    if (Files.exists(f)) lines.addAll(Files.readAllLines(f, StandardCharsets.UTF_8));
                    if (lines.stream().noneMatch(l -> l.trim().equalsIgnoreCase(upper))) lines.add(upper);
                    Files.createDirectories(f.getParent());
                    Files.writeString(f, String.join("\n", lines), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
            }
            ChapterConfig.put(upper, savedConfig);
            ChapterConfig.save();
            if (owningCategoryId != null) {
                CategoryRegistry.addChapterToCategory(owningCategoryId, upper);
                CategoryRegistry.save();
            }

            state.invalidateChapterCaches();
            state.setSelectedChapter(upper);
            state.rebuild();
        }, applyDelete);

        applyDelete.run();
        String countSuffix = questCount > 0 ? " (%d quests)".formatted(questCount) : "";
        ctx.setFeedbackDone(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.chapter_deleted"), chapter,
                countSuffix);
    }

    public void deleteCategoryOnRightClick(@NotNull String categoryId) {
        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        if (cat == null) return;

        if (!cat.chapters().isEmpty() && !categoryId.equals(armedDeleteCategoryId)) {
            armedDeleteCategoryId = categoryId;
            ctx.setFeedback(
                    ChroniclesUIKit
                            .label("phoenix_chronicles.screen.chapter_actions.right_click_delete_category_again"),
                    cat.displayName(), cat.chapters().size());
            return;
        }
        if (cat.chapters().isEmpty() && !CategoryRegistry.childrenOf(categoryId).isEmpty() &&
                !categoryId.equals(armedDeleteCategoryId)) {
            armedDeleteCategoryId = categoryId;
            ctx.setFeedback(
                    ChroniclesUIKit
                            .label("phoenix_chronicles.screen.chapter_actions.right_click_delete_category_again_2"),
                    cat.displayName());
            return;
        }

        armedDeleteCategoryId = null;
        int chapterCount = cat.chapters().size();
        CategoryRegistry.removeCategory(categoryId);
        CategoryRegistry.save();
        state.invalidateChapterCaches();
        state.rebuild();
        String uncatSuffix = chapterCount > 0 ? " (%d chapter(s) uncategorized)".formatted(chapterCount) : "";
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.category_deleted"),
                cat.displayName(), uncatSuffix);
    }

    public void deleteChapterOnRightClick(@NotNull String chapter) {
        int questCount = state.chapterQuestCount(chapter);
        if (questCount > 0 && !chapter.equals(armedDeleteChapterId)) {
            armedDeleteChapterId = chapter;
            ctx.setFeedback(
                    ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.right_click_delete_chapter_again"),
                    ctx.friendly(chapter), questCount);
            return;
        }

        armedDeleteChapterId = null;
        deleteChapter(chapter);
        ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.chapter_deleted_2"),
                ctx.friendly(chapter));
    }

    private void openPasteScreen(@NotNull Screen parent, @Nullable String destCategoryId) {
        Minecraft.getInstance().setScreen(new PasteCategoryScreen(parent, destCategoryId, result -> {
            if (result.newRootId() == null) {
                ctx.setFeedback(ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.can_t_paste_there_too"));
                return;
            }
            if (destCategoryId != null && CategoryRegistry.isCollapsed(destCategoryId))
                CategoryRegistry.toggleCollapsed(destCategoryId);
            CategoryRegistry.save();
            state.invalidateChapterCaches();
            state.rebuild();
            if (!result.warnings().isEmpty()) {
                ctx.setFeedback(ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.pasted_d_chapter_s_d"),
                        CategoryRegistry.pathLabel(result.newRootId()), result.chapters(), result.quests(),
                        result.warnings().size());
                for (String w : result.warnings()) System.err.println("[Phoenix Chronicles] Paste: " + w);
            } else {
                ctx.setFeedback(
                        ChroniclesUIKit.label("phoenix_chronicles.screen.chapter_actions.pasted_d_chapter_s_d_2"),
                        CategoryRegistry.pathLabel(result.newRootId()), result.chapters(), result.quests());
            }
        }));
    }

    public void openSidebarContextMenu(@NotNull SidebarRow row, int mx, int my) {
        List<SidebarPanel.MenuAction> actions = new ArrayList<>();
        Screen parent = state.thisScreen();
        if (row.isFolder()) {
            CategoryDefinition cat = CategoryRegistry.get(row.id());
            String currentLabel = cat != null ? cat.displayName() : row.label();
            actions.add(new SidebarPanel.MenuAction(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.new_folder.title_rename"), () -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.setScreen(new NewFolderScreen(parent, row.id(), currentLabel, id -> state.rebuild()));
                    }));
            if (CategoryRegistry.depthOf(row.id()) + 1 < CategoryRegistry.MAX_DEPTH) {
                actions.add(new SidebarPanel.MenuAction(
                        ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.new_subcategory"), () -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.setScreen(new NewFolderScreen(parent, row.id(), id -> {
                                if (CategoryRegistry.isCollapsed(row.id())) CategoryRegistry.toggleCollapsed(row.id());
                                CategoryRegistry.save();
                                state.rebuild();
                            }));
                        }));
            }
            if (!CategoryRegistry.parentOf(row.id()).isEmpty()) {
                actions.add(new SidebarPanel.MenuAction(
                        ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.move_to_top_level"), () -> {
                            CategoryRegistry.moveCategory(row.id(), null, null);
                            CategoryRegistry.save();
                            state.rebuild();
                        }));
            }
            actions.add(new SidebarPanel.MenuAction(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.copy_category"), () -> {
                        if (CategoryRegistry.copyCategory(row.id())) {
                            ctx.setFeedback(
                                    ChroniclesUIKit.label(
                                            "phoenix_chronicles.screen.chapter_actions.copied_d_categories_d_chapters"),
                                    row.label(),
                                    CategoryRegistry.clipboardSize(), CategoryRegistry.clipboardChapters().size());
                        }
                    }));
            if (CategoryRegistry.hasClipboard()) {
                if (CategoryRegistry.canPasteInto(row.id())) {
                    actions.add(new SidebarPanel.MenuAction(
                            ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.paste_inside"),
                            () -> openPasteScreen(parent, row.id())));
                }
                if (CategoryRegistry.canPasteInto(null)) {
                    actions.add(new SidebarPanel.MenuAction(
                            ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.paste_at_top_level"),
                            () -> openPasteScreen(parent, null)));
                }
            }
            actions.add(new SidebarPanel.MenuAction(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.category_theme"),
                    () -> Minecraft.getInstance().setScreen(new CategoryThemeScreen(parent, row.id()))));
            actions.add(new SidebarPanel.MenuAction(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.force_complete_category"),
                    () -> forceCompleteCategoryOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.reset_category"),
                    () -> resetCategoryOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedDeleteCategoryId) ?
                            ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.confirm_delete_category") :
                            ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.delete_category"),
                    () -> deleteCategoryOnRightClick(row.id())));
        } else {
            actions.add(new SidebarPanel.MenuAction(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.chapter_settings"),
                    () -> Minecraft.getInstance().setScreen(new ChapterThemeScreen(parent, row.id()))));
            actions.add(new SidebarPanel.MenuAction(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.force_complete_chapter"),
                    () -> forceCompleteChapterOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.reset_chapter"),
                    () -> resetChapterOnRightClick(row.id())));
            actions.add(new SidebarPanel.MenuAction(
                    row.id().equals(armedDeleteChapterId) ?
                            ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.confirm_delete_chapter") :
                            ChroniclesUIKit.str("phoenix_chronicles.screen.chapter_actions.delete_chapter"),
                    () -> deleteChapterOnRightClick(row.id())));
        }
        state.openContextMenuFor(mx, my, actions);
    }
}

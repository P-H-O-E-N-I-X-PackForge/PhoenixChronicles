package net.phoenixvine.chronicles.client.screen.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.phoenixvine.chronicles.client.screen.ChronicleOverviewScreen;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.common.codec.QuestCopyTransform;
import net.phoenixvine.chronicles.common.codec.QuestFileLoader;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.codec.QuestFileWatcher;
import net.phoenixvine.chronicles.common.model.CategoryDefinition;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.registry.CategoryRegistry;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Pastes the copied category tree, optionally with its chapters and quests. Copied chapters get new ids, copied quests
 * get new ids and task ids, and links between copied quests (parent, prerequisites, links, chapter/category
 * prerequisites) point at the copies. Links to anything outside the copy are left pointing at the original.
 */
public final class CategoryContentCopier {

    private CategoryContentCopier() {}

    /** @param newRootId id of the pasted root category, or null when nothing was pasted */
    public record Result(@Nullable String newRootId, int chapters, int quests, @NotNull List<String> warnings) {}

    public static @NotNull Result paste(@Nullable String destParent, @Nullable String rootCondition,
                                        @Nullable String find, @Nullable String replace, boolean copyContent) {
        List<String> warnings = new ArrayList<>();
        if (!CategoryRegistry.canPasteInto(destParent)) return new Result(null, 0, 0, warnings);

        List<String> sourceChapters = copyContent ? CategoryRegistry.clipboardChapters() : List.of();

        // 1. new chapter ids
        Set<String> usedChapters = usedChapterIds();
        Map<String, String> chapterMap = new LinkedHashMap<>();
        for (String ch : sourceChapters) {
            String base = ch.toUpperCase(Locale.ROOT);
            String id = base;
            for (int n = 2; usedChapters.contains(id); n++) id = base + "_" + n;
            usedChapters.add(id);
            chapterMap.put(base, id);
        }

        // 2. the quests to copy, with new ids (quest ids are global, so they must not collide with anything)
        Set<String> usedQuestIds = usedQuestIds();
        Map<String, String> questMap = new LinkedHashMap<>();
        Map<String, QuestNode> questSources = new LinkedHashMap<>();
        for (QuestNode node : new ArrayList<>(QuestTreeRegistry.getAllQuests().values())) {
            String chapter = node.getChapter() == null ? "MAIN" : node.getChapter().toUpperCase(Locale.ROOT);
            if (!chapterMap.containsKey(chapter)) continue;
            String oldId = node.getId().getPath().toLowerCase(Locale.ROOT);
            if (!Files.exists(QuestFileSaver.getQuestSnbtPath(node))) {
                warnings.add("Quest '" + oldId + "' has no file on disk and was not copied");
                continue;
            }
            String newId = oldId;
            for (int n = 2; usedQuestIds.contains(newId); n++) newId = oldId + "_" + n;
            usedQuestIds.add(newId);
            questMap.put(oldId, newId);
            questSources.put(oldId, node);
        }

        // 3. the categories (this also tells us the new category ids)
        Map<String, String> categoryMap = new HashMap<>();
        String newRoot = CategoryRegistry.pasteCategory(destParent, rootCondition, find, replace,
                copyContent ? chapterMap : null, categoryMap);
        if (newRoot == null) return new Result(null, 0, 0, warnings);

        // 4. chapter settings, folders and empty-chapter entries
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        Set<String> chaptersWithQuests = new HashSet<>();
        for (QuestNode node : questSources.values())
            chaptersWithQuests.add(node.getChapter() == null ? "MAIN" : node.getChapter().toUpperCase(Locale.ROOT));

        List<String> stubsToAdd = new ArrayList<>();
        for (Map.Entry<String, String> e : chapterMap.entrySet()) {
            String oldChapter = e.getKey();
            String newChapter = e.getValue();

            ChapterConfig cfg = ChapterConfig.fromJson(ChapterConfig.get(oldChapter).toJson());
            String shown = ChapterConfig.getResolvedDisplayName(oldChapter);
            if (shown != null && !shown.isBlank()) cfg.setDisplayName(shown);
            String parentChapter = cfg.getParentChapter();
            if (!parentChapter.isEmpty() && chapterMap.containsKey(parentChapter.toUpperCase(Locale.ROOT)))
                cfg.setParentChapter(chapterMap.get(parentChapter.toUpperCase(Locale.ROOT)));
            ChapterConfig.put(newChapter, cfg);

            copyChapterJson(base, oldChapter, newChapter, warnings);
            if (!chaptersWithQuests.contains(oldChapter)) stubsToAdd.add(newChapter);
        }
        if (!chapterMap.isEmpty()) ChapterConfig.save();
        appendStubChapters(stubsToAdd);

        // 5. quests
        int written = 0;
        for (Map.Entry<String, QuestNode> e : questSources.entrySet()) {
            String oldId = e.getKey();
            QuestNode node = e.getValue();
            String oldChapter = node.getChapter() == null ? "MAIN" : node.getChapter().toUpperCase(Locale.ROOT);
            String newChapter = chapterMap.get(oldChapter);
            try {
                Path srcFile = QuestFileSaver.getQuestSnbtPath(node);
                CompoundTag tag = TagParser.parseTag(Files.readString(srcFile, StandardCharsets.UTF_8));
                QuestCopyTransform.apply(tag, questMap.get(oldId), newChapter, questMap, chapterMap, categoryMap, find,
                        replace);

                Path destDir = base.resolve("quests").resolve(newChapter.toLowerCase(Locale.ROOT));
                Files.createDirectories(destDir);
                QuestFileWatcher.suppressNextReload();
                Files.writeString(destDir.resolve(questMap.get(oldId) + ".snbt"), QuestFileSaver.prettySnbt(tag),
                        StandardCharsets.UTF_8);

                Path md = QuestFileSaver.getQuestMarkdownPath(node);
                if (Files.exists(md)) {
                    Files.writeString(destDir.resolve(questMap.get(oldId) + ".md"),
                            Files.readString(md, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                }
                written++;
            } catch (Exception ex) {
                warnings.add("Quest '" + oldId + "' could not be copied: " + ex.getMessage());
                questMap.remove(oldId);
            }
        }

        // 6. load the new quests (all of them at once so links between them resolve)
        if (written > 0) {
            QuestFileWatcher.suppressNextReload();
            QuestFileLoader.loadAdditiveFromDisk(base);
            QuestFileWatcher.suppressNextReload();
        }
        return new Result(newRoot, chapterMap.size(), written, warnings);
    }

    private static void copyChapterJson(Path base, String oldChapter, String newChapter, List<String> warnings) {
        Path src = base.resolve("quests").resolve(oldChapter.toLowerCase(Locale.ROOT))
                .resolve(oldChapter.toLowerCase(Locale.ROOT) + ".json");
        if (!Files.exists(src)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(src, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return;
            JsonObject json = parsed.getAsJsonObject();
            json.addProperty("id", newChapter.toLowerCase(Locale.ROOT));
            Path dir = base.resolve("quests").resolve(newChapter.toLowerCase(Locale.ROOT));
            Files.createDirectories(dir);
            QuestFileWatcher.suppressNextReload();
            Files.writeString(dir.resolve(newChapter.toLowerCase(Locale.ROOT) + ".json"), json.toString(),
                    StandardCharsets.UTF_8);
        } catch (Exception ex) {
            warnings.add("Chapter '" + oldChapter + "' settings file could not be copied: " + ex.getMessage());
        }
    }

    private static void appendStubChapters(List<String> chapters) {
        if (chapters.isEmpty()) return;
        try {
            Path f = ChronicleOverviewScreen.chaptersFile();
            List<String> lines = Files.exists(f) ? new ArrayList<>(Files.readAllLines(f, StandardCharsets.UTF_8)) :
                    new ArrayList<>();
            for (String c : chapters) if (!lines.contains(c)) lines.add(c);
            Files.createDirectories(f.getParent());
            Files.write(f, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    /** Every chapter id already in use: on quests, on disk, as empty chapters, or in a category. */
    private static Set<String> usedChapterIds() {
        Set<String> used = new HashSet<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values())
            if (n.getChapter() != null) used.add(n.getChapter().toUpperCase(Locale.ROOT));
        for (CategoryDefinition c : CategoryRegistry.getCategories()) used.addAll(c.chapters());
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        try (Stream<Path> dirs = Files.exists(base.resolve("quests")) ? Files.list(base.resolve("quests")) :
                Stream.<Path>empty()) {
            dirs.filter(Files::isDirectory).forEach(d -> used.add(d.getFileName().toString().toUpperCase(Locale.ROOT)));
        } catch (IOException ignored) {}
        try {
            Path f = ChronicleOverviewScreen.chaptersFile();
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) used.add(line.trim().toUpperCase(Locale.ROOT));
                }
            }
        } catch (IOException ignored) {}
        used.add("ALL");
        return used;
    }

    /** Every quest id already in use, loaded or just sitting in a file. */
    private static Set<String> usedQuestIds() {
        Set<String> used = new HashSet<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values())
            used.add(n.getId().getPath().toLowerCase(Locale.ROOT));
        Path quests = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles")
                .resolve("quests");
        if (Files.exists(quests)) {
            try (Stream<Path> walk = Files.walk(quests)) {
                walk.filter(p -> p.toString().endsWith(".snbt")).forEach(p -> {
                    String name = p.getFileName().toString();
                    used.add(name.substring(0, name.length() - ".snbt".length()).toLowerCase(Locale.ROOT));
                });
            } catch (IOException ignored) {}
        }
        return used;
    }
}

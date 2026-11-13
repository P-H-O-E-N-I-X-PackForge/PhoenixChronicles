package net.phoenixvine.chronicles.common.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.phoenixvine.chronicles.common.codec.CategoryLoader;
import net.phoenixvine.chronicles.common.codec.QuestFileWatcher;
import net.phoenixvine.chronicles.common.model.CategoryDefinition;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CategoryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryRegistry.class);

    public static final int MAX_DEPTH = 5;
    private static final int GUARD = 64;

    private static final Map<String, CategoryDefinition> CATEGORIES = new LinkedHashMap<>();
    private static final Set<String> collapsed = new HashSet<>();
    private static final List<String> categoryOrder = new ArrayList<>();
    private static final List<String> standaloneOrder = new ArrayList<>();
    private static final List<String> topLevelOrder = new ArrayList<>();
    
    private static final Map<String, List<String>> itemOrder = new HashMap<>();
    private static final String CATEGORY_TOKEN_PREFIX = "C:";
    private static final String CHAPTER_TOKEN_PREFIX = "S:";

    private static Path categoriesFolder = null;
    private static Path uiStatePath = null;

    private CategoryRegistry() {}

    public static synchronized void register(CategoryDefinition category) {
        if (category != null) CATEGORIES.put(category.id(), category);
    }

    @Nullable
    public static synchronized CategoryDefinition get(String id) {
        return CATEGORIES.get(id);
    }

    public static synchronized void clear() {
        CATEGORIES.clear();
    }

    public static synchronized List<CategoryDefinition> getCategories() {
        List<CategoryDefinition> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : childIds("")) appendSubtree(id, ordered, seen, 0);
        for (CategoryDefinition c : CATEGORIES.values()) {
            if (seen.add(c.id())) ordered.add(c);
        }
        return Collections.unmodifiableList(ordered);
    }

    private static void appendSubtree(String id, List<CategoryDefinition> out, Set<String> seen, int guard) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null || guard > GUARD || !seen.add(id)) return;
        out.add(c);
        for (String child : childIds(id)) appendSubtree(child, out, seen, guard + 1);
    }

    private static String effectiveParent(CategoryDefinition c) {
        String p = c.parent();
        if (p == null || p.isEmpty() || p.equals(c.id()) || !CATEGORIES.containsKey(p)) return "";
        return p;
    }

    private static List<String> childIds(String parent) {
        List<String> kids = new ArrayList<>();
        for (CategoryDefinition c : CATEGORIES.values()) {
            if (effectiveParent(c).equals(parent)) kids.add(c.id());
        }
        List<String> ordered = new ArrayList<>();
        if (parent.isEmpty()) {
            for (String id : categoryOrder) if (kids.remove(id)) ordered.add(id);
        } else {
            for (String token : itemOrder.getOrDefault(parent, List.of())) {
                if (isCategoryToken(token) && kids.remove(tokenId(token))) ordered.add(tokenId(token));
            }
        }
        ordered.addAll(kids);
        return ordered;
    }

    public static boolean isChapterToken(String token) {
        return token.startsWith(CHAPTER_TOKEN_PREFIX);
    }

    public static synchronized List<String> folderItems(String categoryId) {
        CategoryDefinition c = CATEGORIES.get(categoryId);
        if (c == null) return new ArrayList<>();
        Set<String> chapterSet = new HashSet<>(c.chapters());
        List<String> kids = childIds(categoryId);
        Set<String> kidSet = new HashSet<>(kids);

        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String token : itemOrder.getOrDefault(categoryId, List.of())) {
            boolean valid = isCategoryToken(token) ? kidSet.contains(tokenId(token)) :
                    isChapterToken(token) && chapterSet.contains(tokenId(token));
            if (valid && seen.add(token)) out.add(token);
        }
        for (String chapter : c.chapters()) {
            String token = chapterToken(chapter);
            if (seen.add(token)) out.add(token);
        }
        for (String kid : kids) {
            String token = categoryToken(kid);
            if (seen.add(token)) out.add(token);
        }
        return out;
    }

    public static synchronized void placeInFolder(String folderId, String token, @Nullable String beforeToken) {
        CategoryDefinition c = CATEGORIES.get(folderId);
        if (c == null) return;
        List<String> items = folderItems(folderId);
        items.remove(token);
        int idx = beforeToken == null ? items.size() : items.indexOf(beforeToken);
        if (idx < 0) idx = items.size();
        items.add(idx, token);
        itemOrder.put(folderId, items);

        List<String> chapters = new ArrayList<>();
        for (String t : items) {
            if (isChapterToken(t) && c.chapters().contains(tokenId(t))) chapters.add(tokenId(t));
        }
        for (String ch : c.chapters()) if (!chapters.contains(ch)) chapters.add(ch);
        if (!chapters.equals(c.chapters())) {
            CategoryDefinition updated = c.withChapters(chapters);
            CATEGORIES.put(folderId, updated);
            writeCategory(updated);
        }
        saveUiState();
    }

    public static synchronized void moveChapterTo(String chapter, String destCategory, @Nullable String beforeToken) {
        if (!CATEGORIES.containsKey(destCategory)) return;
        String upper = chapter.toUpperCase();
        CategoryDefinition current = categoryFor(upper);
        if (current != null && !current.id().equals(destCategory)) removeChapterFromCategory(current.id(), upper);
        addChapterToCategory(destCategory, upper);
        placeInFolder(destCategory, chapterToken(upper), beforeToken);
    }

    public static synchronized List<CategoryDefinition> childrenOf(String parentId) {
        List<CategoryDefinition> out = new ArrayList<>();
        for (String id : childIds(parentId == null ? "" : parentId)) {
            CategoryDefinition c = CATEGORIES.get(id);
            if (c != null) out.add(c);
        }
        return out;
    }

    public static synchronized String parentOf(String id) {
        CategoryDefinition c = CATEGORIES.get(id);
        return c == null ? "" : effectiveParent(c);
    }

    public static synchronized int depthOf(String id) {
        int depth = 0;
        String cur = parentOf(id);
        while (!cur.isEmpty() && depth < GUARD) {
            depth++;
            cur = parentOf(cur);
        }
        return depth;
    }

    public static synchronized String rootOf(String id) {
        String cur = id;
        for (int i = 0; i < GUARD; i++) {
            String p = parentOf(cur);
            if (p.isEmpty()) return cur;
            cur = p;
        }
        return cur;
    }

    public static synchronized boolean isDescendantOf(String candidate, String ancestor) {
        String cur = parentOf(candidate);
        for (int i = 0; i < GUARD && !cur.isEmpty(); i++) {
            if (cur.equals(ancestor)) return true;
            cur = parentOf(cur);
        }
        return false;
    }

    private static int subtreeHeight(String id, int guard) {
        if (guard > GUARD) return 0;
        int height = 0;
        for (String child : childIds(id)) height = Math.max(height, 1 + subtreeHeight(child, guard + 1));
        return height;
    }

    public static synchronized boolean canNest(String id, String newParent) {
        if (id == null || newParent == null || id.equals(newParent)) return false;
        if (!CATEGORIES.containsKey(id) || !CATEGORIES.containsKey(newParent)) return false;
        if (isDescendantOf(newParent, id)) return false;
        return depthOf(newParent) + 1 + subtreeHeight(id, 0) < MAX_DEPTH;
    }

    public static synchronized String pathLabel(String id) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null) return id;
        StringBuilder sb = new StringBuilder(c.displayName());
        String cur = effectiveParent(c);
        for (int i = 0; i < GUARD && !cur.isEmpty(); i++) {
            CategoryDefinition p = CATEGORIES.get(cur);
            if (p == null) break;
            sb.insert(0, p.displayName() + " / ");
            cur = effectiveParent(p);
        }
        return sb.toString();
    }

    public static synchronized List<String> chaptersIncludingDescendants(String id) {
        List<String> out = new ArrayList<>();
        collectChapters(id, out, new HashSet<>(), 0);
        return out;
    }

    private static void collectChapters(String id, List<String> out, Set<String> seen, int guard) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null || guard > GUARD || !seen.add(id)) return;
        for (String chapter : c.chapters()) if (!out.contains(chapter)) out.add(chapter);
        for (String child : childIds(id)) collectChapters(child, out, seen, guard + 1);
    }

    private static void sanitizeHierarchy() {
        for (CategoryDefinition c : new ArrayList<>(CATEGORIES.values())) {
            String p = c.parent();
            if (p == null || p.isEmpty()) continue;
            if (!CATEGORIES.containsKey(p) || p.equals(c.id()) || parentChainLoops(c.id())) {
                LOGGER.warn("[Chronicles] Category '{}' has an invalid parent '{}'; treating it as top-level", c.id(),
                        p);
                CATEGORIES.put(c.id(), c.withParent(""));
            }
        }
    }

    private static boolean parentChainLoops(String id) {
        Set<String> visited = new HashSet<>();
        String cur = id;
        while (cur != null && !cur.isEmpty()) {
            if (!visited.add(cur)) return true;
            CategoryDefinition c = CATEGORIES.get(cur);
            cur = c == null ? "" : c.parent();
        }
        return false;
    }

    public static synchronized boolean moveCategory(String id, @Nullable String newParent,
                                                    @Nullable String beforeToken) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null) return false;
        String parent = newParent == null ? "" : newParent;
        if (!parent.isEmpty() && !canNest(id, parent)) return false;

        String oldParent = effectiveParent(c);
        if (!parent.equals(oldParent) || !parent.equals(c.parent())) {
            if (!oldParent.isEmpty()) {
                List<String> old = itemOrder.get(oldParent);
                if (old != null) old.remove(categoryToken(id));
            }
            CategoryDefinition updated = c.withParent(parent);
            CATEGORIES.put(id, updated);
            writeCategory(updated);
        }

        if (!parent.isEmpty()) {
            topLevelOrder.remove(categoryToken(id));
            categoryOrder.remove(id);
            placeInFolder(parent, categoryToken(id), beforeToken);
        } else {
            saveUiState();
        }
        return true;
    }

    public record CategorySnapshot(String id, String displayName, int color, String icon, int nameColor,
                                   String enableIf, List<String> chapters, List<String> items,
                                   List<CategorySnapshot> children) {}

    private static CategorySnapshot clipboard = null;

    public static synchronized boolean copyCategory(String id) {
        CategorySnapshot snap = snapshotOf(id, 0);
        clipboard = snap;
        return snap != null;
    }

    private static @Nullable CategorySnapshot snapshotOf(String id, int guard) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null || guard > GUARD) return null;
        List<CategorySnapshot> kids = new ArrayList<>();
        for (String kid : childIds(id)) {
            CategorySnapshot k = snapshotOf(kid, guard + 1);
            if (k != null) kids.add(k);
        }
        return new CategorySnapshot(c.id(), c.displayName(), c.color(), c.icon() == null ? "" : c.icon(),
                c.nameColor(), c.enableIf() == null ? "" : c.enableIf(), new ArrayList<>(c.chapters()),
                folderItems(id), kids);
    }

    public static synchronized boolean hasClipboard() {
        return clipboard != null;
    }

    public static synchronized String clipboardLabel() {
        return clipboard == null ? "" : clipboard.displayName();
    }

    public static synchronized List<String> clipboardChapters() {
        List<String> out = new ArrayList<>();
        if (clipboard != null) collectSnapshotChapters(clipboard, out);
        return out;
    }

    private static void collectSnapshotChapters(CategorySnapshot n, List<String> out) {
        for (String token : n.items()) {
            if (isChapterToken(token)) {
                String ch = tokenId(token);
                if (n.chapters().contains(ch) && !out.contains(ch)) out.add(ch);
            } else {
                for (CategorySnapshot k : n.children()) {
                    if (categoryToken(k.id()).equals(token)) collectSnapshotChapters(k, out);
                }
            }
        }
        for (String ch : n.chapters()) if (!out.contains(ch)) out.add(ch);
    }

    public static synchronized int clipboardSize() {
        return clipboard == null ? 0 : countNodes(clipboard);
    }

    private static int countNodes(CategorySnapshot n) {
        int total = 1;
        for (CategorySnapshot k : n.children()) total += countNodes(k);
        return total;
    }

    private static int snapshotHeight(CategorySnapshot n) {
        int h = 0;
        for (CategorySnapshot k : n.children()) h = Math.max(h, 1 + snapshotHeight(k));
        return h;
    }

    public static synchronized boolean canPasteInto(@Nullable String parentId) {
        if (clipboard == null) return false;
        String parent = parentId == null ? "" : parentId;
        if (parent.isEmpty()) return snapshotHeight(clipboard) < MAX_DEPTH;
        return CATEGORIES.containsKey(parent) && depthOf(parent) + 1 + snapshotHeight(clipboard) < MAX_DEPTH;
    }

    public static synchronized @Nullable String pasteCategory(@Nullable String parentId,
                                                              @Nullable String rootCondition, @Nullable String find,
                                                              @Nullable String replace) {
        return pasteCategory(parentId, rootCondition, find, replace, null, null);
    }

    public static synchronized @Nullable String pasteCategory(@Nullable String parentId,
                                                              @Nullable String rootCondition, @Nullable String find,
                                                              @Nullable String replace,
                                                              @Nullable Map<String, String> chapterMap,
                                                              @Nullable Map<String, String> categoryMapOut) {
        if (!canPasteInto(parentId)) return null;
        String parent = parentId == null ? "" : parentId;
        String newRoot = pasteNode(clipboard, parent, rootCondition, find, replace, true, chapterMap,
                categoryMapOut);
        saveUiState();
        return newRoot;
    }

    private static String pasteNode(CategorySnapshot node, String parent, @Nullable String rootCondition,
                                    @Nullable String find, @Nullable String replace, boolean isRoot,
                                    @Nullable Map<String, String> chapterMap,
                                    @Nullable Map<String, String> categoryMapOut) {
        String id = node.id();
        for (int n = 2; CATEGORIES.containsKey(id); n++) id = node.id() + "_" + n;

        String name = node.displayName();
        if (isRoot) {
            for (String sibling : childIds(parent)) {
                CategoryDefinition sc = CATEGORIES.get(sibling);
                if (sc != null && sc.displayName().equals(name)) {
                    name = name + " (copy)";
                    break;
                }
            }
        }

        String condition = node.enableIf();
        if (isRoot && rootCondition != null && !rootCondition.isBlank()) {
            condition = rootCondition.trim();
        } else if (find != null && !find.isEmpty() && condition != null) {
            condition = condition.replace(find, replace == null ? "" : replace);
        }

        List<String> chapters = new ArrayList<>();
        if (chapterMap != null) {
            for (String ch : node.chapters()) {
                String mapped = chapterMap.get(ch);
                if (mapped != null && !chapters.contains(mapped)) chapters.add(mapped);
            }
        }
        CategoryDefinition created = new CategoryDefinition(id, name, chapters, node.color(), node.icon(),
                node.nameColor(), condition == null ? "" : condition, parent);
        CATEGORIES.put(id, created);
        if (parent.isEmpty() && !categoryOrder.contains(id)) categoryOrder.add(id);
        writeCategory(created);
        if (categoryMapOut != null) categoryMapOut.put(node.id(), id);

        Map<String, String> childIdMap = new HashMap<>();
        for (CategorySnapshot child : node.children()) {
            String newChild = pasteNode(child, id, rootCondition, find, replace, false, chapterMap, categoryMapOut);
            childIdMap.put(child.id(), newChild);
        }

        List<String> items = new ArrayList<>();
        for (String token : node.items()) {
            if (isChapterToken(token)) {
                String mapped = chapterMap == null ? null : chapterMap.get(tokenId(token));
                if (mapped != null && chapters.contains(mapped)) items.add(chapterToken(mapped));
            } else {
                String mapped = childIdMap.get(tokenId(token));
                if (mapped != null) items.add(categoryToken(mapped));
            }
        }
        if (!items.isEmpty()) itemOrder.put(id, items);
        return id;
    }

    public static synchronized boolean isCollapsed(String categoryId) {
        return collapsed.contains(categoryId);
    }

    public static synchronized void toggleCollapsed(String categoryId) {
        if (!collapsed.remove(categoryId)) collapsed.add(categoryId);
    }

    @Nullable
    public static synchronized CategoryDefinition categoryFor(String chapter) {
        String upper = chapter.toUpperCase();
        for (CategoryDefinition c : CATEGORIES.values())
            if (c.chapters().contains(upper)) return c;
        return null;
    }

    public static synchronized void load(Path configDir) {
        categoriesFolder = configDir.resolve("categories");
        uiStatePath = configDir.resolve("category_ui_state.snbt");
        CategoryLoader.reloadAllCategoriesFromDisk(categoriesFolder);
        sanitizeHierarchy();
        loadUiState();
    }

    public static synchronized void save() {
        saveUiState();
    }

    public static synchronized List<String> getStandaloneOrder() {
        return Collections.unmodifiableList(standaloneOrder);
    }

    public static String categoryToken(String categoryId) {
        return CATEGORY_TOKEN_PREFIX + categoryId;
    }

    public static String chapterToken(String chapter) {
        return CHAPTER_TOKEN_PREFIX + chapter.toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean isCategoryToken(String token) {
        return token.startsWith(CATEGORY_TOKEN_PREFIX);
    }

    public static String tokenId(String token) {
        return token.substring(CATEGORY_TOKEN_PREFIX.length());
    }

    public static synchronized List<String> resolveTopLevel(List<String> standaloneChapters) {
        Set<String> valid = new HashSet<>();
        List<String> categoryTokens = new ArrayList<>();
        for (String id : childIds("")) {
            String token = categoryToken(id);
            valid.add(token);
            categoryTokens.add(token);
        }
        Set<String> standaloneUpper = new HashSet<>();
        for (String c : standaloneChapters) standaloneUpper.add(c.toUpperCase(java.util.Locale.ROOT));
        List<String> standaloneTokens = new ArrayList<>();
        for (String c : standaloneOrder) if (standaloneUpper.contains(c)) standaloneTokens.add(chapterToken(c));
        for (String c : standaloneUpper) if (!standaloneOrder.contains(c)) standaloneTokens.add(chapterToken(c));
        valid.addAll(standaloneTokens);

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String token : topLevelOrder) if (valid.contains(token) && seen.add(token)) result.add(token);
        for (String token : categoryTokens) if (seen.add(token)) result.add(token);
        for (String token : standaloneTokens) if (seen.add(token)) result.add(token);
        return result;
    }

    public static synchronized void moveTopLevel(String token, @Nullable String beforeToken,
                                                 List<String> standaloneChapters) {
        if (token.equals(beforeToken)) return;
        List<String> order = resolveTopLevel(standaloneChapters);
        order.remove(token);
        int idx = beforeToken == null ? order.size() : order.indexOf(beforeToken);
        if (idx < 0) idx = order.size();
        order.add(idx, token);
        storeTopLevel(order);
    }

    public static synchronized void ensureLeadingStandalone(List<String> orderedChapterIds) {
        int insertAt = 0;
        boolean changed = false;
        for (String id : orderedChapterIds) {
            String token = chapterToken(id);
            if (topLevelOrder.contains(token)) continue;
            topLevelOrder.add(insertAt++, token);
            changed = true;
        }
        if (changed) saveUiState();
    }

    private static void storeTopLevel(List<String> order) {
        for (String previous : topLevelOrder) if (!order.contains(previous)) order.add(previous);
        topLevelOrder.clear();
        topLevelOrder.addAll(order);
        categoryOrder.clear();
        standaloneOrder.clear();
        for (String token : order) {
            if (isCategoryToken(token)) categoryOrder.add(tokenId(token));
            else standaloneOrder.add(tokenId(token));
        }
        saveUiState();
    }

    public static synchronized void reorderCategoryChapter(String categoryId, String chapId, String targetId) {
        CategoryDefinition cat = CATEGORIES.get(categoryId);
        if (cat == null) return;

        List<String> chapters = new ArrayList<>(cat.chapters());
        String upperChapId = chapId.toUpperCase();
        chapters.remove(upperChapId);

        int idx;
        if (targetId == null) {
            idx = chapters.size();
        } else {
            String upperTargetId = targetId.toUpperCase();
            int targetIdx = chapters.indexOf(upperTargetId);
            if (targetIdx >= 0) {
                idx = targetIdx;
            } else {
                idx = chapters.size();
            }
        }

        chapters.add(Math.max(0, Math.min(chapters.size(), idx)), upperChapId);

        CategoryDefinition updated = cat.withChapters(chapters);
        CATEGORIES.put(categoryId, updated);
        writeCategory(updated);
    }

    public static synchronized void addCategory(String id, String label) {
        addCategory(id, label, null);
    }

    public static synchronized void addCategory(String id, String label, @Nullable String parent) {
        if (CATEGORIES.containsKey(id)) return;
        String p = parent == null ? "" : parent;
        if (!p.isEmpty() && (!CATEGORIES.containsKey(p) || depthOf(p) + 1 >= MAX_DEPTH)) p = "";
        CategoryDefinition category = new CategoryDefinition(id, label, new ArrayList<>(), 0, "", 0, "", p);
        CATEGORIES.put(id, category);
        if (p.isEmpty()) {
            if (!categoryOrder.contains(id)) categoryOrder.add(id);
        }
        writeCategory(category);
    }

    public static synchronized void removeCategory(String id) {
        CategoryDefinition removed = CATEGORIES.get(id);
        String grand = removed == null ? "" : effectiveParent(removed);
        List<String> kids = childIds(id);

        List<String> siblingOrder = grand.isEmpty() ? null : folderItems(grand);
        for (String kid : kids) {
            CategoryDefinition k = CATEGORIES.get(kid);
            if (k == null) continue;
            CategoryDefinition promoted = k.withParent(grand);
            CATEGORIES.put(kid, promoted);
            writeCategory(promoted);
        }

        if (grand.isEmpty()) {
            int topIdx = topLevelOrder.indexOf(categoryToken(id));
            if (topIdx >= 0) {
                for (int i = 0; i < kids.size(); i++) {
                    if (!topLevelOrder.contains(categoryToken(kids.get(i))))
                        topLevelOrder.add(topIdx + i, categoryToken(kids.get(i)));
                }
            }
            int catIdx = categoryOrder.indexOf(id);
            if (catIdx >= 0) {
                for (int i = 0; i < kids.size(); i++) {
                    if (!categoryOrder.contains(kids.get(i))) categoryOrder.add(catIdx + i, kids.get(i));
                }
            }
        } else {
            int at = siblingOrder.indexOf(categoryToken(id));
            if (at < 0) at = siblingOrder.size();
            List<String> kidTokens = new ArrayList<>();
            for (String kid : kids) kidTokens.add(categoryToken(kid));
            siblingOrder.addAll(at, kidTokens);
            siblingOrder.remove(categoryToken(id));
            itemOrder.put(grand, siblingOrder);
        }

        CATEGORIES.remove(id);
        categoryOrder.remove(id);
        topLevelOrder.remove(categoryToken(id));
        collapsed.remove(id);
        itemOrder.remove(id);
        for (List<String> order : itemOrder.values()) order.remove(categoryToken(id));
        if (categoriesFolder != null) {
            QuestFileWatcher.suppressNextReload();
            CategoryLoader.deleteCategoryFile(categoriesFolder, id);
        }
    }

    public static synchronized void renameCategory(String id, String newLabel) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null) return;
        CategoryDefinition updated = c.withDisplayName(newLabel);
        CATEGORIES.put(id, updated);
        writeCategory(updated);
    }

    public static synchronized void updateTheme(String id, int color, String icon, int nameColor) {
        CategoryDefinition c = CATEGORIES.get(id);
        if (c == null) return;
        CategoryDefinition updated = c.withTheme(color, icon, nameColor);
        CATEGORIES.put(id, updated);
        writeCategory(updated);
    }

    public static synchronized void addChapterToCategory(String categoryId, String chapter) {
        CategoryDefinition c = CATEGORIES.get(categoryId);
        if (c == null) return;
        List<String> chaps = new ArrayList<>(c.chapters());
        String upper = chapter.toUpperCase();
        if (!chaps.contains(upper)) chaps.add(upper);
        CategoryDefinition updated = c.withChapters(chaps);
        CATEGORIES.put(categoryId, updated);
        writeCategory(updated);
    }

    public static boolean isEnabledFor(@Nullable CategoryDefinition category,
                                       @Nullable net.minecraft.world.entity.player.Player player) {
        if (category == null) return true;
        for (CategoryDefinition c : withAncestors(category)) {
            if (c.hasCondition() && !net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags.evaluate(c.enableIf(),
                    null, player, "category " + c.id() + " enable_if"))
                return false;
        }
        return true;
    }

    private static synchronized List<CategoryDefinition> withAncestors(CategoryDefinition category) {
        List<CategoryDefinition> chain = new ArrayList<>();
        chain.add(category);
        String cur = effectiveParent(category);
        for (int i = 0; i < GUARD && !cur.isEmpty(); i++) {
            CategoryDefinition p = CATEGORIES.get(cur);
            if (p == null) break;
            chain.add(p);
            cur = effectiveParent(p);
        }
        return chain;
    }

    public static synchronized void removeChapterFromCategory(String categoryId, String chapter) {
        CategoryDefinition c = CATEGORIES.get(categoryId);
        if (c == null) return;
        List<String> chaps = new ArrayList<>(c.chapters());
        chaps.remove(chapter.toUpperCase());
        CategoryDefinition updated = c.withChapters(chaps);
        CATEGORIES.put(categoryId, updated);
        writeCategory(updated);
    }

    private static void writeCategory(CategoryDefinition category) {
        if (categoriesFolder == null) return;
        QuestFileWatcher.suppressNextReload();
        CategoryLoader.writeCategoryFile(categoriesFolder, category);
    }

    private static void loadUiState() {
        collapsed.clear();
        categoryOrder.clear();
        standaloneOrder.clear();
        topLevelOrder.clear();
        itemOrder.clear();
        if (uiStatePath == null || !Files.exists(uiStatePath)) return;
        try {
            String raw = Files.readString(uiStatePath, StandardCharsets.UTF_8);
            CompoundTag root = TagParser.parseTag(raw);
            if (root.contains("collapsed")) {
                ListTag l = root.getList("collapsed", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) collapsed.add(l.getString(i));
            }
            if (root.contains("categoryOrder")) {
                ListTag l = root.getList("categoryOrder", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) categoryOrder.add(l.getString(i));
            }
            if (root.contains("standaloneOrder")) {
                ListTag l = root.getList("standaloneOrder", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) standaloneOrder.add(l.getString(i).toUpperCase());
            }
            if (root.contains("topLevelOrder")) {
                ListTag l = root.getList("topLevelOrder", Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) topLevelOrder.add(l.getString(i));
            }
            if (root.contains("childOrder")) { 
                ListTag l = root.getList("childOrder", Tag.TAG_COMPOUND);
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag entry = l.getCompound(i);
                    String parent = entry.getString("parent");
                    if (parent.isEmpty()) continue;
                    ListTag kids = entry.getList("children", Tag.TAG_STRING);
                    List<String> order = new ArrayList<>();
                    for (int k = 0; k < kids.size(); k++) order.add(categoryToken(kids.getString(k)));
                    itemOrder.put(parent, order);
                }
            }
            if (root.contains("folderOrder")) {
                ListTag l = root.getList("folderOrder", Tag.TAG_COMPOUND);
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag entry = l.getCompound(i);
                    String folder = entry.getString("folder");
                    if (folder.isEmpty()) continue;
                    ListTag items = entry.getList("items", Tag.TAG_STRING);
                    List<String> order = new ArrayList<>();
                    for (int k = 0; k < items.size(); k++) order.add(items.getString(k));
                    itemOrder.put(folder, order);
                }
            }
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to load category_ui_state.snbt: " + e.getMessage());
        }
    }

    private static void saveUiState() {
        if (uiStatePath == null) return;
        try {

            QuestFileWatcher.suppressNextReload();
            CompoundTag root = new CompoundTag();
            ListTag collapsedList = new ListTag();
            for (String id : collapsed) collapsedList.add(StringTag.valueOf(id));
            root.put("collapsed", collapsedList);
            ListTag orderList = new ListTag();
            for (String id : categoryOrder) orderList.add(StringTag.valueOf(id));
            root.put("categoryOrder", orderList);
            ListTag standaloneList = new ListTag();
            for (String chap : standaloneOrder) standaloneList.add(StringTag.valueOf(chap));
            root.put("standaloneOrder", standaloneList);
            ListTag topLevelList = new ListTag();
            for (String token : topLevelOrder) topLevelList.add(StringTag.valueOf(token));
            root.put("topLevelOrder", topLevelList);
            ListTag folderList = new ListTag();
            for (Map.Entry<String, List<String>> e : itemOrder.entrySet()) {
                if (e.getValue().isEmpty() || !CATEGORIES.containsKey(e.getKey())) continue;
                CompoundTag entry = new CompoundTag();
                entry.putString("folder", e.getKey());
                ListTag items = new ListTag();
                for (String item : e.getValue()) items.add(StringTag.valueOf(item));
                entry.put("items", items);
                folderList.add(entry);
            }
            root.put("folderOrder", folderList);
            Files.createDirectories(uiStatePath.getParent());
            Files.writeString(uiStatePath, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[Phoenix Chronicles] Failed to save category_ui_state.snbt: " + e.getMessage());
        }
    }
}

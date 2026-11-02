package net.phoenixvine.chronicles.common.codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Rewrites a quest file's tag so it can be saved as a copy: new ids, and links re-pointed at other copies. */
public final class QuestCopyTransform {

    private static final String NS = "phoenix_chronicles";

    private QuestCopyTransform() {}

    /**
     * @param questMap    old quest path id (lower case) -> new quest path id, for every quest being copied
     * @param chapterMap  old chapter id (upper case) -> new chapter id
     * @param categoryMap old category id -> new category id
     */
    public static void apply(CompoundTag tag, String newId, String newChapter, Map<String, String> questMap,
                             Map<String, String> chapterMap, Map<String, String> categoryMap,
                             @Nullable String find, @Nullable String replace) {
        tag.putString("id", newId);
        tag.putString("chapter", newChapter);
        if (tag.contains("category")) tag.remove("category");

        if (tag.contains("parent")) {
            String mapped = questMap.get(tag.getString("parent").toLowerCase(Locale.ROOT));
            if (mapped != null) tag.putString("parent", mapped);
        }

        if (tag.contains("prerequisites")) {
            ListTag list = tag.getList("prerequisites", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag p = list.getCompound(i);
                String mapped = questMap.get(p.getString("id").toLowerCase(Locale.ROOT));
                if (mapped != null) p.putString("id", mapped);
            }
        }

        if (tag.contains("link_target")) {
            String target = tag.getString("link_target");
            int colon = target.indexOf(':');
            String ns = colon >= 0 ? target.substring(0, colon) : NS;
            String path = (colon >= 0 ? target.substring(colon + 1) : target).toLowerCase(Locale.ROOT);
            String mapped = questMap.get(path);
            if (mapped != null && ns.equals(NS)) tag.putString("link_target", NS + ":" + mapped);
        }

        remapStringList(tag, "chapter_prereqs", chapterMap, true);
        remapStringList(tag, "category_prereqs", categoryMap, false);

        regenerateTaskIds(tag.getList("tasks", Tag.TAG_COMPOUND));
        if (tag.contains("variants")) {
            ListTag variants = tag.getList("variants", Tag.TAG_COMPOUND);
            for (int i = 0; i < variants.size(); i++) {
                regenerateTaskIds(variants.getCompound(i).getList("tasks", Tag.TAG_COMPOUND));
            }
        }

        if (find != null && !find.isEmpty() && tag.contains("enable_if")) {
            tag.putString("enable_if", tag.getString("enable_if").replace(find, replace == null ? "" : replace));
        }
    }

    private static void remapStringList(CompoundTag tag, String key, Map<String, String> map, boolean upper) {
        if (!tag.contains(key)) return;
        ListTag in = tag.getList(key, Tag.TAG_STRING);
        ListTag out = new ListTag();
        for (int i = 0; i < in.size(); i++) {
            String v = in.getString(i);
            String mapped = map.get(upper ? v.toUpperCase(Locale.ROOT) : v);
            out.add(StringTag.valueOf(mapped != null ? mapped : v));
        }
        tag.put(key, out);
    }

    private static void regenerateTaskIds(ListTag tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            CompoundTag t = tasks.getCompound(i);
            if (!t.contains("task_id")) continue;
            String original = t.getString("task_id");
            String ns = NS;
            String path = original;
            int colon = original.indexOf(':');
            if (colon >= 0) {
                ns = original.substring(0, colon);
                path = original.substring(colon + 1);
            }
            t.putString("task_id", ns + ":" + path + "_copy_" +
                    UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }
    }
}

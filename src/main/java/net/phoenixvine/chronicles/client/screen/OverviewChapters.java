package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.client.util.HiddenParts;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.CategoryRegistry;
import net.phoenixvine.chronicles.common.registry.ChapterFlagRegistry;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class OverviewChapters {

    private final ChronicleOverviewScreen screen;

    OverviewChapters(ChronicleOverviewScreen screen) {
        this.screen = screen;
        this.chapterActions = new ChapterActions(screen, screen, screen.editorState);
    }

    boolean chapterHasQuests(String chapter) {
        return chapterQuestCount(chapter) > 0;
    }

    public int chapterQuestCount(String chapter) {
        int count = 0;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (chapter.equalsIgnoreCase(n.getChapter())) count++;
        }
        return count;
    }

    public List<ResourceLocation> questIdsInChapter(String chapter) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (chapter.equalsIgnoreCase(n.getChapter())) ids.add(n.getId());
        }
        return ids;
    }

    public List<ResourceLocation> questIdsInCategory(String categoryId) {
        List<ResourceLocation> ids = new ArrayList<>();
        CategoryDefinition cat = CategoryRegistry
                .get(categoryId);
        if (cat == null) return ids;
        for (String chapter : cat.chapters()) ids.addAll(questIdsInChapter(chapter));
        return ids;
    }

    private void forceCompleteChapterOnRightClick(String chapter) {
        chapterActions.forceCompleteChapterOnRightClick(chapter);
    }

    private void resetChapterOnRightClick(String chapter) {
        chapterActions.resetChapterOnRightClick(chapter);
    }

    private void forceCompleteCategoryOnRightClick(String categoryId) {
        chapterActions.forceCompleteCategoryOnRightClick(categoryId);
    }

    private void resetCategoryOnRightClick(String categoryId) {
        chapterActions.resetCategoryOnRightClick(categoryId);
    }

    void deleteChapter(String chapter) {
        chapterActions.deleteChapter(chapter);
    }

    private void deleteCategoryOnRightClick(String categoryId) {
        chapterActions.deleteCategoryOnRightClick(categoryId);
    }

    private void deleteChapterOnRightClick(String chapter) {
        chapterActions.deleteChapterOnRightClick(chapter);
    }

    public List<String> buildChapterList() {
        if (screen.chapterListCache != null &&
                System.currentTimeMillis() - chapterListCacheAtMs > ChronicleOverviewScreen.CHAPTER_LIST_CACHE_TTL_MS) {
            screen.chapterListCache = null;
        }
        if (screen.chapterListCache == null) {
            Set<String> chapters = new TreeSet<>();
            for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                String cat = n.getChapter();
                if (cat != null && !cat.isBlank()) chapters.add(cat.toUpperCase(Locale.ROOT));
            }
            try {
                Path f = screen.chaptersFile();
                if (Files.exists(f)) {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String cat = line.trim().toUpperCase(Locale.ROOT);
                        if (!cat.isEmpty()) chapters.add(cat);
                    }
                }
            } catch (IOException ignored) {}

            if (!screen.isDevMode || !QuestChroniclesSettings.get().isShowFlagDisabledChapters()) {
                MinecraftServer server = Minecraft.getInstance() != null ?
                        Minecraft.getInstance().getSingleplayerServer() : null;
                chapters.removeIf(c -> !ChapterFlagRegistry.isChapterEnabled(c));
                if (QuestChroniclesSettings.get().isCascadeHiddenQuests())
                    chapters.removeIf(c -> QuestTreeRegistry.isChapterGatedHidden(c, screen::getState));
            }

            screen.chapterListCache = new ArrayList<>(chapters);
            chapterListCacheAtMs = System.currentTimeMillis();
        }
        return new ArrayList<>(screen.chapterListCache);
    }

    public boolean catMatches(QuestNode n) {
        MinecraftServer server = Minecraft.getInstance() != null ? Minecraft.getInstance().getSingleplayerServer() :
                null;

        if (n.isFlagDisabled(server))
            return screen.isDevMode && QuestChroniclesSettings.get().isShowFlagDisabledQuests();

        if (!screen.selectedChapter.equals(n.getChapter())) return false;

        if (!screen.isDevMode) {
            QuestNode.Visibility vis = n.getEffectiveVisibility(server, Minecraft.getInstance().player);
            if (vis == QuestNode.Visibility.HIDDEN && screen.getState(n) == QuestState.LOCKED) return false;
            if (screen.isAncestorGatedHidden(n)) return false;
        }

        if (!screen.stateFilter.equals("ALL")) {
            QuestState st = screen.getState(n);
            boolean stateMatch = switch (screen.stateFilter) {
                case "AVAILABLE" -> st == QuestState.UNLOCKED;
                case "ACTIVE" -> st == QuestState.ACTIVE;
                case "COMPLETE" -> st == QuestState.COMPLETED;
                case "LOCKED" -> st == QuestState.LOCKED;
                default -> true;
            };
            return stateMatch;
        }
        return true;
    }

    String buildSearchHaystack(QuestNode n) {
        StringBuilder sb = new StringBuilder();
        MinecraftServer server = Minecraft.getInstance() != null ? Minecraft.getInstance().getSingleplayerServer() :
                null;

        sb.append(n.getEffectiveTitleRaw(server, Minecraft.getInstance().player).getString().toLowerCase()).append(' ');
        sb.append(n.getId().getPath().replace('_', ' ').toLowerCase()).append(' ');
        sb.append(n.getId().toString().toLowerCase()).append(' ');
        if (!n.getEffectiveDescriptionRaw(server, Minecraft.getInstance().player).getString().isEmpty())
            sb.append(n.getEffectiveDescriptionRaw(server, Minecraft.getInstance().player).getString().toLowerCase())
                    .append(' ');
        if (n.getSubtitle() != null && !n.getSubtitle().isEmpty()) sb.append(n.getSubtitle().toLowerCase()).append(' ');
        sb.append(n.getChapter().toLowerCase()).append(' ');

        for (QuestTask task : n.getEffectiveTasks(server, Minecraft.getInstance().player)) {
            sb.append(task.getDescription().getString().toLowerCase()).append(' ');

            ResourceLocation displayId = HiddenParts.taskTargetHidden(n, task) ? null : task.getDisplayItemId();
            if (displayId != null) {
                Item item = BuiltInRegistries.ITEM.get(displayId);
                if (item != null && item != Items.AIR) {

                    sb.append(item.getDescription().getString().toLowerCase()).append(' ');

                    sb.append(displayId.getPath().replace('_', ' ').toLowerCase()).append(' ');
                    sb.append(displayId.toString().toLowerCase()).append(' ');

                    try {
                        Registry<Item> itemReg = BuiltInRegistries.ITEM;
                        var holder = itemReg.getHolder(itemReg.getId(item));
                        if (holder.isPresent()) {
                            for (var tag : holder.get().tags().toList()) {
                                sb.append(tag.location().getPath().replace('/', ' ').replace('_', ' ').toLowerCase())
                                        .append(' ');
                                sb.append(tag.location().toString().toLowerCase()).append(' ');
                            }
                        }
                    } catch (Exception ignored) {}

                    try {
                        ItemStack stack = new ItemStack(item);
                        LocalPlayer localPlayer = Minecraft
                                .getInstance().player;
                        var tooltipLines = stack.getTooltipLines(
                                net.minecraft.world.item.Item.TooltipContext.of(Minecraft.getInstance().level),
                                localPlayer, TooltipFlag.Default.NORMAL);
                        for (int ti = 1; ti < tooltipLines.size(); ti++) {

                            String txt = tooltipLines.get(ti).getString().trim().toLowerCase();
                            if (!txt.isEmpty()) sb.append(txt).append(' ');
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        for (QuestReward reward : n.getEffectiveRewards(server, Minecraft.getInstance().player)) {
            if (HiddenParts.rewardTargetHidden(n, reward)) continue;
            sb.append(reward.getSummary().getString()).append(' ');
            if (reward instanceof QuestReward.ItemReward ir) {
                ResourceLocation rid = BuiltInRegistries.ITEM.getKey(ir.getItem());
                if (rid != null) {
                    sb.append(rid.getPath().replace('_', ' ')).append(' ');
                    sb.append(rid).append(' ');
                }
            }
        }

        return sb.toString().toLowerCase();
    }

    public int unclaimedRewardCount() {
        if (screen.playerData == null) return 0;
        int count = 0;
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.isFlagDisabled(null)) continue;
            if (screen.playerData.getQuestState(node.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
            if (screen.playerData.hasClaimedRewards(node.getId())) continue;
            if (node.isRewardChoice()) continue;
            if (node.getEffectiveRewards(
                    Minecraft.getInstance() != null ? Minecraft.getInstance().getSingleplayerServer() : null,
                    Minecraft.getInstance() != null ? Minecraft.getInstance().player : null).isEmpty())
                continue;
            count++;
        }
        return count;
    }

    String chapterBreadcrumb(String cat) {
        List<String> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String cur = cat;
        while (cur != null && !cur.isEmpty() && visited.add(cur)) {
            String parent = ChapterConfig.get(cur).getParentChapter();
            if (parent.isEmpty()) break;
            chain.add(0, parent);
            cur = parent;
        }
        StringBuilder sb = new StringBuilder();
        for (String p : chain) sb.append(screen.friendly(p)).append(" §8›  §7");
        sb.append(screen.friendly(cat));
        return sb.toString();
    }

    int[] computeChapterProgress(String cat) {
        int done = 0, total = 0;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getChapter())) continue;
            if (n.isFlagDisabled(null)) continue;

            total++;

            if (screen.getDisplayState(n) == QuestState.COMPLETED) done++;
        }
        return new int[] { done, total };
    }

    boolean computeChapterHasAttention(String cat) {
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getChapter())) continue;
            if (n.isFlagDisabled(null)) continue;
            if (screen.getDisplayState(n) == QuestState.ACTIVE) return true;
        }
        return false;
    }

    boolean computeChapterHasUnclaimedRewards(String cat) {
        if (screen.playerData == null) return false;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!cat.equals("ALL") && !cat.equals(n.getChapter())) continue;
            if (n.isFlagDisabled(null)) continue;
            if (screen.playerData.getQuestState(n.getId(), QuestState.LOCKED) != QuestState.COMPLETED) continue;
            if (screen.playerData.hasClaimedRewards(n.getId())) continue;
            if (n.isRewardChoice()) continue;
            if (n.getEffectiveRewards(
                    Minecraft.getInstance() != null ? Minecraft.getInstance().getSingleplayerServer() : null,
                    Minecraft.getInstance() != null ? Minecraft.getInstance().player : null).isEmpty())
                continue;
            return true;
        }
        return false;
    }

    void computeUnlockPath(QuestNode target) {
        screen.unlockPathHighlight.clear();

        Queue<QuestNode> queue = new LinkedList<>();
        for (QuestNode prereq : target.getPrerequisites()) queue.add(prereq);
        Set<ResourceLocation> visited = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        PlayerQuestData data = mc.player == null ? null :
                QuestCapabilityProvider.of(mc.player)
                        .orElse(null);
        while (!queue.isEmpty()) {
            QuestNode n = queue.poll();
            if (!visited.add(n.getId())) continue;
            screen.unlockPathHighlight.add(n.getId());

            if (data != null) {
                QuestState st = data.getQuestState(n.getId(), QuestState.LOCKED);
                if (st == QuestState.COMPLETED || st == QuestState.ACTIVE) continue;
            }
            for (QuestNode req : n.getPrerequisites()) queue.add(req);
        }
    }

    final ChapterActions chapterActions;

    private long chapterListCacheAtMs = 0;
}

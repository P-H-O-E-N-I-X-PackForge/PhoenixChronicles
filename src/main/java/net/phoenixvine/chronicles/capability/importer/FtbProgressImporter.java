package net.phoenixvine.chronicles.capability.importer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class FtbProgressImporter {

    private static final float COORD_SCALE = 80f;
    private static final float COORD_PADDING = 2.0f;

    private FtbProgressImporter() {}

    public record ImportResult(int ported, int alreadyComplete, int notPortable, int claimedRewards,
                               List<String> warnings) {}

    private record PosKey(String chapter, int x, int y) {}

    private record OrigQuest(String hexId, String chapter, double x, double y, List<String> rewardHexes) {}

    public static ImportResult importProgress(Path ftbChaptersDir, Path ftbPlayerSaveFile, String playerUuidHex,
                                              PlayerQuestData data) {
        List<String> warnings = new ArrayList<>();

        if (!Files.isDirectory(ftbChaptersDir)) {
            warnings.add("FTB Quests chapters dir not found: " + ftbChaptersDir);
            return new ImportResult(0, 0, 0, 0, warnings);
        }
        if (!Files.exists(ftbPlayerSaveFile)) {
            warnings.add("FTB player save not found: " + ftbPlayerSaveFile);
            return new ImportResult(0, 0, 0, 0, warnings);
        }

        List<OrigQuest> origQuests = new ArrayList<>();
        Map<String, double[]> chapterBounds = new HashMap<>();

        List<Path> chapterFiles;
        try (var stream = Files.list(ftbChaptersDir)) {
            chapterFiles = stream
                    .filter(p -> !Files.isDirectory(p))
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".snbt"))
                    .toList();
        } catch (IOException e) {
            warnings.add("Failed to list FTB chapters dir: " + e.getMessage());
            return new ImportResult(0, 0, 0, 0, warnings);
        }

        for (Path file : chapterFiles) {
            String chapterSlug = file.getFileName().toString();
            chapterSlug = chapterSlug.substring(0, chapterSlug.length() - 5).toUpperCase(Locale.ROOT);

            CompoundTag chapter;
            try {
                chapter = LenientSnbtParser.parse(Files.readString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                warnings.add("Failed to parse chapter " + file.getFileName() + ": " + e.getMessage());
                continue;
            }

            ListTag quests = chapter.getList("quests", Tag.TAG_COMPOUND);
            ListTag links = chapter.getList("quest_links", Tag.TAG_COMPOUND);

            double minX = 0, minY = 0;
            boolean first = true;
            for (int i = 0; i < quests.size() + links.size(); i++) {
                CompoundTag q = i < quests.size() ? quests.getCompound(i) : links.getCompound(i - quests.size());
                double x = numeric(q.get("x"));
                double y = numeric(q.get("y"));
                if (first) {
                    minX = x;
                    minY = y;
                    first = false;
                } else {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                }
            }
            chapterBounds.put(chapterSlug, new double[] { minX, minY });

            for (int i = 0; i < quests.size(); i++) {
                CompoundTag q = quests.getCompound(i);
                String hexId = q.getString("id");
                if (hexId.isEmpty()) continue;
                double x = numeric(q.get("x"));
                double y = numeric(q.get("y"));
                List<String> rewardHexes = new ArrayList<>();
                for (Tag rt : q.getList("rewards", Tag.TAG_COMPOUND)) {
                    if (rt instanceof CompoundTag rc && rc.contains("id")) rewardHexes.add(rc.getString("id"));
                }
                origQuests.add(new OrigQuest(hexId, chapterSlug, x, y, rewardHexes));
            }
        }

        Map<PosKey, QuestNode> liveByPos = new HashMap<>();
        for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
            if (node.isLinkStub()) continue;
            PosKey key = new PosKey(node.getChapter().toUpperCase(Locale.ROOT), node.getCustomX(), node.getCustomY());
            liveByPos.putIfAbsent(key, node);
        }

        Map<String, QuestNode> questHexToNode = new HashMap<>();
        Map<String, String> rewardHexToQuestHex = new HashMap<>();
        int unmatched = 0;
        for (OrigQuest oq : origQuests) {
            double[] bounds = chapterBounds.get(oq.chapter());
            if (bounds == null) {
                unmatched++;
                continue;
            }
            int px = (int) Math.round((oq.x() - bounds[0] + COORD_PADDING) * COORD_SCALE);
            int py = (int) Math.round((oq.y() - bounds[1] + COORD_PADDING) * COORD_SCALE);
            QuestNode node = liveByPos.get(new PosKey(oq.chapter(), px, py));
            if (node == null) {
                unmatched++;
                continue;
            }
            questHexToNode.put(oq.hexId(), node);
            for (String rewardHex : oq.rewardHexes()) {
                rewardHexToQuestHex.put(rewardHex, oq.hexId());
            }
        }
        if (unmatched > 0) {
            warnings.add(unmatched + " FTB quest(s) had no matching Chronicles quest node (not imported, " +
                    "or the chapter/position didn't line up) - skipped.");
        }

        CompoundTag playerSave;
        try {
            playerSave = LenientSnbtParser.parse(Files.readString(ftbPlayerSaveFile, StandardCharsets.UTF_8));
        } catch (Exception e) {
            warnings.add("Failed to parse FTB player save: " + e.getMessage());
            return new ImportResult(0, 0, 0, 0, warnings);
        }

        CompoundTag completed = playerSave.getCompound("completed");
        CompoundTag claimedRewards = playerSave.getCompound("claimed_rewards");

        Set<String> claimedQuestHexes = new HashSet<>();
        for (String key : claimedRewards.getAllKeys()) {
            int colon = key.indexOf(':');
            if (colon < 0) continue;
            String prefix = key.substring(0, colon);
            String rewardHex = key.substring(colon + 1);
            if (!prefix.equalsIgnoreCase(playerUuidHex)) continue;
            String questHex = rewardHexToQuestHex.get(rewardHex);
            if (questHex != null) claimedQuestHexes.add(questHex);
        }

        int ported = 0;
        int alreadyComplete = 0;
        int notPortable = 0;
        int claimedCount = 0;

        for (String hexId : completed.getAllKeys()) {
            QuestNode node = questHexToNode.get(hexId);
            if (node == null) {
                notPortable++;
                continue;
            }
            ResourceLocation questId = node.getId();
            long timestamp = readLong(completed, hexId);

            if (data.getQuestState(questId, QuestState.LOCKED) == QuestState.COMPLETED) {
                alreadyComplete++;
            } else {
                data.setQuestState(questId, QuestState.COMPLETED);
                data.recordCompletion(questId, timestamp);
                ported++;
            }

            if (claimedQuestHexes.contains(hexId) && !data.hasClaimedRewards(questId)) {
                data.markRewardsClaimed(questId);
                claimedCount++;
            }
        }

        return new ImportResult(ported, alreadyComplete, notPortable, claimedCount, warnings);
    }

    private static long readLong(CompoundTag compound, String key) {
        Tag tag = compound.get(key);
        return tag instanceof NumericTag n ? n.getAsLong() : 0L;
    }

    private static double numeric(Tag tag) {
        return tag instanceof NumericTag n ? n.getAsDouble() : 0.0;
    }
}

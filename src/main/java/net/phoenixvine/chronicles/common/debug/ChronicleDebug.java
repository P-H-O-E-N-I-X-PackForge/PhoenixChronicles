package net.phoenixvine.chronicles.common.debug;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestTask;

import java.util.List;

/**
 * Quest debugging. {@code /chronicles debug <quest>} watches one quest: every time its tasks are evaluated (and
 * whenever its state changes) the result is written to the server log, with the reason an item task did or did not
 * match. {@code /chronicles diagnose <quest>} prints the same picture once, to chat and the log.
 */
public final class ChronicleDebug {

    private ChronicleDebug() {}

    private static volatile ResourceLocation watched;

    public static void watch(ResourceLocation questId) {
        watched = questId;
    }

    public static ResourceLocation watched() {
        return watched;
    }

    public static boolean isWatching(QuestNode node) {
        ResourceLocation w = watched;
        return w != null && node != null && w.equals(node.getId());
    }

    public static void log(String message, Object... args) {
        PhoenixChronicles.LOGGER.info("[Chronicles debug] " + message, args);
    }

    /** One line per task: how it evaluates for this player right now, and why. */
    public static List<String> describeTasks(Player player, QuestNode node) {
        List<String> out = new java.util.ArrayList<>();
        List<QuestTask> tasks = node.getEffectiveTasks(player.getServer(), player);
        for (int i = 0; i < tasks.size(); i++) {
            QuestTask task = tasks.get(i);
            String line;
            try {
                line = task.diagnose(player);
            } catch (Throwable t) {
                line = "diagnose failed: " + t;
            }
            out.add("task " + i + " [" + task.getClass().getSimpleName() + (task.isOptional() ? ", optional" : "") +
                    "] " + task.getTaskId() + ": " + line);
        }
        return out;
    }
}

package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.render.EmiReturnScreenFix;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.network.packet.C2SClaimQuestRewardPacket;
import net.phoenixvine.chronicles.network.packet.C2SResolveChoiceBoxPacket;

final class TaskActions {

    private final QuestTasksScreen screen;

    TaskActions(QuestTasksScreen screen) {
        this.screen = screen;
    }

    boolean clickLinkButton(int x, int y, int w, int h, double mx, double my) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        net.phoenixvine.chronicles.client.util.QuestChatLinks.copyToClipboard(screen.node);
        return true;
    }

    boolean clickVoiceButton(int x, int y, int w, int h, double mx, double my) {
        if (!screen.voiceShown() || mx < x || mx >= x + w || my < y || my >= y + h) return false;
        net.phoenixvine.chronicles.client.audio.ChroniclesAudio.toggleVoice(screen.node.getAudio().voiceId());
        return true;
    }

    long emergencyCooldownRemainingMs() {
        if (screen.playerData == null || !screen.playerData.hasUsedEmergency(screen.node.getId())) return 0;
        if (!net.phoenixvine.chronicles.client.util.ClientEmergencyState.isRepeatable(screen.node.getId())) return 0;
        long ends = screen.playerData.getEmergencyUsedAt(screen.node.getId()) +
                net.phoenixvine.chronicles.client.util.ClientEmergencyState.getCooldownSeconds(screen.node.getId()) *
                        1000L;
        return Math.max(0, ends - net.phoenixvine.chronicles.client.util.ClientEmergencyState.serverNow());
    }

    boolean clickEmergencyButton(int x, int y, int w, int h, double mx, double my) {
        if (!screen.emergencyShown() || mx < x || mx >= x + w || my < y || my >= y + h) return false;
        if (screen.emergencyUsedUp() || emergencyCooldownRemainingMs() > 0) return true;
        long now = System.currentTimeMillis();
        boolean repeatable = net.phoenixvine.chronicles.client.util.ClientEmergencyState
                .isRepeatable(screen.node.getId());
        if (!repeatable && now >= screen.fullscreen.emergencyArmedUntilMs) {
            screen.fullscreen.emergencyArmedUntilMs = now + 3000;
            return true;
        }
        screen.fullscreen.emergencyArmedUntilMs = 0;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SRequestEmergencyItemsPacket(screen.node.getId()));
        return true;
    }

    void openLink(String url) {
        if (url == null || url.isEmpty()) return;
        if (url.regionMatches(true, 0, "quest:", 0, 6)) {
            openQuestLink(url.substring(6));
            return;
        }
        if (url.startsWith("wiki:")) {
            String spec = url.substring(5);
            String pageId = null;
            int hash = spec.indexOf('#');
            if (hash >= 0) {
                pageId = spec.substring(hash + 1);
                spec = spec.substring(0, hash);
            }
            int slash = spec.indexOf('/');
            String namespace = slash >= 0 ? spec.substring(0, slash) : spec;
            String basePath = slash >= 0 ? spec.substring(slash + 1) : "";
            if (Minecraft.getInstance() != null) {
                Minecraft.getInstance().setScreen(new net.phoenixvine.wiki.client.screen.WikiScreen(screen, namespace,
                        basePath, pageId));
            }
            return;
        }
        try {
            net.minecraft.Util.getPlatform().openUri(java.net.URI.create(url));
        } catch (Exception e) {
            net.phoenixvine.chronicles.PhoenixChronicles.LOGGER.warn("Chronicles: failed to open link '{}'", url, e);
        }
    }

    private void openQuestLink(String spec) {
        String raw = spec == null ? "" : spec.trim();
        if (raw.isEmpty() || Minecraft.getInstance() == null) return;

        ResourceLocation id = raw.indexOf(':') >= 0 ? ResourceLocation.tryParse(raw) :
                ResourceLocation.tryParse(net.phoenixvine.chronicles.PhoenixChronicles.MOD_ID + ":" + raw);
        QuestNode target = id != null ? QuestTreeRegistry.getQuest(id) : null;

        if (!(screen.parent instanceof ChronicleOverviewScreen overview)) {
            net.phoenixvine.chronicles.PhoenixChronicles.LOGGER
                    .warn("Chronicles: quest link '{}' ignored, no overview screen to open it from", raw);
            return;
        }
        if (target == null) {
            overview.setFeedback("§cBroken quest link: %s", raw);
            return;
        }
        if (target == screen.node) return;

        overview.onNodeClicked(target);
    }

    boolean tryOpenArchiveEntry(QuestTask task) {
        if (!(task instanceof ArchiveEntryTask aet)) return false;
        if (!net.phoenixvine.chronicles.integration.archive.ArchiveLoreCompat.hasEntry(aet.getArchiveEntryId())) {
            return false;
        }

        net.phoenixvine.chronicles.integration.archive.ArchiveLoreCompat.openEntry(screen, aet.getArchiveEntryId());

        Player player = Minecraft.getInstance().player;
        if (player != null && !aet.isCompletedFor(player)) {
            aet.markCompletedClient(player);
            QuestNode owner = QuestTreeRegistry.getTaskOwner(aet.getTaskId());
            if (owner != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new net.phoenixvine.chronicles.network.packet.C2SPhantasiaTaskCompletePacket(owner.getId(),
                                aet.getTaskId()));
            }
        }
        return true;
    }

    void tryOpenInRecipeViewer(ItemStack stack) {
        if (stack.isEmpty() || Minecraft.getInstance() == null) return;
        try {
            Class<?> api = Class.forName("dev.emi.emi.api.EmiApi");
            Class<?> esClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            Class<?> ingredientClass = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
            Object es = esClass.getMethod("of", ItemStack.class).invoke(null, stack);

            api.getMethod("displayRecipes", ingredientClass).invoke(null, es);

            try {
                if (!QuestChroniclesSettings.get()
                        .isReturnToQuestbookFromRecipeViewer())
                    return;
                Class<?> recipeScreenClass = Class.forName("dev.emi.emi.screen.RecipeScreen");
                Object current = Minecraft.getInstance().screen;
                if (recipeScreenClass.isInstance(current)) {
                    Object ephemeral = recipeScreenClass.getField("old").get(current);
                    if (ephemeral instanceof net.minecraft.client.gui.screens.Screen ephemeralScreen &&
                            ephemeralScreen != screen) {
                        EmiReturnScreenFix.armReturnTo(ephemeralScreen, screen);
                    }
                }
            } catch (Exception ignored) {}
            return;
        } catch (Exception ignored) {}

        try {
            Class<?> jeiApi = Class.forName("mezz.jei.api.runtime.IJeiRuntime");

        } catch (Exception ignored) {}

        screen.isFullscreen = true;
    }

    boolean tryClaimReward(int rewardIndex) {
        QuestState state = screen.playerData != null ?
                screen.playerData.getQuestState(screen.node.getId(), QuestState.LOCKED) :
                QuestState.LOCKED;
        if (state != QuestState.COMPLETED || screen.content.effectiveRewards().isEmpty()) return false;

        QuestReward.ChoiceBoxReward box = screen.fullscreen.boxAt(rewardIndex);
        if (box != null && box.getMode() != QuestReward.ChoiceBoxReward.Mode.ALL) {
            if (screen.playerData != null && screen.playerData.isChoiceBoxResolved(screen.node.getId(), rewardIndex))
                return false;
            if (box.getMode() == QuestReward.ChoiceBoxReward.Mode.LOOTBOX) {
                net.neoforged.neoforge.network.PacketDistributor
                        .sendToServer(new C2SResolveChoiceBoxPacket(screen.node.getId(), rewardIndex, -1));
            } else {
                screen.popups.openChoiceBox(box, rewardIndex);
            }
            return true;
        }

        if (screen.rewardsClaimed()) return false;
        if (screen.node.isRewardChoice()) {
            if (rewardIndex < 0) return false;
            if (screen.playerData != null &&
                    screen.playerData.getChosenRewardIndices(screen.node.getId()).contains(rewardIndex))
                return false;
            net.neoforged.neoforge.network.PacketDistributor
                    .sendToServer(new C2SClaimQuestRewardPacket(screen.node.getId(), rewardIndex));
        } else {
            net.neoforged.neoforge.network.PacketDistributor
                    .sendToServer(new C2SClaimQuestRewardPacket(screen.node.getId(), -1));
        }
        return true;
    }

    void forceCompleteTask(QuestTask task) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null || task == null) return;
        Minecraft.getInstance().player.connection.sendCommand("chronicles forcetask " + task.getTaskId().getPath());
    }

    void resetTaskProgress(QuestTask task) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null || task == null) return;
        Minecraft.getInstance().player.connection.sendCommand("chronicles resettask " + task.getTaskId().getPath());
    }

    boolean trySubmitItemTask(QuestTask task) {
        if (!(task instanceof ItemRequirementTask it) || !it.shouldConsume() || Minecraft.getInstance() == null ||
                Minecraft.getInstance().player == null) {
            return false;
        }
        if (it.isCompletedFor(Minecraft.getInstance().player)) return false;

        QuestState state = screen.playerData != null ?
                screen.playerData.getQuestState(screen.node.getId(), QuestState.LOCKED) :
                QuestState.LOCKED;
        if (state != QuestState.UNLOCKED && state != QuestState.ACTIVE) return false;

        if (!it.hasRequiredItems(Minecraft.getInstance().player) && !it.usesAe2Storage()) return false;

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SSubmitItemTaskPacket(task.getTaskId()));
        return true;
    }

    boolean tryCompleteCheckmark(QuestTask task) {
        if (!(task instanceof CheckmarkTask)) return false;
        if (task.isCompletedFor(Minecraft.getInstance().player)) return true;

        QuestState state = screen.playerData != null ?
                screen.playerData.getQuestState(screen.node.getId(), QuestState.LOCKED) :
                QuestState.LOCKED;
        if (state != QuestState.UNLOCKED && state != QuestState.ACTIVE) return true;

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new net.phoenixvine.chronicles.network.packet.C2SCompleteCheckmarkTaskPacket(task.getTaskId()));
        return true;
    }
}

package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.common.util.ItemData;

import java.util.ArrayList;
import java.util.List;

final class TaskPresentation {

    private final QuestTasksScreen screen;

    TaskPresentation(QuestTasksScreen screen) {
        this.screen = screen;
    }

    ItemStack rewardStack(QuestReward.ItemReward reward) {
        ItemStack stack = new ItemStack(reward.getItem(), reward.getCount());
        ItemData.applyReward(stack, reward.getNbt());
        return stack;
    }

    private boolean taskTargetMasked(QuestTask task) {
        return task.hidesTarget() && !screen.isTaskDone(task);
    }

    private boolean taskAmountMasked(QuestTask task) {
        return task.hidesAmount() && !screen.isTaskDone(task);
    }

    boolean rewardTargetMasked(QuestReward reward) {
        return reward.hidesTarget() && !screen.rewardsRevealed();
    }

    boolean rewardAmountMasked(QuestReward reward) {
        return reward.hidesAmount() && !screen.rewardsRevealed();
    }

    ItemStack shownRewardStack(QuestReward.ItemReward reward) {
        if (rewardTargetMasked(reward)) return ItemStack.EMPTY;
        ItemStack stack = rewardStack(reward);
        if (rewardAmountMasked(reward)) stack.setCount(1);
        return stack;
    }

    ItemStack getTaskIcon(QuestTask task) {
        ResourceLocation id = task.getDisplayItemId();
        if (id == null || taskTargetMasked(task)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, taskAmountMasked(task) ? 1 : taskRequiredCount(task));

        if (task instanceof ItemRequirementTask t && t.getNbtFilter() != null &&
                !t.getNbtFilter().isEmpty()) {
            ItemData.setTag(stack, t.getNbtFilter().copy());
        }
        return stack;
    }

    private int taskRequiredCount(QuestTask task) {
        if (task instanceof ItemRequirementTask t) return Math.max(1, t.getRequiredCount());
        if (task instanceof CraftItemTask t) return Math.max(1, t.getRequiredCount());
        if (task instanceof TagItemTask t) return Math.max(1, t.getRequired());
        if (task instanceof FilterItemTask t) return Math.max(1, t.getCount());
        return 1;
    }

    String getTaskGlyph(QuestTask task) {
        if (task instanceof ItemRequirementTask) return "§6■";
        if (task instanceof CraftItemTask) return "§e⚒";
        if (task instanceof KillEntityTask) return "§c⚔";
        if (task instanceof FluidRequirementTask) return "§3≋";
        if (task instanceof ExperienceTask) return "§a✦";
        if (task instanceof StatTrackerTask) return "§9≡";
        if (task instanceof AdvancementTask) return "§d★";
        if (task instanceof CheckmarkTask) return "§7☑";
        if (task instanceof InfoTask) return "§7✎";
        if (task instanceof TimerTask) return "§b⏱";
        if (task instanceof TagItemTask) return "§e◈";
        if (task instanceof EnergyStorageTask) return "§6⚡";
        if (task instanceof FilterItemTask) return "§e◈";
        if (task instanceof FilterFluidTask) return "§3◈";
        if (task instanceof MultiblockFormedTask) return "§6⬡";
        if (task instanceof ViewMachineTask) return "§b⬡";
        if (task instanceof ViewSceneTask) return "§b⬢";
        if (task instanceof ViewGuideTask) return "§b📖";
        if (task instanceof ArchiveEntryTask) return "§b📖";
        return "§8◇";
    }

    String getTaskDetail(QuestTask task) {
        String raw = rawTaskDetail(task);
        if (raw == null) return null;
        if (taskTargetMasked(task)) {
            return taskAmountMasked(task) || taskRequiredCount(task) <= 1 ? "???" :
                    "???  ×" + taskRequiredCount(task);
        }
        return taskAmountMasked(task) ? raw.replaceAll("×\\s?\\d+", "×?") : raw;
    }

    private String rawTaskDetail(QuestTask task) {
        if (task instanceof ItemRequirementTask t) {

            String name = "item";
            if (t.getItem() != null) {
                ItemStack display = t.getItem().getDefaultInstance();
                if (t.getNbtFilter() != null && !t.getNbtFilter().isEmpty())
                    ItemData.setTag(display, t.getNbtFilter().copy());
                name = display.getHoverName().getString();
            }
            String suffix = "";
            if (t.shouldConsume()) {
                boolean done = Minecraft.getInstance() != null && Minecraft.getInstance().player != null &&
                        t.isCompletedFor(Minecraft.getInstance().player);
                boolean ready = !done && Minecraft.getInstance() != null && Minecraft.getInstance().player != null &&
                        t.hasRequiredItems(Minecraft.getInstance().player);
                suffix = done ? "  (handed in)" : ready ? "  §a▶ click to hand in" : "  (consumed)";
            }
            return name + (t.getRequiredCount() > 1 ? "  ×" + t.getRequiredCount() : "") + suffix;
        }
        if (task instanceof CraftItemTask t) {
            Item item = t.getItemId() != null ? BuiltInRegistries.ITEM.get(t.getItemId()) : null;
            String name = item != null ? item.getDefaultInstance().getHoverName().getString() :
                    (t.getItemId() != null ? t.getItemId().getPath() : "item");
            return "Craft: " + name + (t.getRequiredCount() > 1 ? " ×" + t.getRequiredCount() : "");
        }
        if (task instanceof KillEntityTask t) {
            var m = t.getMatcher();
            String entity = m.isEmpty() ? "entity" :
                    (m.ids().size() == 1 && m.tags().isEmpty()) ? prettifyId(m.firstId()) : m.displayName();
            return "Kill: " + entity + " ×" + t.getRequiredCount();
        }
        if (task instanceof FluidRequirementTask t) {
            String fluid = t.getFluidId() != null ? prettifyId(t.getFluidId()) : "fluid";
            return fluid + ": " + t.getRequiredAmount() + " mB";
        }
        if (task instanceof ExperienceTask t) {
            return "Reach Level " + t.getRequiredLevel();
        }
        if (task instanceof StatTrackerTask t) {
            String stat = t.getStatId() != null ? prettifyId(t.getStatId()) : "stat";
            return stat + " → " + t.getTargetValue();
        }
        if (task instanceof AdvancementTask t) {
            String adv = t.getAdvancementId() != null ?
                    t.getAdvancementId().getPath().replace('/', ' ').replace('_', ' ') : "advancement";
            return "Unlock: " + adv;
        }
        if (task instanceof InfoTask) {
            return null;
        }
        if (task instanceof TimerTask t) {
            return "Wait " + t.getDurationSeconds() + "s";
        }
        if (task instanceof TagItemTask t) {
            String tag = t.getTag() != null ? "#" + t.getTag().location().getPath() : "#unknown";
            return tag + " ×" + t.getRequired();
        }
        if (task instanceof FilterItemTask t) {
            return t.getFilter().describe() + " ×" + t.getCount() + (t.isConsume() ? "  (consumed)" : "");
        }
        if (task instanceof FilterFluidTask t) {
            return t.getFilter().describe() + ": " + String.format("%,d", t.getAmount()) + " mB" +
                    (t.isConsume() ? "  (consumed)" : "");
        }
        if (task instanceof MultiblockFormedTask t) {
            return "Form: " + (t.getMachineId() != null ? t.getMachineId() : "any multiblock") + " ×" + t.getRequired();
        }
        if (task instanceof ViewMachineTask t) {
            return "Phantasia: " + t.getMachineId();
        }
        if (task instanceof ViewSceneTask t) {
            return "Phantasia scene: " + t.getSceneId();
        }
        if (task instanceof ViewGuideTask t) {
            return "Phantasia guide: " + t.getGuideId();
        }
        if (task instanceof ArchiveEntryTask t) {
            return "Archive entry: " + t.getArchiveEntryId();
        }
        return null;
    }

    private static String prettifyId(ResourceLocation id) {
        return id.getPath().replace('_', ' ');
    }

    java.util.List<Component> buildTaskTooltip(QuestTask task) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        boolean done = screen.isTaskDone(task);
        String status = done ? "§a✔ Complete" : (task.isOptional() ? "§8Optional" : "§c✗ Incomplete");
        lines.add(ChroniclesUIKit.lit(status + "  §7" + task.getDescription().getString()));
        String detail = getTaskDetail(task);
        if (detail != null) lines.add(ChroniclesUIKit.lit("§8" + detail));
        String prog = taskProgressString(task);
        if (prog != null && !done) lines.add(Component.translatable("phoenix_chronicles.ui.progress_label", prog));
        ItemStack icon = getTaskIcon(task);
        if (!icon.isEmpty()) lines.add(Component.translatable("phoenix_chronicles.ui.recipe_browser_hint"));
        if (screen.isEditMode && (task.hidesAmount() || task.hidesTarget())) {
            String real = rawTaskDetail(task);
            lines.add(ChroniclesUIKit.lit("§d✎ Editor only - hidden from players: §7" + (real != null ? real : "")));
        }
        return lines;
    }

    java.util.List<Component> buildRewardTooltip(QuestReward reward) {
        java.util.List<Component> lines = buildPlayerRewardTooltip(reward);
        if (screen.isEditMode && (reward.hidesAmount() || reward.hidesTarget())) {
            lines.add(
                    ChroniclesUIKit.lit("§d✎ Editor only - hidden from players: §7" + reward.getSummary().getString()));
        }
        return lines;
    }

    private java.util.List<Component> buildPlayerRewardTooltip(QuestReward reward) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        if (reward instanceof QuestReward.ItemReward ir && (rewardTargetMasked(ir) || rewardAmountMasked(ir))) {
            Component name = rewardTargetMasked(ir) ? ChroniclesUIKit.lit("???") : rewardStack(ir).getHoverName();
            lines.add(Component.translatable("phoenix_chronicles.ui.reward_line", name,
                    rewardAmountMasked(ir) ? "?" : String.valueOf(ir.getCount())));
            if (!rewardTargetMasked(ir)) {
                java.util.List<Component> vanillaLines = rewardStack(ir).getTooltipLines(
                        net.minecraft.world.item.Item.TooltipContext
                                .of(Minecraft.getInstance() != null ? Minecraft.getInstance().level : null),
                        Minecraft.getInstance() != null ? Minecraft.getInstance().player : null,
                        net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                for (int i = 1; i < vanillaLines.size(); i++) lines.add(vanillaLines.get(i));
            }
        } else if (rewardTargetMasked(reward)) {
            lines.add(ChroniclesUIKit.lit("§7Hidden reward"));
        } else if (reward instanceof QuestReward.ItemReward ir) {
            ItemStack stack = rewardStack(ir);
            lines.add(Component.translatable("phoenix_chronicles.ui.reward_line", stack.getHoverName(),
                    ir.getCount()));

            java.util.List<Component> vanillaLines = stack.getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext
                            .of(Minecraft.getInstance() != null ? Minecraft.getInstance().level : null),
                    Minecraft.getInstance() != null ? Minecraft.getInstance().player : null,
                    net.minecraft.world.item.TooltipFlag.Default.NORMAL);
            for (int i = 1; i < vanillaLines.size(); i++) lines.add(vanillaLines.get(i));
            lines.add(Component.translatable("phoenix_chronicles.ui.recipe_browser_hint"));
        } else if (reward instanceof QuestReward.ChoiceBoxReward box) {
            boolean resolved = screen.playerData != null &&
                    screen.playerData.isChoiceBoxResolved(screen.node.getId(),
                            screen.content.effectiveRewards().indexOf(box));
            lines.add(reward.getSummary());
            if (box.getMode() == QuestReward.ChoiceBoxReward.Mode.ALL) {
                for (QuestReward opt : box.getOptions()) lines.add(Component.literal("§7• ").append(opt.getSummary()));
            } else {
                lines.add(Component.translatable(resolved ? "phoenix_chronicles.ui.choice_box_opened" :
                        box.getMode() == QuestReward.ChoiceBoxReward.Mode.LOOTBOX ?
                                "phoenix_chronicles.ui.choice_box_random_pick" :
                                "phoenix_chronicles.ui.choice_box_choose"));
            }
        } else {
            lines.add(Component.translatable("phoenix_chronicles.ui.generic_reward_line", reward.getType().name()));
        }
        return lines;
    }

    float parseProgress(String prog) {
        if (prog == null) return 0f;

        String cleaned = prog.replaceAll(",", "").replaceAll("(?<=\\d)\\s+[a-zA-Z]+", "");
        int slash = cleaned.indexOf('/');
        if (slash < 0) return 0f;
        try {
            float cur = Float.parseFloat(cleaned.substring(0, slash).trim());
            float tot = Float.parseFloat(cleaned.substring(slash + 1).trim());
            return tot > 0 ? Math.min(1f, cur / tot) : 0f;
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    String taskProgressString(QuestTask task) {
        String raw = rawTaskProgressString(task);
        return raw != null && taskAmountMasked(task) ? "?" : raw;
    }

    private String rawTaskProgressString(QuestTask task) {
        if (screen.player == null) return null;
        if (screen.playerData != null &&
                screen.playerData.getQuestState(screen.node.getId(), QuestState.LOCKED) == QuestState.COMPLETED) {

            String live = task.getProgressString(screen.player);
            if (live != null) {
                int slash = live.indexOf('/');
                if (slash >= 0) {
                    String denom = live.substring(slash + 1).trim();
                    return denom + "/" + denom;
                }
            }
            return null;
        }
        return task.getProgressString(screen.player);
    }

    QuestReward displayRewardFor(int rewardIndex, QuestReward reward) {
        if (reward instanceof QuestReward.ChoiceBoxReward box && screen.playerData != null) {
            int chosen = screen.playerData.getResolvedChoiceBoxOption(screen.node.getId(), rewardIndex);
            if (chosen >= 0 && chosen < box.getOptions().size()) return box.getOptions().get(chosen);
        }
        return reward;
    }

    String rewardGlyph(QuestReward reward) {
        if (reward instanceof QuestReward.ChoiceBoxReward box) {
            return box.getMode() == QuestReward.ChoiceBoxReward.Mode.LOOTBOX ? "🎲" : "🎁";
        }
        return switch (reward.getType()) {
            case XP -> "⚡";
            case COMMAND -> "◆";
            case LOOT_TABLE -> "📦";
            case SCRIPT_EVENT -> "✦";
            case REWARD_TABLE -> "⊞";
            case FLUID -> "💧";
            case OPEN_SCREEN -> "🖥";
            case QUEST_ACTION -> "✔";
            default -> "?";
        };
    }
}

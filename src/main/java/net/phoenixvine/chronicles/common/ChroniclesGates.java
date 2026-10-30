package net.phoenixvine.chronicles.common;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.wiki.api.gate.SuiteGates;

/**
 * What a player may do with quests, as features of the shared gate API ({@link SuiteGates}): another mod binds them to
 * research or anything else, e.g. {@code SuiteGates.bind(ChroniclesGates.CLAIM_REWARDS, "*", "conflux:node/foo")}. A
 * single quest is the feature {@code chronicles:quest/<quest id>}, which gates claiming and choosing its rewards and
 * asking for its emergency items. A feature that nothing is bound to is allowed.
 */
public final class ChroniclesGates {

    /** Claiming quest rewards and resolving choice boxes. */
    public static final String CLAIM_REWARDS = "chronicles:claim_rewards";
    /** Handing in items and ticking off checkmark tasks. */
    public static final String SUBMIT_TASKS = "chronicles:submit_tasks";
    /** Asking for a quest's emergency items. */
    public static final String EMERGENCY_ITEMS = "chronicles:emergency_items";
    /** Turning auto-claim on. */
    public static final String AUTO_CLAIM = "chronicles:auto_claim";

    private ChroniclesGates() {}

    public static String quest(ResourceLocation questId) {
        return "chronicles:quest/" + questId;
    }

    public static boolean allowed(ServerPlayer player, String feature) {
        return SuiteGates.allowed(player, feature, SuiteGates.ANY_SCOPE);
    }

    /** Whether the player may use the feature (and the quest, if there is one); tells them when not. */
    public static boolean check(ServerPlayer player, String feature, ResourceLocation questId) {
        if (allowed(player, feature) && (questId == null || allowed(player, quest(questId)))) return true;
        player.displayClientMessage(Component.literal("Your team has not researched this yet."), true);
        return false;
    }
}

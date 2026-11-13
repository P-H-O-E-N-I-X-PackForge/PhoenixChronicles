package net.phoenixvine.chronicles.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.phoenixvine.wiki.api.gate.SuiteGates;

public final class ChroniclesGates {

    public static final String CLAIM_REWARDS = "chronicles:claim_rewards";
    
    public static final String SUBMIT_TASKS = "chronicles:submit_tasks";
    
    public static final String EMERGENCY_ITEMS = "chronicles:emergency_items";
    
    public static final String AUTO_CLAIM = "chronicles:auto_claim";

    private ChroniclesGates() {}

    public static String quest(ResourceLocation questId) {
        return "chronicles:quest/" + questId;
    }

    public static boolean allowed(ServerPlayer player, String feature) {
        return SuiteGates.allowed(player, feature, SuiteGates.ANY_SCOPE);
    }

    public static boolean check(ServerPlayer player, String feature, ResourceLocation questId) {
        if (allowed(player, feature) && (questId == null || allowed(player, quest(questId)))) return true;
        player.displayClientMessage(SuiteGates.denial(
                !allowed(player, feature) || questId == null ? feature : quest(questId), SuiteGates.ANY_SCOPE), false);
        return false;
    }
}

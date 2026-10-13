package net.phoenixvine.chronicles.integration.jei;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.event.QuestEvent;

@EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, value = Dist.CLIENT)
public class JeiDynamicReloadHook {

    @SubscribeEvent
    public static void onQuestTreeReloaded(QuestEvent.TreeReloaded event) {
        if (!net.neoforged.fml.ModList.get().isLoaded("jei")) return;
        ChroniclesJeiPlugin.refreshQuestRecipes();
    }
}

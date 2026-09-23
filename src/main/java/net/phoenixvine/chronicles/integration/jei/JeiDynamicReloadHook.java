package net.phoenixvine.chronicles.integration.jei;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.common.event.QuestEvent;

@Mod.EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class JeiDynamicReloadHook {

    @SubscribeEvent
    public static void onQuestTreeReloaded(QuestEvent.TreeReloaded event) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("jei")) return;
        ChroniclesJeiPlugin.refreshQuestRecipes();
    }
}

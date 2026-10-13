package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.phoenixvine.chronicles.PhoenixChronicles;

@EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, value = Dist.CLIENT)
public final class EmiReturnScreenFix {

    private EmiReturnScreenFix() {}

    private static Screen armedEphemeralScreen = null;
    private static Screen armedReturnToScreen = null;

    public static void armReturnTo(Screen ephemeralScreen, Screen returnToScreen) {
        armedEphemeralScreen = ephemeralScreen;
        armedReturnToScreen = returnToScreen;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen ephemeral = armedEphemeralScreen;
        if (ephemeral == null) return;
        if (event.getNewScreen() == ephemeral) {
            Screen returnTo = armedReturnToScreen;
            armedEphemeralScreen = null;
            armedReturnToScreen = null;
            if (returnTo != null) event.setNewScreen(returnTo);
        }
    }
}

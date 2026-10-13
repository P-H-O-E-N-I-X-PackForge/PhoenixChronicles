package net.phoenixvine.chronicles.client.registry;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.client.screen.LangEditorScreen;

import java.nio.file.Path;

@EventBusSubscriber(modid = PhoenixChronicles.MOD_ID, value = Dist.CLIENT)
public class LangSyncScheduler {

    private static final long DEBOUNCE_MS = 4000;

    private static volatile boolean dirty = false;
    private static volatile long dueAtMs = 0;

    public static void markDirty() {
        dirty = true;
        dueAtMs = System.currentTimeMillis() + DEBOUNCE_MS;
    }

    public static void flushNow() {
        if (!dirty) return;
        dirty = false;
        Path base = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        LangEditorScreen.writeEnUsJson(base);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (dirty && System.currentTimeMillis() >= dueAtMs) {
            flushNow();
        }
    }
}

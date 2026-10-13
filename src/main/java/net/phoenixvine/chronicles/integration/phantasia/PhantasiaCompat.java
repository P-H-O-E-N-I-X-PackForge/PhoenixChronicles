package net.phoenixvine.chronicles.integration.phantasia;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.phoenixvine.chronicles.common.model.QuestNode;

public class PhantasiaCompat {

    public static final String PHANTASIA_MOD_ID = "phantasia";

    public static boolean isAvailable() {
        return false;
    }

    public static void init() {}

    public static java.util.Collection<String> allMachineIds() {
        return null;
    }

    public static java.util.Collection<String> allSceneIds() {
        return null;
    }

    public static java.util.Collection<String> allGuideIds() {
        return null;
    }

    public static boolean canOpenForTask(Object task) {
        return false;
    }

    public static void openForTask(Object task, Screen parent) {}

    public static Object createPreviewForNode(QuestNode node) {
        return null;
    }

    public static Object createPreview(String machineId) {
        return null;
    }

    public static Object createScenePreview(String sceneId) {
        return null;
    }

    public static void tickPreview(Object preview) {}

    public static boolean isPreviewLoadFailed(Object preview) {
        return false;
    }

    public static void renderPreview(Object preview, GuiGraphics g, int x, int y, int w, int h, float partialTick) {}

    public static boolean isPreviewReady(Object preview) {
        return false;
    }

    public static boolean previewMouseClicked(Object preview, double mx, double my, int x, int y, int w, int h,
                                              Screen parent) {
        return false;
    }

    public static void closePreview(Object preview) {}
}

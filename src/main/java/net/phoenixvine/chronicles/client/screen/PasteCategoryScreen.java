package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.screen.utils.CategoryContentCopier;
import net.phoenixvine.chronicles.common.registry.CategoryRegistry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Asks how to paste the copied category tree: an optional condition for the pasted root, an optional find/replace
 * applied to the conditions (enable_if) of the pasted subcategories and quests, so a copy can be pointed at a
 * different flag, and whether to copy the chapters and quests inside too.
 */
public class PasteCategoryScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 160;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int ACCENT = 0xFF4499CC;

    private final Screen parent;
    private final String destCategoryId;
    private final Consumer<CategoryContentCopier.Result> onDone;
    private boolean copyContent = true;
    private Button contentButton;

    private String rootCondition = "";
    private String find = "";
    private String replace = "";

    private EditBox rootBox, findBox, replaceBox;
    private int panelLeft, panelTop;

    /** @param destCategoryId category to paste inside, or null for the top level */
    public PasteCategoryScreen(Screen parent, @Nullable String destCategoryId,
                               Consumer<CategoryContentCopier.Result> onDone) {
        super(Component.literal("Paste Category"));
        this.parent = parent;
        this.destCategoryId = destCategoryId;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = (height - PANEL_H) / 2;
        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        rootBox = new EditBox(font, fx, panelTop + 34, fw, FIELD_H, Component.empty());
        rootBox.setMaxLength(200);
        rootBox.setHint(Component.literal("(keep the copied condition)"));
        rootBox.setValue(rootCondition);
        rootBox.setResponder(v -> rootCondition = v);
        addRenderableWidget(rootBox);

        int half = (fw - 6) / 2;
        findBox = new EditBox(font, fx, panelTop + 66, half, FIELD_H, Component.empty());
        findBox.setMaxLength(100);
        findBox.setHint(Component.literal("find"));
        findBox.setValue(find);
        findBox.setResponder(v -> find = v);
        addRenderableWidget(findBox);

        replaceBox = new EditBox(font, fx + half + 6, panelTop + 66, half, FIELD_H, Component.empty());
        replaceBox.setMaxLength(100);
        replaceBox.setHint(Component.literal("replace with"));
        replaceBox.setValue(replace);
        replaceBox.setResponder(v -> replace = v);
        addRenderableWidget(replaceBox);
        setInitialFocus(rootBox);

        boolean hasContent = !CategoryRegistry.clipboardChapters().isEmpty();
        contentButton = Button.builder(Component.literal(contentLabel()), b -> {
            copyContent = !copyContent;
            b.setMessage(Component.literal(contentLabel()));
        }).bounds(fx, panelTop + 84, fw, 16).build();
        contentButton.active = hasContent;
        addRenderableWidget(contentButton);

        int btnY = panelTop + PANEL_H - 10 - 18;
        addRenderableWidget(Button.builder(Component.literal("Paste"), b -> paste())
                .bounds(fx, btnY, half, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.cancel"), b -> close())
                .bounds(fx + half + 6, btnY, half, 18).build());
    }

    private String contentLabel() {
        int chapters = CategoryRegistry.clipboardChapters().size();
        if (chapters == 0) return "No chapters in this copy";
        return "Copy " + chapters + " chapter(s) and their quests: " + (copyContent ? "ON" : "OFF");
    }

    private void close() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private void paste() {
        CategoryContentCopier.Result result = CategoryContentCopier.paste(destCategoryId, rootCondition, find, replace,
                copyContent && !CategoryRegistry.clipboardChapters().isEmpty());
        onDone.accept(result);
        close();
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        g.flush();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);

        ChroniclesUIKit.drawModalChrome(g, font, width, height, panelLeft, panelTop, PANEL_W, PANEL_H, 22,
                "§bPaste \"" + CategoryRegistry.clipboardLabel() + "\" (" + CategoryRegistry.clipboardSize() + ")",
                ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER, ACCENT, ChroniclesThemePalette.TEXT);

        g.drawString(font, "§8Condition (enable_if) for the pasted root", panelLeft + MARGIN, panelTop + 24,
                ChroniclesThemePalette.TEXT_FAINT, false);
        g.drawString(font, "§8Replace text in the other pasted conditions", panelLeft + MARGIN, panelTop + 56,
                ChroniclesThemePalette.TEXT_FAINT, false);
        g.drawString(font, "§8Links to quests outside the copy keep pointing at the originals",
                panelLeft + MARGIN, panelTop + 106, ChroniclesThemePalette.TEXT_FAINT, false);

        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) {
            paste();
            return true;
        }
        if (key == 256) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && (mx < panelLeft || mx >= panelLeft + PANEL_W || my < panelTop || my >= panelTop + PANEL_H)) {
            close();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.client.render.ChroniclesThemePalette;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.render.background.BackgroundRenderUtil;
import net.phoenixvine.chronicles.client.render.shader.DynamicShaderManager;
import net.phoenixvine.chronicles.client.rich.CodeEditBox;
import net.phoenixvine.chronicles.client.util.CategoryShaderConfig;
import net.phoenixvine.chronicles.common.model.CategoryDefinition;
import net.phoenixvine.chronicles.common.registry.CategoryRegistry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryThemeScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int MARGIN = 12;
    private static final int FIELD_H = 13;
    private static final int STRIDE = FIELD_H + 8;

    private static final int SEC_HEADER_H = 15;
    private static final int PREVIEW_GAP = 12;
    private static final int PREVIEW_H = 24;

    private static final int ADV_TOGGLE_GAP = 6;
    private static final int ADV_ROW_GAP = 4;
    private static final int ADV_BLOCK_H = FIELD_H * 2 + 2 + ADV_ROW_GAP;
    private static final int ADV_BOTTOM_PAD = 6;
    private static final int ADV_VISIBLE_ROWS = 2;
    private static final int ADV_SCROLLBAR_W = 3;

    private static final int[] ROW_H_TABLE = { STRIDE + 10, STRIDE, STRIDE + 10, STRIDE + 10, STRIDE + 10 };
    private static final int ROW_NAME = 0, ROW_ICON = 1, ROW_COLOR = 2, ROW_NAME_COLOR = 3, ROW_SHADER = 4;
    private static final int BASE_ROWS_H;

    static {
        int h = 28;
        for (int rh : ROW_H_TABLE) h += rh;
        BASE_ROWS_H = h + PREVIEW_GAP + PREVIEW_H + ADV_TOGGLE_GAP + SEC_HEADER_H + 10 + 18 + 10;
    }

    private int panelH;

    private static final int ACCENT = 0xFF884499;

    private final Screen parent;
    private final String categoryId;

    private String cachedDisplayName;
    private String cachedIcon;
    private int cachedColor;
    private int cachedNameColor;
    private String cachedShaderId;

    private EditBox nameBox;
    private EditBox colorBox;
    private EditBox nameColorBox;
    private EditBox shaderBox;

    private boolean advancedOpen = false;
    private int advScrollRow = 0;

    private final List<CategoryShaderConfig.CategoryOverride> overrides = new ArrayList<>();
    private final List<EditBox> overrideConditionBoxes = new ArrayList<>();
    private final Map<Integer, EditBox> overrideShaderBoxes = new HashMap<>();

    private int panelLeft, panelTop;

    private float uiScale = 1f;
    private int vw, vh;

    public CategoryThemeScreen(Screen parent, String categoryId) {
        super(Component.translatable("phoenix_chronicles.screen.category_theme.category_theme"));
        this.parent = parent;
        this.categoryId = categoryId;

        CategoryDefinition cat = CategoryRegistry.get(categoryId);
        this.cachedDisplayName = cat != null ? cat.displayName() : categoryId;
        this.cachedIcon = cat != null ? cat.icon() : "";
        this.cachedColor = cat != null ? cat.color() : 0;
        this.cachedNameColor = cat != null ? cat.nameColor() : 0;
        this.cachedShaderId = CategoryShaderConfig.get(categoryId);
        for (CategoryShaderConfig.CategoryOverride ov : CategoryShaderConfig.getOverrides(categoryId)) {
            overrides.add(ov.copy());
        }
    }

    private int advancedContentH() {
        if (!advancedOpen) return 0;
        int visibleRows = Math.min(overrides.size(), ADV_VISIBLE_ROWS);
        return ADV_TOGGLE_GAP + visibleRows * ADV_BLOCK_H + FIELD_H + ADV_BOTTOM_PAD;
    }

    private int maxAdvScrollRow() {
        return Math.max(0, overrides.size() - ADV_VISIBLE_ROWS);
    }

    private int advViewportTop() {
        return advancedToggleY() + SEC_HEADER_H + ADV_TOGGLE_GAP;
    }

    private int advViewportH() {
        return Math.min(overrides.size(), ADV_VISIBLE_ROWS) * ADV_BLOCK_H;
    }

    private int previewY() {
        return rowTop(ROW_SHADER) + ROW_H_TABLE[ROW_SHADER] + PREVIEW_GAP;
    }

    private int advancedToggleY() {
        return previewY() + PREVIEW_H + ADV_TOGGLE_GAP;
    }

    private int rowTop(int index) {
        int y = panelTop + 28;
        for (int i = 0; i < index; i++) y += ROW_H_TABLE[i];
        return y;
    }

    @Override
    protected void init() {
        panelH = BASE_ROWS_H + advancedContentH();

        uiScale = Math.min(1f, Math.min((width - 8f) / PANEL_W, (height - 8f) / panelH));
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);

        panelLeft = Mth.clamp((vw - PANEL_W) / 2, 4, Math.max(4, vw - PANEL_W - 4));
        panelTop = Mth.clamp((vh - panelH) / 2, 4, Math.max(4, vh - panelH - 4));

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        overrideConditionBoxes.clear();
        overrideShaderBoxes.clear();

        nameBox = new CodeEditBox(font, fx, rowTop(ROW_NAME) + 11, fw, FIELD_H, Component.empty());
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("§8" + categoryId));
        nameBox.setValue(cachedDisplayName);
        nameBox.setResponder(v -> cachedDisplayName = v.replace('&', '§').trim());
        nameBox.setTooltip(Tooltip.create(Component.literal(
                "Leave empty to use the category's ID (\"" + categoryId + "\") as its name.\n" +
                        "Use & for color/formatting codes, e.g. &6&lGolden Category.")));
        addRenderableWidget(nameBox);

        int iconPreviewW = FIELD_H + 4;
        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.change_icon"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(new ItemPickerScreen(this, stack -> {
                        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                        cachedIcon = key != null ? key.toString() : "";
                    }));
                })
                .bounds(fx + iconPreviewW + 4, rowTop(ROW_ICON), fw - iconPreviewW - 4, FIELD_H).build());

        colorBox = new EditBox(font, fx, rowTop(ROW_COLOR) + 11, fw, FIELD_H, Component.empty());
        colorBox.setMaxLength(7);
        colorBox.setHint(Component.translatable("phoenix_chronicles.ui.rrggbb_empty_default"));
        colorBox.setValue(cachedColor != 0 ? ChroniclesUIKit.formatHexColor(cachedColor) : "");
        colorBox.setResponder(v -> cachedColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(colorBox);

        nameColorBox = new EditBox(font, fx, rowTop(ROW_NAME_COLOR) + 11, fw, FIELD_H, Component.empty());
        nameColorBox.setMaxLength(7);
        nameColorBox.setHint(
                Component.translatable("phoenix_chronicles.screen.category_theme.rrggbb_empty_use_accent_color"));
        nameColorBox.setValue(cachedNameColor != 0 ? ChroniclesUIKit.formatHexColor(cachedNameColor) : "");
        nameColorBox.setResponder(v -> cachedNameColor = ChroniclesUIKit.parseHexColor(v, 0));
        addRenderableWidget(nameColorBox);

        int browseW = 48;
        int browseGap = 4;
        int shaderRowY = rowTop(ROW_SHADER);
        shaderBox = new EditBox(font, fx, shaderRowY + 11, fw - browseW - browseGap, FIELD_H, Component.empty());
        shaderBox.setMaxLength(64);
        shaderBox.setHint(Component.translatable("phoenix_chronicles.ui.shader_id_empty_none"));
        shaderBox.setValue(cachedShaderId);
        shaderBox.setResponder(v -> cachedShaderId = v.trim());
        addRenderableWidget(shaderBox);
        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.browse"), b -> {
            if (minecraft != null)
                minecraft.setScreen(new ShaderPickerScreen(this, id -> {
                    cachedShaderId = id;
                    clearWidgets();
                    init();
                }));
        }).bounds(fx + fw - browseW, shaderRowY + 11, browseW, FIELD_H).build());

        int advY = advancedToggleY();

        if (advancedOpen) {
            int oy = advY + SEC_HEADER_H + ADV_TOGGLE_GAP;
            int removeW = 14;
            advScrollRow = Mth.clamp(advScrollRow, 0, maxAdvScrollRow());
            int visEnd = Math.min(overrides.size(), advScrollRow + ADV_VISIBLE_ROWS);
            for (int i = advScrollRow; i < visEnd; i++) {
                CategoryShaderConfig.CategoryOverride ov = overrides.get(i);
                int idx = i;

                EditBox condBox = new EditBox(font, fx, oy, fw - removeW - 4, FIELD_H, Component.empty());
                condBox.setMaxLength(128);
                condBox.setHint(Component.translatable("phoenix_chronicles.ui.condition_e_g_config_pack"));
                condBox.setValue(ov.condition);
                condBox.setResponder(v -> overrides.get(idx).condition = v.trim());
                addRenderableWidget(condBox);
                overrideConditionBoxes.add(condBox);

                addRenderableWidget(Button.builder(Component.literal("§c×"), b -> {
                    overrides.remove(idx);
                    clearWidgets();
                    init();
                }).bounds(fx + fw - removeW, oy, removeW, FIELD_H).build());

                oy += FIELD_H + 2;

                EditBox shBox = new EditBox(font, fx, oy, fw, FIELD_H, Component.empty());
                shBox.setMaxLength(64);
                shBox.setHint(Component.translatable("phoenix_chronicles.ui.shader_id_empty_none"));
                shBox.setValue(ov.shaderId);
                shBox.setResponder(v -> overrides.get(idx).shaderId = v.trim());
                addRenderableWidget(shBox);
                overrideShaderBoxes.put(idx, shBox);

                oy += FIELD_H + ADV_ROW_GAP;
            }

            addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.add_override"), b -> {
                overrides.add(new CategoryShaderConfig.CategoryOverride());
                advScrollRow = maxAdvScrollRow();
                clearWidgets();
                init();
            }).bounds(fx, oy, fw, FIELD_H).build());
        }

        int btnY = panelTop + panelH - 10 - 18;
        int half = (fw - 6) / 2;
        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.save"), b -> save())
                .bounds(fx, btnY, half, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("phoenix_chronicles.ui.cancel"),
                b -> {
                    if (minecraft != null) minecraft.setScreen(parent);
                })
                .bounds(fx + half + 6, btnY, half, 18).build());
    }

    private Item resolveIconPreview() {
        if (cachedIcon != null && !cachedIcon.isEmpty()) {
            try {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(cachedIcon));
                if (item != null && item != Items.AIR) return item;
            } catch (Exception ignored) {}
        }
        return Items.CHEST;
    }

    private void save() {
        CategoryRegistry.renameCategory(categoryId, cachedDisplayName.isEmpty() ? categoryId : cachedDisplayName);
        CategoryRegistry.updateTheme(categoryId, cachedColor, cachedIcon, cachedNameColor);
        CategoryRegistry.save();
        CategoryShaderConfig.set(categoryId, cachedShaderId);
        CategoryShaderConfig.setOverrides(categoryId, overrides);
        CategoryShaderConfig.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {}

    @Override
    public void render(@NotNull GuiGraphics g, int rmx, int rmy, float partial) {
        g.flush();

        int mx = Math.round(rmx / uiScale);
        int my = Math.round(rmy / uiScale);

        ChroniclesUIKit.drawScrim(g, this.width, this.height);

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 300f);
        g.pose().scale(uiScale, uiScale, 1f);

        ChroniclesUIKit.drawModalChrome(g, font, vw, vh, panelLeft, panelTop, PANEL_W, panelH, 22,
                ChroniclesUIKit.str("phoenix_chronicles.screen.category_theme.category_theme_2", categoryId),
                ChroniclesThemePalette.PANEL, ChroniclesThemePalette.HEADER,
                ACCENT, ChroniclesThemePalette.TEXT);

        int fx = panelLeft + MARGIN;
        int fw = PANEL_W - MARGIN * 2;

        g.drawString(font, ChroniclesUIKit.str("phoenix_chronicles.ui.display_name"), fx, rowTop(ROW_NAME),
                ChroniclesThemePalette.TEXT_FAINT);

        int iconRowY = rowTop(ROW_ICON);
        g.fill(fx, iconRowY, fx + FIELD_H + 4, iconRowY + FIELD_H + 4, 0xFF0B0B0F);
        ChroniclesUIKit.drawBorder(g, fx, iconRowY, FIELD_H + 4, FIELD_H + 4, 0xFF333344);
        g.renderItem(new ItemStack(resolveIconPreview()), fx + 1, iconRowY + 1);

        g.drawString(font, ChroniclesUIKit.str("phoenix_chronicles.screen.category_theme.accent_color"), fx,
                rowTop(ROW_COLOR), ChroniclesThemePalette.TEXT_FAINT);
        g.drawString(font, ChroniclesUIKit.str("phoenix_chronicles.ui.display_name_color"), fx, rowTop(ROW_NAME_COLOR),
                ChroniclesThemePalette.TEXT_FAINT);

        g.drawString(font,
                ChroniclesUIKit.str("phoenix_chronicles.screen.category_theme.custom_shader_folder_row_background"), fx,
                rowTop(ROW_SHADER),
                ChroniclesThemePalette.TEXT_FAINT);
        ChroniclesUIKit.drawShaderWarning(g, font, shaderBox, DynamicShaderManager.lastCompileFailed(cachedShaderId));
        if (shaderBox.isMouseOver(mx, my)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("phoenix_chronicles.ui.a_frag_file_s_name"));
            tip.add(Component.translatable("phoenix_chronicles.ui.config_phoenix_chronicles_shaders"));
            if (DynamicShaderManager.lastCompileFailed(cachedShaderId)) {
                tip.add(Component.translatable("phoenix_chronicles.ui.failed_to_compile_check_the"));
            }
            List<String> available = DynamicShaderManager.listAvailable();
            if (!available.isEmpty()) {
                tip.add(Component.translatable("phoenix_chronicles.ui.available", String.join(", ", available)));
            }
            g.renderComponentTooltip(font, tip, mx, my);
        }

        int previewY = rowTop(ROW_SHADER) + ROW_H_TABLE[ROW_SHADER] + 12;
        ChroniclesUIKit.drawBorder(g, fx, previewY, fw, 24, 0xFF333344);
        if (!cachedShaderId.isBlank()) {
            ShaderInstance shader = DynamicShaderManager.get(cachedShaderId);
            if (shader != null) {
                float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
                BackgroundRenderUtil.drawDynamicShaderQuad(g, shader, fx, previewY, fw, 24, t);
            } else if (DynamicShaderManager.lastCompileFailed(cachedShaderId)) {
                g.fill(fx, previewY, fx + fw, previewY + 24, 0xFF0B0B0F);
                g.drawCenteredString(font, ChroniclesUIKit.str("phoenix_chronicles.ui.compile_error"), fx + fw / 2,
                        previewY + 8, 0xFFFF5555);
            } else {
                g.fill(fx, previewY, fx + fw, previewY + 24, 0xFF0B0B0F);
                g.drawCenteredString(font, ChroniclesUIKit.str("phoenix_chronicles.ui.not_found"), fx + fw / 2,
                        previewY + 8, 0xFF555566);
            }
        } else {
            int bg = (cachedColor != 0) ? (0xFF000000 | cachedColor) : 0xFF0B0B0F;
            g.fill(fx, previewY, fx + fw, previewY + 24, bg);
            g.drawCenteredString(font, ChroniclesUIKit.str("phoenix_chronicles.ui.preview"), fx + fw / 2, previewY + 8,
                    0xFFAAAAAA);
        }

        int advY = advancedToggleY();
        ChroniclesUIKit.drawSectionHeader(g, font, fx, advY, fw, SEC_HEADER_H, "Advanced", !advancedOpen,
                overrides.isEmpty() ? "" : overrides.size() + " override(s)", mx, my,
                ChroniclesThemePalette.TEXT, ChroniclesThemePalette.TEXT_DIM);

        if (advancedOpen && overrides.size() > ADV_VISIBLE_ROWS) {
            int vpTop = advViewportTop();
            int vpH = advViewportH();
            int sbX = fx + fw + 2;
            g.fill(sbX, vpTop, sbX + ADV_SCROLLBAR_W, vpTop + vpH, 0x33FFFFFF);
            int thumbH = Math.max(6, vpH * ADV_VISIBLE_ROWS / overrides.size());
            int thumbY = vpTop + (vpH - thumbH) * advScrollRow / Math.max(1, maxAdvScrollRow());
            g.fill(sbX, thumbY, sbX + ADV_SCROLLBAR_W, thumbY + thumbH, 0xFF8866CC);
        }

        for (EditBox condBox : overrideConditionBoxes) {
            if (condBox.isMouseOver(mx, my)) {
                g.renderComponentTooltip(font, List.of(
                        Component.translatable("phoenix_chronicles.ui.evaluated_the_same_way_quest"),
                        Component.translatable("phoenix_chronicles.ui.config_file_key_value_kjs"),
                        Component.translatable("phoenix_chronicles.ui.mod_modid_rule_gamerule_value"),
                        Component.translatable("phoenix_chronicles.ui.is_and_is_or"),
                        Component.translatable("phoenix_chronicles.screen.category_theme.first_override_top_to_bottom"),
                        Component.translatable("phoenix_chronicles.screen.category_theme.falls_back_to_the_custom"),
                        Component.translatable("phoenix_chronicles.screen.category_theme.this_list_is_empty")), mx, my);
                break;
            }
        }
        for (Map.Entry<Integer, EditBox> entry : overrideShaderBoxes.entrySet()) {
            EditBox shBox = entry.getValue();
            String shaderId = overrides.get(entry.getKey()).shaderId;
            ChroniclesUIKit.drawShaderWarning(g, font, shBox, DynamicShaderManager.lastCompileFailed(shaderId));
            if (shBox.isMouseOver(mx, my)) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("phoenix_chronicles.ui.a_frag_file_s_name_2"));
                if (DynamicShaderManager.lastCompileFailed(shaderId)) {
                    tip.add(Component.translatable("phoenix_chronicles.ui.failed_to_compile_check_the"));
                }
                g.renderComponentTooltip(font, tip, mx, my);
                break;
            }
        }

        g.flush();
        super.render(g, mx, my, partial);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double rmx, double rmy, int btn) {
        double mx = rmx / uiScale;
        double my = rmy / uiScale;
        if (btn == 0) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int advY = advancedToggleY();
            if (mx >= fx && mx < fx + fw && my >= advY && my < advY + SEC_HEADER_H) {
                advancedOpen = !advancedOpen;
                clearWidgets();
                init();
                return true;
            }
        }
        if (btn == 0 && (mx < panelLeft || mx >= panelLeft + PANEL_W || my < panelTop || my >= panelTop + panelH)) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double rmx, double rmy, int btn, double dragX, double dragY) {
        return super.mouseDragged(rmx / uiScale, rmy / uiScale, btn, dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseReleased(double rmx, double rmy, int btn) {
        return super.mouseReleased(rmx / uiScale, rmy / uiScale, btn);
    }

    @Override
    public boolean mouseScrolled(double rmx, double rmy, double delta) {
        double mx = rmx / uiScale;
        double my = rmy / uiScale;
        if (advancedOpen && overrides.size() > ADV_VISIBLE_ROWS) {
            int fx = panelLeft + MARGIN;
            int fw = PANEL_W - MARGIN * 2;
            int vpTop = advViewportTop();
            int vpH = advViewportH();
            if (mx >= fx && mx < fx + fw && my >= vpTop && my < vpTop + vpH) {
                advScrollRow = Mth.clamp(advScrollRow - (int) Math.signum(delta), 0, maxAdvScrollRow());
                clearWidgets();
                init();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (minecraft != null) minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

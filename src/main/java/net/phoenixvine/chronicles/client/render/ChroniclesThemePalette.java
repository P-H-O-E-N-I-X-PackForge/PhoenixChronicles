package net.phoenixvine.chronicles.client.render;

import net.phoenixvine.wiki.theme.PhoenixTheme;

public class ChroniclesThemePalette {

    public static int BG, PANEL, PANEL_DARK, HEADER, BORDER, BORDER_LIT, SEL_TAB, SEL_ACCENT;

    public static int TEXT, TEXT_DIM, TEXT_FAINT, TEXT_DONE, TEXT_ACT, PROG_FILL;

    public static int NODE_LOCKED, NODE_UNLOCKED, NODE_ACTIVE, NODE_DONE;
    public static int NBORD_LOCKED, NBORD_UNLOCKED, NBORD_ACTIVE, NBORD_DONE, NBORD_DEV;

    /** Whether the active theme's background is bright enough that white/light-gray text reads poorly on it. */
    public static boolean IS_LIGHT;

    public static void refresh(PhoenixTheme t) {
        BG = t.bg.getColor();
        PANEL = t.panel.getColor();
        PANEL_DARK = t.header.getColor();
        HEADER = t.header.getColor();
        BORDER = t.border.getColor();
        BORDER_LIT = t.accent.getColor();
        SEL_TAB = t.panel.getColor();
        SEL_ACCENT = t.accent.getColor();

        TEXT = t.text.getColor();
        TEXT_DIM = t.textDim.getColor();
        TEXT_FAINT = t.textFaint.getColor();
        TEXT_DONE = t.done.getColor();
        TEXT_ACT = t.activeColor.getColor();
        PROG_FILL = t.accent.getColor();

        IS_LIGHT = luminance(BG) > 140;

        int bg = t.bg.getColor();
        NODE_LOCKED = blendColor(bg, t.locked.getColor(), 0.18f);
        NODE_UNLOCKED = blendColor(bg, t.border.getColor(), 0.35f);
        NODE_ACTIVE = blendColor(bg, t.activeColor.getColor(), 0.22f);
        NODE_DONE = blendColor(bg, t.done.getColor(), 0.18f);

        NBORD_LOCKED = blendColor(t.locked.getColor(), 0xFF000000, 0.25f);
        NBORD_UNLOCKED = blendColor(t.border.getColor(), 0xFFFFFFFF, 0.15f);
        NBORD_ACTIVE = t.activeColor.getColor();
        NBORD_DONE = t.done.getColor();
        NBORD_DEV = blendColor(t.accent.getColor(), 0xFFCC44FF, 0.5f);
    }

    private static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (int) (0.299f * r + 0.587f * g + 0.114f * b);
    }

    /**
     * Swaps the legacy formatting codes we use as ad hoc "neutral text" tiers (§f white / primary,
     * §7 gray / secondary, §8 dark gray / tertiary) so they stay legible when {@link #IS_LIGHT} is
     * true, without touching the saturated/semantic codes (§a, §c, §e, etc.) that already read fine
     * on either background.
     *
     * <p>
     * A no-op unless the active theme is light and the string actually contains a legacy code, so
     * it's safe to call on every bit of ephemeral UI text (button labels, hints, tooltips, drawn
     * strings) regardless of theme. Do NOT use this on text meant to be persisted (quest titles,
     * descriptions, markdown content, etc.) - it must only affect how a viewer's client currently
     * displays text, never what gets saved to disk.
     */
    public static String adapt(String raw) {
        if (!IS_LIGHT || raw == null || raw.indexOf('§') < 0) return raw;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(c);
            if (c == '§' && i + 1 < raw.length()) {
                char code = raw.charAt(i + 1);
                char swapped = switch (Character.toLowerCase(code)) {
                    case 'f' -> '0';
                    case '0' -> 'f';
                    case '7' -> '8';
                    case '8' -> '7';
                    default -> code;
                };
                sb.append(swapped);
                i++;
            }
        }
        return sb.toString();
    }

    private static int blendColor(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

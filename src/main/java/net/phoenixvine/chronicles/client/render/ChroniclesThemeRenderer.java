package net.phoenixvine.chronicles.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ChroniclesThemeRenderer {

    public static void drawScrollbar(GuiGraphics g, int rightMarginX, int topBoundsY, int bottomBoundsY,
                                     int currentScrollY, int totalContentHeight) {
        int visibleHeight = bottomBoundsY - topBoundsY;
        if (totalContentHeight <= visibleHeight) return;

        int trackWidth = 2;
        int scrollTrackLeft = rightMarginX - trackWidth;

        int thumbHeight = Math.max(16, (visibleHeight * visibleHeight) / totalContentHeight);
        long maxScrollableDistance = totalContentHeight - visibleHeight;

        int thumbY = topBoundsY + (int) ((long) currentScrollY * (visibleHeight - thumbHeight) / maxScrollableDistance);

        g.fill(scrollTrackLeft, topBoundsY, rightMarginX, bottomBoundsY, 0x22FFFFFF);
        g.fill(scrollTrackLeft, thumbY, rightMarginX, thumbY + thumbHeight, 0x88FFFFFF);
    }

    public static void drawText(GuiGraphics g, Font font, String text, int x, int y, int textColorDim) {
        g.drawString(font, text, x, y, textColorDim, false);
    }
}

package net.phoenixvine.chronicles.common.model;

import java.util.List;

public record CategoryDefinition(String id, String displayName, List<String> chapters, int color, String icon,
                                 int nameColor, String enableIf) {

    public CategoryDefinition(String id, String displayName, List<String> chapters) {
        this(id, displayName, chapters, 0, "", 0, "");
    }

    public CategoryDefinition(String id, String displayName, List<String> chapters, int color, String icon,
                              int nameColor) {
        this(id, displayName, chapters, color, icon, nameColor, "");
    }

    public CategoryDefinition withTheme(int color, String icon, int nameColor) {
        return new CategoryDefinition(id, displayName, chapters, color, icon != null ? icon : "", nameColor,
                enableIf);
    }

    /**
     * A flag expression (like a quest's enable_if): when it is false for the player, the category and its chapters are
     * hidden.
     */
    public boolean hasCondition() {
        return enableIf != null && !enableIf.isBlank();
    }

    public int effectiveNameColor() {
        return nameColor != 0 ? nameColor : color;
    }
}

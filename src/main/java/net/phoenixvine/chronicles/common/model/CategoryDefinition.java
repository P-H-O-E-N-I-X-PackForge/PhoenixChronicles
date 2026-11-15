package net.phoenixvine.chronicles.common.model;

import java.util.List;

public record CategoryDefinition(String id, String displayName, List<String> chapters, int color, String icon,
                                 int nameColor, String enableIf, String parent) {

    public CategoryDefinition {
        if (parent == null) parent = "";
    }

    public CategoryDefinition(String id, String displayName, List<String> chapters) {
        this(id, displayName, chapters, 0, "", 0, "", "");
    }

    public CategoryDefinition(String id, String displayName, List<String> chapters, int color, String icon,
                              int nameColor) {
        this(id, displayName, chapters, color, icon, nameColor, "", "");
    }

    public CategoryDefinition(String id, String displayName, List<String> chapters, int color, String icon,
                              int nameColor, String enableIf) {
        this(id, displayName, chapters, color, icon, nameColor, enableIf, "");
    }

    public CategoryDefinition withTheme(int color, String icon, int nameColor) {
        return new CategoryDefinition(id, displayName, chapters, color, icon != null ? icon : "", nameColor,
                enableIf, parent);
    }

    public CategoryDefinition withChapters(List<String> newChapters) {
        return new CategoryDefinition(id, displayName, newChapters, color, icon, nameColor, enableIf, parent);
    }

    public CategoryDefinition withDisplayName(String newName) {
        return new CategoryDefinition(id, newName, chapters, color, icon, nameColor, enableIf, parent);
    }

    public CategoryDefinition withParent(String newParent) {
        return new CategoryDefinition(id, displayName, chapters, color, icon, nameColor, enableIf,
                newParent == null ? "" : newParent);
    }

    public boolean hasParent() {
        return !parent.isEmpty();
    }

    public boolean hasCondition() {
        return enableIf != null && !enableIf.isBlank();
    }

    public int effectiveNameColor() {
        return nameColor != 0 ? nameColor : color;
    }
}

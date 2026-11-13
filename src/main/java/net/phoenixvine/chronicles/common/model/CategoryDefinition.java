package net.phoenixvine.chronicles.common.model;

import java.util.List;

/**
 * A sidebar folder of chapters. Categories can be nested: {@code parent} is the id of the category this one sits
 * inside, or empty for a top-level category.
 */
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

    /**
     * A flag expression (like a quest's enable_if): when it is false for the player, the category and its chapters
     * (and any categories nested inside it) are hidden.
     */
    public boolean hasCondition() {
        return enableIf != null && !enableIf.isBlank();
    }

    public int effectiveNameColor() {
        return nameColor != 0 ? nameColor : color;
    }
}

package net.phoenixvine.chronicles.client.screen.utils;

/**
 * One row of the chapter sidebar. {@code depth} is how many folders the row sits inside (0 = top level), so nested
 * categories and the chapters in them indent further.
 */
public record SidebarRow(boolean isFolder, String id, String label, int y, int height, boolean inFolder,
                         boolean collapsed, boolean subChapter, boolean locked, int depth) {

    public SidebarRow(boolean isFolder, String id, String label, int y, int height, boolean inFolder,
                      boolean collapsed, boolean subChapter, boolean locked) {
        this(isFolder, id, label, y, height, inFolder, collapsed, subChapter, locked, inFolder ? 1 : 0);
    }
}

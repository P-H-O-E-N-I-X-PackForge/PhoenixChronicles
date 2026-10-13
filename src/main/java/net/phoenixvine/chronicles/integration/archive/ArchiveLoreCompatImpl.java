package net.phoenixvine.chronicles.integration.archive;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;

final class ArchiveLoreCompatImpl {

    private ArchiveLoreCompatImpl() {}

    static boolean hasLoreFor(String questId) {
        return false;
    }

    static void openLoreFor(Screen returnTo, String questId) {}

    static void unlockEntry(ServerPlayer player, String entryId) {}

    static void installEntryListener() {}

    static boolean hasEntry(String entryId) {
        return false;
    }

    static void openEntry(Screen returnTo, String entryId) {}
}

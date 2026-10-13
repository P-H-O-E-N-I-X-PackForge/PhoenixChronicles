package net.phoenixvine.chronicles.client.registry;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.phoenixvine.chronicles.common.registry.QuestLangRegistry;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class ChroniclesLangPack {

    public static void register(@NotNull AddPackFindersEvent event) {
        Path root = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles");
        QuestLangRegistry.ensurePackStructure(root);
        QuestLangRegistry.syncTranslations(root);

        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(
                    new net.minecraft.server.packs.PackLocationInfo("phoenix_chronicles_lang",
                            Component.literal("Phoenix Chronicles Quest Translations"), PackSource.BUILT_IN,
                            java.util.Optional.empty()),
                    new PathPackResources.PathResourcesSupplier(root),
                    PackType.CLIENT_RESOURCES,
                    new net.minecraft.server.packs.PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) consumer.accept(pack);
        });
    }

    public static void reload() {
        QuestLangRegistry.syncTranslations(
                Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("phoenix_chronicles"));
        Minecraft.getInstance().reloadResourcePacks();
    }
}

package net.phoenixvine.chronicles;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.phoenixvine.chronicles.client.ChronicleShaders;
import net.phoenixvine.chronicles.client.ChroniclesClient;
import net.phoenixvine.chronicles.client.registry.ChroniclesLangPack;
import net.phoenixvine.chronicles.common.item.ChronicleItems;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.network.ChronicleNetwork;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PhoenixChronicles.MOD_ID)
public class PhoenixChronicles {

    public static final String MOD_ID = "phoenix_chronicles";
    public static final Logger LOGGER = LogManager.getLogger();

    public PhoenixChronicles(IEventBus modEventBus, ModContainer container) {
        ChronicleItems.ITEMS.register(modEventBus);
        net.phoenixvine.chronicles.capability.QuestCapabilityProvider.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ChronicleNetwork::register);

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(ChroniclesClient::onClientSetup);
            modEventBus.addListener(ChronicleShaders::onRegisterShaders);
        }

        modEventBus.addListener(this::addPackFinders);
        modEventBus.addListener(this::buildCreativeTab);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[Phoenix Chronicles] Bootstrapping standalone quest engine core...");

            PhoenixTheme.loadThemes();
            PhoenixTaskRegistry.registerBuiltins();

            if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("phantasia")) {
                PhantasiaCompat.init();
            }

            LOGGER.info("Look, I found a {}!", Items.DIAMOND);
        });
    }

    private void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        ChroniclesLangPack.register(event);
    }

    private void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) return;
        event.accept(ChronicleItems.CHRONICLE_BOOK.get());
        event.accept(ChronicleItems.CHRONICLE_LOOT_CRATE.get());
        event.accept(ChronicleItems.ITEM_FILTER_TOKEN.get());
        event.accept(ChronicleItems.FLUID_FILTER_TOKEN.get());
    }
}

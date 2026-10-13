package net.phoenixvine.chronicles.common.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.phoenixvine.chronicles.PhoenixChronicles;

public class ChronicleItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PhoenixChronicles.MOD_ID);

    public static final DeferredHolder<Item, ChronicleBookItem> CHRONICLE_BOOK = ITEMS.register("chronicle_book",
            () -> new ChronicleBookItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, ChronicleLootCrateItem> CHRONICLE_LOOT_CRATE = ITEMS.register(
            "chronicle_loot_crate",
            () -> new ChronicleLootCrateItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, ItemFilterTokenItem> ITEM_FILTER_TOKEN = ITEMS.register(
            "item_filter_token",
            () -> new ItemFilterTokenItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, FluidFilterTokenItem> FLUID_FILTER_TOKEN = ITEMS.register(
            "fluid_filter_token",
            () -> new FluidFilterTokenItem(new Item.Properties().stacksTo(1)));
}

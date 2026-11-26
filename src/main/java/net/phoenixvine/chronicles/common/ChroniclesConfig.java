package net.phoenixvine.chronicles.common;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-side switches of the quest engine. The file is {@code config/phoenix_chronicles-common.toml}. */
public final class ChroniclesConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue GIVE_BOOK_ON_FIRST_JOIN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("starting_items");
        GIVE_BOOK_ON_FIRST_JOIN = b
                .comment("Give a player the Chronicle book the first time they join the world. It is given once per",
                        "player and is not given again after death or when they rejoin. A full inventory drops it at",
                        "their feet.")
                .define("giveBookOnFirstJoin", false);
        b.pop();
        SPEC = b.build();
    }

    private ChroniclesConfig() {}
}

package net.phoenixvine.chronicles.integration.gtceu;

import net.neoforged.fml.ModList;

public final class GTCEuCompat {

    private GTCEuCompat() {}

    public static final String GTCEU_MOD_ID = "gtceu";

    public static boolean isAvailable() {
        return ModList.get().isLoaded(GTCEU_MOD_ID);
    }
}

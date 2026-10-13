package net.phoenixvine.chronicles;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

@SuppressWarnings("unused")
@GTAddon(PhoenixChronicles.MOD_ID)
public class ChroniclesGTAddon implements IGTAddon {

    private static GTRegistrate registrate;

    @Override
    public GTRegistrate getRegistrate() {
        if (registrate == null) registrate = GTRegistrate.create(PhoenixChronicles.MOD_ID);
        return registrate;
    }
}

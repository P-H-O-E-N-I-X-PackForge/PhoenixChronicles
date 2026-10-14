package net.phoenixvine.chronicles.integration.gtm;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.phoenixvine.chronicles.common.tasks.MultiblockFormedTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Only touch this class when GregTech is loaded. */
public final class GtmMachines {

    private GtmMachines() {}

    /** "*" (any multiblock) followed by every multiblock controller id, sorted. */
    public static Collection<String> multiblockControllerIds() {
        List<String> ids = new ArrayList<>();
        for (var definition : GTRegistries.MACHINES) {
            if (definition instanceof MultiblockMachineDefinition) ids.add(definition.getId().toString());
        }
        ids.sort(String::compareTo);
        ids.add(0, MultiblockFormedTask.ANY);
        return ids;
    }
}

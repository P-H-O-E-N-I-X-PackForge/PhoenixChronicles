package net.phoenixvine.chronicles.integration.gtceu;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.common.tasks.MultiblockFormedTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class GtmMachines {

    private GtmMachines() {}

    public static Collection<String> multiblockControllerIds() {
        List<String> ids = new ArrayList<>();
        for (var block : ForgeRegistries.BLOCKS) {
            if (block instanceof MetaMachineBlock machine &&
                    machine.getDefinition() instanceof MultiblockMachineDefinition) {
                var key = ForgeRegistries.BLOCKS.getKey(block);
                if (key != null) ids.add(key.toString());
            }
        }
        ids.sort(String::compareTo);
        ids.add(0, MultiblockFormedTask.ANY);
        return ids;
    }
}

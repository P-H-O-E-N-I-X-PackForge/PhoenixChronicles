package net.phoenixvine.chronicles.mixin;

import net.phoenixvine.chronicles.integration.gtm.GtmHooks;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires when the structure actually forms, not when the controller is placed. */
@Mixin(value = MultiblockControllerMachine.class, remap = false)
public abstract class MultiblockControllerFormedMixinGtm8 {

    @Inject(method = "onStructureFormed", at = @At("TAIL"), remap = false)
    private void phoenixChronicles$onStructureFormed(CallbackInfo ci) {
        MultiblockControllerMachine self = (MultiblockControllerMachine) (Object) this;
        GtmHooks.onMultiblockFormed(self.getLevel(), self.getBlockPos(), self.getOwnerUUID());
    }
}

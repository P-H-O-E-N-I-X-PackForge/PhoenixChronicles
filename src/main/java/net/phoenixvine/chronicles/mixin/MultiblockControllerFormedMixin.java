package net.phoenixvine.chronicles.mixin;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import net.phoenixvine.chronicles.integration.gtm.GtmHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiblockControllerMachine.class, remap = false)
public abstract class MultiblockControllerFormedMixin {

    @Shadow
    protected boolean isFormed;

    @Unique
    private boolean phoenixChronicles$wasFormed;

    @Inject(method = "formStructure(Ljava/lang/String;)V", at = @At("HEAD"), remap = false)
    private void phoenixChronicles$beforeForm(String name, CallbackInfo ci) {
        phoenixChronicles$wasFormed = isFormed;
    }

    @Inject(method = "formStructure(Ljava/lang/String;)V", at = @At("TAIL"), remap = false)
    private void phoenixChronicles$afterForm(String name, CallbackInfo ci) {
        if (phoenixChronicles$wasFormed || !isFormed) return;
        MultiblockControllerMachine self = (MultiblockControllerMachine) (Object) this;
        GtmHooks.onMultiblockFormed(self.getLevel(), self.getBlockPos(), self.getOwnerUUID());
    }
}

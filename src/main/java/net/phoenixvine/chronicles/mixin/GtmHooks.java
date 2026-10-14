package net.phoenixvine.chronicles.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.phoenixvine.chronicles.QuestAPI;

import java.util.UUID;

public final class GtmHooks {

    private static final double NEAREST_PLAYER_RANGE = 10.0;

    private GtmHooks() {}

    public static void onRecipeFinished(Level level, BlockPos machinePos, UUID owner, ResourceLocation recipeType,
                                        ResourceLocation recipeId) {
        if (!(level instanceof ServerLevel serverLevel) || recipeType == null) return;

        ServerPlayer player = findPlayer(serverLevel, machinePos, owner);
        if (player == null) return;

        QuestAPI.fireRecipeCompleted(player, recipeType, recipeId, 1);
    }

    public static void onMultiblockFormed(Level level, BlockPos controllerPos, UUID owner) {
        if (!(level instanceof ServerLevel serverLevel) || controllerPos == null) return;

        ResourceLocation machineId = ForgeRegistries.BLOCKS.getKey(
                serverLevel.getBlockState(controllerPos).getBlock());
        if (machineId == null) return;

        ServerPlayer player = findPlayer(serverLevel, controllerPos, owner);
        if (player == null) return;

        QuestAPI.fireMultiblockFormed(player, machineId, 1);

        CompoundTag data = new CompoundTag();
        data.putString("machine_id", machineId.toString());
        QuestAPI.fireExternalEvent(player, "gtceu_multiblock:" + machineId, data);
    }

    /** The machine's owner when online, otherwise the nearest player. */
    private static ServerPlayer findPlayer(ServerLevel level, BlockPos pos, UUID owner) {
        if (owner != null) {
            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer != null) return ownerPlayer;
        }
        Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                NEAREST_PLAYER_RANGE, false);
        return nearest instanceof ServerPlayer sp ? sp : null;
    }
}

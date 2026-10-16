package net.phoenixvine.chronicles.common.event;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.PooledTaskProgress;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.codec.*;
import net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.*;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;
import net.phoenixvine.chronicles.common.tracker.TeamKeyResolver;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncPooledProgressPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = PhoenixChronicles.MOD_ID)
public class ChronicleEvents {

    public static MinecraftServer getCachedServer() {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    private static volatile boolean hasServerStarted = false;

    public static boolean hasServerFullyStarted() {
        return hasServerStarted;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ChronicleDataLoader());
    }

    public static java.nio.file.Path resolveConfigDir(MinecraftServer server) {
        try {
            java.nio.file.Path worldSpecific = server
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("phoenix_chronicles");
            if (java.nio.file.Files.exists(worldSpecific)) return worldSpecific;
        } catch (Exception ignored) {}
        return server.getServerDirectory().resolve("config").resolve("phoenix_chronicles");
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        java.nio.file.Path configDir = resolveConfigDir(event.getServer());

        try {
            java.nio.file.Files.createDirectories(configDir.resolve("ftb_import"));
        } catch (java.io.IOException ignored) {}

        PhoenixTaskRegistry.registerBuiltins();
        net.phoenixvine.chronicles.integration.archive.ArchiveLoreCompat.registerRewards();
        KubeJsTaskTypeLoader.load(configDir);
        PhoenixQuestFlags.invalidateCaches();
        ChronicleDataMigration.migrate(configDir);
        ChapterFlagRegistry.load(configDir);
        ChapterPrereqDefaults.load(configDir);
        ChapterEmergencyItems.load(configDir);
        QuestEngineConfig.load(configDir);
        RewardTableRegistry.load(configDir);
        CategoryRegistry.load(configDir);
        QuestFileLoader.loadAdditiveFromDisk(configDir);
        QuestFileWatcher.start(event.getServer(), configDir);
        hasServerStarted = true;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new QuestEvent.TreeReloaded());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        hasServerStarted = false;
        QuestFileWatcher.stop();
    }

    @SubscribeEvent
    public static void onItemPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;

        QuestCapabilityProvider.of(player).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer(), player)) {
                    if (task instanceof ItemRequirementTask ||
                            task instanceof TagItemTask) {
                        changed = true;
                        break;
                    }
                }

                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }
            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem());
        if (itemId == null) return;
        int amount = event.getCrafting().getCount();

        QuestCapabilityProvider.of(player).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer(), player)) {
                    if (task instanceof CraftItemTask craftTask) {
                        craftTask.onItemCrafted(player, itemId, amount);
                        changed = true;
                    } else if (task instanceof ItemRequirementTask ||
                            task instanceof TagItemTask) {
                                changed = true;
                            }
                }

                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onEntityKilled(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            if (player.level().isClientSide) return;

            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            if (entityId == null) return;

            QuestCapabilityProvider.of(player).ifPresent(data -> {
                boolean needSync = false;
                for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                    QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                    if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                    boolean changed = false;
                    for (Object task : node.getEffectiveTasks(player.getServer(), player)) {
                        if (task instanceof KillEntityTask killTask) {
                            killTask.onEntityKilled(player, event.getEntity().getType());
                            changed = true;
                        }
                    }
                    if (changed) {
                        QuestProgressTracker.checkAndTryComplete(player, node);
                        needSync = true;
                    }
                }

                if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    QuestProgressTracker.sendProgressSync(sp);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ResourceLocation advancementId = event.getAdvancement().id();

        QuestCapabilityProvider.of(player).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;

                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer(), player)) {
                    if (task instanceof AdvancementTask advTask) {
                        advTask.onAdvancementEarned(player, advancementId);
                        changed = true;
                    }
                }
                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        net.minecraft.world.level.block.Block broken = event.getState().getBlock();
        QuestCapabilityProvider.of(player).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer(), player)) {
                    if (task instanceof BlockBreakTask breakTask) {
                        breakTask.onBlockBroken(player, broken);
                        changed = true;
                    }
                }
                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync) QuestProgressTracker.sendProgressSync(player);
        });
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        Block placed = event.getPlacedBlock().getBlock();
        handleBlockEvent(player, placed, "PLACE");
    }

    @SubscribeEvent
    public static void onBlockRightClicked(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Block clicked = event.getLevel().getBlockState(event.getPos()).getBlock();

        if (net.phoenixvine.chronicles.integration.gtceu.GTCEuCompat.isAvailable()) {
            EnergyStorageTask.onBlockRightClicked(
                    player, event.getLevel(), event.getPos());
        }

        if (player.level().isClientSide) return;
        handleBlockEvent(player, clicked, "RIGHT_CLICK");
    }

    private static void handleBlockEvent(Player player, Block block, String action) {
        QuestCapabilityProvider.of(player).ifPresent(data -> {
            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer(), player)) {
                    if (task instanceof BlockInteractTask blockTask) {
                        blockTask.onBlockEvent(player, block, action);
                        changed = true;
                    }
                }
                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        var dimension = event.getTo();
        QuestCapabilityProvider.of(player).ifPresent(data -> {

            if (player instanceof net.minecraft.server.level.ServerPlayer spInit) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(spInit,
                        new S2CSyncPlayerProgressPacket(data, true));
            }

            boolean needSync = false;
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                QuestState state = data.getQuestState(node.getId(), QuestState.LOCKED);
                if (state == QuestState.COMPLETED || state == QuestState.LOCKED) continue;
                boolean changed = false;
                for (Object task : node.getEffectiveTasks(player.getServer(), player)) {
                    if (task instanceof DimensionTask dimTask) {
                        dimTask.onChangedDimension(player, dimension);
                        changed = true;
                    }
                }
                if (changed) {
                    QuestProgressTracker.checkAndTryComplete(player, node);
                    needSync = true;
                }
            }

            if (needSync && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                QuestProgressTracker.sendProgressSync(sp);
            }
        });
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        QuestCapabilityProvider.of(event.getEntity())
                .ifPresent(newData -> QuestProgressTracker.cachePlayerData(event.getEntity().getUUID(), newData));
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
        if (serverPlayer.level().isClientSide) return;

        QuestCapabilityProvider.of(serverPlayer)
                .ifPresent(data -> net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                        new S2CSyncPlayerProgressPacket(data, true)));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EnergyStorageTask.clearBlockCache(
                event.getEntity().getUUID());
        QuestProgressTracker.clearInventoryFingerprint(
                event.getEntity().getUUID());
        QuestProgressTracker.clearPlayerDataCache(
                event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {

            QuestProgressTracker.beginLoginSync(serverPlayer.getUUID());

            QuestCapabilityProvider.of(serverPlayer)
                    .ifPresent(data -> QuestProgressTracker.cachePlayerData(serverPlayer.getUUID(), data));

            Map<ResourceLocation, QuestNode> serverQuests = QuestTreeRegistry.getAllQuests();
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new S2CSyncQuestsPacket(serverQuests, serverPlayer.getServer()));

            QuestProgressTracker.autoUnlockSatisfiedQuests(serverPlayer);
            QuestProgressTracker.sendProgressSync(serverPlayer);
            sendInitialPooledSync(serverPlayer);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new net.phoenixvine.chronicles.network.packet.S2CSyncTeamKeyPacket(
                            TeamKeyResolver.resolve(serverPlayer).orElse("")));
        }
    }

    private static void sendInitialPooledSync(net.minecraft.server.level.ServerPlayer player) {
        TeamKeyResolver.resolve(player).ifPresent(teamKey -> {
            PooledTaskProgress pooled = PooledTaskProgress.get(player.serverLevel());
            Map<ResourceLocation, net.minecraft.nbt.CompoundTag> bulk = new HashMap<>();
            for (QuestNode node : QuestTreeRegistry.getAllQuests().values()) {
                if (!node.isPooledProgress()) continue;
                for (QuestTask task : node.getEffectiveTasks(player.getServer(),
                        player)) {
                    bulk.put(task.getTaskId(), pooled.getOrCreate(teamKey, task.getTaskId()));
                }
            }
            if (!bulk.isEmpty()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new S2CSyncPooledProgressPacket(bulk));
            }
        });
    }

    @SubscribeEvent
    public static void onTreeReloaded(QuestEvent.TreeReloaded event) {
        MinecraftServer server = getCachedServer();
        if (server == null) return;
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestProgressTracker.autoUnlockSatisfiedQuests(player);
        }
    }
}

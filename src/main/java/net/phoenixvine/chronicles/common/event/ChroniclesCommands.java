package net.phoenixvine.chronicles.common.event;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.phoenixvine.chronicles.PhoenixChronicles;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.common.codec.*;
import net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.model.RewardTable;
import net.phoenixvine.chronicles.common.registry.*;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.common.tracker.QuestProgressTracker;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = PhoenixChronicles.MOD_ID)
public class ChroniclesCommands {

    private static net.minecraft.resources.ResourceLocation parseQuestArg(String raw) {
        return raw.indexOf(':') >= 0 ? net.minecraft.resources.ResourceLocation.parse(raw) :
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", raw);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("chronicles")
                .then(statusCommand())
                .then(emergencyCommand())
                .then(completeCommand())
                .then(forcetaskCommand())
                .then(resettaskCommand())
                .then(unlockCommand())
                .then(resetCommand())
                .then(activeCommand())
                .then(reloadCommand())
                .then(rewardtableCommand())
                .then(exportCommand())
                .then(importCommand())
                .then(importFtbCommand())
                .then(importFtbProgressCommand())
                .then(validateCommand())
                .then(flagCommand()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> statusCommand() {
        return Commands.literal("status")
                .then(Commands.argument("quest", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> {
                            if (!(ctx.getSource()
                                    .getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                                ctx.getSource().sendFailure(Component.literal("Must be run by a player."));
                                return 0;
                            }
                            String qStr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                    "quest");
                            net.minecraft.resources.ResourceLocation questId;
                            try {
                                questId = parseQuestArg(qStr);
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(Component.literal("Invalid quest id: " + qStr));
                                return 0;
                            }
                            QuestNode node = QuestTreeRegistry.getQuest(questId);
                            if (node == null) {
                                ctx.getSource().sendFailure(Component.literal("Quest not found: " + qStr));
                                return 0;
                            }
                            QuestState state = QuestCapabilityProvider.of(sp)
                                    .map(d -> d.getQuestState(questId, QuestState.LOCKED))
                                    .orElse(QuestState.LOCKED);
                            String stateLabel = switch (state) {
                                case COMPLETED -> "§aCompleted";
                                case ACTIVE -> "§eActive";
                                case UNLOCKED -> "§bAvailable";
                                default -> "§7Locked";
                            };
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Quest \"" + node.getTitle().getString() + "\": " + stateLabel), false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> emergencyCommand() {
        return Commands.literal("emergency")
                .then(Commands.argument("quest", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> {
                            if (!(ctx.getSource()
                                    .getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                                ctx.getSource().sendFailure(Component.literal("Must be run by a player."));
                                return 0;
                            }
                            String qStr = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                    "quest");
                            net.minecraft.resources.ResourceLocation questId;
                            try {
                                questId = parseQuestArg(qStr);
                            } catch (Exception e) {
                                ctx.getSource().sendFailure(Component.literal("Invalid quest id: " + qStr));
                                return 0;
                            }
                            QuestNode node = QuestTreeRegistry.getQuest(questId);
                            if (node == null) {
                                ctx.getSource().sendFailure(Component.literal("Quest not found: " + qStr));
                                return 0;
                            }
                            QuestProgressTracker.EmergencyResult result = QuestProgressTracker
                                    .claimEmergencyItems(sp, node);
                            if (!result.success()) {
                                ctx.getSource().sendFailure(Component.literal(result.message()));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§a" + result.message()), false);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> completeCommand() {
        return Commands.literal("complete")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("quest", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> devSetState(ctx, QuestState.COMPLETED, null))
                        .then(Commands
                                .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> devSetState(ctx, QuestState.COMPLETED,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> forcetaskCommand() {
        return Commands.literal("forcetask")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("task",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> devForceTask(ctx, null))
                        .then(Commands
                                .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> devForceTask(ctx,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resettaskCommand() {
        return Commands.literal("resettask")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("task",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> devResetTask(ctx, null))
                        .then(Commands
                                .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> devResetTask(ctx,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> unlockCommand() {
        return Commands.literal("unlock")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("quest", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> devSetState(ctx, QuestState.UNLOCKED, null))
                        .then(Commands
                                .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> devSetState(ctx, QuestState.UNLOCKED,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resetCommand() {
        return Commands.literal("reset")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("quest", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> devResetQuest(ctx, null))
                        .then(Commands
                                .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> devResetQuest(ctx,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> activeCommand() {
        return Commands.literal("active")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("quest", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> devSetState(ctx, QuestState.ACTIVE, null))
                        .then(Commands
                                .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> devSetState(ctx, QuestState.ACTIVE,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,
                                                "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return Commands.literal("reload")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    MinecraftServer server = ChronicleEvents.getCachedServer();
                    if (server == null) {
                        ctx.getSource().sendFailure(Component.literal("Server not available."));
                        return 0;
                    }
                    java.nio.file.Path configDir = ChronicleEvents.resolveConfigDir(server);
                    QuestTreeRegistry.clearConfigQuests();
                    ChapterFlagRegistry.load(configDir);
                    ChapterPrereqDefaults.load(configDir);
                    ChapterEmergencyItems.load(configDir);
                    QuestEngineConfig.load(configDir);
                    RewardTableRegistry.load(configDir);
                    CategoryRegistry.load(configDir);
                    QuestFileLoader.loadAdditiveFromDisk(configDir);
                    int questCount = QuestTreeRegistry.getAllQuests().size();
                    S2CSyncQuestsPacket syncPacket = new S2CSyncQuestsPacket(QuestTreeRegistry.getAllQuests(),
                            server);
                    int playerCount = 0;
                    for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, syncPacket);
                        playerCount++;
                    }
                    final int fp = playerCount;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a✔ Reloaded " + questCount + " quest(s) from config, synced to " + fp +
                                    " player(s)."),
                            true);
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rewardtableCommand() {
        return Commands.literal("rewardtable")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("export")
                        .then(Commands
                                .argument("table", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(RewardTableRegistry.getAll().keySet(), b))
                                .executes(ctx -> {
                                    net.minecraft.server.level.ServerPlayer sp = ctx.getSource()
                                            .getPlayerOrException();
                                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                            "table");
                                    RewardTable table = RewardTableRegistry.get(id);
                                    if (table == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("No reward table '" + id + "'."));
                                        return 0;
                                    }
                                    net.minecraft.world.Container box = RewardTableChest.targetContainer(sp);
                                    if (box == null) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "Look at a chest, barrel, or other container (within 6 blocks)."));
                                        return 0;
                                    }
                                    RewardTableChest.Result r = RewardTableChest.export(table, box);
                                    if (!r.ok()) {
                                        ctx.getSource().sendFailure(Component.literal(r.message()));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a✔ " + r.message()),
                                            false);
                                    return 1;
                                })))
                .then(Commands.literal("import")
                        .then(Commands
                                .argument("table", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggest(RewardTableRegistry.getAll().keySet(), b))
                                .executes(ctx -> doRewardTableImport(ctx, null))
                                .then(Commands
                                        .argument("pick",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(ctx -> doRewardTableImport(ctx,
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(ctx, "pick"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> exportCommand() {
        return Commands.literal("export")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    MinecraftServer server = ChronicleEvents.getCachedServer();
                    if (server == null) {
                        ctx.getSource().sendFailure(Component.literal("Server not available."));
                        return 0;
                    }
                    String stamp = java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    java.nio.file.Path exportDir = ChronicleEvents.resolveConfigDir(server)
                            .resolve("export").resolve(stamp);
                    try {
                        java.nio.file.Files.createDirectories(exportDir);
                    } catch (java.io.IOException ignored) {}
                    int count = QuestFileSaver.exportTo(exportDir);
                    final java.nio.file.Path fp = exportDir;
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a✔ Exported §f" + count + " §aquest(s) to §7" + fp), false);
                    return count;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> importCommand() {
        return Commands.literal("import")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> doImport(ctx, "import"))
                .then(Commands
                        .argument("subfolder", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> doImport(ctx,
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                        "subfolder"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> importFtbCommand() {
        return Commands.literal("import-ftb")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> doImportFtb(ctx, "ftb_import"))
                .then(Commands
                        .argument("subfolder", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(ctx -> doImportFtb(ctx,
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                        "subfolder"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> importFtbProgressCommand() {
        return Commands.literal("import-ftb-progress")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> doImportFtbProgress(ctx, null))
                .then(Commands
                        .argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(ctx -> doImportFtbProgress(ctx,
                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> validateCommand() {
        return Commands.literal("validate")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    List<String> errors = QuestFileLoader.LOAD_ERRORS;

                    List<String> noTask = new ArrayList<>();
                    for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {

                        if (n.getTasks().isEmpty() && !n.isLinkStub()) noTask.add(n.getId().getPath());
                    }
                    int total = errors.size() + noTask.size();
                    if (total == 0) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§a✔ No issues found. " +
                                QuestTreeRegistry.getAllQuests().size() + " quests loaded cleanly."), false);
                        return 1;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("§e⚠ " + total + " issue(s) found:"),
                            false);
                    for (String err : errors)
                        ctx.getSource().sendSuccess(() -> Component.literal("§c✗ " + err), false);
                    for (String id : noTask)
                        ctx.getSource()
                                .sendSuccess(() -> Component.literal(
                                        "§7◦ '" + id + "' has no tasks: will auto-complete on unlock."),
                                        false);
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> flagCommand() {
        return Commands.literal("flag")
                .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> {
                            if (!(ctx.getSource()
                                    .getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                                ctx.getSource().sendFailure(Component.literal("Must be run by a player."));
                                return 0;
                            }
                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                    "name");
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§7" + PhoenixQuestFlags.describeFlag(sp, name)),
                                    false);
                            return 1;
                        }));
    }

    private static int devSetState(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                   QuestState target,
                                   @Nullable net.minecraft.server.level.ServerPlayer explicitPlayer) {
        net.minecraft.server.level.ServerPlayer sp = explicitPlayer;
        if (sp == null) {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer self)) {
                ctx.getSource().sendFailure(Component.literal("Must specify a player when running from console."));
                return 0;
            }
            sp = self;
        }
        String questArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "quest");
        net.minecraft.resources.ResourceLocation questId;
        try {
            questId = parseQuestArg(questArg);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid quest ID: " + questArg));
            return 0;
        }
        QuestNode node = QuestTreeRegistry.getQuest(questId);
        if (node == null) {
            ctx.getSource().sendFailure(Component.literal("Quest not found: " + questArg));
            return 0;
        }
        final net.minecraft.server.level.ServerPlayer fsp = sp;
        QuestCapabilityProvider.of(fsp)
                .ifPresent(data -> {
                    data.setQuestState(questId, target);
                    QuestProgressTracker.updateActiveTracking(
                            fsp.getUUID(), node, target);

                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(fsp, new S2CSyncPlayerProgressPacket(
                            data));
                });
        String label = target == QuestState.COMPLETED ? "§acompleted" :
                target == QuestState.ACTIVE ? "§estarted" :
                        target == QuestState.UNLOCKED ? "§bunlocked" : "§7reset";
        String name = fsp.getName().getString();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Quest " + label + "§r for " + name + ": " + questArg), true);
        return 1;
    }

    private static int devForceTask(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                    @Nullable net.minecraft.server.level.ServerPlayer explicitPlayer) {
        net.minecraft.server.level.ServerPlayer sp = explicitPlayer;
        if (sp == null) {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer self)) {
                ctx.getSource().sendFailure(Component.literal("Must specify a player when running from console."));
                return 0;
            }
            sp = self;
        }
        String taskArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "task");
        net.minecraft.resources.ResourceLocation taskId;
        try {
            taskId = parseQuestArg(taskArg);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid task ID: " + taskArg));
            return 0;
        }
        QuestNode node = QuestTreeRegistry.getTaskOwner(taskId);
        if (node == null) {
            ctx.getSource().sendFailure(Component.literal("Task not found: " + taskArg));
            return 0;
        }

        net.minecraft.server.level.ServerPlayer fsp = sp;
        net.phoenixvine.chronicles.capability.TaskProgressAccess.with(fsp, taskId,
                nbt -> nbt.putBoolean("completed", true));
        QuestProgressTracker.checkAndTryComplete(fsp, node);
        QuestProgressTracker.sendProgressSync(fsp);

        String name = fsp.getName().getString();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Task §aforce-completed§r for " + name + ": " + taskArg), true);
        return 1;
    }

    private static int devResetTask(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                    @Nullable net.minecraft.server.level.ServerPlayer explicitPlayer) {
        net.minecraft.server.level.ServerPlayer sp = explicitPlayer;
        if (sp == null) {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer self)) {
                ctx.getSource().sendFailure(Component.literal("Must specify a player when running from console."));
                return 0;
            }
            sp = self;
        }
        String taskArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "task");
        net.minecraft.resources.ResourceLocation taskId;
        try {
            taskId = parseQuestArg(taskArg);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid task ID: " + taskArg));
            return 0;
        }
        QuestNode node = QuestTreeRegistry.getTaskOwner(taskId);
        if (node == null) {
            ctx.getSource().sendFailure(Component.literal("Task not found: " + taskArg));
            return 0;
        }

        net.minecraft.server.level.ServerPlayer fsp = sp;
        net.phoenixvine.chronicles.capability.TaskProgressAccess.clear(fsp, taskId);

        QuestCapabilityProvider.of(fsp).ifPresent(data -> {
            if (data.getQuestState(node.getId(), QuestState.LOCKED) == QuestState.COMPLETED) {
                data.setQuestState(node.getId(), QuestState.UNLOCKED);
                data.clearClaimedRewards(node.getId());
                data.clearChosenRewardIndices(node.getId());
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(fsp, new S2CSyncPlayerProgressPacket(data));
        });
        QuestProgressTracker.sendProgressSync(fsp);

        String name = fsp.getName().getString();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Task §7progress reset§r for " + name + ": " + taskArg), true);
        return 1;
    }

    private static int devResetQuest(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                     @Nullable net.minecraft.server.level.ServerPlayer explicitPlayer) {
        net.minecraft.server.level.ServerPlayer sp = explicitPlayer;
        if (sp == null) {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer self)) {
                ctx.getSource().sendFailure(Component.literal("Must specify a player when running from console."));
                return 0;
            }
            sp = self;
        }
        String questArg = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "quest");
        net.minecraft.resources.ResourceLocation questId;
        try {
            questId = parseQuestArg(questArg);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid quest ID: " + questArg));
            return 0;
        }
        QuestNode node = QuestTreeRegistry.getQuest(questId);
        if (node == null) {
            ctx.getSource().sendFailure(Component.literal("Quest not found: " + questArg));
            return 0;
        }
        List<net.minecraft.resources.ResourceLocation> taskIds = new java.util.ArrayList<>();
        for (QuestTask t : node.getTasks()) taskIds.add(t.getTaskId());
        final net.minecraft.server.level.ServerPlayer fsp = sp;
        QuestCapabilityProvider.of(fsp)
                .ifPresent(data -> {
                    data.resetQuestProgress(questId, taskIds);

                    if (QuestProgressTracker.prereqsSatisfied(node, data,
                            fsp.getServer())) {
                        QuestProgressTracker.changeQuestState(fsp, node,
                                QuestState.UNLOCKED);
                    }

                    QuestProgressTracker.relockUnsatisfied(fsp);

                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(fsp, new S2CSyncPlayerProgressPacket(
                            data));
                });
        String name = fsp.getName().getString();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Quest §7progress reset§r for " + name + ": " + questArg), true);
        return 1;
    }

    private static int doImportFtbProgress(
                                           com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                           @Nullable net.minecraft.server.level.ServerPlayer explicitPlayer) {
        net.minecraft.server.level.ServerPlayer sp = explicitPlayer;
        if (sp == null) {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer self)) {
                ctx.getSource().sendFailure(Component.literal("Must specify a player when running from console."));
                return 0;
            }
            sp = self;
        }

        MinecraftServer server = ChronicleEvents.getCachedServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("Server not available."));
            return 0;
        }

        java.nio.file.Path configDir = ChronicleEvents.resolveConfigDir(server);
        java.nio.file.Path ftbChaptersDir = configDir.getParent().resolve("ftbquests").resolve("quests")
                .resolve("chapters");
        java.nio.file.Path ftbPlayerSaveFile = server
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("ftbquests").resolve(sp.getStringUUID() + ".snbt");

        if (!java.nio.file.Files.exists(ftbPlayerSaveFile)) {
            ctx.getSource().sendFailure(Component.literal(
                    "No FTB Quests save found for " + sp.getName().getString() + " at §7" + ftbPlayerSaveFile));
            return 0;
        }

        String playerUuidHex = sp.getUUID().toString().replace("-", "");
        final net.minecraft.server.level.ServerPlayer fsp = sp;

        var resultHolder = new Object() {

            net.phoenixvine.chronicles.capability.importer.FtbProgressImporter.ImportResult result;
        };
        QuestCapabilityProvider.of(fsp).ifPresent(data -> {
            resultHolder.result = net.phoenixvine.chronicles.capability.importer.FtbProgressImporter
                    .importProgress(ftbChaptersDir, ftbPlayerSaveFile, playerUuidHex, data);

            QuestProgressTracker.autoUnlockSatisfiedQuests(fsp);

            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(fsp,
                    new S2CSyncPlayerProgressPacket(data, true));
        });

        var result = resultHolder.result;
        if (result == null) {
            ctx.getSource().sendFailure(Component.literal("No quest capability available for that player."));
            return 0;
        }

        String name = fsp.getName().getString();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a✔ FTB progress import for " + name + ": §f" + result.ported() + " §aquest(s) newly completed, §7" +
                        result.alreadyComplete() + " §aalready complete, §f" + result.claimedRewards() +
                        " §areward(s) marked claimed" +
                        (result.notPortable() > 0 ? " §e(" + result.notPortable() +
                                " completed FTB quest(s) had no Chronicles " + "counterpart and couldn't be ported)" :
                                "")),
                true);
        for (String w : result.warnings()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e⚠ " + w), false);
        }
        return result.ported();
    }

    private static int doRewardTableImport(
                                           com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                           Integer pick) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer sp = ctx.getSource().getPlayerOrException();
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "table");
        if (!RewardTableChest.isValidId(id)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Table ids can use letters, digits, '_', '-' and '.', and must not start with '.'."));
            return 0;
        }
        MinecraftServer server = ChronicleEvents.getCachedServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("Server not available."));
            return 0;
        }
        net.minecraft.world.Container box = RewardTableChest.targetContainer(sp);
        if (box == null) {
            ctx.getSource()
                    .sendFailure(Component.literal("Look at a chest, barrel, or other container (within 6 blocks)."));
            return 0;
        }
        RewardTableChest.Result r = RewardTableChest.importInto(ChronicleEvents.resolveConfigDir(server), id, pick,
                box);
        if (!r.ok()) {
            ctx.getSource().sendFailure(Component.literal(r.message()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a✔ " + r.message()), true);
        return 1;
    }

    private static int doImport(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                String subfolder) {
        MinecraftServer server = ChronicleEvents.getCachedServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("Server not available."));
            return 0;
        }
        java.nio.file.Path importDir = ChronicleEvents.resolveConfigDir(server).resolve(subfolder);
        if (!java.nio.file.Files.exists(importDir)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Import folder not found: §7" + importDir + "\n§cCreate it and place .snbt files inside."));
            return 0;
        }
        int before = QuestTreeRegistry.getAllQuests().size();
        QuestFileLoader.loadAdditiveFromDisk(importDir);
        int added = QuestTreeRegistry.getAllQuests().size() - before;
        net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket syncPacket = new net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket(
                QuestTreeRegistry.getAllQuests(), server);
        int playerCount = 0;
        for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, syncPacket);
            playerCount++;
        }
        final int fp = playerCount;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a✔ Imported §f" + added + " §anew quest(s) from §7" + importDir +
                        (fp > 0 ? " §aand synced to §f" + fp + " §aplayer(s)." : ".")),
                true);
        return added;
    }

    private static boolean hasSnbtFiles(java.nio.file.Path dir) {
        if (!java.nio.file.Files.isDirectory(dir)) return false;
        try (var stream = java.nio.file.Files.list(dir)) {
            return stream.anyMatch(p -> !java.nio.file.Files.isDirectory(p) &&
                    p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".snbt"));
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static java.nio.file.Path resolveFtbQuestsChaptersDir(MinecraftServer server) {
        try {
            java.nio.file.Path worldSpecific = server
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("ftbquests").resolve("quests").resolve("chapters");
            if (java.nio.file.Files.isDirectory(worldSpecific)) return worldSpecific;
        } catch (Exception ignored) {}
        return server.getServerDirectory().resolve("config").resolve("ftbquests").resolve("quests")
                .resolve("chapters");
    }

    private static int doImportFtb(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                                   String subfolder) {
        MinecraftServer server = ChronicleEvents.getCachedServer();
        if (server == null) {
            ctx.getSource().sendFailure(Component.literal("Server not available."));
            return 0;
        }
        java.nio.file.Path configDir = ChronicleEvents.resolveConfigDir(server);
        java.nio.file.Path resolvedImportDir = configDir.resolve(subfolder);

        if ("ftb_import".equals(subfolder)) {
            java.nio.file.Path autoDetected = resolveFtbQuestsChaptersDir(server);
            resolvedImportDir = hasSnbtFiles(autoDetected) ? autoDetected : resolvedImportDir;
        }
        final java.nio.file.Path importDir = resolvedImportDir;

        if (!java.nio.file.Files.exists(importDir)) {
            ctx.getSource().sendFailure(Component.literal(
                    "FTB import folder not found: §7" + importDir +
                            "\n§7Place FTB Quests chapter .snbt files inside and run this command again."));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7Importing FTB Quests from §f" + importDir + "§7 - this runs in the background, hang on..."), true);

        Thread importThread = new Thread(() -> {
            long tImportStart = System.currentTimeMillis();
            net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter.ImportResult result;
            try {
                result = net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter.importDirectory(importDir,
                        configDir);
            } catch (Exception e) {
                server.execute(
                        () -> ctx.getSource().sendFailure(Component.literal("§cFTB import failed: " + e.getMessage())));
                return;
            }
            long tImportDone = System.currentTimeMillis();
            System.out.println(
                    "[PhoenixChronicles] Import (file conversion) took " + (tImportDone - tImportStart) + "ms");
            final net.phoenixvine.chronicles.capability.importer.FtbQuestsImporter.ImportResult r = result;
            server.execute(() -> {
                long tReloadStart = System.currentTimeMillis();

                QuestTreeRegistry.clearConfigQuests();
                ChapterFlagRegistry.load(configDir);
                ChapterPrereqDefaults.load(configDir);
                ChapterEmergencyItems.load(configDir);
                QuestEngineConfig.load(configDir);
                RewardTableRegistry.load(configDir);
                CategoryRegistry.load(configDir);
                QuestFileLoader.loadAdditiveFromDisk(configDir);
                long tReloadDone = System.currentTimeMillis();
                System.out
                        .println("[PhoenixChronicles] Registry reload (on main thread) took " +
                                (tReloadDone - tReloadStart) +
                                "ms" + " (queued " + (tReloadStart - tImportDone) + "ms after import finished)");

                List<String> loadErrors = List.copyOf(QuestFileLoader.LOAD_ERRORS);

                boolean localReload = server.isSingleplayer();
                int playerCount = 0;
                if (localReload) {
                    net.phoenixvine.chronicles.network.packet.S2CReloadQuestsFromDiskPacket reloadPacket = new net.phoenixvine.chronicles.network.packet.S2CReloadQuestsFromDiskPacket();
                    for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, reloadPacket);
                        playerCount++;
                    }
                } else {
                    net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket syncPacket = new net.phoenixvine.chronicles.network.packet.S2CSyncQuestsPacket(
                            QuestTreeRegistry.getAllQuests(), server);
                    for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, syncPacket);
                        playerCount++;
                    }
                }
                long tSyncDone = System.currentTimeMillis();
                System.out.println(
                        "[PhoenixChronicles] Player sync/notify (" +
                                (localReload ? "local reload signal" : "full packet") +
                                ") took " + (tSyncDone - tReloadDone) + "ms");
                final int fp = playerCount;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a✔ FTB import: §f" + r.imported() + " §aconverted, §c" + r.skipped() + " §askipped" +
                                (r.category().isEmpty() ? "" : " §8(chapter: §7" + r.category() + "§8)") +
                                (fp > 0 ? " §8· §asynced to §f" + fp + " §aplayer(s)" : "")),
                        true);
                for (String w : r.warnings()) {
                    ctx.getSource().sendSuccess(() -> Component.literal("§e⚠ " + w), false);
                }

                if (!loadErrors.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§c⚠ " + loadErrors.size() + " issue(s) found after reload:"), false);
                    for (String err : loadErrors) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§c  • " + err), false);
                    }
                }
            });
        }, "phoenix-chronicles-ftb-import");
        importThread.setDaemon(true);
        importThread.setPriority(Thread.MIN_PRIORITY);
        importThread.start();
        return 1;
    }
}

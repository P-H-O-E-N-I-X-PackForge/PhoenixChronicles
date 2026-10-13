package net.phoenixvine.chronicles.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.phoenixvine.chronicles.network.packet.*;

public class ChronicleNetwork {

    private static final String PROTOCOL = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(C2SAcknowledgeInfoTasksPacket.TYPE, C2SAcknowledgeInfoTasksPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SBulkQuestActionPacket.TYPE, C2SBulkQuestActionPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SClaimAllRewardsPacket.TYPE, C2SClaimAllRewardsPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SClaimQuestRewardPacket.TYPE, C2SClaimQuestRewardPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SCompleteCheckmarkTaskPacket.TYPE, C2SCompleteCheckmarkTaskPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SPhantasiaTaskCompletePacket.TYPE, C2SPhantasiaTaskCompletePacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SReopenCompletedQuestPacket.TYPE, C2SReopenCompletedQuestPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SRequestEmergencyItemsPacket.TYPE, C2SRequestEmergencyItemsPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SResolveChoiceBoxPacket.TYPE, C2SResolveChoiceBoxPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SScreenOpenedTaskPacket.TYPE, C2SScreenOpenedTaskPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SSetAutoClaimPacket.TYPE, C2SSetAutoClaimPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SSetFilterTokenPacket.TYPE, C2SSetFilterTokenPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SSetQuestStatePacket.TYPE, C2SSetQuestStatePacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2SSubmitItemTaskPacket.TYPE, C2SSubmitItemTaskPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToServer(C2STogglePinPacket.TYPE, C2STogglePinPacket.STREAM_CODEC, (msg, ctx) -> msg.handle(ctx));
        registrar.playToClient(S2COpenExternalScreenPacket.TYPE, S2COpenExternalScreenPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToClient(S2CReloadQuestsFromDiskPacket.TYPE, S2CReloadQuestsFromDiskPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToClient(S2CSyncPlayerProgressPacket.TYPE, S2CSyncPlayerProgressPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToClient(S2CSyncPooledProgressPacket.TYPE, S2CSyncPooledProgressPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToClient(S2CSyncQuestsPacket.TYPE, S2CSyncQuestsPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        registrar.playToClient(S2CSyncTeamKeyPacket.TYPE, S2CSyncTeamKeyPacket.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
    }
}

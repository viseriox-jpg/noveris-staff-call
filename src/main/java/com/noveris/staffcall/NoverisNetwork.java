package com.noveris.staffcall;

import net.minecraft.server.level.ServerPlayer;
import com.noveris.staffcall.client.NoverisClientEvents;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class NoverisNetwork {
    private NoverisNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SubmitPlayerCallPayload.TYPE, SubmitPlayerCallPayload.STREAM_CODEC,
                NoverisNetwork::handleSubmit);
        registrar.playToClient(OpenPlayerCallScreenPayload.TYPE, OpenPlayerCallScreenPayload.STREAM_CODEC,
                NoverisClientEvents::handleOpenScreen);
        registrar.playToClient(PlayerCallStatusPayload.TYPE, PlayerCallStatusPayload.STREAM_CODEC,
                NoverisClientEvents::handleStatus);
    }

    private static void handleSubmit(SubmitPlayerCallPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NoverisStaffCall.EVENTS.submitPlayerCall(player, payload.callType(), payload.reason());
        }
    }
}

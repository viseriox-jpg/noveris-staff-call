package com.noveris.staffcall;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class NoverisNetwork {
    private NoverisNetwork() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(SubmitPlayerCallPayload.TYPE, SubmitPlayerCallPayload.STREAM_CODEC,
                NoverisNetwork::handleSubmit);
        registrar.playToServer(NoveLiveBookRequestPayload.TYPE, NoveLiveBookRequestPayload.STREAM_CODEC,
                NoverisNetwork::handleBookRequest);
        registrar.playToClient(OpenPlayerCallScreenPayload.TYPE, OpenPlayerCallScreenPayload.STREAM_CODEC,
                NoverisNetwork::handleOpenScreen);
        registrar.playToClient(PlayerCallStatusPayload.TYPE, PlayerCallStatusPayload.STREAM_CODEC,
                NoverisNetwork::handleStatus);
        registrar.playToClient(NoveLiveBookPayload.TYPE_ID, NoveLiveBookPayload.STREAM_CODEC,
                NoverisNetwork::handleBook);
    }

    private static void handleSubmit(SubmitPlayerCallPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NoverisStaffCall.EVENTS.submitPlayerCall(player, payload.callType(), payload.reason());
        }
    }

    private static void handleBookRequest(NoveLiveBookRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) NoveLiveBookRequestPayload.sendBook(player);
    }

    private static void handleOpenScreen(OpenPlayerCallScreenPayload payload, IPayloadContext context) {
        invokeClient("handleOpenScreen", OpenPlayerCallScreenPayload.class, payload, context);
    }

    private static void handleStatus(PlayerCallStatusPayload payload, IPayloadContext context) {
        invokeClient("handleStatus", PlayerCallStatusPayload.class, payload, context);
    }

    private static void handleBook(NoveLiveBookPayload payload, IPayloadContext context) {
        invokeClient("handleNoveLiveBook", NoveLiveBookPayload.class, payload, context);
    }

    private static void invokeClient(String method, Class<?> payloadType, Object payload, IPayloadContext context) {
        try {
            Class<?> handler = Class.forName("com.noveris.staffcall.client.NoverisClientEvents");
            handler.getMethod(method, payloadType, IPayloadContext.class).invoke(null, payload, context);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Não foi possível encaminhar o pacote para o cliente", exception);
        }
    }
}

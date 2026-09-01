package com.noveris.staffcall;

import com.noveris.staffcall.novelive.NoveLiveEffects;
import com.noveris.staffcall.novelive.NoveLiveManager;
import com.noveris.staffcall.novelive.SoulChangeType;
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
        registrar.playToServer(NoveLiveAdminRequestPayload.TYPE, NoveLiveAdminRequestPayload.STREAM_CODEC,
                NoverisNetwork::handleAdminRequest);
        registrar.playToServer(NoveLiveAdminActionPayload.TYPE, NoveLiveAdminActionPayload.STREAM_CODEC,
                NoverisNetwork::handleAdminAction);
        registrar.playToClient(OpenPlayerCallScreenPayload.TYPE, OpenPlayerCallScreenPayload.STREAM_CODEC,
                NoverisNetwork::handleOpenScreen);
        registrar.playToClient(PlayerCallStatusPayload.TYPE, PlayerCallStatusPayload.STREAM_CODEC,
                NoverisNetwork::handleStatus);
        registrar.playToClient(NoveLiveBookPayload.TYPE_ID, NoveLiveBookPayload.STREAM_CODEC,
                NoverisNetwork::handleBook);
        registrar.playToClient(NoveLiveAdminPayload.TYPE_ID, NoveLiveAdminPayload.STREAM_CODEC,
                NoverisNetwork::handleAdminPanel);
    }

    private static void handleSubmit(SubmitPlayerCallPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NoverisStaffCall.EVENTS.submitPlayerCall(player, payload.callType(), payload.reason());
        }
    }

    private static void handleBookRequest(NoveLiveBookRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) NoveLiveBookRequestPayload.sendDestination(player, player);
    }

    private static boolean canAdmin(ServerPlayer player) {
        return player.createCommandSourceStack().hasPermission(
                NoverisConfig.load(player.getServer()).permissionNoveLiveAdmin);
    }

    private static void handleAdminRequest(NoveLiveAdminRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && canAdmin(player)) {
            NoveLiveAdminPayload.send(player, payload.selectedPlayerId(), "");
        }
    }

    private static void handleAdminAction(NoveLiveAdminActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer staff) || !canAdmin(staff)) return;
        NoveLiveManager manager = NoveLiveManager.INSTANCE;
        String feedback;
        if (payload.action().equals("CONFIRM")) {
            NoveLiveManager.ConfirmResult result = manager.confirm(staff.getServer(), payload.ruptureId(), staff.getName().getString());
            feedback = result == NoveLiveManager.ConfirmResult.SUCCESS ? "Ruptura confirmada." : switch (result) {
                case NOT_FOUND -> "Ruptura não encontrada.";
                case ALREADY_RESOLVED -> "Essa ruptura já foi julgada.";
                case NO_FRAGMENTS -> "A alma já está desfeita.";
                default -> "Não foi possível confirmar.";
            };
            NoveLiveEffects.refreshAll(staff.getServer());
        } else if (payload.action().equals("REJECT")) {
            feedback = manager.reject(staff.getServer(), payload.ruptureId(), staff.getName().getString(),
                    "Dispensada pelo painel administrativo") ? "Ruptura rejeitada." : "Essa ruptura já foi julgada.";
        } else if (payload.action().equals("CLEAR_HISTORY")) {
            try {
                int removed = manager.clearHistory(staff.getServer(), java.util.UUID.fromString(payload.playerId()));
                feedback = removed + " registro(s) apagado(s) do histórico.";
            } catch (IllegalArgumentException exception) {
                feedback = "Alma inválida.";
            }
        } else {
            ServerPlayer target;
            try { target = staff.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(payload.playerId())); }
            catch (IllegalArgumentException exception) { target = null; }
            if (target == null) {
                NoveLiveAdminPayload.send(staff, "", "O jogador não está mais online.");
                return;
            }
            int current = manager.soul(staff.getServer(), target).fragments();
            int requested;
            SoulChangeType type;
            switch (payload.action()) {
                case "ADD" -> { requested = current + Math.clamp(payload.amount(), 1, 5); type = SoulChangeType.RESTAURACAO_ADMIN; }
                case "REMOVE" -> { requested = current - Math.clamp(payload.amount(), 1, 5); type = SoulChangeType.REMOCAO_ADMIN; }
                case "SET" -> { requested = Math.clamp(payload.amount(), 0, 3); type = SoulChangeType.DEFINICAO_ADMIN; }
                default -> { NoveLiveAdminPayload.send(staff, payload.playerId(), "Ação inválida."); return; }
            }
            NoveLiveManager.ChangeResult result = manager.change(staff.getServer(), target, requested, type,
                    staff.getName().getString(), "Alteração pelo painel administrativo");
            NoveLiveEffects.refresh(target);
            NoveLiveEffects.administrativeChange(target, type, result.before(), result.after(),
                    result.reservesBefore(), result.reservesAfter());
            feedback = target.getName().getString() + ": " + result.before() + "/3 + " + result.reservesBefore()
                    + "R → " + result.after() + "/3 + " + result.reservesAfter() + "R.";
        }
        NoveLiveAdminPayload.refreshAdmins(staff.getServer());
        NoveLiveAdminPayload.send(staff, payload.playerId(), feedback, true);
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

    private static void handleAdminPanel(NoveLiveAdminPayload payload, IPayloadContext context) {
        invokeClient("handleNoveLiveAdmin", NoveLiveAdminPayload.class, payload, context);
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

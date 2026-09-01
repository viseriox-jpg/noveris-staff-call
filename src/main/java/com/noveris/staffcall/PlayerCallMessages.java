package com.noveris.staffcall;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class PlayerCallMessages {
    private PlayerCallMessages() { }

    static Component requestSent(PlayerCallType type) {
        return box(type, type == PlayerCallType.RP ? "◆ AUDIÊNCIA ENVIADA" : "⚠ SUPORTE ENVIADO")
                .append(text(type == PlayerCallType.RP ? "A equipe foi convocada." : "Solicitação enviada à equipe."))
                .append(warning("Expira em 5 minutos."))
                .append(actions())
                .append(Component.literal("[CANCELAR]").withStyle(style -> style
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novecall cancelar"))));
    }

    static Component staffAlert(String player, PlayerCallType type, String reason) {
        return box(type, type == PlayerCallType.RP ? "◆ AUDIÊNCIA • RP" : "⚠ SUPORTE • OFF-RP")
                .append(Component.literal("\n" + player).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(": " + reason).withStyle(ChatFormatting.WHITE))
                .append(warning("Expira em 5 minutos."))
                .append(actions())
                .append(Component.literal("[ATENDER]").withStyle(style -> style
                        .withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/novecall atender " + player))))
                .append(Component.literal("   "))
                .append(Component.literal("[RECUSAR]").withStyle(style -> style
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                "/novecall recusar " + player + " "))));
    }

    static Component acceptedForPlayer(PlayerCallType type, String staff) {
        return box(type, type == PlayerCallType.RP ? "◆ AUDIÊNCIA CONCEDIDA" : "◆ SUPORTE ACEITO")
                .append(name("Staff", staff))
                .append(text(type == PlayerCallType.RP ? "O emissário atravessa o Véu." : "A staff está a caminho."));
    }

    static Component arrivalForPlayer(PlayerCallType type, String staff) {
        return box(type, type == PlayerCallType.RP ? "◆ O EMISSÁRIO CHEGOU" : "◆ STAFF NO LOCAL")
                .append(name("Staff", staff))
                .append(text(type == PlayerCallType.RP ? "A audiência começou." : "Atendimento técnico iniciado."));
    }

    static Component arrivalForStaff(PlayerCallType type, String player, String reason) {
        return box(type, type == PlayerCallType.RP ? "◆ AUDIÊNCIA INICIADA" : "◆ ATENDIMENTO INICIADO")
                .append(name("Jogador", player)).append(text("Motivo: " + reason))
                .append(hint("Conclua com /novecall concluir " + player));
    }

    static Component unsafe(PlayerCallType type) {
        return box(type, "⚠ DESTINO INSEGURO")
                .append(text("Não foi possível teleportar a staff."))
                .append(warning("O chamado voltou à fila por 5 minutos."));
    }

    static Component refused(PlayerCallType type, String reason) {
        return box(type, "⚠ CHAMADO RECUSADO").append(text("Motivo: " + reason))
                .append(warning("Tente novamente em 5 minutos."));
    }

    static Component cancelled(PlayerCallType type, String reason) {
        return box(type, "⚠ CHAMADO ENCERRADO").append(text("Motivo: " + reason));
    }

    static Component concluded(PlayerCallType type, String staff) {
        return box(type, type == PlayerCallType.RP ? "◆ AUDIÊNCIA ENCERRADA" : "◆ ATENDIMENTO CONCLUÍDO")
                .append(name("Staff", staff)).append(text("Atendimento finalizado."));
    }

    static Component transferred(PlayerCallType type, String staff) {
        return box(type, "◆ RESPONSÁVEL ALTERADO").append(name("Nova staff", staff))
                .append(text("A nova responsável está a caminho."));
    }

    static Component reopened(PlayerCallType type, String staff) {
        return box(type, "◆ ATENDIMENTO REABERTO").append(name("Staff", staff))
                .append(text("Uma nova travessia foi iniciada."));
    }

    static Component expirationWarning(PlayerCallType type) {
        return box(type, "⚠ AVISO DE TEMPO").append(warning("Restam 5 minutos."))
                .append(text("Conclua o atendimento antes da expiração."));
    }

    static Component expired(PlayerCallType type) {
        return box(type, "⚠ TEMPO ESGOTADO").append(text("O atendimento atingiu 30 minutos."))
                .append(text("Encerrado automaticamente."));
    }

    static Component info(String player, PlayerCallType type, String status, String staff, String reason) {
        return box(type, "◆ CHAMADO • " + type.label).append(name("Jogador", player))
                .append(text("Status: " + status + " • Staff: " + staff))
                .append(text("Motivo: " + reason));
    }

    private static MutableComponent box(PlayerCallType type, String title) {
        ChatFormatting color = type == PlayerCallType.RP ? ChatFormatting.GOLD : ChatFormatting.RED;
        return Component.literal(title).withStyle(color, ChatFormatting.BOLD);
    }

    private static MutableComponent name(String label, String value) {
        return Component.literal("\n" + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
    }

    private static MutableComponent text(String value) {
        return Component.literal("\n" + value).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent warning(String value) {
        return Component.literal("\n" + value).withStyle(ChatFormatting.YELLOW);
    }

    private static MutableComponent hint(String value) {
        return Component.literal("\n" + value).withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent actions() {
        return Component.literal("\n\n");
    }
}

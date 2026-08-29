package com.noveris.staffcall;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class PlayerCallMessages {
    private PlayerCallMessages() { }

    static Component requestSent(PlayerCallType type) {
        return box(type, type == PlayerCallType.RP ? "◆  ARAUTO ENVIADO" : "◆  CHAMADO OFF-RP ENVIADO")
                .append(field("Status", type == PlayerCallType.RP
                        ? "A equipe foi convocada para atender ao seu chamado."
                        : "Solicitação encaminhada à equipe disponível."))
                .append(field("Prazo", "5 minutos"))
                .append(line())
                .append(Component.literal("[CANCELAR CHAMADO]").withStyle(style -> style
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novecall cancelar"))));
    }

    static Component staffAlert(String player, PlayerCallType type, String reason) {
        return box(type, type == PlayerCallType.RP ? "◆  PEDIDO DE AUDIÊNCIA" : "⚠  SOLICITAÇÃO OFF-RP")
                .append(field("Jogador", player))
                .append(field("Categoria", type.label))
                .append(field("Motivo", reason))
                .append(line())
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
        return box(type, type == PlayerCallType.RP ? "◆  AUDIÊNCIA CONCEDIDA" : "◆  CHAMADO ACEITO")
                .append(field("Responsável", staff))
                .append(field("Status", type == PlayerCallType.RP
                        ? "O emissário atravessa o Véu em sua direção."
                        : "Teleporte seguro da staff iniciado."));
    }

    static Component arrivalForPlayer(PlayerCallType type, String staff) {
        return box(type, type == PlayerCallType.RP ? "◆  O EMISSÁRIO CHEGOU" : "◆  STAFF NO LOCAL")
                .append(field("Responsável", staff))
                .append(field("Status", type == PlayerCallType.RP
                        ? "A travessia foi concluída. A audiência começou."
                        : "Destino confirmado. Atendimento técnico iniciado."));
    }

    static Component arrivalForStaff(PlayerCallType type, String player, String reason) {
        return box(type, type == PlayerCallType.RP ? "◆  AUDIÊNCIA INICIADA" : "◆  ATENDIMENTO INICIADO")
                .append(field("Jogador", player)).append(field("Motivo", reason))
                .append(field("Ação", "Use /novecall concluir " + player + " ao finalizar."));
    }

    static Component unsafe(PlayerCallType type) {
        return box(type, "⚠  DESTINO INSEGURO")
                .append(field("Falha", "Não foi possível encontrar uma área segura para a staff."))
                .append(field("Status", "O chamado voltou à fila por mais 5 minutos."));
    }

    static Component refused(PlayerCallType type, String reason) {
        return box(type, "⚠  CHAMADO RECUSADO").append(field("Motivo", reason))
                .append(field("Nova tentativa", "Disponível em 5 minutos."));
    }

    static Component cancelled(PlayerCallType type, String reason) {
        return box(type, "⚠  CHAMADO ENCERRADO").append(field("Motivo", reason));
    }

    static Component concluded(PlayerCallType type, String staff) {
        return box(type, type == PlayerCallType.RP ? "◆  AUDIÊNCIA ENCERRADA" : "◆  ATENDIMENTO CONCLUÍDO")
                .append(field("Responsável", staff)).append(field("Status", "Atendimento finalizado."));
    }

    static Component transferred(PlayerCallType type, String staff) {
        return box(type, "◆  RESPONSÁVEL ALTERADO").append(field("Nova staff", staff))
                .append(field("Status", "A nova responsável está a caminho."));
    }

    static Component reopened(PlayerCallType type, String staff) {
        return box(type, "◆  ATENDIMENTO REABERTO").append(field("Responsável", staff))
                .append(field("Status", "Uma nova travessia foi iniciada."));
    }

    static Component expirationWarning(PlayerCallType type) {
        return box(type, "⚠  AVISO DE TEMPO").append(field("Restante", "5 minutos"))
                .append(field("Ação", "Conclua o atendimento antes da expiração."));
    }

    static Component expired(PlayerCallType type) {
        return box(type, "⚠  TEMPO ESGOTADO").append(field("Duração", "30 minutos"))
                .append(field("Status", "O atendimento foi encerrado automaticamente."));
    }

    static Component info(String player, PlayerCallType type, String status, String staff, String reason) {
        return box(type, "◆  INFORMAÇÕES DO CHAMADO").append(field("Jogador", player))
                .append(field("Categoria", type.label)).append(field("Status", status))
                .append(field("Responsável", staff)).append(field("Motivo", reason));
    }

    private static MutableComponent box(PlayerCallType type, String title) {
        ChatFormatting color = type == PlayerCallType.RP ? ChatFormatting.GOLD : ChatFormatting.RED;
        return Component.literal(title).withStyle(color, ChatFormatting.BOLD).append(line());
    }

    private static MutableComponent field(String label, String value) {
        return Component.literal("\n" + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static MutableComponent line() {
        return Component.literal("\n────────────────────").withStyle(ChatFormatting.DARK_GRAY);
    }
}

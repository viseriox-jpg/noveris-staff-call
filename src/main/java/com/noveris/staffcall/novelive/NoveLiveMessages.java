package com.noveris.staffcall.novelive;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

final class NoveLiveMessages {
    private NoveLiveMessages() { }

    static Component modeEnabled() {
        return Component.literal("✦ O VÉU SE ENFRAQUECE ✦\n").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("A morte agora alcança além da carne.\n").withStyle(ChatFormatting.RED))
                .append(Component.literal("Aqueles alcançados por seu olhar poderão não retornar inteiros.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    static Component modeDisabled() {
        return Component.literal("◆ O VÉU TORNA A SE FECHAR ◆\n").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("As almas já não estão expostas à ruptura.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    static Component marked() {
        return Component.literal("⚠ UMA PRESENÇA ALÉM DO VÉU VOLTOU SEU OLHAR PARA VOCÊ\n")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Sua essência foi reconhecida. Se você tombar, seu destino atravessará o Limiar.")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
    }

    static Component unmarked() {
        return Component.literal("◆ O olhar além do Véu se afastou. Sua essência voltou a caminhar sem julgamento.")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
    }

    static Component awaitingJudgment() {
        return Component.literal("◆ SUA ESSÊNCIA ATRAVESSOU OS LIMITES DO MUNDO DOS VIVOS\n")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Diante do Trono Velado, ela aguarda um veredito.")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
    }

    static Component judgmentReleased() {
        return Component.literal("◆ O JULGAMENTO CESSOU\n").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("Sua essência foi devolvida intacta ao mundo dos vivos.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    static Component rupture(String name, int fragments) {
        String symbols = symbols(fragments);
        return Component.literal("✦ ALGO SE ROMPEU ALÉM DO MUNDO DOS VIVOS ✦\n")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("A alma de ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(name).withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(SoulState.fromFragments(fragments).label)))))
                .append(Component.literal(" perdeu parte de sua essência.\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(symbols).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
    }

    static Component soulDestroyed(String name) {
        return Component.literal("☠ O ÚLTIMO FRAGMENTO SE PARTIU ☠\n").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("A essência de ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(name).withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                .append(Component.literal(" deixou de responder ao mundo dos vivos.\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("◇ ◇ ◇ ◇ — Alma Desfeita").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
    }

    static Component restored(int fragments) {
        return Component.literal("✦ UMA CENTELHA ATRAVESSOU O LIMIAR\n").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("Uma parte perdida de sua essência retornou.\n").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                .append(Component.literal(symbols(fragments) + " — " + SoulState.fromFragments(fragments).label)
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }

    static Component diminished(int fragments) {
        return Component.literal("◆ O VÉU TOCOU SUA ESSÊNCIA\n").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Algo arrancou uma parcela de sua alma.\n").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC))
                .append(Component.literal(symbols(fragments) + " — " + SoulState.fromFragments(fragments).label)
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    static Component realigned(int fragments) {
        return Component.literal("◆ A PRESENÇA REORDENOU SUA ESSÊNCIA\n").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("Seu reflexo diante do Véu assumiu uma nova forma.\n").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))
                .append(Component.literal(symbols(fragments) + " — " + SoulState.fromFragments(fragments).label)
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }

    static String symbols(int fragments) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < 4; index++) {
            if (index > 0) value.append(' ');
            value.append(index < fragments ? '◆' : '◇');
        }
        return value.toString();
    }
}

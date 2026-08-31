package com.noveris.staffcall.novelive;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

final class NoveLiveMessages {
    private NoveLiveMessages() { }

    static Component modeEnabled() {
        return Component.literal("✦ O VÉU SE ENFRAQUECE ✦\n").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("A morte agora alcança além da carne.\n").withStyle(ChatFormatting.RED))
                .append(Component.literal("Aqueles expostos ao cânone poderão sentir sua essência se partir.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    static Component modeDisabled() {
        return Component.literal("◆ O VÉU TORNA A SE FECHAR ◆\n").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("As almas já não estão expostas à ruptura.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }

    static Component marked() {
        return Component.literal("⚠ SUA ALMA FOI EXPOSTA AO CÂNONE\n").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Se você tombar enquanto o Véu estiver enfraquecido, sua morte será submetida ao julgamento da staff.")
                        .withStyle(ChatFormatting.RED));
    }

    static Component unmarked() {
        return Component.literal("◆ A pressão sobre sua alma desapareceu.")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
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

    static String symbols(int fragments) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < 4; index++) {
            if (index > 0) value.append(' ');
            value.append(index < fragments ? '◆' : '◇');
        }
        return value.toString();
    }
}

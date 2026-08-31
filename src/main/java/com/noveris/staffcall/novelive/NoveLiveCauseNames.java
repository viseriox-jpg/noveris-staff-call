package com.noveris.staffcall.novelive;

import java.util.Locale;

public final class NoveLiveCauseNames {
    private NoveLiveCauseNames() { }

    public static String translate(String cause) {
        if (cause == null || cause.isBlank()) return "Origem desconhecida";
        String value = cause.toLowerCase(Locale.ROOT);
        if (value.contains("fall") || value.contains("stalagmite")) return "Queda fatal";
        if (value.contains("player")) return "Golpe de outra alma";
        if (value.contains("mob") || value.contains("sting") || value.contains("wither")) return "Criatura do mundo";
        if (value.contains("fire") || value.contains("lava") || value.contains("hotfloor")) return "Fogo e chamas";
        if (value.contains("drown")) return "Afogamento";
        if (value.contains("magic") || value.contains("indirectmagic")) return "Força arcana";
        if (value.contains("explosion") || value.contains("fireworks")) return "Explosão";
        if (value.contains("arrow") || value.contains("trident")) return "Projétil";
        if (value.contains("inwall") || value.contains("cramming")) return "Sufocamento";
        if (value.contains("starve")) return "Inanição";
        if (value.contains("lightning")) return "Relâmpago";
        if (value.contains("freeze")) return "Frio extremo";
        if (value.contains("void") || value.contains("outofworld")) return "Consumido pelo vazio";
        return "Origem desconhecida";
    }
}

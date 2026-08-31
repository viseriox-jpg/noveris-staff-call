package com.noveris.staffcall.novelive;

public enum SoulState {
    INTEGRA("Alma Íntegra"),
    FERIDA("Alma Ferida"),
    FRAGMENTADA("Alma Fragmentada"),
    COLAPSO("Alma à Beira do Colapso"),
    DESFEITA("Alma Desfeita");

    public final String label;

    SoulState(String label) { this.label = label; }

    public static SoulState fromFragments(int fragments) {
        return switch (Math.clamp(fragments, 0, 4)) {
            case 4 -> INTEGRA;
            case 3 -> FERIDA;
            case 2 -> FRAGMENTADA;
            case 1 -> COLAPSO;
            default -> DESFEITA;
        };
    }
}

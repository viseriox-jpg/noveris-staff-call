package com.noveris.staffcall;

enum PlayerCallType {
    RP("RP", CallPalette.DOURADO),
    OFF_RP("OFF-RP", CallPalette.VERMELHO);

    final String label;
    final CallPalette palette;

    PlayerCallType(String label, CallPalette palette) {
        this.label = label;
        this.palette = palette;
    }

    static PlayerCallType fromNetwork(String value) {
        return "offrp".equalsIgnoreCase(value) ? OFF_RP : RP;
    }
}

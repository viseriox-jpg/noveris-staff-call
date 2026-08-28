package com.noveris.staffcall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.BossEvent;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Optional;

enum CallPalette {
    DOURADO("dourado", ChatFormatting.GOLD, ChatFormatting.YELLOW,
            BossEvent.BossBarColor.YELLOW,
            new Vector3f(1.0F, 0.62F, 0.08F), new Vector3f(1.0F, 0.9F, 0.25F)),
    VERMELHO("vermelho", ChatFormatting.RED, ChatFormatting.GOLD,
            BossEvent.BossBarColor.RED,
            new Vector3f(1.0F, 0.08F, 0.03F), new Vector3f(1.0F, 0.42F, 0.08F)),
    AZUL("azul", ChatFormatting.AQUA, ChatFormatting.WHITE,
            BossEvent.BossBarColor.BLUE,
            new Vector3f(0.04F, 0.2F, 0.95F), new Vector3f(0.18F, 0.72F, 1.0F)),
    VERDE("verde", ChatFormatting.GREEN, ChatFormatting.YELLOW,
            BossEvent.BossBarColor.GREEN,
            new Vector3f(0.08F, 0.85F, 0.18F), new Vector3f(0.62F, 1.0F, 0.2F)),
    ROXO("roxo", ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE,
            BossEvent.BossBarColor.PURPLE,
            new Vector3f(0.65F, 0.12F, 1.0F), new Vector3f(1.0F, 0.28F, 0.9F)),
    BRANCO("branco", ChatFormatting.WHITE, ChatFormatting.GRAY,
            BossEvent.BossBarColor.WHITE,
            new Vector3f(1.0F, 1.0F, 1.0F), new Vector3f(0.72F, 0.82F, 0.9F)),
    LARANJA("laranja", ChatFormatting.GOLD, ChatFormatting.RED,
            BossEvent.BossBarColor.RED,
            new Vector3f(1.0F, 0.28F, 0.02F), new Vector3f(1.0F, 0.62F, 0.05F)),
    ROSA("rosa", ChatFormatting.LIGHT_PURPLE, ChatFormatting.WHITE,
            BossEvent.BossBarColor.PINK,
            new Vector3f(1.0F, 0.18F, 0.58F), new Vector3f(1.0F, 0.62F, 0.82F)),
    CIANO("ciano", ChatFormatting.AQUA, ChatFormatting.WHITE,
            BossEvent.BossBarColor.BLUE,
            new Vector3f(0.0F, 0.82F, 0.82F), new Vector3f(0.48F, 1.0F, 1.0F)),
    CINZA("cinza", ChatFormatting.GRAY, ChatFormatting.WHITE,
            BossEvent.BossBarColor.WHITE,
            new Vector3f(0.38F, 0.42F, 0.48F), new Vector3f(0.76F, 0.8F, 0.86F));

    final String id;
    final ChatFormatting primaryText;
    final ChatFormatting accentText;
    final BossEvent.BossBarColor bossBarColor;
    final DustParticleOptions primaryDust;
    final DustParticleOptions accentDust;

    CallPalette(String id, ChatFormatting primaryText, ChatFormatting accentText,
                BossEvent.BossBarColor bossBarColor, Vector3f primaryRgb, Vector3f accentRgb) {
        this.id = id;
        this.primaryText = primaryText;
        this.accentText = accentText;
        this.bossBarColor = bossBarColor;
        this.primaryDust = new DustParticleOptions(primaryRgb, 0.8F);
        this.accentDust = new DustParticleOptions(accentRgb, 0.6F);
    }

    static Optional<CallPalette> fromName(String name) {
        return Arrays.stream(values()).filter(palette -> palette.id.equalsIgnoreCase(name)).findFirst();
    }

    static String[] names() {
        return Arrays.stream(values()).map(palette -> palette.id).toArray(String[]::new);
    }
}

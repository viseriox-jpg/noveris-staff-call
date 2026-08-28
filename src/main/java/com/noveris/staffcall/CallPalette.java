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
    AZUL("azul", ChatFormatting.AQUA, ChatFormatting.BLUE,
            BossEvent.BossBarColor.BLUE,
            new Vector3f(0.08F, 0.42F, 1.0F), new Vector3f(0.2F, 0.85F, 1.0F)),
    VERDE("verde", ChatFormatting.GREEN, ChatFormatting.YELLOW,
            BossEvent.BossBarColor.GREEN,
            new Vector3f(0.08F, 0.85F, 0.18F), new Vector3f(0.62F, 1.0F, 0.2F)),
    ROXO("roxo", ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE,
            BossEvent.BossBarColor.PURPLE,
            new Vector3f(0.65F, 0.12F, 1.0F), new Vector3f(1.0F, 0.28F, 0.9F)),
    BRANCO("branco", ChatFormatting.WHITE, ChatFormatting.GRAY,
            BossEvent.BossBarColor.WHITE,
            new Vector3f(1.0F, 1.0F, 1.0F), new Vector3f(0.72F, 0.82F, 0.9F));

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
        this.primaryDust = new DustParticleOptions(primaryRgb, 1.35F);
        this.accentDust = new DustParticleOptions(accentRgb, 1.0F);
    }

    static Optional<CallPalette> fromName(String name) {
        return Arrays.stream(values()).filter(palette -> palette.id.equalsIgnoreCase(name)).findFirst();
    }

    static String[] names() {
        return Arrays.stream(values()).map(palette -> palette.id).toArray(String[]::new);
    }
}

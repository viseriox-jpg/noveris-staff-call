package com.noveris.staffcall.novelive;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.joml.Vector3f;

public final class NoveLiveEffects {
    private static final DustParticleOptions RED_DUST = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.03F), 0.7F);

    private NoveLiveEffects() { }

    public static void refreshAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(NoveLiveEffects::refresh);
    }

    public static void refresh(ServerPlayer player) {
        int fragments = NoveLiveManager.INSTANCE.soul(player.getServer(), player).fragments();
        if (fragments == 1) {
            MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (current == null || current.getAmplifier() < 1 || current.getDuration() < 60) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1,
                        true, false, true));
            }
        } else {
            MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (current != null && current.getAmplifier() == 1 && current.isAmbient() && !current.isVisible()) {
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
        }
    }

    public static void particles(ServerPlayer player, long tick) {
        int fragments = NoveLiveManager.INSTANCE.soul(player.getServer(), player).fragments();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (fragments == 1 && tick % 10 == 0) {
            level.sendParticles(RED_DUST, player.getX(), player.getY() + 1.0, player.getZ(),
                    2, 0.32, 0.55, 0.32, 0.01);
            if (tick % 40 == 0) level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.8,
                    player.getZ(), 1, 0.2, 0.35, 0.2, 0.005);
        } else if (fragments == 0 && tick % 20 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.9,
                    player.getZ(), 1, 0.25, 0.45, 0.25, 0.002);
            if (tick % 60 == 0) level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.7,
                    player.getZ(), 1, 0.2, 0.25, 0.2, 0.002);
        }
    }
}

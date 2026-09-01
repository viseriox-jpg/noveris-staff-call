package com.noveris.staffcall.novelive;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    public static void administrativeChange(ServerPlayer player, SoulChangeType type, int before, int after,
                                            int reservesBefore, int reservesAfter) {
        if (reservesAfter > reservesBefore && after == before) player.sendSystemMessage(NoveLiveMessages.reserveStored());
        else if (reservesAfter < reservesBefore && after == before) player.sendSystemMessage(NoveLiveMessages.reserveRemoved());
        else if (after > before) player.sendSystemMessage(NoveLiveMessages.restored(after));
        else if (after < before) player.sendSystemMessage(NoveLiveMessages.diminished(after));
        else player.sendSystemMessage(NoveLiveMessages.realigned(after));
        if (!(player.level() instanceof ServerLevel level)) return;
        if (after > before || reservesAfter > reservesBefore) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
                    18, 0.45, 0.75, 0.45, 0.025);
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 0.8, player.getZ(),
                    24, 0.55, 0.55, 0.55, 0.2);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.9F, 1.15F);
        } else if (after < before || reservesAfter < reservesBefore) {
            level.sendParticles(RED_DUST, player.getX(), player.getY() + 1.0, player.getZ(),
                    16, 0.45, 0.7, 0.45, 0.02);
            level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.9, player.getZ(),
                    7, 0.35, 0.55, 0.35, 0.015);
            level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                    SoundSource.PLAYERS, 0.55F, 0.7F);
        }
    }

    static void confirmed(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        level.sendParticles(RED_DUST, player.getX(), player.getY() + 1.0, player.getZ(),
                22, 0.5, 0.8, 0.5, 0.025);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.4, 0.65, 0.4, 0.015);
    }

    static void rejected(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                18, 0.45, 0.7, 0.45, 0.15);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.8F, 0.9F);
    }
}

package com.noveris.staffcall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class StaffCallManager {
    private final Map<UUID, StaffCallSession> sessions = new HashMap<>();

    boolean hasActiveCall(UUID targetId) {
        return sessions.containsKey(targetId);
    }

    boolean begin(ServerPlayer staff, ServerPlayer target) {
        if (hasActiveCall(target.getUUID())) return false;

        StaffCallSession session = new StaffCallSession(
                staff.getUUID(),
                target.getUUID(),
                target.level().dimension(),
                target.position(),
                target.getYRot(),
                target.getXRot()
        );

        sessions.put(target.getUUID(), session);
        startPresentation(target);
        return true;
    }

    boolean cancel(UUID targetId, MinecraftServer server, boolean notifyTarget) {
        StaffCallSession removed = sessions.remove(targetId);
        if (removed == null) return false;

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (notifyTarget && target != null) {
            target.displayClientMessage(
                    Component.literal("O chamado foi interrompido.")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    false
            );
            target.level().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.6F, 0.7F);
        }
        return true;
    }

    void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, StaffCallSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            StaffCallSession session = iterator.next().getValue();
            ServerPlayer target = server.getPlayerList().getPlayer(session.targetId);
            ServerPlayer staff = server.getPlayerList().getPlayer(session.staffId);

            if (target == null || staff == null || !target.isAlive() || !staff.isAlive()) {
                iterator.remove();
                continue;
            }

            session.age++;
            tickPresentation(target, session);

            if (session.age >= StaffCallSession.LOCK_FROM_TICK) {
                lockTarget(target, session);
            }

            if (session.age >= StaffCallSession.TOTAL_TICKS) {
                complete(target, staff);
                iterator.remove();
            }
        }
    }

    private void startPresentation(ServerPlayer target) {
        target.displayClientMessage(
                Component.literal("Uma presença reclama sua atenção...")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC),
                false
        );
        target.sendSystemMessage(
                Component.literal("[Noveris] Você está sendo convocado pela Staff.")
                        .withStyle(ChatFormatting.DARK_PURPLE)
        );
        target.level().playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.45F, 0.55F);
    }

    private void tickPresentation(ServerPlayer target, StaffCallSession session) {
        if (!(target.level() instanceof ServerLevel level)) return;

        if (session.age % 2 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    8, 0.65, 0.9, 0.65, 0.02);
        }

        if (session.age == 20) {
            target.displayClientMessage(
                    Component.literal("O Véu se abre diante de você.")
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            level.playSound(null, target.blockPosition(), SoundEvents.PORTAL_TRIGGER,
                    SoundSource.PLAYERS, 0.35F, 0.8F);
        }

        if (session.age == 40) {
            target.displayClientMessage(
                    Component.literal("Não resista ao chamado.")
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
            level.sendParticles(ParticleTypes.WITCH,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    35, 0.8, 1.1, 0.8, 0.05);
            level.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_STARE,
                    SoundSource.PLAYERS, 0.45F, 0.65F);
        }

        if (session.age >= 55 && session.age % 5 == 0) {
            level.sendParticles(ParticleTypes.PORTAL,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    24, 0.4, 0.8, 0.4, 0.18);
        }
    }

    private void lockTarget(ServerPlayer target, StaffCallSession session) {
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;

        if (target.level().dimension().equals(session.targetStartDimension)) {
            Vec3 p = session.targetStartPosition;
            target.teleportTo((ServerLevel) target.level(), p.x, p.y, p.z,
                    session.targetStartYaw, session.targetStartPitch);
        }
    }

    private void complete(ServerPlayer target, ServerPlayer staff) {
        ServerLevel destinationLevel = (ServerLevel) staff.level();

        Vec3 look = staff.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() > 1.0E-6) horizontal = horizontal.normalize();
        Vec3 destination = staff.position().add(horizontal.scale(1.75));

        destinationLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                destination.x, destination.y + 1.0, destination.z,
                45, 0.8, 1.1, 0.8, 0.08);

        target.teleportTo(destinationLevel,
                destination.x, destination.y, destination.z,
                staff.getYRot() + 180.0F, target.getXRot());

        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;

        destinationLevel.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.75F, 0.65F);
        destinationLevel.sendParticles(ParticleTypes.WITCH,
                target.getX(), target.getY() + 1.0, target.getZ(),
                55, 0.75, 1.0, 0.75, 0.06);

        target.displayClientMessage(
                Component.literal("Você atravessou o Véu.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
    }
}

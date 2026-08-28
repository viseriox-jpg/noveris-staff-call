package com.noveris.staffcall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class StaffCallManager {
    private final Map<UUID, StaffCallSession> sessions = new HashMap<>();

    boolean hasActiveCall(UUID targetId) {
        return sessions.containsKey(targetId);
    }

    CallPalette getCallPalette(UUID targetId) {
        StaffCallSession session = sessions.get(targetId);
        return session == null ? null : session.palette;
    }

    boolean begin(ServerPlayer staff, ServerPlayer target, CallPalette palette) {
        if (hasActiveCall(target.getUUID())) return false;

        ServerBossEvent progressBar = new ServerBossEvent(
                Component.literal("O decreto se aproxima").withStyle(palette.primaryText),
                palette.bossBarColor,
                BossEvent.BossBarOverlay.PROGRESS
        );
        progressBar.addPlayer(target);

        StaffCallSession session = new StaffCallSession(
                staff.getUUID(),
                target.getUUID(),
                target.level().dimension(),
                target.position(),
                target.getYRot(),
                target.getXRot(),
                progressBar,
                palette
        );

        sessions.put(target.getUUID(), session);
        startPresentation(target, session);
        return true;
    }

    boolean cancel(UUID targetId, MinecraftServer server, boolean notifyTarget) {
        StaffCallSession removed = sessions.remove(targetId);
        if (removed == null) return false;
        removed.progressBar.removeAllPlayers();

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (notifyTarget && target != null) {
            showTitle(target,
                    Component.literal("A VONTADE FOI RECOLHIDA").withStyle(removed.palette.primaryText),
                    Component.literal("A presença além do Véu desviou Seu olhar")
                            .withStyle(removed.palette.accentText),
                    5, 35, 10);
            target.displayClientMessage(
                    Component.literal("[O Chamado] O decreto foi suspenso.")
                            .withStyle(removed.palette.primaryText, ChatFormatting.ITALIC),
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
                session.progressBar.removeAllPlayers();
                iterator.remove();
                continue;
            }

            session.age++;
            session.progressBar.setProgress(Math.max(0.0F,
                    1.0F - (session.age / (float) StaffCallSession.TOTAL_TICKS)));
            tickPresentation(target, session);

            if (session.age >= StaffCallSession.LOCK_FROM_TICK) {
                lockTarget(target, session);
            }

            if (session.age >= StaffCallSession.TOTAL_TICKS) {
                session.progressBar.removeAllPlayers();
                complete(target, staff, session);
                iterator.remove();
            }
        }
    }

    private void startPresentation(ServerPlayer target, StaffCallSession session) {
        CallPalette palette = session.palette;
        showTitle(target,
                Component.literal("A VONTADE O CHAMA")
                        .withStyle(palette.primaryText, ChatFormatting.BOLD),
                Component.literal("Curve-se diante d'Aquele que observa além do Véu")
                        .withStyle(palette.accentText, ChatFormatting.ITALIC),
                10, 60, 10);
        target.displayClientMessage(
                Component.literal("Uma consciência ancestral fixa o olhar sobre sua alma...")
                        .withStyle(palette.accentText, ChatFormatting.ITALIC),
                false
        );
        target.sendSystemMessage(
                Component.literal("[O Chamado] Uma voz sem origem ecoa dentro de sua alma.")
                        .withStyle(palette.primaryText)
        );
        target.level().playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.45F, 0.55F);
    }

    private void tickPresentation(ServerPlayer target, StaffCallSession session) {
        if (!(target.level() instanceof ServerLevel level)) return;
        CallPalette palette = session.palette;

        if (session.age % 2 == 0) {
            level.sendParticles(palette.primaryDust,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    10, 0.65, 0.9, 0.65, 0.01);
        }

        if (session.age == 65) {
            target.displayClientMessage(
                    Component.literal("O Véu se curva. Sua presença foi exigida.")
                            .withStyle(palette.accentText), true);
            level.playSound(null, target.blockPosition(), SoundEvents.PORTAL_TRIGGER,
                    SoundSource.PLAYERS, 0.35F, 0.8F);
        }

        if (session.age == 85) {
            showTitle(target,
                    Component.literal("O DECRETO É ABSOLUTO")
                            .withStyle(palette.primaryText, ChatFormatting.BOLD),
                    Component.literal("Nem distância, nem mundo, negarão Sua vontade")
                            .withStyle(palette.accentText),
                    10, 55, 10);
            level.sendParticles(palette.accentDust,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    40, 0.8, 1.1, 0.8, 0.03);
            level.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_STARE,
                    SoundSource.PLAYERS, 0.45F, 0.65F);
        }

        if (session.age >= 120 && session.age % 5 == 0) {
            level.sendParticles(palette.primaryDust,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    28, 0.4, 0.8, 0.4, 0.08);
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

    private void complete(ServerPlayer target, ServerPlayer staff, StaffCallSession session) {
        ServerLevel destinationLevel = (ServerLevel) staff.level();
        CallPalette palette = session.palette;

        Vec3 look = staff.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() > 1.0E-6) horizontal = horizontal.normalize();
        Vec3 desiredDestination = staff.position().add(horizontal.scale(8.0));
        Optional<Vec3> safeDestination = findSafeDestination(destinationLevel, target, desiredDestination);

        if (safeDestination.isEmpty()) {
            showTitle(target,
                    Component.literal("A PASSAGEM FOI NEGADA")
                            .withStyle(palette.primaryText, ChatFormatting.BOLD),
                    Component.literal("Nenhum solo seguro acolheu sua travessia")
                            .withStyle(palette.accentText),
                    10, 60, 15);
            target.displayClientMessage(
                    Component.literal("[O Chamado] O Véu recusou uma chegada insegura.")
                            .withStyle(palette.primaryText, ChatFormatting.ITALIC), false);
            staff.sendSystemMessage(Component.literal("Não foi encontrado um destino seguro para o chamado.")
                    .withStyle(palette.accentText));
            return;
        }

        Vec3 destination = safeDestination.get();

        destinationLevel.sendParticles(palette.primaryDust,
                destination.x, destination.y + 1.0, destination.z,
                50, 0.8, 1.1, 0.8, 0.06);

        target.teleportTo(destinationLevel,
                destination.x, destination.y, destination.z,
                staff.getYRot() + 180.0F, target.getXRot());

        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;

        destinationLevel.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.75F, 0.65F);
        destinationLevel.sendParticles(palette.accentDust,
                target.getX(), target.getY() + 1.0, target.getZ(),
                60, 0.75, 1.0, 0.75, 0.05);

        showTitle(target,
                Component.literal("DIANTE DA PRESENÇA")
                        .withStyle(palette.primaryText, ChatFormatting.BOLD),
                Component.literal("O decreto foi cumprido")
                        .withStyle(palette.accentText, ChatFormatting.ITALIC),
                10, 70, 20);
        target.displayClientMessage(
                Component.literal("[O Chamado] O Véu se fecha. A vontade foi cumprida.")
                        .withStyle(palette.primaryText, ChatFormatting.ITALIC), false);
    }

    private Optional<Vec3> findSafeDestination(ServerLevel level, ServerPlayer target, Vec3 desired) {
        BlockPos origin = BlockPos.containing(desired);
        int[] verticalOffsets = {0, 1, -1, 2, -2, 3, -3};

        for (int radius = 0; radius <= 4; radius++) {
            for (int dy : verticalOffsets) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                        BlockPos feet = origin.offset(dx, dy, dz);
                        Vec3 candidate = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        if (isSafeDestination(level, target, feet, candidate)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean isSafeDestination(ServerLevel level, ServerPlayer target, BlockPos feet, Vec3 candidate) {
        if (!level.getWorldBorder().isWithinBounds(feet)) return false;

        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
        if (!floor.isFaceSturdy(level, floorPos, Direction.UP)) return false;
        if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)
                || floor.is(Blocks.POWDER_SNOW)) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;

        return level.noCollision(target,
                target.getBoundingBox().move(candidate.subtract(target.position())));
    }

    private void showTitle(ServerPlayer target, Component title, Component subtitle,
                           int fadeInTicks, int stayTicks, int fadeOutTicks) {
        target.connection.send(new ClientboundSetTitlesAnimationPacket(fadeInTicks, stayTicks, fadeOutTicks));
        target.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        target.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}

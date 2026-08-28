package com.noveris.staffcall;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class StaffCallManager {
    private final Map<UUID, StaffCallSession> sessions = new HashMap<>();
    private final Map<UUID, ReturnPoint> returnPoints = new HashMap<>();
    private final CallHistory history = new CallHistory();

    boolean hasActiveCall(UUID targetId) {
        return sessions.containsKey(targetId);
    }

    CallPalette getCallPalette(UUID targetId) {
        StaffCallSession session = sessions.get(targetId);
        return session == null ? null : session.palette;
    }

    List<CallHistory.Entry> getHistory(MinecraftServer server, String playerName, int limit) {
        return history.findForPlayer(server, playerName, limit);
    }

    BeginResult begin(ServerPlayer staff, ServerPlayer target, CallPalette palette) {
        if (target == null || !target.isAlive()) return BeginResult.TARGET_UNAVAILABLE;
        if (staff == null || !staff.isAlive()) return BeginResult.STAFF_UNAVAILABLE;
        if (hasActiveCall(target.getUUID())) return BeginResult.ALREADY_ACTIVE;
        NoverisConfig config = NoverisConfig.load(staff.getServer());

        ServerBossEvent progressBar = new ServerBossEvent(
                Component.literal("O chamado se aproxima").withStyle(palette.primaryText),
                palette.bossBarColor,
                BossEvent.BossBarOverlay.PROGRESS
        );
        progressBar.addPlayer(target);

        StaffCallSession session = new StaffCallSession(
                staff.getUUID(),
                target.getUUID(),
                staff.getName().getString(),
                target.getName().getString(),
                target.level().dimension(),
                target.position(),
                target.getYRot(),
                target.getXRot(),
                progressBar,
                palette,
                config
        );

        sessions.put(target.getUUID(), session);
        history.record(staff.getServer(), "INICIADO", session.staffName, session.targetName,
                palette.id, formatLocation(target.level().dimension(), target.position()), "-");
        startPresentation(target, session);
        return BeginResult.SUCCESS;
    }

    void recordRequest(MinecraftServer server, String action, String staffName,
                       String targetName, CallPalette palette) {
        history.record(server, action, staffName, targetName, palette.id, "-", "-");
    }

    boolean cancel(UUID targetId, MinecraftServer server, boolean notifyTarget) {
        StaffCallSession removed = sessions.remove(targetId);
        if (removed == null) return false;
        removed.progressBar.removeAllPlayers();
        history.record(server, "CANCELADO", removed.staffName, removed.targetName,
                removed.palette.id,
                formatLocation(removed.targetStartDimension, removed.targetStartPosition), "-");

        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (notifyTarget && target != null) {
            restoreAfterLift(target, removed);
            showTitle(target,
                    Component.literal("O CHAMADO SILENCIA").withStyle(removed.palette.primaryText),
                    Component.literal("A voz além do Véu se afasta")
                            .withStyle(removed.palette.accentText),
                    5, 35, 10);
            target.displayClientMessage(
                    Component.literal("[O Chamado] A voz já não exige sua presença.")
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
                if (target != null && target.isAlive()) restoreAfterLift(target, session);
                history.record(server, "INTERROMPIDO", session.staffName, session.targetName,
                        session.palette.id,
                        formatLocation(session.targetStartDimension, session.targetStartPosition), "-");
                iterator.remove();
                ServerPlayer notifier = staff != null ? staff : target;
                if (notifier != null) notifier.sendSystemMessage(Component.literal(
                        target == null ? "Chamado interrompido: o alvo se desconectou."
                                : staff == null ? "Chamado interrompido: o invocador se desconectou."
                                : "Chamado interrompido: um dos jogadores não está mais disponível.")
                        .withStyle(ChatFormatting.RED));
                continue;
            }

            session.age++;
            session.progressBar.setProgress(Math.max(0.0F,
                    1.0F - (session.age / (float) session.totalTicks)));
            tickPresentation(target, staff, session);

            if (session.age >= session.liftFromTick) {
                liftTarget(target, session);
            }

            if (session.age >= session.totalTicks) {
                session.progressBar.removeAllPlayers();
                complete(target, staff, session);
                iterator.remove();
            }
        }
    }

    private void startPresentation(ServerPlayer target, StaffCallSession session) {
        CallPalette palette = session.palette;
        showTitle(target,
                Component.literal("OUÇA O CHAMADO")
                        .withStyle(palette.primaryText, ChatFormatting.BOLD),
                Component.literal("Uma voz além do Véu conhece seu nome")
                        .withStyle(palette.accentText, ChatFormatting.ITALIC),
                10, 60, 10);
        target.displayClientMessage(
                Component.literal("O mundo ao redor parece perder consistência...")
                        .withStyle(palette.accentText, ChatFormatting.ITALIC),
                false
        );
        target.sendSystemMessage(
                Component.literal("[O Chamado] A voz exige sua presença.")
                        .withStyle(palette.primaryText)
        );
        target.level().playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 0.45F, 0.55F);
    }

    private void tickPresentation(ServerPlayer target, ServerPlayer staff, StaffCallSession session) {
        if (!(target.level() instanceof ServerLevel level)) return;
        CallPalette palette = session.palette;

        if (session.age % 2 == 0) {
            level.sendParticles(palette.primaryDust,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    10, 0.65, 0.9, 0.65, 0.01);
        }

        if (session.age == Math.max(1, Math.round(session.totalTicks * 0.40F))) {
            target.displayClientMessage(
                    Component.literal("A distância deixa de existir.")
                            .withStyle(palette.accentText), true);
            level.playSound(null, target.blockPosition(), SoundEvents.PORTAL_TRIGGER,
                    SoundSource.PLAYERS, 0.35F, 0.8F);
        }

        if (session.age == Math.max(2, Math.round(session.totalTicks * 0.53F))) {
            showTitle(target,
                    Component.literal("O VÉU SE ABRE")
                            .withStyle(palette.primaryText, ChatFormatting.BOLD),
                    Component.empty(),
                    10, 55, 10);
            level.sendParticles(palette.accentDust,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    40, 0.8, 1.1, 0.8, 0.03);
            level.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_STARE,
                    SoundSource.PLAYERS, 0.45F, 0.65F);
        }

        if (session.age >= session.liftFromTick + 20 && session.age % 5 == 0) {
            level.sendParticles(palette.primaryDust,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    28, 0.4, 0.8, 0.4, 0.08);
        }

        if (session.age >= session.liftFromTick && session.age % 10 == 0
                && staff.level() instanceof ServerLevel staffLevel) {
            showArrivalCircle(staffLevel, staff, target, session);
        }
    }

    private void liftTarget(ServerPlayer target, StaffCallSession session) {
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;

        if (target.level().dimension().equals(session.targetStartDimension)) {
            Vec3 p = session.targetStartPosition;
            double progress = Math.min(1.0, Math.max(0.0,
                    (session.age - session.liftFromTick)
                            / (double) Math.max(1, session.totalTicks - session.liftFromTick)));
            double easedProgress = Math.sin(progress * Math.PI * 0.5);
            double desiredY = p.y + session.liftHeight * easedProgress;
            Vec3 destination = new Vec3(p.x, desiredY, p.z);
            ServerLevel level = (ServerLevel) target.level();

            if (level.noCollision(target,
                    target.getBoundingBox().move(destination.subtract(target.position())))) {
                target.teleportTo(level, destination.x, destination.y, destination.z,
                        session.targetStartYaw, session.targetStartPitch);
            }
        }
    }

    private void restoreAfterLift(ServerPlayer target, StaffCallSession session) {
        if (session.age < session.liftFromTick) return;
        if (!target.level().dimension().equals(session.targetStartDimension)) return;

        Vec3 start = session.targetStartPosition;
        ServerLevel level = (ServerLevel) target.level();
        if (level.noCollision(target,
                target.getBoundingBox().move(start.subtract(target.position())))) {
            target.teleportTo(level, start.x, start.y, start.z,
                    session.targetStartYaw, session.targetStartPitch);
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
    }

    private void complete(ServerPlayer target, ServerPlayer staff, StaffCallSession session) {
        ServerLevel destinationLevel = (ServerLevel) staff.level();
        CallPalette palette = session.palette;

        Vec3 look = staff.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() > 1.0E-6) horizontal = horizontal.normalize();
        Vec3 staffAnchor = staff.getUUID().equals(target.getUUID())
                ? session.targetStartPosition
                : staff.position();
        Vec3 desiredDestination = staffAnchor.add(horizontal.scale(session.arrivalDistance));
        DestinationSearch safeSearch = findSafeDestination(destinationLevel, target, desiredDestination);

        if (safeSearch.destination.isEmpty()) {
            showTitle(target,
                    Component.literal("O VÉU NÃO SE ABRE")
                            .withStyle(palette.primaryText, ChatFormatting.BOLD),
                    Component.literal("Nenhum caminho alcança este lugar")
                            .withStyle(palette.accentText),
                    10, 60, 15);
            target.displayClientMessage(
                    Component.literal("[O Chamado] Nenhum caminho seguro foi encontrado.")
                            .withStyle(palette.primaryText, ChatFormatting.ITALIC), false);
            staff.sendSystemMessage(Component.literal("Chamado falhou: " + safeSearch.reason.message)
                    .withStyle(ChatFormatting.RED));
            history.record(staff.getServer(), "SEM_DESTINO", session.staffName, session.targetName,
                    palette.id, formatLocation(session.targetStartDimension, session.targetStartPosition),
                    formatLocation(destinationLevel.dimension(), desiredDestination));
            return;
        }

        Vec3 destination = safeSearch.destination.get();

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
                Component.literal("O CHAMADO SE CUMPRE")
                        .withStyle(palette.primaryText, ChatFormatting.BOLD),
                Component.literal("O Véu se fecha às suas costas")
                        .withStyle(palette.accentText, ChatFormatting.ITALIC),
                10, 70, 20);
        target.displayClientMessage(
                Component.literal("[O Chamado] Você está onde sua presença foi exigida.")
                        .withStyle(palette.primaryText, ChatFormatting.ITALIC), false);

        returnPoints.put(target.getUUID(), new ReturnPoint(
                session.targetStartDimension, session.targetStartPosition,
                session.targetStartYaw, session.targetStartPitch,
                session.staffName, session.targetName, palette));
        history.record(staff.getServer(), "CONCLUIDO", session.staffName, session.targetName,
                palette.id, formatLocation(session.targetStartDimension, session.targetStartPosition),
                formatLocation(destinationLevel.dimension(), destination));
    }

    ReturnResult returnPlayer(MinecraftServer server, ServerPlayer requester, ServerPlayer target) {
        if (hasActiveCall(target.getUUID())) return ReturnResult.ACTIVE_CALL;
        ReturnPoint point = returnPoints.get(target.getUUID());
        if (point == null) return ReturnResult.NO_RETURN_POINT;

        ServerLevel destinationLevel = server.getLevel(point.dimension);
        if (destinationLevel == null) return ReturnResult.DIMENSION_UNAVAILABLE;
        DestinationSearch safeSearch = findSafeDestination(destinationLevel, target, point.position);
        if (safeSearch.destination.isEmpty()) return ReturnResult.NO_SAFE_DESTINATION;

        Vec3 destination = safeSearch.destination.get();
        String returnOrigin = formatLocation(target.level().dimension(), target.position());
        target.teleportTo(destinationLevel, destination.x, destination.y, destination.z,
                point.yaw, point.pitch);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
        returnPoints.remove(target.getUUID());

        destinationLevel.sendParticles(point.palette.primaryDust,
                destination.x, destination.y + 1.0, destination.z,
                45, 0.7, 1.0, 0.7, 0.05);
        destinationLevel.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.7F, 0.8F);
        showTitle(target,
                Component.literal("CAMINHO DE VOLTA").withStyle(point.palette.primaryText, ChatFormatting.BOLD),
                Component.literal("O Véu conduz ao lugar de origem")
                        .withStyle(point.palette.accentText),
                10, 60, 15);

        history.record(server, "RETORNADO", requester.getName().getString(), point.targetName,
                point.palette.id, returnOrigin,
                formatLocation(point.dimension, destination));
        return ReturnResult.SUCCESS;
    }

    private void showArrivalCircle(ServerLevel level, ServerPlayer staff,
                                   ServerPlayer target, StaffCallSession session) {
        CallPalette palette = session.palette;
        Vec3 horizontal = new Vec3(staff.getLookAngle().x, 0.0, staff.getLookAngle().z);
        if (horizontal.lengthSqr() > 1.0E-6) horizontal = horizontal.normalize();
        Vec3 staffAnchor = staff.getUUID().equals(target.getUUID())
                ? session.targetStartPosition
                : staff.position();
        Vec3 desired = staffAnchor.add(horizontal.scale(session.arrivalDistance));
        Vec3 center = findSafeDestination(level, target, desired).destination.orElse(desired);

        double radius = 1.35;
        for (int point = 0; point < 20; point++) {
            double angle = (Math.PI * 2.0 * point) / 20.0;
            level.sendParticles(palette.primaryDust,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.12,
                    center.z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(palette.accentDust,
                center.x, center.y + 0.15, center.z,
                8, 0.45, 0.02, 0.45, 0.01);
    }

    private DestinationSearch findSafeDestination(ServerLevel level, ServerPlayer target, Vec3 desired) {
        BlockPos origin = BlockPos.containing(desired);
        int[] verticalOffsets = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 7, -7, 8, -8};
        Map<DestinationFailure, Integer> failures = new HashMap<>();

        for (int radius = 0; radius <= 4; radius++) {
            for (int dy : verticalOffsets) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;

                        BlockPos feet = origin.offset(dx, dy, dz);
                        Vec3 candidate = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        DestinationFailure failure = inspectDestination(level, target, feet, candidate);
                        if (failure == DestinationFailure.NONE) {
                            return new DestinationSearch(Optional.of(candidate), DestinationFailure.NONE);
                        }
                        failures.merge(failure, 1, Integer::sum);
                    }
                }
            }
        }

        DestinationFailure reason = failures.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                .orElse(DestinationFailure.BLOCKED);
        return new DestinationSearch(Optional.empty(), reason);
    }

    private DestinationFailure inspectDestination(ServerLevel level, ServerPlayer target, BlockPos feet, Vec3 candidate) {
        if (!level.getWorldBorder().isWithinBounds(feet)) return DestinationFailure.OUTSIDE_BORDER;

        BlockPos floorPos = feet.below();
        BlockState floor = level.getBlockState(floorPos);
        if (!floor.isFaceSturdy(level, floorPos, Direction.UP)) return DestinationFailure.NO_SOLID_FLOOR;
        if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)
                || floor.is(Blocks.POWDER_SNOW)) return DestinationFailure.DANGEROUS_FLOOR;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return DestinationFailure.FLUID;

        return level.noCollision(target,
                target.getBoundingBox().move(candidate.subtract(target.position())))
                ? DestinationFailure.NONE : DestinationFailure.BLOCKED;
    }

    private void showTitle(ServerPlayer target, Component title, Component subtitle,
                           int fadeInTicks, int stayTicks, int fadeOutTicks) {
        target.connection.send(new ClientboundSetTitlesAnimationPacket(fadeInTicks, stayTicks, fadeOutTicks));
        target.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        target.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private String formatLocation(ResourceKey<Level> dimension, Vec3 position) {
        return String.format(Locale.ROOT, "%s (%.1f, %.1f, %.1f)",
                dimension.location(), position.x, position.y, position.z);
    }

    enum ReturnResult {
        SUCCESS,
        ACTIVE_CALL,
        NO_RETURN_POINT,
        NO_SAFE_DESTINATION,
        DIMENSION_UNAVAILABLE
    }

    enum BeginResult { SUCCESS, ALREADY_ACTIVE, TARGET_UNAVAILABLE, STAFF_UNAVAILABLE }

    private enum DestinationFailure {
        NONE("destino seguro"),
        OUTSIDE_BORDER("o destino está fora da borda do mundo"),
        NO_SOLID_FLOOR("não há chão sólido na área de chegada"),
        DANGEROUS_FLOOR("a área de chegada possui blocos perigosos"),
        FLUID("a área de chegada está ocupada por líquido"),
        BLOCKED("o espaço necessário para o jogador está bloqueado");

        final String message;
        DestinationFailure(String message) { this.message = message; }
    }

    private record DestinationSearch(Optional<Vec3> destination, DestinationFailure reason) {}

    private record ReturnPoint(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch,
                               String staffName, String targetName, CallPalette palette) {
    }
}

package com.noveris.staffcall;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class PlayerCallService {
    private static final long ACCEPTED_COOLDOWN_MILLIS = 2L * 60L * 60L * 1000L;
    private static final long UNANSWERED_COOLDOWN_MILLIS = 5L * 60L * 1000L;
    private static final int PENDING_TICKS = 5 * 60 * 20;

    private final StaffCallManager manager;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, UUID> completedByStaff = new HashMap<>();

    PlayerCallService(StaffCallManager manager) {
        this.manager = manager;
    }

    void submit(ServerPlayer requester, PlayerCallType type, String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim().replace('\n', ' ').replace('\r', ' ');
        if (reason.length() < 10 || reason.length() > 120) {
            requester.sendSystemMessage(Component.literal("O motivo deve ter entre 10 e 120 caracteres.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (pending.containsKey(requester.getUUID()) || active.containsKey(requester.getUUID())) {
            requester.sendSystemMessage(Component.literal("Você já possui um chamado pendente ou em atendimento.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        long remaining = cooldowns.getOrDefault(requester.getUUID(), 0L) - System.currentTimeMillis();
        if (remaining > 0) {
            requester.sendSystemMessage(Component.literal("Você poderá chamar a staff novamente em "
                    + formatRemaining(remaining) + ".").withStyle(ChatFormatting.RED));
            return;
        }

        Pending call = new Pending(requester.getUUID(), requester.getName().getString(), type, reason, PENDING_TICKS);
        pending.put(requester.getUUID(), call);
        manager.recordRequest(requester.getServer(), "PLAYER_SOLICITOU_" + type.name(),
                "STAFF", call.requesterName, type.palette);

        int notified = 0;
        for (ServerPlayer staff : requester.getServer().getPlayerList().getPlayers()) {
            if (!requester.getServer().getPlayerList().isOp(staff.getGameProfile())) continue;
            notified++;
            staff.sendSystemMessage(staffAlert(call));
        }
        requester.sendSystemMessage(Component.literal("Chamado " + type.label
                + " enviado para " + notified + " membro(s) da staff. Ele expira em 5 minutos.")
                .withStyle(type.palette.primaryText));
    }

    int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer requester = EntityArgument.getPlayer(ctx, "player");
        Pending call = pending.remove(requester.getUUID());
        if (call == null) return fail(ctx, "Esse jogador não possui chamado pendente.");

        StaffCallManager.BeginResult result = manager.begin(requester, staff, call.type.palette, call.type);
        if (result != StaffCallManager.BeginResult.SUCCESS) {
            pending.put(requester.getUUID(), call);
            return fail(ctx, "Não foi possível iniciar o teleporte da staff: " + result.name().toLowerCase() + ".");
        }

        cooldowns.put(requester.getUUID(), System.currentTimeMillis() + ACCEPTED_COOLDOWN_MILLIS);
        active.put(requester.getUUID(), new Active(call, staff.getUUID(), staff.getName().getString()));
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_CHAMADO_ACEITO_" + call.type.name(),
                staff.getName().getString(), call.requesterName, call.type.palette);
        requester.sendSystemMessage(Component.literal(staff.getName().getString()
                + " aceitou seu chamado. A staff está a caminho.").withStyle(call.type.palette.primaryText));
        broadcastOps(ctx.getSource().getServer(), Component.literal("[NoveCall] " + staff.getName().getString()
                + " assumiu o chamado de " + call.requesterName + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    int refuse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer requester = EntityArgument.getPlayer(ctx, "player");
        String reason = StringArgumentType.getString(ctx, "motivo").trim();
        if (reason.isEmpty()) return fail(ctx, "Informe o motivo da recusa.");
        Pending call = pending.remove(requester.getUUID());
        if (call == null) return fail(ctx, "Esse jogador não possui chamado pendente.");

        cooldowns.put(requester.getUUID(), System.currentTimeMillis() + UNANSWERED_COOLDOWN_MILLIS);
        requester.sendSystemMessage(Component.literal("Seu chamado foi recusado: " + reason)
                .withStyle(ChatFormatting.RED));
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_CHAMADO_RECUSADO_" + call.type.name(),
                ctx.getSource().getTextName(), call.requesterName, call.type.palette);
        ctx.getSource().sendSuccess(() -> Component.literal("Chamado recusado. Cooldown de 5 minutos aplicado."), true);
        return 1;
    }

    int conclude(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer requester = EntityArgument.getPlayer(ctx, "player");
        Active call = active.get(requester.getUUID());
        if (call == null) return fail(ctx, "Esse jogador não possui atendimento ativo.");
        if (!call.staffId.equals(staff.getUUID()) && !ctx.getSource().hasPermission(4)) {
            return fail(ctx, "Somente a staff que assumiu o chamado pode concluí-lo.");
        }
        active.remove(requester.getUUID());
        completedByStaff.put(call.staffId, requester.getUUID());
        requester.sendSystemMessage(Component.literal("Seu atendimento foi concluído por " + call.staffName + ".")
                .withStyle(ChatFormatting.GREEN));
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_ATENDIMENTO_CONCLUIDO_" + call.pending.type.name(),
                call.staffName, call.pending.requesterName, call.pending.type.palette);
        ctx.getSource().sendSuccess(() -> Component.literal("Atendimento concluído. Use /novecall retornar para voltar."), true);
        return 1;
    }

    int returnStaff(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        if (!completedByStaff.containsKey(staff.getUUID())) {
            return fail(ctx, "Conclua seu atendimento antes de retornar.");
        }
        StaffCallManager.ReturnResult result = manager.returnPlayer(ctx.getSource().getServer(), staff, staff);
        if (result != StaffCallManager.ReturnResult.SUCCESS) {
            return fail(ctx, "Não foi possível retornar: " + result.name().toLowerCase() + ".");
        }
        completedByStaff.remove(staff.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("Você retornou ao local anterior."), false);
        return 1;
    }

    int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer requester = EntityArgument.getPlayer(ctx, "player");
        Pending waiting = pending.remove(requester.getUUID());
        if (waiting != null) {
            cooldowns.put(requester.getUUID(), System.currentTimeMillis() + UNANSWERED_COOLDOWN_MILLIS);
            requester.sendSystemMessage(Component.literal("Seu chamado foi cancelado pela staff.").withStyle(ChatFormatting.RED));
            ctx.getSource().sendSuccess(() -> Component.literal("Chamado pendente cancelado."), true);
            return 1;
        }
        Active running = active.remove(requester.getUUID());
        if (running == null) return 0;
        manager.cancel(running.staffId, ctx.getSource().getServer(), true);
        requester.sendSystemMessage(Component.literal("Seu atendimento foi encerrado pela staff.").withStyle(ChatFormatting.RED));
        ctx.getSource().sendSuccess(() -> Component.literal("Atendimento encerrado à força."), true);
        return 1;
    }

    int list(CommandContext<CommandSourceStack> ctx) {
        if (pending.isEmpty()) return fail(ctx, "Não há chamados pendentes.");
        ctx.getSource().sendSuccess(() -> Component.literal("Chamados pendentes:").withStyle(ChatFormatting.GOLD), false);
        for (Pending call : pending.values()) {
            int seconds = Math.max(0, call.remainingTicks / 20);
            ctx.getSource().sendSuccess(() -> Component.literal("• " + call.requesterName + " [" + call.type.label
                    + "] " + call.reason + " (" + seconds + "s)").withStyle(call.type.palette.primaryText), false);
        }
        return pending.size();
    }

    int removeCooldown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        cooldowns.remove(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("Cooldown removido de " + player.getName().getString() + "."), true);
        player.sendSystemMessage(Component.literal("Seu cooldown de chamados foi removido pela staff.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    void tick(MinecraftServer server) {
        Iterator<Pending> iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Pending call = iterator.next();
            if (--call.remainingTicks > 0) continue;
            iterator.remove();
            cooldowns.put(call.requesterId, System.currentTimeMillis() + UNANSWERED_COOLDOWN_MILLIS);
            ServerPlayer requester = server.getPlayerList().getPlayer(call.requesterId);
            if (requester != null) requester.sendSystemMessage(Component.literal(
                    "Nenhuma staff respondeu. O chamado expirou e você poderá tentar novamente em 5 minutos.")
                    .withStyle(ChatFormatting.GRAY));
            manager.recordRequest(server, "PLAYER_CHAMADO_EXPIRADO_" + call.type.name(),
                    "STAFF", call.requesterName, call.type.palette);
        }
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
    }

    void logout(ServerPlayer player) {
        Pending waiting = pending.remove(player.getUUID());
        if (waiting != null) cooldowns.put(player.getUUID(), System.currentTimeMillis() + UNANSWERED_COOLDOWN_MILLIS);
        Iterator<Map.Entry<UUID, Active>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Active> entry = iterator.next();
            Active call = entry.getValue();
            if (!call.staffId.equals(player.getUUID()) && !entry.getKey().equals(player.getUUID())) continue;
            iterator.remove();
            ServerPlayer requester = player.getServer().getPlayerList().getPlayer(entry.getKey());
            if (requester != null && !requester.getUUID().equals(player.getUUID())) {
                requester.sendSystemMessage(Component.literal("O atendimento foi encerrado porque um participante desconectou.")
                        .withStyle(ChatFormatting.RED));
            }
        }
        completedByStaff.remove(player.getUUID());
    }

    private Component staffAlert(Pending call) {
        ChatFormatting color = call.type == PlayerCallType.RP ? ChatFormatting.GOLD : ChatFormatting.RED;
        String prefix = call.type == PlayerCallType.RP ? "[Chamado RP] Um jogador pede a presença da staff"
                : "[ALERTA OFF-RP] Solicitação técnica recebida";
        return Component.literal(prefix + "\nJogador: " + call.requesterName + "\nMotivo: " + call.reason + "\n")
                .withStyle(color)
                .append(Component.literal("[ATENDER]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/novecall atender " + call.requesterName))))
                .append(Component.literal("  Use /novecall recusar " + call.requesterName + " <motivo>")
                        .withStyle(ChatFormatting.GRAY));
    }

    private void broadcastOps(MinecraftServer server, Component message) {
        for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(staff.getGameProfile())) staff.sendSystemMessage(message);
        }
    }

    private int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }

    private String formatRemaining(long millis) {
        long minutes = Math.max(1, (millis + 59_999L) / 60_000L);
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "min" : minutes + "min";
    }

    private static final class Pending {
        final UUID requesterId;
        final String requesterName;
        final PlayerCallType type;
        final String reason;
        int remainingTicks;

        Pending(UUID requesterId, String requesterName, PlayerCallType type, String reason, int remainingTicks) {
            this.requesterId = requesterId;
            this.requesterName = requesterName;
            this.type = type;
            this.reason = reason;
            this.remainingTicks = remainingTicks;
        }
    }

    private record Active(Pending pending, UUID staffId, String staffName) {
    }
}

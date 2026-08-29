package com.noveris.staffcall;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class PlayerCallService {
    private static final long LONG_COOLDOWN = 2L * 60L * 60L * 1000L;
    private static final long SHORT_COOLDOWN = 5L * 60L * 1000L;
    private static final int PENDING_TICKS = 5 * 60 * 20;
    private static final int WARN_TICKS = 25 * 60 * 20;
    private static final int EXPIRE_TICKS = 30 * 60 * 20;
    private static final String COOLDOWN_FILE = "noveris_staff_call_cooldowns.json";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Long>>() { }.getType();

    private final StaffCallManager manager;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, UUID> awaitingReturn = new HashMap<>();
    private final Map<UUID, Closed> closed = new HashMap<>();
    private boolean loaded;

    PlayerCallService(StaffCallManager manager) { this.manager = manager; }

    void submit(ServerPlayer player, PlayerCallType type, String rawReason) {
        loadCooldowns(player.getServer());
        String reason = clean(rawReason);
        if (reason.length() < 10 || reason.length() > 120) {
            player.sendSystemMessage(Component.literal("O motivo deve ter entre 10 e 120 caracteres.").withStyle(ChatFormatting.RED));
            return;
        }
        if (pending.containsKey(player.getUUID()) || active.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Você já possui um chamado pendente ou ativo.").withStyle(ChatFormatting.RED));
            return;
        }
        long remaining = cooldowns.getOrDefault(player.getUUID(), 0L) - System.currentTimeMillis();
        if (remaining > 0) {
            player.sendSystemMessage(Component.literal("Você poderá chamar novamente em " + remaining(remaining) + ".")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        Pending call = new Pending(player.getUUID(), player.getName().getString(), type, reason);
        pending.put(player.getUUID(), call);
        manager.recordRequest(player.getServer(), "PLAYER_SOLICITOU_" + type.name(), "STAFF", call.name, type.palette);
        notifyOps(player.getServer(), call);
        player.sendSystemMessage(Component.literal("Chamado " + type.label
                        + " enviado para a equipe disponível. Ele expira em 5 minutos.\n")
                .withStyle(type.palette.primaryText)
                .append(Component.literal("[CANCELAR CHAMADO]").withStyle(style -> style
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novecall cancelar")))));
    }

    int cancelOwn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Pending call = pending.remove(player.getUUID());
        if (call == null) return fail(ctx, "Você não possui chamado pendente.");
        cooldown(ctx.getSource().getServer(), player.getUUID(), SHORT_COOLDOWN);
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_CANCELOU_" + call.type.name(), player.getName().getString(), call.name, call.type.palette);
        broadcastOps(ctx.getSource().getServer(), Component.literal("[NoveCall] " + call.name + " cancelou o chamado.").withStyle(ChatFormatting.GRAY));
        ctx.getSource().sendSuccess(() -> Component.literal("Chamado cancelado. Cooldown de 5 minutos aplicado."), false);
        return 1;
    }

    int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        if (busy(staff.getUUID())) return fail(ctx, "Conclua e retorne do atendimento atual antes de aceitar outro.");
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Pending call = pending.remove(player.getUUID());
        if (call == null) return fail(ctx, "Esse jogador não possui chamado pendente.");
        if (!begin(player, staff, call)) {
            pending.put(player.getUUID(), call);
            return fail(ctx, "O teleporte não pôde ser iniciado; o chamado continua na fila.");
        }
        cooldown(ctx.getSource().getServer(), player.getUUID(), LONG_COOLDOWN);
        active.put(player.getUUID(), new Active(call, staff));
        player.sendSystemMessage(Component.literal(staff.getName().getString() + " aceitou seu chamado. A staff está a caminho.")
                .withStyle(call.type.palette.primaryText));
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_CHAMADO_ACEITO_" + call.type.name(), staff.getName().getString(), call.name, call.type.palette);
        broadcastOps(ctx.getSource().getServer(), Component.literal("[NoveCall] " + staff.getName().getString()
                + " assumiu o chamado de " + call.name + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    int refuse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String reason = clean(StringArgumentType.getString(ctx, "motivo"));
        if (reason.isEmpty()) return fail(ctx, "Informe o motivo da recusa.");
        Pending call = pending.remove(player.getUUID());
        if (call == null) return fail(ctx, "Esse jogador não possui chamado pendente.");
        cooldown(ctx.getSource().getServer(), player.getUUID(), SHORT_COOLDOWN);
        player.sendSystemMessage(Component.literal("Seu chamado foi recusado: " + reason).withStyle(ChatFormatting.RED));
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_CHAMADO_RECUSADO_" + call.type.name(), ctx.getSource().getTextName(), call.name, call.type.palette);
        return 1;
    }

    int conclude(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Active call = active.get(player.getUUID());
        if (call == null || !call.arrived) return fail(ctx, "Esse atendimento ainda não foi iniciado.");
        if (!call.staffId.equals(staff.getUUID()) && !ctx.getSource().hasPermission(4)) return fail(ctx, "Somente a staff responsável pode concluir.");
        finish(ctx.getSource().getServer(), player, call, "CONCLUIDO");
        ctx.getSource().sendSuccess(() -> Component.literal("Atendimento concluído. Use /novecall retornar."), true);
        return 1;
    }

    int returnStaff(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        if (!awaitingReturn.containsKey(staff.getUUID())) return fail(ctx, "Conclua seu atendimento antes de retornar.");
        StaffCallManager.ReturnResult result = manager.returnPlayer(ctx.getSource().getServer(), staff, staff);
        if (result != StaffCallManager.ReturnResult.SUCCESS) return fail(ctx, "Não foi possível retornar: " + result.name().toLowerCase() + ".");
        awaitingReturn.remove(staff.getUUID());
        return 1;
    }

    int cancelByStaff(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String reason = clean(StringArgumentType.getString(ctx, "motivo"));
        if (reason.isEmpty()) return fail(ctx, "Informe o motivo do cancelamento.");
        Pending waiting = pending.remove(player.getUUID());
        if (waiting != null) {
            cooldown(ctx.getSource().getServer(), player.getUUID(), SHORT_COOLDOWN);
            player.sendSystemMessage(Component.literal("Chamado cancelado pela staff: " + reason).withStyle(ChatFormatting.RED));
            return 1;
        }
        Active call = active.remove(player.getUUID());
        if (call == null) return 0;
        manager.cancel(call.staffId, ctx.getSource().getServer(), true);
        if (call.arrived) awaitingReturn.put(call.staffId, player.getUUID());
        closed.put(player.getUUID(), new Closed(call.pending, call.staffId, call.staffName));
        player.sendSystemMessage(Component.literal("Atendimento cancelado pela staff: " + reason).withStyle(ChatFormatting.RED));
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_ATENDIMENTO_CANCELADO_" + call.pending.type.name(), ctx.getSource().getTextName(), call.pending.name, call.pending.type.palette);
        return 1;
    }

    int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Pending waiting = pending.get(player.getUUID());
        Active running = active.get(player.getUUID());
        if (waiting == null && running == null) return fail(ctx, "Esse jogador não possui chamado.");
        Pending call = running == null ? waiting : running.pending;
        String status = running == null ? "PENDENTE" : running.arrived ? "EM ATENDIMENTO" : "STAFF A CAMINHO";
        String staff = running == null ? "não definida" : running.staffName;
        ctx.getSource().sendSuccess(() -> Component.literal("NoveCall — " + call.name + "\nTipo: " + call.type.label
                + " | Status: " + status + " | Staff: " + staff + "\nMotivo: " + call.reason)
                .withStyle(call.type.palette.primaryText), false);
        return 1;
    }

    int transfer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer current = ctx.getSource().getPlayerOrException();
        ServerPlayer next = EntityArgument.getPlayer(ctx, "staff");
        if (!ctx.getSource().getServer().getPlayerList().isOp(next.getGameProfile())) return fail(ctx, "A nova staff precisa ser OP.");
        if (busy(next.getUUID())) return fail(ctx, "Essa staff já está ocupada.");
        Map.Entry<UUID, Active> entry = active.entrySet().stream().filter(e -> e.getValue().staffId.equals(current.getUUID())).findFirst().orElse(null);
        if (entry == null) return fail(ctx, "Você não possui atendimento para transferir.");
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayer(entry.getKey());
        if (player == null || !begin(player, next, entry.getValue().pending)) return fail(ctx, "Não foi possível transferir.");
        Active call = entry.getValue();
        if (!call.arrived) manager.cancel(current.getUUID(), ctx.getSource().getServer(), true);
        if (call.arrived) awaitingReturn.put(current.getUUID(), player.getUUID());
        call.staffId = next.getUUID(); call.staffName = next.getName().getString(); call.arrived = false; call.ticks = 0; call.warned = false;
        player.sendSystemMessage(Component.literal("Atendimento transferido para " + call.staffName + ".").withStyle(call.pending.type.palette.primaryText));
        next.sendSystemMessage(Component.literal("Atendimento recebido. Motivo: " + call.pending.reason).withStyle(call.pending.type.palette.primaryText));
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_ATENDIMENTO_TRANSFERIDO_" + call.pending.type.name(), current.getName().getString() + "->" + call.staffName, call.pending.name, call.pending.type.palette);
        return 1;
    }

    int reopen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Closed last = closed.get(player.getUUID());
        if (last == null || !last.staffId.equals(staff.getUUID())) return fail(ctx, "Somente a staff responsável pode reabrir o atendimento recente.");
        if (active.containsKey(player.getUUID()) || pending.containsKey(player.getUUID()) || !begin(player, staff, last.pending)) return fail(ctx, "Não foi possível reabrir.");
        active.put(player.getUUID(), new Active(last.pending, staff));
        awaitingReturn.remove(staff.getUUID()); closed.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Atendimento reaberto por " + staff.getName().getString() + ".").withStyle(last.pending.type.palette.primaryText));
        return 1;
    }

    int list(CommandContext<CommandSourceStack> ctx) {
        if (pending.isEmpty()) return fail(ctx, "Não há chamados pendentes.");
        for (Pending call : pending.values()) ctx.getSource().sendSuccess(() -> Component.literal("• " + call.name + " ["
                + call.type.label + "] " + call.reason + " (" + Math.max(0, call.ticks / 20) + "s)").withStyle(call.type.palette.primaryText), false);
        return pending.size();
    }

    int removeCooldown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player"); loadCooldowns(ctx.getSource().getServer());
        cooldowns.remove(player.getUUID()); saveCooldowns(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Cooldown removido de " + player.getName().getString() + "."), true); return 1;
    }

    int checkCooldown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player"); loadCooldowns(ctx.getSource().getServer());
        long value = cooldowns.getOrDefault(player.getUUID(), 0L) - System.currentTimeMillis();
        ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + ": " + (value > 0 ? remaining(value) : "sem cooldown")), false);
        return 1;
    }

    void tick(MinecraftServer server) {
        loadCooldowns(server);
        Iterator<Pending> waiting = pending.values().iterator();
        while (waiting.hasNext()) {
            Pending call = waiting.next();
            if (--call.ticks > 0) continue;
            waiting.remove(); cooldown(server, call.id, SHORT_COOLDOWN);
            ServerPlayer player = server.getPlayerList().getPlayer(call.id);
            if (player != null) player.sendSystemMessage(Component.literal("O chamado expirou. Tente novamente em 5 minutos.").withStyle(ChatFormatting.GRAY));
        }
        tickActive(server);
        if (cooldowns.entrySet().removeIf(e -> e.getValue() <= System.currentTimeMillis())) saveCooldowns(server);
    }

    private void tickActive(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Active>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Active> entry = it.next(); Active call = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer staff = server.getPlayerList().getPlayer(call.staffId);
            if (player == null || staff == null) continue;
            if (!call.arrived) {
                StaffCallManager.TeleportResult result = manager.pollTeleportResult(call.staffId);
                if (result == null) continue;
                if (result != StaffCallManager.TeleportResult.SUCCESS) {
                    it.remove(); cooldowns.remove(call.pending.id); saveCooldowns(server);
                    call.pending.ticks = PENDING_TICKS; pending.put(call.pending.id, call.pending);
                    player.sendSystemMessage(Component.literal("O destino não estava seguro. Seu chamado voltou à fila por 5 minutos.").withStyle(ChatFormatting.RED));
                    staff.sendSystemMessage(Component.literal("Destino inseguro; chamado devolvido à fila.").withStyle(ChatFormatting.RED));
                    notifyOps(server, call.pending); continue;
                }
                call.arrived = true; call.ticks = 0; arrival(player, staff, call);
                manager.recordRequest(server, "PLAYER_STAFF_CHEGOU_" + call.pending.type.name(), call.staffName, call.pending.name, call.pending.type.palette);
                continue;
            }
            call.ticks++;
            if (!call.warned && call.ticks >= WARN_TICKS) {
                call.warned = true;
                staff.sendSystemMessage(Component.literal("Atendimento expira em 5 minutos.").withStyle(ChatFormatting.YELLOW));
                player.sendSystemMessage(Component.literal("Atendimento será encerrado em 5 minutos.").withStyle(ChatFormatting.YELLOW));
            }
            if (call.ticks >= EXPIRE_TICKS) {
                it.remove(); awaitingReturn.put(call.staffId, player.getUUID()); closed.put(player.getUUID(), new Closed(call.pending, call.staffId, call.staffName));
                player.sendSystemMessage(Component.literal("Atendimento encerrado após 30 minutos.").withStyle(ChatFormatting.GRAY));
                staff.sendSystemMessage(Component.literal("Atendimento expirado. Use /novecall retornar.").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    void logout(ServerPlayer player) {
        Pending call = pending.remove(player.getUUID()); if (call != null) cooldown(player.getServer(), player.getUUID(), SHORT_COOLDOWN);
        active.entrySet().removeIf(e -> e.getKey().equals(player.getUUID()) || e.getValue().staffId.equals(player.getUUID()));
        awaitingReturn.remove(player.getUUID());
    }

    private void finish(MinecraftServer server, ServerPlayer player, Active call, String action) {
        active.remove(player.getUUID()); awaitingReturn.put(call.staffId, player.getUUID());
        closed.put(player.getUUID(), new Closed(call.pending, call.staffId, call.staffName));
        player.sendSystemMessage(Component.literal("Atendimento concluído por " + call.staffName + ".").withStyle(ChatFormatting.GREEN));
        manager.recordRequest(server, "PLAYER_ATENDIMENTO_" + action + "_" + call.pending.type.name(), call.staffName, call.pending.name, call.pending.type.palette);
    }

    private void arrival(ServerPlayer player, ServerPlayer staff, Active call) {
        boolean rp = call.pending.type == PlayerCallType.RP;
        player.sendSystemMessage(Component.literal(rp ? "[O Chamado] A staff atravessou o Véu e chegou até você."
                : "[NoveCall OFF-RP] A staff chegou. O atendimento técnico começou.")
                .withStyle(rp ? ChatFormatting.GOLD : ChatFormatting.RED));
        staff.sendSystemMessage(Component.literal((rp ? "[O Chamado] A travessia terminou. " : "[NoveCall OFF-RP] Destino alcançado. ")
                + "Motivo: " + call.pending.reason).withStyle(rp ? ChatFormatting.GOLD : ChatFormatting.RED));
    }

    private boolean begin(ServerPlayer player, ServerPlayer staff, Pending call) {
        return manager.begin(player, staff, call.type.palette, call.type) == StaffCallManager.BeginResult.SUCCESS;
    }

    private boolean busy(UUID id) { return awaitingReturn.containsKey(id) || active.values().stream().anyMatch(c -> c.staffId.equals(id)); }

    private void notifyOps(MinecraftServer server, Pending call) {
        Component message = Component.literal((call.type == PlayerCallType.RP ? "[Chamado RP] " : "[ALERTA OFF-RP] ")
                        + call.name + "\nMotivo: " + call.reason + "\n").withStyle(call.type.palette.primaryText)
                .append(Component.literal("[ATENDER]").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novecall atender " + call.name))))
                .append(Component.literal("  [RECUSAR]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/novecall recusar " + call.name + " "))));
        for (ServerPlayer staff : server.getPlayerList().getPlayers()) if (server.getPlayerList().isOp(staff.getGameProfile())) staff.sendSystemMessage(message);
    }

    private void broadcastOps(MinecraftServer server, Component message) {
        for (ServerPlayer staff : server.getPlayerList().getPlayers()) if (server.getPlayerList().isOp(staff.getGameProfile())) staff.sendSystemMessage(message);
    }

    private void loadCooldowns(MinecraftServer server) {
        if (loaded) return; loaded = true; Path path = path(server); if (!Files.exists(path)) return;
        try {
            Map<String, Long> values = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
            if (values != null) values.forEach((id, end) -> { try { if (end > System.currentTimeMillis()) cooldowns.put(UUID.fromString(id), end); } catch (RuntimeException ignored) { } });
        } catch (IOException | RuntimeException e) { NoverisStaffCall.LOGGER.error("Falha ao carregar cooldowns", e); }
    }

    private void cooldown(MinecraftServer server, UUID id, long duration) { loadCooldowns(server); cooldowns.put(id, System.currentTimeMillis() + duration); saveCooldowns(server); }
    private void saveCooldowns(MinecraftServer server) {
        Map<String, Long> values = new HashMap<>(); cooldowns.forEach((id, end) -> values.put(id.toString(), end));
        try { Files.writeString(path(server), GSON.toJson(values), StandardCharsets.UTF_8); }
        catch (IOException e) { NoverisStaffCall.LOGGER.error("Falha ao salvar cooldowns", e); }
    }
    private Path path(MinecraftServer server) { return server.getWorldPath(LevelResource.ROOT).resolve(COOLDOWN_FILE); }
    private int fail(CommandContext<CommandSourceStack> ctx, String text) { ctx.getSource().sendFailure(Component.literal(text)); return 0; }
    private String clean(String value) { return value == null ? "" : value.trim().replace('\n', ' ').replace('\r', ' '); }
    private String remaining(long millis) { long m = Math.max(1, (millis + 59_999) / 60_000); return m >= 60 ? m / 60 + "h " + m % 60 + "min" : m + "min"; }

    private static final class Pending {
        final UUID id; final String name; final PlayerCallType type; final String reason; int ticks = PENDING_TICKS;
        Pending(UUID id, String name, PlayerCallType type, String reason) { this.id = id; this.name = name; this.type = type; this.reason = reason; }
    }
    private static final class Active {
        final Pending pending; UUID staffId; String staffName; boolean arrived; boolean warned; int ticks;
        Active(Pending pending, ServerPlayer staff) { this.pending = pending; this.staffId = staff.getUUID(); this.staffName = staff.getName().getString(); }
    }
    private record Closed(Pending pending, UUID staffId, String staffName) { }
}

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
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlayerCallService {
    private static final long REPEAT_BLOCK = 30L * 60L * 1000L;
    private static final Pattern DURATION = Pattern.compile("^(\\d+)(m|h|d)$", Pattern.CASE_INSENSITIVE);
    private static final String COOLDOWN_FILE = "noveris_staff_call_cooldowns.json";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Long>>() { }.getType();

    private final StaffCallManager manager;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, UUID> awaitingReturn = new HashMap<>();
    private final Map<UUID, Closed> closed = new HashMap<>();
    private final Map<UUID, RecentReason> recentReasons = new HashMap<>();
    private final Map<UUID, Mute> mutes = new HashMap<>();
    private final Map<UUID, Integer> refusals = new HashMap<>();
    private int syncTicks;
    private boolean loaded;

    PlayerCallService(StaffCallManager manager) { this.manager = manager; }

    void submit(ServerPlayer player, PlayerCallType type, String rawReason) {
        loadCooldowns(player.getServer());
        NoverisConfig config = NoverisConfig.load(player.getServer());
        String reason = clean(rawReason);
        if (reason.length() < config.reasonMinLength || reason.length() > config.reasonMaxLength) {
            player.sendSystemMessage(Component.literal("O motivo deve ter entre " + config.reasonMinLength + " e " + config.reasonMaxLength + " caracteres.").withStyle(ChatFormatting.RED));
            return;
        }
        Mute mute = mutes.get(player.getUUID());
        if (mute != null && mute.until > System.currentTimeMillis()) {
            player.sendSystemMessage(Component.literal("Suas chamadas estão silenciadas por " + remaining(mute.until - System.currentTimeMillis()) + ". Motivo: " + mute.reason).withStyle(ChatFormatting.RED)); return;
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        RecentReason recent = recentReasons.get(player.getUUID());
        if (recent != null && recent.until > System.currentTimeMillis() && recent.reason.equals(normalized)) {
            player.sendSystemMessage(Component.literal("Esse mesmo motivo já foi enviado nos últimos 30 minutos.").withStyle(ChatFormatting.RED)); return;
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
        Pending call = new Pending(player.getUUID(), player.getName().getString(), type, reason, config.playerQueueTicks);
        pending.put(player.getUUID(), call);
        recentReasons.put(player.getUUID(), new RecentReason(normalized, System.currentTimeMillis() + REPEAT_BLOCK));
        manager.recordRequest(player.getServer(), "PLAYER_SOLICITOU_" + type.name(), "STAFF", call.name, type.palette, "Motivo: " + reason);
        notifyOps(player.getServer(), call);
        player.sendSystemMessage(PlayerCallMessages.requestSent(type));
    }

    int cancelOwn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Pending call = pending.remove(player.getUUID());
        if (call == null) return fail(ctx, "Você não possui chamado pendente.");
        cooldown(ctx.getSource().getServer(), player.getUUID(), NoverisConfig.load(ctx.getSource().getServer()).playerShortCooldownMillis);
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
        cooldown(ctx.getSource().getServer(), player.getUUID(), NoverisConfig.load(ctx.getSource().getServer()).playerCooldownMillis);
        active.put(player.getUUID(), new Active(call, staff));
        player.sendSystemMessage(PlayerCallMessages.acceptedForPlayer(call.type, staff.getName().getString()));
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
        cooldown(ctx.getSource().getServer(), player.getUUID(), NoverisConfig.load(ctx.getSource().getServer()).playerShortCooldownMillis);
        refusals.merge(player.getUUID(), 1, Integer::sum);
        player.sendSystemMessage(PlayerCallMessages.refused(call.type, reason));
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
            cooldown(ctx.getSource().getServer(), player.getUUID(), NoverisConfig.load(ctx.getSource().getServer()).playerShortCooldownMillis);
            player.sendSystemMessage(PlayerCallMessages.cancelled(waiting.type, reason));
            return 1;
        }
        Active call = active.remove(player.getUUID());
        if (call == null) return 0;
        manager.cancel(call.staffId, ctx.getSource().getServer(), true);
        if (call.arrived) awaitingReturn.put(call.staffId, player.getUUID());
        closed.put(player.getUUID(), new Closed(call.pending, call.staffId, call.staffName));
        player.sendSystemMessage(PlayerCallMessages.cancelled(call.pending.type, reason));
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
        ctx.getSource().sendSuccess(() -> PlayerCallMessages.info(call.name, call.type, status, staff, call.reason), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Recusas registradas: " + refusals.getOrDefault(player.getUUID(), 0)).withStyle(ChatFormatting.GRAY), false);
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
        player.sendSystemMessage(PlayerCallMessages.transferred(call.pending.type, call.staffName));
        next.sendSystemMessage(PlayerCallMessages.arrivalForStaff(call.pending.type, call.pending.name, call.pending.reason));
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
        player.sendSystemMessage(PlayerCallMessages.reopened(last.pending.type, staff.getName().getString()));
        return 1;
    }

    int list(CommandContext<CommandSourceStack> ctx) {
        if (pending.isEmpty()) return fail(ctx, "Não há chamados pendentes.");
        ArrayList<Pending> calls = new ArrayList<>(pending.values());
        calls.sort(Comparator.comparingLong(c -> c.createdAt));
        for (int i = 0; i < calls.size(); i++) {
            Pending call = calls.get(i); long elapsed = Math.max(0, (System.currentTimeMillis() - call.createdAt) / 1000);
            String line = (i + 1) + ". " + call.name + " • " + call.type.label + " • aguardando " + String.format("%02d:%02d", elapsed / 60, elapsed % 60);
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(call.type.palette.primaryText), false);
        }
        return pending.size();
    }

    int mute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Matcher matcher = DURATION.matcher(StringArgumentType.getString(ctx, "tempo"));
        if (!matcher.matches()) return fail(ctx, "Use um tempo como 30m, 2h ou 1d.");
        long value = Long.parseLong(matcher.group(1));
        long factor = switch (matcher.group(2).toLowerCase(Locale.ROOT)) { case "d" -> 86_400_000L; case "h" -> 3_600_000L; default -> 60_000L; };
        String reason = clean(StringArgumentType.getString(ctx, "motivo"));
        if (reason.isEmpty()) return fail(ctx, "Informe o motivo do silenciamento.");
        mutes.put(player.getUUID(), new Mute(System.currentTimeMillis() + Math.min(value * factor, 365L * 86_400_000L), reason));
        manager.recordRequest(ctx.getSource().getServer(), "PLAYER_SILENCIADO", ctx.getSource().getTextName(), player.getName().getString(), CallPalette.VERMELHO, "Motivo: " + reason);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + " foi silenciado por " + matcher.group() + "."), true); return 1;
    }

    int unmute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player"); mutes.remove(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("Silenciamento removido de " + player.getName().getString() + "."), true); return 1;
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
        NoverisConfig config = NoverisConfig.load(server);
        Iterator<Pending> waiting = pending.values().iterator();
        while (waiting.hasNext()) {
            Pending call = waiting.next();
            if (server.getPlayerList().getPlayer(call.id) == null) continue;
            if (--call.ticks > 0) continue;
            waiting.remove(); cooldown(server, call.id, config.playerShortCooldownMillis);
            ServerPlayer player = server.getPlayerList().getPlayer(call.id);
            if (player != null) player.sendSystemMessage(Component.literal("O chamado expirou. Tente novamente em 5 minutos.").withStyle(ChatFormatting.GRAY));
        }
        tickActive(server);
        if (++syncTicks >= 20) { syncTicks = 0; for (ServerPlayer player : server.getPlayerList().getPlayers()) sendStatus(player); }
        if (cooldowns.entrySet().removeIf(e -> e.getValue() <= System.currentTimeMillis())) saveCooldowns(server);
    }

    private void tickActive(MinecraftServer server) {
        NoverisConfig config = NoverisConfig.load(server);
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
                    call.pending.ticks = config.playerQueueTicks; pending.put(call.pending.id, call.pending);
                    player.sendSystemMessage(PlayerCallMessages.unsafe(call.pending.type));
                    staff.sendSystemMessage(PlayerCallMessages.unsafe(call.pending.type));
                    notifyOps(server, call.pending); continue;
                }
                call.arrived = true; call.ticks = 0; arrival(player, staff, call);
                manager.recordRequest(server, "PLAYER_STAFF_CHEGOU_" + call.pending.type.name(), call.staffName, call.pending.name, call.pending.type.palette);
                continue;
            }
            call.ticks++;
            if (!call.warned && call.ticks >= config.playerWarningTicks) {
                call.warned = true;
                staff.sendSystemMessage(PlayerCallMessages.expirationWarning(call.pending.type));
                player.sendSystemMessage(PlayerCallMessages.expirationWarning(call.pending.type));
            }
            if (call.ticks >= config.playerMaxDurationTicks) {
                it.remove(); awaitingReturn.put(call.staffId, player.getUUID()); closed.put(player.getUUID(), new Closed(call.pending, call.staffId, call.staffName));
                player.sendSystemMessage(PlayerCallMessages.expired(call.pending.type));
                staff.sendSystemMessage(PlayerCallMessages.expired(call.pending.type));
            }
        }
    }

    void logout(ServerPlayer player) { }

    void login(ServerPlayer player) {
        Active own = active.get(player.getUUID());
        if (own != null && !own.arrived) {
            ServerPlayer staff = player.getServer().getPlayerList().getPlayer(own.staffId);
            if (staff != null) begin(player, staff, own.pending);
        }
        for (Map.Entry<UUID, Active> entry : active.entrySet()) {
            Active call = entry.getValue();
            if (!call.arrived && call.staffId.equals(player.getUUID())) {
                ServerPlayer requester = player.getServer().getPlayerList().getPlayer(entry.getKey());
                if (requester != null) begin(requester, player, call.pending);
            }
        }
        sendStatus(player);
    }

    void sendStatus(ServerPlayer player) {
        Pending waiting = pending.get(player.getUUID()); Active running = active.get(player.getUUID());
        if (waiting != null) { PacketDistributor.sendToPlayer(player, new PlayerCallStatusPayload(waiting.type.name().toLowerCase(Locale.ROOT), "AGUARDANDO_STAFF", waiting.reason, "", Math.max(0, waiting.ticks / 20), true)); return; }
        if (running != null) { NoverisConfig config = NoverisConfig.load(player.getServer()); String state = running.arrived ? "EM_ATENDIMENTO" : "STAFF_A_CAMINHO"; int left = running.arrived ? Math.max(0, (config.playerMaxDurationTicks - running.ticks) / 20) : 0; PacketDistributor.sendToPlayer(player, new PlayerCallStatusPayload(running.pending.type.name().toLowerCase(Locale.ROOT), state, running.pending.reason, running.staffName, left, false)); return; }
        PacketDistributor.sendToPlayer(player, new PlayerCallStatusPayload("", "NONE", "", "", 0, false));
    }

    private void finish(MinecraftServer server, ServerPlayer player, Active call, String action) {
        active.remove(player.getUUID()); awaitingReturn.put(call.staffId, player.getUUID());
        closed.put(player.getUUID(), new Closed(call.pending, call.staffId, call.staffName));
        player.sendSystemMessage(PlayerCallMessages.concluded(call.pending.type, call.staffName));
        manager.recordRequest(server, "PLAYER_ATENDIMENTO_" + action + "_" + call.pending.type.name(), call.staffName, call.pending.name, call.pending.type.palette);
    }

    private void arrival(ServerPlayer player, ServerPlayer staff, Active call) {
        player.sendSystemMessage(PlayerCallMessages.arrivalForPlayer(call.pending.type, call.staffName));
        staff.sendSystemMessage(PlayerCallMessages.arrivalForStaff(call.pending.type, call.pending.name, call.pending.reason));
    }

    private boolean begin(ServerPlayer player, ServerPlayer staff, Pending call) {
        return manager.begin(player, staff, call.type.palette, call.type) == StaffCallManager.BeginResult.SUCCESS;
    }

    private boolean busy(UUID id) { return awaitingReturn.containsKey(id) || active.values().stream().anyMatch(c -> c.staffId.equals(id)); }

    private void notifyOps(MinecraftServer server, Pending call) {
        Component message = PlayerCallMessages.staffAlert(call.name, call.type, call.reason);
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
    private String clean(String value) { return value == null ? "" : value.replace('§', ' ').replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim(); }
    private String remaining(long millis) { long m = Math.max(1, (millis + 59_999) / 60_000); return m >= 60 ? m / 60 + "h " + m % 60 + "min" : m + "min"; }

    private static final class Pending {
        final UUID id; final String name; final PlayerCallType type; final String reason; final long createdAt = System.currentTimeMillis(); int ticks;
        Pending(UUID id, String name, PlayerCallType type, String reason, int ticks) { this.id = id; this.name = name; this.type = type; this.reason = reason; this.ticks = ticks; }
    }
    private static final class Active {
        final Pending pending; UUID staffId; String staffName; boolean arrived; boolean warned; int ticks;
        Active(Pending pending, ServerPlayer staff) { this.pending = pending; this.staffId = staff.getUUID(); this.staffName = staff.getName().getString(); }
    }
    private record Closed(Pending pending, UUID staffId, String staffName) { }
    private record RecentReason(String reason, long until) { }
    private record Mute(long until, String reason) { }
}

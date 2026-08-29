package com.noveris.staffcall;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StaffCallEvents {
    private static final String HISTORY_DELETE_TARGET = "noverisHistoryDeleteTarget";
    private static final String HISTORY_DELETE_EXPIRES = "noverisHistoryDeleteExpires";
    private final StaffCallManager manager = new StaffCallManager();
    private final PlayerCallService playerCalls = new PlayerCallService(manager);
    private final Map<UUID, PendingCall> pendingCalls = new HashMap<>();
    private final Map<String, HistoryDeletion> historyDeletions = new HashMap<>();

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("novecall")
                .executes(this::openPlayerCallScreen)
                .then(Commands.literal("atender")
                        .requires(this::playerCallAdmin)
                        .then(Commands.argument("player", EntityArgument.player()).executes(playerCalls::accept)))
                .then(Commands.literal("concluir")
                        .requires(this::playerCallAdmin)
                        .then(Commands.argument("player", EntityArgument.player()).executes(playerCalls::conclude)))
                .then(Commands.literal("info")
                        .requires(this::playerCallAdmin)
                        .then(Commands.argument("player", EntityArgument.player()).executes(playerCalls::info)))
                .then(Commands.literal("transferir")
                        .requires(this::playerCallAdmin)
                        .then(Commands.argument("staff", EntityArgument.player()).executes(playerCalls::transfer)))
                .then(Commands.literal("reabrir")
                        .requires(this::playerCallAdmin)
                        .then(Commands.argument("player", EntityArgument.player()).executes(playerCalls::reopen)))
                .then(Commands.literal("pendentes")
                        .requires(this::playerCallAdmin).executes(playerCalls::list))
                .then(Commands.literal("cooldown")
                        .requires(this::playerCallAdmin)
                        .then(Commands.literal("consultar")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(playerCalls::checkCooldown)))
                        .then(Commands.literal("remover")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(playerCalls::removeCooldown))))
                .then(Commands.literal("chamar")
                        .requires(s -> s.hasPermission(NoverisConfig.load(s.getServer()).permissionCall))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> request(ctx, CallPalette.DOURADO))
                                .then(Commands.literal("forcar")
                                        .requires(s -> s.hasPermission(NoverisConfig.load(s.getServer()).permissionForce))
                                        .executes(ctx -> force(ctx, CallPalette.DOURADO)))
                                .then(Commands.argument("paleta", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CallPalette.names(), builder))
                                        .executes(ctx -> request(ctx, getPalette(ctx)))
                                        .then(Commands.literal("forcar")
                                                .requires(s -> s.hasPermission(NoverisConfig.load(s.getServer()).permissionForce))
                                                .executes(ctx -> force(ctx, getPalette(ctx)))))))
                .then(Commands.literal("aceitar").executes(this::accept))
                .then(Commands.literal("recusar")
                        .executes(this::refuse)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(this::playerCallAdmin)
                                .then(Commands.argument("motivo", StringArgumentType.greedyString())
                                        .executes(playerCalls::refuse))))
                .then(Commands.literal("silenciar")
                        .requires(this::playerCallAdmin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("tempo", StringArgumentType.word())
                                        .then(Commands.argument("motivo", StringArgumentType.greedyString())
                                                .executes(playerCalls::mute)))))
                .then(Commands.literal("dessilenciar")
                        .requires(this::playerCallAdmin)
                        .then(Commands.argument("player", EntityArgument.player()).executes(playerCalls::unmute)))
                .then(Commands.literal("cancelar")
                        .executes(playerCalls::cancelOwn)
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(s -> s.hasPermission(NoverisConfig.load(s.getServer()).permissionCancel))
                                .then(Commands.argument("motivo", StringArgumentType.greedyString())
                                        .executes(playerCalls::cancelByStaff))))
                .then(Commands.literal("status")
                        .requires(s -> s.hasPermission(NoverisConfig.load(s.getServer()).permissionStatus))
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::status)))
                .then(Commands.literal("retornar")
                        .requires(s -> s.hasPermission(NoverisConfig.load(s.getServer()).permissionReturn))
                        .executes(playerCalls::returnStaff)
                        .then(Commands.argument("player", EntityArgument.player()).executes(this::returnPlayer)))
                .then(Commands.literal("historico")
                        .requires(s -> s.hasPermission(NoverisConfig.load(s.getServer()).permissionHistory))
                        .then(Commands.literal("apagar")
                                .requires(s -> s.hasPermission(NoverisConfig.load(s.getServer()).permissionHistoryDelete))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                historyPlayerNames(ctx.getSource()), builder))
                                        .executes(this::requestHistoryDeletion)
                                        .then(Commands.literal("confirmar").executes(this::confirmHistoryDeletion))))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        ctx.getSource().getServer().getPlayerNames(), builder))
                                .executes(ctx -> showHistory(ctx, StringArgumentType.getString(ctx, "player"))))));
    }

    void submitPlayerCall(ServerPlayer player, String type, String reason) {
        playerCalls.submit(player, PlayerCallType.fromNetwork(type), reason);
    }

    private int openPlayerCallScreen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NoverisConfig config = NoverisConfig.load(ctx.getSource().getServer());
        PacketDistributor.sendToPlayer(player, new OpenPlayerCallScreenPayload(config.reasonMinLength, config.reasonMaxLength));
        playerCalls.sendStatus(player);
        return 1;
    }

    private boolean playerCallAdmin(CommandSourceStack source) {
        return source.hasPermission(NoverisConfig.load(source.getServer()).permissionPlayerCallAdmin);
    }

    private CallPalette getPalette(CommandContext<CommandSourceStack> ctx) {
        return CallPalette.fromName(StringArgumentType.getString(ctx, "paleta"))
                .orElseThrow(() -> new IllegalArgumentException("Paleta inválida"));
    }

    private int request(CommandContext<CommandSourceStack> ctx, CallPalette palette) throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (manager.hasActiveCall(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Não foi possível solicitar: o alvo já está em um chamado ativo."));
            return 0;
        }
        if (pendingCalls.containsKey(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("Não foi possível solicitar: o alvo já possui um pedido pendente."));
            return 0;
        }
        NoverisConfig config = NoverisConfig.load(ctx.getSource().getServer());
        pendingCalls.put(target.getUUID(), new PendingCall(staff.getUUID(), target.getUUID(),
                staff.getName().getString(), target.getName().getString(), palette,
                config.confirmationTimeoutTicks));
        manager.recordRequest(ctx.getSource().getServer(), "SOLICITADO", staff.getName().getString(),
                target.getName().getString(), palette);
        target.sendSystemMessage(Component.literal("[O Chamado] Uma voz solicita sua presença. ")
                .withStyle(palette.primaryText)
                .append(Component.literal("[ACEITAR]").withStyle(style -> style
                        .withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novecall aceitar"))))
                .append(Component.literal("  "))
                .append(Component.literal("[RECUSAR]").withStyle(style -> style
                        .withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novecall recusar")))));
        ctx.getSource().sendSuccess(() -> Component.literal("Pedido enviado a " + target.getName().getString()
                + ". Ele expira em " + config.confirmationTimeoutTicks / 20 + " segundos."), false);
        return 1;
    }

    private int force(CommandContext<CommandSourceStack> ctx, CallPalette palette) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        PendingCall removed = pendingCalls.remove(target.getUUID());
        if (removed != null) manager.recordRequest(ctx.getSource().getServer(), "SUBSTITUIDO_POR_FORCADO",
                removed.staffName, removed.targetName, removed.palette);
        return start(ctx.getSource(), ctx.getSource().getPlayerOrException(), target, palette, true);
    }

    private int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        PendingCall pending = pendingCalls.remove(target.getUUID());
        if (pending == null) {
            ctx.getSource().sendFailure(Component.literal("Você não possui um pedido de chamado pendente."));
            return 0;
        }
        ServerPlayer staff = ctx.getSource().getServer().getPlayerList().getPlayer(pending.staffId);
        if (staff == null) {
            ctx.getSource().sendFailure(Component.literal("Não foi possível aceitar: o invocador está desconectado."));
            manager.recordRequest(ctx.getSource().getServer(), "FALHA_INVOCADOR_OFFLINE",
                    pending.staffName, pending.targetName, pending.palette);
            return 0;
        }
        manager.recordRequest(ctx.getSource().getServer(), "ACEITO", pending.staffName, pending.targetName, pending.palette);
        return start(ctx.getSource(), staff, target, pending.palette, false);
    }

    private int refuse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        PendingCall pending = pendingCalls.remove(target.getUUID());
        if (pending == null) {
            ctx.getSource().sendFailure(Component.literal("Você não possui um pedido de chamado pendente."));
            return 0;
        }
        manager.recordRequest(ctx.getSource().getServer(), "RECUSADO", pending.staffName, pending.targetName, pending.palette);
        ServerPlayer staff = ctx.getSource().getServer().getPlayerList().getPlayer(pending.staffId);
        if (staff != null) staff.sendSystemMessage(Component.literal(pending.targetName
                + " recusou o pedido de chamado.").withStyle(ChatFormatting.RED));
        ctx.getSource().sendSuccess(() -> Component.literal("Você recusou o pedido de chamado."), false);
        return 1;
    }

    private int start(CommandSourceStack source, ServerPlayer staff, ServerPlayer target,
                      CallPalette palette, boolean forced) {
        StaffCallManager.BeginResult result = manager.begin(staff, target, palette);
        if (result != StaffCallManager.BeginResult.SUCCESS) {
            String reason = switch (result) {
                case ALREADY_ACTIVE -> "o alvo já está em um chamado ativo";
                case TARGET_UNAVAILABLE -> "o alvo está desconectado, morto ou indisponível";
                case STAFF_UNAVAILABLE -> "o invocador está desconectado, morto ou indisponível";
                default -> "erro desconhecido";
            };
            source.sendFailure(Component.literal("Não foi possível iniciar o chamado: " + reason + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal((forced ? "Chamado forçado" : "Chamado aceito") + " para ")
                .withStyle(palette.primaryText).append(target.getDisplayName().copy().withStyle(palette.accentText))
                .append(Component.literal(" usando a paleta " + palette.id + ".").withStyle(palette.primaryText)), true);
        return 1;
    }

    private int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        PendingCall pending = pendingCalls.remove(target.getUUID());
        if (pending != null) {
            manager.recordRequest(ctx.getSource().getServer(), "SOLICITACAO_CANCELADA",
                    pending.staffName, pending.targetName, pending.palette);
            ctx.getSource().sendSuccess(() -> Component.literal("Pedido pendente cancelado."), true);
            return 1;
        }
        CallPalette palette = manager.getCallPalette(target.getUUID());
        if (!manager.cancel(target.getUUID(), ctx.getSource().getServer(), true)) {
            ctx.getSource().sendFailure(Component.literal("Esse jogador não possui chamado ativo nem pedido pendente."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Chamado de ").withStyle(palette.primaryText)
                .append(target.getDisplayName().copy().withStyle(palette.accentText))
                .append(Component.literal(" cancelado.").withStyle(palette.primaryText)), true);
        return 1;
    }

    private int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        boolean active = manager.hasActiveCall(target.getUUID());
        String status = active ? "está em um chamado ativo."
                : pendingCalls.containsKey(target.getUUID()) ? "possui um pedido pendente."
                : "não possui chamado ativo nem pedido pendente.";
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " " + status), false);
        return active ? 1 : 0;
    }

    private int returnPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer requester = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        StaffCallManager.ReturnResult result = manager.returnPlayer(ctx.getSource().getServer(), requester, target);
        if (result != StaffCallManager.ReturnResult.SUCCESS) {
            String reason = switch (result) {
                case ACTIVE_CALL -> "o jogador está em uma convocação ativa";
                case NO_RETURN_POINT -> "não existe ponto de retorno disponível";
                case DIMENSION_UNAVAILABLE -> "a dimensão de origem não está disponível";
                case NO_SAFE_DESTINATION -> "o ponto de retorno está bloqueado ou não possui chão seguro";
                default -> "erro desconhecido";
            };
            ctx.getSource().sendFailure(Component.literal("Não foi possível retornar: " + reason + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString()
                + " retornou ao local anterior.").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private int showHistory(CommandContext<CommandSourceStack> ctx, String playerName) {
        List<CallHistory.Entry> entries = manager.getHistory(ctx.getSource().getServer(), playerName, 8);
        if (entries.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Nenhum registro encontrado para " + playerName + "."));
            return 0;
        }
        NoverisConfig config = NoverisConfig.load(ctx.getSource().getServer());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(config.timezone);
        ctx.getSource().sendSuccess(() -> Component.literal("Histórico de " + playerName
                + " (" + config.timezone + "):").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (CallHistory.Entry entry : entries) {
            String time = formatter.format(Instant.ofEpochMilli(entry.timestamp));
            ctx.getSource().sendSuccess(() -> Component.literal("[" + time + "] ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(entry.action).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" | staff: " + entry.staff + " | cor: " + entry.palette)
                            .withStyle(ChatFormatting.WHITE)), false);
            if (!"-".equals(entry.destination)) ctx.getSource().sendSuccess(
                    () -> Component.literal("  " + entry.origin + " -> " + entry.destination)
                            .withStyle(ChatFormatting.DARK_GRAY), false);
            if (entry.detail != null && !entry.detail.isBlank() && !"-".equals(entry.detail)) {
                ctx.getSource().sendSuccess(() -> Component.literal("  " + entry.detail)
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return entries.size();
    }

    private int requestHistoryDeletion(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        int count = manager.countHistory(ctx.getSource().getServer(), playerName);
        if (count == 0) {
            ctx.getSource().sendFailure(Component.literal("Nenhum registro encontrado para " + playerName + "."));
            return 0;
        }
        historyDeletions.entrySet().removeIf(entry -> entry.getValue().expiresAt < System.currentTimeMillis());
        historyDeletions.put(historyDeletionKey(ctx.getSource(), playerName),
                new HistoryDeletion(playerName, System.currentTimeMillis() + 30_000L));
        try {
            ServerPlayer requester = ctx.getSource().getPlayerOrException();
            requester.getPersistentData().putString(HISTORY_DELETE_TARGET, playerName);
            requester.getPersistentData().putLong(HISTORY_DELETE_EXPIRES, System.currentTimeMillis() + 30_000L);
        } catch (CommandSyntaxException ignored) { }
        Component confirm = Component.literal("[CONFIRMAR EXCLUSÃO]").withStyle(style -> style
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/novecall historico apagar " + playerName + " confirmar")));
        ctx.getSource().sendSuccess(() -> Component.literal("⚠ Apagar permanentemente " + count
                        + " registro(s) de " + playerName + "? ").withStyle(ChatFormatting.YELLOW)
                .append(confirm), false);
        ctx.getSource().sendSuccess(() -> Component.literal("A confirmação expira em 30 segundos.")
                .withStyle(ChatFormatting.GRAY), false);
        return count;
    }

    private int confirmHistoryDeletion(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String key = historyDeletionKey(ctx.getSource(), playerName);
        HistoryDeletion deletion = historyDeletions.remove(key);
        boolean valid = deletion != null && deletion.expiresAt >= System.currentTimeMillis()
                && deletion.playerName.equalsIgnoreCase(playerName);
        try {
            ServerPlayer requester = ctx.getSource().getPlayerOrException();
            String storedTarget = requester.getPersistentData().getString(HISTORY_DELETE_TARGET);
            long storedExpiry = requester.getPersistentData().getLong(HISTORY_DELETE_EXPIRES);
            valid = valid || (storedExpiry >= System.currentTimeMillis() && storedTarget.equalsIgnoreCase(playerName));
            requester.getPersistentData().remove(HISTORY_DELETE_TARGET);
            requester.getPersistentData().remove(HISTORY_DELETE_EXPIRES);
        } catch (CommandSyntaxException ignored) { }
        if (!valid) {
            ctx.getSource().sendFailure(Component.literal("A confirmação não existe ou expirou. Execute o comando novamente."));
            return 0;
        }
        int removed = manager.deleteHistory(ctx.getSource().getServer(), playerName);
        if (removed < 0) {
            ctx.getSource().sendFailure(Component.literal("Não foi possível salvar a exclusão no arquivo de histórico."));
            return 0;
        }
        NoverisStaffCall.LOGGER.warn("AUDITORIA: {} apagou {} registro(s) do histórico de {}",
                ctx.getSource().getTextName(), removed, playerName);
        ctx.getSource().sendSuccess(() -> Component.literal("◆ " + removed + " registro(s) de "
                + playerName + " foram apagados permanentemente.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        return removed;
    }

    private String historyDeletionKey(CommandSourceStack source, String playerName) {
        String requester;
        try { requester = source.getPlayerOrException().getUUID().toString(); }
        catch (CommandSyntaxException ignored) { requester = "source:" + source.getTextName().toLowerCase(java.util.Locale.ROOT); }
        return requester + "|" + playerName.toLowerCase(java.util.Locale.ROOT);
    }

    private Iterable<String> historyPlayerNames(CommandSourceStack source) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        java.util.Collections.addAll(names, source.getServer().getPlayerNames());
        names.addAll(manager.getHistoryPlayerNames(source.getServer()));
        return names;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        manager.tick(event.getServer());
        playerCalls.tick(event.getServer());
        Iterator<PendingCall> iterator = pendingCalls.values().iterator();
        while (iterator.hasNext()) {
            PendingCall pending = iterator.next();
            if (--pending.remainingTicks > 0) continue;
            iterator.remove();
            manager.recordRequest(event.getServer(), "EXPIRADO", pending.staffName, pending.targetName, pending.palette);
            ServerPlayer target = event.getServer().getPlayerList().getPlayer(pending.targetId);
            ServerPlayer staff = event.getServer().getPlayerList().getPlayer(pending.staffId);
            if (target != null) target.sendSystemMessage(Component.literal("O pedido de chamado expirou.").withStyle(ChatFormatting.GRAY));
            if (staff != null) staff.sendSystemMessage(Component.literal("O pedido enviado a "
                    + pending.targetName + " expirou.").withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getServer() == null) return;
        if (event.getEntity() instanceof ServerPlayer player) playerCalls.logout(player);
        manager.cancel(event.getEntity().getUUID(), event.getEntity().getServer(), false);
        PendingCall pending = pendingCalls.remove(event.getEntity().getUUID());
        if (pending != null) manager.recordRequest(event.getEntity().getServer(), "ALVO_DESCONECTADO",
                pending.staffName, pending.targetName, pending.palette);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) playerCalls.login(player);
    }

    private static final class PendingCall {
        final UUID staffId;
        final UUID targetId;
        final String staffName;
        final String targetName;
        final CallPalette palette;
        int remainingTicks;

        PendingCall(UUID staffId, UUID targetId, String staffName, String targetName,
                    CallPalette palette, int remainingTicks) {
            this.staffId = staffId;
            this.targetId = targetId;
            this.staffName = staffName;
            this.targetName = targetName;
            this.palette = palette;
            this.remainingTicks = remainingTicks;
        }
    }

    private record HistoryDeletion(String playerName, long expiresAt) { }
}

package com.noveris.staffcall;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class StaffCallEvents {
    private static final DateTimeFormatter HISTORY_TIME =
            DateTimeFormatter.ofPattern("dd/MM HH:mm")
                    .withZone(ZoneId.of("America/Sao_Paulo"));
    private final StaffCallManager manager = new StaffCallManager();

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("noveris")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("chamar")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> call(ctx, CallPalette.DOURADO))
                                        .then(Commands.argument("paleta", StringArgumentType.word())
                                                .suggests((ctx, builder) ->
                                                        SharedSuggestionProvider.suggest(CallPalette.names(), builder))
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "paleta");
                                                    CallPalette palette = CallPalette.fromName(name).orElse(null);
                                                    if (palette == null) {
                                                        ctx.getSource().sendFailure(Component.literal(
                                                                "Cor inválida. Use: "
                                                                        + String.join(", ", CallPalette.names())));
                                                        return 0;
                                                    }
                                                    return call(ctx, palette);
                                                }))))
                        .then(Commands.literal("cancelar")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            CallPalette palette = manager.getCallPalette(target.getUUID());
                                            boolean cancelled = manager.cancel(target.getUUID(), ctx.getSource().getServer(), true);
                                            if (!cancelled) {
                                                ctx.getSource().sendFailure(Component.literal("Esse jogador não possui um chamado ativo."));
                                                return 0;
                                            }

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Chamado de ")
                                                            .withStyle(palette.primaryText)
                                                            .append(target.getDisplayName().copy()
                                                                    .withStyle(palette.accentText))
                                                            .append(Component.literal(" cancelado.")
                                                                    .withStyle(palette.primaryText)),
                                                    true
                                            );
                                            return 1;
                                        })))
                        .then(Commands.literal("status")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            boolean active = manager.hasActiveCall(target.getUUID());
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal(target.getName().getString() + (active
                                                            ? " está em um chamado ativo."
                                                            : " não está em nenhum chamado.")),
                                                    false
                                            );
                                            return active ? 1 : 0;
                                        })))
                        .then(Commands.literal("retornar")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer requester = ctx.getSource().getPlayerOrException();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            StaffCallManager.ReturnResult result = manager.returnPlayer(
                                                    ctx.getSource().getServer(), requester, target);

                                            if (result == StaffCallManager.ReturnResult.ACTIVE_CALL) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Não é possível retornar um jogador durante uma convocação ativa."));
                                                return 0;
                                            }
                                            if (result == StaffCallManager.ReturnResult.NO_RETURN_POINT) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Esse jogador não possui um ponto de retorno disponível."));
                                                return 0;
                                            }
                                            if (result == StaffCallManager.ReturnResult.NO_SAFE_DESTINATION) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Não foi encontrado um local seguro para o retorno."));
                                                return 0;
                                            }

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal(target.getName().getString()
                                                                    + " retornou ao local anterior.")
                                                            .withStyle(ChatFormatting.GOLD),
                                                    true);
                                            return 1;
                                        })))
                        .then(Commands.literal("historico")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                ctx.getSource().getServer().getPlayerNames(), builder))
                                        .executes(ctx -> showHistory(ctx,
                                                StringArgumentType.getString(ctx, "player")))))
        );
    }

    private int call(CommandContext<CommandSourceStack> ctx, CallPalette palette)
            throws CommandSyntaxException {
        ServerPlayer staff = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

        if (!manager.begin(staff, target, palette)) {
            ctx.getSource().sendFailure(Component.literal(
                    target.getName().getString() + " já está em um chamado ativo."));
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("Chamado iniciado para ")
                        .withStyle(palette.primaryText)
                        .append(target.getDisplayName().copy().withStyle(palette.accentText))
                        .append(Component.literal(" usando a paleta " + palette.id + ".")
                                .withStyle(palette.primaryText)),
                true
        );
        return 1;
    }

    private int showHistory(CommandContext<CommandSourceStack> ctx, String playerName) {
        List<CallHistory.Entry> entries = manager.getHistory(
                ctx.getSource().getServer(), playerName, 8);
        if (entries.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "Nenhum registro encontrado para " + playerName + "."));
            return 0;
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("Histórico de " + playerName + " (horário de Brasília):")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);

        for (CallHistory.Entry entry : entries) {
            String time = HISTORY_TIME.format(Instant.ofEpochMilli(entry.timestamp));
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[" + time + "] ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(entry.action).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" | staff: " + entry.staff
                                    + " | cor: " + entry.palette).withStyle(ChatFormatting.WHITE)),
                    false);

            if (!"-".equals(entry.destination)) {
                ctx.getSource().sendSuccess(
                        () -> Component.literal("  " + entry.origin + " -> " + entry.destination)
                                .withStyle(ChatFormatting.DARK_GRAY),
                        false);
            }
        }
        return entries.size();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        manager.tick(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getServer() != null) {
            manager.cancel(event.getEntity().getUUID(), event.getEntity().getServer(), false);
        }
    }
}

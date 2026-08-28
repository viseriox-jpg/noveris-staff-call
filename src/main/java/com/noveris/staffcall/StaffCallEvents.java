package com.noveris.staffcall;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class StaffCallEvents {
    private final StaffCallManager manager = new StaffCallManager();

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("noveris")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("chamar")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer staff = ctx.getSource().getPlayerOrException();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

                                            if (!manager.begin(staff, target)) {
                                                ctx.getSource().sendFailure(Component.literal(target.getName().getString()
                                                        + " já está em um chamado ativo."));
                                                return 0;
                                            }

                                            CallPalette palette = manager.getActivePalette();
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Chamado iniciado para ")
                                                            .withStyle(palette.primaryText)
                                                            .append(target.getDisplayName().copy()
                                                                    .withStyle(palette.accentText)),
                                                    true
                                            );
                                            return 1;
                                        })))
                        .then(Commands.literal("cancelar")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            boolean cancelled = manager.cancel(target.getUUID(), ctx.getSource().getServer(), true);
                                            if (!cancelled) {
                                                ctx.getSource().sendFailure(Component.literal("Esse jogador não possui um chamado ativo."));
                                                return 0;
                                            }

                                            CallPalette palette = manager.getActivePalette();
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
                        .then(Commands.literal("cor")
                                .executes(ctx -> {
                                    CallPalette palette = manager.getActivePalette();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Paleta atual: ")
                                                    .withStyle(ChatFormatting.GRAY)
                                                    .append(Component.literal(palette.id)
                                                            .withStyle(palette.primaryText, ChatFormatting.BOLD)),
                                            false
                                    );
                                    return 1;
                                })
                                .then(Commands.argument("paleta", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                SharedSuggestionProvider.suggest(CallPalette.names(), builder))
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "paleta");
                                            CallPalette palette = CallPalette.fromName(name).orElse(null);
                                            if (palette == null) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Cor inválida. Use: " + String.join(", ", CallPalette.names())));
                                                return 0;
                                            }

                                            manager.setActivePalette(palette);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Paleta do chamado alterada para ")
                                                            .withStyle(palette.accentText)
                                                            .append(Component.literal(palette.id)
                                                                    .withStyle(palette.primaryText, ChatFormatting.BOLD)),
                                                    true
                                            );
                                            return 1;
                                        })))
        );
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

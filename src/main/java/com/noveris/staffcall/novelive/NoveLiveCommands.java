package com.noveris.staffcall.novelive;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.noveris.staffcall.NoveLiveBookRequestPayload;
import com.noveris.staffcall.NoveLiveAdminPayload;
import com.noveris.staffcall.NoverisConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class NoveLiveCommands {
    private static final NoveLiveManager MANAGER = NoveLiveManager.INSTANCE;

    private NoveLiveCommands() { }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("novelife")
                .then(Commands.literal("estado").executes(NoveLiveCommands::state))
                .then(Commands.literal("destino").executes(NoveLiveCommands::destination)
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .requires(NoveLiveCommands::bookAll).executes(NoveLiveCommands::destinationOf)))
                .then(Commands.literal("ativar").requires(NoveLiveCommands::admin).executes(ctx -> mode(ctx, true)))
                .then(Commands.literal("desativar").requires(NoveLiveCommands::admin).executes(ctx -> mode(ctx, false)))
                .then(Commands.literal("painel").requires(NoveLiveCommands::admin)
                        .executes(NoveLiveCommands::adminPanel)
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(NoveLiveCommands::adminPanelOf)))
                .then(Commands.literal("marcar").requires(NoveLiveCommands::admin)
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> mark(ctx, true))))
                .then(Commands.literal("desmarcar").requires(NoveLiveCommands::admin)
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(ctx -> mark(ctx, false))))
                .then(Commands.literal("marcados").requires(NoveLiveCommands::admin).executes(NoveLiveCommands::marked))
                .then(Commands.literal("alma").requires(NoveLiveCommands::admin)
                        .then(Commands.literal("definir").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(0, 3))
                                        .executes(ctx -> change(ctx, SoulChangeType.DEFINICAO_ADMIN)))))
                        .then(Commands.literal("adicionar").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> change(ctx, SoulChangeType.RESTAURACAO_ADMIN)))))
                        .then(Commands.literal("remover").then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("quantidade", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> change(ctx, SoulChangeType.REMOCAO_ADMIN)))))
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(NoveLiveCommands::soul)))
                .then(Commands.literal("rupturas").requires(NoveLiveCommands::admin)
                        .then(Commands.literal("pendentes").executes(ctx -> pending(ctx, 1))
                                .then(Commands.argument("pagina", IntegerArgumentType.integer(1))
                                        .executes(ctx -> pending(ctx, IntegerArgumentType.getInteger(ctx, "pagina"))))))
                .then(Commands.literal("historico").requires(NoveLiveCommands::admin)
                        .then(Commands.literal("apagar")
                                .then(Commands.argument("jogador", EntityArgument.player())
                                        .then(Commands.literal("confirmar").executes(NoveLiveCommands::clearHistory))))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(ctx -> history(ctx, 1))
                                .then(Commands.argument("pagina", IntegerArgumentType.integer(1))
                                        .executes(ctx -> history(ctx, IntegerArgumentType.getInteger(ctx, "pagina"))))))
                .then(Commands.literal("ruptura").requires(NoveLiveCommands::admin)
                        .then(Commands.literal("info").then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(NoveLiveCommands::ruptureInfo)))
                        .then(Commands.literal("confirmar").then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(NoveLiveCommands::confirm)))
                        .then(Commands.literal("rejeitar").then(Commands.argument("id", LongArgumentType.longArg(1))
                                .then(Commands.argument("motivo", StringArgumentType.greedyString())
                                        .executes(NoveLiveCommands::reject))))));
    }

    private static boolean admin(CommandSourceStack source) {
        return source.hasPermission(NoverisConfig.load(source.getServer()).permissionNoveLiveAdmin);
    }

    private static boolean bookAll(CommandSourceStack source) {
        return source.hasPermission(NoverisConfig.load(source.getServer()).permissionNoveLiveBookAll);
    }

    private static int state(CommandContext<CommandSourceStack> ctx) {
        boolean active = MANAGER.canonicalMode(ctx.getSource().getServer());
        if (admin(ctx.getSource())) {
            ctx.getSource().sendSuccess(() -> Component.literal("◆ Estado do Véu: ")
                    .withStyle(ChatFormatting.GRAY).append(Component.literal(active ? "ABERTO" : "SELADO")
                            .withStyle(active ? ChatFormatting.RED : ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(active
                    ? "◆ O Véu está aberto, e certas almas permanecem sob seu olhar."
                    : "◆ O Limiar permanece selado; o olhar além dele está distante.")
                    .withStyle(active ? ChatFormatting.DARK_RED : ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        }
        return 1;
    }

    private static int destination(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NoveLiveBookRequestPayload.sendDestination(player, player);
        return 1;
    }

    private static int destinationOf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
        NoveLiveBookRequestPayload.sendDestination(viewer, target);
        return 1;
    }

    private static int adminPanel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        NoveLiveAdminPayload.send(viewer, "", "");
        return 1;
    }

    private static int adminPanelOf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "jogador");
        NoveLiveAdminPayload.send(viewer, target.getUUID().toString(), "");
        return 1;
    }

    private static int mode(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        MANAGER.setCanonicalMode(ctx.getSource().getServer(), enabled);
        NoveLiveAdminPayload.refreshAdmins(ctx.getSource().getServer());
        return 1;
    }

    private static int mark(CommandContext<CommandSourceStack> ctx, boolean marked) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jogador");
        if (!MANAGER.mark(ctx.getSource().getServer(), player, marked)) {
            ctx.getSource().sendFailure(Component.literal("Esse jogador já está " + (marked ? "marcado" : "desmarcado") + "."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString()
                + (marked ? " foi exposto ao cânone." : " não está mais exposto ao cânone.")), true);
        NoveLiveAdminPayload.refreshAdmins(ctx.getSource().getServer());
        return 1;
    }

    private static int marked(CommandContext<CommandSourceStack> ctx) {
        List<NoveLiveManager.SoulView> marked = MANAGER.souls(ctx.getSource().getServer()).stream()
                .filter(NoveLiveManager.SoulView::marked).toList();
        if (marked.isEmpty()) { ctx.getSource().sendFailure(Component.literal("Nenhuma alma está marcada.")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Almas expostas ao cânone:").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        marked.forEach(value -> ctx.getSource().sendSuccess(() -> Component.literal("• " + value.name() + " — "
                + NoveLiveMessages.symbols(value.fragments())).withStyle(ChatFormatting.RED), false));
        return marked.size();
    }

    private static int soul(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jogador");
        NoveLiveManager.SoulView soul = MANAGER.soul(ctx.getSource().getServer(), player);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + "\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(NoveLiveMessages.symbols(soul.fragments()) + "\n").withStyle(ChatFormatting.RED))
                .append(Component.literal(soul.fragments() + "/3 — " + soul.state().label).withStyle(ChatFormatting.GRAY)), false);
        return soul.fragments();
    }

    private static int change(CommandContext<CommandSourceStack> ctx, SoulChangeType type) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jogador");
        int amount = IntegerArgumentType.getInteger(ctx, "quantidade");
        int current = MANAGER.soul(ctx.getSource().getServer(), player).fragments();
        int requested = switch (type) {
            case DEFINICAO_ADMIN -> amount;
            case RESTAURACAO_ADMIN -> current + amount;
            case REMOCAO_ADMIN -> current - amount;
            default -> current;
        };
        NoveLiveManager.ChangeResult result = MANAGER.change(ctx.getSource().getServer(), player, requested,
                type, ctx.getSource().getTextName(), "Alteração administrativa");
        NoveLiveEffects.refresh(player);
        NoveLiveEffects.administrativeChange(player, type, result.before(), result.after(),
                result.reservesBefore(), result.reservesAfter());
        NoveLiveAdminPayload.refreshAdmins(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + ": " + result.before()
                + "/3 + " + result.reservesBefore() + "R → " + result.after() + "/3 + "
                + result.reservesAfter() + "R — " + result.state().label).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int pending(CommandContext<CommandSourceStack> ctx, int page) {
        List<NoveLiveManager.RuptureView> all = MANAGER.pending(ctx.getSource().getServer());
        int pages = Math.max(1, (all.size() + 7) / 8);
        if (page > pages) { ctx.getSource().sendFailure(Component.literal("Página inválida. Existem " + pages + " página(s).")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Rupturas pendentes • " + page + "/" + pages)
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
        all.stream().skip((long) (page - 1) * 8).limit(8).forEach(value ->
                ctx.getSource().sendSuccess(() -> Component.literal("#" + value.id() + " • " + value.playerName()
                        + " • " + NoveLiveCauseNames.translate(value.cause())).withStyle(ChatFormatting.RED), false));
        return all.size();
    }

    private static int ruptureInfo(CommandContext<CommandSourceStack> ctx) {
        long id = LongArgumentType.getLong(ctx, "id");
        NoveLiveManager.RuptureView value = MANAGER.ruptureView(ctx.getSource().getServer(), id);
        if (value == null) { ctx.getSource().sendFailure(Component.literal("Ruptura não encontrada.")); return 0; }
        String time = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(value.timestamp()));
        ctx.getSource().sendSuccess(() -> Component.literal("RUPTURA #" + id + " — " + value.playerName() + "\n")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Causa: " + NoveLiveCauseNames.translate(value.cause()) + " | " + time + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value.dimension() + " • " + value.x() + ", " + value.y() + ", " + value.z() + "\n").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("Assassino: " + value.killer() + " | Arma: " + value.weapon()).withStyle(ChatFormatting.GRAY)), false);
        return 1;
    }

    private static int history(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jogador");
        List<NoveLiveManager.ChangeView> all = MANAGER.history(ctx.getSource().getServer(), player.getName().getString());
        int pages = Math.max(1, (all.size() + 7) / 8);
        if (page > pages) { ctx.getSource().sendFailure(Component.literal("Página inválida. Existem " + pages + " página(s).")); return 0; }
        ctx.getSource().sendSuccess(() -> Component.literal("Histórico da alma de " + player.getName().getString()
                + " • " + page + "/" + pages).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
        all.stream().skip((long) (page - 1) * 8).limit(8).forEach(value -> ctx.getSource().sendSuccess(
                () -> Component.literal(formatter.format(Instant.ofEpochMilli(value.timestamp())) + " • "
                        + value.before() + "/3+" + value.reservesBefore() + "R → " + value.after() + "/3+"
                        + value.reservesAfter() + "R • " + value.type().name()
                        + ("-".equals(value.administrator()) ? "" : " • " + value.administrator()))
                        .withStyle(ChatFormatting.GRAY), false));
        return all.size();
    }

    private static int clearHistory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jogador");
        int removed = MANAGER.clearHistory(ctx.getSource().getServer(), player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal("Histórico de " + player.getName().getString()
                + " apagado: " + removed + " registro(s).").withStyle(ChatFormatting.DARK_PURPLE), true);
        NoveLiveAdminPayload.refreshAdmins(ctx.getSource().getServer());
        return removed;
    }

    private static int confirm(CommandContext<CommandSourceStack> ctx) {
        long id = LongArgumentType.getLong(ctx, "id");
        NoveLiveManager.ConfirmResult result = MANAGER.confirm(ctx.getSource().getServer(), id, ctx.getSource().getTextName());
        if (result != NoveLiveManager.ConfirmResult.SUCCESS) {
            String error = switch (result) {
                case NOT_FOUND -> "Ruptura não encontrada.";
                case ALREADY_RESOLVED -> "Essa ruptura já foi resolvida.";
                case NO_FRAGMENTS -> "A alma já está desfeita; a ruptura permanece pendente, mas não pode ser confirmada.";
                default -> "Não foi possível confirmar.";
            };
            ctx.getSource().sendFailure(Component.literal(error)); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Ruptura #" + id + " confirmada.").withStyle(ChatFormatting.RED), true);
        NoveLiveEffects.refreshAll(ctx.getSource().getServer());
        NoveLiveAdminPayload.refreshAdmins(ctx.getSource().getServer());
        return 1;
    }

    private static int reject(CommandContext<CommandSourceStack> ctx) {
        long id = LongArgumentType.getLong(ctx, "id");
        String reason = StringArgumentType.getString(ctx, "motivo").trim();
        if (reason.isEmpty() || !MANAGER.reject(ctx.getSource().getServer(), id, ctx.getSource().getTextName(), reason)) {
            ctx.getSource().sendFailure(Component.literal("Ruptura inexistente, já resolvida ou motivo vazio.")); return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Ruptura #" + id + " rejeitada."), true);
        NoveLiveAdminPayload.refreshAdmins(ctx.getSource().getServer());
        return 1;
    }
}

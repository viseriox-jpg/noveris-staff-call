package com.noveris.staffcall.novelive;

import com.noveris.staffcall.NoverisConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class NoveLiveEvents {
    private int effectTicker;

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        NoveLiveCommands.register(event);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String killer = "-";
        String weapon = "-";
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            killer = attacker.getName().getString();
            if (!attacker.getMainHandItem().isEmpty()) weapon = attacker.getMainHandItem().getHoverName().getString();
        }
        long id = NoveLiveManager.INSTANCE.registerDeath(player.getServer(), player,
                event.getSource().getMsgId(), player.level().dimension().location().toString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ(), killer, weapon);
        if (id < 0) return;

        player.sendSystemMessage(Component.literal("◆ Sua morte ecoou além do Véu. A ruptura #" + id
                + " aguarda o julgamento da staff.").withStyle(ChatFormatting.DARK_RED));
        Component alert = Component.literal("⚠ RUPTURA CANÔNICA PENDENTE #" + id + "\n")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Jogador: " + player.getName().getString() + " | Causa: "
                        + event.getSource().getMsgId() + "\n").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[CONFIRMAR]").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novelive ruptura confirmar " + id))))
                .append(Component.literal("  "))
                .append(Component.literal("[DETALHES]").withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/novelive ruptura info " + id))));
        int permission = NoverisConfig.load(player.getServer()).permissionNoveLiveAdmin;
        for (ServerPlayer staff : player.getServer().getPlayerList().getPlayers()) {
            if (staff.createCommandSourceStack().hasPermission(permission)) staff.sendSystemMessage(alert);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NoveLiveManager.INSTANCE.ensurePlayer(player);
            NoveLiveEffects.refresh(player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) NoveLiveEffects.refresh(player);
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) NoveLiveEffects.refresh(player);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) NoveLiveEffects.particles(player, tick);
        if (++effectTicker >= 40) {
            effectTicker = 0;
            NoveLiveEffects.refreshAll(event.getServer());
        }
    }
}

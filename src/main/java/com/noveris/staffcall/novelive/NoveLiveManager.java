package com.noveris.staffcall.novelive;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NoveLiveManager {
    public static final NoveLiveManager INSTANCE = new NoveLiveManager();
    private static final int MAX_HISTORY = 2_000;

    private final NoveLiveStorage storage = new NoveLiveStorage();
    private final Map<UUID, Long> processedDeaths = new HashMap<>();
    private NoveLiveStorage.Data data;

    private NoveLiveManager() { }

    public synchronized boolean canonicalMode(MinecraftServer server) { return data(server).canonicalMode; }

    public synchronized void setCanonicalMode(MinecraftServer server, boolean enabled) {
        NoveLiveStorage.Data value = data(server);
        if (value.canonicalMode == enabled) return;
        value.canonicalMode = enabled;
        save(server);
        Component message = enabled ? NoveLiveMessages.modeEnabled() : NoveLiveMessages.modeDisabled();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            title(player,
                    enabled ? Component.literal("O VÉU SE ENFRAQUECE").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                            : Component.literal("O VÉU SE FECHA").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                    enabled ? Component.literal("A morte alcança além da carne").withStyle(ChatFormatting.RED)
                            : Component.literal("As almas já não estão expostas").withStyle(ChatFormatting.LIGHT_PURPLE));
            player.level().playSound(null, player.blockPosition(),
                    enabled ? SoundEvents.WITHER_SPAWN : SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.MASTER, enabled ? 0.45F : 0.7F, enabled ? 0.65F : 0.9F);
        }
    }

    public synchronized SoulView soul(MinecraftServer server, ServerPlayer player) {
        return soul(server, player.getUUID(), player.getName().getString());
    }

    public synchronized void ensurePlayer(ServerPlayer player) {
        record(player.getServer(), player.getUUID(), player.getName().getString());
        save(player.getServer());
    }

    public synchronized SoulView soul(MinecraftServer server, UUID id, String name) {
        NoveLiveStorage.SoulRecord record = record(server, id, name);
        return new SoulView(id, record.name, record.fragments, record.marked, SoulState.fromFragments(record.fragments));
    }

    public synchronized ChangeResult change(MinecraftServer server, ServerPlayer player, int requested,
                                             SoulChangeType type, String administrator, String reason) {
        NoveLiveStorage.SoulRecord soul = record(server, player.getUUID(), player.getName().getString());
        int before = soul.fragments;
        int after = Math.clamp(requested, 0, 4);
        soul.fragments = after;
        addHistory(player.getUUID(), soul.name, before, after, type, "COMANDO", administrator, reason);
        save(server);
        return new ChangeResult(before, after, SoulState.fromFragments(after));
    }

    public synchronized boolean mark(MinecraftServer server, ServerPlayer player, boolean marked) {
        NoveLiveStorage.SoulRecord soul = record(server, player.getUUID(), player.getName().getString());
        if (soul.marked == marked) return false;
        soul.marked = marked;
        save(server);
        player.sendSystemMessage(marked ? NoveLiveMessages.marked() : NoveLiveMessages.unmarked());
        return true;
    }

    public synchronized long registerDeath(MinecraftServer server, ServerPlayer player, String cause,
                                           String dimension, int x, int y, int z, String killer, String weapon) {
        NoveLiveStorage.Data value = data(server);
        NoveLiveStorage.SoulRecord soul = record(server, player.getUUID(), player.getName().getString());
        if (!value.canonicalMode || !soul.marked) return -1;
        long tick = server.getTickCount();
        if (processedDeaths.getOrDefault(player.getUUID(), Long.MIN_VALUE) == tick) return -1;
        processedDeaths.put(player.getUUID(), tick);

        NoveLiveStorage.RuptureRecord rupture = new NoveLiveStorage.RuptureRecord();
        rupture.id = value.nextRuptureId++;
        rupture.playerId = player.getUUID().toString();
        rupture.playerName = player.getName().getString();
        rupture.timestamp = System.currentTimeMillis();
        rupture.cause = cause;
        rupture.dimension = dimension;
        rupture.x = x; rupture.y = y; rupture.z = z;
        rupture.killer = killer;
        rupture.weapon = weapon;
        value.ruptures.add(rupture);
        save(server);
        return rupture.id;
    }

    public synchronized ConfirmResult confirm(MinecraftServer server, long id, String staff) {
        NoveLiveStorage.RuptureRecord rupture = rupture(server, id);
        if (rupture == null) return ConfirmResult.NOT_FOUND;
        if (rupture.status != RuptureStatus.PENDENTE) return ConfirmResult.ALREADY_RESOLVED;
        UUID playerId = UUID.fromString(rupture.playerId);
        NoveLiveStorage.SoulRecord soul = record(server, playerId, rupture.playerName);
        if (soul.fragments <= 0) return ConfirmResult.NO_FRAGMENTS;
        int before = soul.fragments;
        soul.fragments--;
        rupture.status = RuptureStatus.CONFIRMADA;
        rupture.reviewer = staff;
        addHistory(playerId, soul.name, before, soul.fragments, SoulChangeType.MORTE_CANONICA,
                "RUPTURA_#" + id, staff, rupture.cause);
        save(server);
        announceRupture(server, soul.name, soul.fragments);
        return ConfirmResult.SUCCESS;
    }

    public synchronized boolean reject(MinecraftServer server, long id, String staff, String reason) {
        NoveLiveStorage.RuptureRecord rupture = rupture(server, id);
        if (rupture == null || rupture.status != RuptureStatus.PENDENTE) return false;
        rupture.status = RuptureStatus.REJEITADA;
        rupture.reviewer = staff;
        rupture.reviewReason = reason;
        save(server);
        return true;
    }

    public synchronized List<RuptureView> pending(MinecraftServer server) {
        return data(server).ruptures.stream().filter(value -> value.status == RuptureStatus.PENDENTE)
                .sorted(Comparator.comparingLong(value -> value.id)).map(this::view).toList();
    }

    public synchronized RuptureView ruptureView(MinecraftServer server, long id) {
        NoveLiveStorage.RuptureRecord value = rupture(server, id);
        return value == null ? null : view(value);
    }

    public synchronized List<SoulView> souls(MinecraftServer server) {
        List<SoulView> result = new ArrayList<>();
        data(server).souls.forEach((id, soul) -> {
            try { result.add(new SoulView(UUID.fromString(id), soul.name, soul.fragments, soul.marked,
                    SoulState.fromFragments(soul.fragments))); } catch (RuntimeException ignored) { }
        });
        result.sort(Comparator.comparing(SoulView::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized List<ChangeView> history(MinecraftServer server, String playerName) {
        List<ChangeView> result = new ArrayList<>();
        for (NoveLiveStorage.ChangeRecord value : data(server).history.reversed()) {
            if (value.playerName.equalsIgnoreCase(playerName)) {
                result.add(new ChangeView(value.timestamp, value.playerName, value.before, value.after,
                        value.type, value.origin, value.administrator, value.reason));
            }
        }
        return result;
    }

    private void announceRupture(MinecraftServer server, String name, int fragments) {
        Component message = fragments == 0 ? NoveLiveMessages.soulDestroyed(name)
                : NoveLiveMessages.rupture(name, fragments);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            if (fragments == 0) {
                title(player, Component.literal("O ÚLTIMO FRAGMENTO SE PARTIU").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.literal(name + " — Alma Desfeita").withStyle(ChatFormatting.GRAY));
                player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_DEATH,
                        SoundSource.MASTER, 0.65F, 0.55F);
            } else {
                player.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                        SoundSource.MASTER, 0.35F, 0.65F);
            }
        }
    }

    private void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 15));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    private NoveLiveStorage.Data data(MinecraftServer server) {
        if (data == null) data = storage.load(server);
        return data;
    }

    private NoveLiveStorage.SoulRecord record(MinecraftServer server, UUID id, String name) {
        NoveLiveStorage.SoulRecord value = data(server).souls.computeIfAbsent(id.toString(), ignored -> new NoveLiveStorage.SoulRecord(name));
        value.fragments = Math.clamp(value.fragments, 0, 4);
        value.name = name;
        return value;
    }

    private NoveLiveStorage.RuptureRecord rupture(MinecraftServer server, long id) {
        return data(server).ruptures.stream().filter(value -> value.id == id).findFirst().orElse(null);
    }

    private RuptureView view(NoveLiveStorage.RuptureRecord value) {
        return new RuptureView(value.id, value.playerName, value.timestamp, value.cause, value.dimension,
                value.x, value.y, value.z, value.killer, value.weapon, value.status);
    }

    private void addHistory(UUID id, String name, int before, int after, SoulChangeType type,
                            String origin, String administrator, String reason) {
        NoveLiveStorage.ChangeRecord entry = new NoveLiveStorage.ChangeRecord();
        entry.timestamp = System.currentTimeMillis(); entry.playerId = id.toString(); entry.playerName = name;
        entry.before = before; entry.after = after; entry.type = type; entry.origin = origin;
        entry.administrator = administrator; entry.reason = reason;
        data.history.add(entry);
        while (data.history.size() > MAX_HISTORY) data.history.removeFirst();
    }

    private void save(MinecraftServer server) { storage.save(server, data(server)); }

    public enum ConfirmResult { SUCCESS, NOT_FOUND, ALREADY_RESOLVED, NO_FRAGMENTS }
    public record SoulView(UUID id, String name, int fragments, boolean marked, SoulState state) { }
    public record ChangeResult(int before, int after, SoulState state) { }
    public record RuptureView(long id, String playerName, long timestamp, String cause, String dimension,
                              int x, int y, int z, String killer, String weapon, RuptureStatus status) { }
    public record ChangeView(long timestamp, String playerName, int before, int after, SoulChangeType type,
                             String origin, String administrator, String reason) { }
}

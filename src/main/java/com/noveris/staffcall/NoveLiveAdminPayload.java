package com.noveris.staffcall;

import com.google.gson.Gson;
import com.noveris.staffcall.novelive.NoveLiveManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public record NoveLiveAdminPayload(String json) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    public static final Type<NoveLiveAdminPayload> TYPE_ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "novelive_admin_panel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoveLiveAdminPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NoveLiveAdminPayload::json, NoveLiveAdminPayload::new);

    public static void send(ServerPlayer viewer, String selectedId, String feedback) {
        NoveLiveManager manager = NoveLiveManager.INSTANCE;
        List<NoveLiveAdminData.SoulEntry> souls = viewer.getServer().getPlayerList().getPlayers().stream()
                .map(player -> {
                    NoveLiveManager.SoulView soul = manager.soul(viewer.getServer(), player);
                    return new NoveLiveAdminData.SoulEntry(player.getUUID().toString(), soul.name(), soul.fragments(),
                            soul.state().label, soul.marked(), manager.pendingFor(viewer.getServer(), player.getUUID()));
                }).sorted((left, right) -> left.name().compareToIgnoreCase(right.name())).toList();
        List<NoveLiveAdminData.RuptureEntry> ruptures = manager.pending(viewer.getServer()).stream()
                .map(value -> new NoveLiveAdminData.RuptureEntry(value.id(), value.playerId().toString(),
                        value.playerName(), value.timestamp(), value.cause(), value.dimension(), value.x(), value.y(),
                        value.z(), value.killer(), value.weapon())).toList();
        String selected = selectedId == null ? "" : selectedId;
        if (!selected.isEmpty() && souls.stream().noneMatch(value -> value.id().equals(selected))) selected = "";
        PacketDistributor.sendToPlayer(viewer, new NoveLiveAdminPayload(GSON.toJson(new NoveLiveAdminData(
                manager.canonicalMode(viewer.getServer()), selected, souls, ruptures, feedback == null ? "" : feedback))));
    }

    public NoveLiveAdminData data() { return GSON.fromJson(json, NoveLiveAdminData.class); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE_ID; }
}

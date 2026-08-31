package com.noveris.staffcall;

import com.google.gson.Gson;
import com.noveris.staffcall.novelive.NoveLiveManager;
import com.noveris.staffcall.novelive.NoveLiveCauseNames;
import com.noveris.staffcall.NoverisConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public record NoveLiveAdminPayload(String json, boolean updateOnly) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    public static final Type<NoveLiveAdminPayload> TYPE_ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "novelive_admin_panel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoveLiveAdminPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NoveLiveAdminPayload::json,
            ByteBufCodecs.BOOL, NoveLiveAdminPayload::updateOnly,
            NoveLiveAdminPayload::new);

    public static void send(ServerPlayer viewer, String selectedId, String feedback) {
        send(viewer, selectedId, feedback, false);
    }

    public static void send(ServerPlayer viewer, String selectedId, String feedback, boolean updateOnly) {
        NoveLiveManager manager = NoveLiveManager.INSTANCE;
        List<NoveLiveAdminData.SoulEntry> souls = viewer.getServer().getPlayerList().getPlayers().stream()
                .map(player -> {
                    NoveLiveManager.SoulView soul = manager.soul(viewer.getServer(), player);
                    return new NoveLiveAdminData.SoulEntry(player.getUUID().toString(), soul.name(), soul.fragments(),
                            soul.state().label, soul.marked(), manager.pendingFor(viewer.getServer(), player.getUUID()));
                }).sorted((left, right) -> left.name().compareToIgnoreCase(right.name())).toList();
        List<NoveLiveAdminData.RuptureEntry> ruptures = manager.pending(viewer.getServer()).stream()
                .map(value -> new NoveLiveAdminData.RuptureEntry(value.id(), value.playerId().toString(),
                        value.playerName(), value.timestamp(), NoveLiveCauseNames.translate(value.cause()), value.dimension(), value.x(), value.y(),
                        value.z(), value.killer(), value.weapon())).toList();
        String selected = selectedId == null ? "" : selectedId;
        String requestedSelection = selected;
        if (!requestedSelection.isEmpty() && souls.stream().noneMatch(value -> value.id().equals(requestedSelection))) selected = "";
        PacketDistributor.sendToPlayer(viewer, new NoveLiveAdminPayload(GSON.toJson(new NoveLiveAdminData(
                manager.canonicalMode(viewer.getServer()), selected, souls, ruptures, feedback == null ? "" : feedback)), updateOnly));
    }

    public static void refreshAdmins(net.minecraft.server.MinecraftServer server) {
        int permission = NoverisConfig.load(server).permissionNoveLiveAdmin;
        server.getPlayerList().getPlayers().stream()
                .filter(player -> player.createCommandSourceStack().hasPermission(permission))
                .forEach(player -> send(player, "", "", true));
    }

    public NoveLiveAdminData data() { return GSON.fromJson(json, NoveLiveAdminData.class); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE_ID; }
}

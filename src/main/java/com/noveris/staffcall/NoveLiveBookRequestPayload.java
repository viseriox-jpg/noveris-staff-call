package com.noveris.staffcall;

import com.google.gson.Gson;
import com.noveris.staffcall.novelive.NoveLiveManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public record NoveLiveBookRequestPayload() implements CustomPacketPayload {
    public static final NoveLiveBookRequestPayload INSTANCE = new NoveLiveBookRequestPayload();
    public static final Type<NoveLiveBookRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "novelive_book_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoveLiveBookRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    private static final Gson GSON = new Gson();

    public static void sendBook(ServerPlayer player) {
        boolean viewAll = player.createCommandSourceStack().hasPermission(
                NoverisConfig.load(player.getServer()).permissionNoveLiveBookAll);
        List<NoveLiveManager.SoulView> souls = viewAll ? NoveLiveManager.INSTANCE.souls(player.getServer())
                : List.of(NoveLiveManager.INSTANCE.soul(player.getServer(), player));
        List<NoveLiveBookPayload.Entry> entries = souls.stream().map(value -> new NoveLiveBookPayload.Entry(
                value.name(), value.fragments(), value.state().label, value.marked())).toList();
        PacketDistributor.sendToPlayer(player, new NoveLiveBookPayload(
                NoveLiveManager.INSTANCE.canonicalMode(player.getServer()), viewAll, GSON.toJson(entries)));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

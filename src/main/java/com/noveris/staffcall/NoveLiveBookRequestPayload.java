package com.noveris.staffcall;

import com.noveris.staffcall.novelive.NoveLiveManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record NoveLiveBookRequestPayload() implements CustomPacketPayload {
    public static final NoveLiveBookRequestPayload INSTANCE = new NoveLiveBookRequestPayload();
    public static final Type<NoveLiveBookRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "novelive_destination_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoveLiveBookRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static void sendDestination(ServerPlayer viewer, ServerPlayer target) {
        NoveLiveManager.SoulView soul = NoveLiveManager.INSTANCE.soul(viewer.getServer(), target);
        PacketDistributor.sendToPlayer(viewer, new NoveLiveBookPayload(
                NoveLiveManager.INSTANCE.canonicalMode(viewer.getServer()),
                NoveLiveManager.INSTANCE.pendingFor(viewer.getServer(), target.getUUID()),
                soul.name(), soul.fragments(), soul.state().label, soul.marked()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

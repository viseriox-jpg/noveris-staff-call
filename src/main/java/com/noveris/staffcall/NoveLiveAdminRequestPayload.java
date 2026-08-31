package com.noveris.staffcall;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NoveLiveAdminRequestPayload(String selectedPlayerId) implements CustomPacketPayload {
    public static final Type<NoveLiveAdminRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "novelive_admin_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoveLiveAdminRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NoveLiveAdminRequestPayload::selectedPlayerId, NoveLiveAdminRequestPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

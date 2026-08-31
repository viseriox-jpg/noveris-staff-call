package com.noveris.staffcall;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NoveLiveBookPayload(boolean canonicalMode, int pendingRuptures, String name,
                                  int fragments, String state, boolean marked) implements CustomPacketPayload {
    public static final Type<NoveLiveBookPayload> TYPE_ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "novelive_destination"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoveLiveBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NoveLiveBookPayload::canonicalMode,
            ByteBufCodecs.VAR_INT, NoveLiveBookPayload::pendingRuptures,
            ByteBufCodecs.STRING_UTF8, NoveLiveBookPayload::name,
            ByteBufCodecs.VAR_INT, NoveLiveBookPayload::fragments,
            ByteBufCodecs.STRING_UTF8, NoveLiveBookPayload::state,
            ByteBufCodecs.BOOL, NoveLiveBookPayload::marked,
            NoveLiveBookPayload::new);

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE_ID; }
}

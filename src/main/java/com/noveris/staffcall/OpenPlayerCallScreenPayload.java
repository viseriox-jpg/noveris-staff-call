package com.noveris.staffcall;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenPlayerCallScreenPayload() implements CustomPacketPayload {
    public static final OpenPlayerCallScreenPayload INSTANCE = new OpenPlayerCallScreenPayload();
    public static final Type<OpenPlayerCallScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "open_player_call_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPlayerCallScreenPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

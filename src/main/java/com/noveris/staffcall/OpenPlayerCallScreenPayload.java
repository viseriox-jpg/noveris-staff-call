package com.noveris.staffcall;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenPlayerCallScreenPayload(int minLength, int maxLength) implements CustomPacketPayload {
    public static final Type<OpenPlayerCallScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "open_player_call_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPlayerCallScreenPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, OpenPlayerCallScreenPayload::minLength,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, OpenPlayerCallScreenPayload::maxLength,
            OpenPlayerCallScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

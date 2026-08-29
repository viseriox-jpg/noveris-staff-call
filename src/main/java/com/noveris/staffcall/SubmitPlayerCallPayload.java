package com.noveris.staffcall;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SubmitPlayerCallPayload(String callType, String reason) implements CustomPacketPayload {
    public static final Type<SubmitPlayerCallPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "submit_player_call"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitPlayerCallPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SubmitPlayerCallPayload::callType,
                    ByteBufCodecs.STRING_UTF8, SubmitPlayerCallPayload::reason,
                    SubmitPlayerCallPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

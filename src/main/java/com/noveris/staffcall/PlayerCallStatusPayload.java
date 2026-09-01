package com.noveris.staffcall;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayerCallStatusPayload(String callType, String state, String reason, String staff,
                                      int remainingSeconds, boolean canCancel) implements CustomPacketPayload {
    public static final Type<PlayerCallStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "player_call_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerCallStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, PlayerCallStatusPayload::callType,
                    ByteBufCodecs.STRING_UTF8, PlayerCallStatusPayload::state,
                    ByteBufCodecs.STRING_UTF8, PlayerCallStatusPayload::reason,
                    ByteBufCodecs.STRING_UTF8, PlayerCallStatusPayload::staff,
                    ByteBufCodecs.VAR_INT, PlayerCallStatusPayload::remainingSeconds,
                    ByteBufCodecs.BOOL, PlayerCallStatusPayload::canCancel,
                    PlayerCallStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

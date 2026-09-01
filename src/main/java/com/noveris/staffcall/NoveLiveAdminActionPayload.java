package com.noveris.staffcall;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NoveLiveAdminActionPayload(String action, String playerId, int amount, long ruptureId)
        implements CustomPacketPayload {
    public static final Type<NoveLiveAdminActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "novelive_admin_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoveLiveAdminActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NoveLiveAdminActionPayload::action,
            ByteBufCodecs.STRING_UTF8, NoveLiveAdminActionPayload::playerId,
            ByteBufCodecs.VAR_INT, NoveLiveAdminActionPayload::amount,
            ByteBufCodecs.VAR_LONG, NoveLiveAdminActionPayload::ruptureId,
            NoveLiveAdminActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package com.noveris.staffcall;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record NoveLiveBookPayload(boolean canonicalMode, boolean viewAll, String entriesJson)
        implements CustomPacketPayload {
    public static final Type<NoveLiveBookPayload> TYPE_ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NoverisStaffCall.MOD_ID, "novelive_book"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NoveLiveBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NoveLiveBookPayload::canonicalMode,
            ByteBufCodecs.BOOL, NoveLiveBookPayload::viewAll,
            ByteBufCodecs.STRING_UTF8, NoveLiveBookPayload::entriesJson,
            NoveLiveBookPayload::new);
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type ENTRY_LIST = new TypeToken<List<Entry>>() { }.getType();

    public List<Entry> entries() {
        List<Entry> values = GSON.fromJson(entriesJson, ENTRY_LIST);
        return values == null ? List.of() : values;
    }

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE_ID; }

    public record Entry(String name, int fragments, String state, boolean marked) { }
}

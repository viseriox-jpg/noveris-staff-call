package com.noveris.staffcall.client;

import com.noveris.staffcall.OpenPlayerCallScreenPayload;
import com.noveris.staffcall.PlayerCallStatusPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NoverisClientEvents {
    private NoverisClientEvents() {
    }

    public static void handleOpenScreen(OpenPlayerCallScreenPayload payload, IPayloadContext context) {
        Minecraft.getInstance().setScreen(new PlayerCallScreen(payload.minLength(), payload.maxLength()));
    }

    public static void handleStatus(PlayerCallStatusPayload payload, IPayloadContext context) {
        if (Minecraft.getInstance().screen instanceof PlayerCallScreen screen) screen.updateStatus(payload);
    }
}

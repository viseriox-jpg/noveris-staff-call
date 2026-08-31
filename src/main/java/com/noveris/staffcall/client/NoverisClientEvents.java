package com.noveris.staffcall.client;

import com.noveris.staffcall.OpenPlayerCallScreenPayload;
import com.noveris.staffcall.PlayerCallStatusPayload;
import com.noveris.staffcall.NoveLiveBookPayload;
import com.noveris.staffcall.NoveLiveAdminPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NoverisClientEvents {
    private NoverisClientEvents() {
    }

    public static void handleOpenScreen(OpenPlayerCallScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new PlayerCallScreen(payload.minLength(), payload.maxLength())));
    }

    public static void handleStatus(PlayerCallStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof PlayerCallScreen screen) screen.updateStatus(payload);
        });
    }

    public static void handleNoveLiveBook(NoveLiveBookPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Minecraft.getInstance().setScreen(new NoveLiveBookScreen(payload)));
    }

    public static void handleNoveLiveAdmin(NoveLiveAdminPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof NoveLiveAdminScreen screen) screen.update(payload);
            else Minecraft.getInstance().setScreen(new NoveLiveAdminScreen(payload));
        });
    }
}

package com.noveris.staffcall.client;

import com.noveris.staffcall.NoveLiveBookRequestPayload;
import com.noveris.staffcall.NoverisStaffCall;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NoverisStaffCall.MOD_ID, value = Dist.CLIENT)
public final class NoveLiveClientGameEvents {
    private NoveLiveClientGameEvents() { }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (NoveLiveClientModEvents.OPEN_SOUL_BOOK.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(NoveLiveBookRequestPayload.INSTANCE);
            }
        }
    }
}

package com.noveris.staffcall.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.noveris.staffcall.NoverisStaffCall;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = NoverisStaffCall.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class NoveLiveClientModEvents {
    public static final KeyMapping OPEN_SOUL_BOOK = new KeyMapping(
            "key.noveris_staff_call.open_soul_book", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.noveris_staff_call");

    private NoveLiveClientModEvents() { }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SOUL_BOOK);
    }
}

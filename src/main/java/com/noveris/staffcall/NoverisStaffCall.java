package com.noveris.staffcall;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.noveris.staffcall.novelive.NoveLiveEvents;

@Mod(NoverisStaffCall.MOD_ID)
public final class NoverisStaffCall {
    public static final String MOD_ID = "noveris_staff_call";
    static final Logger LOGGER = LogUtils.getLogger();
    static final StaffCallEvents EVENTS = new StaffCallEvents();
    static final NoveLiveEvents NOVE_LIVE_EVENTS = new NoveLiveEvents();

    public NoverisStaffCall(IEventBus modBus) {
        modBus.addListener(NoverisNetwork::register);
        NeoForge.EVENT_BUS.register(EVENTS);
        NeoForge.EVENT_BUS.register(NOVE_LIVE_EVENTS);
    }
}

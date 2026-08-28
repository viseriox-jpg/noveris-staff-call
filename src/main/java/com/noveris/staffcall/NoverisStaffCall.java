package com.noveris.staffcall;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(NoverisStaffCall.MOD_ID)
public final class NoverisStaffCall {
    public static final String MOD_ID = "noveris_staff_call";
    static final Logger LOGGER = LogUtils.getLogger();

    public NoverisStaffCall() {
        NeoForge.EVENT_BUS.register(new StaffCallEvents());
    }
}

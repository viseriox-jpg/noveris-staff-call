package com.noveris.staffcall;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(NoverisStaffCall.MOD_ID)
public final class NoverisStaffCall {
    public static final String MOD_ID = "noveris_staff_call";

    public NoverisStaffCall() {
        NeoForge.EVENT_BUS.register(new StaffCallEvents());
    }
}

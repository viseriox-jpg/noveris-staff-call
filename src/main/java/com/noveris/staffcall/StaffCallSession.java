package com.noveris.staffcall;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class StaffCallSession {
    static final int TOTAL_TICKS = 160;      // 8 seconds
    static final int LIFT_FROM_TICK = 100;   // controlled ascent during final 3 seconds
    static final double LIFT_HEIGHT = 6.0;

    final UUID staffId;
    final UUID targetId;
    final String staffName;
    final String targetName;
    final ResourceKey<Level> targetStartDimension;
    final Vec3 targetStartPosition;
    final float targetStartYaw;
    final float targetStartPitch;
    final ServerBossEvent progressBar;
    final CallPalette palette;

    int age;

    StaffCallSession(UUID staffId, UUID targetId, String staffName, String targetName,
                     ResourceKey<Level> targetStartDimension,
                     Vec3 targetStartPosition, float targetStartYaw, float targetStartPitch,
                     ServerBossEvent progressBar, CallPalette palette) {
        this.staffId = staffId;
        this.targetId = targetId;
        this.staffName = staffName;
        this.targetName = targetName;
        this.targetStartDimension = targetStartDimension;
        this.targetStartPosition = targetStartPosition;
        this.targetStartYaw = targetStartYaw;
        this.targetStartPitch = targetStartPitch;
        this.progressBar = progressBar;
        this.palette = palette;
    }
}

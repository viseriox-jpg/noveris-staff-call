package com.noveris.staffcall;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class StaffCallSession {
    static final int TOTAL_TICKS = 80;      // 4 seconds
    static final int LOCK_FROM_TICK = 50;   // freeze only for final 1.5 seconds

    final UUID staffId;
    final UUID targetId;
    final ResourceKey<Level> targetStartDimension;
    final Vec3 targetStartPosition;
    final float targetStartYaw;
    final float targetStartPitch;

    int age;

    StaffCallSession(UUID staffId, UUID targetId, ResourceKey<Level> targetStartDimension,
                     Vec3 targetStartPosition, float targetStartYaw, float targetStartPitch) {
        this.staffId = staffId;
        this.targetId = targetId;
        this.targetStartDimension = targetStartDimension;
        this.targetStartPosition = targetStartPosition;
        this.targetStartYaw = targetStartYaw;
        this.targetStartPitch = targetStartPitch;
    }
}

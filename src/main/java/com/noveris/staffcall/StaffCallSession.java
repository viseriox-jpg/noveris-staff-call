package com.noveris.staffcall;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class StaffCallSession {

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
    final int totalTicks;
    final int liftFromTick;
    final double liftHeight;
    final double arrivalDistance;
    final PlayerCallType playerCallType;

    int age;

    StaffCallSession(UUID staffId, UUID targetId, String staffName, String targetName,
                     ResourceKey<Level> targetStartDimension,
                     Vec3 targetStartPosition, float targetStartYaw, float targetStartPitch,
                     ServerBossEvent progressBar, CallPalette palette, NoverisConfig config) {
        this(staffId, targetId, staffName, targetName, targetStartDimension, targetStartPosition,
                targetStartYaw, targetStartPitch, progressBar, palette, config, null);
    }

    StaffCallSession(UUID staffId, UUID targetId, String staffName, String targetName,
                     ResourceKey<Level> targetStartDimension,
                     Vec3 targetStartPosition, float targetStartYaw, float targetStartPitch,
                     ServerBossEvent progressBar, CallPalette palette, NoverisConfig config,
                     PlayerCallType playerCallType) {
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
        this.totalTicks = config.durationTicks;
        this.liftFromTick = Math.max(0, totalTicks - 60);
        this.liftHeight = config.levitationHeight;
        this.arrivalDistance = config.arrivalDistance;
        this.playerCallType = playerCallType;
    }
}

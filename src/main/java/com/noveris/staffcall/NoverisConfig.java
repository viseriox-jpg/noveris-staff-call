package com.noveris.staffcall;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class NoverisConfig {
    private static final String FILE_NAME = "noveris_staff_call-server.toml";
    private static final List<String> DEFAULT_FILE = List.of(
            "# Noveris Staff Call - configuração do servidor",
            "duration_seconds = 8",
            "levitation_height = 6.0",
            "arrival_distance = 8.0",
            "confirmation_timeout_seconds = 30",
            "timezone = \"America/Sao_Paulo\"",
            "permission_call = 2",
            "permission_force = 2",
            "permission_cancel = 2",
            "permission_status = 2",
            "permission_return = 2",
            "permission_history = 2"
    );

    final int durationTicks;
    final double levitationHeight;
    final double arrivalDistance;
    final int confirmationTimeoutTicks;
    final ZoneId timezone;
    final int permissionCall;
    final int permissionForce;
    final int permissionCancel;
    final int permissionStatus;
    final int permissionReturn;
    final int permissionHistory;

    private NoverisConfig(Map<String, String> values) {
        durationTicks = integer(values, "duration_seconds", 8, 4, 60) * 20;
        levitationHeight = decimal(values, "levitation_height", 6.0, 0.0, 32.0);
        arrivalDistance = decimal(values, "arrival_distance", 8.0, 2.0, 32.0);
        confirmationTimeoutTicks = integer(values, "confirmation_timeout_seconds", 30, 5, 300) * 20;
        timezone = zone(values.getOrDefault("timezone", "America/Sao_Paulo"));
        permissionCall = permission(values, "permission_call", 2);
        permissionForce = permission(values, "permission_force", 2);
        permissionCancel = permission(values, "permission_cancel", 2);
        permissionStatus = permission(values, "permission_status", 2);
        permissionReturn = permission(values, "permission_return", 2);
        permissionHistory = permission(values, "permission_history", 2);
    }

    static NoverisConfig load(MinecraftServer server) {
        Path path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("serverconfig").resolve(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) Files.write(path, DEFAULT_FILE);
            Map<String, String> values = new HashMap<>();
            for (String raw : Files.readAllLines(path)) {
                String line = raw.split("#", 2)[0].trim();
                int separator = line.indexOf('=');
                if (separator > 0) {
                    String value = line.substring(separator + 1).trim();
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.put(line.substring(0, separator).trim(), value);
                }
            }
            return new NoverisConfig(values);
        } catch (IOException | RuntimeException exception) {
            NoverisStaffCall.LOGGER.error("Não foi possível carregar {}. Usando valores padrão.", path, exception);
            return new NoverisConfig(Map.of());
        }
    }

    private static int integer(Map<String, String> values, String key, int fallback, int min, int max) {
        try { return Math.clamp(Integer.parseInt(values.getOrDefault(key, String.valueOf(fallback))), min, max); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static double decimal(Map<String, String> values, String key, double fallback, double min, double max) {
        try { return Math.clamp(Double.parseDouble(values.getOrDefault(key, String.valueOf(fallback))), min, max); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static int permission(Map<String, String> values, String key, int fallback) {
        return integer(values, key, fallback, 0, 4);
    }

    private static ZoneId zone(String value) {
        try { return ZoneId.of(value); }
        catch (RuntimeException ignored) { return ZoneId.of("America/Sao_Paulo"); }
    }
}

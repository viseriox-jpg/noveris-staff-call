package com.noveris.staffcall.novelive;

import com.google.gson.Gson;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NoveLiveStorage {
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "noveris_novelive.json";

    Data load(MinecraftServer server) {
        Path path = path(server);
        if (!Files.exists(path)) return new Data();
        try {
            Data data = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
            return data == null ? new Data() : data.normalize();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Não foi possível carregar os Fragmentos da Alma", exception);
        }
    }

    void save(MinecraftServer server, Data data) {
        Path path = path(server);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível persistir os Fragmentos da Alma", exception);
        }
    }

    private Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    static final class Data {
        int version = 2;
        boolean canonicalMode;
        long nextRuptureId = 1;
        Map<String, SoulRecord> souls = new LinkedHashMap<>();
        List<RuptureRecord> ruptures = new ArrayList<>();
        List<ChangeRecord> history = new ArrayList<>();

        Data normalize() {
            if (souls == null) souls = new LinkedHashMap<>();
            if (ruptures == null) ruptures = new ArrayList<>();
            if (history == null) history = new ArrayList<>();
            if (nextRuptureId < 1) nextRuptureId = 1;
            if (version < 2) {
                souls.values().forEach(soul -> {
                    if (soul.fragments > 3) {
                        soul.reserves += soul.fragments - 3;
                        soul.fragments = 3;
                    }
                });
                version = 2;
            }
            return this;
        }
    }

    static final class SoulRecord {
        String name;
        int fragments = 3;
        int reserves;
        boolean marked;

        SoulRecord() { }
        SoulRecord(String name) { this.name = name; }
    }

    static final class RuptureRecord {
        long id;
        String playerId;
        String playerName;
        long timestamp;
        String cause;
        String dimension;
        int x;
        int y;
        int z;
        String killer;
        String weapon;
        RuptureStatus status = RuptureStatus.PENDENTE;
        String reviewer = "-";
        String reviewReason = "-";
    }

    static final class ChangeRecord {
        long timestamp;
        String playerId;
        String playerName;
        int before;
        int after;
        int reservesBefore;
        int reservesAfter;
        SoulChangeType type;
        String origin;
        String administrator;
        String reason;
    }
}

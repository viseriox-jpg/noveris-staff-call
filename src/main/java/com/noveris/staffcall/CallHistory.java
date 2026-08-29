package com.noveris.staffcall;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class CallHistory {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final int MAX_ENTRIES = 500;
    private static final String FILE_NAME = "noveris_staff_call_history.jsonl";

    private final Deque<Entry> entries = new ArrayDeque<>();
    private boolean loaded;

    void record(MinecraftServer server, String action, String staff, String target,
                String palette, String origin, String destination) {
        record(server, action, staff, target, palette, origin, destination, "-");
    }

    void record(MinecraftServer server, String action, String staff, String target,
                String palette, String origin, String destination, String detail) {
        ensureLoaded(server);
        Entry entry = new Entry(System.currentTimeMillis(), action, staff, target,
                palette, origin, destination, detail);
        entries.addLast(entry);
        trim();

        try {
            Files.writeString(historyPath(server), GSON.toJson(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            LOGGER.error("Could not persist Noveris Staff Call history", exception);
        }
    }

    List<Entry> findForPlayer(MinecraftServer server, String playerName, int limit) {
        ensureLoaded(server);
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries.reversed()) {
            if (entry.target.equalsIgnoreCase(playerName)) {
                matches.add(entry);
                if (matches.size() >= limit) break;
            }
        }
        return matches;
    }

    private void ensureLoaded(MinecraftServer server) {
        if (loaded) return;
        loaded = true;
        Path path = historyPath(server);
        if (!Files.exists(path)) return;

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    Entry entry = GSON.fromJson(line, Entry.class);
                    if (entry != null) entries.addLast(entry);
                } catch (JsonSyntaxException exception) {
                    LOGGER.warn("Ignoring malformed Noveris Staff Call history entry");
                }
            }
            trim();
        } catch (IOException exception) {
            LOGGER.error("Could not load Noveris Staff Call history", exception);
        }
    }

    private void trim() {
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
    }

    private Path historyPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    static final class Entry {
        long timestamp;
        String action;
        String staff;
        String target;
        String palette;
        String origin;
        String destination;
        String detail;

        @SuppressWarnings("unused")
        Entry() {
        }

        Entry(long timestamp, String action, String staff, String target,
              String palette, String origin, String destination, String detail) {
            this.timestamp = timestamp;
            this.action = action;
            this.staff = staff;
            this.target = target;
            this.palette = palette;
            this.origin = origin;
            this.destination = destination;
            this.detail = detail;
        }
    }
}

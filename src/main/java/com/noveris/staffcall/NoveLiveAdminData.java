package com.noveris.staffcall;

import java.util.List;

public record NoveLiveAdminData(boolean canonicalMode, String selectedPlayerId, List<SoulEntry> souls,
                                List<RuptureEntry> ruptures, String feedback) {
    public record SoulEntry(String id, String name, int fragments, String state, boolean marked,
                            int reserves, int maxReserves, int pendingRuptures, int historyCount,
                            List<HistoryEntry> recentHistory) { }

    public record HistoryEntry(long timestamp, int before, int after, int reservesBefore, int reservesAfter,
                               String type, String administrator) { }

    public record RuptureEntry(long id, String playerId, String playerName, long timestamp, String cause,
                               String dimension, int x, int y, int z, String killer, String weapon,
                               int fragments, int reserves) { }
}

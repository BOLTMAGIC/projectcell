package com.prc.projectcell.ae2;

import appeng.api.stacks.AEItemKey;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Caches the per-item data getAvailableStacks() needs (EMC value, AE2 key, NBT-filter status)
 * so it is not recomputed for the whole knowledge list on every call.
 * <p>
 * None of this depends on the player's EMC balance, which is read live, so a snapshot stays
 * valid until the player's knowledge or the EMC mapping changes. Invalidation is driven by
 * ProjectE events (see SnapshotInvalidationListener); a knowledge-size check is kept as a
 * safety net for changes that do not fire an event.
 */
public class KnowledgeSnapshot {
    /** A cell with the NBT filter on sees a different item list, so each mode has its own snapshot. */
    private record SnapshotKey(UUID playerId, boolean nbtFilter) {
    }

    private static final Map<SnapshotKey, CachedSnapshot> SNAPSHOTS = new HashMap<>();

    public static class CachedSnapshot {
        public final List<ComputedItemData> items;
        public final int knowledgeSize;

        public CachedSnapshot(List<ComputedItemData> items, int knowledgeSize) {
            this.items = items;
            this.knowledgeSize = knowledgeSize;
        }
    }

    public record ComputedItemData(AEItemKey key, long emcValue) {
    }

    public static synchronized CachedSnapshot getOrCreateSnapshot(
            UUID playerId, IKnowledgeProvider provider, boolean nbtFilter) {

        Collection<ItemInfo> knowledge = provider.getKnowledge();
        SnapshotKey snapshotKey = new SnapshotKey(playerId, nbtFilter);
        CachedSnapshot cached = SNAPSHOTS.get(snapshotKey);
        if (cached != null && cached.knowledgeSize == knowledge.size()) {
            return cached;
        }

        List<ComputedItemData> items = new ArrayList<>(knowledge.size());
        for (ItemInfo info : knowledge) {
            long emcValue = EMCValueCache.getValue(info);
            if (emcValue <= 0) {
                continue;
            }
            AEItemKey key = EMCMEStorage.createKey(info);
            if (key == null) {
                continue;
            }
            // Blocked items are never listed, so there is no need to keep them.
            if (nbtFilter && EMCMEStorage.isBlockedPublic(key)) {
                continue;
            }
            items.add(new ComputedItemData(key, emcValue));
        }

        CachedSnapshot snapshot = new CachedSnapshot(items, knowledge.size());
        SNAPSHOTS.put(snapshotKey, snapshot);
        return snapshot;
    }

    public static synchronized void invalidateSnapshot(UUID playerId) {
        SNAPSHOTS.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public static synchronized void clearAll() {
        SNAPSHOTS.clear();
    }
}

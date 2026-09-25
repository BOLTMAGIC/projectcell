package com.prc.projectcell.event;

import com.prc.projectcell.ae2.EMCValueCache;
import com.prc.projectcell.ae2.KnowledgeSnapshot;
import com.prc.projectcell.ae2.NBTDecisionCache;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drops cached knowledge snapshots whenever the data they were built from changes.
 */
@Mod.EventBusSubscriber(modid = "projectcell", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SnapshotInvalidationListener {

    @SubscribeEvent
    public static void onKnowledgeChange(PlayerKnowledgeChangeEvent event) {
        KnowledgeSnapshot.invalidateSnapshot(event.getPlayerUUID());
    }

    /** EMC values were recalculated (server start, /reload): every cached value may be stale. */
    @SubscribeEvent
    public static void onEmcRemap(EMCRemapEvent event) {
        EMCValueCache.clearCache();
        NBTDecisionCache.clear();
        KnowledgeSnapshot.clearAll();
    }

    /** Respawn or return from the End: the player's knowledge is copied into a new provider. */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        KnowledgeSnapshot.invalidateSnapshot(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        KnowledgeSnapshot.invalidateSnapshot(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        KnowledgeSnapshot.clearAll();
    }
}

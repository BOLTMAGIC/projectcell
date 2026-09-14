package com.prc.projectcell.event;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.prc.projectcell.ae2.StorageQueryBatcher;
import com.prc.projectcell.ae2.EMCValueCache;
import com.prc.projectcell.ae2.NBTDecisionCache;
import com.prc.projectcell.ae2.KnowledgeSnapshot;

/**
 * Server tick event listener for batch processing and cache maintenance.
 * <p>
 * Handles:
 * - Storage query batch processing
 * - Periodic cache clearing
 * - Thread pool monitoring
 * - Knowledge snapshot tick updates
 */
@Mod.EventBusSubscriber(modid = "projectcell", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerTickEventListener {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // CRITICAL: Update knowledge snapshot tick counter (enables cache invalidation)
        KnowledgeSnapshot.updateTick();

        // Process batched queries
        StorageQueryBatcher.processBatch();

        // Periodic cache clearing
        if (++tickCounter % 20 == 0) {  // Every second (20 ticks)
            EMCValueCache.clearCache();
            NBTDecisionCache.clear();
        }

        // Monitor async thread pool
        if (tickCounter % 100 == 0) {  // Every 5 seconds
            // Log if too many queued tasks
            String asyncStats = com.prc.projectcell.ae2.AsyncEMCProcessor.getStats();
        }
    }
}

package com.prc.projectcell.event;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.prc.projectcell.ae2.EMCValueCache;
import com.prc.projectcell.ae2.NBTDecisionCache;

/**
 * Server tick event listener for periodic cache clearing.
 */
@Mod.EventBusSubscriber(modid = "projectcell", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerTickEventListener {
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Periodic cache clearing
        if (++tickCounter % 20 == 0) {  // Every second (20 ticks)
            EMCValueCache.clearCache();
            NBTDecisionCache.clear();
        }
    }
}

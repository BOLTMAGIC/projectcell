package com.prc.projectcell.util;

import java.util.UUID;
import javax.annotation.Nullable;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.server.ServerLifecycleHooks;

public class ProjectEUtil {
   @Nullable
   public static ServerPlayer getPlayer(UUID uuid) {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      return server == null ? null : server.getPlayerList().getPlayer(uuid);
   }

   /**
    * Always resolves the provider from the current player entity. It must not be cached:
    * respawning or relogging creates a new player entity with a new provider, and writes
    * to the old one (EMC added by an insert, say) would be lost.
    */
   @Nullable
   public static IKnowledgeProvider getKnowledgeProvider(UUID uuid) {
      ServerPlayer player = getPlayer(uuid);
      return player == null ? null : getKnowledgeProvider(player);
   }

   @Nullable
   public static IKnowledgeProvider getKnowledgeProvider(Player player) {
      return player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).resolve().orElse(null);
   }
}

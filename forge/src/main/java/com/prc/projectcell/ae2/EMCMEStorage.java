package com.prc.projectcell.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.prc.projectcell.Config;
import com.prc.projectcell.util.ProjectEUtil;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class EMCMEStorage implements StorageCell {
   // ===== CACHING LAYER ======
   // Cache for ItemInfo -> AEItemKey to reduce AEItemKey.of() overhead
   // WeakHashMap ensures ItemInfo garbage collection
   private static final Map<ItemInfo, AEItemKey> KEY_CACHE = new WeakHashMap<>();
   private static final int KEY_CACHE_MAX_SIZE = 1024;
   private static int cacheClears = 0;

   private final UUID owner;
   private final boolean nbtFilter;

   private static long limit() {
      return Config.SERVER.spec.isLoaded() && !Config.SERVER.limitItemCount.get() ? Long.MAX_VALUE : 2147483647L;

   }

    private static boolean hasBlockedNbt(ItemStack stack) {
       CompoundTag tag = stack.getTag();
       if (tag == null || tag.isEmpty()) return false;
       if (tag.contains("Enchantments")) return true;
       // Fast path: check Damage tag without copying
       if (tag.contains("Damage", Tag.TAG_ANY_NUMERIC) && tag.getInt("Damage") > 0) return true;
       if (tag.contains("display")) {
          CompoundTag display = tag.getCompound("display");
           return display.contains("Name") || display.contains("Lore");
       }
       return false;
    }

   private static boolean hasBlockedNbt(AEItemKey key) {
      return hasBlockedNbt(key.toStack());
   }

      public static AEItemKey createKey(ItemInfo info) {
        // Try to get from cache first
        AEItemKey cached = KEY_CACHE.get(info);
        if (cached != null) {
           return cached;
        }

        ItemStack stack = info.createStack();
        // Only modify tag if Damage is zero (avoid unnecessary copy)
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("Damage", Tag.TAG_ANY_NUMERIC) && tag.getInt("Damage") == 0) {
           // Create a minimal copy only if we need to modify
           if (!tag.isEmpty() && tag.size() > 1) {
              stack = stack.copy();
               assert stack.getTag() != null;
               stack.getTag().remove("Damage");
           } else {
              stack.setTag(null);
           }
        }
        AEItemKey key = AEItemKey.of(stack);

        // Cache the result
        if (KEY_CACHE.size() < KEY_CACHE_MAX_SIZE) {
           KEY_CACHE.put(info, key);
        } else if (++cacheClears % 10 == 0) {
           // Periodically clear cache to avoid memory bloat
           KEY_CACHE.clear();
        }

        return key;
     }

    private static boolean hasNonPersistentNbt(AEItemKey key) {
       ItemStack stack = key.toStack();
       ItemInfo info = ItemInfo.fromStack(stack);
       ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(info);

       // Fast path: compare without creating stack copy
       if (ItemStack.isSameItemSameTags(stack, persistent.createStack())) {
          return false;
       }

       CompoundTag tag = stack.getTag();
       if (tag != null && tag.contains("Damage", Tag.TAG_ANY_NUMERIC) && tag.getInt("Damage") == 0) {
          // Only copy if we need to check the cleaned version
          ItemStack cleaned = stack.copy();
          CompoundTag cleanedTag = cleaned.getTag();
          if (cleanedTag != null) {
             cleanedTag.remove("Damage");
             if (cleanedTag.isEmpty()) cleaned.setTag(null);
          }
           return !ItemStack.isSameItemSameTags(cleaned, persistent.createStack());
       }
       return true;
    }

    private static boolean isBlocked(AEItemKey key) {
        // Check cache first
        Boolean cached = NBTDecisionCache.getCachedBlockStatus(key);
        if (cached != null) {
           return cached;
        }

        // Cache miss - compute result
        boolean blocked = hasBlockedNbt(key) || hasNonPersistentNbt(key);

        // Store in cache
        NBTDecisionCache.cacheBlockStatus(key, blocked);

        return blocked;
     }

    public static boolean isBlockedPublic(AEItemKey key) {
        return isBlocked(key);
    }

   public EMCMEStorage(UUID owner, boolean nbtFilter, ISaveProvider host) {
      this.owner = owner;
      this.nbtFilter = nbtFilter;
   }

   public CellState getStatus() {
      return CellState.NOT_EMPTY;
   }

   public double getIdleDrain() {
      return 0.0;
   }

   public void persist() {
   }

   public boolean canFitInsideCell() {
      return true;
   }

    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
       if (what instanceof AEItemKey itemKey) {
          // Check EMC value first (very fast)
          ItemStack stack = itemKey.toStack();
          if (!IEMCProxy.INSTANCE.hasValue(stack)) {
             return false;
          }
          // Only do expensive NBT checks if necessary
          return !this.nbtFilter || !isBlocked(itemKey);
       }
       return false;
    }

    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
       amount = Math.min(amount, limit());
       if (!(what instanceof AEItemKey itemKey) || amount <= 0L) {
          return 0L;
       }
       ItemStack singleStack = itemKey.toStack(1);
       if (this.nbtFilter && isBlocked(itemKey)) {
          return 0L;
       }
       if (!IEMCProxy.INSTANCE.hasValue(singleStack)) {
          return 0L;
       }
       IKnowledgeProvider provider = ProjectEUtil.getKnowledgeProvider(this.owner);
       if (provider == null) {
          return 0L;
       }
       // Refuse items that would give no EMC, otherwise they are accepted and voided.
       long sellValue = IEMCProxy.INSTANCE.getSellValue(singleStack);
       if (sellValue <= 0L) {
          return 0L;
       }
       if (mode == Actionable.MODULATE) {
          BigInteger totalValue = BigInteger.valueOf(sellValue).multiply(BigInteger.valueOf(amount));
          provider.setEmc(provider.getEmc().add(totalValue));
          ServerPlayer player = ProjectEUtil.getPlayer(this.owner);
          if (player != null) {
             if (provider.addKnowledge(singleStack)) {
                provider.syncKnowledgeChange(player, IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(singleStack)), true);
             }
             provider.syncEmc(player);
          }
       }
       return amount;
    }

     public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        amount = Math.min(amount, limit());
        if (!(what instanceof AEItemKey itemKey) || amount <= 0L) {
           return 0L;
        }
        if (this.nbtFilter && isBlocked(itemKey)) {
           return 0L;
        }

        ItemStack stack = itemKey.toStack();
        // Cache EMC value early instead of recalculating
        ItemInfo info = ItemInfo.fromStack(stack);
        long itemValue = EMCValueCache.getValue(info);
        if (itemValue <= 0L) {
            return 0L;
        }

        ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(info);
        // Check if the persistent ItemInfo has value (this normalizes NBT differences)
        if (EMCValueCache.getValue(persistent) <= 0L) {
            return 0L;
        }

        IKnowledgeProvider provider = ProjectEUtil.getKnowledgeProvider(this.owner);
        if (provider == null || !provider.hasKnowledge(persistent.createStack())) {
            return 0L;
        }

        BigInteger playerEmc = provider.getEmc();

        // Optimization: use safe long arithmetic instead of BigInteger division
        long maxExtractFromValue = SafeLongArithmetic.safeDivide(playerEmc, itemValue);
        long extractAmount = Math.min(maxExtractFromValue, amount);
        extractAmount = Math.min(extractAmount, limit());

        if (extractAmount <= 0L) {
           return 0L;
        }

        if (mode == Actionable.MODULATE) {
           BigInteger totalCost = BigInteger.valueOf(itemValue).multiply(BigInteger.valueOf(extractAmount));
           provider.setEmc(playerEmc.subtract(totalCost));
           ServerPlayer player = ProjectEUtil.getPlayer(this.owner);
           if (player != null) {
              provider.syncEmc(player);
           }
        }
        return extractAmount;
     }

     public void getAvailableStacks(KeyCounter out) {
        IKnowledgeProvider provider = ProjectEUtil.getKnowledgeProvider(this.owner);
        if (provider != null) {
           BigInteger playerEmc = provider.getEmc();
           long lim = limit();

           // Fast path: if player has no EMC, return empty
           if (playerEmc.signum() <= 0) {
              return;
           }

           // Per-item data is cached until the player's knowledge or the EMC mapping changes;
           // only the counts depend on the live EMC balance.
           KnowledgeSnapshot.CachedSnapshot snapshot =
              KnowledgeSnapshot.getOrCreateSnapshot(this.owner, provider, this.nbtFilter);

           for (KnowledgeSnapshot.ComputedItemData data : snapshot.items) {
              long maxCount = SafeLongArithmetic.safeDivide(playerEmc, data.emcValue());
              if (maxCount <= 0L) {
                 continue;
              }
              out.add(data.key(), Math.min(maxCount, lim));
           }
        }
     }

   public Component getDescription() {
      return Component.translatable("projectcell.ae2.emc_storage_description");
   }
}

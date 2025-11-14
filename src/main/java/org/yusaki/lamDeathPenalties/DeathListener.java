package org.yusaki.lamDeathPenalties;

import com.tcoded.folialib.FoliaLib;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

import static org.yusaki.lib.modules.MessageManager.placeholders;

public class DeathListener implements Listener {
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    private final FoliaLib foliaLib;
    private final Random random;
    private static final double BALANCE_EPSILON = 0.0001D;
    private static final String METADATA_KEY = "lmdp_dropped_items";
    private static final String METADATA_KEPT_ITEMS = "lmdp_kept_items";
    private static final String METADATA_PROCESSED = "lmdp_processed";
    
    
    public DeathListener(LamDeathPenalties plugin, SoulPointsManager soulPointsManager, FoliaLib foliaLib) {
        this.plugin = plugin;
        this.soulPointsManager = soulPointsManager;
        this.foliaLib = foliaLib;
        this.random = new Random();
    }
    
    /**
     * Public API for AxGraves to restore kept items after grave creation
     * This should be called after AxGraves has collected the dropped items
     */
    public void restoreKeptItemsFromMetadata(Player player) {
        if (!player.hasMetadata(METADATA_KEPT_ITEMS)) {
            return;
        }
        
        Object metadataValue = player.getMetadata(METADATA_KEPT_ITEMS).get(0).value();
        if (!(metadataValue instanceof KeptItemsData keptData)) {
            return;
        }
        
        // Schedule restoration using Folia-compatible scheduler
        foliaLib.getImpl().runNextTick(task -> {
            restoreKeptItemsToOriginalSlots(player, keptData.itemsToKeep, keptData.originalInventory, keptData.originalArmor, keptData.originalOffhand);
            
            // Clean up metadata
            player.removeMetadata(METADATA_KEPT_ITEMS, plugin);
            player.removeMetadata(METADATA_KEY, plugin);
            player.removeMetadata(METADATA_PROCESSED, plugin);
            
            plugin.getYskLib().logDebug(plugin, "Restored kept items for " + player.getName() + " after AxGraves grave creation");
        });
    }
    
    // Workaround for Folia - PlayerRespawnEvent doesn't fire on Folia
    // See: https://github.com/PaperMC/Folia/issues/105
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!plugin.isSoulPointsEnabled()) {
            return;
        }

        // Detect respawn: player inventory closes, player is alive and online with health > 0
        if (event.getInventory().getType() == InventoryType.CRAFTING
            && player.isOnline()
            && !player.isDead()
            && player.getHealth() > 0) {

            // Schedule task to reapply max health penalty after respawn
            foliaLib.getImpl().runAtEntityLater(player, task -> {
                if (player.isOnline()) {
                    soulPointsManager.refreshPlayerMaxHealth(player);
                    plugin.getYskLib().logDebug(plugin, "Reapplied max health penalty after respawn for " + player.getName());
                }
            }, 1L); // 1 tick delay
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        
        if (!plugin.getConfig().getBoolean("soul-points.max-soul-points.enabled", true)) {
            return;
        }
        
        // Check if the victim was killed by another player
        EntityDamageEvent lastDamageCause = victim.getLastDamageCause();
        if (!(lastDamageCause instanceof EntityDamageByEntityEvent damageByEntityEvent)) {
            return;
        }
        
        // Get the killer (could be direct player or projectile shooter)
        Player killer = null;
        if (damageByEntityEvent.getDamager() instanceof Player) {
            killer = (Player) damageByEntityEvent.getDamager();
        } else if (damageByEntityEvent.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                killer = (Player) projectile.getShooter();
            }
        }
        
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;  // No killer or suicide
        }
        
        // Check if killer has bypass permission
        if (killer.hasPermission("lmdp.bypass")) {
            return;
        }
        
        // Reduce killer's max soul points
        int reductionAmount = plugin.getConfig().getInt("soul-points.max-soul-points.reduction-per-kill", 1);
        int oldMax = soulPointsManager.getMaxSoulPoints(killer.getUniqueId());
        
        soulPointsManager.reduceMaxSoulPoints(killer.getUniqueId(), reductionAmount);
        
        int newMax = soulPointsManager.getMaxSoulPoints(killer.getUniqueId());
        
        // Notify killer if their max was reduced
        if (newMax < oldMax) {
            int reduction = oldMax - newMax;
            int configMax = plugin.getConfig().getInt("soul-points.max", 10);
            org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();
            
            plugin.getYskLib().logDebug(plugin, "Player " + killer.getName() + " killed " + victim.getName() + " - max soul points reduced by " + reduction);
            
            messageManager.sendMessageList(plugin, killer, "max-soul-points-reduced", placeholders(
                "count", String.valueOf(reduction),
                "plural", reduction > 1 ? "s" : "",
                "current_max", String.valueOf(newMax),
                "config_max", String.valueOf(configMax),
                "victim", victim.getName()
            ));
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!plugin.isSoulPointsEnabled()) {
            plugin.getYskLib().logDebug(plugin, "Soul points system disabled - skipping death handling for " + player.getName());
            return;
        }

        plugin.getYskLib().logDebug(plugin, "Processing death event for player: " + player.getName());

        // Check if player has bypass permission - if so, behave like keepInventory
        if (player.hasPermission("lmdp.bypass")) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            plugin.getYskLib().logDebug(plugin, "Player " + player.getName() + " has bypass permission - keeping inventory");
            return;
        }

        // Check if keepInventory is enabled - if so, skip all plugin logic
        if (Boolean.parseBoolean(player.getWorld().getGameRuleValue("keepInventory"))) {
            plugin.getYskLib().logDebug(plugin, "keepInventory is enabled in world " + player.getWorld().getName() + " - skipping");
            return;
        }
        
        // Mark that we processed this death
        player.setMetadata(METADATA_PROCESSED, new FixedMetadataValue(plugin, true));

        // Get soul points before reduction
        int oldSoulPoints = soulPointsManager.getSoulPoints(player.getUniqueId());

        // Reduce soul points by 1
        soulPointsManager.removeSoulPoint(player.getUniqueId());

        // Get current soul points after reduction
        int currentSoulPoints = soulPointsManager.getSoulPoints(player.getUniqueId());

        plugin.getYskLib().logDebug(plugin, "Soul points for " + player.getName() + ": " + oldSoulPoints + " -> " + currentSoulPoints);

        // Get drop rates for old soul points (before death penalty)
        SoulPointsManager.DropRates dropRates = soulPointsManager.getDropRates(oldSoulPoints);

        plugin.getYskLib().logDebug(plugin, "Drop rates - Items: " + dropRates.itemDrop + "%, Hotbar: " + dropRates.hotbarDrop + ", Armor: " + dropRates.armorDrop);

        // Handle item drops and get info about what dropped
        ItemDropResult dropResult = handleItemDrops(event, dropRates);

        plugin.getYskLib().logDebug(plugin, "Dropped " + dropResult.itemsDropped + "/" + dropResult.totalItems + " items for " + player.getName());

        SoulPointsManager.MaxHealthPenaltyResult maxHealthResult = soulPointsManager.applyMaxHealthPenalty(player, dropRates);
        MoneyPenaltyResult moneyResult = applyMoneyPenalty(player, dropRates);

        plugin.getYskLib().logDebug(plugin, "Max health change applied: " + formatHearts(maxHealthResult.deltaHearts) + " for " + player.getName());
        if (moneyResult.amountLost > 0.0D) {
            plugin.getYskLib().logDebug(plugin, "Money penalty applied: " + moneyResult.amountLost + " for " + player.getName());
        }
        
        // Experience always drops (default Minecraft behavior)
        // Send death notification with delay using Folia-compatible scheduler
        foliaLib.getImpl().runAtEntityLater(player, task -> {
            sendDeathNotification(player, oldSoulPoints, currentSoulPoints, dropResult, moneyResult, maxHealthResult);
        }, 40L); // 2 second delay after death
    }
    
    private ItemDropResult handleItemDrops(PlayerDeathEvent event, SoulPointsManager.DropRates dropRates) {
        Player player = event.getEntity();
        
        // Always clear default drops and keep inventory - we'll handle drops manually
        event.getDrops().clear();
        event.setKeepInventory(true);
        
        if (dropRates.itemDrop == 0) {
            // No items should drop, keep everything
            return new ItemDropResult(0, 0, new HashMap<>());
        }
        
        // Store original inventory state for restoration
        ItemStack[] originalInventory = player.getInventory().getContents().clone();
        ItemStack[] originalArmor = player.getInventory().getArmorContents().clone();
        ItemStack originalOffhand = player.getInventory().getItemInOffHand().clone();
        
        // Collect all individual items (accounting for quantities)
        List<ItemEntry> vulnerableItems = new ArrayList<>();
        List<ItemEntry> itemsToKeep = new ArrayList<>();
        
        // Collect body items (always vulnerable)
        for (int slot = 9; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && !item.getType().isAir()) {
                addItemsFromStack(vulnerableItems, item, slot, "inventory");
            }
        }
        
        // Handle hotbar items
        if (dropRates.hotbarDrop) {
            // Hotbar is vulnerable
            for (int slot = 0; slot < 9; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    addItemsFromStack(vulnerableItems, item, slot, "inventory");
                }
            }
        } else {
            // Keep hotbar items
            for (int slot = 0; slot < 9; slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    addItemsFromStack(itemsToKeep, item, slot, "inventory");
                }
            }
        }
        
        // Handle armor
        if (dropRates.armorDrop) {
            // Armor is vulnerable
            ItemStack[] armor = player.getInventory().getArmorContents();
            String[] armorSlots = {"boots", "leggings", "chestplate", "helmet"};
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null && !armor[i].getType().isAir()) {
                    addItemsFromStack(vulnerableItems, armor[i], i, armorSlots[i]);
                }
            }
        } else {
            // Keep armor items
            ItemStack[] armor = player.getInventory().getArmorContents();
            String[] armorSlots = {"boots", "leggings", "chestplate", "helmet"};
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null && !armor[i].getType().isAir()) {
                    addItemsFromStack(itemsToKeep, armor[i], i, armorSlots[i]);
                }
            }
        }
        
        // Handle offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            if (dropRates.hotbarDrop) {
                addItemsFromStack(vulnerableItems, offhand, 0, "offhand");
            } else {
                addItemsFromStack(itemsToKeep, offhand, 0, "offhand");
            }
        }
        
        // Track what gets dropped for notification
        Map<org.bukkit.Material, Integer> droppedItems = new HashMap<>();
        int totalVulnerableCount = vulnerableItems.size();
        int itemsToDropCount = 0;
        List<ItemEntry> itemsToDrop = new ArrayList<>();
        
        // Calculate what to drop from vulnerable items (by individual item count)
        if (dropRates.itemDrop >= 100) {
            // Drop all vulnerable items
            itemsToDropCount = vulnerableItems.size();
            itemsToDrop.addAll(vulnerableItems);
        } else if (dropRates.itemDrop > 0) {
            // Calculate number of individual items to drop
            itemsToDropCount = (int) Math.ceil(totalVulnerableCount * (dropRates.itemDrop / 100.0));
            
            // Randomly select items to drop
            Collections.shuffle(vulnerableItems, random);
            
            for (int i = 0; i < vulnerableItems.size(); i++) {
                if (i < itemsToDropCount) {
                    itemsToDrop.add(vulnerableItems.get(i));
                } else {
                    itemsToKeep.add(vulnerableItems.get(i));
                }
            }
        } else {
            // Keep all vulnerable items
            itemsToKeep.addAll(vulnerableItems);
        }
        
        // Clear player inventory and manually drop items at death location
        // This must be done immediately while still in the death event
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        
        // Check if AxGraves is installed
        boolean axGravesInstalled = Bukkit.getPluginManager().isPluginEnabled("AxGraves");
        
        if (axGravesInstalled) {
            // Store dropped items in metadata for AxGraves to collect
            List<ItemStack> itemsToDropList = new ArrayList<>();
            for (ItemEntry entry : itemsToDrop) {
                itemsToDropList.add(entry.item.clone());
                droppedItems.merge(entry.item.getType(), 1, Integer::sum);
            }
            player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, itemsToDropList));
            
            // Store kept items in metadata for restoration if AxGraves doesn't handle them
            player.setMetadata(METADATA_KEPT_ITEMS, new FixedMetadataValue(plugin, new KeptItemsData(itemsToKeep, originalInventory, originalArmor, originalOffhand)));
            
            plugin.getYskLib().logDebug(plugin, "AxGraves detected - stored " + itemsToDropList.size() + " items in metadata for grave collection");
            
            // Fallback: If AxGraves doesn't process the items within 2 seconds, drop them manually
            // This handles cases where AxGraves skips grave creation (no permission, disabled world, etc.)
            Location deathLocation = player.getLocation().clone();
            foliaLib.getImpl().runAtEntityLater(player, task -> {
                if (player.hasMetadata(METADATA_KEY)) {
                    // AxGraves never collected the items, drop them manually
                    Object metadataValue = player.getMetadata(METADATA_KEY).get(0).value();
                    if (metadataValue instanceof List<?>) {
                        for (Object item : (List<?>) metadataValue) {
                            if (item instanceof ItemStack itemStack) {
                                player.getWorld().dropItemNaturally(deathLocation, itemStack);
                            }
                        }
                    }
                    
                    // Restore kept items
                    if (player.hasMetadata(METADATA_KEPT_ITEMS)) {
                        Object keptMetadata = player.getMetadata(METADATA_KEPT_ITEMS).get(0).value();
                        if (keptMetadata instanceof KeptItemsData keptData) {
                            restoreKeptItemsToOriginalSlots(player, keptData.itemsToKeep, keptData.originalInventory, keptData.originalArmor, keptData.originalOffhand);
                        }
                    }
                    
                    // Clean up metadata
                    player.removeMetadata(METADATA_KEY, plugin);
                    player.removeMetadata(METADATA_KEPT_ITEMS, plugin);
                    player.removeMetadata(METADATA_PROCESSED, plugin);
                    
                    plugin.getYskLib().logDebug(plugin, "AxGraves didn't create grave - falling back to manual drop for " + player.getName());
                }
            }, 40L); // 2 seconds delay
        } else {
            // Manually drop items at death location (keepInventory=true prevents event.getDrops() from working)
            for (ItemEntry entry : itemsToDrop) {
                player.getWorld().dropItemNaturally(player.getLocation(), entry.item);
                droppedItems.merge(entry.item.getType(), 1, Integer::sum);
            }
            
            // Schedule inventory restoration with original positions
            foliaLib.getImpl().runNextTick(task -> {
                restoreKeptItemsToOriginalSlots(player, itemsToKeep, originalInventory, originalArmor, originalOffhand);
            });
        }
        
        return new ItemDropResult(totalVulnerableCount, itemsToDropCount, droppedItems);
    }
    
    private void addItemsFromStack(List<ItemEntry> list, ItemStack stack, int slot, String type) {
        // Add each individual item in the stack
        for (int i = 0; i < stack.getAmount(); i++) {
            ItemStack singleItem = stack.clone();
            singleItem.setAmount(1);
            list.add(new ItemEntry(singleItem, slot, type));
        }
    }
    
    private Map<org.bukkit.Material, Integer> addItemEntriesToDrops(PlayerDeathEvent event, List<ItemEntry> entries) {
        // Group items by type and add to drops
        Map<org.bukkit.Material, Integer> itemCounts = new HashMap<>();
        for (ItemEntry entry : entries) {
            itemCounts.merge(entry.item.getType(), 1, Integer::sum);
        }
        
        for (Map.Entry<org.bukkit.Material, Integer> dropEntry : itemCounts.entrySet()) {
            ItemStack dropStack = new ItemStack(dropEntry.getKey(), dropEntry.getValue());
            event.getDrops().add(dropStack);
        }
        
        return itemCounts;
    }
    
    private void restoreKeptItems(Player player, List<ItemStack> itemsToKeep) {
        // Clear inventory first
        player.getInventory().clear();
        
        // Restore kept items
        for (ItemStack item : itemsToKeep) {
            // Try to place in original slots if possible, otherwise add to inventory
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(item);
            } else {
                // Inventory full, drop at player location
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }
    
    
    private void restoreKeptItemsToOriginalSlots(Player player, List<ItemEntry> itemsToKeep, 
                                                ItemStack[] originalInventory, ItemStack[] originalArmor, ItemStack originalOffhand) {
        // Inventory is already cleared in handleItemDrops, no need to clear again
        
        // Group items by slot and type to reconstruct original stacks
        Map<String, Map<Integer, List<ItemEntry>>> groupedItems = new HashMap<>();
        
        for (ItemEntry entry : itemsToKeep) {
            groupedItems.computeIfAbsent(entry.type, k -> new HashMap<>())
                       .computeIfAbsent(entry.slot, k -> new ArrayList<>())
                       .add(entry);
        }
        
        // Restore inventory items
        if (groupedItems.containsKey("inventory")) {
            for (Map.Entry<Integer, List<ItemEntry>> slotEntry : groupedItems.get("inventory").entrySet()) {
                int slot = slotEntry.getKey();
                List<ItemEntry> items = slotEntry.getValue();
                
                if (!items.isEmpty()) {
                    ItemStack stackToPlace = items.get(0).item.clone();
                    stackToPlace.setAmount(items.size());
                    player.getInventory().setItem(slot, stackToPlace);
                }
            }
        }
        
        // Restore armor items
        if (groupedItems.containsKey("boots") || groupedItems.containsKey("leggings") || 
            groupedItems.containsKey("chestplate") || groupedItems.containsKey("helmet")) {
            
            ItemStack[] newArmor = new ItemStack[4];
            String[] armorTypes = {"boots", "leggings", "chestplate", "helmet"};
            
            for (int i = 0; i < armorTypes.length; i++) {
                if (groupedItems.containsKey(armorTypes[i]) && groupedItems.get(armorTypes[i]).containsKey(i)) {
                    List<ItemEntry> armorItems = groupedItems.get(armorTypes[i]).get(i);
                    if (!armorItems.isEmpty()) {
                        ItemStack armorPiece = armorItems.get(0).item.clone();
                        armorPiece.setAmount(armorItems.size());
                        newArmor[i] = armorPiece;
                    }
                }
            }
            
            player.getInventory().setArmorContents(newArmor);
        }
        
        // Restore offhand items
        if (groupedItems.containsKey("offhand") && groupedItems.get("offhand").containsKey(0)) {
            List<ItemEntry> offhandItems = groupedItems.get("offhand").get(0);
            if (!offhandItems.isEmpty()) {
                ItemStack offhandStack = offhandItems.get(0).item.clone();
                offhandStack.setAmount(offhandItems.size());
                player.getInventory().setItemInOffHand(offhandStack);
            }
        }
    }
    
    private void sendDeathNotification(Player player, int oldSoulPoints, int currentSoulPoints,
                                      ItemDropResult dropResult, MoneyPenaltyResult moneyResult,
                                      SoulPointsManager.MaxHealthPenaltyResult maxHealthResult) {
        int maxSoulPoints = plugin.getConfig().getInt("soul-points.max", 10);
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        // Prepare placeholders
        Map<String, String> placeholdersMap = placeholders(
            "old_points", String.valueOf(oldSoulPoints),
            "current_points", String.valueOf(currentSoulPoints),
            "max_points", String.valueOf(maxSoulPoints),
            "items_dropped", String.valueOf(dropResult.itemsDropped),
            "total_items", String.valueOf(dropResult.totalItems)
        );

        double moneyLost = moneyResult != null ? moneyResult.amountLost : 0.0D;
        placeholdersMap.put("money_lost", formatCurrency(moneyLost));
        placeholdersMap.put("drop_percentage", dropResult.totalItems > 0
            ? String.format("%.1f", (double) dropResult.itemsDropped / dropResult.totalItems * 100)
            : "0");
        placeholdersMap.put("max_health_lost", formatHearts(maxHealthResult != null ? maxHealthResult.deltaHearts : 0.0D));
        
        // Show item loss summary
        if (dropResult.totalItems > 0) {
            // Show specific items dropped (top 3 most common)
            if (!dropResult.droppedItems.isEmpty()) {
                List<Map.Entry<org.bukkit.Material, Integer>> sortedDrops = new ArrayList<>(dropResult.droppedItems.entrySet());
                sortedDrops.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                StringBuilder itemList = new StringBuilder();
                int shown = 0;
                for (Map.Entry<org.bukkit.Material, Integer> entry : sortedDrops) {
                    if (shown >= 3) break;
                    if (shown > 0) itemList.append("&7, ");
                    itemList.append("&c").append(entry.getValue()).append("x ").append(formatMaterialName(entry.getKey()));
                    shown++;
                }
                if (sortedDrops.size() > 3) {
                    itemList.append("&7, and ").append(sortedDrops.size() - 3).append(" more types");
                }
                placeholdersMap.put("item_list", itemList.toString());
            } else {
                placeholdersMap.put("item_list", "");
            }

            // Show recovery info
            if (currentSoulPoints < maxSoulPoints) {
                String recoveryMode = plugin.getRecoveryMode();
                long recoverySeconds = plugin.getRecoveryIntervalSeconds();
                placeholdersMap.put("interval", formatInterval(recoverySeconds));
                placeholdersMap.put("mode", recoveryMode);
            }

            messageManager.sendMessageList(plugin, player, "death-penalty", placeholdersMap);
        } else {
            // Show recovery info for no-items case
            if (currentSoulPoints < maxSoulPoints) {
                String recoveryMode = plugin.getRecoveryMode();
                long recoverySeconds = plugin.getRecoveryIntervalSeconds();
                placeholdersMap.put("interval", formatInterval(recoverySeconds));
                placeholdersMap.put("mode", recoveryMode);
            }

            messageManager.sendMessageList(plugin, player, "death-penalty-no-items", placeholdersMap);
        }
    }

    private MoneyPenaltyResult applyMoneyPenalty(Player player, SoulPointsManager.DropRates dropRates) {
        if (player == null || dropRates == null || dropRates.moneyPenalty <= 0.0D) {
            return MoneyPenaltyResult.empty();
        }

        Economy economy = plugin.getEconomy();
        if (economy == null) {
            if (dropRates.moneyPenalty > 0.0D) {
                plugin.getLogger().warning("Vault economy not found; skipping money penalty for " + player.getName());
            }
            return MoneyPenaltyResult.empty();
        }

        double initialBalance = economy.getBalance(player);
        double amountToWithdraw = 0.0D;

        if (dropRates.moneyPenalty > 0.0D) {
            if (dropRates.moneyMode == SoulPointsManager.DropRates.MoneyPenaltyMode.PERCENT) {
                amountToWithdraw = initialBalance * (dropRates.moneyPenalty / 100.0D);
            } else {
                amountToWithdraw = dropRates.moneyPenalty;
            }
        }

        amountToWithdraw = Math.max(0.0D, Math.min(amountToWithdraw, initialBalance));

        double withdrawn = 0.0D;
        double remaining = initialBalance;

        if (amountToWithdraw > 0.0D) {
            EconomyResponse response = economy.withdrawPlayer(player, amountToWithdraw);
            if (response.transactionSuccess()) {
                withdrawn = response.amount;
                remaining = Math.max(0.0D, response.balance);
            } else {
                plugin.getLogger().warning("Failed to withdraw money penalty for " + player.getName() + ": " + response.errorMessage);
                remaining = economy.getBalance(player);
            }
        }

        boolean depleted = remaining <= BALANCE_EPSILON || (amountToWithdraw <= 0.0D && initialBalance <= BALANCE_EPSILON);

        return new MoneyPenaltyResult(withdrawn, remaining, depleted);
    }

    private String formatCurrency(double amount) {
        if (amount <= 0.0D) {
            return "0";
        }
        Economy economy = plugin.getEconomy();
        if (economy != null) {
            return economy.format(amount);
        }
        return String.format("%.2f", amount);
    }

    private String formatHearts(double hearts) {
        double absolute = Math.abs(hearts);
        if (absolute <= 0.0001D) {
            return "0 hearts";
        }
        String sign = hearts > 0.0D ? "-" : "+";
        return String.format("%s%.1f hearts", sign, absolute);
    }
    
    private String formatMaterialName(org.bukkit.Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return result.toString();
    }

    private String formatInterval(long seconds) {
        long remainingSeconds = seconds;
        long hours = remainingSeconds / 3600;
        remainingSeconds %= 3600;
        long minutes = remainingSeconds / 60;
        remainingSeconds %= 60;

        StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours).append("h");
        }
        if (minutes > 0) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(minutes).append("m");
        }
        if (remainingSeconds > 0 || builder.length() == 0) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(remainingSeconds).append("s");
        }
        return builder.toString();
    }
    
    // Helper class to track item positions
    private static class ItemEntry {
        final ItemStack item;
        final int slot;
        final String type; // "inventory", "boots", "leggings", "chestplate", "helmet", "offhand"
        
        ItemEntry(ItemStack item, int slot, String type) {
            this.item = item;
            this.slot = slot;
            this.type = type;
        }
    }
    
    // Helper class to store kept items data for later restoration
    private static class KeptItemsData {
        final List<ItemEntry> itemsToKeep;
        final ItemStack[] originalInventory;
        final ItemStack[] originalArmor;
        final ItemStack originalOffhand;
        
        KeptItemsData(List<ItemEntry> itemsToKeep, ItemStack[] originalInventory, ItemStack[] originalArmor, ItemStack originalOffhand) {
            this.itemsToKeep = itemsToKeep;
            this.originalInventory = originalInventory;
            this.originalArmor = originalArmor;
            this.originalOffhand = originalOffhand;
        }
    }
    
    // Helper class to track drop results
    private static class ItemDropResult {
        final int totalItems;
        final int itemsDropped;
        final Map<org.bukkit.Material, Integer> droppedItems;
        
        ItemDropResult(int totalItems, int itemsDropped, Map<org.bukkit.Material, Integer> droppedItems) {
            this.totalItems = totalItems;
            this.itemsDropped = itemsDropped;
            this.droppedItems = droppedItems;
        }
    }

    private static class MoneyPenaltyResult {
        final double amountLost;
        final double remainingBalance;
        final boolean depleted;

        MoneyPenaltyResult(double amountLost, double remainingBalance, boolean depleted) {
            this.amountLost = amountLost;
            this.remainingBalance = remainingBalance;
            this.depleted = depleted;
        }

        static MoneyPenaltyResult empty() {
            return new MoneyPenaltyResult(0.0D, 0.0D, false);
        }
    }
}

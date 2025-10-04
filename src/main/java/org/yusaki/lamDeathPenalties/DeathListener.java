package org.yusaki.lamDeathPenalties;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static org.yusaki.lib.modules.MessageManager.placeholders;

public class DeathListener implements Listener {
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    private final FoliaLib foliaLib;
    private final Random random;
    
    
    public DeathListener(LamDeathPenalties plugin, SoulPointsManager soulPointsManager, FoliaLib foliaLib) {
        this.plugin = plugin;
        this.soulPointsManager = soulPointsManager;
        this.foliaLib = foliaLib;
        this.random = new Random();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

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

        // Get soul points before reduction
        int oldSoulPoints = soulPointsManager.getSoulPoints(player.getUniqueId());

        // Reduce soul points by 1
        soulPointsManager.removeSoulPoint(player.getUniqueId());

        // Get current soul points after reduction
        int currentSoulPoints = soulPointsManager.getSoulPoints(player.getUniqueId());

        plugin.getYskLib().logDebug(plugin, "Soul points for " + player.getName() + ": " + oldSoulPoints + " -> " + currentSoulPoints);

        // Get drop rates for current soul points
        SoulPointsManager.DropRates dropRates = soulPointsManager.getDropRates(currentSoulPoints);

        plugin.getYskLib().logDebug(plugin, "Drop rates - Items: " + dropRates.itemDrop + "%, Hotbar: " + dropRates.hotbarDrop + ", Armor: " + dropRates.armorDrop);

        // Handle item drops and get info about what dropped
        ItemDropResult dropResult = handleItemDrops(event, dropRates);

        plugin.getYskLib().logDebug(plugin, "Dropped " + dropResult.itemsDropped + "/" + dropResult.totalItems + " items for " + player.getName());
        
        // Experience always drops (default Minecraft behavior)
        // Send death notification with delay using Folia-compatible scheduler
        foliaLib.getImpl().runAtEntityLater(player, task -> {
            sendDeathNotification(player, oldSoulPoints, currentSoulPoints, dropResult);
        }, 40L); // 2 second delay after death
    }
    
    private ItemDropResult handleItemDrops(PlayerDeathEvent event, SoulPointsManager.DropRates dropRates) {
        Player player = event.getEntity();
        
        // Always clear default drops to prevent duplication
        event.getDrops().clear();
        
        if (dropRates.itemDrop == 0) {
            // No items should drop, keep everything
            event.setKeepInventory(true);
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
        
        // Calculate what to drop from vulnerable items (by individual item count)
        if (dropRates.itemDrop >= 100) {
            // Drop all vulnerable items
            itemsToDropCount = vulnerableItems.size();
            droppedItems = addItemEntriesToDrops(event, vulnerableItems);
        } else if (dropRates.itemDrop > 0) {
            // Calculate number of individual items to drop
            itemsToDropCount = (int) Math.ceil(totalVulnerableCount * (dropRates.itemDrop / 100.0));
            
            // Randomly select items to drop
            Collections.shuffle(vulnerableItems, random);
            
            for (int i = 0; i < vulnerableItems.size(); i++) {
                if (i < itemsToDropCount) {
                    ItemStack dropItem = new ItemStack(vulnerableItems.get(i).item.getType(), 1);
                    event.getDrops().add(dropItem);
                    // Track dropped items
                    droppedItems.merge(dropItem.getType(), 1, Integer::sum);
                } else {
                    itemsToKeep.add(vulnerableItems.get(i));
                }
            }
        } else {
            // Keep all vulnerable items
            itemsToKeep.addAll(vulnerableItems);
        }
        
        // Schedule inventory restoration with original positions
        foliaLib.getImpl().runNextTick(task -> {
            restoreKeptItemsToOriginalSlots(player, itemsToKeep, originalInventory, originalArmor, originalOffhand);
        });
        
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
        // Clear inventory first
        player.getInventory().clear();
        
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
                                      ItemDropResult dropResult) {
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
        
        // Show item loss summary
        if (dropResult.totalItems > 0) {
            double dropPercentage = (double) dropResult.itemsDropped / dropResult.totalItems * 100;
            placeholdersMap.put("drop_percentage", String.format("%.1f", dropPercentage));

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
                String recoveryMode = plugin.getConfig().getString("recovery.mode", "real-time");
                int recoveryHours = plugin.getConfig().getInt("recovery.interval-hours", 1);
                placeholdersMap.put("hours", String.valueOf(recoveryHours));
                placeholdersMap.put("mode", recoveryMode);
            }

            messageManager.sendMessageList(plugin, player, "death-penalty", placeholdersMap);
        } else {
            // Show recovery info for no-items case
            if (currentSoulPoints < maxSoulPoints) {
                String recoveryMode = plugin.getConfig().getString("recovery.mode", "real-time");
                int recoveryHours = plugin.getConfig().getInt("recovery.interval-hours", 1);
                placeholdersMap.put("hours", String.valueOf(recoveryHours));
                placeholdersMap.put("mode", recoveryMode);
            }

            messageManager.sendMessageList(plugin, player, "death-penalty-no-items", placeholdersMap);
        }
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
}
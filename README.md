# LamDeathPenalties API Documentation

This document describes the public API for the LamDeathPenalties plugin, allowing other plugins to integrate with the soul points system.

## Getting Started

### Adding the API to Your Plugin

1. Add LamDeathPenalties as a dependency in your `plugin.yml`:
```yaml
depend: [LamDeathPenalties]
# or for optional dependency:
softdepend: [LamDeathPenalties]
```

2. Get the API instance in your plugin:
```java
import org.yusaki.lamDeathPenalties.api.LamDeathPenaltiesAPI;
import org.bukkit.plugin.RegisteredServiceProvider;

public class YourPlugin extends JavaPlugin {
    private LamDeathPenaltiesAPI soulPointsAPI;
    
    @Override
    public void onEnable() {
        // Get the API service
        RegisteredServiceProvider<LamDeathPenaltiesAPI> provider = 
            getServer().getServicesManager().getRegistration(LamDeathPenaltiesAPI.class);
        
        if (provider != null) {
            soulPointsAPI = provider.getProvider();
            getLogger().info("Successfully hooked into LamDeathPenalties API!");
        } else {
            getLogger().warning("LamDeathPenalties not found!");
        }
    }
}
```

## API Methods

### Reading Soul Points

```java
// Get a player's current soul points
int points = soulPointsAPI.getSoulPoints(player);
int points = soulPointsAPI.getSoulPoints(playerUUID);

// Check if player exists in the system
boolean exists = soulPointsAPI.hasPlayerData(player);

// Get configuration values
int maxPoints = soulPointsAPI.getMaxSoulPoints(); // Usually 10
int startingPoints = soulPointsAPI.getStartingSoulPoints(); // Usually 10
```

### Modifying Soul Points

```java
// Set soul points directly (clamped to 0-max)
boolean success = soulPointsAPI.setSoulPoints(player, 5);

// Add soul points (can be negative to subtract)
boolean success = soulPointsAPI.addSoulPoints(player, 2);

// Remove soul points
boolean success = soulPointsAPI.removeSoulPoints(player, 1);
```

### Drop Rate Information

```java
// Get drop rates for a specific soul point level
DropRates rates = soulPointsAPI.getDropRates(3);
System.out.println("Item drop: " + rates.itemDrop + "%");
System.out.println("Hotbar vulnerable: " + rates.hotbarDrop);
System.out.println("Armor vulnerable: " + rates.armorDrop);

// Get drop rates for a player's current level
DropRates playerRates = soulPointsAPI.getPlayerDropRates(player);
```

### Recovery Information

```java
// Get time until next recovery in milliseconds
long timeMs = soulPointsAPI.getTimeUntilNextRecovery(player);
if (timeMs > 0) {
    long minutes = timeMs / (1000 * 60);
    player.sendMessage("Recovery in " + minutes + " minutes");
} else {
    player.sendMessage("Ready for recovery!");
}

// Manually trigger recovery processing
boolean recoveryOccurred = soulPointsAPI.processRecovery(player);
```

## Events

The API provides two events for listening to soul point changes:

### SoulPointsChangeEvent (Cancellable)

Called **before** soul points change. Can be cancelled or modified.

```java
@EventHandler
public void onSoulPointsChange(SoulPointsChangeEvent event) {
    Player player = event.getPlayer();
    int oldPoints = event.getOldSoulPoints();
    int newPoints = event.getNewSoulPoints();
    ChangeReason reason = event.getReason();
    
    // Cancel death penalties for VIP players
    if (reason == ChangeReason.DEATH && player.hasPermission("vip.nodeathpenalty")) {
        event.setCancelled(true);
        player.sendMessage("&aVIP protection activated!");
        return;
    }
    
    // Double recovery for premium players
    if (reason == ChangeReason.RECOVERY && player.hasPermission("premium.fastrecovery")) {
        int recoveryAmount = newPoints - oldPoints;
        event.setNewSoulPoints(Math.min(newPoints + recoveryAmount, 10));
    }
}
```

### SoulPointsChangedEvent (Not Cancellable)

Called **after** soul points have changed.

```java
@EventHandler
public void onSoulPointsChanged(SoulPointsChangedEvent event) {
    Player player = event.getPlayer();
    int change = event.getChangeAmount(); // Positive = gained, negative = lost
    
    // Notify about significant changes
    if (Math.abs(change) >= 3) {
        Bukkit.broadcastMessage(player.getName() + " lost/gained " + Math.abs(change) + " soul points!");
    }
    
    // Warning for low soul points
    if (event.getNewSoulPoints() <= 2) {
        player.sendMessage("&c⚠ Warning: Critical soul point level!");
    }
}
```

### Change Reasons

```java
public enum ChangeReason {
    DEATH,           // Player died
    RECOVERY,        // Automatic recovery
    COMMAND,         // Admin command
    API,             // Direct API call (setSoulPoints)
    PLUGIN_SET,      // Plugin used API
    PLUGIN_ADD,      // Plugin added points
    PLUGIN_REMOVE    // Plugin removed points
}
```

## Practical Examples

### Example 1: Reward System Integration

```java
public void giveQuestReward(Player player) {
    int soulPoints = soulPointsAPI.getSoulPoints(player);
    
    if (soulPoints >= 8) {
        // Premium rewards for high soul points
        giveItem(player, "DIAMOND", 5);
        player.sendMessage("&bBonus reward for high soul points!");
    } else if (soulPoints >= 5) {
        // Standard rewards
        giveItem(player, "IRON_INGOT", 10);
    } else {
        // Reduced rewards for low soul points
        giveItem(player, "COBBLESTONE", 32);
        player.sendMessage("&7Reward reduced due to low soul points...");
    }
}
```

### Example 2: Protection System

```java
@EventHandler
public void onSoulPointsChange(SoulPointsChangeEvent event) {
    Player player = event.getPlayer();
    
    // Protect players in safe zones
    if (isInSafeZone(player) && event.getReason() == ChangeReason.DEATH) {
        event.setCancelled(true);
        player.sendMessage("&aSafe zone protection prevented soul point loss!");
    }
    
    // Grace period for new players
    if (getPlaytime(player) < 3600000 && event.getReason() == ChangeReason.DEATH) { // 1 hour
        event.setCancelled(true);
        player.sendMessage("&eNewbie protection active!");
    }
}
```

### Example 3: Display Integration

```java
public void showPlayerStats(Player viewer, Player target) {
    int points = soulPointsAPI.getSoulPoints(target);
    int maxPoints = soulPointsAPI.getMaxSoulPoints();
    DropRates rates = soulPointsAPI.getPlayerDropRates(target);
    
    viewer.sendMessage("&b" + target.getName() + "'s Soul Points: " + points + "/" + maxPoints);
    viewer.sendMessage("&7Drop Rate: " + rates.itemDrop + "% items");
    viewer.sendMessage("&7Hotbar Protected: " + (!rates.hotbarDrop ? "&aYes" : "&cNo"));
    viewer.sendMessage("&7Armor Protected: " + (!rates.armorDrop ? "&aYes" : "&cNo"));
    
    long recovery = soulPointsAPI.getTimeUntilNextRecovery(target);
    if (recovery > 0) {
        long minutes = recovery / (1000 * 60);
        viewer.sendMessage("&7Next Recovery: " + minutes + " minutes");
    }
}
```

### Example 4: Soul Point Marketplace

```java
public void buySoulPoint(Player player) {
    // Check if player can afford it
    if (!economy.has(player, 1000)) {
        player.sendMessage("&cYou need $1000 to buy a soul point!");
        return;
    }
    
    // Check if player is at max
    int current = soulPointsAPI.getSoulPoints(player);
    int max = soulPointsAPI.getMaxSoulPoints();
    if (current >= max) {
        player.sendMessage("&cYou already have maximum soul points!");
        return;
    }
    
    // Purchase the soul point
    economy.withdrawPlayer(player, 1000);
    boolean success = soulPointsAPI.addSoulPoints(player, 1);
    
    if (success) {
        player.sendMessage("&aPurchased 1 soul point for $1000!");
    } else {
        economy.depositPlayer(player, 1000); // Refund
        player.sendMessage("&cFailed to purchase soul point!");
    }
}
```

## Best Practices

1. **Always check if API is available** before using it
2. **Handle event cancellation** - other plugins might cancel your changes
3. **Use appropriate change reasons** when modifying soul points via API
4. **Respect the max/min limits** - the API will clamp values but you should check first
5. **Listen to both events** - use `SoulPointsChangeEvent` to prevent/modify, `SoulPointsChangedEvent` to react
6. **Be careful with recursive changes** - don't modify soul points inside change events unless necessary

## API Versioning

This API follows semantic versioning. Major version changes may include breaking changes, while minor versions add features and patches fix bugs.

Current API Version: 1.0.0

## Support

For API support or feature requests, please create an issue on the plugin's GitHub repository.
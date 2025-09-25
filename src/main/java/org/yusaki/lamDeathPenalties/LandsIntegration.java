package org.yusaki.lamDeathPenalties;

import me.angeschossen.lands.api.events.land.spawn.LandSpawnTeleportEvent;
import me.angeschossen.lands.api.land.Land;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.yusaki.lamDeathPenalties.api.events.SoulPointsChangeEvent;

/**
 * Integration with Lands plugin to deduct soul points for land spawn teleportation
 */
public class LandsIntegration implements Listener {
    
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    private final MessageManager messageManager;
    
    public LandsIntegration(LamDeathPenalties plugin) {
        this.plugin = plugin;
        this.soulPointsManager = plugin.getSoulPointsManager();
        this.messageManager = plugin.getMessageManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onLandSpawnTeleport(LandSpawnTeleportEvent event) {
        // Check if this feature is enabled in config
        if (!plugin.getConfig().getBoolean("lands-integration.enabled", false)) {
            return;
        }
        
        LandPlayer landPlayer = event.getLandPlayer();
        Player player = Bukkit.getPlayer(landPlayer.getUID());
        
        if (player == null) {
            return;
        }
        
        Land land = event.getLand();
        
        // Get configuration for cost
        int soulPointCost = plugin.getConfig().getInt("lands-integration.spawn-cost", 1);
        boolean onlyOtherLands = plugin.getConfig().getBoolean("lands-integration.only-other-lands", true);
        boolean exemptTrusted = plugin.getConfig().getBoolean("lands-integration.exempt-trusted", true);
        
        // Skip if cost is 0
        if (soulPointCost <= 0) {
            return;
        }
        
        // Check if we only charge for other people's lands
        if (onlyOtherLands && land.getOwnerUID().equals(landPlayer.getUID())) {
            return;
        }
        
        // Check if trusted players are exempt
        if (exemptTrusted && land.isTrusted(landPlayer.getUID())) {
            return;
        }
        
        // Check player's current soul points
        int currentPoints = soulPointsManager.getSoulPoints(player.getUniqueId());
        
        // Check if player has enough soul points
        if (currentPoints < soulPointCost) {
            // Cancel teleportation if insufficient soul points
            event.setCancelled(true);
            messageManager.sendMessage(player, "lands-insufficient-soul-points", 
                MessageManager.placeholders(
                    "cost", String.valueOf(soulPointCost),
                    "current", String.valueOf(currentPoints),
                    "land_name", land.getName()
                )
            );
            return;
        }
        
        // Deduct soul points
        soulPointsManager.setSoulPointsWithReason(
            player.getUniqueId(), 
            currentPoints - soulPointCost, 
            SoulPointsChangeEvent.ChangeReason.API
        );
        
        // Notify player
        messageManager.sendMessage(player, "lands-teleport-cost", 
            MessageManager.placeholders(
                "cost", String.valueOf(soulPointCost),
                "remaining", String.valueOf(currentPoints - soulPointCost),
                "land_name", land.getName()
            )
        );
        
        plugin.getLogger().info(String.format("Player %s paid %d soul points to teleport to land '%s'", 
            player.getName(), soulPointCost, land.getName()));
    }
}
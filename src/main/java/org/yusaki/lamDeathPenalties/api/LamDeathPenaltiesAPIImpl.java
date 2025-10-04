package org.yusaki.lamDeathPenalties.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.yusaki.lamDeathPenalties.LamDeathPenalties;
import org.yusaki.lamDeathPenalties.SoulPointsManager;
import org.yusaki.lamDeathPenalties.SoulPointsManager.DropRates;
import org.yusaki.lamDeathPenalties.api.events.SoulPointsChangeEvent;
import org.yusaki.lamDeathPenalties.api.events.SoulPointsChangedEvent;

import java.util.UUID;

/**
 * Implementation of the LamDeathPenalties public API
 */
public class LamDeathPenaltiesAPIImpl implements LamDeathPenaltiesAPI {
    
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    
    public LamDeathPenaltiesAPIImpl(LamDeathPenalties plugin) {
        this.plugin = plugin;
        this.soulPointsManager = plugin.getSoulPointsManager();
    }
    
    @Override
    public int getSoulPoints(UUID playerId) {
        return soulPointsManager.getSoulPoints(playerId);
    }
    
    @Override
    public int getSoulPoints(Player player) {
        return getSoulPoints(player.getUniqueId());
    }
    
    @Override
    public boolean setSoulPoints(UUID playerId, int points) {
        if (!plugin.isSoulPointsEnabled()) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        int oldPoints = soulPointsManager.getSoulPoints(playerId);
        int clampedPoints = Math.max(0, Math.min(points, getMaxSoulPoints()));
        
        // Fire pre-change event
        SoulPointsChangeEvent changeEvent = new SoulPointsChangeEvent(
            player, oldPoints, clampedPoints, SoulPointsChangeEvent.ChangeReason.API
        );
        Bukkit.getPluginManager().callEvent(changeEvent);
        
        if (changeEvent.isCancelled()) {
            return false;
        }
        
        // Apply the change (use event's potentially modified value)
        soulPointsManager.setSoulPoints(playerId, changeEvent.getNewSoulPoints());
        
        // Fire post-change event
        SoulPointsChangedEvent changedEvent = new SoulPointsChangedEvent(
            player, oldPoints, changeEvent.getNewSoulPoints(), SoulPointsChangeEvent.ChangeReason.API
        );
        Bukkit.getPluginManager().callEvent(changedEvent);
        
        return true;
    }
    
    @Override
    public boolean setSoulPoints(Player player, int points) {
        return setSoulPoints(player.getUniqueId(), points);
    }
    
    @Override
    public boolean addSoulPoints(UUID playerId, int points) {
        if (!plugin.isSoulPointsEnabled()) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        int oldPoints = soulPointsManager.getSoulPoints(playerId);
        int newPoints = Math.max(0, Math.min(oldPoints + points, getMaxSoulPoints()));
        
        // Fire pre-change event
        SoulPointsChangeEvent changeEvent = new SoulPointsChangeEvent(
            player, oldPoints, newPoints, 
            points > 0 ? SoulPointsChangeEvent.ChangeReason.PLUGIN_ADD : SoulPointsChangeEvent.ChangeReason.PLUGIN_REMOVE
        );
        Bukkit.getPluginManager().callEvent(changeEvent);
        
        if (changeEvent.isCancelled()) {
            return false;
        }
        
        // Apply the change
        soulPointsManager.setSoulPoints(playerId, changeEvent.getNewSoulPoints());
        
        // Fire post-change event
        SoulPointsChangedEvent changedEvent = new SoulPointsChangedEvent(
            player, oldPoints, changeEvent.getNewSoulPoints(), 
            points > 0 ? SoulPointsChangeEvent.ChangeReason.PLUGIN_ADD : SoulPointsChangeEvent.ChangeReason.PLUGIN_REMOVE
        );
        Bukkit.getPluginManager().callEvent(changedEvent);
        
        return true;
    }
    
    @Override
    public boolean addSoulPoints(Player player, int points) {
        return addSoulPoints(player.getUniqueId(), points);
    }
    
    @Override
    public boolean removeSoulPoints(UUID playerId, int points) {
        return addSoulPoints(playerId, -points);
    }
    
    @Override
    public boolean removeSoulPoints(Player player, int points) {
        return addSoulPoints(player.getUniqueId(), -points);
    }
    
    @Override
    public DropRates getDropRates(int soulPoints) {
        return soulPointsManager.getDropRates(soulPoints);
    }
    
    @Override
    public DropRates getPlayerDropRates(UUID playerId) {
        int soulPoints = getSoulPoints(playerId);
        return getDropRates(soulPoints);
    }
    
    @Override
    public DropRates getPlayerDropRates(Player player) {
        return getPlayerDropRates(player.getUniqueId());
    }

    @Override
    public int getMaxSoulPoints() {
        return plugin.getConfig().getInt("soul-points.max", 10);
    }

    @Override
    public boolean isSoulPointsEnabled() {
        return plugin.isSoulPointsEnabled();
    }

    @Override
    public int getStartingSoulPoints() {
        return plugin.getConfig().getInt("soul-points.starting", 10);
    }
    
    @Override
    public long getTimeUntilNextRecovery(UUID playerId) {
        return soulPointsManager.getTimeUntilNextRecovery(playerId);
    }
    
    @Override
    public long getTimeUntilNextRecovery(Player player) {
        return getTimeUntilNextRecovery(player.getUniqueId());
    }
    
    @Override
    public boolean hasPlayerData(UUID playerId) {
        // Check if player has been initialized in the system
        try {
            soulPointsManager.getSoulPoints(playerId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean hasPlayerData(Player player) {
        return hasPlayerData(player.getUniqueId());
    }
    
    @Override
    public boolean processRecovery(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return false;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return false;

        int oldPoints = soulPointsManager.getSoulPoints(playerId);
        soulPointsManager.processRecovery(playerId);
        int newPoints = soulPointsManager.getSoulPoints(playerId);
        
        // Only fire events if points actually changed
        if (oldPoints != newPoints) {
            SoulPointsChangedEvent changedEvent = new SoulPointsChangedEvent(
                player, oldPoints, newPoints, SoulPointsChangeEvent.ChangeReason.RECOVERY
            );
            Bukkit.getPluginManager().callEvent(changedEvent);
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean processRecovery(Player player) {
        return processRecovery(player.getUniqueId());
    }
}

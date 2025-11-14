package org.yusaki.lamDeathPenalties.api;

import org.bukkit.entity.Player;
import org.yusaki.lamDeathPenalties.SoulPointsManager.DropRates;

import java.util.UUID;

/**
 * Public API for LamDeathPenalties plugin
 * Allows other plugins to interact with the soul points system safely
 */
public interface LamDeathPenaltiesAPI {
    
    /**
     * Get a player's current soul points
     * @param playerId The player's UUID
     * @return Current soul points (0-10)
     */
    int getSoulPoints(UUID playerId);
    
    /**
     * Get a player's current soul points
     * @param player The player
     * @return Current soul points (0-10)
     */
    int getSoulPoints(Player player);
    
    /**
     * Set a player's soul points
     * @param playerId The player's UUID
     * @param points New soul points value (will be clamped to 0-max)
     * @return True if successful, false if player not found
     */
    boolean setSoulPoints(UUID playerId, int points);
    
    /**
     * Set a player's soul points
     * @param player The player
     * @param points New soul points value (will be clamped to 0-max)
     * @return True if successful
     */
    boolean setSoulPoints(Player player, int points);
    
    /**
     * Add soul points to a player
     * @param playerId The player's UUID
     * @param points Points to add (can be negative to subtract)
     * @return True if successful, false if player not found
     */
    boolean addSoulPoints(UUID playerId, int points);
    
    /**
     * Add soul points to a player
     * @param player The player
     * @param points Points to add (can be negative to subtract)
     * @return True if successful
     */
    boolean addSoulPoints(Player player, int points);
    
    /**
     * Remove soul points from a player
     * @param playerId The player's UUID
     * @param points Points to remove
     * @return True if successful, false if player not found
     */
    boolean removeSoulPoints(UUID playerId, int points);
    
    /**
     * Remove soul points from a player
     * @param player The player
     * @param points Points to remove
     * @return True if successful
     */
    boolean removeSoulPoints(Player player, int points);
    
    /**
     * Get the drop rates for a specific soul points level
     * @param soulPoints The soul points level (0-10)
     * @return DropRates object containing item drop %, hotbar drop, armor drop settings
     */
    DropRates getDropRates(int soulPoints);
    
    /**
     * Get the drop rates for a player's current soul points
     * @param playerId The player's UUID
     * @return DropRates object for the player's current level
     */
    DropRates getPlayerDropRates(UUID playerId);
    
    /**
     * Get the drop rates for a player's current soul points
     * @param player The player
     * @return DropRates object for the player's current level
     */
    DropRates getPlayerDropRates(Player player);
    
    /**
     * Get the maximum soul points value from config
     * @return Server's configured maximum soul points (default 10)
     */
    int getConfigMaxSoulPoints();
    
    /**
     * Get a player's personal maximum soul points
     * @param playerId The player's UUID
     * @return Player's current max soul points capacity
     */
    int getMaxSoulPoints(UUID playerId);
    
    /**
     * Get a player's personal maximum soul points
     * @param player The player
     * @return Player's current max soul points capacity
     */
    int getMaxSoulPoints(Player player);
    
    /**
     * Set a player's personal maximum soul points
     * @param playerId The player's UUID
     * @param maxPoints New max soul points (will be clamped to 0-config max)
     * @return True if successful, false if player not found
     */
    boolean setMaxSoulPoints(UUID playerId, int maxPoints);
    
    /**
     * Set a player's personal maximum soul points
     * @param player The player
     * @param maxPoints New max soul points (will be clamped to 0-config max)
     * @return True if successful
     */
    boolean setMaxSoulPoints(Player player, int maxPoints);
    
    /**
     * Reduce a player's maximum soul points (e.g., from PvP kills)
     * @param playerId The player's UUID
     * @param amount Amount to reduce (respects configured minimum)
     * @return True if successful, false if player not found
     */
    boolean reduceMaxSoulPoints(UUID playerId, int amount);
    
    /**
     * Reduce a player's maximum soul points (e.g., from PvP kills)
     * @param player The player
     * @param amount Amount to reduce (respects configured minimum)
     * @return True if successful
     */
    boolean reduceMaxSoulPoints(Player player, int amount);
    
    /**
     * Add to a player's maximum soul points
     * @param playerId The player's UUID
     * @param amount Amount to add (capped at config max)
     * @return True if successful, false if player not found
     */
    boolean addMaxSoulPoints(UUID playerId, int amount);
    
    /**
     * Add to a player's maximum soul points
     * @param player The player
     * @param amount Amount to add (capped at config max)
     * @return True if successful
     */
    boolean addMaxSoulPoints(Player player, int amount);
    
    /**
     * Get time until next max soul points recovery in milliseconds
     * @param playerId The player's UUID
     * @return Time until next max recovery in milliseconds, 0 if already at config max
     */
    long getTimeUntilNextMaxRecovery(UUID playerId);
    
    /**
     * Get time until next max soul points recovery in milliseconds
     * @param player The player
     * @return Time until next max recovery in milliseconds, 0 if already at config max
     */
    long getTimeUntilNextMaxRecovery(Player player);
    
    /**
     * Manually trigger max soul points recovery processing for a player
     * @param playerId The player's UUID
     * @return True if recovery was processed, false if no recovery was due
     */
    boolean processMaxRecovery(UUID playerId);
    
    /**
     * Manually trigger max soul points recovery processing for a player
     * @param player The player
     * @return True if recovery was processed, false if no recovery was due
     */
    boolean processMaxRecovery(Player player);

    /**
     * Check whether the soul points system is currently enabled
     * @return true if enabled, false if disabled via configuration
     */
    boolean isSoulPointsEnabled();

    /**
     * Get the starting soul points value for new players
     * @return Starting soul points (default 10)
     */
    int getStartingSoulPoints();
    
    /**
     * Get time until next recovery for a player in milliseconds
     * @param playerId The player's UUID
     * @return Time until next recovery in milliseconds, 0 if already at max
     */
    long getTimeUntilNextRecovery(UUID playerId);
    
    /**
     * Get time until next recovery for a player in milliseconds
     * @param player The player
     * @return Time until next recovery in milliseconds, 0 if already at max
     */
    long getTimeUntilNextRecovery(Player player);
    
    /**
     * Check if a player exists in the soul points system
     * @param playerId The player's UUID
     * @return True if player has data
     */
    boolean hasPlayerData(UUID playerId);
    
    /**
     * Check if a player exists in the soul points system
     * @param player The player
     * @return True if player has data
     */
    boolean hasPlayerData(Player player);
    
    /**
     * Manually trigger recovery processing for a player
     * @param playerId The player's UUID
     * @return True if recovery was processed, false if no recovery was due
     */
    boolean processRecovery(UUID playerId);
    
    /**
     * Manually trigger recovery processing for a player
     * @param player The player
     * @return True if recovery was processed, false if no recovery was due
     */
    boolean processRecovery(Player player);
}

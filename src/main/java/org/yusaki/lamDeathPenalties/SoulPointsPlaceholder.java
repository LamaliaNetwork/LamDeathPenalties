package org.yusaki.lamDeathPenalties;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion for LamDeathPenalties
 * 
 * Available placeholders (use %soulpoints_<placeholder>%):
 * 
 * Soul Points:
 *   - current                    : Current soul points
 *   - max                        : Player's personal max soul points
 *   - max_personal               : Player's personal max soul points (alias)
 *   - max_config                 : Server's configured max soul points
 *   - percentage                 : Current/Max percentage (0-100)
 *   - progress_bar               : Visual progress bar (█████░░░░░)
 * 
 * Drop Rates (for current soul points level):
 *   - next_item_drop             : Item drop percentage
 *   - next_hotbar_drop           : Whether hotbar drops (true/false)
 *   - next_armor_drop            : Whether armor drops (true/false)
 * 
 * Recovery:
 *   - recovery_time              : Time until next soul point recovery (formatted)
 *   - recovery_time_seconds      : Time until next recovery (in seconds)
 *   - max_recovery_time          : Time until next max soul points recovery (formatted)
 *   - max_recovery_time_seconds  : Time until next max recovery (in seconds)
 */
public class SoulPointsPlaceholder extends PlaceholderExpansion {
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    private final RecoveryScheduler recoveryScheduler;
    
    public SoulPointsPlaceholder(LamDeathPenalties plugin, SoulPointsManager soulPointsManager, RecoveryScheduler recoveryScheduler) {
        this.plugin = plugin;
        this.soulPointsManager = soulPointsManager;
        this.recoveryScheduler = recoveryScheduler;
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "soulpoints";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }
    
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        
        switch (params.toLowerCase()) {
            case "current":
                return String.valueOf(soulPointsManager.getSoulPoints(player.getUniqueId()));
                
            case "max":
                return String.valueOf(soulPointsManager.getMaxSoulPoints(player.getUniqueId()));
                
            case "max_personal":
                return String.valueOf(soulPointsManager.getMaxSoulPoints(player.getUniqueId()));
                
            case "max_config":
                return String.valueOf(plugin.getConfig().getInt("soul-points.max", 10));
                
            case "percentage":
                int current = soulPointsManager.getSoulPoints(player.getUniqueId());
                int max = soulPointsManager.getMaxSoulPoints(player.getUniqueId());
                if (max == 0) return "0";
                return String.valueOf((int) ((double) current / max * 100));
                
            case "next_item_drop":
                int currentPoints = soulPointsManager.getSoulPoints(player.getUniqueId());
                SoulPointsManager.DropRates dropRates = soulPointsManager.getDropRates(currentPoints);
                return String.valueOf(dropRates.itemDrop);
                
            case "next_hotbar_drop":
                currentPoints = soulPointsManager.getSoulPoints(player.getUniqueId());
                dropRates = soulPointsManager.getDropRates(currentPoints);
                return String.valueOf(dropRates.hotbarDrop);
                
            case "next_armor_drop":
                currentPoints = soulPointsManager.getSoulPoints(player.getUniqueId());
                dropRates = soulPointsManager.getDropRates(currentPoints);
                return String.valueOf(dropRates.armorDrop);
                
            case "recovery_time":
                if (!player.isOnline()) {
                    return "N/A";
                }
                long timeUntilRecovery = recoveryScheduler.getTimeUntilNextRecovery(player.getUniqueId());
                return formatTime(timeUntilRecovery);
                
            case "recovery_time_seconds":
                if (!player.isOnline()) {
                    return "0";
                }
                timeUntilRecovery = recoveryScheduler.getTimeUntilNextRecovery(player.getUniqueId());
                return String.valueOf(timeUntilRecovery / 1000);
                
            case "max_recovery_time":
                if (!player.isOnline()) {
                    return "N/A";
                }
                long timeUntilMaxRecovery = getTimeUntilNextMaxRecovery(player.getUniqueId());
                return formatTime(timeUntilMaxRecovery);
                
            case "max_recovery_time_seconds":
                if (!player.isOnline()) {
                    return "0";
                }
                timeUntilMaxRecovery = getTimeUntilNextMaxRecovery(player.getUniqueId());
                return String.valueOf(timeUntilMaxRecovery / 1000);
                
            case "progress_bar":
                current = soulPointsManager.getSoulPoints(player.getUniqueId());
                max = soulPointsManager.getMaxSoulPoints(player.getUniqueId());
                return createProgressBar(current, max);
                
            default:
                return null;
        }
    }
    
    private String formatTime(long milliseconds) {
        if (milliseconds <= 0) {
            return "Ready";
        }
        
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds %= 60;
        minutes %= 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    private long getTimeUntilNextMaxRecovery(java.util.UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return 0;
        }
        if (!plugin.getConfig().getBoolean("soul-points.max-soul-points.regeneration.enabled", true)) {
            return 0;
        }
        
        int currentMax = soulPointsManager.getMaxSoulPoints(playerId);
        int configMax = plugin.getConfig().getInt("soul-points.max", 10);
        
        if (currentMax >= configMax) {
            return 0;
        }
        
        SoulPointsManager.PlayerSoulData data = soulPointsManager.playerData.get(playerId);
        if (data == null) {
            return 0;
        }
        
        String maxRecoveryMode = plugin.getConfig().getString("soul-points.max-soul-points.regeneration.mode", "real-time");
        long intervalSeconds = plugin.getConfig().getLong("soul-points.max-soul-points.regeneration.interval-seconds", 86400L);
        long intervalMs = intervalSeconds * 1000L;
        
        if (maxRecoveryMode.equals("active-time")) {
            long currentTime = System.currentTimeMillis();
            long accumulatedPlayTime = data.totalMaxPlayTime;
            if (data.maxSessionStartTime > 0L) {
                accumulatedPlayTime += Math.max(0L, currentTime - data.maxSessionStartTime);
            }
            
            long remainder = intervalMs - (accumulatedPlayTime % intervalMs);
            return remainder == intervalMs ? 0 : remainder;
        }
        
        long timeSinceLastRecovery = System.currentTimeMillis() - data.lastMaxRecoveryTime;
        return Math.max(0, intervalMs - timeSinceLastRecovery);
    }
    
    private String createProgressBar(int current, int max) {
        if (max == 0) {
            return "░░░░░░░░░░"; // Empty bar if max is 0
        }
        
        int barLength = 10;
        int filledBars = (int) ((double) current / max * barLength);
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        return bar.toString();
    }
}
package org.yusaki.lamDeathPenalties;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

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
                return String.valueOf(plugin.getConfig().getInt("soul-points.max", 10));
                
            case "percentage":
                int current = soulPointsManager.getSoulPoints(player.getUniqueId());
                int max = plugin.getConfig().getInt("soul-points.max", 10);
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
                
            case "progress_bar":
                current = soulPointsManager.getSoulPoints(player.getUniqueId());
                max = plugin.getConfig().getInt("soul-points.max", 10);
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
    
    private String createProgressBar(int current, int max) {
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
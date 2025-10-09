package org.yusaki.lamDeathPenalties;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

import static org.yusaki.lib.modules.MessageManager.placeholders;

public class RecoveryScheduler {
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    private final FoliaLib foliaLib;
    
    public RecoveryScheduler(LamDeathPenalties plugin, SoulPointsManager soulPointsManager, FoliaLib foliaLib) {
        this.plugin = plugin;
        this.soulPointsManager = soulPointsManager;
        this.foliaLib = foliaLib;
        
        startRecoveryTask();
    }
    
    private void startRecoveryTask() {
        // Run recovery check every 5 minutes (6000 ticks)
        foliaLib.getImpl().runTimer(task -> {
            processAllPlayerRecovery();
        }, 6000L, 6000L);
    }
    
    private void processAllPlayerRecovery() {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        String recoveryMode = plugin.getRecoveryMode();

        if (recoveryMode.equals("real-time")) {
            processRealTimeRecovery();
        } else if (recoveryMode.equals("active-time")) {
            processActiveTimeRecovery();
        }
    }
    
    private void processRealTimeRecovery() {
        // Process recovery for all players (online and offline)
        // This is handled in SoulPointsManager.processRecovery()
        for (Player player : Bukkit.getOnlinePlayers()) {
            soulPointsManager.processRecovery(player.getUniqueId());
        }
        
        // For offline players, recovery will be processed when they join
        // This is more efficient than loading all player data every 5 minutes
    }
    
    private void processActiveTimeRecovery() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            soulPointsManager.startSession(playerId);
            int oldSoulPoints = soulPointsManager.getSoulPoints(playerId);
            
            soulPointsManager.processRecovery(playerId);
            
            int newSoulPoints = soulPointsManager.getSoulPoints(playerId);
            
            // Notify player if they gained soul points
            if (newSoulPoints > oldSoulPoints) {
                int recoveryCount = newSoulPoints - oldSoulPoints;
                int maxSoulPoints = plugin.getConfig().getInt("soul-points.max", 10);
                
                plugin.getYskLib().logDebug(plugin, "Player " + player.getName() + " recovered " + recoveryCount + " soul point(s) via active-time mode");
                org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();
                messageManager.sendMessageList(plugin, player, "recovery-gained", placeholders(
                    "count", String.valueOf(recoveryCount),
                    "plural", recoveryCount > 1 ? "s" : "",
                    "current_points", String.valueOf(newSoulPoints),
                    "max_points", String.valueOf(maxSoulPoints)
                ));
            }
        }
    }
    
    public void onPlayerJoin(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        String recoveryMode = plugin.getRecoveryMode();

        Player player = Bukkit.getPlayer(playerId);
        String playerName = player != null ? player.getName() : playerId.toString();

        if (recoveryMode.equals("real-time")) {
            // Process any recovery that should have happened while offline
            plugin.getYskLib().logDebug(plugin, "Processing offline recovery for " + playerName);
            soulPointsManager.processRecovery(playerId);
        } else if (recoveryMode.equals("active-time")) {
            soulPointsManager.startSession(playerId);
            int oldSoulPoints = soulPointsManager.getSoulPoints(playerId);
            soulPointsManager.processRecovery(playerId);
            int newSoulPoints = soulPointsManager.getSoulPoints(playerId);

            if (player != null && newSoulPoints > oldSoulPoints) {
                int recoveryCount = newSoulPoints - oldSoulPoints;
                int maxSoulPoints = plugin.getConfig().getInt("soul-points.max", 10);

                plugin.getYskLib().logDebug(plugin, "Player " + player.getName() + " recovered " + recoveryCount + " soul point(s) on join (active-time)");
                org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();
                messageManager.sendMessageList(plugin, player, "recovery-gained", placeholders(
                    "count", String.valueOf(recoveryCount),
                    "plural", recoveryCount > 1 ? "s" : "",
                    "current_points", String.valueOf(newSoulPoints),
                    "max_points", String.valueOf(maxSoulPoints)
                ));
            } else {
                plugin.getYskLib().logDebug(plugin, "Active-time recovery mode for " + playerName);
            }
        }
    }
    
    public void onPlayerQuit(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        String recoveryMode = plugin.getRecoveryMode();

        if (recoveryMode.equals("active-time")) {
            soulPointsManager.endSession(playerId);
            plugin.getYskLib().logDebug(plugin, "Player quit - active-time session persisted");
        }
    }
    
    public long getTimeUntilNextRecovery(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return 0;
        }
        return soulPointsManager.getTimeUntilNextRecovery(playerId);
    }
}

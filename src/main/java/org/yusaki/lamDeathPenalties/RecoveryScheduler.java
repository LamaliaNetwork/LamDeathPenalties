package org.yusaki.lamDeathPenalties;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.yusaki.lib.modules.MessageManager.placeholders;

public class RecoveryScheduler {
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    private final FoliaLib foliaLib;
    private final Map<UUID, Long> playerSessionStartTimes;
    
    public RecoveryScheduler(LamDeathPenalties plugin, SoulPointsManager soulPointsManager, FoliaLib foliaLib) {
        this.plugin = plugin;
        this.soulPointsManager = soulPointsManager;
        this.foliaLib = foliaLib;
        this.playerSessionStartTimes = new HashMap<>();
        
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
        long currentTime = System.currentTimeMillis();
        long intervalSeconds = plugin.getRecoveryIntervalSeconds();
        long intervalMs = intervalSeconds * 1000L;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            Long sessionStart = playerSessionStartTimes.get(playerId);
            
            if (sessionStart != null) {
                long sessionTime = currentTime - sessionStart;
                
                // Check if player has been online long enough for recovery
                if (sessionTime >= intervalMs) {
                    int currentSoulPoints = soulPointsManager.getSoulPoints(playerId);
                    int maxSoulPoints = plugin.getConfig().getInt("soul-points.max", 10);
                    
                    if (currentSoulPoints < maxSoulPoints) {
                        // Calculate how many recoveries the player should get
                        int recoveryCount = (int) (sessionTime / intervalMs);
                        
                        // Add soul points (manager will handle the maximum)
                        soulPointsManager.addSoulPoints(playerId, recoveryCount);
                        
                        // Update session start time
                        long remainingTime = sessionTime % intervalMs;
                        playerSessionStartTimes.put(playerId, currentTime - remainingTime);
                        
                        // Notify player if they gained soul points
                        if (recoveryCount > 0) {
                            plugin.getYskLib().logDebug(plugin, "Player " + player.getName() + " recovered " + recoveryCount + " soul point(s) via active-time mode");
                            org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();
                            messageManager.sendMessageList(plugin, player, "recovery-gained", placeholders(
                                "count", String.valueOf(recoveryCount),
                                "plural", recoveryCount > 1 ? "s" : "",
                                "current_points", String.valueOf(soulPointsManager.getSoulPoints(playerId)),
                                "max_points", String.valueOf(maxSoulPoints)
                            ));
                        }
                    }
                }
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
            // Start tracking session time
            playerSessionStartTimes.put(playerId, System.currentTimeMillis());
            plugin.getYskLib().logDebug(plugin, "Started active-time session tracking for " + playerName);
        }
    }
    
    public void onPlayerQuit(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        String recoveryMode = plugin.getRecoveryMode();

        if (recoveryMode.equals("active-time")) {
            // Update total play time and remove from session tracking
            Long sessionStart = playerSessionStartTimes.remove(playerId);
            if (sessionStart != null) {
                long sessionTime = System.currentTimeMillis() - sessionStart;
                soulPointsManager.updatePlayTime(playerId, sessionTime);
            }
        }
    }
    
    public long getTimeUntilNextRecovery(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return 0;
        }
        String recoveryMode = plugin.getRecoveryMode();

        if (recoveryMode.equals("real-time")) {
            return soulPointsManager.getTimeUntilNextRecovery(playerId);
        } else if (recoveryMode.equals("active-time")) {
            Long sessionStart = playerSessionStartTimes.get(playerId);
            if (sessionStart == null) {
                return 0; // Player not online
            }
            
            long intervalSeconds = plugin.getRecoveryIntervalSeconds();
            long intervalMs = intervalSeconds * 1000L;
            long sessionTime = System.currentTimeMillis() - sessionStart;

            return Math.max(0, intervalMs - sessionTime);
        }
        
        return 0;
    }
}

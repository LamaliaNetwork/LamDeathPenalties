package org.yusaki.lamDeathPenalties;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.yusaki.lamDeathPenalties.api.events.SoulPointsChangeEvent;
import org.yusaki.lamDeathPenalties.api.events.SoulPointsChangedEvent;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SoulPointsManager {
    private final LamDeathPenalties plugin;
    private final Gson gson;
    private final File dataFile;
    private Map<UUID, PlayerSoulData> playerData;
    
    public SoulPointsManager(LamDeathPenalties plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.json");
        this.playerData = new HashMap<>();
        loadPlayerData();
    }
    
    public int getSoulPoints(UUID playerId) {
        PlayerSoulData data = playerData.get(playerId);
        if (data == null) {
            // New player, create with starting soul points
            int startingPoints = plugin.getConfig().getInt("soul-points.starting", 10);
            data = new PlayerSoulData(startingPoints, System.currentTimeMillis(), 0);
            playerData.put(playerId, data);
            savePlayerData();
        }
        return data.soulPoints;
    }
    
    public void setSoulPoints(UUID playerId, int points) {
        int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
        points = Math.max(0, Math.min(points, maxPoints));
        
        PlayerSoulData data = playerData.get(playerId);
        if (data == null) {
            data = new PlayerSoulData(points, System.currentTimeMillis(), 0);
        } else {
            data.soulPoints = points;
        }
        playerData.put(playerId, data);
        savePlayerData();
    }
    
    public void addSoulPoints(UUID playerId, int points) {
        int current = getSoulPoints(playerId);
        setSoulPoints(playerId, current + points);
    }
    
    public void removeSoulPoints(UUID playerId, int points) {
        int current = getSoulPoints(playerId);
        setSoulPoints(playerId, current - points);
    }
    
    public void removeSoulPoint(UUID playerId) {
        removeSoulPointWithReason(playerId, SoulPointsChangeEvent.ChangeReason.DEATH);
    }
    
    public void removeSoulPointWithReason(UUID playerId, SoulPointsChangeEvent.ChangeReason reason) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            setSoulPointsWithReason(playerId, getSoulPoints(playerId) - 1, reason);
        } else {
            int current = getSoulPoints(playerId);
            setSoulPoints(playerId, current - 1);
        }
    }
    
    public void setSoulPointsWithReason(UUID playerId, int points, SoulPointsChangeEvent.ChangeReason reason) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            setSoulPoints(playerId, points);
            return;
        }
        
        int oldPoints = getSoulPoints(playerId);
        int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
        int clampedPoints = Math.max(0, Math.min(points, maxPoints));
        
        if (oldPoints == clampedPoints) {
            return;
        }
        
        SoulPointsChangeEvent changeEvent = new SoulPointsChangeEvent(player, oldPoints, clampedPoints, reason);
        Bukkit.getPluginManager().callEvent(changeEvent);
        
        if (changeEvent.isCancelled()) {
            return;
        }
        
        setSoulPoints(playerId, changeEvent.getNewSoulPoints());
        
        SoulPointsChangedEvent changedEvent = new SoulPointsChangedEvent(
            player, oldPoints, changeEvent.getNewSoulPoints(), reason
        );
        Bukkit.getPluginManager().callEvent(changedEvent);
    }
    
    public DropRates getDropRates(int soulPoints) {
        ConfigurationSection dropConfig = plugin.getConfig().getConfigurationSection("drop-rates." + soulPoints);
        if (dropConfig == null) {
            // Fallback to 0 soul points config if level doesn't exist
            dropConfig = plugin.getConfig().getConfigurationSection("drop-rates.0");
        }
        
        int itemDrop = dropConfig.getInt("item-drop", 100);
        boolean hotbarDrop = dropConfig.getBoolean("hotbar-drop", true);
        boolean armorDrop = dropConfig.getBoolean("armor-drop", true);
        
        return new DropRates(itemDrop, hotbarDrop, armorDrop);
    }
    
    public void updatePlayTime(UUID playerId, long sessionTime) {
        PlayerSoulData data = playerData.get(playerId);
        if (data != null) {
            data.totalPlayTime += sessionTime;
            savePlayerData();
        }
    }
    
    public void processRecovery(UUID playerId) {
        PlayerSoulData data = playerData.get(playerId);
        if (data == null) return;
        
        String recoveryMode = plugin.getConfig().getString("recovery.mode", "real-time");
        int intervalHours = plugin.getConfig().getInt("recovery.interval-hours", 1);
        long intervalMs = intervalHours * 60 * 60 * 1000L;
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRecovery = currentTime - data.lastRecoveryTime;
        
        if (recoveryMode.equals("active-time")) {
            // For active time, we need to track play sessions
            // This is simplified - in practice you'd track session start/end times
            return;
        }
        
        // Real-time recovery
        if (timeSinceLastRecovery >= intervalMs) {
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            if (data.soulPoints < maxPoints) {
                int recoveryCount = (int) (timeSinceLastRecovery / intervalMs);
                int newPoints = Math.min(maxPoints, data.soulPoints + recoveryCount);
                data.soulPoints = newPoints;
                data.lastRecoveryTime = currentTime;
                savePlayerData();
            }
        }
    }
    
    public long getTimeUntilNextRecovery(UUID playerId) {
        PlayerSoulData data = playerData.get(playerId);
        if (data == null) return 0;
        
        int intervalHours = plugin.getConfig().getInt("recovery.interval-hours", 1);
        long intervalMs = intervalHours * 60 * 60 * 1000L;
        long timeSinceLastRecovery = System.currentTimeMillis() - data.lastRecoveryTime;
        
        return Math.max(0, intervalMs - timeSinceLastRecovery);
    }
    
    private void loadPlayerData() {
        if (!dataFile.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<UUID, PlayerSoulData>>(){}.getType();
            Map<UUID, PlayerSoulData> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                playerData = loaded;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load player data: " + e.getMessage());
        }
    }
    
    public void savePlayerData() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(playerData, writer);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data: " + e.getMessage());
        }
    }
    
    public static class PlayerSoulData {
        public int soulPoints;
        public long lastRecoveryTime;
        public long totalPlayTime;
        
        public PlayerSoulData(int soulPoints, long lastRecoveryTime, long totalPlayTime) {
            this.soulPoints = soulPoints;
            this.lastRecoveryTime = lastRecoveryTime;
            this.totalPlayTime = totalPlayTime;
        }
    }
    
    public static class DropRates {
        public final int itemDrop;
        public final boolean hotbarDrop;
        public final boolean armorDrop;
        
        public DropRates(int itemDrop, boolean hotbarDrop, boolean armorDrop) {
            this.itemDrop = itemDrop;
            this.hotbarDrop = hotbarDrop;
            this.armorDrop = armorDrop;
        }
    }
}
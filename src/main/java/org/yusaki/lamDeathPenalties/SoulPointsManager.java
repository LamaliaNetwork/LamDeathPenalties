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
        plugin.getYskLib().logDebug(plugin, "SoulPointsManager initialized with " + playerData.size() + " players");
    }
    
    public int getSoulPoints(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            PlayerSoulData data = playerData.get(playerId);
            return data != null ? data.soulPoints : plugin.getConfig().getInt("soul-points.starting", 10);
        }
        PlayerSoulData data = playerData.get(playerId);
        if (data == null) {
            // New player, create with starting soul points
            int startingPoints = plugin.getConfig().getInt("soul-points.starting", 10);
            data = new PlayerSoulData(startingPoints, System.currentTimeMillis(), 0);
            playerData.put(playerId, data);
            savePlayerData();
            plugin.getYskLib().logDebug(plugin, "Created new player data for " + playerId + " with " + startingPoints + " soul points");
        }
        return data.soulPoints;
    }
    
    public void setSoulPoints(UUID playerId, int points) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
        int oldPoints = playerData.containsKey(playerId) ? playerData.get(playerId).soulPoints : 0;
        points = Math.max(0, Math.min(points, maxPoints));

        PlayerSoulData data = playerData.get(playerId);
        if (data == null) {
            data = new PlayerSoulData(points, System.currentTimeMillis(), 0);
        } else {
            data.soulPoints = points;
        }
        playerData.put(playerId, data);
        savePlayerData();

        if (oldPoints != points) {
            plugin.getYskLib().logDebug(plugin, "Soul points changed for " + playerId + ": " + oldPoints + " -> " + points);
        }
    }
    
    public void addSoulPoints(UUID playerId, int points) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        int current = getSoulPoints(playerId);
        setSoulPoints(playerId, current + points);
    }

    public void removeSoulPoints(UUID playerId, int points) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        int current = getSoulPoints(playerId);
        setSoulPoints(playerId, current - points);
    }

    public void removeSoulPoint(UUID playerId) {
        removeSoulPointWithReason(playerId, SoulPointsChangeEvent.ChangeReason.DEATH);
    }
    
    public void removeSoulPointWithReason(UUID playerId, SoulPointsChangeEvent.ChangeReason reason) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            setSoulPointsWithReason(playerId, getSoulPoints(playerId) - 1, reason);
        } else {
            int current = getSoulPoints(playerId);
            setSoulPoints(playerId, current - 1);
        }
    }
    
    public void setSoulPointsWithReason(UUID playerId, int points, SoulPointsChangeEvent.ChangeReason reason) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
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
        if (!plugin.isSoulPointsEnabled()) {
            return getDefaultDropRates();
        }

        ConfigurationSection dropRatesRoot = plugin.getConfig().getConfigurationSection("soul-points.drop-rates");
        MoneyPenaltyConfig defaultMoney = getDefaultMoneyPenalty();
        ConfigurationSection dropConfig = findDropConfig(dropRatesRoot, soulPoints);

        if (dropConfig == null) {
            // Legacy fallback for older configs that used top-level drop-rates
            ConfigurationSection legacyDropRates = plugin.getConfig().getConfigurationSection("drop-rates");
            dropConfig = findDropConfig(legacyDropRates, soulPoints);
        }

        if (dropConfig != null) {
            return buildDropRates(dropConfig, defaultMoney);
        }

        return getDefaultDropRates();
    }

    private ConfigurationSection findDropConfig(ConfigurationSection root, int soulPoints) {
        if (root == null) {
            return null;
        }

        int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
        int target = Math.max(0, Math.min(soulPoints, maxPoints));

        for (int level = target; level <= maxPoints; level++) {
            ConfigurationSection section = root.getConfigurationSection(String.valueOf(level));
            if (section != null) {
                return section;
            }
        }

        return null;
    }

    private DropRates buildDropRates(ConfigurationSection section, MoneyPenaltyConfig defaultMoney) {
        int itemDrop = section.getInt("item-drop", 100);
        boolean hotbarDrop = section.getBoolean("hotbar-drop", true);
        boolean armorDrop = section.getBoolean("armor-drop", true);
        MoneyPenaltyConfig moneyPenalty = resolveMoneyPenalty(section, defaultMoney);

        return new DropRates(itemDrop, hotbarDrop, armorDrop, moneyPenalty.amount, moneyPenalty.mode);
    }

    private DropRates getDefaultDropRates() {
        int itemDrop = plugin.getConfig().getInt("default-penalty.item-drop", 0);
        boolean hotbarDrop = plugin.getConfig().getBoolean("default-penalty.hotbar-drop", false);
        boolean armorDrop = plugin.getConfig().getBoolean("default-penalty.armor-drop", false);
        MoneyPenaltyConfig moneyPenalty = getDefaultMoneyPenalty();

        return new DropRates(itemDrop, hotbarDrop, armorDrop, moneyPenalty.amount, moneyPenalty.mode);
    }

    private MoneyPenaltyConfig getDefaultMoneyPenalty() {
        ConfigurationSection moneySection = plugin.getConfig().getConfigurationSection("default-penalty.money");
        DropRates.MoneyPenaltyMode mode = DropRates.MoneyPenaltyMode.FLAT;
        double amount = 0.0D;

        if (moneySection != null) {
            mode = DropRates.MoneyPenaltyMode.fromString(moneySection.getString("mode"), DropRates.MoneyPenaltyMode.FLAT);
            amount = moneySection.getDouble("amount", 0.0D);
        } else if (plugin.getConfig().isSet("default-penalty.money")) {
            amount = plugin.getConfig().getDouble("default-penalty.money", 0.0D);
        }

        return new MoneyPenaltyConfig(amount, mode);
    }

    private MoneyPenaltyConfig resolveMoneyPenalty(ConfigurationSection section, MoneyPenaltyConfig fallback) {
        if (section == null) {
            return fallback;
        }

        MoneyPenaltyConfig result = fallback;

        if (section.isConfigurationSection("money")) {
            ConfigurationSection moneySection = section.getConfigurationSection("money");
            if (moneySection != null) {
                DropRates.MoneyPenaltyMode mode = DropRates.MoneyPenaltyMode.fromString(
                    moneySection.getString("mode"),
                    fallback != null ? fallback.mode : DropRates.MoneyPenaltyMode.FLAT
                );
                double amount = moneySection.getDouble("amount", fallback != null ? fallback.amount : 0.0D);
                result = new MoneyPenaltyConfig(amount, mode);
            }
        } else if (section.isSet("money")) {
            double amount = section.getDouble("money");
            DropRates.MoneyPenaltyMode mode = fallback != null ? fallback.mode : DropRates.MoneyPenaltyMode.FLAT;
            result = new MoneyPenaltyConfig(amount, mode);
        }

        return result;
    }
    
    public void updatePlayTime(UUID playerId, long sessionTime) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        PlayerSoulData data = playerData.get(playerId);
        if (data != null) {
            data.totalPlayTime += sessionTime;
            savePlayerData();
        }
    }

    public void processRecovery(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return;
        }
        PlayerSoulData data = playerData.get(playerId);
        if (data == null) return;
        
        String recoveryMode = plugin.getRecoveryMode();
        long intervalSeconds = plugin.getRecoveryIntervalSeconds();
        long intervalMs = intervalSeconds * 1000L;
        
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
        if (!plugin.isSoulPointsEnabled()) {
            return 0;
        }
        PlayerSoulData data = playerData.get(playerId);
        if (data == null) return 0;
        
        long intervalSeconds = plugin.getRecoveryIntervalSeconds();
        long intervalMs = intervalSeconds * 1000L;
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
            plugin.getYskLib().logWarn(plugin, "Failed to load player data: " + e.getMessage());
        }
        plugin.getYskLib().logDebug(plugin, "Loaded " + playerData.size() + " player records from " + dataFile.getName());
    }
    
    public void savePlayerData() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(playerData, writer);
            }
            plugin.getYskLib().logDebug(plugin, "Saved " + playerData.size() + " player records to " + dataFile.getName());
        } catch (IOException e) {
            plugin.getYskLib().logWarn(plugin, "Failed to save player data: " + e.getMessage());
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
    
    private static class MoneyPenaltyConfig {
        final double amount;
        final DropRates.MoneyPenaltyMode mode;

        MoneyPenaltyConfig(double amount, DropRates.MoneyPenaltyMode mode) {
            this.amount = amount;
            this.mode = mode;
        }
    }

    public static class DropRates {
        public final int itemDrop;
        public final boolean hotbarDrop;
        public final boolean armorDrop;
        public final double moneyPenalty;
        public final MoneyPenaltyMode moneyMode;
        
        public DropRates(int itemDrop, boolean hotbarDrop, boolean armorDrop, double moneyPenalty, MoneyPenaltyMode moneyMode) {
            this.itemDrop = itemDrop;
            this.hotbarDrop = hotbarDrop;
            this.armorDrop = armorDrop;
            this.moneyPenalty = moneyPenalty;
            this.moneyMode = moneyMode;
        }

        public enum MoneyPenaltyMode {
            FLAT,
            PERCENT;

            public static MoneyPenaltyMode fromString(String value, MoneyPenaltyMode fallback) {
                if (value == null) {
                    return fallback;
                }

                switch (value.toLowerCase()) {
                    case "flat":
                    case "absolute":
                        return FLAT;
                    case "percent":
                    case "percentage":
                        return PERCENT;
                    default:
                        return fallback;
                }
            }
        }
    }
}

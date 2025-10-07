package org.yusaki.lamDeathPenalties;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.yusaki.lamDeathPenalties.api.events.SoulPointsChangeEvent;
import org.yusaki.lamDeathPenalties.api.events.SoulPointsChangedEvent;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SoulPointsManager {
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("d9f8f1e3-2c11-4a3a-9abc-1f1e6f9d72b5");
    private static final String MAX_HEALTH_MODIFIER_NAME = "LamDeathPenaltiesMaxHealth";
    private static final double MIN_MAX_HEALTH_POINTS = 2.0D;

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

        refreshPlayerMaxHealth(playerId);

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
        
        int newPoints = changeEvent.getNewSoulPoints();
        setSoulPoints(playerId, newPoints);

        // Execute commands for the new soul points level (only when decreasing)
        if (newPoints < oldPoints) {
            DropRates dropRates = getDropRates(newPoints);
            if (dropRates != null && !dropRates.commands.isEmpty()) {
                executeCommands(dropRates.commands, player);
            }
        }

        SoulPointsChangedEvent changedEvent = new SoulPointsChangedEvent(
            player, oldPoints, newPoints, reason
        );
        Bukkit.getPluginManager().callEvent(changedEvent);
    }
    
    public DropRates getDropRates(int soulPoints) {
        if (!plugin.isSoulPointsEnabled()) {
            return getDefaultDropRates();
        }

        ConfigurationSection dropRatesRoot = plugin.getConfig().getConfigurationSection("soul-points.drop-rates");
        if (dropRatesRoot == null) {
            // Legacy fallback for older configs that used top-level drop-rates
            dropRatesRoot = plugin.getConfig().getConfigurationSection("drop-rates");
        }

        if (dropRatesRoot == null) {
            return getDefaultDropRates();
        }

        // Get defaults as ultimate fallback
        ItemPenaltyConfig defaultItems = getDefaultItemPenalty();
        MoneyPenaltyConfig defaultMoney = getDefaultMoneyPenalty();
        MaxHealthPenaltyConfig defaultMaxHealth = getDefaultMaxHealthPenalty();

        // Search through levels from current to max, building fallbacks for each penalty type
        int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
        int target = Math.max(0, Math.min(soulPoints, maxPoints));

        ItemPenaltyConfig resolvedItems = null;
        MoneyPenaltyConfig resolvedMoney = null;
        MaxHealthPenaltyConfig resolvedMaxHealth = null;
        List<String> resolvedCommands = null;

        for (int level = target; level <= maxPoints; level++) {
            ConfigurationSection section = dropRatesRoot.getConfigurationSection(String.valueOf(level));
            if (section != null) {
                if (resolvedItems == null) {
                    resolvedItems = tryResolveItemPenalty(section);
                }
                if (resolvedMoney == null) {
                    resolvedMoney = tryResolveMoneyPenalty(section);
                }
                if (resolvedMaxHealth == null) {
                    resolvedMaxHealth = tryResolveMaxHealthPenalty(section);
                }
                if (resolvedCommands == null && section.isList("commands")) {
                    resolvedCommands = section.getStringList("commands");
                }
            }
        }

        // Use defaults for any unresolved penalties
        if (resolvedItems == null) resolvedItems = defaultItems;
        if (resolvedMoney == null) resolvedMoney = defaultMoney;
        if (resolvedMaxHealth == null) resolvedMaxHealth = defaultMaxHealth;
        if (resolvedCommands == null) resolvedCommands = Collections.emptyList();

        return new DropRates(
            resolvedItems.dropPercent,
            resolvedItems.hotbarDrop,
            resolvedItems.armorDrop,
            resolvedMoney.amount,
            resolvedMoney.mode,
            resolvedMaxHealth.amount,
            resolvedMaxHealth.mode,
            resolvedCommands
        );
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

    private DropRates getDefaultDropRates() {
        ItemPenaltyConfig itemPenalty = getDefaultItemPenalty();
        MoneyPenaltyConfig moneyPenalty = getDefaultMoneyPenalty();
        MaxHealthPenaltyConfig maxHealthPenalty = getDefaultMaxHealthPenalty();

        return new DropRates(
            itemPenalty.dropPercent,
            itemPenalty.hotbarDrop,
            itemPenalty.armorDrop,
            moneyPenalty.amount,
            moneyPenalty.mode,
            maxHealthPenalty.amount,
            maxHealthPenalty.mode,
            Collections.emptyList()
        );
    }

    // Try to resolve penalty without fallbacks - returns null if not defined at this level
    private ItemPenaltyConfig tryResolveItemPenalty(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        if (section.isConfigurationSection("items")) {
            ConfigurationSection itemsSection = section.getConfigurationSection("items");
            if (itemsSection != null) {
                int dropPercent = itemsSection.getInt("drop-percent", -1);
                boolean hotbarDrop = itemsSection.getBoolean("hotbar", false);
                boolean armorDrop = itemsSection.getBoolean("armor", false);
                if (dropPercent >= 0) {
                    return new ItemPenaltyConfig(dropPercent, hotbarDrop, armorDrop);
                }
            }
        }

        if (section.isSet("item-drop")) {
            int dropPercent = section.getInt("item-drop", -1);
            boolean hotbarDrop = section.getBoolean("hotbar-drop", false);
            boolean armorDrop = section.getBoolean("armor-drop", false);
            if (dropPercent >= 0) {
                return new ItemPenaltyConfig(dropPercent, hotbarDrop, armorDrop);
            }
        }

        return null;
    }

    private MoneyPenaltyConfig tryResolveMoneyPenalty(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        if (section.isConfigurationSection("money")) {
            ConfigurationSection moneySection = section.getConfigurationSection("money");
            if (moneySection != null) {
                DropRates.MoneyPenaltyMode mode = DropRates.MoneyPenaltyMode.fromString(
                    moneySection.getString("mode"),
                    DropRates.MoneyPenaltyMode.FLAT
                );
                double amount = moneySection.getDouble("amount", 0.0D);
                return new MoneyPenaltyConfig(amount, mode);
            }
        } else if (section.isSet("money")) {
            double amount = section.getDouble("money");
            return new MoneyPenaltyConfig(amount, DropRates.MoneyPenaltyMode.FLAT);
        }

        return null;
    }

    private MaxHealthPenaltyConfig tryResolveMaxHealthPenalty(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        if (section.isConfigurationSection("max-health")) {
            ConfigurationSection maxSection = section.getConfigurationSection("max-health");
            if (maxSection != null) {
                double amount = maxSection.getDouble("amount", 0.0D);
                String modeValue = maxSection.getString("mode", "remove");
                DropRates.MaxHealthPenaltyMode mode = DropRates.MaxHealthPenaltyMode.fromString(modeValue, DropRates.MaxHealthPenaltyMode.REMOVE);
                return new MaxHealthPenaltyConfig(amount, mode);
            }
        } else if (section.isSet("max-health")) {
            double amount = section.getDouble("max-health", 0.0D);
            return new MaxHealthPenaltyConfig(amount, DropRates.MaxHealthPenaltyMode.REMOVE);
        }

        return null;
    }

    private ItemPenaltyConfig getDefaultItemPenalty() {
        ItemPenaltyConfig fallback = new ItemPenaltyConfig(0, false, false);
        ConfigurationSection itemsSection = plugin.getConfig().getConfigurationSection("default-penalty.items");

        if (itemsSection != null) {
            int dropPercent = itemsSection.getInt("drop-percent", fallback.dropPercent);
            boolean hotbarDrop = itemsSection.getBoolean("hotbar", fallback.hotbarDrop);
            boolean armorDrop = itemsSection.getBoolean("armor", fallback.armorDrop);
            return new ItemPenaltyConfig(dropPercent, hotbarDrop, armorDrop);
        }

        int dropPercent = plugin.getConfig().getInt("default-penalty.item-drop", fallback.dropPercent);
        boolean hotbarDrop = plugin.getConfig().getBoolean("default-penalty.hotbar-drop", fallback.hotbarDrop);
        boolean armorDrop = plugin.getConfig().getBoolean("default-penalty.armor-drop", fallback.armorDrop);

        return new ItemPenaltyConfig(dropPercent, hotbarDrop, armorDrop);
    }

    private ItemPenaltyConfig resolveItemPenalty(ConfigurationSection section, ItemPenaltyConfig fallback) {
        ItemPenaltyConfig safeFallback = fallback != null ? fallback : new ItemPenaltyConfig(100, true, true);

        if (section == null) {
            return safeFallback;
        }

        if (section.isConfigurationSection("items")) {
            ConfigurationSection itemsSection = section.getConfigurationSection("items");
            if (itemsSection != null) {
                int dropPercent = itemsSection.getInt("drop-percent", safeFallback.dropPercent);
                boolean hotbarDrop = itemsSection.getBoolean("hotbar", safeFallback.hotbarDrop);
                boolean armorDrop = itemsSection.getBoolean("armor", safeFallback.armorDrop);
                return new ItemPenaltyConfig(dropPercent, hotbarDrop, armorDrop);
            }
        }

        if (section.isSet("item-drop") || section.isSet("hotbar-drop") || section.isSet("armor-drop")) {
            int dropPercent = section.getInt("item-drop", safeFallback.dropPercent);
            boolean hotbarDrop = section.getBoolean("hotbar-drop", safeFallback.hotbarDrop);
            boolean armorDrop = section.getBoolean("armor-drop", safeFallback.armorDrop);
            return new ItemPenaltyConfig(dropPercent, hotbarDrop, armorDrop);
        }

        return safeFallback;
    }

    private MaxHealthPenaltyConfig getDefaultMaxHealthPenalty() {
        ConfigurationSection maxSection = plugin.getConfig().getConfigurationSection("default-penalty.max-health");
        if (maxSection != null) {
            String modeValue = maxSection.getString("mode", "remove");
            double amount = maxSection.getDouble("amount", 0.0D);
            DropRates.MaxHealthPenaltyMode mode = DropRates.MaxHealthPenaltyMode.fromString(modeValue, DropRates.MaxHealthPenaltyMode.REMOVE);
            return new MaxHealthPenaltyConfig(amount, mode);
        }

        if (plugin.getConfig().isSet("default-penalty.max-health")) {
            double amount = plugin.getConfig().getDouble("default-penalty.max-health", 0.0D);
            return new MaxHealthPenaltyConfig(amount, DropRates.MaxHealthPenaltyMode.REMOVE);
        }

        return new MaxHealthPenaltyConfig(0.0D, DropRates.MaxHealthPenaltyMode.REMOVE);
    }

    private MaxHealthPenaltyConfig resolveMaxHealthPenalty(ConfigurationSection section, MaxHealthPenaltyConfig fallback) {
        if (section == null) {
            return fallback;
        }

        if (section.isConfigurationSection("max-health")) {
            ConfigurationSection maxSection = section.getConfigurationSection("max-health");
            if (maxSection != null) {
                double amount = maxSection.getDouble("amount", fallback != null ? fallback.amount : 0.0D);
                String modeValue = maxSection.getString("mode", fallback != null ? fallback.mode.name().toLowerCase(Locale.ROOT) : "remove");
                DropRates.MaxHealthPenaltyMode mode = DropRates.MaxHealthPenaltyMode.fromString(modeValue, fallback != null ? fallback.mode : DropRates.MaxHealthPenaltyMode.REMOVE);
                return new MaxHealthPenaltyConfig(amount, mode);
            }
        } else if (section.isSet("max-health")) {
            double amount = section.getDouble("max-health", fallback != null ? fallback.amount : 0.0D);
            DropRates.MaxHealthPenaltyMode mode = fallback != null ? fallback.mode : DropRates.MaxHealthPenaltyMode.REMOVE;
            return new MaxHealthPenaltyConfig(amount, mode);
        }

        return fallback;
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

    public void refreshPlayerMaxHealth(Player player) {
        if (player == null) {
            return;
        }

        if (!plugin.isSoulPointsEnabled()) {
            clearMaxHealthPenalty(player);
            return;
        }

        DropRates dropRates = getDropRates(getSoulPoints(player.getUniqueId()));
        applyMaxHealthPenalty(player, dropRates);
    }

    public void refreshPlayerMaxHealth(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            refreshPlayerMaxHealth(player);
        }
    }

    public void clearMaxHealthPenalty(Player player) {
        if (player == null) {
            return;
        }

        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        AttributeModifier existing = getMaxHealthModifier(attribute);
        if (existing != null) {
            attribute.removeModifier(existing);
        }
    }

    public MaxHealthPenaltyResult applyMaxHealthPenalty(Player player, DropRates dropRates) {
        double appliedHearts = 0.0D;
        double previousHearts = 0.0D;

        if (player == null) {
            return new MaxHealthPenaltyResult(appliedHearts, 0.0D);
        }

        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) {
            return new MaxHealthPenaltyResult(appliedHearts, 0.0D);
        }

        AttributeModifier existing = getMaxHealthModifier(attribute);
        if (existing != null) {
            if (existing.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                previousHearts = -existing.getAmount() / 2.0D;
            }
            attribute.removeModifier(existing);
        }

        if (plugin.isSoulPointsEnabled() && dropRates != null) {
            DropRates.MaxHealthPenaltyMode mode = dropRates.maxHealthMode != null
                ? dropRates.maxHealthMode
                : DropRates.MaxHealthPenaltyMode.REMOVE;
            double requestedHearts = Math.max(0.0D, dropRates.maxHealthPenalty);

            if (requestedHearts > 0.0D) {
                if (mode == DropRates.MaxHealthPenaltyMode.REMOVE) {
                    double baseValue = attribute.getBaseValue();
                    double minAllowed = Math.max(1.0D, MIN_MAX_HEALTH_POINTS);
                    double maxPoints = Math.max(0.0D, baseValue - minAllowed);
                    double requestedPoints = requestedHearts * 2.0D;
                    double appliedPoints = Math.min(maxPoints, requestedPoints);

                    if (appliedPoints > 0.0D) {
                        AttributeModifier modifier = new AttributeModifier(
                            MAX_HEALTH_MODIFIER_ID,
                            MAX_HEALTH_MODIFIER_NAME,
                            -appliedPoints,
                            AttributeModifier.Operation.ADD_NUMBER
                        );
                        attribute.addModifier(modifier);
                        appliedHearts = appliedPoints / 2.0D;
                    }

                    double currentMax = attribute.getValue();
                    if (player.getHealth() > currentMax) {
                        player.setHealth(currentMax);
                    }
                } else {
                    double addedPoints = requestedHearts * 2.0D;
                    AttributeModifier modifier = new AttributeModifier(
                        MAX_HEALTH_MODIFIER_ID,
                        MAX_HEALTH_MODIFIER_NAME,
                        addedPoints,
                        AttributeModifier.Operation.ADD_NUMBER
                    );
                    attribute.addModifier(modifier);
                    appliedHearts = -(addedPoints / 2.0D);
                }
            }
        }

        double deltaHearts = appliedHearts - previousHearts;
        return new MaxHealthPenaltyResult(appliedHearts, deltaHearts);
    }

    private AttributeModifier getMaxHealthModifier(AttributeInstance attribute) {
        for (AttributeModifier modifier : attribute.getModifiers()) {
            if (modifier.getUniqueId().equals(MAX_HEALTH_MODIFIER_ID)) {
                return modifier;
            }
        }
        return null;
    }

    public double getMinimumMaxHealthPoints() {
        return MIN_MAX_HEALTH_POINTS;
    }

    private void executeCommands(List<String> commands, Player player) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String command : commands) {
            if (command == null || command.trim().isEmpty()) {
                continue;
            }
            String parsed = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    public static class MaxHealthPenaltyResult {
        public final double totalHearts;
        public final double deltaHearts;

        public MaxHealthPenaltyResult(double totalHearts, double deltaHearts) {
            this.totalHearts = totalHearts;
            this.deltaHearts = deltaHearts;
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
    
    private static class ItemPenaltyConfig {
        final int dropPercent;
        final boolean hotbarDrop;
        final boolean armorDrop;

        ItemPenaltyConfig(int dropPercent, boolean hotbarDrop, boolean armorDrop) {
            this.dropPercent = dropPercent;
            this.hotbarDrop = hotbarDrop;
            this.armorDrop = armorDrop;
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

    private static class MaxHealthPenaltyConfig {
        final double amount;
        final DropRates.MaxHealthPenaltyMode mode;

        MaxHealthPenaltyConfig(double amount, DropRates.MaxHealthPenaltyMode mode) {
            this.amount = Math.abs(amount);
            this.mode = mode != null ? mode : DropRates.MaxHealthPenaltyMode.REMOVE;
        }
    }

    public static class DropRates {
        public final int itemDrop;
        public final boolean hotbarDrop;
        public final boolean armorDrop;
        public final double moneyPenalty;
        public final MoneyPenaltyMode moneyMode;
        public final double maxHealthPenalty;
        public final MaxHealthPenaltyMode maxHealthMode;
        public final List<String> commands;

        public DropRates(int itemDrop, boolean hotbarDrop, boolean armorDrop, double moneyPenalty, MoneyPenaltyMode moneyMode,
                         double maxHealthPenalty, MaxHealthPenaltyMode maxHealthMode, List<String> commands) {
            this.itemDrop = itemDrop;
            this.hotbarDrop = hotbarDrop;
            this.armorDrop = armorDrop;
            this.moneyPenalty = moneyPenalty;
            this.moneyMode = moneyMode;
            this.maxHealthPenalty = maxHealthPenalty;
            this.maxHealthMode = maxHealthMode != null ? maxHealthMode : MaxHealthPenaltyMode.REMOVE;
            this.commands = commands != null ? commands : Collections.emptyList();
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

        public enum MaxHealthPenaltyMode {
            REMOVE,
            ADD;

            public static MaxHealthPenaltyMode fromString(String value, MaxHealthPenaltyMode fallback) {
                if (value == null) {
                    return fallback;
                }
                switch (value.toLowerCase(Locale.ROOT)) {
                    case "remove":
                    case "loss":
                    case "subtract":
                        return REMOVE;
                    case "add":
                    case "bonus":
                    case "increase":
                        return ADD;
                    default:
                        return fallback;
                }
            }
        }

    }
}

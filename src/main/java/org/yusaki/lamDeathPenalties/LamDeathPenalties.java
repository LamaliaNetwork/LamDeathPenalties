package org.yusaki.lamDeathPenalties;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.yusaki.lamDeathPenalties.api.LamDeathPenaltiesAPI;
import org.yusaki.lamDeathPenalties.api.LamDeathPenaltiesAPIImpl;
import org.yusaki.lib.YskLib;

public final class LamDeathPenalties extends JavaPlugin implements Listener {

    private YskLib yskLib;
    private FoliaLib foliaLib;
    private SoulPointsManager soulPointsManager;
    private RecoveryScheduler recoveryScheduler;
    private DeathListener deathListener;
    private SoulPointsCommand soulPointsCommand;
    private boolean soulPointsEnabled = true;
    @Override
    public void onEnable() {
        // Get YskLib instance
        yskLib = (YskLib) getServer().getPluginManager().getPlugin("YskLib");
        if (yskLib == null) {
            getLogger().severe("YskLib not found! This plugin requires YskLib to function.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        yskLib.logDebug(this, "YskLib instance obtained successfully");

        // Initialize FoliaLib
        foliaLib = new FoliaLib(this);

        // Save default config and update with YskLib
        saveDefaultConfig();
        yskLib.updateConfig(this);

        // Load messages via YskLib
        yskLib.loadMessages(this);

        loadSettings();

        // Initialize managers
        soulPointsManager = new SoulPointsManager(this);
        recoveryScheduler = new RecoveryScheduler(this, soulPointsManager, foliaLib);
        deathListener = new DeathListener(this, soulPointsManager, foliaLib);
        soulPointsCommand = new SoulPointsCommand(this, soulPointsManager, recoveryScheduler);
        
        // Register events
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register commands
        getCommand("lmdp").setExecutor(soulPointsCommand);
        getCommand("lmdp").setTabCompleter(soulPointsCommand);
        
        // Register PlaceholderAPI expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SoulPointsPlaceholder(this, soulPointsManager, recoveryScheduler).register();
            yskLib.logInfo(this, "PlaceholderAPI integration enabled!");
        }
        
        
        
        // Register public API
        LamDeathPenaltiesAPIImpl api = new LamDeathPenaltiesAPIImpl(this);
        getServer().getServicesManager().register(LamDeathPenaltiesAPI.class, api, this, ServicePriority.Normal);
        yskLib.logInfo(this, "LamDeathPenalties API registered!");
        yskLib.logDebug(this, "API service priority: Normal");

        yskLib.logInfo(this, "LamDeathPenalties enabled with FoliaLib support!");
    }

    @Override
    public void onDisable() {
        // Save player data before shutdown
        if (soulPointsManager != null) {
            soulPointsManager.savePlayerData();
        }

        if (yskLib != null) {
            yskLib.logInfo(this, "LamDeathPenalties disabled!");
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isSoulPointsEnabled()) {
            return;
        }
        if (recoveryScheduler != null) {
            recoveryScheduler.onPlayerJoin(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isSoulPointsEnabled()) {
            return;
        }
        if (recoveryScheduler != null) {
            recoveryScheduler.onPlayerQuit(event.getPlayer().getUniqueId());
        }
    }
    
    // Getters for other classes
    public FoliaLib getFoliaLib() {
        return foliaLib;
    }
    
    public SoulPointsManager getSoulPointsManager() {
        return soulPointsManager;
    }
    
    public RecoveryScheduler getRecoveryScheduler() {
        return recoveryScheduler;
    }

    public org.yusaki.lib.modules.MessageManager getMessageManager() {
        return yskLib != null ? yskLib.getMessageManager() : null;
    }
    

    public void reloadPlugin() {
        yskLib.logDebug(this, "Starting plugin reload...");

        // Save current data before reloading
        if (soulPointsManager != null) {
            soulPointsManager.savePlayerData();
            yskLib.logDebug(this, "Player data saved before reload");
        }

        // Reload configuration using YskLib updater
        if (yskLib != null) {
            yskLib.updateConfig(this);
            yskLib.loadMessages(this);
            yskLib.logDebug(this, "Config and messages reloaded");
        } else {
            reloadConfig();
        }

        loadSettings();

        yskLib.logInfo(this, "Plugin configuration reloaded!");
    }

    public YskLib getYskLib() {
        return yskLib;
    }

    public boolean isSoulPointsEnabled() {
        return soulPointsEnabled;
    }

    private void loadSettings() {
        soulPointsEnabled = getConfig().getBoolean("soul-points.enabled", true);
        if (yskLib != null) {
            yskLib.logDebug(this, "Soul points system enabled: " + soulPointsEnabled);
        } else {
            getLogger().info("Soul points system enabled: " + soulPointsEnabled);
        }
    }

    public String getRecoveryMode() {
        String mode = getConfig().getString("soul-points.recovery.mode");
        if (mode == null) {
            mode = getConfig().getString("recovery.mode", "real-time");
        }
        return mode;
    }

    public long getRecoveryIntervalSeconds() {
        if (getConfig().isSet("soul-points.recovery.interval-seconds")) {
            long seconds = getConfig().getLong("soul-points.recovery.interval-seconds", 3600L);
            return Math.max(1L, seconds);
        }
        long hours = getConfig().getLong("recovery.interval-hours", 1L);
        return Math.max(1L, hours * 3600L);
    }
}

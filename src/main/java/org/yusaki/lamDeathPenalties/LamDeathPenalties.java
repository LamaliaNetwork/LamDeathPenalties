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
    @Override
    public void onEnable() {
        // Get YskLib instance
        yskLib = (YskLib) getServer().getPluginManager().getPlugin("YskLib");
        if (yskLib == null) {
            getLogger().severe("YskLib not found! This plugin requires YskLib to function.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize FoliaLib
        foliaLib = new FoliaLib(this);

        // Save default config and update with YskLib
        saveDefaultConfig();
        yskLib.updateConfig(this);

        // Load messages via YskLib
        yskLib.loadMessages(this);

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
            getLogger().info("PlaceholderAPI integration enabled!");
        }
        
        
        
        // Register public API
        LamDeathPenaltiesAPIImpl api = new LamDeathPenaltiesAPIImpl(this);
        getServer().getServicesManager().register(LamDeathPenaltiesAPI.class, api, this, ServicePriority.Normal);
        getLogger().info("LamDeathPenalties API registered!");
        
        getLogger().info("LamDeathPenalties enabled with FoliaLib support!");
    }

    @Override
    public void onDisable() {
        // Save player data before shutdown
        if (soulPointsManager != null) {
            soulPointsManager.savePlayerData();
        }
        
        getLogger().info("LamDeathPenalties disabled!");
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (recoveryScheduler != null) {
            recoveryScheduler.onPlayerJoin(event.getPlayer().getUniqueId());
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
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
        // Save current data before reloading
        if (soulPointsManager != null) {
            soulPointsManager.savePlayerData();
        }

        // Reload configuration using YskLib updater
        if (yskLib != null) {
            yskLib.updateConfig(this);
            yskLib.loadMessages(this);
        } else {
            reloadConfig();
        }

        getLogger().info("Plugin configuration reloaded!");
    }

    public YskLib getYskLib() {
        return yskLib;
    }
}

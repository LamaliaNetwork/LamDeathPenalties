package org.yusaki.lamDeathPenalties;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class LamDeathPenalties extends JavaPlugin implements Listener {
    
    private FoliaLib foliaLib;
    private MessageManager messageManager;
    private SoulPointsManager soulPointsManager;
    private RecoveryScheduler recoveryScheduler;
    private DeathListener deathListener;
    private SoulPointsCommand soulPointsCommand;

    @Override
    public void onEnable() {
        // Initialize FoliaLib
        foliaLib = new FoliaLib(this);
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize managers
        messageManager = new MessageManager(this);
        soulPointsManager = new SoulPointsManager(this);
        recoveryScheduler = new RecoveryScheduler(this, soulPointsManager, foliaLib);
        deathListener = new DeathListener(this, soulPointsManager, foliaLib);
        soulPointsCommand = new SoulPointsCommand(this, soulPointsManager, recoveryScheduler);
        
        // Register events
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(this, this);
        
        // Register commands
        getCommand("soulpoints").setExecutor(soulPointsCommand);
        getCommand("soulpoints").setTabCompleter(soulPointsCommand);
        
        // Register PlaceholderAPI expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SoulPointsPlaceholder(this, soulPointsManager, recoveryScheduler).register();
            getLogger().info("PlaceholderAPI integration enabled!");
        }
        
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
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public void reloadPlugin() {
        // Reload configuration
        reloadConfig();
        
        // Save current data before reloading
        if (soulPointsManager != null) {
            soulPointsManager.savePlayerData();
        }
        
        // Reload messages
        if (messageManager != null) {
            messageManager.loadMessages();
        }
        
        getLogger().info("Plugin configuration reloaded!");
    }
}

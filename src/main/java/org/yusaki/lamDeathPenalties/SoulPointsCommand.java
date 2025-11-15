package org.yusaki.lamDeathPenalties;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.yusaki.lamDeathPenalties.api.events.SoulPointsChangeEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.yusaki.lib.modules.MessageManager.placeholders;

public class SoulPointsCommand implements CommandExecutor, TabCompleter {
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    private final RecoveryScheduler recoveryScheduler;
    
    public SoulPointsCommand(LamDeathPenalties plugin, SoulPointsManager soulPointsManager, RecoveryScheduler recoveryScheduler) {
        this.plugin = plugin;
        this.soulPointsManager = soulPointsManager;
        this.recoveryScheduler = recoveryScheduler;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (args.length == 0) {
            if (!plugin.isSoulPointsEnabled()) {
                messageManager.sendMessage(plugin, sender, "soul-points-disabled");
                return true;
            }
            // Show current soul points
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(plugin, sender, "player-only");
                return true;
            }

            Player player = (Player) sender;
            showSoulPoints(player, player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (!plugin.isSoulPointsEnabled() && !subCommand.equals("reload")) {
            messageManager.sendMessage(plugin, sender, "soul-points-disabled");
            return true;
        }

        switch (subCommand) {
            case "set":
                return handleSetCommand(sender, args);
            case "give":
            case "add":
                return handleGiveCommand(sender, args);
            case "take":
            case "remove":
                return handleTakeCommand(sender, args);
            case "setmax":
                return handleSetMaxCommand(sender, args);
            case "givemax":
            case "addmax":
                return handleGiveMaxCommand(sender, args);
            case "takemax":
            case "removemax":
                return handleTakeMaxCommand(sender, args);
            case "check":
            case "view":
                return handleCheckCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            default:
                sendHelpMessage(sender);
                return true;
        }
    }
    
    private boolean handleSetCommand(CommandSender sender, String[] args) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (!sender.hasPermission("lmdp.admin")) {
            messageManager.sendMessage(plugin, sender, "no-permission");
            return true;
        }

        if (args.length < 3) {
            messageManager.sendMessage(plugin, sender, "set-usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(plugin, sender, "player-not-found", placeholders("player", args[1]));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);

            if (amount < 0 || amount > maxPoints) {
                messageManager.sendMessage(plugin, sender, "set-amount-range", placeholders("max_points", String.valueOf(maxPoints)));
                return true;
            }

            soulPointsManager.setSoulPointsWithReason(target.getUniqueId(), amount, SoulPointsChangeEvent.ChangeReason.COMMAND);

            plugin.getYskLib().logDebug(plugin, sender.getName() + " set soul points for " + target.getName() + " to " + amount);

            messageManager.sendMessage(plugin, sender, "set-success-sender", placeholders(
                "player", target.getName(),
                "amount", String.valueOf(amount),
                "max_points", String.valueOf(maxPoints)
            ));
            messageManager.sendMessage(plugin, target, "set-success-target", placeholders(
                "amount", String.valueOf(amount),
                "max_points", String.valueOf(maxPoints)
            ));

        } catch (NumberFormatException e) {
            messageManager.sendMessage(plugin, sender, "invalid-number", placeholders("input", args[2]));
        }

        return true;
    }
    
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (!sender.hasPermission("lmdp.admin")) {
            messageManager.sendMessage(plugin, sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messageManager.sendMessage(plugin, sender, "give-usage");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(plugin, sender, "player-not-found", placeholders("player", args[1]));
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            int currentPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            
            if (amount <= 0) {
                messageManager.sendMessage(plugin, sender, "give-amount-positive");
                return true;
            }
            
            soulPointsManager.addSoulPoints(target.getUniqueId(), amount);
            int newPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int actualGiven = newPoints - currentPoints;

            plugin.getYskLib().logDebug(plugin, sender.getName() + " gave " + actualGiven + " soul points to " + target.getName());

            messageManager.sendMessage(plugin, sender, "give-success-sender", placeholders(
                "given", String.valueOf(actualGiven),
                "player", target.getName(),
                "new_points", String.valueOf(newPoints),
                "max_points", String.valueOf(maxPoints)
            ));
            messageManager.sendMessage(plugin, target, "give-success-target", placeholders(
                "given", String.valueOf(actualGiven),
                "plural", actualGiven != 1 ? "s" : "",
                "new_points", String.valueOf(newPoints),
                "max_points", String.valueOf(maxPoints)
            ));
            
        } catch (NumberFormatException e) {
            messageManager.sendMessage(plugin, sender, "invalid-number", placeholders("input", args[2]));
        }
        
        return true;
    }
    
    private boolean handleTakeCommand(CommandSender sender, String[] args) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (!sender.hasPermission("lmdp.admin")) {
            messageManager.sendMessage(plugin, sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messageManager.sendMessage(plugin, sender, "take-usage");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(plugin, sender, "player-not-found", placeholders("player", args[1]));
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            int currentPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            
            if (amount <= 0) {
                messageManager.sendMessage(plugin, sender, "take-amount-positive");
                return true;
            }
            
            soulPointsManager.removeSoulPoints(target.getUniqueId(), amount);
            int newPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int actualTaken = currentPoints - newPoints;

            plugin.getYskLib().logDebug(plugin, sender.getName() + " took " + actualTaken + " soul points from " + target.getName());

            messageManager.sendMessage(plugin, sender, "take-success-sender", placeholders(
                "taken", String.valueOf(actualTaken),
                "player", target.getName(),
                "new_points", String.valueOf(newPoints),
                "max_points", String.valueOf(maxPoints)
            ));
            messageManager.sendMessage(plugin, target, "take-success-target", placeholders(
                "taken", String.valueOf(actualTaken),
                "plural", actualTaken != 1 ? "s" : "",
                "new_points", String.valueOf(newPoints),
                "max_points", String.valueOf(maxPoints)
            ));
            
        } catch (NumberFormatException e) {
            messageManager.sendMessage(plugin, sender, "invalid-number", placeholders("input", args[2]));
        }
        
        return true;
    }

    private boolean handleSetMaxCommand(CommandSender sender, String[] args) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (!sender.hasPermission("lmdp.admin")) {
            messageManager.sendMessage(plugin, sender, "no-permission");
            return true;
        }

        if (args.length < 3) {
            messageManager.sendMessage(plugin, sender, "setmax-usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(plugin, sender, "player-not-found", placeholders("player", args[1]));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            int configMax = plugin.getConfig().getInt("soul-points.max", 10);

            if (amount < 0) {
                messageManager.sendMessage(plugin, sender, "setmax-amount-positive");
                return true;
            }

            int oldMax = soulPointsManager.getMaxSoulPoints(target.getUniqueId());
            soulPointsManager.setMaxSoulPoints(target.getUniqueId(), amount);
            int actualMax = soulPointsManager.getMaxSoulPoints(target.getUniqueId());
            
            // Reset recovery timers if max changed
            if (oldMax != actualMax) {
                soulPointsManager.resetRecoveryTimers(target.getUniqueId());
            }

            plugin.getYskLib().logDebug(plugin, sender.getName() + " set max soul points for " + target.getName() + " to " + actualMax);

            messageManager.sendMessage(plugin, sender, "setmax-success-sender", placeholders(
                "player", target.getName(),
                "amount", String.valueOf(actualMax),
                "config_max", String.valueOf(configMax)
            ));
            messageManager.sendMessage(plugin, target, "setmax-success-target", placeholders(
                "amount", String.valueOf(actualMax),
                "config_max", String.valueOf(configMax)
            ));

        } catch (NumberFormatException e) {
            messageManager.sendMessage(plugin, sender, "invalid-number", placeholders("input", args[2]));
        }

        return true;
    }

    private boolean handleGiveMaxCommand(CommandSender sender, String[] args) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (!sender.hasPermission("lmdp.admin")) {
            messageManager.sendMessage(plugin, sender, "no-permission");
            return true;
        }

        if (args.length < 3) {
            messageManager.sendMessage(plugin, sender, "givemax-usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(plugin, sender, "player-not-found", placeholders("player", args[1]));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            int currentMax = soulPointsManager.getMaxSoulPoints(target.getUniqueId());
            int configMax = plugin.getConfig().getInt("soul-points.max", 10);

            if (amount <= 0) {
                messageManager.sendMessage(plugin, sender, "givemax-amount-positive");
                return true;
            }

            soulPointsManager.addMaxSoulPoints(target.getUniqueId(), amount);
            int newMax = soulPointsManager.getMaxSoulPoints(target.getUniqueId());
            int actualGiven = newMax - currentMax;
            
            // Reset recovery timers if max changed
            if (actualGiven > 0) {
                soulPointsManager.resetRecoveryTimers(target.getUniqueId());
            }

            plugin.getYskLib().logDebug(plugin, sender.getName() + " gave " + actualGiven + " max soul points to " + target.getName());

            messageManager.sendMessage(plugin, sender, "givemax-success-sender", placeholders(
                "given", String.valueOf(actualGiven),
                "player", target.getName(),
                "new_max", String.valueOf(newMax),
                "config_max", String.valueOf(configMax)
            ));
            messageManager.sendMessage(plugin, target, "givemax-success-target", placeholders(
                "given", String.valueOf(actualGiven),
                "plural", actualGiven != 1 ? "s" : "",
                "new_max", String.valueOf(newMax),
                "config_max", String.valueOf(configMax)
            ));

        } catch (NumberFormatException e) {
            messageManager.sendMessage(plugin, sender, "invalid-number", placeholders("input", args[2]));
        }

        return true;
    }

    private boolean handleTakeMaxCommand(CommandSender sender, String[] args) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (!sender.hasPermission("lmdp.admin")) {
            messageManager.sendMessage(plugin, sender, "no-permission");
            return true;
        }

        if (args.length < 3) {
            messageManager.sendMessage(plugin, sender, "takemax-usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(plugin, sender, "player-not-found", placeholders("player", args[1]));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            int currentMax = soulPointsManager.getMaxSoulPoints(target.getUniqueId());
            int configMax = plugin.getConfig().getInt("soul-points.max", 10);

            if (amount <= 0) {
                messageManager.sendMessage(plugin, sender, "takemax-amount-positive");
                return true;
            }

            soulPointsManager.reduceMaxSoulPoints(target.getUniqueId(), amount);
            int newMax = soulPointsManager.getMaxSoulPoints(target.getUniqueId());
            int actualTaken = currentMax - newMax;
            
            // Reset recovery timers if max changed
            if (actualTaken > 0) {
                soulPointsManager.resetRecoveryTimers(target.getUniqueId());
            }

            plugin.getYskLib().logDebug(plugin, sender.getName() + " took " + actualTaken + " max soul points from " + target.getName());

            messageManager.sendMessage(plugin, sender, "takemax-success-sender", placeholders(
                "taken", String.valueOf(actualTaken),
                "player", target.getName(),
                "new_max", String.valueOf(newMax),
                "config_max", String.valueOf(configMax)
            ));
            messageManager.sendMessage(plugin, target, "takemax-success-target", placeholders(
                "taken", String.valueOf(actualTaken),
                "plural", actualTaken != 1 ? "s" : "",
                "new_max", String.valueOf(newMax),
                "config_max", String.valueOf(configMax)
            ));

        } catch (NumberFormatException e) {
            messageManager.sendMessage(plugin, sender, "invalid-number", placeholders("input", args[2]));
        }

        return true;
    }
    
    private boolean handleCheckCommand(CommandSender sender, String[] args) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(plugin, sender, "check-usage");
                return true;
            }
            Player player = (Player) sender;
            showSoulPoints(sender, player);
            return true;
        }
        
        if (!sender.hasPermission("lmdp.check.others")) {
            messageManager.sendMessage(plugin, sender, "no-permission-check-others");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(plugin, sender, "player-not-found", placeholders("player", args[1]));
            return true;
        }
        
        showSoulPoints(sender, target);
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();

        if (!sender.hasPermission("lmdp.admin")) {
            messageManager.sendMessage(plugin, sender, "no-permission");
            return true;
        }

        plugin.reloadPlugin();
        messageManager.sendMessage(plugin, sender, "config-reloaded");
        return true;
    }
    
    private void showSoulPoints(CommandSender sender, Player target) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();
        int currentPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
        int maxPoints = soulPointsManager.getMaxSoulPoints(target.getUniqueId());
        int configMax = plugin.getConfig().getInt("soul-points.max", 10);

        // Get drop rates for current soul points
        SoulPointsManager.DropRates dropRates = soulPointsManager.getDropRates(currentPoints);

        // Get time until next recovery
        long timeUntilRecovery = recoveryScheduler.getTimeUntilNextRecovery(target.getUniqueId());
        String recoveryTime = formatTime(timeUntilRecovery);
        
        // Get time until next max soul points recovery
        long timeUntilMaxRecovery = getTimeUntilNextMaxRecovery(target.getUniqueId());
        String maxRecoveryTime = formatTime(timeUntilMaxRecovery);

        // Create progress bar
        String progressBar = createProgressBar(currentPoints, maxPoints);
        
        messageManager.sendMessageList(plugin, sender, "soul-points-display", placeholders(
            "player", target.getName(),
            "progress_bar", progressBar,
            "current_points", String.valueOf(currentPoints),
            "max_points", String.valueOf(maxPoints),
            "config_max", String.valueOf(configMax),
            "item_drop", String.valueOf(dropRates != null ? dropRates.itemDrop : 0),
            "hotbar_drop", formatBooleanDrop(dropRates != null && dropRates.hotbarDrop, messageManager),
            "armor_drop", formatBooleanDrop(dropRates != null && dropRates.armorDrop, messageManager),
            "money_drop", formatMoneyDrop(dropRates),
            "max_health_drop", formatMaxHealthDrop(dropRates),
            "recovery_time", recoveryTime,
            "max_recovery_time", maxRecoveryTime
        ));
    }
    
    private long getTimeUntilNextMaxRecovery(UUID playerId) {
        if (!plugin.isSoulPointsEnabled()) {
            return 0;
        }
        if (!plugin.getConfig().getBoolean("soul-points.max-soul-points.regeneration.enabled", true)) {
            return 0;
        }
        
        int currentMax = soulPointsManager.getMaxSoulPoints(playerId);
        int configMax = plugin.getConfig().getInt("soul-points.max", 10);
        
        // If already at config max, no recovery needed
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
        
        // Real-time recovery
        long timeSinceLastRecovery = System.currentTimeMillis() - data.lastMaxRecoveryTime;
        return Math.max(0, intervalMs - timeSinceLastRecovery);
    }
    
    private String createProgressBar(int current, int max) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();
        int barLength = 20;
        int filledBars = (int) ((double) current / max * barLength);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append(messageManager.getMessage(plugin, "progress-bar-filled"));
            } else {
                bar.append(messageManager.getMessage(plugin, "progress-bar-empty"));
            }
        }
        return bar.toString();
    }
    
    private String formatTime(long milliseconds) {
        if (milliseconds <= 0) {
            return "Ready!";
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

    private String formatBooleanDrop(boolean value, org.yusaki.lib.modules.MessageManager messageManager) {
        return value ? messageManager.getMessage(plugin, "display-yes") : messageManager.getMessage(plugin, "display-no");
    }

    private String formatMoneyDrop(SoulPointsManager.DropRates dropRates) {
        if (dropRates == null) {
            return "0";
        }
        if (dropRates.moneyMode == SoulPointsManager.DropRates.MoneyPenaltyMode.PERCENT) {
            return String.format("%.2f%%", dropRates.moneyPenalty);
        }
        return String.format("%.2f", dropRates.moneyPenalty);
    }

    private String formatMaxHealthDrop(SoulPointsManager.DropRates dropRates) {
        if (dropRates == null || dropRates.maxHealthPenalty <= 0.0D) {
            return "0 hearts";
        }
        boolean removal = dropRates.maxHealthMode == null || dropRates.maxHealthMode == SoulPointsManager.DropRates.MaxHealthPenaltyMode.REMOVE;
        String sign = removal ? "-" : "+";
        return String.format("%s%.1f hearts", sign, dropRates.maxHealthPenalty);
    }
    
    private void sendHelpMessage(CommandSender sender) {
        org.yusaki.lib.modules.MessageManager messageManager = plugin.getMessageManager();
        if (sender.hasPermission("lmdp.admin")) {
            messageManager.sendMessageList(plugin, sender, "help-menu");
        } else {
            // Show basic help without admin commands
            List<String> helpLines = messageManager.getMessageList(plugin, "help-menu");
            for (String line : helpLines) {
                // Skip admin-only lines
                if (line.contains("set") || line.contains("give") || line.contains("take") || line.contains("reload")) {
                    continue;
                }
                sender.sendMessage(line);
            }
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!plugin.isSoulPointsEnabled()) {
            if (args.length == 1 && sender.hasPermission("lmdp.admin")) {
                if ("reload".startsWith(args[0].toLowerCase())) {
                    completions.add("reload");
                }
            }
            return completions;
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("check");
            if (sender.hasPermission("lmdp.admin")) {
                subCommands = Arrays.asList("set", "give", "take", "setmax", "givemax", "takemax", "check", "reload");
            }
            
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("setmax") || args[0].equalsIgnoreCase("givemax") || args[0].equalsIgnoreCase("takemax") || args[0].equalsIgnoreCase("check"))) {
            // Player name completion
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            // Number suggestions for regular soul points
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            for (int i = 1; i <= maxPoints; i++) {
                completions.add(String.valueOf(i));
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("setmax") || args[0].equalsIgnoreCase("givemax") || args[0].equalsIgnoreCase("takemax"))) {
            // Number suggestions for max soul points
            int configMax = plugin.getConfig().getInt("soul-points.max", 10);
            for (int i = 1; i <= configMax; i++) {
                completions.add(String.valueOf(i));
            }
        }
        
        return completions;
    }
}

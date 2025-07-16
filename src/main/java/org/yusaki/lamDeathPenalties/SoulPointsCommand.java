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

public class SoulPointsCommand implements CommandExecutor, TabCompleter {
    private final LamDeathPenalties plugin;
    private final SoulPointsManager soulPointsManager;
    private final RecoveryScheduler recoveryScheduler;
    private final MessageManager messageManager;
    
    public SoulPointsCommand(LamDeathPenalties plugin, SoulPointsManager soulPointsManager, RecoveryScheduler recoveryScheduler) {
        this.plugin = plugin;
        this.soulPointsManager = soulPointsManager;
        this.recoveryScheduler = recoveryScheduler;
        this.messageManager = plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Show current soul points
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(sender, "player-only");
                return true;
            }
            
            Player player = (Player) sender;
            showSoulPoints(player, player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "set":
                return handleSetCommand(sender, args);
            case "give":
            case "add":
                return handleGiveCommand(sender, args);
            case "take":
            case "remove":
                return handleTakeCommand(sender, args);
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
        if (!sender.hasPermission("soulpoints.admin")) {
            messageManager.sendMessage(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messageManager.sendMessage(sender, "set-usage");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(sender, "player-not-found", MessageManager.placeholders("player", args[1]));
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            
            if (amount < 0 || amount > maxPoints) {
                messageManager.sendMessage(sender, "set-amount-range", MessageManager.placeholders("max_points", String.valueOf(maxPoints)));
                return true;
            }
            
            soulPointsManager.setSoulPointsWithReason(target.getUniqueId(), amount, SoulPointsChangeEvent.ChangeReason.COMMAND);
            messageManager.sendMessage(sender, "set-success-sender", MessageManager.placeholders(
                "player", target.getName(),
                "amount", String.valueOf(amount),
                "max_points", String.valueOf(maxPoints)
            ));
            messageManager.sendMessage(target, "set-success-target", MessageManager.placeholders(
                "amount", String.valueOf(amount),
                "max_points", String.valueOf(maxPoints)
            ));
            
        } catch (NumberFormatException e) {
            messageManager.sendMessage(sender, "invalid-number", MessageManager.placeholders("input", args[2]));
        }
        
        return true;
    }
    
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("soulpoints.admin")) {
            messageManager.sendMessage(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messageManager.sendMessage(sender, "give-usage");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(sender, "player-not-found", MessageManager.placeholders("player", args[1]));
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            int currentPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            
            if (amount <= 0) {
                messageManager.sendMessage(sender, "give-amount-positive");
                return true;
            }
            
            soulPointsManager.addSoulPoints(target.getUniqueId(), amount);
            int newPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int actualGiven = newPoints - currentPoints;
            
            messageManager.sendMessage(sender, "give-success-sender", MessageManager.placeholders(
                "given", String.valueOf(actualGiven),
                "player", target.getName(),
                "new_points", String.valueOf(newPoints),
                "max_points", String.valueOf(maxPoints)
            ));
            messageManager.sendMessage(target, "give-success-target", MessageManager.placeholders(
                "given", String.valueOf(actualGiven),
                "plural", actualGiven != 1 ? "s" : "",
                "new_points", String.valueOf(newPoints),
                "max_points", String.valueOf(maxPoints)
            ));
            
        } catch (NumberFormatException e) {
            messageManager.sendMessage(sender, "invalid-number", MessageManager.placeholders("input", args[2]));
        }
        
        return true;
    }
    
    private boolean handleTakeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("soulpoints.admin")) {
            messageManager.sendMessage(sender, "no-permission");
            return true;
        }
        
        if (args.length < 3) {
            messageManager.sendMessage(sender, "take-usage");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(sender, "player-not-found", MessageManager.placeholders("player", args[1]));
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            int currentPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            
            if (amount <= 0) {
                messageManager.sendMessage(sender, "take-amount-positive");
                return true;
            }
            
            soulPointsManager.removeSoulPoints(target.getUniqueId(), amount);
            int newPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int actualTaken = currentPoints - newPoints;
            
            messageManager.sendMessage(sender, "take-success-sender", MessageManager.placeholders(
                "taken", String.valueOf(actualTaken),
                "player", target.getName(),
                "new_points", String.valueOf(newPoints),
                "max_points", String.valueOf(maxPoints)
            ));
            messageManager.sendMessage(target, "take-success-target", MessageManager.placeholders(
                "taken", String.valueOf(actualTaken),
                "plural", actualTaken != 1 ? "s" : "",
                "new_points", String.valueOf(newPoints),
                "max_points", String.valueOf(maxPoints)
            ));
            
        } catch (NumberFormatException e) {
            messageManager.sendMessage(sender, "invalid-number", MessageManager.placeholders("input", args[2]));
        }
        
        return true;
    }
    
    private boolean handleCheckCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(sender, "check-usage");
                return true;
            }
            Player player = (Player) sender;
            showSoulPoints(sender, player);
            return true;
        }
        
        if (!sender.hasPermission("soulpoints.check.others")) {
            messageManager.sendMessage(sender, "no-permission-check-others");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageManager.sendMessage(sender, "player-not-found", MessageManager.placeholders("player", args[1]));
            return true;
        }
        
        showSoulPoints(sender, target);
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("soulpoints.admin")) {
            messageManager.sendMessage(sender, "no-permission");
            return true;
        }
        
        plugin.reloadPlugin();
        messageManager.sendMessage(sender, "config-reloaded");
        return true;
    }
    
    private void showSoulPoints(CommandSender sender, Player target) {
        int currentPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
        int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
        
        // Get drop rates for current soul points
        SoulPointsManager.DropRates dropRates = soulPointsManager.getDropRates(currentPoints);
        
        // Get time until next recovery
        long timeUntilRecovery = recoveryScheduler.getTimeUntilNextRecovery(target.getUniqueId());
        String recoveryTime = formatTime(timeUntilRecovery);
        
        // Create progress bar
        String progressBar = createProgressBar(currentPoints, maxPoints);
        
        messageManager.sendMessageList(sender, "soul-points-display", MessageManager.placeholders(
            "player", target.getName(),
            "progress_bar", progressBar,
            "current_points", String.valueOf(currentPoints),
            "max_points", String.valueOf(maxPoints),
            "item_drop", String.valueOf(dropRates.itemDrop),
            "hotbar_drop", dropRates.hotbarDrop ? messageManager.getMessage("yes") : messageManager.getMessage("no"),
            "armor_drop", dropRates.armorDrop ? messageManager.getMessage("yes") : messageManager.getMessage("no"),
            "recovery_time", recoveryTime
        ));
    }
    
    private String createProgressBar(int current, int max) {
        int barLength = 20;
        int filledBars = (int) ((double) current / max * barLength);
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append(messageManager.getMessage("progress-bar-filled"));
            } else {
                bar.append(messageManager.getMessage("progress-bar-empty"));
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
    
    private void sendHelpMessage(CommandSender sender) {
        if (sender.hasPermission("soulpoints.admin")) {
            messageManager.sendMessageList(sender, "help-menu");
        } else {
            // Show basic help without admin commands
            List<String> helpLines = messageManager.getMessageList("help-menu");
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
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("check");
            if (sender.hasPermission("soulpoints.admin")) {
                subCommands = Arrays.asList("set", "give", "take", "check", "reload");
            }
            
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("check"))) {
            // Player name completion
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            // Number suggestions
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            for (int i = 1; i <= maxPoints; i++) {
                completions.add(String.valueOf(i));
            }
        }
        
        return completions;
    }
}
package org.yusaki.lamDeathPenalties;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        if (args.length == 0) {
            // Show current soul points
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players.");
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
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /soulpoints set <player> <amount>");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[1]);
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            
            if (amount < 0 || amount > maxPoints) {
                sender.sendMessage("§cAmount must be between 0 and " + maxPoints);
                return true;
            }
            
            soulPointsManager.setSoulPoints(target.getUniqueId(), amount);
            sender.sendMessage("§aSet " + target.getName() + "'s soul points to " + amount + "/" + maxPoints);
            target.sendMessage("§a✦ Your soul points have been set to " + amount + "/" + maxPoints);
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + args[2]);
        }
        
        return true;
    }
    
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("soulpoints.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /soulpoints give <player> <amount>");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[1]);
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            int currentPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive.");
                return true;
            }
            
            soulPointsManager.addSoulPoints(target.getUniqueId(), amount);
            int newPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int actualGiven = newPoints - currentPoints;
            
            sender.sendMessage("§aGave " + actualGiven + " soul points to " + target.getName() + " (now " + newPoints + "/" + maxPoints + ")");
            target.sendMessage("§a✦ You received " + actualGiven + " soul point" + (actualGiven != 1 ? "s" : "") + "! Current: " + newPoints + "/" + maxPoints);
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + args[2]);
        }
        
        return true;
    }
    
    private boolean handleTakeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("soulpoints.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /soulpoints take <player> <amount>");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[1]);
            return true;
        }
        
        try {
            int amount = Integer.parseInt(args[2]);
            int currentPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int maxPoints = plugin.getConfig().getInt("soul-points.max", 10);
            
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive.");
                return true;
            }
            
            soulPointsManager.removeSoulPoints(target.getUniqueId(), amount);
            int newPoints = soulPointsManager.getSoulPoints(target.getUniqueId());
            int actualTaken = currentPoints - newPoints;
            
            sender.sendMessage("§cTook " + actualTaken + " soul points from " + target.getName() + " (now " + newPoints + "/" + maxPoints + ")");
            target.sendMessage("§c✦ You lost " + actualTaken + " soul point" + (actualTaken != 1 ? "s" : "") + "! Current: " + newPoints + "/" + maxPoints);
            
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: " + args[2]);
        }
        
        return true;
    }
    
    private boolean handleCheckCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cUsage: /soulpoints check <player>");
                return true;
            }
            Player player = (Player) sender;
            showSoulPoints(sender, player);
            return true;
        }
        
        if (!sender.hasPermission("soulpoints.check.others")) {
            sender.sendMessage("§cYou don't have permission to check other players' soul points.");
            return true;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[1]);
            return true;
        }
        
        showSoulPoints(sender, target);
        return true;
    }
    
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("soulpoints.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        plugin.reloadPlugin();
        sender.sendMessage("§aPlugin configuration reloaded!");
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
        
        sender.sendMessage("§8§m                                                ");
        sender.sendMessage("§6✦ Soul Points - " + target.getName());
        sender.sendMessage("");
        sender.sendMessage("§f" + progressBar + " §7(" + currentPoints + "/" + maxPoints + ")");
        sender.sendMessage("");
        sender.sendMessage("§7Current Penalties:");
        sender.sendMessage("  §cItem Drop: §f" + dropRates.itemDrop + "%");
        sender.sendMessage("  §cHotbar Drop: §f" + (dropRates.hotbarDrop ? "Yes" : "No"));
        sender.sendMessage("  §cArmor Drop: §f" + (dropRates.armorDrop ? "Yes" : "No"));
        
        if (currentPoints < maxPoints) {
            sender.sendMessage("");
            sender.sendMessage("§7Next Recovery: §f" + recoveryTime);
        }
        
        sender.sendMessage("§8§m                                                ");
    }
    
    private String createProgressBar(int current, int max) {
        int barLength = 20;
        int filledBars = (int) ((double) current / max * barLength);
        
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("§a█");
            } else {
                bar.append("§7█");
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
        sender.sendMessage("§8§m                                                ");
        sender.sendMessage("§6✦ Soul Points Commands");
        sender.sendMessage("");
        sender.sendMessage("§f/soulpoints §7- Check your soul points");
        sender.sendMessage("§f/soulpoints check <player> §7- Check someone's soul points");
        
        if (sender.hasPermission("soulpoints.admin")) {
            sender.sendMessage("§f/soulpoints set <player> <amount> §7- Set soul points");
            sender.sendMessage("§f/soulpoints give <player> <amount> §7- Give soul points");
            sender.sendMessage("§f/soulpoints take <player> <amount> §7- Take soul points");
            sender.sendMessage("§f/soulpoints reload §7- Reload config");
        }
        
        sender.sendMessage("§8§m                                                ");
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
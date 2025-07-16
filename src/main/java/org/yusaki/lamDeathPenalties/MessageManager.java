package org.yusaki.lamDeathPenalties;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageManager {
    private final LamDeathPenalties plugin;
    private Map<String, String> singleMessages;
    private Map<String, List<String>> multiMessages;
    
    public MessageManager(LamDeathPenalties plugin) {
        this.plugin = plugin;
        this.singleMessages = new HashMap<>();
        this.multiMessages = new HashMap<>();
        loadMessages();
    }
    
    public void loadMessages() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        
        if (messagesSection == null) {
            plugin.getLogger().warning("No messages section found in config.yml! Using default messages.");
            return;
        }
        
        singleMessages.clear();
        multiMessages.clear();
        
        for (String key : messagesSection.getKeys(false)) {
            Object value = messagesSection.get(key);
            
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> messageList = (List<String>) value;
                multiMessages.put(key, messageList);
            } else if (value instanceof String) {
                singleMessages.put(key, (String) value);
            }
        }
        
        plugin.getLogger().info("Loaded " + singleMessages.size() + " single messages and " + multiMessages.size() + " multi-line messages");
    }
    
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = singleMessages.get(key);
        if (message == null) {
            return "§cMessage not found: " + key;
        }
        
        return replacePlaceholders(message, placeholders);
    }
    
    public String getMessage(String key) {
        return getMessage(key, new HashMap<>());
    }
    
    public List<String> getMessageList(String key, Map<String, String> placeholders) {
        List<String> messages = multiMessages.get(key);
        if (messages == null) {
            return List.of("§cMessage list not found: " + key);
        }
        
        return messages.stream()
                .map(msg -> replacePlaceholders(msg, placeholders))
                .toList();
    }
    
    public List<String> getMessageList(String key) {
        return getMessageList(key, new HashMap<>());
    }
    
    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
    
    public void sendMessage(org.bukkit.command.CommandSender sender, String key, Map<String, String> placeholders) {
        sender.sendMessage(getMessage(key, placeholders));
    }
    
    public void sendMessage(org.bukkit.command.CommandSender sender, String key) {
        sendMessage(sender, key, new HashMap<>());
    }
    
    public void sendMessageList(org.bukkit.command.CommandSender sender, String key, Map<String, String> placeholders) {
        List<String> messages = getMessageList(key, placeholders);
        for (String message : messages) {
            sender.sendMessage(message);
        }
    }
    
    public void sendMessageList(org.bukkit.command.CommandSender sender, String key) {
        sendMessageList(sender, key, new HashMap<>());
    }
    
    public static Map<String, String> createPlaceholders() {
        return new HashMap<>();
    }
    
    public static Map<String, String> placeholders(String... keyValuePairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i + 1 < keyValuePairs.length) {
                map.put(keyValuePairs[i], keyValuePairs[i + 1]);
            }
        }
        return map;
    }
}
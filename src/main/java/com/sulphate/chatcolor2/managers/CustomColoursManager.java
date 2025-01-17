package com.sulphate.chatcolor2.managers;

import com.sulphate.chatcolor2.utils.Config;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CustomColoursManager {

    private final ConfigsManager configsManager;

    private final Map<String, String> customColoursMap;
    private YamlConfiguration config;

    public CustomColoursManager(ConfigsManager configsManager) {
        this.configsManager = configsManager;
        customColoursMap = new HashMap<>();

        reload();
    }

    public boolean addCustomColour(String name, String colour) {
        if (customColoursMap.containsKey(name)) {
            return false;
        }

        customColoursMap.put(name, colour);
        config.set(name, colour);
        configsManager.saveConfig(Config.CUSTOM_COLOURS);
        return true;
    }

    public String getCustomColour(String name) {
        return customColoursMap.get(name);
    }

    public void reload() {
        customColoursMap.clear();
        config = configsManager.getConfig(Config.CUSTOM_COLOURS);
        Set<String> keys = config.getKeys(false);

        for (String key : keys) {
            String colour = config.getString(key);
            customColoursMap.put('%' + key, colour);
        }
    }

}

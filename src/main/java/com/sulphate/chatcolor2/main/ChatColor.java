package com.sulphate.chatcolor2.main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import com.sulphate.chatcolor2.commands.ChatColorCommand;
import com.sulphate.chatcolor2.gui.GUIManager;
import com.sulphate.chatcolor2.listeners.*;
import com.sulphate.chatcolor2.managers.ConfigsManager;
import com.sulphate.chatcolor2.managers.ConfirmationsManager;
import com.sulphate.chatcolor2.managers.CustomColoursManager;
import com.sulphate.chatcolor2.managers.HandlersManager;
import com.sulphate.chatcolor2.schedulers.AutoSaveScheduler;
import com.sulphate.chatcolor2.utils.*;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.sulphate.chatcolor2.schedulers.ConfirmScheduler;
import com.sulphate.chatcolor2.commands.ConfirmHandler;

public class ChatColor extends JavaPlugin {

    private static ChatColor plugin;

    private HandlersManager handlersManager;
    private ConfigsManager configsManager;
    private CustomColoursManager customColoursManager;
    private ConfigUtils configUtils;
    private GeneralUtils generalUtils;
    private GUIManager guiManager;
    private ConfirmationsManager confirmationsManager;
    private AutoSaveScheduler saveScheduler;
    private Messages M;

    private ConsoleCommandSender console = Bukkit.getConsoleSender();
    private PluginManager manager;

    @Override
    public void onEnable() {
        plugin = this;
        manager = Bukkit.getPluginManager();

        // Create any needed configs.
        if (!setupConfigs()) {
            manager.disablePlugin(this);
            return;
        }

        //Checking if Metrics is allowed for this plugin
        boolean metrics = getConfig().getBoolean("stats");
        if (metrics) {
            new Metrics(this);
        }

        // Setup objects. commands & listeners.
        setupObjects();
        setupCommands();
        setupListeners();

        // Startup messages.
        List<String> messages = configUtils.getStartupMessages();

        for (String message : messages) {
            message = message.replace("[version]", getDescription().getVersion());
            message = message.replace("[version-description]", "Custom colours v2.0!");
            console.sendMessage(M.PREFIX + GeneralUtils.colourise(message));
        }

        // Show legacy notice if necessary.
        if (CompatabilityUtils.isHexLegacy()) {
            console.sendMessage(M.PREFIX + M.LEGACY_DETECTED);
        }

        // Check whether PlaceholderAPI is installed, if it is load the expansion.
        if (manager.getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(this, configUtils, generalUtils, customColoursManager, M).register();
            console.sendMessage(M.PREFIX + M.PLACEHOLDERS_ENABLED);
        }
        else {
            console.sendMessage(M.PREFIX + M.PLACEHOLDERS_DISABLED);
        }

        // Send the relevant metrics message.
        if (!metrics) {
            console.sendMessage(M.PREFIX + M.METRICS_DISABLED);
        }
        else {
            console.sendMessage(M.PREFIX + M.METRICS_ENABLED);
        }

        // Call join event for all online players.
        for (Player player : Bukkit.getOnlinePlayers()) {
            manager.callEvent(new PlayerJoinEvent(player, ""));
        }
    }

    private void setupObjects() {
        // Init compatability utils.
        CompatabilityUtils.init();

        handlersManager = new HandlersManager();
        configsManager = new ConfigsManager();
        customColoursManager = new CustomColoursManager(configsManager);
        configUtils = new ConfigUtils(configsManager);
        generalUtils = new GeneralUtils(configUtils, customColoursManager, M);
        M = new Messages(configUtils);
        guiManager = new GUIManager(configsManager, configUtils, generalUtils, M);
        confirmationsManager = new ConfirmationsManager();

        boolean setSaveInterval = false;
        if (configUtils.getSetting("save-interval") == null) {
            setSaveInterval = true;
            saveScheduler = new AutoSaveScheduler(5); // Use the default if the setting is null, set afterwards.
        }
        else {
            saveScheduler = new AutoSaveScheduler((int) configUtils.getSetting("save-interval"));
        }

        // Scan messages and settings to make sure all are present.
        scanMessages();
        scanSettings();

        if (setSaveInterval) {
            saveScheduler.setSaveInterval((int) configUtils.getSetting("save-interval"));
        }
    }

    private void setupCommands() {
        getCommand("chatcolor").setExecutor(new ChatColorCommand(
                M, generalUtils, configUtils, confirmationsManager, configsManager, handlersManager,
                guiManager, customColoursManager
        ));

        handlersManager.registerHandler(ConfirmHandler.class, new ConfirmHandler(M, confirmationsManager, configUtils, generalUtils));
    }

    private void setupListeners() {
        manager.registerEvents(new PlayerJoinListener(M, configUtils, generalUtils, configsManager), this);
        manager.registerEvents(new ChatListener(configUtils, generalUtils), this);
        manager.registerEvents(new CustomCommandListener(configUtils), this);
        manager.registerEvents(guiManager, this);
    }

    @Override
    public void onDisable() {
        saveScheduler.stop();
        plugin = null;

        console.sendMessage(M.PREFIX + M.SHUTDOWN.replace("[version]", getDescription().getVersion()));
    }

    public static ChatColor getPlugin() {
        return plugin;
    }

    public AutoSaveScheduler getSaveScheduler() {
        return saveScheduler;
    }

    private boolean setupConfigs() {
        File dataFolder = getDataFolder();

        // Create data folder if it doesn't exist.
        if (!dataFolder.exists()) {
            if (!dataFolder.mkdir()) {
                console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cError: Failed to create data folder."));
                return false;
            }
        }

        File config = new File(dataFolder, "config.yml");

        // Save default config if it doesn't exist.
        if (!config.exists()) {
            saveResource("config.yml", true);
        }
        else {
            // Check if the old config version is less than 1.7.9:
            // If it is, backup the old config and load the new format.
            YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(config);
            String version = currentConfig.getString("version");
            String latest = getDescription().getVersion();

            if (!compareVersions(version, "1.7.9")) {
                if (!backupOldConfig()) return false;
                saveResource("config.yml", true);

                console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cWarning: &eAn old version of the config was found. It has been copied to &aold-config.yml&e."));
            }
            else if (!compareVersions(version, "1.12")) {
                File legacyGroupConfigFile = new File(dataFolder, "groups.yml");

                if (legacyGroupConfigFile.exists()) {
                    YamlConfiguration legacyGroupConfig = YamlConfiguration.loadConfiguration(legacyGroupConfigFile);
                    File newGroupConfigFile = new File(dataFolder, "groups.yml");

                    try {
                        newGroupConfigFile.createNewFile();
                        legacyGroupConfig.save(newGroupConfigFile);
                        legacyGroupConfigFile.delete();

                        GeneralUtils.sendConsoleMessage("&b[ChatColor] &bInfo: &eCopied legacy groups config to a new file, groups.yml.");
                    }
                    catch (IOException ex) {
                        ex.printStackTrace();
                        GeneralUtils.sendConsoleMessage("&b[ChatColor] &cWarning: &eFailed to copy legacy groups config to new file.");
                    }
                }
            }

            // Update the version if it's behind.
            if (!version.equals(latest)) {
                currentConfig.set("version", latest);

                try {
                    currentConfig.save(new File(dataFolder, "config.yml"));
                }
                catch (IOException ex) {
                    console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cError: Failed to save updated config.yml."));
                    // Not a critical error, continuing.
                }
            }
        }

        File messages = new File(dataFolder, "messages.yml");

        // Save default messages if they don't exist.
        if (!messages.exists()) {
            saveResource("messages.yml", true);
        }

        File groupsFile = new File(dataFolder, "groups.yml");

        // Save default colours file if it doesn't exist.
        if (!groupsFile.exists()) {
            saveResource("groups.yml", true);
        }

        File playerList = new File(dataFolder, "player-list.yml");

        // Create player list if it doesn't exist.
        if (!playerList.exists()) {
            try {
                playerList.createNewFile();
            }
            catch (IOException ex) {
                console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cError: Failed to create player list file."));
                return false;
            }
        }

        File guiConfig = new File(dataFolder, "gui.yml");

        if (!guiConfig.exists()) {
            saveResource("gui.yml", true);
        }

        File customColours = new File(dataFolder, "custom-colors.yml");

        if (!customColours.exists()) {
            saveResource("custom-colors.yml", true);
        }

        return true;
    }

    // Compares two version strings, returning true if the first is greater than or equal to the second (in format x.x.x...x).
    private boolean compareVersions(String version1, String version2) {
        // This happens on VERY old versions of the plugin (2017).
        if (version1 == null) {
            return false;
        }

        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        // Iterating up to first version's length, as the version string may be shorter.
        for (int i = 0; i < parts1.length; i++) {
            if (i == parts2.length) {
                return true;
            }

            int intPart1 = Integer.parseInt(parts1[i]);
            int intPart2 = Integer.parseInt(parts2[i]);

            // If greater, this is a newer version.
            if (intPart1 > intPart2) return true;
            // If less, this is an older version.
            if (intPart1 < intPart2) return false;
            // If equal, continue to compare.
        }

        return true;
    }

    // Backs up an old version of the config to a separate file, so it can be copied from to the new format.
    private boolean backupOldConfig() {
        File oldConfig = new File(getDataFolder(), "config.yml");
        File backupFile = new File(getDataFolder(), "old-config.yml");

        try {
            // Create the backup file, load the old config and save it to the file.
            if (!backupFile.exists()) {
                if (!backupFile.createNewFile()) return false;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(oldConfig);
            config.save(backupFile);
        }
        catch (IOException ex) {
            console.sendMessage(GeneralUtils.colourise("&b[ChatColor] &cError: Failed to create backup file."));
            return false;
        }

        return true;
    }

    // Scans the current messages.yml to make sure all messages are present (compared with current default).
    private void scanMessages() {
        InputStream defaultStream = getResource("messages.yml");

        if (defaultStream == null) {
            console.sendMessage(M.PREFIX + GeneralUtils.colourise("&cError: Failed to load default messages resource. Messages will not be scanned."));
            return;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
        YamlConfiguration currentConfig = configsManager.getConfig("messages.yml");

        Set<String> keys = defaultConfig.getKeys(false);
        boolean needsReload = false;
        for (String key : keys) {
            // Check all messages are present.
            if (!currentConfig.contains(key)) {
                // If not, set the message and save the config.
                // Yes, this ruins the formatting, but at least the plugin works.
                currentConfig.set(key, defaultConfig.getString(key));
                configsManager.saveConfig(Config.MESSAGES);

                console.sendMessage(M.PREFIX + GeneralUtils.colourise("&eAdded new message: &a" + key));
                needsReload = true;
            }
        }

        // Reload messages if necessary.
        if (needsReload) {
            M.reloadMessages();
        }
    }

    // Scans the current config.yml for settings differences (any new settings will be added).
    private void scanSettings() {
        InputStream defaultStream = getResource("config.yml");

        if (defaultStream == null) {
            console.sendMessage(M.PREFIX + GeneralUtils.colourise("&cError: Failed to load default config resource. Settings will not be scanned."));
            return;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
        YamlConfiguration currentConfig = configsManager.getConfig("config.yml");

        Set<String> keys = defaultConfig.getConfigurationSection("settings").getKeys(false);
        for (String key : keys) {
            // Check all settings are present.
            if (!currentConfig.contains("settings." + key)) {
                // If not, set the default and save the config.
                currentConfig.set("settings." + key, defaultConfig.get("settings." + key));
                configsManager.saveConfig(Config.MAIN_CONFIG);
            }
        }
    }

    // Adds a player to the confirming list, and starts the scheduler.
    public void createConfirmScheduler(Player player, String type, Object value) {
        ConfirmScheduler scheduler = new ConfirmScheduler(M, confirmationsManager, configUtils, player, type, value);
        confirmationsManager.addConfirmingPlayer(player, scheduler);
    }

}

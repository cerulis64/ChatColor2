package com.sulphate.chatcolor2.commands;

import com.sulphate.chatcolor2.gui.GUIManager;
import com.sulphate.chatcolor2.main.ChatColor;
import com.sulphate.chatcolor2.managers.ConfigsManager;
import com.sulphate.chatcolor2.managers.ConfirmationsManager;
import com.sulphate.chatcolor2.managers.CustomColoursManager;
import com.sulphate.chatcolor2.managers.HandlersManager;
import com.sulphate.chatcolor2.utils.CompatabilityUtils;
import com.sulphate.chatcolor2.utils.ConfigUtils;
import com.sulphate.chatcolor2.utils.GeneralUtils;
import com.sulphate.chatcolor2.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class ChatColorCommand implements CommandExecutor {

    private final Messages M;
    private final GeneralUtils generalUtils;
    private final ConfigUtils configUtils;
    private final ConfirmationsManager confirmationsManager;
    private final ConfigsManager configsManager;
    private final HandlersManager handlersManager;
    private final GUIManager guiManager;
    private final CustomColoursManager customColoursManager;

    public ChatColorCommand(
            Messages M, GeneralUtils generalUtils, ConfigUtils configUtils, ConfirmationsManager confirmationsManager,
            ConfigsManager configsManager, HandlersManager handlersManager, GUIManager guiManager,
            CustomColoursManager customColoursManager
    ) {
        this.M = M;
        this.generalUtils = generalUtils;
        this.configUtils = configUtils;
        this.confirmationsManager = confirmationsManager;
        this.configsManager = configsManager;
        this.handlersManager = handlersManager;
        this.guiManager = guiManager;
        this.customColoursManager = customColoursManager;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        int argsno = args.length;

        if (sender instanceof Player) {

            Player s = (Player) sender;
            // Run the command check.
            // This checks: permissions, valid colours, errors, confirming restrictions, and anything else I missed.
            if (!checkCommand(args, s)) {
                return true;
            }

            List<String> cmds = Arrays.asList("confirm", "help", "commandshelp", "permissionshelp", "settingshelp", "set", "reset", "reload", "available", "gui", "add", "remove", "group");
            if (cmds.contains(args[0].toLowerCase())) {
                switch (args[0].toLowerCase()) {
                    case "confirm": {
                        return handlersManager.callHandler(ConfirmHandler.class, s);
                    }

                    case "help":
                    case "commandshelp": {
                        handleCommandsHelp(s);
                        return true;
                    }

                    case "permissionshelp": {
                        handlePermissionsHelp(s);
                        return true;
                    }

                    case "settingshelp": {
                        handleSettingsHelp(s);
                        return true;
                    }

                    case "set": {
                        handleSet(args, s);
                        return true;
                    }

                    case "reset": {
                        s.sendMessage(M.PREFIX + M.CONFIRM);
                        ChatColor.getPlugin().createConfirmScheduler(s, "reset", null);
                        return true;
                    }

                    case "reload": {
                        configsManager.loadAllConfigs();
                        M.reloadMessages();
                        guiManager.reload();
                        customColoursManager.reload();

                        // Re-load player configs to avoid plugin inoperation.
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            configsManager.loadPlayerConfig(player.getUniqueId());
                        }

                        s.sendMessage(M.PREFIX + M.RELOADED_MESSAGES);
                        return true;
                    }

                    case "available": {
                        char[] availableColours = GeneralUtils.getAvailableColours(s);
                        char[] availableModifiers = GeneralUtils.getAvailableModifiers(s);

                        String colourString = buildCharacterColourString(availableColours);
                        String modifierString = buildCharacterColourString(availableModifiers);

                        s.sendMessage(M.PREFIX + M.AVAILABLE_COLORS);
                        s.sendMessage(GeneralUtils.colourise(" &7- &e" + M.PREFIX + M.COLORS + ": " + colourString));
                        s.sendMessage(GeneralUtils.colourise(" &7- &e" + M.PREFIX + M.MODIFIERS + ": " + modifierString));
                        s.sendMessage(M.PREFIX + (checkPermission(s, "chatcolor.use-hex-codes") ? M.HEX_ACCESS : M.NO_HEX_PERMISSIONS));

                        return true;
                    }

                    case "gui": {
                        guiManager.openGUI(s, "main");
                        return true;
                    }

                    case "add": {
                        // Add the new modifier to their chat colour.
                        String modifierToAdd = getModifier(args[1]);
                        String colour = configUtils.getColour(s.getUniqueId());
                        String newColour = colour + modifierToAdd;

                        configUtils.setColour(s.getUniqueId(), newColour);
                        s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OWN_COLOR, newColour));
                        return true;
                    }

                    case "remove": {
                        // Remove the modifier from their chat colour.
                        String modifierToRemove = getModifier(args[1]);
                        String colour = configUtils.getColour(s.getUniqueId());
                        String newColour = colour.replace(modifierToRemove, "");

                        configUtils.setColour(s.getUniqueId(), newColour);
                        s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OWN_COLOR, newColour));
                        return true;
                    }

                    case "group": {
                        if (args[1].equals("list")) {
                            s.sendMessage(M.PREFIX + M.GROUP_COLOR_LIST);

                            HashMap<String, String> groupColours = configUtils.getGroupColours();
                            for (String colourName : groupColours.keySet()) {
                                s.sendMessage(generalUtils.colourSetMessage(M.GROUP_COLOR_FORMAT.replace("[color-name]", colourName), groupColours.get(colourName)));
                            }

                            return true;
                        }

                        // The action to perform.
                        String action = args[1];
                        String name = args[2];

                        if (action.equals("add")) {
                            String colour = getColour(args[3]);
                            String modifiers = "";

                            // Build the modifiers.
                            if (args.length > 4) {
                                StringBuilder modifiersBuilder = new StringBuilder();

                                for (int i = 4; i < args.length; i++) {
                                    modifiersBuilder.append(getModifier(args[i]));
                                }

                                modifiers = modifiersBuilder.toString();
                            }

                            String fullColour = colour + modifiers;
                            configUtils.addGroupColour(name, fullColour);
                            s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.ADDED_GROUP_COLOR.replace("[color-name]", name), fullColour));
                        }
                        else if (action.equals("remove")) {
                            configUtils.removeGroupColour(name);
                            s.sendMessage(M.PREFIX + M.REMOVED_GROUP_COLOR.replace("[color-name]", name));
                        }

                        return true;
                    }
                }
            }

            // Check if they are setting another player's name.
            UUID uuid = configUtils.getUUIDFromName(args[0]);
            if (uuid != null) {
                // If they are offline, we must load their config first.
                if (configsManager.getPlayerConfig(uuid) == null) {
                    configsManager.loadPlayerConfig(uuid);
                }

                String result;

                // Check if it's being set back to default.
                if (args[1].equals("default")) {
                    String colour = configUtils.getDefaultColourForPlayer(uuid);
                    configUtils.setColour(uuid, colour);

                    result = colour;
                }
                else {
                    result = colourFromArgs(args, 1);
                    configUtils.setColour(uuid, result);
                }

                // Notify the player, if necessary.
                Player target = Bukkit.getPlayer(uuid);
                if ((boolean) configUtils.getSetting("notify-others") && target != null) {
                    target.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.PLAYER_SET_YOUR_COLOR.replace("[player]", s.getName()), result));
                }

                s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OTHERS_COLOR.replace("[player]", args[0]), result));
            }
            // Otherwise, set their colour.
            else {
                // Check if they have been set to a group colour, and if it's forced.
                // Admins no longer bypass this check as it's no longer possible to wildcard group colour permissions.
                if (configUtils.getGroupColour(s) != null) {
                    if ((boolean) configUtils.getSetting("force-group-colors")) {
                        s.sendMessage(M.PREFIX + M.USING_GROUP_COLOR);
                        return true;
                    }
                }

                String result;

                // Check if they want to go back to their default.
                if (args[0].equals("default")) {
                    String colour = configUtils.getDefaultColourForPlayer(s.getUniqueId());
                    configUtils.setColour(s.getUniqueId(), colour);

                    result = colour;
                }
                else {
                    result = colourFromArgs(args, 0);
                    configUtils.setColour(s.getUniqueId(), result);
                }

                s.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OWN_COLOR, result));
            }

            return true;
        }
        // Sent from console - no need for checking permissions.
        // Settings changes are not supported by the console, only from in-game.
        else {
            if (argsno < 2) {
                sender.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return true;
            }

            if (argsno > 6) {
                sender.sendMessage(M.PREFIX + M.TOO_MANY_ARGS);
                return true;
            }

            UUID uuid = configUtils.getUUIDFromName(args[0]);

            // If it's a command block sending the command, check for @p argument.
            if (uuid == null && sender instanceof BlockCommandSender && args[0].equals("@p")) {
                BlockCommandSender blockSender = (BlockCommandSender) sender;
                Location location = blockSender.getBlock().getLocation();

                // Find the nearest player.
                Collection<Player> players = location.getWorld().getEntitiesByClass(Player.class);
                Player closestPlayer = null;
                double closestDistance = Integer.MAX_VALUE;

                for (Player player : players) {
                    Location playerLoc = player.getLocation();
                    double distance = playerLoc.distance(location); // Wow, didn't know that existed, very nice.

                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestPlayer = player;
                    }
                }

                // May technically be null but the likelihood is it won't. Still, won't cause any issues.
                if (closestPlayer != null) {
                    uuid = closestPlayer.getUniqueId();
                }
            }

            if (uuid != null) {
                // If their config hasn't been loaded, we must load it.
                if (configsManager.getPlayerConfig(uuid) == null) {
                    configsManager.loadPlayerConfig(uuid);
                }

                // Make sure the colour / modifiers are valid.
                for (int i = 1; i < argsno; i++) {
                    if (i == 1) {
                        String colour = getColour(args[i]);

                        if (colour == null) {
                            sender.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[i]));
                            return true;
                        }
                        // Check for hex support, if necessary.
                        else if (GeneralUtils.isValidHexColour(colour) && CompatabilityUtils.isHexLegacy()) {
                            sender.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                            return false;
                        }
                    }

                    else if (getModifier(args[i]) == null) {
                        sender.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", args[i]));
                        return true;
                    }
                }

                String colour = colourFromArgs(args, 1);
                configUtils.setColour(uuid, colour);
                Player target = Bukkit.getPlayer(args[0]);

                if ((boolean) configUtils.getSetting("notify-others") && target != null) {
                    target.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.PLAYER_SET_YOUR_COLOR.replace("[player]", "CONSOLE"), colour));
                }
                sender.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.SET_OTHERS_COLOR.replace("[player]", args[0]), colour));
            }
            else {
                sender.sendMessage(M.PREFIX + M.PLAYER_NOT_JOINED);
            }
        }

        return true;
    }

    private String buildCharacterColourString(char[] characters) {
        String comma = GeneralUtils.colourise("&7, ");
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < characters.length; i++) {
            char mod = characters[i];
            builder.append('&').append(mod).append(mod);

            if (i != characters.length - 1) {
                builder.append(comma);
            }
        }

        return builder.toString();
    }

    public String colourFromArgs(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();

        for (int i = startIndex; i < args.length; i++) {
            if (i == startIndex) {
                builder.append(getColour(args[i]));
            }
            else {
                builder.append(getModifier(args[i]));
            }
        }

        return builder.toString();
    }

    private boolean checkPermission(Player player, String permission) {
        return (player.isOp() || player.hasPermission(permission));
    }

    // Checks the command given, including any permissions / invalid commands.
    private boolean checkCommand(String[] args, Player player) {
        UUID uuid = player.getUniqueId();

        if (args.length == 0) {
            // Check if they have a group colour, and if it should be enforced (copied code from chat listener, may abstract it at some point).
            String groupColour = configUtils.getGroupColour(player);
            String colour = configUtils.getColour(uuid);

            if (groupColour != null) {
                // If it should be forced, set it so.
                if ((boolean) configUtils.getSetting("force-group-colors")) {
                    colour = groupColour;
                }
            }

            player.sendMessage(M.PREFIX + generalUtils.colourSetMessage(M.CURRENT_COLOR, colour));
            return false;
        }

        if (!player.isOp() && !player.hasPermission("chatcolor.use")) {
            player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
            return false;
        }

        // Single-argument commands.
        List<String> cmds = Arrays.asList("confirm", "reload", "reset", "help", "permissionshelp", "commandshelp", "settingshelp", "available");
        if (cmds.contains(args[0])) {
            if (args[0].equalsIgnoreCase("reset") && confirmationsManager.isConfirming(player)) {
                player.sendMessage(M.PREFIX + M.ALREADY_CONFIRMING);
                return false;
            }

            if (!player.isOp() && !player.hasPermission("chatcolor.admin") && !(args[0].equals("commandshelp") || args[0].equals("available"))) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            return true;
        }

        // Check if they are changing a setting.
        if (args[0].equals("set")) {
            if (args.length < 3) {
                player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return false;
            }

            List<String> settings = Arrays.asList("auto-save", "save-interval", "color-override", "notify-others", "join-message", "confirm-timeout", "default-color", "command-name", "force-group-colors", "default-color-enabled");

            if (!settings.contains(args[1])) {
                player.sendMessage(M.PREFIX + M.INVALID_SETTING.replace("[setting]", args[1]));
                return false;
            }

            if (confirmationsManager.isConfirming(player)) {
                player.sendMessage(M.PREFIX + M.ALREADY_CONFIRMING);
                return false;
            }

            if (!player.isOp() && !player.hasPermission("chatcolor.admin")) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            return true;
        }

        // Check if they want to use the GUI, and if they can.
        if (args[0].equals("gui")) {
            if (!player.isOp() && !player.hasPermission("chatcolor.gui")) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            return true;
        }

        // Check if they are adding/removing a modifier from their colour.
        if (args[0].equals("add") || args[0].equals("remove")) {
            if (args.length < 2) {
                player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return false;
            }

            String colour = configUtils.getColour(player.getUniqueId());

            if (GeneralUtils.isCustomColour(colour)) {
                player.sendMessage(M.CANNOT_MODIFY_CUSTOM_COLOR);
                return false;
            }

            String modifierToCheck = args[1];
            String modifier = getModifier(modifierToCheck);

            if (modifier == null) {
                player.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", modifierToCheck));
                return false;
            }

            // Check if it's in their colour or not.
            if (args[0].equals("remove") && !colour.contains(modifier)) {
                player.sendMessage(M.PREFIX + M.MODIFIER_NOT_IN_COLOR);
                return false;
            }

            if (args[0].equals("add") && colour.contains(modifier)) {
                player.sendMessage(M.PREFIX + M.MODIFIER_ALREADY_IN_COLOR);
                return false;
            }

            return true;
        }

        // Check if they are modifying a group colour.
        if (args[0].equals("group")) {
            if (args.length < 2) {
                player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return false;
            }

            if (!player.isOp() && !player.hasPermission("chatcolor.admin")) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            // Check for list command.
            if (args[1].equals("list")) {
                return true;
            }
            else if (args.length < 3) {
                player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return false;
            }

            String action = args[1];
            String name = args[2];

            if (action.equals("add")) {

                if (args.length < 4) {
                    player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                    return false;
                }

                // Make sure it doesn't already exist.
                if (configUtils.groupColourExists(name)) {
                    player.sendMessage(M.PREFIX + M.GROUP_COLOR_EXISTS);
                    return false;
                }

                // Check the colour is valid.
                String colour = getColour(args[3]);

                if (colour == null) {
                    player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[3]));
                    return false;
                }
                // Check for hex support, if necessary.
                else if (CompatabilityUtils.isHexLegacy() && GeneralUtils.isValidHexColour(colour)) {
                    player.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                    return false;
                }
                else if (GeneralUtils.isCustomColour(colour) && customColoursManager.getCustomColour(colour) == null) {
                    player.sendMessage(M.INVALID_CUSTOM_COLOR);
                    return false;
                }

                // Check all modifiers, if there are any.
                if (args.length > 4) {
                    for (int i = 4; i < args.length; i++) {
                        if (getModifier(args[i]) == null) {
                            player.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", args[i]));
                            return false;
                        }
                    }
                }

                return true;
            }
            else if (action.equals("remove")) {

                // Make sure it exists.
                if (!configUtils.groupColourExists(name)) {
                    player.sendMessage(M.PREFIX + M.GROUP_COLOR_NOT_EXISTS);
                    return false;
                }

                return true;
            }
            // Action not recognised, invalid command.
            else {
                player.sendMessage(M.PREFIX + M.INVALID_COMMAND);
                return false;
            }
        }

        UUID targetUUID = configUtils.getUUIDFromName(args[0]);
        if (targetUUID != null) {
            if (!player.isOp() && !player.hasPermission("chatcolor.change.others")) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            if (args.length > 7) {
                player.sendMessage(M.PREFIX + M.TOO_MANY_ARGS);
                return false;
            }

            if (args.length < 2) {
                player.sendMessage(M.PREFIX + M.NOT_ENOUGH_ARGS);
                return false;
            }

            String colour = getColour(args[1]);

            if (colour != null) {
                // Check for hex support, if necessary.
                if (GeneralUtils.isValidHexColour(colour) && CompatabilityUtils.isHexLegacy()) {
                    player.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                    return false;
                }
                else if (GeneralUtils.isCustomColour(colour)) {
                    if (customColoursManager.getCustomColour(colour) == null) {
                        player.sendMessage(M.INVALID_CUSTOM_COLOR);
                        return false;
                    }
                    else if (args.length > 2) {
                        player.sendMessage(M.CANNOT_MODIFY_CUSTOM_COLOR);
                        return false;
                    }
                }

                // If setting to their default, return true.
                if (colour.equals("default")) {
                    return true;
                }

                for (int i = 1; i < args.length; i++) {
                    if (i == 1) {
                        // Check for hex colour.
                        if (args[i].startsWith("#")) {
                            if (!checkPermission(player, "chatcolor.use-hex-codes")) {
                                player.sendMessage(M.PREFIX + M.NO_HEX_PERMISSIONS);
                                return false;
                            }
                        }
                        else if (!checkPermission(player, "chatcolor.color." + args[i])) {
                            player.sendMessage(M.PREFIX + M.NO_COLOR_PERMS.replace("[color]", generalUtils.colouriseMessage(colour, args[1], false)));
                            return false;
                        }

                        continue;
                    }

                    if (getModifier(args[i]) == null) {
                        player.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", args[i]));
                        return false;
                    }

                    if (!player.isOp() && !player.hasPermission("chatcolor.modifier." + args[i])) {
                        player.sendMessage(M.PREFIX + M.NO_MOD_PERMS.replace("[modifier]", args[i]));
                        return false;
                    }
                }
                return true;
            }

            if (args[1].length() == 1) {
                player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[1]));
                return false;
            }

            player.sendMessage(M.PREFIX + M.INVALID_COMMAND);
            return false;
        }

        String colour = getColour(args[0]);
        if (colour != null) {
            if (!checkPermission(player, "chatcolor.change.self")) {
                player.sendMessage(M.PREFIX + M.NO_PERMISSIONS);
                return false;
            }

            if (args.length > 6) {
                player.sendMessage(M.PREFIX + M.TOO_MANY_ARGS);
                return false;
            }

            // Check for hex support, if necessary.
            if (GeneralUtils.isValidHexColour(colour) && CompatabilityUtils.isHexLegacy()) {
                player.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                return false;
            }
            else if (GeneralUtils.isCustomColour(colour)) {
                if (customColoursManager.getCustomColour(colour) == null) {
                    player.sendMessage(M.INVALID_CUSTOM_COLOR);
                    return false;
                }
                else if (!player.isOp() && !player.hasPermission("chatcolor.custom." + colour.replace("%", ""))) {
                    player.sendMessage(M.NO_CUSTOM_COLOR_PERMISSIONS);
                    return false;
                }
                else if (args.length > 1) {
                    player.sendMessage(M.CANNOT_MODIFY_CUSTOM_COLOR);
                    return false;
                }
            }

            // If setting to their default, return true.
            if (colour.equals("default")) {
                return true;
            }

            for (int i = 0; i < args.length; i++) {
                if (i == 0) {
                    if (colour.startsWith("#")) {
                        if (!checkPermission(player, "chatcolor.use-hex-codes")) {
                            player.sendMessage(M.PREFIX + M.NO_HEX_PERMISSIONS);
                            return false;
                        }
                    }
                    else if (!checkPermission(player, "chatcolor.color." + args[0])) {
                        player.sendMessage(M.PREFIX + M.NO_COLOR_PERMS.replace("[color]", generalUtils.colouriseMessage(colour, args[0], false)));
                        return false;
                    }

                    continue;
                }

                String mod = getModifier(args[i]);
                if (mod == null) {
                    player.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", args[i]));
                    return false;
                }

                if (!checkPermission(player, "chatcolor.modifier." + args[i])) {
                    player.sendMessage(M.PREFIX + M.NO_MOD_PERMS.replace("[modifier]", GeneralUtils.colourise(mod + args[i])));
                    return false;
                }
            }
            return true;
        }

        if (args[0].length() == 1) {
            player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[0]));
            return false;
        }

        player.sendMessage(M.PREFIX + M.INVALID_COMMAND);
        return false;
    }

    //This is how the help command will be handled.
    // TODO: Make these all configurable messages when I regain the will to live.
    private void handleCommandsHelp(Player player) {
        player.sendMessage(M.PREFIX + "Displaying command help!");
        player.sendMessage(GeneralUtils.colourise(" &7- &eMain Command: &c/chatcolor <color/default> [modifiers]"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise(" &eOther Commands:"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eCommands Help: &c/chatcolor commandshelp"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eSee Available Colors: &c/chatcolor available"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eOpen the ChatColor GUI: &c/chatcolor gui"));

        if (player.isOp() || player.hasPermission("chatcolor.admin")) {
            player.sendMessage(GeneralUtils.colourise(" &7- &ePermissions Help: &c/chatcolor permissionshelp"));
            player.sendMessage(GeneralUtils.colourise(" &7- &eSettings Help: &c/chatcolor settingshelp"));
            player.sendMessage(GeneralUtils.colourise(" &7- &eReload Configs: &c/chatcolor reload"));
            player.sendMessage(GeneralUtils.colourise(" &7- &eSet Settings: &c/chatcolor set <setting> <value>"));
        }

        player.sendMessage(GeneralUtils.colourise(" &7- &eSet Color to Default: &c/chatcolor [player] default"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise("&eValid Colors:"));
        player.sendMessage(GeneralUtils.colourise("&00&11&22&33&44&55&66&77&88&99"));
        player.sendMessage(GeneralUtils.colourise("&aa&bb&cc&dd&ee&ff"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise("&eAlternatively:"));
        player.sendMessage(GeneralUtils.colourise("&0black, &1dark.blue, &2green, &3dark.aqua,"));
        player.sendMessage(GeneralUtils.colourise("&4red, &5purple, &6gold, &7grey, &8dark.grey, &9blue"));
        player.sendMessage(GeneralUtils.colourise("&alight.green, &baqua, &clight.red, &dmagenta, &eyellow, &fwhite"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise("&eValid modifiers:"));
        player.sendMessage(GeneralUtils.colourise("&ck, &c&ll&r, &c&mm&r, &c&nn&r, &c&oo"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise("&eAlternatively:"));
        player.sendMessage(GeneralUtils.colourise("&cobfuscated, &c&lbold&r, &c&mstrikethrough&r, &c&nunderlined&r, &c&oitalic"));
    }

    private void handlePermissionsHelp(Player player) {
        player.sendMessage(M.PREFIX + "Displaying permissions help!");
        player.sendMessage(GeneralUtils.colourise(" &7- &eMain Permission: &cchatcolor.use"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eAll Perms: &cchatcolor.*"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise("&eAdmin Permissions:"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eAll Admin Commands: 7cchatcolor.admin"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise("&eColor Permissions:"));
        player.sendMessage(GeneralUtils.colourise(" &7- &ePermission: &cchatcolor.color.<color>"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eExample: &cchatcolor.color.a"));
        player.sendMessage(GeneralUtils.colourise("&eNote: &cYou must use characters (e.g. a, b, c), not words."));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise("&eModifier Permissions:"));
        player.sendMessage(GeneralUtils.colourise(" &7- &ePermission: &cchatcolor.modifier.<modifier>"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eExample: &cchatcolor.modifier.k"));
        player.sendMessage(GeneralUtils.colourise("&eNote: No words may be used."));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise("&eOther Permissions:"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eChange Own Color: &cchatcolor.change.self"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eChange Other's Color: &cchatcolor.change.others"));
        player.sendMessage(GeneralUtils.colourise(" &7- &eSet a Group Chat Color: &7cchatcolor.group.<color name>"));
    }

    private void handleSettingsHelp(Player player) {
        player.sendMessage(M.PREFIX + "Displaying settings help!");
        player.sendMessage(GeneralUtils.colourise(" &7- &eauto-save: &cAuto save data every 5 minutes."));
        player.sendMessage(GeneralUtils.colourise("   &eUsage: &b/chatcolor set auto-save <true/false>"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise(" &7- &esave-interval: &cSets the time between saves, in minutes."));
        player.sendMessage(GeneralUtils.colourise("   &eUsage: &b/chatcolor set save-interval <time>"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise(" &7- &ecolor-override: &cOverride '&' symbols in chat."));
        player.sendMessage(GeneralUtils.colourise("   &eUsage: &b/chatcolor set color-override <true/false>"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise(" &7- &econfirm-timeout: &cSet time for confirming settings."));
        player.sendMessage(GeneralUtils.colourise("   &eUsage: &b/chatcolor set confirm-timeout <seconds>"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise(" &7- &edefault-color: &cChange the default color."));
        player.sendMessage(GeneralUtils.colourise("   &eUsage: &b/chatcolor set default-color <color> <modifiers..>"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise(" &7- &ejoin-message: &cTell players their color on join."));
        player.sendMessage(GeneralUtils.colourise("   &eUsage: &b/chatcolor set join-message <true/false>"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise(" &7- &enotify-others: &cTell others if you change their color."));
        player.sendMessage(GeneralUtils.colourise("   &eUsage: &b/chatcolor set notify-others <true/false>"));

        player.sendMessage("");
        player.sendMessage(GeneralUtils.colourise(" &7- &eforce-group-colors: &cForce group colors to be active."));
        player.sendMessage(GeneralUtils.colourise("   &eUsage: &b/chatcolor set force-group-colors <true/false>"));
    }

    private void handleSet(String[] args, Player player) {
        String trueVal = GeneralUtils.colourise("&aTRUE");
        String falseVal = GeneralUtils.colourise("&cFALSE");

        String setting = args[1];
        String currentValueString = "";
        String valueString = "";
        Object value = null;

        switch (args[1]) {

            case "auto-save": {
                boolean val;

                try {
                    val = Boolean.parseBoolean(args[2]);
                }
                catch (Exception e) {
                    player.sendMessage(M.PREFIX + M.NEEDS_BOOLEAN);
                    return;
                }

                boolean autosave = (boolean) configUtils.getSetting("auto-save");

                if (val == autosave) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = val ? falseVal : trueVal;
                valueString = val ? trueVal : falseVal;
                value = val;
                break;
            }

            case "save-interval": {
                int val;

                try {
                    val = Integer.parseInt(args[2]);
                }
                catch (NumberFormatException ex) {
                    player.sendMessage(M.PREFIX + M.NEEDS_NUMBER);
                    return;
                }

                int saveInterval = (int) configUtils.getSetting("save-interval");

                if (val == saveInterval) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = saveInterval + "";
                valueString = val + "";
                value = val;
                break;
            }

            case "color-override": {
                boolean val;

                try {
                    val = Boolean.parseBoolean(args[2]);
                }
                catch (Exception e) {
                    player.sendMessage(M.PREFIX + M.NEEDS_BOOLEAN);
                    return;
                }

                boolean override = (boolean) configUtils.getSetting("color-override");

                if (val == override) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = val ? falseVal : trueVal;
                valueString = val ? trueVal : falseVal;
                value = val;
                break;
            }

            case "notify-others": {
                boolean val;

                try {
                    val = Boolean.parseBoolean(args[2]);
                }
                catch (Exception e) {
                    player.sendMessage(M.PREFIX + M.NEEDS_BOOLEAN);
                    return;
                }

                boolean notify = (boolean) configUtils.getSetting("notify-others");

                if (val == notify) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = val ? falseVal : trueVal;
                valueString = val ? trueVal : falseVal;
                value = val;
                break;
            }

            case "join-message": {
                boolean val;

                try {
                    val = Boolean.parseBoolean(args[2]);
                }
                catch (Exception e) {
                    player.sendMessage(M.PREFIX + M.NEEDS_BOOLEAN);
                    return;
                }

                boolean notify = (boolean) configUtils.getSetting("join-message");

                if (val == notify) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = val ? falseVal : trueVal;
                valueString = val ? trueVal : falseVal;
                value = val;
                break;
            }

            case "confirm-timeout": {
                int val;

                try {
                    val = Integer.parseInt(args[2]);
                }
                catch (Exception e) {
                    player.sendMessage(M.PREFIX + M.NEEDS_NUMBER);
                    return;
                }

                currentValueString = (int) configUtils.getSetting("confirm-timeout") + "";
                valueString = val + "";
                value = val;
                break;
            }

            case "default-color": {
                String colour = getColour(args[2]);

                // Check for hex support, if necessary.
                if (GeneralUtils.isValidHexColour(colour) && CompatabilityUtils.isHexLegacy()) {
                    player.sendMessage(M.PREFIX + M.NO_HEX_SUPPORT);
                    return;
                }

                if (colour == null) {
                    player.sendMessage(M.PREFIX + M.INVALID_COLOR.replace("[color]", args[2]));
                    return;
                }

                StringBuilder builder = new StringBuilder(colour);

                for (int i = 3; i < args.length; i++) {
                    String mod = getModifier(args[i]);

                    if (mod == null) {
                        player.sendMessage(M.PREFIX + M.INVALID_MODIFIER.replace("[modifier]", args[i]));
                        return;
                    }
                    else {
                        builder.append(mod);
                    }
                }

                String fullColour = builder.toString();

                currentValueString = configUtils.getCurrentDefaultColour();
                // The only place in the plugin that 'this' is needed - no point in having a message for this (pardon the pun).
                valueString = generalUtils.colouriseMessage(fullColour, "this", false);
                value = fullColour;
                break;
            }

            case "command-name": {
                String cmd = args[2];

                // Make sure no other plugin has this command set.
                for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                    if (p.getDescription().getCommands() != null) {

                        if (p.getDescription().getCommands().containsKey(cmd)) {
                            player.sendMessage(M.PREFIX + M.COMMAND_EXISTS);
                            return;
                        }

                    }
                }

                currentValueString = (String) configUtils.getSetting("command-name");
                value = valueString = cmd;
                break;
            }

            case "force-group-colors": {
                boolean val;

                try {
                    val = Boolean.parseBoolean(args[2]);
                }
                catch (Exception e) {
                    player.sendMessage(M.PREFIX + M.NEEDS_BOOLEAN);
                    return;
                }

                boolean notify = (boolean) configUtils.getSetting("force-group-colors");

                if (val == notify) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = val ? falseVal : trueVal;
                valueString = val ? trueVal : falseVal;
                value = val;
                break;
            }

            case "default-color-enabled": {
                boolean val;

                try {
                    val = Boolean.parseBoolean(args[2]);
                }
                catch (Exception e) {
                    player.sendMessage(M.PREFIX + M.NEEDS_BOOLEAN);
                    return;
                }

                boolean enabled = (boolean) configUtils.getSetting("default-color-enabled");

                if (val == enabled) {
                    player.sendMessage(M.PREFIX + M.ALREADY_SET);
                    return;
                }

                currentValueString = val ? falseVal : trueVal;
                valueString = val ? trueVal : falseVal;
                value = val;
                break;
            }
        }

        player.sendMessage(M.PREFIX + M.IS_CURRENTLY.replace("[setting]", setting).replace("[value]", currentValueString));
        player.sendMessage(M.PREFIX + M.TO_CHANGE.replace("[value]", valueString));
        player.sendMessage(M.PREFIX + M.CONFIRM);

        ChatColor.getPlugin().createConfirmScheduler(player, setting, value);
    }

    public static String getColour(String str) {
        String colour = str.toLowerCase();

        if (colour.equals("default") || GeneralUtils.isCustomColour(colour)) {
            return colour;
        }
        else if (GeneralUtils.isValidHexColour(str)) {
            return '&' + colour;
        }

        List<String> words = Arrays.asList("black", "dark.blue", "green", "dark.aqua", "red", "purple", "gold", "gray", "dark.gray", "blue", "light.green", "aqua", "light.red", "magenta", "yellow", "white");

        if (words.contains(colour)) {
            if (words.indexOf(colour) < 10) {
                return "&" + words.indexOf(colour);
            }

            for (int i = 10; i < words.size(); i++) {
                // The chars representing the colours can just be added to from 'a'.
                char colorChar = 'a';
                colorChar += (i - 10);

                String currentWord = words.get(i);
                if (colour.equals(currentWord)) {
                    return "&" + colorChar;
                }
            }
        }

        List<String> other = Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f");

        if (other.contains(colour)) {
            return "&" + colour;
        }
        else {
            return null;
        }
    }

    public static String getModifier(String str) {
        List<String> mods = Arrays.asList("obfuscated", "bold", "strikethrough", "underlined", "italic");
        String s = str.toLowerCase();

        if (mods.contains(s)) {
            for (int i = 0; i < mods.size(); i++) {
                char modChar = 'k';
                modChar += i;

                String currentMod = mods.get(i);
                if (currentMod.equals(s)) {
                    return "&" + modChar;
                }
            }
        }

        List<String> other = Arrays.asList("k", "l", "m", "n", "o");

        if (other.contains(s)) {
            return "&" + s;
        }

        return null;
    }

}

package me.elite.rtpplugin;

import me.elite.Factions.FactionsPlugin;
import me.elite.Factions.utils.FactionUtilityManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;

import java.util.*;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;


public class RTPPlugin extends JavaPlugin implements CommandExecutor {

    private FactionsPlugin factionsPlugin;
    public Map<String, WorldInfo> worlds = new HashMap<>();

    @Override
    public void onEnable() {
        // Get the factions plugin instance
        factionsPlugin = (FactionsPlugin) Bukkit.getPluginManager().getPlugin("EclipseFactions");
        if (factionsPlugin == null) {
            getLogger().warning("EclipseFactions plugin not found! Corner claims will not be displayed.");
        } else {
            getLogger().info("EclipseFactions plugin loaded successfully!");

            // Delay world loading until after all plugins are loaded (including Multiverse)
            Bukkit.getScheduler().runTaskLater(this, this::loadWorldsFromConfig, 20L); // 1 second delay
        }

        getCommand("rtp").setExecutor(this);
        getCommand("wild").setExecutor(this);
        getServer().getPluginManager().registerEvents(new RTPListener(this), this);
        getLogger().info("RTPPlugin enabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle reload command for admins
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("multiworlds.admin")) {
            loadWorldsFromConfig();
            sender.sendMessage("§aMultiWorlds configuration reloaded! Found " + worlds.size() + " worlds.");
            return true;
        }

        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        // Check if we have any worlds loaded
        if (worlds.isEmpty()) {
            player.sendMessage("§cNo worlds are configured! Please check the server configuration.");
            getLogger().warning("No worlds loaded in RTP plugin. Check EclipseFactions config.yml worlds section.");
            return true;
        }

        // Get GUI configuration
        int guiSize = 27; // default
        String guiTitle = "Choose a world"; // default

        if (factionsPlugin != null) {
            guiSize = factionsPlugin.getConfigManager().getMultiWorldsGuiSize();
            guiTitle = factionsPlugin.getConfigManager().getMultiWorldsGuiTitle();
        }

        Inventory gui = Bukkit.createInventory(null, guiSize, guiTitle);

        // Fill with glass panes
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" "); // Invisible name
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < guiSize; i++) {
            gui.setItem(i, filler);
        }

        // Place worlds in configured slots
        for (String worldKey : worlds.keySet()) {
            WorldInfo worldInfo = worlds.get(worldKey);
            if (worldInfo != null) {
                int slot = getWorldSlot(worldKey);

                // Make sure slot is valid for this GUI size
                if (slot >= 0 && slot < guiSize) {
                    gui.setItem(slot, createWorldItem(
                            worldInfo.getDisplayName(),
                            worldInfo.getWorldName(),
                            worldInfo.getSkullId(),
                            worldInfo.getWorldType()
                    ));
                }
            }
        }

        player.openInventory(gui);
        return true;
    }

    private int getWorldSlot(String worldKey) {
        if (factionsPlugin != null) {
            return factionsPlugin.getConfigManager().getWorldSlot(worldKey);
        }

        // Fallback slot assignment if config not available
        switch (worldKey) {
            case "world": return 10;
            case "world2": return 12;
            case "world_nether": return 14;
            case "world_the_end": return 16;
            default: return -1;
        }
    }

    private List<String> getWorldsInConfigOrder() {
        List<String> orderedWorlds = new ArrayList<>();

        if (factionsPlugin != null) {
            // Get the config section to preserve order
            Set<String> configWorlds = factionsPlugin.getConfigManager().getConfiguredWorlds();
            for (String worldName : configWorlds) {
                if (worlds.containsKey(worldName)) {
                    orderedWorlds.add(worldName);
                }
            }
        }

        // If no config order available, use existing order
        if (orderedWorlds.isEmpty()) {
            orderedWorlds.addAll(worlds.keySet());
        }

        return orderedWorlds;
    }

    private ItemStack createWorldItem(String displayName, String worldKey, String base64Texture, String worldType) {
        List<String> lore = new ArrayList<>();

        // Get world size from config
        String sizeText = "25k x 25k"; // default
        if (factionsPlugin != null) {
            int sizeBlocks = factionsPlugin.getConfigManager().getWorldSizeBlocks(worldKey);
            int sizeKb = sizeBlocks / 1000;
            sizeText = sizeKb + "k x " + sizeKb + "k";
        }

        // Basic world info
        lore.add("");
        lore.add("§c▐ Type: §f" + worldType);
        lore.add("§c▐ Size: §f" + sizeText);
        lore.add("");

        // Add corner claim information if factions plugin is available
        if (factionsPlugin != null) {
            FactionUtilityManager utilityManager = factionsPlugin.getUtilityManager();
            if (utilityManager != null) {
                List<String> cornerInfo = utilityManager.formatCornerClaimsForLore(worldKey);
                lore.addAll(cornerInfo);
                lore.add("");
            }
        }

        lore.add("§7[ CLICK TO TELEPORT ]");

        return createCustomHead(displayName, worldKey, base64Texture, lore);
    }

    private static ItemStack createCustomHead(String displayName, String worldKey, String base64Texture, List<String> loreLines) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setDisplayName(displayName);
        meta.setLocalizedName(worldKey);
        meta.setLore(loreLines);

        // Add texture to head
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);
        profile.getProperties().put("textures", new Property("textures", base64Texture));

        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        head.setItemMeta(meta);
        return head;
    }

    private void loadWorldsFromConfig() {
        worlds.clear();

        if (factionsPlugin == null) {
            getLogger().warning("Cannot load worlds from config - EclipseFactions not found!");
            getLogger().warning("Using fallback world configuration...");
            loadFallbackWorlds();
            return;
        }

        Set<String> configuredWorlds = factionsPlugin.getConfigManager().getConfiguredWorlds();
        if (configuredWorlds.isEmpty()) {
            getLogger().warning("No worlds configured in EclipseFactions config.yml!");
            getLogger().warning("Using fallback world configuration...");
            loadFallbackWorlds();
            return;
        }

        for (String worldName : configuredWorlds) {
            WorldConfig worldConfig = new WorldConfig(worldName, factionsPlugin);

            // Check if world exists
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                getLogger().warning("World '" + worldName + "' not found on server!");
                continue;
            }

            // Check and fix world border
            if (!worldConfig.isWorldBorderCorrect()) {
                getLogger().info("Fixing world border for: " + worldName);
                worldConfig.fixWorldBorder();
                getLogger().info("World border corrected: " + worldName + " (size: " + worldConfig.getSizeBlocks() + " blocks)");
            } else {
                getLogger().info("World border OK: " + worldName + " (size: " + worldConfig.getSizeBlocks() + " blocks)");
            }

            // Convert to WorldInfo for compatibility
            WorldInfo worldInfo = new WorldInfo(
                    worldConfig.getWorldName(),
                    worldConfig.getDisplayName(),
                    worldConfig.getWorldType(),
                    worldConfig.getSkullTexture()
            );

            worlds.put(worldName, worldInfo);
        }

        if (worlds.isEmpty()) {
            getLogger().warning("No valid worlds loaded from config!");
            getLogger().warning("Using fallback world configuration...");
            loadFallbackWorlds();
        } else {
            getLogger().info("Loaded " + worlds.size() + " worlds from config");
        }
    }

    private void loadFallbackWorlds() {
        // Load basic worlds that exist on the server
        String[][] fallbackWorlds = {
                {"world", "§e§l☀ §a§lEarth §e§l☀", "Overworld", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUwMTU0NzBlMjg2ZTRlZDc3YTAzODc2Y2JiZmQ3YjNkMzU4YTYwNjA2YjQ0ZGQyYzRiYzhkOGU5YzM3M2VlOSJ9fX0="},
                {"world2", "§4§l🔥 F§c§lire P§6§llanet 🔥", "Overworld", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGQ4NTlmN2IzY2RmZGFkNDcxODI4ODRlMTI3ZjQ2MWZlOGY5ZmM1MmY3ZDE1MDQyN2MxMTcwNzliMDkyNGUzIn19fQ=="},
                {"world_nether", "§4§l☠ Hell ☠", "Nether", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdlMGJmNjI3NTg4MjZhNzc2ODA4YzFkNzViNmM2MGY3MjAzZjA4YTk2ODE0NDgzZmMwZDhkYzBjNTMyZTBjNSJ9fX0="},
                {"world_the_end", "§d§l🗡 §5§lEnd §d§l🗡", "The End", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzZjYWM1OWIyYWFlNDg5YWEwNjg3YjVkODAyYjI1NTVlYjE0YTQwYmQ2MmIyMWViMTE2ZmE1NjljZGI3NTYifX19"}
        };

        for (String[] worldData : fallbackWorlds) {
            String worldName = worldData[0];
            World world = Bukkit.getWorld(worldName);

            if (world != null) {
                WorldInfo worldInfo = new WorldInfo(worldName, worldData[1], worldData[2], worldData[3]);
                worlds.put(worldName, worldInfo);
                getLogger().info("Loaded fallback world: " + worldName);
            }
        }

        getLogger().info("Loaded " + worlds.size() + " fallback worlds");
    }
}


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

import java.util.Random;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;


public class RTPPlugin extends JavaPlugin implements CommandExecutor {

    private FactionsPlugin factionsPlugin;

    @Override
    public void onEnable() {
        // Get the factions plugin instance
        factionsPlugin = (FactionsPlugin) Bukkit.getPluginManager().getPlugin("Factions");
        if (factionsPlugin == null) {
            getLogger().warning("Factions plugin not found! Corner claims will not be displayed.");
        }

        getCommand("rtp").setExecutor(this);
        getCommand("wild").setExecutor(this);
        getServer().getPluginManager().registerEvents(new RTPListener(this), this);
        getLogger().info("RTPPlugin enabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        Inventory gui = Bukkit.createInventory(null, 9, "Choose a world");

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" "); // Invisible name
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, filler);
        }

        // Create world items with corner claim information
        gui.setItem(1, createWorldItem(
                "§e§l☀ §a§lEarth §e§l☀",
                "world",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUwMTU0NzBlMjg2ZTRlZDc3YTAzODc2Y2JiZmQ3YjNkMzU4YTYwNjA2YjQ0NmQyYzRiYzhkOGU5YzM3M2VlOSJ9fX0=",
                "Overworld"
        ));

        gui.setItem(3, createWorldItem(
                "§4§l\uD83D\uDD25 F§c§lire P§6§llanet \uD83D\uDD25",
                "world2",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGQ4NTlmN2IzY2RmZGFkNDcxODI4ODRlMTI3ZjQ2MWZlOGY5ZmM1MmY3ZDE1MDQyN2MxMTcwNzliMDkyNGUzIn19fQ==",
                "Overworld"
        ));

        gui.setItem(5, createWorldItem(
                "§4§l☠ Hell ☠",
                "world_nether",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdlMGJmNjI3NTg4MjZhNzc2ODA4YzFkNzViNmM2MGY3MjAzZjA4YTk2ODE0NDgzZmMwZDhkYzBjNTMyZTBjNSJ9fX0=",
                "Nether"
        ));

        gui.setItem(7, createWorldItem(
                "§d§l\uD83D\uDDE1 §5§lEnd §d§l\uD83D\uDDE1",
                "world_the_end",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzZjYWM1OWIyYWFlNDg5YWEwNjg3YjVkODAyYjI1NTVlYjE0YTQwYmQ2MmIyMWViMTE2ZmE1NjljZGI3NTYifX19",
                "The End"
        ));

        player.openInventory(gui);
        return true;
    }

    private ItemStack createWorldItem(String displayName, String worldKey, String base64Texture, String worldType) {
        List<String> lore = new ArrayList<>();

        // Basic world info
        lore.add("");
        lore.add("§c▐ Type: §f" + worldType);
        lore.add("§c▐ Size: §f25k x 25k");
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
}


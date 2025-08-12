package me.elite.rtpplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import java.util.Random;

public class RTPListener implements Listener {

    private final RTPPlugin plugin;
    private final Random rand = new Random();

    // Store players currently waiting to teleport and their initial locations
    private final Map<UUID, Location> teleportingPlayers = new ConcurrentHashMap<>();

    // Cooldown: player UUID + worldName -> cooldown expiry (System.currentTimeMillis())
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public RTPListener(RTPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("Choose a world")) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        String worldKey = clicked.getItemMeta().getLocalizedName();

        // Check cooldown
        String cooldownKey = player.getUniqueId().toString() + "-" + worldKey;
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(cooldownKey)) {
            long expires = cooldowns.get(cooldownKey);
            if (now < expires) {
                long secondsLeft = (expires - now) / 1000;
                player.sendMessage("§cYou must wait " + secondsLeft + " seconds before teleporting to this world again.");
                player.closeInventory();
                return;
            }
        }

        World world = Bukkit.getWorld(worldKey);
        if (world == null) {
            player.sendMessage("§cThat world doesn't exist!");
            player.closeInventory();
            return;
        }

        player.sendMessage("§aTeleporting you in 3 seconds. Don't move!");
        player.closeInventory();

        // Store player's current location so we can detect movement
        Location initialLoc = player.getLocation();
        teleportingPlayers.put(player.getUniqueId(), initialLoc);

        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if player moved
                Location beforeTeleportLoc = teleportingPlayers.get(player.getUniqueId());
                if (beforeTeleportLoc == null || !locationsAreClose(player.getLocation(), beforeTeleportLoc)) {
                    player.sendMessage("§cTeleport cancelled because you moved!");
                    teleportingPlayers.remove(player.getUniqueId());
                    return;
                }

                // Teleport player
                Location randomLoc = getRandomSafeLocation(world);
                if (randomLoc != null) {
                    player.teleport(randomLoc);
                    player.sendMessage("§aYou have been teleported!");

                    // Set cooldown for 20 seconds
                    cooldowns.put(cooldownKey, System.currentTimeMillis() + 20_000);
                } else {
                    player.sendMessage("§cFailed to find a safe location.");
                }

                teleportingPlayers.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 60L);
    }

    // Helper to check if player location changed significantly
    private boolean locationsAreClose(Location a, Location b) {
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.distanceSquared(b) < 0.01; // basically no movement
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (teleportingPlayers.containsKey(playerId)) {
            Location initial = teleportingPlayers.get(playerId);
            if (!locationsAreClose(event.getTo(), initial)) {
                teleportingPlayers.remove(playerId);
                event.getPlayer().sendMessage("§cTeleport cancelled because you moved!");
            }
        }
    }

    private Location getRandomSafeLocation(World world) {
        int x = rand.nextInt(3000) - 1500;
        int z = rand.nextInt(3000) - 1500;
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }
}
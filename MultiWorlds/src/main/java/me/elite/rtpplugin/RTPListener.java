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
import java.util.List;
import java.util.ArrayList;

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
        // Check if this is our GUI (support custom titles from config)
        String title = event.getView().getTitle();
        if (!title.equals("Choose a world") && !title.contains("world")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();

        // Check if clicked item is a glass pane (filler item) - ignore it
        if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return; // Don't do anything, don't send error message
        }

        // Check if item has metadata (world items should have localizedName)
        if (clicked.getItemMeta() == null || clicked.getItemMeta().getLocalizedName() == null || clicked.getItemMeta().getLocalizedName().isEmpty()) {
            return; // Not a world item, ignore silently
        }

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
        // Get world border info
        org.bukkit.WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize() / 2.0; // Half size for radius
        org.bukkit.Location borderCenter = border.getCenter();

        int maxAttempts = 50; // Prevent infinite loops

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Generate random coordinates within world border
            double randomX = (rand.nextDouble() * 2 - 1) * (borderSize - 50); // 50 block buffer from border
            double randomZ = (rand.nextDouble() * 2 - 1) * (borderSize - 50);

            int x = (int) (borderCenter.getX() + randomX);
            int z = (int) (borderCenter.getZ() + randomZ);

            Location safeLocation = findSafeLocationAt(world, x, z);
            if (safeLocation != null) {
                return safeLocation;
            }
        }

        // Fallback to spawn if no safe location found
        return world.getSpawnLocation();
    }

    private Location findSafeLocationAt(World world, int x, int z) {
        // Handle different world environments
        switch (world.getEnvironment()) {
            case NETHER:
                return findSafeNetherLocation(world, x, z);
            case THE_END:
                return findSafeEndLocation(world, x, z);
            default:
                return findSafeOverworldLocation(world, x, z);
        }
    }

    private Location findSafeOverworldLocation(World world, int x, int z) {
        // Start from top and work down to find safe spot
        for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
            org.bukkit.block.Block blockFeet = world.getBlockAt(x, y, z);      // Player's feet
            org.bukkit.block.Block blockHead = world.getBlockAt(x, y + 1, z);  // Player's head
            org.bukkit.block.Block blockBelow = world.getBlockAt(x, y - 1, z); // Block below feet

            // Check if this is a good spot
            if (isSafeOverworldBlock(blockBelow) &&
                    isAirOrPassable(blockFeet) &&
                    isAirOrPassable(blockHead) &&
                    !isWater(blockBelow) &&
                    !isWater(blockFeet) &&    // Player's feet can't be in water
                    !isWater(blockHead) &&    // Player's head can't be in water
                    !isLava(blockBelow) &&
                    !isLava(blockFeet) &&
                    !isLava(blockHead) &&
                    isAboveSeaLevel(world, y)) {  // Additional check for sea level

                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }

        return null; // No safe location found at this x,z
    }

    private boolean isAboveSeaLevel(World world, int y) {
        // Most overworld generates sea level at y=63, so we want to be above y=65 to be safe
        return y >= 65;
    }

    private Location findSafeNetherLocation(World world, int x, int z) {
        // In nether, search from y=10 to y=120 (avoid lava lakes and bedrock roof)
        for (int y = 120; y >= 10; y--) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            org.bukkit.block.Block below = world.getBlockAt(x, y - 1, z);
            org.bukkit.block.Block above = world.getBlockAt(x, y + 1, z);

            // Check if this is a good spot in nether
            if (isSafeNetherBlock(below) &&
                    isAirOrPassable(block) &&
                    isAirOrPassable(above) &&
                    !isLava(below) &&
                    !isLava(block) &&
                    !isLava(above)) {

                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }

        return null;
    }

    private Location findSafeEndLocation(World world, int x, int z) {
        // In the end, find solid end stone or similar
        for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
            org.bukkit.block.Block block = world.getBlockAt(x, y, z);
            org.bukkit.block.Block below = world.getBlockAt(x, y - 1, z);
            org.bukkit.block.Block above = world.getBlockAt(x, y + 1, z);

            if (isSafeEndBlock(below) &&
                    isAirOrPassable(block) &&
                    isAirOrPassable(above)) {

                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }

        return null;
    }

    private boolean isSafeOverworldBlock(org.bukkit.block.Block block) {
        Material type = block.getType();

        // Block must be solid and safe
        if (!type.isSolid() ||
                type == Material.LAVA ||
                type == Material.WATER ||
                type == Material.CACTUS ||
                type == Material.MAGMA_BLOCK ||
                type.name().contains("PRESSURE_PLATE") ||
                type.name().contains("TRIPWIRE")) {
            return false;
        }

        // Additional check: make sure this isn't underwater terrain
        // Check blocks around to ensure we're not on ocean floor
        World world = block.getWorld();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        // Check a few blocks above to make sure we're not underwater
        for (int checkY = y + 1; checkY <= y + 5; checkY++) {
            org.bukkit.block.Block above = world.getBlockAt(x, checkY, z);
            if (isWater(above)) {
                return false; // This location is underwater
            }
        }

        return true;
    }

    private boolean isSafeNetherBlock(org.bukkit.block.Block block) {
        Material type = block.getType();
        return type.isSolid() &&
                type != Material.LAVA &&
                type != Material.MAGMA_BLOCK &&
                type != Material.FIRE &&
                !type.name().contains("PRESSURE_PLATE");
    }

    private boolean isSafeEndBlock(org.bukkit.block.Block block) {
        Material type = block.getType();
        return type.isSolid() &&
                type != Material.LAVA &&
                !type.name().contains("PRESSURE_PLATE");
    }

    private boolean isAirOrPassable(org.bukkit.block.Block block) {
        Material type = block.getType();
        return type == Material.AIR ||
                type == Material.CAVE_AIR ||
                type == Material.VOID_AIR ||
                !type.isSolid();
    }

    private boolean isWater(org.bukkit.block.Block block) {
        Material type = block.getType();
        return type == Material.WATER;
    }

    private boolean isLava(org.bukkit.block.Block block) {
        Material type = block.getType();
        return type == Material.LAVA;
    }
}
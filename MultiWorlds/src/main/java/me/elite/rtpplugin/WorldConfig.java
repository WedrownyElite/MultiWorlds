package me.elite.rtpplugin;

import me.elite.Factions.FactionsPlugin;
import me.elite.Factions.territory.ChunkCoord;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class WorldConfig {
    private final String worldName;
    private final String displayName;
    private final String worldType;
    private final String itemType;
    private final String skullTexture;
    private final int sizeBlocks;
    private final int sizeChunks;

    public WorldConfig(String worldName, FactionsPlugin factionsPlugin) {
        this.worldName = worldName;
        this.displayName = factionsPlugin.getConfigManager().getWorldDisplayName(worldName);
        this.worldType = factionsPlugin.getConfigManager().getWorldType(worldName);
        this.itemType = factionsPlugin.getConfigManager().getWorldItemType(worldName);
        this.skullTexture = factionsPlugin.getConfigManager().getWorldSkullTexture(worldName);
        this.sizeBlocks = factionsPlugin.getConfigManager().getWorldSizeBlocks(worldName);
        this.sizeChunks = factionsPlugin.getConfigManager().getWorldSizeChunks(worldName);
    }

    // Getters
    public String getWorldName() { return worldName; }
    public String getDisplayName() { return displayName; }
    public String getWorldType() { return worldType; }
    public String getItemType() { return itemType; }
    public String getSkullTexture() { return skullTexture; }
    public int getSizeBlocks() { return sizeBlocks; }
    public int getSizeChunks() { return sizeChunks; }

    public ChunkCoord[] getCornerChunks() {
        int halfSize = sizeChunks / 2;
        return new ChunkCoord[] {
                new ChunkCoord(-halfSize, -halfSize),     // Bottom-left (-, -)
                new ChunkCoord(-halfSize, halfSize - 1),  // Top-left (-, +)
                new ChunkCoord(halfSize - 1, halfSize - 1), // Top-right (+, +)
                new ChunkCoord(halfSize - 1, -halfSize)   // Bottom-right (+, -)
        };
    }

    public boolean isWorldBorderCorrect() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;

        org.bukkit.WorldBorder border = world.getWorldBorder();

        // Check if centered at 0,0
        if (border.getCenter().getX() != 0.5 || border.getCenter().getZ() != 0.5) {
            return false;
        }

        // Check if size matches (allowing 1 block tolerance for chunk alignment)
        double expectedSize = sizeBlocks;
        double actualSize = border.getSize();
        return Math.abs(actualSize - expectedSize) <= 16; // Within 1 chunk
    }

    public void fixWorldBorder() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(sizeBlocks);
    }
}
package me.elite.rtpplugin;

public class WorldInfo {
    private final String worldName;
    private final String displayName;
    private final String worldType;
    private final String skullId;

    public WorldInfo(String worldName, String displayName, String worldType, String skullId) {
        this.worldName = worldName;
        this.displayName = displayName;
        this.worldType = worldType;
        this.skullId = skullId;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getWorldType() {
        return worldType;
    }

    public String getSkullId() {
        return skullId;
    }
}

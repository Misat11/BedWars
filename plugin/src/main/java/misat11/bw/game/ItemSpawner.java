package misat11.bw.game;

import misat11.bw.api.Team;
import org.bukkit.Location;

public class ItemSpawner implements misat11.bw.api.ItemSpawner {
    public Location loc;
    public ItemSpawnerType type;
    public String customName;
    public double startLevel;
    public double currentLevel;
    public boolean hologramEnabled;
    public Team team;

    public ItemSpawner(Location loc, ItemSpawnerType type, String customName, boolean hologramEnabled, double startLevel, Team team) {
        this.loc = loc;
        this.type = type;
        this.customName = customName;
        this.currentLevel = this.startLevel = startLevel;
        this.hologramEnabled = hologramEnabled;
        this.team = team;
    }

    @Override
    public misat11.bw.api.ItemSpawnerType getItemSpawnerType() {
        return type;
    }

    @Override
    public Location getLocation() {
        return loc;
    }

    @Override
    public boolean hasCustomName() {
        return customName != null;
    }

    @Override
    public String getCustomName() {
        return customName;
    }

    @Override
    public double getStartLevel() {
        return startLevel;
    }

    @Override
    public double getCurrentLevel() {
        return currentLevel;
    }

    @Override
    public boolean getHologramEnabled() {
        return hologramEnabled;
    }

    @Override
    public void setCurrentLevel(double level) {
        currentLevel = level;
    }

    @Override
    public Team getTeam() {
        return team;
    }

    @Override
    public void setTeam(Team team) {
        this.team = team;
    }
}

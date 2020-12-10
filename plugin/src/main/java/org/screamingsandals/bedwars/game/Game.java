package org.screamingsandals.bedwars.game;

import com.onarandombox.MultiverseCore.api.Core;
import io.papermc.lib.PaperLib;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.ArenaTime;
import org.screamingsandals.bedwars.api.Region;
import org.screamingsandals.bedwars.api.RunningTeam;
import org.screamingsandals.bedwars.api.boss.BossBar;
import org.screamingsandals.bedwars.api.boss.BossBar19;
import org.screamingsandals.bedwars.api.boss.StatusBar;
import org.screamingsandals.bedwars.api.config.ConfigurationContainer;
import org.screamingsandals.bedwars.api.events.*;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.api.special.SpecialItem;
import org.screamingsandals.bedwars.api.upgrades.UpgradeRegistry;
import org.screamingsandals.bedwars.api.upgrades.UpgradeStorage;
import org.screamingsandals.bedwars.api.utils.DelayFactory;
import org.screamingsandals.bedwars.boss.BossBarSelector;
import org.screamingsandals.bedwars.boss.XPBar;
import org.screamingsandals.bedwars.commands.AdminCommand;
import org.screamingsandals.bedwars.commands.StatsCommand;
import org.screamingsandals.bedwars.config.GameConfigurationContainer;
import org.screamingsandals.bedwars.inventories.TeamSelectorInventory;
import org.screamingsandals.bedwars.lib.debug.Debug;
import org.screamingsandals.bedwars.lib.nms.entity.EntityUtils;
import org.screamingsandals.bedwars.lib.nms.holograms.Hologram;
import org.screamingsandals.bedwars.lib.signmanager.SignBlock;
import org.screamingsandals.bedwars.listener.Player116ListenerUtils;
import org.screamingsandals.bedwars.region.FlatteningRegion;
import org.screamingsandals.bedwars.region.LegacyRegion;
import org.screamingsandals.bedwars.scoreboard.ScreamingBoard;
import org.screamingsandals.bedwars.statistics.PlayerStatistic;
import org.screamingsandals.bedwars.utils.*;
import org.screamingsandals.simpleinventories.utils.MaterialSearchEngine;
import org.screamingsandals.simpleinventories.utils.StackParser;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.screamingsandals.bedwars.lib.lang.I.*;

public class Game implements org.screamingsandals.bedwars.api.game.Game {
    public boolean gameStartItem;
    private String name;
    private Location pos1;
    private Location pos2;
    private Location lobbySpawn;
    private Location specSpawn;
    private final List<Team> teams = new ArrayList<>();
    private final List<ItemSpawner> spawners = new ArrayList<>();
    private final Map<Player, RespawnProtection> respawnProtectionMap = new HashMap<>();
    private int pauseCountdown;
    private int gameTime;
    private int minPlayers;
    private final List<GamePlayer> players = new ArrayList<>();
    private World world;
    private List<GameStore> gameStore = new ArrayList<>();
    private ArenaTime arenaTime = ArenaTime.WORLD;
    private WeatherType arenaWeather = null;
    private BarColor lobbyBossBarColor = null;
    private BarColor gameBossBarColor = null;
    private String customPrefix = null;
    private boolean preServerRestart = false;

    // STATUS
    private GameStatus previousStatus = GameStatus.DISABLED;
    private GameStatus status = GameStatus.DISABLED;
    private GameStatus afterRebuild = GameStatus.WAITING;
    private int countdown = -1, previousCountdown = -1;
    private int calculatedMaxPlayers;
    private BukkitTask task;
    private final List<CurrentTeam> teamsInGame = new ArrayList<>();
    private final Region region = Main.isLegacy() ? new LegacyRegion() : new FlatteningRegion();
    private TeamSelectorInventory teamSelectorInventory;
    private final Scoreboard gameScoreboard = Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false )  ?
            null : Bukkit.getScoreboardManager().getNewScoreboard();
    private StatusBar statusbar;
    private final Map<Location, ItemStack[]> usedChests = new HashMap<>();
    private final List<SpecialItem> activeSpecialItems = new ArrayList<>();
    private final List<DelayFactory> activeDelays = new ArrayList<>();
    private final List<Hologram> createdHolograms = new ArrayList<>();
    private final Map<ItemSpawner, Hologram> countdownHolograms = new HashMap<>();
    private final Map<GamePlayer, Inventory> fakeEnderChests = new HashMap<>();
    private int postGameWaiting = 3;
    private ScreamingBoard experimentalBoard = null;

    @Getter
    private final GameConfigurationContainer configurationContainer = new GameConfigurationContainer();

    private boolean preparing = false;

    private Game() {

    }

    public static Game loadGame(File file) {
        return loadGame(file, true);
    }

    public static Game loadGame(File file, boolean firstAttempt) {
        try {
            if (!file.exists()) {
                return null;
            }

            final FileConfiguration configMap = new YamlConfiguration();
            try {
                configMap.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                e.printStackTrace();
                return null;
            }

            final Game game = new Game();
            game.name = configMap.getString("name");
            game.pauseCountdown = configMap.getInt("pauseCountdown");
            game.gameTime = configMap.getInt("gameTime");

            String worldName = configMap.getString("world");
            game.world = Bukkit.getWorld(worldName);

            if (game.world == null) {
                if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                    Bukkit.getConsoleSender().sendMessage("§c[B§fW] §cWorld " + worldName
                            + " was not found, but we found Multiverse-Core, so we will try to load this world.");

                    Core multiverse = (Core) Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                    if (multiverse != null && multiverse.getMVWorldManager().loadWorld(worldName)) {
                        Bukkit.getConsoleSender().sendMessage("§c[B§fW] §aWorld " + worldName
                                + " was succesfully loaded with Multiverse-Core, continue in arena loading.");

                        game.world = Bukkit.getWorld(worldName);
                    } else {
                        Bukkit.getConsoleSender().sendMessage("§c[B§fW] §cArena " + game.name
                                + " can't be loaded, because world " + worldName + " is missing!");
                        return null;
                    }
                } else if (firstAttempt) {
                    Bukkit.getConsoleSender().sendMessage(
                            "§c[B§fW] §eArena " + game.name + " can't be loaded, because world " + worldName + " is missing! We will try it again after all plugins will be loaded!");
                    Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> loadGame(file, false), 10L);
                    return null;
                } else {
                    Bukkit.getConsoleSender().sendMessage(
                            "§c[B§fW] §cArena " + game.name + " can't be loaded, because world " + worldName + " is missing!");
                    return null;
                }
            }

            if (Main.getVersionNumber() >= 115) {
                game.world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            }

            game.pos1 = MiscUtils.readLocationFromString(game.world, configMap.getString("pos1"));
            game.pos2 = MiscUtils.readLocationFromString(game.world, configMap.getString("pos2"));

            if (Main.getConfigurator().config.getBoolean("prevent-spawning-mobs", true)) {
                for (Entity e : game.world.getEntitiesByClass(Monster.class)) {
                    if (GameCreator.isInArea(e.getLocation(), game.pos1, game.pos2)) {
                        PaperLib.getChunkAtAsync(e.getLocation())
                                .thenAccept(chunk -> e.remove());
                    }
                }
            }

            game.specSpawn = MiscUtils.readLocationFromString(game.world, configMap.getString("specSpawn"));
            String spawnWorld = configMap.getString("lobbySpawnWorld");
            World lobbySpawnWorld = Bukkit.getWorld(spawnWorld);
            if (lobbySpawnWorld == null) {
                if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
                    Bukkit.getConsoleSender().sendMessage("§c[B§fW] §cWorld " + spawnWorld
                            + " was not found, but we found Multiverse-Core, so we will try to load this world.");

                    Core multiverse = (Core) Bukkit.getPluginManager().getPlugin("Multiverse-Core");
                    if (multiverse != null && multiverse.getMVWorldManager().loadWorld(spawnWorld)) {
                        Bukkit.getConsoleSender().sendMessage("§c[B§fW] §aWorld " + spawnWorld
                                + " was succesfully loaded with Multiverse-Core, continue in arena loading.");

                        lobbySpawnWorld = Bukkit.getWorld(spawnWorld);
                    } else {
                        Bukkit.getConsoleSender().sendMessage("§c[B§fW] §cArena " + game.name
                                + " can't be loaded, because world " + spawnWorld + " is missing!");
                        return null;
                    }
                } else if (firstAttempt) {
                    Bukkit.getConsoleSender().sendMessage(
                            "§c[B§fW] §eArena " + game.name + " can't be loaded, because world " + worldName + " is missing! We will try it again after all plugins will be loaded!");
                    Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> loadGame(file, false), 10L);
                    return null;
                } else {
                    Bukkit.getConsoleSender().sendMessage(
                            "§c[B§fW] §cArena " + game.name + " can't be loaded, because world " + spawnWorld + " is missing!");
                    return null;
                }
            }
            game.lobbySpawn = MiscUtils.readLocationFromString(lobbySpawnWorld, configMap.getString("lobbySpawn"));
            game.minPlayers = configMap.getInt("minPlayers", 2);
            if (configMap.isSet("teams")) {
                for (String teamN : configMap.getConfigurationSection("teams").getKeys(false)) {
                    ConfigurationSection team = configMap.getConfigurationSection("teams").getConfigurationSection(teamN);
                    Team t = new Team();
                    t.newColor = team.getBoolean("isNewColor", false);
                    t.color = TeamColor.valueOf(MiscUtils.convertColorToNewFormat(team.getString("color"), t));
                    t.name = teamN;
                    t.bed = MiscUtils.readLocationFromString(game.world, team.getString("bed"));
                    t.maxPlayers = team.getInt("maxPlayers");
                    t.spawn = MiscUtils.readLocationFromString(game.world, team.getString("spawn"));
                    t.game = game;

                    t.newColor = true;
                    game.teams.add(t);
                }
            }
            if (configMap.isSet("spawners")) {
                List<Map<String, Object>> spawners = (List<Map<String, Object>>) configMap.getList("spawners");
                for (Map<String, Object> spawner : spawners) {
                    ItemSpawner sa = new ItemSpawner(
                            MiscUtils.readLocationFromString(game.world, (String) spawner.get("location")),
                            Main.getSpawnerType(((String) spawner.get("type")).toLowerCase()),
                            (String) spawner.get("customName"), ((Boolean) spawner.getOrDefault("hologramEnabled", true)),
                            ((Number) spawner.getOrDefault("startLevel", 1)).doubleValue(),
                            game.getTeamFromName((String) spawner.get("team")),
                            (int) spawner.getOrDefault("maxSpawnedResources", -1),
                            (Boolean) spawner.getOrDefault("floatingEnabled", false));
                    game.spawners.add(sa);
                }
            }
            if (configMap.isSet("stores")) {
                List<Object> stores = (List<Object>) configMap.getList("stores");
                for (Object store : stores) {
                    if (store instanceof Map) {
                        Map<String, String> map = (Map<String, String>) store;
                        game.gameStore.add(new GameStore(MiscUtils.readLocationFromString(game.world, map.get("loc")),
                                map.get("shop"), "true".equals(map.getOrDefault("parent", "true")),
                                EntityType.valueOf(map.getOrDefault("type", "VILLAGER").toUpperCase()),
                                map.getOrDefault("name", ""), map.containsKey("name"), "true".equals(map.getOrDefault("isBaby", "false")), map.get("skin")));
                    } else if (store instanceof String) {
                        game.gameStore.add(new GameStore(MiscUtils.readLocationFromString(game.world, (String) store), null,
                                true, EntityType.VILLAGER, "", false, false, null));
                    }
                }
            }

            ConfigurationSection constants = configMap.getConfigurationSection("constant");
            if (constants != null) {
                Set<String> keys = constants.getKeys(false);

                for (String key : keys) {
                    game.configurationContainer.update(key, constants.get(key));
                }
            }

            game.arenaTime = ArenaTime.valueOf(configMap.getString("arenaTime", ArenaTime.WORLD.name()).toUpperCase());
            game.arenaWeather = loadWeather(configMap.getString("arenaWeather", "default").toUpperCase());

            game.postGameWaiting = configMap.getInt("postGameWaiting", 3);
            game.customPrefix = configMap.getString("customPrefix", null);

            try {
                game.lobbyBossBarColor = loadBossBarColor(
                        configMap.getString("lobbyBossBarColor", "default").toUpperCase());
                game.gameBossBarColor = loadBossBarColor(configMap.getString("gameBossBarColor", "default").toUpperCase());
            } catch (Throwable t) {
                // We're using 1.8
            }

            Main.addGame(game);
            game.start();
            Bukkit.getConsoleSender().sendMessage("§c[B§fW] §aArena §f" + game.name + "§a loaded!");
            return game;
        } catch (Throwable throwable) {
            Debug.warn("Something went wrong while loading arena file " + file.getName() + ". Please report this to our Discord or GitHub!", true);
            throwable.printStackTrace();
            return null;
        }
    }

    public void removeEntityAsync(Entity e) {
        if (GameCreator.isInArea(e.getLocation(), pos1, pos2)) {
            PaperLib.getChunkAtAsync(e.getLocation())
                    .thenAccept(chunk -> e.remove());
        }
    }


    public static WeatherType loadWeather(String weather) {
        try {
            return WeatherType.valueOf(weather);
        } catch (Exception e) {
            return null;
        }
    }

    public static BarColor loadBossBarColor(String color) {
        try {
            return BarColor.valueOf(color);
        } catch (Exception e) {
            return null;
        }
    }

    public static Game createGame(String name) {
        Game game = new Game();
        game.name = name;
        game.pauseCountdown = 60;
        game.gameTime = 3600;
        game.minPlayers = 2;

        return game;
    }

    public static String bedExistString() {
        return Main.getConfigurator().config.getString("scoreboard.bedExists");
    }

    public static String bedLostString() {
        return Main.getConfigurator().config.getString("scoreboard.bedLost");
    }

    public static String anchorEmptyString() {
        return Main.getConfigurator().config.getString("scoreboard.anchorEmpty");
    }

    public static boolean isBungeeEnabled() {
        return Main.getConfigurator().config.getBoolean("bungee.enabled");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        if (this.world == null) {
            this.world = world;
        }
    }

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(Location lobbySpawn) {
        this.lobbySpawn = lobbySpawn;
    }

    public int getPauseCountdown() {
        return pauseCountdown;
    }

    public void setPauseCountdown(int pauseCountdown) {
        this.pauseCountdown = pauseCountdown;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public boolean checkMinPlayers() {
        return players.size() >= getMinPlayers();
    }

    public int countPlayers() {
        return this.players.size();
    }

    public int countSpectators() {
        return (int) this.players.stream().filter(t -> t.isSpectator && getPlayerTeam(t) == null).count();
    }

    public int countSpectating() {
        return (int) this.players.stream().filter(t -> t.isSpectator).count();
    }

    public int countRespawnable() {
        return (int) this.players.stream().filter(t -> getPlayerTeam(t) != null).count();
    }

    public int countAlive() {
        return (int) this.players.stream().filter(t -> !t.isSpectator).count();
    }

    @Override
    public List<org.screamingsandals.bedwars.api.game.GameStore> getGameStores() {
        return new ArrayList<>(gameStore);
    }

    public void setGameStores(List<GameStore> gameStore) {
        this.gameStore = gameStore;
    }

    public List<GameStore> getGameStoreList() {
        return gameStore;
    }

    public Location getSpecSpawn() {
        return specSpawn;
    }

    public void setSpecSpawn(Location specSpawn) {
        this.specSpawn = specSpawn;
    }

    public int getGameTime() {
        return gameTime;
    }

    public void setGameTime(int gameTime) {
        this.gameTime = gameTime;
    }

    @Override
    public org.screamingsandals.bedwars.api.Team getTeamFromName(String name) {
        Team team = null;
        for (Team t : getTeams()) {
            if (t.getName().equalsIgnoreCase(name)) {
                team = t;
            }
        }
        return team;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public List<ItemSpawner> getSpawners() {
        return spawners;
    }

    public TeamSelectorInventory getTeamSelectorInventory() {
        return teamSelectorInventory;
    }

    public boolean isBlockAddedDuringGame(Location loc) {
        return status == GameStatus.RUNNING && region.isBlockAddedDuringGame(loc);
    }

    public boolean blockPlace(GamePlayer player, Block block, BlockState replaced, ItemStack itemInHand) {
        if (status != GameStatus.RUNNING) {
            return false; // ?
        }
        if (player.isSpectator) {
            return false;
        }
        if (Main.isFarmBlock(block.getType())) {
            return true;
        }
        if (!GameCreator.isInArea(block.getLocation(), pos1, pos2)) {
            return false;
        }

        BedwarsPlayerBuildBlock event = new BedwarsPlayerBuildBlock(this, player.player, getPlayerTeam(player), block,
                itemInHand, replaced);
        Main.getInstance().getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        if (replaced.getType() != Material.AIR) {
            if (region.isBlockAddedDuringGame(replaced.getLocation())) {
                return true;
            } else if (Main.isBreakableBlock(replaced.getType()) || region.isLiquid(replaced.getType())) {
                region.putOriginalBlock(block.getLocation(), replaced);
            } else {
                return false;
            }
        }
        region.addBuiltDuringGame(block.getLocation());

        return true;
    }

    public boolean blockBreak(GamePlayer player, Block block, BlockBreakEvent event) {
        if (status != GameStatus.RUNNING) {
            return false; // ?
        }
        if (player.isSpectator) {
            return false;
        }
        if (Main.isFarmBlock(block.getType())) {
            return true;
        }
        if (!GameCreator.isInArea(block.getLocation(), pos1, pos2)) {
            return false;
        }

        final BedwarsPlayerBreakBlock breakEvent = new BedwarsPlayerBreakBlock(this, player.player, getPlayerTeam(player),
                block);
        Main.getInstance().getServer().getPluginManager().callEvent(breakEvent);

        if (breakEvent.isCancelled()) {
            return false;
        }

        if (region.isBlockAddedDuringGame(block.getLocation())) {
            region.removeBlockBuiltDuringGame(block.getLocation());

            if (block.getType() == Material.ENDER_CHEST) {
                CurrentTeam team = getTeamOfChest(block);
                if (team != null) {
                    team.removeTeamChest(block);
                    String message = i18nc("team_chest_broken", customPrefix);
                    for (GamePlayer gp : team.players) {
                        gp.player.sendMessage(message);
                    }

                    if (breakEvent.isDrops()) {
                        event.setDropItems(false);
                        player.player.getInventory().addItem(new ItemStack(Material.ENDER_CHEST));
                    }
                }
            }

            if (!breakEvent.isDrops()) {
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    block.setType(Material.AIR);
                }
            }
            return true;
        }

        Location loc = block.getLocation();
        if (region.isBedBlock(block.getState())) {
            if (!region.isBedHead(block.getState())) {
                loc = region.getBedNeighbor(block).getLocation();
            }
        }
        if (isTargetBlock(loc)) {
            if (region.isBedBlock(block.getState())) {
                if (getPlayerTeam(player).teamInfo.bed.equals(loc)) {
                    return false;
                }
                bedDestroyed(loc, player.player, true, false, false);
                region.putOriginalBlock(block.getLocation(), block.getState());
                if (block.getLocation().equals(loc)) {
                    Block neighbor = region.getBedNeighbor(block);
                    region.putOriginalBlock(neighbor.getLocation(), neighbor.getState());
                } else {
                    region.putOriginalBlock(loc, region.getBedNeighbor(block).getState());
                }
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    if (region.isBedHead(block.getState())) {
                        region.getBedNeighbor(block).setType(Material.AIR);
                    } else {
                        block.setType(Material.AIR);
                    }
                }
                return true;
            } else if (configurationContainer.getOrDefault(ConfigurationContainer.CAKE_TARGET_BLOCK_EATING, Boolean.class, false) && block.getType().name().contains("CAKE")) {
                return false; // when CAKES are in eating mode, don't allow to just break it
            } else {
                if (getPlayerTeam(player).teamInfo.bed.equals(loc)) {
                    return false;
                }
                bedDestroyed(loc, player.player, false, "RESPAWN_ANCHOR".equals(block.getType().name()), block.getType().name().contains("CAKE"));
                region.putOriginalBlock(loc, block.getState());
                try {
                    event.setDropItems(false);
                } catch (Throwable tr) {
                    block.setType(Material.AIR);
                }
                return true;
            }
        }
        if (Main.isBreakableBlock(block.getType())) {
            region.putOriginalBlock(block.getLocation(), block.getState());
            return true;
        }
        return false;
    }

    public void targetBlockExplode(RunningTeam team) {
        Location loc = team.getTargetBlock();
        Block block = loc.getBlock();
        if (region.isBedBlock(block.getState())) {
            if (!region.isBedHead(block.getState())) {
                loc = region.getBedNeighbor(block).getLocation();
            }
        }
        if (isTargetBlock(loc)) {
            if (region.isBedBlock(block.getState())) {
                bedDestroyed(loc, null, true, false, false);
                region.putOriginalBlock(block.getLocation(), block.getState());
                if (block.getLocation().equals(loc)) {
                    Block neighbor = region.getBedNeighbor(block);
                    region.putOriginalBlock(neighbor.getLocation(), neighbor.getState());
                } else {
                    region.putOriginalBlock(loc, region.getBedNeighbor(block).getState());
                }
                if (region.isBedHead(block.getState())) {
                    region.getBedNeighbor(block).setType(Material.AIR);
                } else {
                    block.setType(Material.AIR);
                }
            } else {
                bedDestroyed(loc, null, false, "RESPAWN_ANCHOR".equals(block.getType().name()), block.getType().name().contains("CAKE"));
                region.putOriginalBlock(loc, block.getState());
                block.setType(Material.AIR);
            }
        }
    }

    private boolean isTargetBlock(Location loc) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isBed && team.teamInfo.bed.equals(loc)) {
                return true;
            }
        }
        return false;
    }

    public Region getRegion() {
        return region;
    }

    public CurrentTeam getPlayerTeam(GamePlayer player) {
        for (CurrentTeam team : teamsInGame) {
            if (team.players.contains(player)) {
                return team;
            }
        }
        return null;
    }

    public CurrentTeam getCurrentTeamFromTeam(org.screamingsandals.bedwars.api.Team team) {
        for (CurrentTeam currentTeam : teamsInGame) {
            if (currentTeam.teamInfo == team) {
                return currentTeam;
            }
        }
        return null;
    }

    public void bedDestroyed(Location loc, Player broker, boolean isItBedBlock, boolean isItAnchor, boolean isItCake) {
        if (status == GameStatus.RUNNING) {
            for (CurrentTeam team : teamsInGame) {
                if (team.teamInfo.bed.equals(loc)) {
                    Debug.info(name + ": target block of  " + team.teamInfo.getName() + " has been destroyed");
                    team.isBed = false;
                    updateScoreboard();
                    String colored_broker = "explosion";
                    if (broker != null) {
                        colored_broker = getPlayerTeam(Main.getPlayerGameProfile(broker)).teamInfo.color.chatColor + broker.getDisplayName();
                    }
                    for (GamePlayer player : players) {
                        final String key = isItBedBlock ? "bed_is_destroyed" : (isItAnchor ? "anchor_is_destroyed" : (isItCake ? "cake_is_destroyed" : "target_is_destroyed"));
                        Title.send(player.player,
                                i18n(key, false)
                                        .replace("%team%", team.teamInfo.color.chatColor + team.teamInfo.name)
                                        .replace("%broker%", colored_broker),
                                i18n(getPlayerTeam(player) == team ? "bed_is_destroyed_subtitle_for_victim"
                                        : "bed_is_destroyed_subtitle", false));
                        player.player.sendMessage(i18nc(key, customPrefix)
                                .replace("%team%", team.teamInfo.color.chatColor + team.teamInfo.name)
                                .replace("%broker%", colored_broker));
                        SpawnEffects.spawnEffect(this, player.player, "game-effects.beddestroy");
                        Sounds.playSound(player.player, player.player.getLocation(),
                                Main.getConfigurator().config.getString("sounds.on_bed_destroyed"),
                                Sounds.ENTITY_ENDER_DRAGON_GROWL, 1, 1);
                    }

                    if (team.hasBedHolo()) {
                        team.getBedHolo().setLine(0,
                                i18nonly(isItBedBlock ? "protect_your_bed_destroyed" : (isItAnchor ? "protect_your_anchor_destroyed" : (isItCake ? "protect_your_cake_destroyed" : "protect_your_target_destroyed"))));
                        team.getBedHolo().addViewers(team.getConnectedPlayers());
                    }

                    if (team.hasProtectHolo()) {
                        team.getProtectHolo().destroy();
                    }

                    BedwarsTargetBlockDestroyedEvent targetBlockDestroyed = new BedwarsTargetBlockDestroyedEvent(this,
                            broker, team);
                    Main.getInstance().getServer().getPluginManager().callEvent(targetBlockDestroyed);

                    if (broker != null) {
                        if (Main.isPlayerStatisticsEnabled()) {
                            PlayerStatistic statistic = Main.getPlayerStatisticsManager().getStatistic(broker);
                            statistic.addDestroyedBeds(1);
                            statistic.addScore(Main.getConfigurator().config.getInt("statistics.scores.bed-destroy", 25));
                        }

                        dispatchRewardCommands("player-destroy-bed", broker,
                                Main.getConfigurator().config.getInt("statistics.scores.bed-destroy", 25));
                    }
                }
            }
        }
    }

    public void internalJoinPlayer(GamePlayer gamePlayer) {
        BedwarsPlayerJoinEvent joinEvent = new BedwarsPlayerJoinEvent(this, gamePlayer.player);
        Main.getInstance().getServer().getPluginManager().callEvent(joinEvent);

        if (joinEvent.isCancelled()) {
            Debug.info(gamePlayer.player.getName() + " can't join to the game: event cancelled");
            String message = joinEvent.getCancelMessage();
            if (message != null && !message.equals("")) {
                gamePlayer.player.sendMessage(message);
            }
            gamePlayer.changeGame(null);
            return;
        }
        Debug.info(gamePlayer.player.getName() + " joined bedwars match " + name);

        boolean isEmpty = players.isEmpty();
        if (!players.contains(gamePlayer)) {
            players.add(gamePlayer);
        }
        updateSigns();

        if (Main.isPlayerStatisticsEnabled()) {
            // Load
            Main.getPlayerStatisticsManager().getStatistic(gamePlayer.player);
        }

        if (arenaTime.time >= 0) {
            gamePlayer.player.setPlayerTime(arenaTime.time, false);
        }

        if (arenaWeather != null) {
            gamePlayer.player.setPlayerWeather(arenaWeather);
        }

        if (Main.getTabManager() != null) {
            players.forEach(Main.getTabManager()::modifyForPlayer);
        }

        if (Main.getConfigurator().config.getBoolean("tab.enable") && Main.getConfigurator().config.getBoolean("tab.hide-foreign-players")) {
            Bukkit.getOnlinePlayers().stream().filter(p -> Main.getInstance().getGameOfPlayer(p) != this).forEach(gamePlayer::hidePlayer);
            players.forEach(p -> p.showPlayer(gamePlayer.player));
        }

        if (status == GameStatus.WAITING) {
            Debug.info(gamePlayer.player.getName() + " moving to lobby");
            mpr("join").prefix(customPrefix).replace("name", gamePlayer.player.getDisplayName())
                    .replace("players", players.size())
                    .replace("maxplayers", calculatedMaxPlayers)
                    .send(getConnectedPlayers());

            gamePlayer.teleport(lobbySpawn, () -> {
                gamePlayer.invClean(); // temp fix for inventory issues?
                SpawnEffects.spawnEffect(Game.this, gamePlayer.player, "game-effects.lobbyjoin");

                if (configurationContainer.getOrDefault(ConfigurationContainer.JOIN_RANDOM_TEAM_ON_JOIN, Boolean.class, false)) {
                    joinRandomTeam(gamePlayer);
                }

                if (configurationContainer.getOrDefault(ConfigurationContainer.COMPASS, Boolean.class, false)) {
                    int compassPosition = Main.getConfigurator().config.getInt("hotbar.selector", 0);
                    if (compassPosition >= 0 && compassPosition <= 8) {
                        ItemStack compass = Main.getConfigurator().readDefinedItem("jointeam", "COMPASS");
                        ItemMeta metaCompass = compass.getItemMeta();
                        metaCompass.setDisplayName(i18n("compass_selector_team", false));
                        compass.setItemMeta(metaCompass);
                        gamePlayer.player.getInventory().setItem(compassPosition, compass);
                    }
                }

                int leavePosition = Main.getConfigurator().config.getInt("hotbar.leave", 8);
                if (leavePosition >= 0 && leavePosition <= 8) {
                    ItemStack leave = Main.getConfigurator().readDefinedItem("leavegame", "SLIME_BALL");
                    ItemMeta leaveMeta = leave.getItemMeta();
                    leaveMeta.setDisplayName(i18n("leave_from_game_item", false));
                    leave.setItemMeta(leaveMeta);
                    gamePlayer.player.getInventory().setItem(leavePosition, leave);
                }

                if (gamePlayer.player.hasPermission("bw.vip.startitem")
                        || gamePlayer.player.hasPermission("misat11.bw.vip.startitem")) {
                    int vipPosition = Main.getConfigurator().config.getInt("hotbar.start", 1);
                    if (vipPosition >= 0 && vipPosition <= 8) {
                        ItemStack startGame = Main.getConfigurator().readDefinedItem("startgame", "DIAMOND");
                        ItemMeta startGameMeta = startGame.getItemMeta();
                        startGameMeta.setDisplayName(i18n("start_game_item", false));
                        startGame.setItemMeta(startGameMeta);

                        gamePlayer.player.getInventory().setItem(vipPosition, startGame);
                    }
                }
            });

            if (isEmpty) {
                runTask();
            } else {
                statusbar.addPlayer(gamePlayer.player);
            }
        }

        if (status == GameStatus.RUNNING || status == GameStatus.GAME_END_CELEBRATING) {
            if (Main.getConfigurator().config.getBoolean("tab.enable") && Main.getConfigurator().config.getBoolean("tab.hide-spectators")) {
                players.stream().filter(p -> p.isSpectator && !isPlayerInAnyTeam(p.player)).forEach(p -> gamePlayer.hidePlayer(p.player));
            }

            makeSpectator(gamePlayer, true);
            createdHolograms.forEach(hologram -> {
                if (teamsInGame.stream().noneMatch(t -> t.getProtectHolo().equals(hologram))) {
                    hologram.addViewer(gamePlayer.player);
                }
            });
        }

        BedwarsPlayerJoinedEvent joinedEvent = new BedwarsPlayerJoinedEvent(this, getPlayerTeam(gamePlayer), gamePlayer.player);
        Main.getInstance().getServer().getPluginManager().callEvent(joinedEvent);
    }

    public void internalLeavePlayer(GamePlayer gamePlayer) {
        if (status == GameStatus.DISABLED) {
            return;
        }

        BedwarsPlayerLeaveEvent playerLeaveEvent = new BedwarsPlayerLeaveEvent(this, gamePlayer.player,
                getPlayerTeam(gamePlayer));
        Main.getInstance().getServer().getPluginManager().callEvent(playerLeaveEvent);
        Debug.info(name + ": player  " + gamePlayer.player.getName() + " is leaving the game");

        if (!gamePlayer.isSpectator) {
            String message = i18nc("leave", customPrefix).replace("%name%", gamePlayer.player.getDisplayName())
                    .replace("%players%", Integer.toString(players.size()))
                    .replaceAll("%maxplayers%", Integer.toString(calculatedMaxPlayers));

            if (!preServerRestart) {
                for (GamePlayer p : players) {
                    p.player.sendMessage(message);
                }
            }
        }

        players.remove(gamePlayer);
        updateSigns();

        if (status == GameStatus.WAITING) {
            SpawnEffects.spawnEffect(this, gamePlayer.player, "game-effects.lobbyleave");
        }

        if (Main.getTabManager() != null) {
            Main.getTabManager().clear(gamePlayer);
            players.forEach(Main.getTabManager()::modifyForPlayer);
        }

        if (Main.getConfigurator().config.getBoolean("tab.enable") && Main.getConfigurator().config.getBoolean("tab.hide-foreign-players")) {
            players.forEach(p -> p.hidePlayer(gamePlayer.player));
        }

        statusbar.removePlayer(gamePlayer.player);
        createdHolograms.forEach(holo -> holo.removeViewer(gamePlayer.player));

        gamePlayer.player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        if (Main.getConfigurator().config.getBoolean("mainlobby.enabled")
                && !Main.getConfigurator().config.getBoolean("bungee.enabled")) {
            try {
                Location mainLobbyLocation = MiscUtils.readLocationFromString(
                        Bukkit.getWorld(Main.getConfigurator().config.getString("mainlobby.world")),
                        Main.getConfigurator().config.getString("mainlobby.location"));
                gamePlayer.teleport(mainLobbyLocation);
                gamePlayer.mainLobbyUsed = true;
            } catch (Throwable t) {
                Bukkit.getLogger().severe("You didn't setup properly the mainlobby! Do it via commands not directly in config.yml");
            }
        }

        if (status == GameStatus.RUNNING || status == GameStatus.WAITING) {
            CurrentTeam team = getPlayerTeam(gamePlayer);
            if (team != null) {
                team.players.remove(gamePlayer);
                if (status == GameStatus.WAITING) {
                    if (!Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false))
                        team.getScoreboardTeam().removeEntry(gamePlayer.player.getName());
                    if (team.players.isEmpty()) {
                        teamsInGame.remove(team);
                        if (!Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false))
                            team.getScoreboardTeam().unregister();
                    }

                    if (Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false)) {
                        experimentalBoard.handlePlayerLeave(gamePlayer.player);
                    }

                } else {
                    updateScoreboard();
                }
            }
        }

        if (Main.isPlayerStatisticsEnabled()) {
            PlayerStatistic statistic = Main.getPlayerStatisticsManager().getStatistic(gamePlayer.player);
            Main.getPlayerStatisticsManager().storeStatistic(statistic);

            Main.getPlayerStatisticsManager().unloadStatistic(gamePlayer.player);
        }

        if (players.isEmpty()) {
            if (!preServerRestart) {
                BedWarsPlayerLastLeaveEvent playerLastLeaveEvent = new BedWarsPlayerLastLeaveEvent(this, gamePlayer.player,
                        getPlayerTeam(gamePlayer));
                Main.getInstance().getServer().getPluginManager().callEvent(playerLastLeaveEvent);
            }

            if (status != GameStatus.WAITING) {
                afterRebuild = GameStatus.WAITING;
                updateSigns();
                rebuild();
            } else {
                status = GameStatus.WAITING;
                cancelTask();
            }
            countdown = -1;

            if (!Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false)) {
                if (gameScoreboard.getObjective("display") != null) {
                    gameScoreboard.getObjective("display").unregister();
                }
                if (gameScoreboard.getObjective("lobby") != null) {
                    gameScoreboard.getObjective("lobby").unregister();
                }
                gameScoreboard.clearSlot(DisplaySlot.SIDEBAR);

                for (CurrentTeam team : teamsInGame) {
                    team.getScoreboardTeam().unregister();
                }
            }
            teamsInGame.clear();

            for (GameStore store : gameStore) {
                LivingEntity villager = store.kill();
                if (villager != null) {
                    Main.unregisterGameEntity(villager);
                }
            }
        }
    }

    public void saveToConfig() {
        File dir = new File(Main.getInstance().getDataFolder(), "arenas");
        if (!dir.exists())
            dir.mkdirs();
        File file = new File(dir, name + ".yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        FileConfiguration configMap = new YamlConfiguration();
        configMap.set("name", name);
        configMap.set("pauseCountdown", pauseCountdown);
        configMap.set("gameTime", gameTime);
        configMap.set("world", world.getName());
        configMap.set("pos1", MiscUtils.setLocationToString(pos1));
        configMap.set("pos2", MiscUtils.setLocationToString(pos2));
        configMap.set("specSpawn", MiscUtils.setLocationToString(specSpawn));
        configMap.set("lobbySpawn", MiscUtils.setLocationToString(lobbySpawn));
        configMap.set("lobbySpawnWorld", lobbySpawn.getWorld().getName());
        configMap.set("minPlayers", minPlayers);
        configMap.set("postGameWaiting", postGameWaiting);
        configMap.set("customPrefix", customPrefix);
        if (!teams.isEmpty()) {
            for (Team t : teams) {
                configMap.set("teams." + t.name + ".isNewColor", t.isNewColor());
                configMap.set("teams." + t.name + ".color", t.color.name());
                configMap.set("teams." + t.name + ".maxPlayers", t.maxPlayers);
                configMap.set("teams." + t.name + ".bed", MiscUtils.setLocationToString(t.bed));
                configMap.set("teams." + t.name + ".spawn", MiscUtils.setLocationToString(t.spawn));
            }
        }
        List<Map<String, Object>> nS = new ArrayList<>();
        for (ItemSpawner spawner : spawners) {
            Map<String, Object> spawnerMap = new HashMap<>();
            spawnerMap.put("location", MiscUtils.setLocationToString(spawner.loc));
            spawnerMap.put("type", spawner.type.getConfigKey());
            spawnerMap.put("customName", spawner.customName);
            spawnerMap.put("startLevel", spawner.startLevel);
            spawnerMap.put("hologramEnabled", spawner.hologramEnabled);
            if (spawner.getTeam() != null) {
                spawnerMap.put("team", spawner.getTeam().getName());
            } else {
                spawnerMap.put("team", null);
            }
            spawnerMap.put("maxSpawnedResources", spawner.maxSpawnedResources);
            nS.add(spawnerMap);
        }
        configMap.set("spawners", nS);
        if (!gameStore.isEmpty()) {
            List<Map<String, String>> nL = new ArrayList<>();
            for (GameStore store : gameStore) {
                Map<String, String> map = new HashMap<>();
                map.put("loc", MiscUtils.setLocationToString(store.getStoreLocation()));
                map.put("shop", store.getShopFile());
                map.put("parent", store.getUseParent() ? "true" : "false");
                map.put("type", store.getEntityType().name());
                if (store.isShopCustomName()) {
                    map.put("name", store.getShopCustomName());
                }
                map.put("isBaby", store.isBaby() ? "true" : "false");
                map.put("skin", store.getSkinName());
                nL.add(map);
            }
            configMap.set("stores", nL);
        }

        configurationContainer.getSaved().forEach((key, value) -> {
            configMap.set("constant." + key, value);
        });

        configMap.set("arenaTime", arenaTime.name());
        configMap.set("arenaWeather", arenaWeather == null ? "default" : arenaWeather.name());

        try {
            configMap.set("lobbyBossBarColor", lobbyBossBarColor == null ? "default" : lobbyBossBarColor.name());
            configMap.set("gameBossBarColor", gameBossBarColor == null ? "default" : gameBossBarColor.name());
        } catch (Throwable t) {
            // We're using 1.8
        }

        try {
            configMap.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        if (status == GameStatus.DISABLED) {
            preparing = true;
            status = GameStatus.WAITING;
            if (experimentalBoard == null && Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false)) {
                experimentalBoard = new ScreamingBoard(this);
            }
            countdown = -1;
            calculatedMaxPlayers = 0;
            for (Team team : teams) {
                calculatedMaxPlayers += team.maxPlayers;
            }
            new BukkitRunnable() {
                public void run() {
                    updateSigns();
                }
            }.runTask(Main.getInstance());

            if (Main.getConfigurator().config.getBoolean("bossbar.use-xp-bar", false)) {
                statusbar = new XPBar();
            } else {
                statusbar = BossBarSelector.getBossBar();
            }
            preparing = false;
        }
    }

    public void stop() {
        if (status == GameStatus.DISABLED) {
            return; // Game is already stopped
        }
        List<GamePlayer> clonedPlayers = (List<GamePlayer>) ((ArrayList<GamePlayer>) players).clone();
        for (GamePlayer p : clonedPlayers)
            p.changeGame(null);
        if (status != GameStatus.REBUILDING) {
            status = GameStatus.DISABLED;
            updateSigns();
        } else {
            afterRebuild = GameStatus.DISABLED;
        }
    }

    public void joinToGame(Player player) {
        if (status == GameStatus.DISABLED) {
            return;
        }

        if (preparing) {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> joinToGame(player), 1L);
            return;
        }

        if (status == GameStatus.REBUILDING) {
            if (isBungeeEnabled()) {
                BungeeUtils.movePlayerToBungeeServer(player, false);
                BungeeUtils.sendPlayerBungeeMessage(player,
                        i18n("game_is_rebuilding").replace("%arena%", Game.this.name));
            } else {
                player.sendMessage(i18n("game_is_rebuilding").replace("%arena%", this.name));
            }
            return;
        }

        if ((status == GameStatus.RUNNING || status == GameStatus.GAME_END_CELEBRATING)
                && !configurationContainer.getOrDefault(ConfigurationContainer.SPECTATOR_JOIN, Boolean.class, false)) {
            if (isBungeeEnabled()) {
                BungeeUtils.movePlayerToBungeeServer(player, false);
                BungeeUtils.sendPlayerBungeeMessage(player,
                        i18n("game_already_running").replace("%arena%", Game.this.name));
            } else {
                player.sendMessage(i18n("game_already_running").replace("%arena%", this.name));
            }
            return;
        }

        if (players.size() >= calculatedMaxPlayers && status == GameStatus.WAITING) {
            if (Main.getPlayerGameProfile(player).canJoinFullGame()) {
                List<GamePlayer> withoutVIP = getPlayersWithoutVIP();

                if (withoutVIP.size() == 0) {
                    player.sendMessage(i18n("vip_game_is_full"));
                    return;
                }

                GamePlayer kickPlayer;
                if (withoutVIP.size() == 1) {
                    kickPlayer = withoutVIP.get(0);
                } else {
                    kickPlayer = withoutVIP.get(MiscUtils.randInt(0, players.size() - 1));
                }

                if (isBungeeEnabled()) {
                    BungeeUtils.sendPlayerBungeeMessage(kickPlayer.player,
                            i18n("game_kicked_by_vip").replace("%arena%", Game.this.name));
                } else {
                    kickPlayer.player.sendMessage(i18n("game_kicked_by_vip").replace("%arena%", this.name));
                }
                kickPlayer.changeGame(null);
            } else {
                if (isBungeeEnabled()) {
                    BungeeUtils.movePlayerToBungeeServer(player, false);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            BungeeUtils.sendPlayerBungeeMessage(player,
                                    i18n("game_is_full").replace("%arena%", Game.this.name));
                        }
                    }.runTaskLater(Main.getInstance(), 5L);
                } else {
                    player.sendMessage(i18n("game_is_full").replace("%arena%", this.name));
                }
                return;
            }
        }

        GamePlayer gPlayer = Main.getPlayerGameProfile(player);
        gPlayer.changeGame(this);
    }

    public void leaveFromGame(Player player) {
        if (status == GameStatus.DISABLED) {
            return;
        }
        if (Main.isPlayerInGame(player)) {
            GamePlayer gPlayer = Main.getPlayerGameProfile(player);

            if (gPlayer.getGame() == this) {
                gPlayer.changeGame(null);
                if (status == GameStatus.RUNNING || status == GameStatus.GAME_END_CELEBRATING) {
                    updateScoreboard();
                }
            }
        }
    }

    public CurrentTeam getCurrentTeamByTeam(Team team) {
        for (CurrentTeam current : teamsInGame) {
            if (current.teamInfo == team) {
                return current;
            }
        }
        return null;
    }

    public Team getFirstTeamThatIsntInGame() {
        for (Team team : teams) {
            if (getCurrentTeamByTeam(team) == null) {
                return team;
            }
        }
        return null;
    }

    public CurrentTeam getTeamWithLowestPlayers() {
        CurrentTeam lowest = null;

        for (CurrentTeam team : teamsInGame) {
            if (lowest == null) {
                lowest = team;
            }

            if (lowest.players.size() > team.players.size()) {
                lowest = team;
            }
        }

        return lowest;
    }

    public List<GamePlayer> getPlayersInTeam(Team team) {
        CurrentTeam currentTeam = null;
        for (CurrentTeam cTeam : teamsInGame) {
            if (cTeam.teamInfo == team) {
                currentTeam = cTeam;
            }
        }

        if (currentTeam != null) {
            return currentTeam.players;
        } else {
            return new ArrayList<>();
        }
    }

    private void internalTeamJoin(GamePlayer player, Team teamForJoin) {
        CurrentTeam current = null;
        for (CurrentTeam t : teamsInGame) {
            if (t.teamInfo == teamForJoin) {
                current = t;
                break;
            }
        }

        CurrentTeam cur = getPlayerTeam(player);
        BedwarsPlayerJoinTeamEvent event = new BedwarsPlayerJoinTeamEvent(current, player.player, this, cur);
        Main.getInstance().getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        if (current == null) {
            current = new CurrentTeam(teamForJoin, this);
            if (!Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false)) {
                org.bukkit.scoreboard.Team scoreboardTeam = gameScoreboard.getTeam(teamForJoin.name);
                if (scoreboardTeam == null) {
                    scoreboardTeam = gameScoreboard.registerNewTeam(teamForJoin.name);
                }
                if (!Main.isLegacy()) {
                    scoreboardTeam.setColor(teamForJoin.color.chatColor);
                } else {
                    scoreboardTeam.setPrefix(teamForJoin.color.chatColor.toString());
                }
                scoreboardTeam.setAllowFriendlyFire(configurationContainer.getOrDefault(ConfigurationContainer.FRIENDLY_FIRE, Boolean.class, false));

                current.setScoreboardTeam(scoreboardTeam);
            } else{
                experimentalBoard.registerCurrentTeam(teamForJoin);
            }
        }

        if (cur == current) {
            player.player.sendMessage(
                    i18nc("team_already_selected", customPrefix).replace("%team%", teamForJoin.color.chatColor + teamForJoin.name)
                            .replace("%players%", Integer.toString(current.players.size()))
                            .replaceAll("%maxplayers%", Integer.toString(current.teamInfo.maxPlayers)));
            return;
        }
        if (current.players.size() >= current.teamInfo.maxPlayers) {
            if (cur != null) {
                player.player.sendMessage(i18nc("team_is_full_you_are_staying", customPrefix)
                        .replace("%team%", teamForJoin.color.chatColor + teamForJoin.name)
                        .replace("%oldteam%", cur.teamInfo.color.chatColor + cur.teamInfo.name));
            } else {
                player.player.sendMessage(
                        i18nc("team_is_full", customPrefix).replace("%team%", teamForJoin.color.chatColor + teamForJoin.name));
            }
            return;
        }
        if (cur != null) {
            cur.players.remove(player);
            if (!Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false))
                cur.getScoreboardTeam().removeEntry(player.player.getName());
            else
                experimentalBoard.handlePlayerLeave(player.player);

            if (cur.players.isEmpty()) {
                teamsInGame.remove(cur);
                if (!Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false))
                    cur.getScoreboardTeam().unregister();
                else
                    experimentalBoard.unregisterTeam(cur.teamInfo, ScreamingBoard.LOBBY_OBJECTIVE);
            }
            Debug.info(name + ": player " + player.player.getName() + " left the team " + cur.getName());
        }
        current.players.add(player);
        if (!Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false))
            current.getScoreboardTeam().addEntry(player.player.getName());
        else
            experimentalBoard.registerPlayerInTeam(player.player, teamForJoin);

        Debug.info(name + ": player " + player.player.getName() + " joined the team " + current.getName());

        player.player
                .sendMessage(i18nc("team_selected", customPrefix).replace("%team%", teamForJoin.color.chatColor + teamForJoin.name)
                        .replace("%players%", Integer.toString(current.players.size()))
                        .replaceAll("%maxplayers%", Integer.toString(current.teamInfo.maxPlayers)));

        if (configurationContainer.getOrDefault(ConfigurationContainer.ADD_WOOL_TO_INVENTORY_ON_JOIN, Boolean.class, false)) {
            int colorPosition = Main.getConfigurator().config.getInt("hotbar.color", 1);
            if (colorPosition >= 0 && colorPosition <= 8) {
                ItemStack stack = teamForJoin.color.getWool();
                ItemMeta stackMeta = stack.getItemMeta();
                stackMeta.setDisplayName(teamForJoin.color.chatColor + teamForJoin.name);
                stack.setItemMeta(stackMeta);
                player.player.getInventory().setItem(colorPosition, stack);
            }
        }

        if (configurationContainer.getOrDefault(ConfigurationContainer.COLORED_LEATHER_BY_TEAM_IN_LOBBY, Boolean.class, false)) {
            ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta meta = (LeatherArmorMeta) chestplate.getItemMeta();
            meta.setColor(teamForJoin.color.leatherColor);
            chestplate.setItemMeta(meta);
            player.player.getInventory().setChestplate(chestplate);
        }

        if (!teamsInGame.contains(current)) {
            teamsInGame.add(current);
        }
    }

    public void joinRandomTeam(GamePlayer player) {
        Team teamForJoin;
        if (teamsInGame.size() < 2) {
            teamForJoin = getFirstTeamThatIsntInGame();
        } else {
            CurrentTeam current = getTeamWithLowestPlayers();
            if (current.players.size() >= current.getMaxPlayers()) {
                teamForJoin = getFirstTeamThatIsntInGame();
            } else {
                teamForJoin = current.teamInfo;
            }
        }

        if (teamForJoin == null) {
            return;
        }

        internalTeamJoin(player, teamForJoin);
    }

    public Location makeSpectator(GamePlayer gamePlayer, boolean leaveItem) {
        Debug.info(gamePlayer.player.getName() + " spawning as spectator");
        Player player = gamePlayer.player;
        gamePlayer.isSpectator = true;
        gamePlayer.teleport(specSpawn, () -> {
            if (!configurationContainer.getOrDefault(ConfigurationContainer.KEEP_INVENTORY, Boolean.class, false) || leaveItem) {
                gamePlayer.invClean(); // temp fix for inventory issues?
            }
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setGameMode(GameMode.SPECTATOR);

            if (leaveItem) {
                if (Main.getConfigurator().config.getBoolean("tab.enable") && Main.getConfigurator().config.getBoolean("tab.hide-spectators")) {
                    players.forEach(p -> p.hidePlayer(player));
                }

                int leavePosition = Main.getConfigurator().config.getInt("hotbar.leave", 8);
                if (leavePosition >= 0 && leavePosition <= 8) {
                    ItemStack leave = Main.getConfigurator().readDefinedItem("leavegame", "SLIME_BALL");
                    ItemMeta leaveMeta = leave.getItemMeta();
                    leaveMeta.setDisplayName(i18n("leave_from_game_item", false));
                    leave.setItemMeta(leaveMeta);
                    gamePlayer.player.getInventory().setItem(leavePosition, leave);
                }
            }

            if (Main.getTabManager() != null) {
                players.forEach(Main.getTabManager()::modifyForPlayer);
            }
        });

        return specSpawn;
    }

    @SuppressWarnings("unchecked")
    public void makePlayerFromSpectator(GamePlayer gamePlayer) {
        Debug.info(gamePlayer.player.getName() + " changing spectator to regular player");
        Player player = gamePlayer.player;
        CurrentTeam currentTeam = getPlayerTeam(gamePlayer);

        if (gamePlayer.getGame() == this && currentTeam != null) {
            gamePlayer.isSpectator = false;
            gamePlayer.teleport(currentTeam.getTeamSpawn(), () -> {
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setGameMode(GameMode.SURVIVAL);

                if (Main.getConfigurator().config.getBoolean("tab.enable") && Main.getConfigurator().config.getBoolean("tab.hide-spectators")) {
                    players.forEach(p -> p.showPlayer(player));
                }

                if (Main.getConfigurator().config.getBoolean("respawn.protection-enabled", true)) {
                    RespawnProtection respawnProtection = addProtectedPlayer(player);
                    respawnProtection.runProtection();
                }

                if (configurationContainer.getOrDefault(ConfigurationContainer.ENABLE_PLAYER_RESPAWN_ITEMS, Boolean.class, false)) {
                    List<ItemStack> givedGameStartItems = StackParser.parseAll((Collection<Object>) Main.getConfigurator().config
                            .getList("gived-player-respawn-items"));
                    if (givedGameStartItems != null) {
                        MiscUtils.giveItemsToPlayer(givedGameStartItems, player, currentTeam.getColor());
                    } else {
                        Debug.warn("You have wrongly configured gived-player-respawn-items!", true);
                    }
                }
                MiscUtils.giveItemsToPlayer(gamePlayer.getPermaItemsPurchased(), player, currentTeam.getColor());

                if (configurationContainer.getOrDefault(ConfigurationContainer.KEEP_ARMOR, Boolean.class, false)) {
                    final ItemStack[] armorContents = gamePlayer.getGameArmorContents();
                    if (armorContents != null) {
                        gamePlayer.player.getInventory().setArmorContents(armorContents);
                    }
                }

                if (Main.getTabManager() != null) {
                    players.forEach(Main.getTabManager()::modifyForPlayer);
                }
            });
        }
    }

    public void setBossbarProgress(int count, int max) {
        double progress = (double) count / (double) max;
        statusbar.setProgress(progress);
        if (statusbar instanceof XPBar) {
            XPBar xpbar = (XPBar) statusbar;
            xpbar.setSeconds(count);
        }
    }

    @SuppressWarnings("unchecked")
    public void run() {
        // Phase 1: Check if game is running
        if (status == GameStatus.DISABLED) { // Game is not running, why cycle is still running?
            cancelTask();
            return;
        }
        BedwarsGameChangedStatusEvent statusE = new BedwarsGameChangedStatusEvent(this);
        // Phase 2: If this is first tick, prepare waiting lobby
        if (countdown == -1 && status == GameStatus.WAITING) {
            Debug.info(name + ": preparing lobby");
            previousCountdown = countdown = pauseCountdown;
            previousStatus = GameStatus.WAITING;
            String title = i18nonly("bossbar_waiting");
            statusbar.setProgress(0);
            statusbar.setVisible(configurationContainer.getOrDefault(ConfigurationContainer.LOBBY_BOSSBAR, Boolean.class, false));
            for (GamePlayer p : players) {
                statusbar.addPlayer(p.player);
            }
            if (statusbar instanceof BossBar) {
                BossBar bossbar = (BossBar) statusbar;
                bossbar.setMessage(title);
                if (bossbar instanceof BossBar19) {
                    BossBar19 bossbar19 = (BossBar19) bossbar;
                    bossbar19.setColor(lobbyBossBarColor != null ? lobbyBossBarColor
                            : BarColor.valueOf(Main.getConfigurator().config.getString("bossbar.lobby.color")));
                    bossbar19
                            .setStyle(BarStyle.valueOf(Main.getConfigurator().config.getString("bossbar.lobby.style")));
                }
            }
            if (teamSelectorInventory == null) {
                teamSelectorInventory = new TeamSelectorInventory(Main.getInstance(), this);
            }
            updateSigns();
            Debug.info(name + ": lobby prepared");
        }

        // Phase 3: Prepare information about next tick for tick event and update
        // bossbar with scoreboard
        int nextCountdown = countdown;
        GameStatus nextStatus = status;

        if (status == GameStatus.WAITING) {
            // Game start item
            if (gameStartItem) {
                if (players.size() >= getMinPlayers()) {
                    for (GamePlayer player : players) {
                        if (getPlayerTeam(player) == null) {
                            joinRandomTeam(player);
                        }
                    }
                }
                if (players.size() > 1) {
                    countdown = 0;
                    gameStartItem = false;
                }
            }

            if (players.size() >= getMinPlayers()
                    && (configurationContainer.getOrDefault(ConfigurationContainer.JOIN_RANDOM_TEAM_AFTER_LOBBY, Boolean.class, false) || teamsInGame.size() > 1)) {
                if (countdown == 0) {
                    nextCountdown = gameTime;
                    nextStatus = GameStatus.RUNNING;
                } else {
                    nextCountdown--;

                    if (countdown <= 10 && countdown >= 1 && countdown != previousCountdown) {

                        for (GamePlayer player : players) {
                            Title.send(player.player, ChatColor.YELLOW + Integer.toString(countdown), "");
                            Sounds.playSound(player.player, player.player.getLocation(),
                                    Main.getConfigurator().config.getString("sounds.on_countdown"), Sounds.UI_BUTTON_CLICK,
                                    1, 1);
                        }
                    }
                }
            } else {
                nextCountdown = countdown = pauseCountdown;
            }
            setBossbarProgress(countdown, pauseCountdown);
            updateLobbyScoreboard();
        } else if (status == GameStatus.RUNNING) {
            if (countdown == 0) {
                nextCountdown = postGameWaiting;
                nextStatus = GameStatus.GAME_END_CELEBRATING;
            } else {
                nextCountdown--;
            }
            setBossbarProgress(countdown, gameTime);
            updateScoreboardTimer();
        } else if (status == GameStatus.GAME_END_CELEBRATING) {
            if (countdown == 0) {
                nextStatus = GameStatus.REBUILDING;
                nextCountdown = 0;
            } else {
                nextCountdown--;
            }
            setBossbarProgress(countdown, postGameWaiting);
        }

        // Phase 4: Call Tick Event
        BedwarsGameTickEvent tick = new BedwarsGameTickEvent(this, previousCountdown, previousStatus, countdown, status,
                nextCountdown, nextStatus);
        Bukkit.getPluginManager().callEvent(tick);
        Debug.info(name + ": tick passed: " + tick.getPreviousCountdown() + "," + tick.getCountdown() + "," + tick.getNextCountdown() + " (" + tick.getPreviousStatus() + "," + tick.getStatus() + "," + tick.getNextStatus() + ")");

        // Phase 5: Update Previous information
        previousCountdown = countdown;
        previousStatus = status;

        // Phase 6: Process tick
        // Phase 6.1: If status changed
        if (status != tick.getNextStatus()) {
            // Phase 6.1.1: Prepare game if next status is RUNNING
            if (tick.getNextStatus() == GameStatus.RUNNING) {
                Debug.info(name + ": preparing game");
                preparing = true;
                BedwarsGameStartEvent startE = new BedwarsGameStartEvent(this);
                Main.getInstance().getServer().getPluginManager().callEvent(startE);
                Main.getInstance().getServer().getPluginManager().callEvent(statusE);

                if (startE.isCancelled()) {
                    tick.setNextCountdown(pauseCountdown);
                    tick.setNextStatus(GameStatus.WAITING);
                    preparing = false;
                } else {

                    if (configurationContainer.getOrDefault(ConfigurationContainer.JOIN_RANDOM_TEAM_AFTER_LOBBY, Boolean.class, false)) {
                        for (GamePlayer player : players) {
                            if (getPlayerTeam(player) == null) {
                                joinRandomTeam(player);
                            }
                        }
                    }

                    statusbar.setProgress(0);
                    statusbar.setVisible(configurationContainer.getOrDefault(ConfigurationContainer.GAME_BOSSBAR, Boolean.class, false));
                    if (statusbar instanceof BossBar) {
                        BossBar bossbar = (BossBar) statusbar;
                        bossbar.setMessage(i18n("bossbar_running", false));
                        if (bossbar instanceof BossBar19) {
                            BossBar19 bossbar19 = (BossBar19) bossbar;
                            bossbar19.setColor(gameBossBarColor != null ? gameBossBarColor
                                    : BarColor.valueOf(Main.getConfigurator().config.getString("bossbar.game.color")));
                            bossbar19.setStyle(
                                    BarStyle.valueOf(Main.getConfigurator().config.getString("bossbar.game.style")));
                        }
                    }
                    if (teamSelectorInventory != null)
                        teamSelectorInventory.destroy();
                    teamSelectorInventory = null;
                    if (!Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false)) {
                        if (gameScoreboard.getObjective("lobby") != null) {
                            gameScoreboard.getObjective("lobby").unregister();
                        }
                        gameScoreboard.clearSlot(DisplaySlot.SIDEBAR);
                    }
                    Bukkit.getScheduler().runTaskLater(Main.getInstance(), this::updateSigns, 3L);
                    for (GameStore store : gameStore) {
                        LivingEntity villager = store.spawn();
                        if (villager != null) {
                            Main.registerGameEntity(villager, this);
                            EntityUtils.disableEntityAI(villager);
                            villager.getLocation().getWorld().getNearbyEntities(villager.getLocation(), 1, 1, 1).forEach(entity -> {
                                if (entity.getType() == villager.getType() && entity.getLocation().getBlock().equals(villager.getLocation().getBlock()) && !villager.equals(entity)) {
                                    entity.remove();
                                }
                            });
                        }
                    }

                    for (ItemSpawner spawner : spawners) {
                        UpgradeStorage storage = UpgradeRegistry.getUpgrade("spawner");
                        if (storage != null) {
                            storage.addUpgrade(this, spawner);
                        }
                    }

                    if (configurationContainer.getOrDefault(ConfigurationContainer.SPAWNER_HOLOGRAMS, Boolean.class, false)) {
                        for (ItemSpawner spawner : spawners) {
                            CurrentTeam spawnerTeam = getCurrentTeamFromTeam(spawner.getTeam());
                            if (configurationContainer.getOrDefault(ConfigurationContainer.STOP_TEAM_SPAWNERS_ON_DIE, Boolean.class, false) && spawner.getTeam() != null && spawnerTeam == null) {
                                continue; // team of this spawner is not available. Fix #147
                            }


                            if (spawner.getHologramEnabled()) {
                                Location loc;

                                if (spawner.getFloatingEnabled() &&
                                        Main.getConfigurator().config.getBoolean("floating-generator.enabled", true)) {
                                    loc = spawner.loc.clone().add(0,
                                            Main.getConfigurator().config.getDouble("floating-generator.holo-height", 2.0), 0);
                                    spawner.spawnFloatingStand();
                                } else {
                                    loc = spawner.loc.clone().add(0,
                                            Main.getConfigurator().config.getDouble("spawner-holo-height", 0.25), 0);
                                }

                                Hologram holo = Main.getHologramManager().spawnHologram(getConnectedPlayers(), loc,
                                        spawner.type.getItemBoldName());

                                createdHolograms.add(holo);
                                if (configurationContainer.getOrDefault(ConfigurationContainer.SPAWNER_COUNTDOWN_HOLOGRAM, Boolean.class, false)) {
                                    holo.addLine(spawner.type.getInterval() < 2 ? i18nonly("every_second_spawning")
                                            : i18nonly("countdown_spawning").replace("%seconds%",
                                            Integer.toString(spawner.type.getInterval())));
                                    countdownHolograms.put(spawner, holo);
                                }
                            }
                        }
                    }

                    String gameStartTitle = i18nonly("game_start_title");
                    String gameStartSubtitle = i18nonly("game_start_subtitle").replace("%arena%", this.name);
                    for (GamePlayer player : this.players) {
                        Debug.info(name + ": moving " + player.player.getName() + " into game");
                        CurrentTeam team = getPlayerTeam(player);
                        player.player.getInventory().clear();
                        // Player still had armor on legacy versions
                        player.player.getInventory().setHelmet(null);
                        player.player.getInventory().setChestplate(null);
                        player.player.getInventory().setLeggings(null);
                        player.player.getInventory().setBoots(null);
                        Title.send(player.player, gameStartTitle, gameStartSubtitle);
                        if (team == null) {
                            makeSpectator(player, true);
                        } else {
                            player.teleport(team.teamInfo.spawn, () -> {
                                player.player.setGameMode(GameMode.SURVIVAL);
                                if (configurationContainer.getOrDefault(ConfigurationContainer.ENABLE_GAME_START_ITEMS, Boolean.class, false)) {
                                    List<ItemStack> givedGameStartItems = StackParser.parseAll((Collection<Object>) Main.getConfigurator().config
                                            .getList("gived-game-start-items"));
                                    if (givedGameStartItems != null) {
                                        MiscUtils.giveItemsToPlayer(givedGameStartItems, player.player, team.getColor());
                                    } else {
                                        Debug.warn("You have wrongly configured gived-player-start-items!", true);
                                    }
                                }
                                SpawnEffects.spawnEffect(this, player.player, "game-effects.start");
                            });
                        }
                        Sounds.playSound(player.player, player.player.getLocation(),
                                Main.getConfigurator().config.getString("sounds.on_game_start"),
                                Sounds.ENTITY_PLAYER_LEVELUP, 1, 1);
                    }

                    if (configurationContainer.getOrDefault(ConfigurationContainer.REMOVE_UNUSED_TARGET_BLOCKS, Boolean.class, false)) {
                        for (Team team : teams) {
                            CurrentTeam ct = null;
                            for (CurrentTeam curt : teamsInGame) {
                                if (curt.teamInfo == team) {
                                    ct = curt;
                                    break;
                                }
                            }
                            if (ct == null) {
                                Location loc = team.bed;
                                Block block = team.bed.getBlock();
                                if (region.isBedBlock(block.getState())) {
                                    region.putOriginalBlock(block.getLocation(), block.getState());
                                    Block neighbor = region.getBedNeighbor(block);
                                    region.putOriginalBlock(neighbor.getLocation(), neighbor.getState());
                                    neighbor.setType(Material.AIR, false);
                                    block.setType(Material.AIR);
                                } else {
                                    region.putOriginalBlock(loc, block.getState());
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }

                    for (CurrentTeam team : teamsInGame) {
                        Block block = team.getTargetBlock().getBlock();
                        if (block != null && "RESPAWN_ANCHOR".equals(block.getType().name())) { // don't break the game for older servers
                            new BukkitRunnable() {
                                public void run() {
                                    RespawnAnchor anchor = (RespawnAnchor) block.getBlockData();
                                    anchor.setCharges(0);
                                    block.setBlockData(anchor);
                                    if (configurationContainer.getOrDefault(ConfigurationContainer.ANCHOR_AUTO_FILL, Boolean.class, false)) {
                                        new BukkitRunnable() {
                                            public void run() {
                                                anchor.setCharges(anchor.getCharges() + 1);
                                                Sounds.playSound(team.getTargetBlock(), Main.getConfigurator().config.getString("target-block.respawn-anchor.sound.charge"), Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 1, 1);
                                                block.setBlockData(anchor);
                                                if (anchor.getCharges() >= anchor.getMaximumCharges()) {
                                                    updateScoreboard();
                                                    this.cancel();
                                                }
                                            }
                                        }.runTaskTimer(Main.getInstance(), 50L, 10L);
                                    }
                                }
                            }.runTask(Main.getInstance());
                        }
                    }

                    if (configurationContainer.getOrDefault(ConfigurationContainer.HOLOGRAMS_ABOVE_BEDS, Boolean.class, false)) {
                        for (CurrentTeam team : teamsInGame) {
                            Block bed = team.teamInfo.bed.getBlock();
                            Location loc = team.teamInfo.bed.clone().add(0.5, 1.5, 0.5);
                            boolean isBlockTypeBed = region.isBedBlock(bed.getState());
                            boolean isAnchor = "RESPAWN_ANCHOR".equals(bed.getType().name());
                            boolean isCake = bed.getType().name().contains("CAKE");
                            List<Player> enemies = getConnectedPlayers();
                            enemies.removeAll(team.getConnectedPlayers());
                            Hologram holo = Main.getHologramManager().spawnHologram(enemies, loc,
                                    i18nonly(isBlockTypeBed ? "destroy_this_bed" : (isAnchor ? "destroy_this_anchor" : (isCake ? "destroy_this_cake" : "destroy_this_target")))
                                            .replace("%teamcolor%", team.teamInfo.color.chatColor.toString()));
                            createdHolograms.add(holo);
                            team.setBedHolo(holo);
                            Hologram protectHolo = Main.getHologramManager().spawnHologram(team.getConnectedPlayers(), loc,
                                    i18nonly(isBlockTypeBed ? "protect_your_bed" : (isAnchor ? "protect_your_anchor" : (isCake ? "protect_your_cake" : "protect_your_target")))
                                            .replace("%teamcolor%", team.teamInfo.color.chatColor.toString()));
                            createdHolograms.add(protectHolo);
                            team.setProtectHolo(protectHolo);
                        }
                    }

                    // Check target blocks existence
                    for (CurrentTeam team : teamsInGame) {
                        Location targetLocation = team.getTargetBlock();
                        if (targetLocation.getBlock().getType() == Material.AIR) {
                            ItemStack stack = team.teamInfo.color.getWool();
                            Block placedBlock = targetLocation.getBlock();
                            placedBlock.setType(stack.getType());
                            if (!Main.isLegacy()) {
                                try {
                                    // The method is no longer in API, but in legacy versions exists
                                    Block.class.getMethod("setData", byte.class).invoke(placedBlock, (byte) stack.getDurability());
                                } catch (Exception e) {
                                }
                            }
                        }
                    }

                    if (Main.getVersionNumber() >= 115 && !Main.getConfigurator().config.getBoolean("allow-fake-death")) {
                        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
                    }
                    preparing = false;

                    BedwarsGameStartedEvent startedEvent = new BedwarsGameStartedEvent(this);
                    Main.getInstance().getServer().getPluginManager().callEvent(startedEvent);
                    Main.getInstance().getServer().getPluginManager().callEvent(statusE);
                    updateScoreboard();
                    Debug.info(name + ": game prepared");
                }
            }
            // Phase 6.2: If status is same as before
        } else {
            // Phase 6.2.1: On game tick (if not interrupted by a change of status)
            if (status == GameStatus.RUNNING && tick.getNextStatus() == GameStatus.RUNNING) {
                int runningTeams = 0;
                for (CurrentTeam t : teamsInGame) {
                    runningTeams += t.isAlive() ? 1 : 0;
                }
                if (runningTeams <= 1) {
                    if (runningTeams == 1) {
                        CurrentTeam winner = null;
                        for (CurrentTeam t : teamsInGame) {
                            if (t.isAlive()) {
                                winner = t;
                                String time = getFormattedTimeLeft(gameTime - countdown);
                                String message = i18nc("team_win", customPrefix)
                                        .replace("%team%", TeamColor.fromApiColor(t.getColor()).chatColor + t.getName())
                                        .replace("%time%", time);
                                String subtitle = i18n("team_win", false)
                                        .replace("%team%", TeamColor.fromApiColor(t.getColor()).chatColor + t.getName())
                                        .replace("%time%", time);
                                boolean madeRecord = processRecord(t, gameTime - countdown);
                                for (GamePlayer player : players) {
                                    player.player.sendMessage(message);
                                    if (getPlayerTeam(player) == t) {
                                        Title.send(player.player, i18nonly("you_won"), subtitle);
                                        Main.depositPlayer(player.player, Main.getVaultWinReward());

                                        SpawnEffects.spawnEffect(this, player.player, "game-effects.end");

                                        if (Main.isPlayerStatisticsEnabled()) {
                                            PlayerStatistic statistic = Main.getPlayerStatisticsManager()
                                                    .getStatistic(player.player);
                                            statistic.addWins(1);
                                            statistic.addScore(Main.getConfigurator().config.getInt("statistics.scores.win", 50));

                                            if (madeRecord) {
                                                statistic.addScore(Main.getConfigurator().config.getInt("statistics.scores.record", 100));
                                            }

                                            if (Main.isHologramsEnabled()) {
                                                Main.getHologramInteraction().updateHolograms(player.player);
                                            }

                                            if (Main.getConfigurator().config
                                                    .getBoolean("statistics.show-on-game-end")) {
                                                StatsCommand.sendStats(player.player, Main.getPlayerStatisticsManager().getStatistic(player.player));
                                            }

                                        }

                                        if (Main.getConfigurator().config.getBoolean("rewards.enabled")) {
                                            final Player pl = player.player;
                                            new BukkitRunnable() {

                                                @Override
                                                public void run() {
                                                    if (Main.isPlayerStatisticsEnabled()) {
                                                        PlayerStatistic statistic = Main.getPlayerStatisticsManager()
                                                                .getStatistic(player.player);
                                                        Game.this.dispatchRewardCommands("player-win", pl,
                                                                statistic.getScore());
                                                    } else {
                                                        Game.this.dispatchRewardCommands("player-win", pl, 0);
                                                    }
                                                }

                                            }.runTaskLater(Main.getInstance(), (2 + postGameWaiting) * 20);
                                        }
                                    } else {
                                        Title.send(player.player, i18n("you_lost", false), subtitle);

                                        if (Main.isPlayerStatisticsEnabled() && Main.isHologramsEnabled()) {
                                            Main.getHologramInteraction().updateHolograms(player.player);
                                        }
                                    }
                                }
                                break;
                            }
                        }

                        BedwarsGameEndingEvent endingEvent = new BedwarsGameEndingEvent(this, winner);
                        Bukkit.getPluginManager().callEvent(endingEvent);
                        Main.getInstance().getServer().getPluginManager().callEvent(statusE);
                        Debug.info(name + ": game is ending");

                        tick.setNextCountdown(postGameWaiting);
                        tick.setNextStatus(GameStatus.GAME_END_CELEBRATING);
                    } else {
                        tick.setNextStatus(GameStatus.REBUILDING);
                        tick.setNextCountdown(0);
                    }
                } else if (countdown != gameTime /* Prevent spawning resources on game start */) {
                    for (ItemSpawner spawner : spawners) {
                        CurrentTeam spawnerTeam = getCurrentTeamFromTeam(spawner.getTeam());
                        if (configurationContainer.getOrDefault(ConfigurationContainer.STOP_TEAM_SPAWNERS_ON_DIE, Boolean.class, false) && spawner.getTeam() != null && spawnerTeam == null) {
                            continue; // team of this spawner is not available. Fix #147
                        }

                        ItemSpawnerType type = spawner.type;
                        int cycle = type.getInterval();
                        /*
                         * Calculate resource spawn from elapsedTime, not from remainingTime/countdown
                         */
                        int elapsedTime = gameTime - countdown;

                        if (spawner.getHologramEnabled()) {
                            if (configurationContainer.getOrDefault(ConfigurationContainer.SPAWNER_HOLOGRAMS, Boolean.class, false)
                                    && configurationContainer.getOrDefault(ConfigurationContainer.SPAWNER_COUNTDOWN_HOLOGRAM, Boolean.class, false)
                                    && !spawner.spawnerIsFullHologram) {
                                if (cycle > 1) {
                                    int modulo = cycle - elapsedTime % cycle;
                                    countdownHolograms.get(spawner).setLine(1,
                                            i18nonly("countdown_spawning").replace("%seconds%", Integer.toString(modulo)));
                                } else if (spawner.rerenderHologram) {
                                    countdownHolograms.get(spawner).setLine(1, i18nonly("every_second_spawning"));
                                    spawner.rerenderHologram = false;
                                }
                            }
                        }

                        if (spawnerTeam != null) {
                            if (configurationContainer.getOrDefault(ConfigurationContainer.STOP_TEAM_SPAWNERS_ON_DIE, Boolean.class, false) && (spawnerTeam.isDead())) {
                                continue;
                            }
                        }

                        if ((elapsedTime % cycle) == 0) {
                            int calculatedStack = 1;
                            double currentLevel = spawner.getCurrentLevel();
                            calculatedStack = (int) currentLevel;

                            /* Allow half level */
                            if ((currentLevel % 1) != 0) {
                                int a = elapsedTime / cycle;
                                if ((a % 2) == 0) {
                                    calculatedStack++;
                                }
                            }

                            BedwarsResourceSpawnEvent resourceSpawnEvent = new BedwarsResourceSpawnEvent(this, spawner,
                                    type.getStack(calculatedStack));
                            Main.getInstance().getServer().getPluginManager().callEvent(resourceSpawnEvent);

                            if (resourceSpawnEvent.isCancelled()) {
                                continue;
                            }

                            ItemStack resource = resourceSpawnEvent.getResource();

                            resource.setAmount(spawner.nextMaxSpawn(resource.getAmount(), countdownHolograms.get(spawner)));

                            if (resource.getAmount() > 0) {
                                Location loc = spawner.getLocation().clone().add(0, 0.05, 0);
                                Item item = loc.getWorld().dropItem(loc, resource);
                                double spread = type.getSpread();
                                if (spread != 1.0) {
                                    item.setVelocity(item.getVelocity().multiply(spread));
                                }
                                item.setPickupDelay(0);
                                spawner.add(item);
                            }
                        }
                    }
                }
            }
        }

        // Phase 7: Update status and countdown for next tick
        countdown = tick.getNextCountdown();
        status = tick.getNextStatus();

        // Phase 8: Check if game end celebrating started and remove title on bossbar
        if (status == GameStatus.GAME_END_CELEBRATING && previousStatus != status) {
            if (statusbar instanceof BossBar) {
                BossBar bossbar = (BossBar) statusbar;
                bossbar.setMessage(" ");
            }
        }

        // Phase 9: Check if status is rebuilding and rebuild game
        if (status == GameStatus.REBUILDING) {
            BedwarsGameEndEvent event = new BedwarsGameEndEvent(this);
            Main.getInstance().getServer().getPluginManager().callEvent(event);
            Main.getInstance().getServer().getPluginManager().callEvent(statusE);

            String message = i18nc("game_end", customPrefix);
            for (GamePlayer player : (List<GamePlayer>) ((ArrayList<GamePlayer>) players).clone()) {
                player.player.sendMessage(message);
                player.changeGame(null);

                if (Main.getConfigurator().config.getBoolean("rewards.enabled")) {
                    final Player pl = player.player;
                    new BukkitRunnable() {

                        @Override
                        public void run() {
                            if (Main.isPlayerStatisticsEnabled()) {
                                PlayerStatistic statistic = Main.getPlayerStatisticsManager()
                                        .getStatistic(player.player);
                                Game.this.dispatchRewardCommands("player-end-game", pl, statistic.getScore());
                            } else {
                                Game.this.dispatchRewardCommands("player-end-game", pl, 0);
                            }
                        }

                    }.runTaskLater(Main.getInstance(), 40);
                }
            }

            if (status == GameStatus.REBUILDING) { // If status is still rebuilding
                rebuild();
            }

            if (isBungeeEnabled()) {
                preServerRestart = true;

                if (!getConnectedPlayers().isEmpty()) {
                    kickAllPlayers();
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (Main.getConfigurator().config.getBoolean("bungee.serverRestart")) {
                            BedWarsServerRestartEvent serverRestartEvent = new BedWarsServerRestartEvent();
                            Main.getInstance().getServer().getPluginManager().callEvent(serverRestartEvent);

                            Main.getInstance().getServer()
                                    .dispatchCommand(Main.getInstance().getServer().getConsoleSender(), "restart");
                        } else if (Main.getConfigurator().config.getBoolean("bungee.serverStop")) {
                            Bukkit.shutdown();
                        }
                    }

                }.runTaskLater(Main.getInstance(), 30L);
            }
        }
    }

    public void rebuild() {
        if (experimentalBoard != null) {
            experimentalBoard.forceStop();
            experimentalBoard = null;
        }
        Debug.info(name + ": rebuilding starts");
        teamsInGame.clear();
        activeSpecialItems.clear();
        activeDelays.clear();

        BedwarsPreRebuildingEvent preRebuildingEvent = new BedwarsPreRebuildingEvent(this);
        Main.getInstance().getServer().getPluginManager().callEvent(preRebuildingEvent);

        for (ItemSpawner spawner : spawners) {
            spawner.currentLevel = spawner.startLevel;
            spawner.spawnedItems.clear();
            spawner.destroy();
        }
        for (GameStore store : gameStore) {
            LivingEntity villager = store.kill();
            if (villager != null) {
                Main.unregisterGameEntity(villager);
            }
        }

        region.regen();
        // Remove items
        for (Entity e : this.world.getEntities()) {
            if (GameCreator.isInArea(e.getLocation(), pos1, pos2)) {
                if (e instanceof Item) {
                    removeEntityAsync(e);
                }

                if (e instanceof ArmorStand) {
                    final String customName = e.getCustomName();
                    if (customName != null && customName.equals(ItemSpawner.ARMOR_STAND_DISPLAY_NAME_HIDDEN)) {
                        removeEntityAsync(e);
                    }
                }
            }
        }

        // Chest clearing
        for (Map.Entry<Location, ItemStack[]> entry : usedChests.entrySet()) {
            Location location = entry.getKey();
            Chunk chunk = location.getChunk();
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            Block block = location.getBlock();
            ItemStack[] contents = entry.getValue();
            if (block.getState() instanceof InventoryHolder) {
                InventoryHolder chest = (InventoryHolder) block.getState();
                chest.getInventory().setContents(contents);
            }
        }
        usedChests.clear();

        // Clear fake ender chests
        for (Inventory inv : fakeEnderChests.values()) {
            inv.clear();
        }
        fakeEnderChests.clear();

        // Remove remaining entities registered by other plugins
        for (Entity entity : Main.getGameEntities(this)) {
            Chunk chunk = entity.getLocation().getChunk();
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            entity.remove();
            Main.unregisterGameEntity(entity);
        }

        // Holograms destroy
        for (Hologram holo : createdHolograms) {
            holo.destroy();
        }
        createdHolograms.clear();
        countdownHolograms.clear();

        UpgradeRegistry.clearAll(this);

        BedwarsPostRebuildingEvent postRebuildingEvent = new BedwarsPostRebuildingEvent(this);
        Main.getInstance().getServer().getPluginManager().callEvent(postRebuildingEvent);

        this.status = this.afterRebuild;
        this.countdown = -1;
        updateSigns();
        cancelTask();
        Debug.info(name + ": rebuilding ends");

    }

    public boolean processRecord(CurrentTeam t, int wonTime) {
        int time = Main.getConfigurator().recordConfig.getInt("record." + this.getName() + ".time", Integer.MAX_VALUE);
        if (time > wonTime) {
            Main.getConfigurator().recordConfig.set("record." + this.getName() + ".time", wonTime);
            Main.getConfigurator().recordConfig.set("record." + this.getName() + ".team",
                    t.teamInfo.color.chatColor + t.teamInfo.name);
            List<String> winners = new ArrayList<String>();
            for (GamePlayer p : t.players) {
                winners.add(p.player.getName());
            }
            Main.getConfigurator().recordConfig.set("record." + this.getName() + ".winners", winners);
            try {
                Main.getConfigurator().recordConfig.save(Main.getConfigurator().recordFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void runTask() {
        if (task != null) {
            if (Bukkit.getScheduler().isQueued(task.getTaskId())) {
                task.cancel();
            }
            task = null;
        }
        task = (new BukkitRunnable() {

            public void run() {
                Game.this.run();
            }

        }.runTaskTimer(Main.getInstance(), 0, 20));
    }

    private void cancelTask() {
        if (task != null) {
            if (Bukkit.getScheduler().isQueued(task.getTaskId())) {
                task.cancel();
            }
            task = null;
        }
    }

    public void selectTeam(GamePlayer playerGameProfile, String displayName) {
        if (status == GameStatus.WAITING) {
            displayName = ChatColor.stripColor(displayName);
            playerGameProfile.player.closeInventory();
            for (Team team : teams) {
                if (displayName.equals(team.name)) {
                    internalTeamJoin(playerGameProfile, team);
                    break;
                }
            }
        }
    }

    public void updateScoreboard() {
        if (!configurationContainer.getOrDefault(ConfigurationContainer.GAME_SCOREBOARD, Boolean.class, false)) {
            return;
        }

        if (Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false)) {
            return;
        }

        Objective obj = this.gameScoreboard.getObjective("display");
        if (obj == null) {
            obj = this.gameScoreboard.registerNewObjective("display", "dummy");
        }

        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.setDisplayName(this.formatScoreboardTitle());

        for (CurrentTeam team : teamsInGame) {
            this.gameScoreboard.resetScores(this.formatScoreboardTeam(team, false, false));
            this.gameScoreboard.resetScores(this.formatScoreboardTeam(team, false, true));
            this.gameScoreboard.resetScores(this.formatScoreboardTeam(team, true, false));

            Score score = obj.getScore(this.formatScoreboardTeam(team, !team.isBed, team.isBed && "RESPAWN_ANCHOR".equals(team.teamInfo.bed.getBlock().getType().name()) && Player116ListenerUtils.isAnchorEmpty(team.teamInfo.bed.getBlock())));
            score.setScore(team.players.size());
        }

        for (GamePlayer player : players) {
            player.player.setScoreboard(gameScoreboard);
        }
    }

    private String formatScoreboardTeam(CurrentTeam team, boolean destroy, boolean empty) {
        if (team == null) {
            return "";
        }

        return Main.getConfigurator().config.getString("scoreboard.teamTitle")
                .replace("%color%", team.teamInfo.color.chatColor.toString()).replace("%team%", team.teamInfo.name)
                .replace("%bed%", destroy ? bedLostString() : (empty ? anchorEmptyString() : bedExistString()));
    }

    private void updateScoreboardTimer() {
        if (this.status != GameStatus.RUNNING || !configurationContainer.getOrDefault(ConfigurationContainer.GAME_SCOREBOARD, Boolean.class, false)) {
            return;
        }

        if (Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false)) {
            return;
        }

        Objective obj = this.gameScoreboard.getObjective("display");
        if (obj == null) {
            obj = this.gameScoreboard.registerNewObjective("display", "dummy");
        }

        obj.setDisplayName(this.formatScoreboardTitle());

        for (GamePlayer player : players) {
            player.player.setScoreboard(gameScoreboard);
        }
    }

    public String formatScoreboardTitle() {
        return Objects.requireNonNull(Main.getConfigurator().config.getString("scoreboard.title"))
                .replace("%game%", this.name)
                .replace("%time%", this.getFormattedTimeLeft());
    }

    public String getFormattedTimeLeft() {
        return getFormattedTimeLeft(this.countdown);
    }

    public String getFormattedTimeLeft(int countdown) {
        int min;
        int sec;
        String minStr;
        String secStr;

        min = (int) Math.floor(countdown / 60);
        sec = countdown % 60;

        minStr = (min < 10) ? "0" + min : String.valueOf(min);
        secStr = (sec < 10) ? "0" + sec : String.valueOf(sec);

        return minStr + ":" + secStr;
    }

    public void updateSigns() {
        final FileConfiguration config = Main.getConfigurator().config;
        final List<SignBlock> gameSigns = Main.getSignManager().getSignsForName(this.name);

        if (gameSigns.isEmpty()) {
            return;
        }

        String statusLine = "";
        String playersLine = "";
        MaterialSearchEngine.Result blockBehindMaterial = null;
        switch (status) {
            case DISABLED:
                statusLine = i18nonly("sign_status_disabled");
                playersLine = i18nonly("sign_status_disabled_players");
                blockBehindMaterial = MiscUtils.getMaterialFromString(config.getString("sign.block-behind.game-disabled"), "RED_STAINED_GLASS");
                break;
            case REBUILDING:
                statusLine = i18nonly("sign_status_rebuilding");
                playersLine = i18nonly("sign_status_rebuilding_players");
                blockBehindMaterial = MiscUtils.getMaterialFromString(config.getString("sign.block-behind.rebuilding"), "BROWN_STAINED_GLASS");
                break;
            case RUNNING:
            case GAME_END_CELEBRATING:
                statusLine = i18nonly("sign_status_running");
                playersLine = i18nonly("sign_status_running_players");
                blockBehindMaterial = MiscUtils.getMaterialFromString(config.getString("sign.block-behind.in-game"), "GREEN_STAINED_GLASS");
                break;
            case WAITING:
                statusLine = i18nonly("sign_status_waiting");
                playersLine = i18nonly("sign_status_waiting_players");
                blockBehindMaterial = MiscUtils.getMaterialFromString(config.getString("sign.block-behind.waiting"), "ORANGE_STAINED_GLASS");
                break;
        }

        playersLine = playersLine.replace("%players%", Integer.toString(players.size()));
        playersLine = playersLine.replace("%maxplayers%", Integer.toString(calculatedMaxPlayers));

        final List<String> texts = new ArrayList<>(Main.getConfigurator().config.getStringList("sign.lines"));

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            texts.set(i, text.replaceAll("%arena%", this.getName()).replaceAll("%status%", statusLine)
                    .replaceAll("%players%", playersLine));
        }

        for (SignBlock signBlock : gameSigns) {
            if (signBlock.getLocation().getChunk().isLoaded()) {
                BlockState blockState = signBlock.getLocation().getBlock().getState();
                if (blockState instanceof Sign) {
                    Sign sign = (Sign) blockState;
                    for (int i = 0; i < texts.size() && i < 4; i++) {
                        sign.setLine(i, texts.get(i));
                    }
                    sign.update();
                }

                if (config.getBoolean("sign.block-behind.enabled", false)) {
                    final Optional<Block> optionalBlock = signBlock.getBlockBehindSign();
                    if (optionalBlock.isPresent()) {
                        final Block glassBlock = optionalBlock.get();
                        glassBlock.setType(blockBehindMaterial.getMaterial());
                        if (Main.isLegacy()) {
                            try {
                                // The method is no longer in API, but in legacy versions exists
                                Block.class.getMethod("setData", byte.class).invoke(glassBlock, (byte) blockBehindMaterial.getDamage());
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateLobbyScoreboard() {
        if (status != GameStatus.WAITING || !configurationContainer.getOrDefault(ConfigurationContainer.LOBBY_SCOREBOARD, Boolean.class, false)) {
            return;
        }

        if (Main.getConfigurator().config.getBoolean("experimental.new-scoreboard-system.enabled", false)) {
            return;
        }

        Objective obj = gameScoreboard.getObjective("lobby");
        if (obj == null) {
            obj = gameScoreboard.registerNewObjective("lobby", "dummy");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setDisplayName(this.formatLobbyScoreboardString(
                    Main.getConfigurator().config.getString("lobby-scoreboard.title", "§eBEDWARS")));
        }

        List<String> rows = Main.getConfigurator().config.getStringList("lobby-scoreboard.content");
        if (rows.isEmpty()) {
            return;
        }

        rows = resizeAndMakeUnique(rows);

        //reset only scores that are changed instead of resetting all entries every tick
        //helps resolve scoreboard flickering
        int i = 15;
        for (String row : rows) {
            try {
                final String element = formatLobbyScoreboardString(row);
                final Score score = obj.getScore(element);

                if (score.getScore() != i) {
                    score.setScore(i);
                    for (String entry : gameScoreboard.getEntries()) {
                        if (obj.getScore(entry).getScore() == i && !entry.equalsIgnoreCase(element)) {
                            gameScoreboard.resetScores(entry);
                        }
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException e){
                e.printStackTrace();
            }
            i--;
        }


        players.forEach(player -> player.player.setScoreboard(gameScoreboard));
    }

    public List<String> resizeAndMakeUnique(List<String> lines) {
        final List<String> content = new ArrayList<>();

        lines.forEach(line -> {
            String copy = line;
            if (copy == null) {
                copy = " ";
            }

            //avoid exceptions returned by getScore()
            if (copy.length() > 40) {
                copy = copy.substring(40);
            }


            final StringBuilder builder = new StringBuilder(copy);
            while (content.contains(builder.toString())) {
                builder.append(" ");
            }
            content.add(builder.toString());
        });

        if(content.size() > 15) {
            return content.subList(0, 15);
        }
        return content;
    }

    public String formatLobbyScoreboardString(String str) {
        String finalStr = str;

        finalStr = finalStr.replace("%arena%", name);
        finalStr = finalStr.replace("%players%", String.valueOf(players.size()));
        finalStr = finalStr.replace("%maxplayers%", String.valueOf(calculatedMaxPlayers));

        return finalStr;
    }

    @Override
    public void selectPlayerTeam(Player player, org.screamingsandals.bedwars.api.Team team) {
        if (!Main.isPlayerInGame(player)) {
            return;
        }
        GamePlayer profile = Main.getPlayerGameProfile(player);
        if (profile.getGame() != this) {
            return;
        }

        selectTeam(profile, team.getName());
    }

    @Override
    public World getGameWorld() {
        return world;
    }

    @Override
    public Location getSpectatorSpawn() {
        return specSpawn;
    }

    @Override
    public int countConnectedPlayers() {
        return players.size();
    }

    @Override
    public List<Player> getConnectedPlayers() {
        List<Player> playerList = new ArrayList<>();
        for (GamePlayer player : players) {
            playerList.add(player.player);
        }
        return playerList;
    }

    @Override
    public List<org.screamingsandals.bedwars.api.Team> getAvailableTeams() {
        return new ArrayList<>(teams);
    }

    @Override
    public List<RunningTeam> getRunningTeams() {
        return new ArrayList<>(teamsInGame);
    }

    @Override
    public RunningTeam getTeamOfPlayer(Player player) {
        if (!Main.isPlayerInGame(player)) {
            return null;
        }
        return getPlayerTeam(Main.getPlayerGameProfile(player));
    }

    @Override
    public boolean isLocationInArena(Location location) {
        return GameCreator.isInArea(location, pos1, pos2);
    }

    @Override
    public World getLobbyWorld() {
        return lobbySpawn.getWorld();
    }

    @Override
    public int getLobbyCountdown() {
        return pauseCountdown;
    }

    @Override
    public CurrentTeam getTeamOfChest(Location location) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isTeamChestRegistered(location)) {
                return team;
            }
        }
        return null;
    }

    @Override
    public CurrentTeam getTeamOfChest(Block block) {
        for (CurrentTeam team : teamsInGame) {
            if (team.isTeamChestRegistered(block)) {
                return team;
            }
        }
        return null;
    }

    public void addChestForFutureClear(Location loc, Inventory inventory) {
        if (!usedChests.containsKey(loc)) {
            ItemStack[] contents = inventory.getContents();
            ItemStack[] clone = new ItemStack[contents.length];
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack != null)
                    clone[i] = stack.clone();
            }
            usedChests.put(loc, clone);
        }
    }

    @Override
    public int getMaxPlayers() {
        return calculatedMaxPlayers;
    }

    @Override
    public int countGameStores() {
        return gameStore.size();
    }

    @Override
    public int countAvailableTeams() {
        return teams.size();
    }

    @Override
    public int countRunningTeams() {
        return teamsInGame.size();
    }

    @Override
    public boolean isPlayerInAnyTeam(Player player) {
        return getTeamOfPlayer(player) != null;
    }

    @Override
    public boolean isPlayerInTeam(Player player, RunningTeam team) {
        return getTeamOfPlayer(player) == team;
    }

    @Override
    public int countTeamChests() {
        int total = 0;
        for (CurrentTeam team : teamsInGame) {
            total += team.countTeamChests();
        }
        return total;
    }

    @Override
    public int countTeamChests(RunningTeam team) {
        return team.countTeamChests();
    }

    @Override
    public List<SpecialItem> getActivedSpecialItems() {
        return new ArrayList<>(activeSpecialItems);
    }

    @Override
    public List<SpecialItem> getActivedSpecialItems(Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (type.isInstance(item)) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfTeam(org.screamingsandals.bedwars.api.Team team) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfTeam(org.screamingsandals.bedwars.api.Team team,
                                                          Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (type.isInstance(item) && item.getTeam() == team) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfTeam(org.screamingsandals.bedwars.api.Team team) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team) {
                return item;
            }
        }
        return null;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfTeam(org.screamingsandals.bedwars.api.Team team,
                                                        Class<? extends SpecialItem> type) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getTeam() == team && type.isInstance(item)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfPlayer(Player player) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public List<SpecialItem> getActivedSpecialItemsOfPlayer(Player player, Class<? extends SpecialItem> type) {
        List<SpecialItem> items = new ArrayList<>();
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player && type.isInstance(item)) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfPlayer(Player player) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player) {
                return item;
            }
        }
        return null;
    }

    @Override
    public SpecialItem getFirstActivedSpecialItemOfPlayer(Player player, Class<? extends SpecialItem> type) {
        for (SpecialItem item : activeSpecialItems) {
            if (item.getPlayer() == player && type.isInstance(item)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void registerSpecialItem(SpecialItem item) {
        if (!activeSpecialItems.contains(item)) {
            activeSpecialItems.add(item);
        }
    }

    @Override
    public void unregisterSpecialItem(SpecialItem item) {
        activeSpecialItems.remove(item);
    }

    @Override
    public boolean isRegisteredSpecialItem(SpecialItem item) {
        return activeSpecialItems.contains(item);
    }

    @Override
    public List<DelayFactory> getActiveDelays() {
        return new ArrayList<>(activeDelays);
    }

    @Override
    public List<DelayFactory> getActiveDelaysOfPlayer(Player player) {
        List<DelayFactory> delays = new ArrayList<>();
        for (DelayFactory delay : activeDelays) {
            if (delay.getPlayer() == player) {
                delays.add(delay);
            }
        }
        return delays;
    }

    @Override
    public DelayFactory getActiveDelay(Player player, Class<? extends SpecialItem> specialItem) {
        for (DelayFactory delayFactory : getActiveDelaysOfPlayer(player)) {
            if (specialItem.isInstance(delayFactory.getSpecialItem())) {
                return delayFactory;
            }
        }
        return null;
    }

    @Override
    public void registerDelay(DelayFactory delayFactory) {
        if (!activeDelays.contains(delayFactory)) {
            activeDelays.add(delayFactory);
        }
    }

    @Override
    public void unregisterDelay(DelayFactory delayFactory) {
        activeDelays.remove(delayFactory);
    }

    @Override
    public boolean isDelayActive(Player player, Class<? extends SpecialItem> specialItem) {
        for (DelayFactory delayFactory : getActiveDelaysOfPlayer(player)) {
            if (specialItem.isInstance(delayFactory.getSpecialItem())) {
                return delayFactory.getDelayActive();
            }
        }
        return false;
    }

    @Override
    public ArenaTime getArenaTime() {
        return arenaTime;
    }

    public void setArenaTime(ArenaTime arenaTime) {
        this.arenaTime = arenaTime;
    }

    @Override
    public WeatherType getArenaWeather() {
        return arenaWeather;
    }

    public void setArenaWeather(WeatherType arenaWeather) {
        this.arenaWeather = arenaWeather;
    }

    @Override
    public BarColor getLobbyBossBarColor() {
        return this.lobbyBossBarColor;
    }

    public void setLobbyBossBarColor(BarColor color) {
        this.lobbyBossBarColor = color;
    }

    @Override
    public BarColor getGameBossBarColor() {
        return this.gameBossBarColor;
    }

    public void setGameBossBarColor(BarColor color) {
        this.gameBossBarColor = color;
    }

    @Override
    public List<org.screamingsandals.bedwars.api.game.ItemSpawner> getItemSpawners() {
        return new ArrayList<>(spawners);
    }

    public void dispatchRewardCommands(String type, Player player, int score) {
        if (!Main.getConfigurator().config.getBoolean("rewards.enabled")) {
            return;
        }

        List<String> list = Main.getConfigurator().config.getStringList("rewards." + type);
        for (String command : list) {
            command = command.replaceAll("\\{player}", player.getName());
            command = command.replaceAll("\\{score}", Integer.toString(score));
            command = command.startsWith("/") ? command.substring(1) : command;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    @Override
    public void selectPlayerRandomTeam(Player player) {
        joinRandomTeam(Main.getPlayerGameProfile(player));
    }

    @Override
    public StatusBar getStatusBar() {
        return statusbar;
    }

    public void kickAllPlayers() {
        for (Player player : getConnectedPlayers()) {
            leaveFromGame(player);
        }
    }

    @Override
    public boolean getBungeeEnabled() {
        return Main.getConfigurator().config.getBoolean("bungee.enabled");
    }

    @Override
    public boolean isEntityShop(Entity entity) {
        for (GameStore store : gameStore) {
            if (store.getEntity().equals(entity)) {
                return true;
            }
        }
        return false;
    }

    public RespawnProtection addProtectedPlayer(Player player) {
        int time = Main.getConfigurator().config.getInt("respawn.protection-time", 10);

        RespawnProtection respawnProtection = new RespawnProtection(this, player, time);
        respawnProtectionMap.put(player, respawnProtection);

        return respawnProtection;
    }

    public void removeProtectedPlayer(Player player) {
        RespawnProtection respawnProtection = respawnProtectionMap.get(player);
        if (respawnProtection == null) {
            return;
        }

        try {
            respawnProtection.cancel();
        } catch (Exception ignored) {
        }

        respawnProtectionMap.remove(player);
    }

    @Override
    public boolean isProtectionActive(Player player) {
        return (respawnProtectionMap.containsKey(player));
    }

    public List<GamePlayer> getPlayersWithoutVIP() {
        List<GamePlayer> gamePlayerList = new ArrayList<>(this.players);
        gamePlayerList.removeIf(GamePlayer::canJoinFullGame);

        return gamePlayerList;
    }

    public Inventory getFakeEnderChest(GamePlayer player) {
        if (!fakeEnderChests.containsKey(player)) {
            fakeEnderChests.put(player, Bukkit.createInventory(player.player, InventoryType.ENDER_CHEST));
        }
        return fakeEnderChests.get(player);
    }

    @Override
    public int getPostGameWaiting() {
        return this.postGameWaiting;
    }

    public void setPostGameWaiting(int time) {
        this.postGameWaiting = time;
    }

    @Override
    public String getCustomPrefix() {
        return customPrefix;
    }

    @Override
    public boolean isInEditMode() {
        return AdminCommand.gc.containsKey(name);
    }

    public void setCustomPrefix(String customPrefix) {
        this.customPrefix = customPrefix;
    }
}
package misat11.bw;

import misat11.bw.api.BedwarsAPI;
import misat11.bw.api.GameStatus;
import misat11.bw.api.GameStore;
import misat11.bw.api.utils.ColorChanger;
import misat11.bw.commands.*;
import misat11.bw.database.DatabaseManager;
import misat11.bw.game.Game;
import misat11.bw.game.GamePlayer;
import misat11.bw.game.ItemSpawnerType;
import misat11.bw.game.TeamColor;
import misat11.bw.holograms.HolographicDisplaysInteraction;
import misat11.bw.holograms.IHologramInteraction;
import misat11.bw.listener.*;
import misat11.bw.placeholderapi.BedwarsExpansion;
import misat11.bw.special.SpecialRegister;
import misat11.bw.statistics.PlayerStatisticManager;
import misat11.bw.utils.Configurator;
import misat11.bw.utils.GameSign;
import misat11.bw.utils.ShopMenu;
import misat11.bw.utils.SignManager;
import misat11.lib.lang.I18n;
import misat11.lib.nms.NMSUtils;
import misat11.lib.sgui.InventoryListener;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.update.spiget.SpigetUpdate;
import org.inventivetalent.update.spiget.UpdateCallback;
import org.inventivetalent.update.spiget.comparator.VersionComparator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static misat11.lib.lang.I18n.i18n;

public class Main extends JavaPlugin implements BedwarsAPI {
	private static Main instance;
	private String version, nmsVersion;
	private boolean isSpigot, snapshot, isVault, isLegacy, isNMS;
	private int versionNumber = 0;
	private Economy econ = null;
	private HashMap<String, Game> games = new HashMap<>();
	private HashMap<Player, GamePlayer> playersInGame = new HashMap<>();
	private HashMap<Entity, Game> entitiesInGame = new HashMap<>();
	private Configurator configurator;
	private ShopMenu menu;
	private SignManager signManager;
	private HashMap<String, ItemSpawnerType> spawnerTypes = new HashMap<>();
	private DatabaseManager databaseManager;
	private PlayerStatisticManager playerStatisticsManager;
	private IHologramInteraction hologramInteraction;
	private HashMap<String, BaseCommand> commands;
	private SpigetUpdate updater;
	private ColorChanger colorChanger;
	public static List<String> autoColoredMaterials = new ArrayList<>();

	static {
		// ColorChanger list of materials
		autoColoredMaterials.add("WOOL");
		autoColoredMaterials.add("CARPET");
		autoColoredMaterials.add("CONCRETE");
		autoColoredMaterials.add("CONCRETE_POWDER");
		autoColoredMaterials.add("STAINED_CLAY"); // LEGACY ONLY
		autoColoredMaterials.add("TERRACOTTA"); // FLATTENING ONLY
		autoColoredMaterials.add("STAINED_GLASS");
		autoColoredMaterials.add("STAINED_GLASS_PANE");
	}

	public static Main getInstance() {
		return instance;
	}

	public static Configurator getConfigurator() {
		return instance.configurator;
	}

	public static String getVersion() {
		return instance.version;
	}

	public static boolean isSnapshot() {
		return instance.snapshot;
	}

	public static boolean isSpigot() {
		return instance.isSpigot;
	}

	public static boolean isVault() {
		return instance.isVault;
	}

	public static boolean isLegacy() {
		return instance.isLegacy;
	}

	public static boolean isNMS() {
		return instance.isNMS;
	}

	public static String getNMSVersion() {
		return isNMS() ? instance.nmsVersion : null;
	}

	public static void depositPlayer(Player player, double coins) {
		try {
			if (isVault() && instance.configurator.config.getBoolean("vault.enable")) {
				EconomyResponse response = instance.econ.depositPlayer(player, coins);
				if (response.transactionSuccess()) {
					player.sendMessage(i18n("vault_deposite").replace("%coins%", Double.toString(coins)).replace(
							"%currency%",
							(coins == 1 ? instance.econ.currencyNameSingular() : instance.econ.currencyNamePlural())));
				}
			}
		} catch (Throwable ignored) {
		}
	}

	public static int getVaultKillReward() {
		return instance.configurator.config.getInt("vault.reward.kill");
	}

	public static int getVaultWinReward() {
		return instance.configurator.config.getInt("vault.reward.win");
	}

	public static Game getGame(String string) {
		return instance.games.get(string);
	}

	public static boolean isGameExists(String string) {
		return instance.games.containsKey(string);
	}

	public static void addGame(Game game) {
		instance.games.put(game.getName(), game);
	}

	public static void removeGame(Game game) {
		instance.games.remove(game.getName());
	}

	public static Game getInGameEntity(Entity entity) {
		return instance.entitiesInGame.getOrDefault(entity, null);
	}

	public static void registerGameEntity(Entity entity, Game game) {
		instance.entitiesInGame.put(entity, game);
	}

	public static void unregisterGameEntity(Entity entity) {
		if (instance.entitiesInGame.containsKey(entity))
			instance.entitiesInGame.remove(entity);
	}

	public static boolean isPlayerInGame(Player player) {
		if (instance.playersInGame.containsKey(player))
			return instance.playersInGame.get(player).isInGame();
		return false;
	}

	public static GamePlayer getPlayerGameProfile(Player player) {
		if (instance.playersInGame.containsKey(player))
			return instance.playersInGame.get(player);
		GamePlayer gPlayer = new GamePlayer(player);
		instance.playersInGame.put(player, gPlayer);
		return gPlayer;
	}

	public static void unloadPlayerGameProfile(Player player) {
		if (instance.playersInGame.containsKey(player)) {
			instance.playersInGame.get(player).changeGame(null);
			instance.playersInGame.remove(player);
		}
	}

	public static boolean isPlayerGameProfileRegistered(Player player) {
		return instance.playersInGame.containsKey(player);
	}

	public static void sendGameListInfo(CommandSender player) {
		for (Game game : instance.games.values()) {
			player.sendMessage((game.getStatus() == GameStatus.DISABLED ? "§c" : "§a") + game.getName() + "§f "
					+ game.countPlayers());
		}
	}

	public static void openStore(Player player, GameStore store) {
		instance.menu.show(player, store);
	}

	public static boolean isFarmBlock(Material mat) {
		if (instance.configurator.config.getBoolean("farmBlocks.enable")) {
			List<String> list = (List<String>) instance.configurator.config.getList("farmBlocks.blocks");
			return list.contains(mat.name());
		}
		return false;
	}

	public static boolean isBreakableBlock(Material mat) {
		if (instance.configurator.config.getBoolean("breakable.enabled")) {
			List<String> list = (List<String>) instance.configurator.config.getList("breakable.blocks");
			return list.contains(mat.name());
		}
		return false;
	}

	public static boolean isCommandAllowedInGame(String commandPref) {
		if ("/bw".equals(commandPref) || "/bedwars".equals(commandPref)) {
			return true;
		}
		List<String> commands = instance.configurator.config.getStringList("allowed-commands");
		for (String comm : commands) {
			if (!comm.startsWith("/")) {
				comm = "/" + comm;
			}
			if (comm.equals(commandPref)) {
				return !instance.configurator.config.getBoolean("change-allowed-commands-to-blacklist", false);
			}
		}
		return instance.configurator.config.getBoolean("change-allowed-commands-to-blacklist", false);
	}

	public static boolean isSignRegistered(Location location) {
		return instance.signManager.isSignRegistered(location);
	}

	public static void unregisterSign(Location location) {
		instance.signManager.unregisterSign(location);
	}

	public static boolean registerSign(Location location, String game) {
		return instance.signManager.registerSign(location, game);
	}

	public static GameSign getSign(Location location) {
		return instance.signManager.getSign(location);
	}

	public static List<GameSign> getSignsForGame(Game game) {
		return instance.signManager.getSignsForGame(game);
	}

	public static ItemSpawnerType getSpawnerType(String key) {
		return instance.spawnerTypes.get(key);
	}

	public static List<String> getAllSpawnerTypes() {
		return new ArrayList<>(instance.spawnerTypes.keySet());
	}

	public static List<String> getGameNames() {
		List<String> list = new ArrayList<>();
		for (Game game : instance.games.values()) {
			list.add(game.getName());
		}
		return list;
	}

	public static DatabaseManager getDatabaseManager() {
		return instance.databaseManager;
	}

	public static PlayerStatisticManager getPlayerStatisticsManager() {
		return instance.playerStatisticsManager;
	}

	public static boolean isPlayerStatisticsEnabled() {
		return instance.configurator.config.getBoolean("statistics.enabled");
	}

	public static boolean isHologramsEnabled() {
		return instance.configurator.config.getBoolean("holograms.enabled") && instance.hologramInteraction != null;
	}

	public static IHologramInteraction getHologramInteraction() {
		return instance.hologramInteraction;
	}

	public static HashMap<String, BaseCommand> getCommands() {
		return instance.commands;
	}

	public static List<Entity> getGameEntities(Game game) {
		List<Entity> entityList = new ArrayList<>();
		for (Map.Entry<Entity, Game> entry : instance.entitiesInGame.entrySet()) {
			if (entry.getValue() == game) {
				entityList.add(entry.getKey());
			}
		}
		return entityList;
	}

	public static int getVersionNumber() {
	    return instance.versionNumber;
    }

	public void onEnable() {
		instance = this;
		version = this.getDescription().getVersion();
		snapshot = version.toLowerCase().contains("pre") || version.toLowerCase().contains("snapshot");
		isNMS = NMSUtils.NMS_BASED_SERVER;
		nmsVersion = NMSUtils.NMS_VERSION;
		isSpigot = NMSUtils.IS_SPIGOT_SERVER;
		colorChanger = new misat11.bw.utils.ColorChanger();

		if (!getServer().getPluginManager().isPluginEnabled("Vault")) {
			isVault = false;
		} else {
			setupEconomy();
			isVault = true;
		}

		String[] bukkitVersion = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
		for (int i = 0; i < 2; i++) {
			versionNumber += Integer.parseInt(bukkitVersion[i]) * (i == 0 ? 100 : 1);
		}

		isLegacy = versionNumber < 113;

		configurator = new Configurator(this);

		configurator.createFiles();

		I18n.load(this, configurator.config.getString("locale"));

		signManager = new SignManager(configurator.signconfig, configurator.signconfigf);

		databaseManager = new DatabaseManager(configurator.config.getString("database.host"),
				configurator.config.getInt("database.port"), configurator.config.getString("database.user"),
				configurator.config.getString("database.password"), configurator.config.getString("database.db"),
				configurator.config.getString("database.table-prefix", "bw_"));

		if (isPlayerStatisticsEnabled()) {
			playerStatisticsManager = new PlayerStatisticManager();
			playerStatisticsManager.initialize();
		}

		try {
			if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
				new BedwarsExpansion().register();
			}

			if (configurator.config.getBoolean("holograms.enabled")) {
				// Holographic Displays
				if (Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays")) {
					hologramInteraction = new HolographicDisplaysInteraction();
				}

				if (hologramInteraction != null) {
					hologramInteraction.loadHolograms();
				}
			}
		} catch (Throwable ignored) {
		}

		commands = new HashMap<>();
		new AddholoCommand();
		new AdminCommand();
		new AutojoinCommand();
		new HelpCommand();
		new JoinCommand();
		new LeaveCommand();
		new ListCommand();
		new RejoinCommand();
		new ReloadCommand();
		new RemoveholoCommand();
		new StatsCommand();
		new MainlobbyCommand();

		BwCommandsExecutor cmd = new BwCommandsExecutor();
		getCommand("bw").setExecutor(cmd);
		getCommand("bw").setTabCompleter(cmd);
		getServer().getPluginManager().registerEvents(new PlayerListener(), this);
		if (versionNumber >= 109) {
			getServer().getPluginManager().registerEvents(new Player19Listener(), this);
		}
		if (versionNumber >= 112) {
			getServer().getPluginManager().registerEvents(new Player112Listener(), this);
		}
		getServer().getPluginManager().registerEvents(new VillagerListener(), this);
		getServer().getPluginManager().registerEvents(new SignListener(), this);
		getServer().getPluginManager().registerEvents(new WorldListener(), this);
		getServer().getPluginManager().registerEvents(new InventoryListener(), this);
		
		NMSUtils.init(this);

		SpecialRegister.onEnable(this);

		getServer().getServicesManager().register(BedwarsAPI.class, this, this, ServicePriority.Normal);

		for (String spawnerN : configurator.config.getConfigurationSection("resources").getKeys(false)) {

			String name = Main.getConfigurator().config.getString("resources." + spawnerN + ".name");
			String translate = Main.getConfigurator().config.getString("resources." + spawnerN + ".translate");
			int interval = Main.getConfigurator().config.getInt("resources." + spawnerN + ".interval", 1);
			double spread = Main.getConfigurator().config.getDouble("resources." + spawnerN + ".spread");
			int damage = Main.getConfigurator().config.getInt("resources." + spawnerN + ".damage");
			String materialName = Main.getConfigurator().config.getString("resources." + spawnerN + ".material", "AIR");
			String colorName = Main.getConfigurator().config.getString("resources." + spawnerN + ".color", "WHITE");

			Material material = Material.valueOf(materialName);
			if (material == Material.AIR || material == null) {
				continue;
			}

			ChatColor color = ChatColor.valueOf(colorName);

			spawnerTypes.put(spawnerN.toLowerCase(), new ItemSpawnerType(spawnerN.toLowerCase(), name, translate,
					spread, material, color, interval, damage));
		}

		menu = new ShopMenu();

		if (getConfigurator().config.getBoolean("bungee.enabled")) {
			Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		}

		Bukkit.getConsoleSender().sendMessage("§c=====§f======  by Misat11");
		Bukkit.getConsoleSender()
				.sendMessage("§c+ Bed§fWars +  §6Version: " + version + " (API: " + getAPIVersion() + ")");
		Bukkit.getConsoleSender()
				.sendMessage("§c=====§f======  " + (snapshot ? "§cSNAPSHOT VERSION" : "§aSTABLE VERSION"));
		if (isVault) {
			Bukkit.getConsoleSender().sendMessage("§c[B§fW] §6Found Vault");
		}
		if (!isSpigot) {
			Bukkit.getConsoleSender()
					.sendMessage("§c[B§fW] §cWARNING: You are not using Spigot. Some features may not work properly.");
		}

		if (versionNumber < 109) {
			Bukkit.getConsoleSender().sendMessage(
					"§c[B§fW] §cIMPORTANT WARNING: You are using version older than 1.9! This version is not officially supported and some features may not work at all!");
		}

		File folder = new File(getDataFolder().toString(), "arenas");
		if (folder.exists()) {
			File[] listOfFiles = folder.listFiles();
			if (listOfFiles.length > 0) {
				for (int i = 0; i < listOfFiles.length; i++) {
					if (listOfFiles[i].isFile()) {
						Game.loadGame(listOfFiles[i]);
					}
				}
			}
		}

		try {
			// Fixing bugs created by third party plugin

			// PerWorldInventory
			if (Bukkit.getPluginManager().isPluginEnabled("PerWorldInventory")) {
				Plugin pwi = Bukkit.getPluginManager().getPlugin("PerWorldInventory");
				if (pwi.getClass().getName().equals("me.ebonjaeger.perworldinventory.PerWorldInventory")) {
					// Kotlin version
					getServer().getPluginManager().registerEvents(new PerWorldInventoryKotlinListener(), this);
				} else {
					// Legacy version
					getServer().getPluginManager().registerEvents(new PerWorldInventoryLegacyListener(), this);
				}
			}

		} catch (Throwable ignored) {
			// maybe something here can cause exception

		}

		updater = new SpigetUpdate(this, 63714);

		updater.setVersionComparator(VersionComparator.SEM_VER_SNAPSHOT);

		updater.checkForUpdate(new UpdateCallback() {
			@Override
			public void updateAvailable(String newVersion, String downloadUrl, boolean hasDirectDownload) {
				Bukkit.getConsoleSender().sendMessage("§c[B§fW] §aNew RELEASE version " + newVersion
						+ " of BedWars is available! Download it from " + downloadUrl);
			}

			@Override
			public void upToDate() {

			}
		});
	}

	public void onDisable() {
		if (signManager != null) {
			signManager.save();
		}
		for (Game game : games.values()) {
			game.stop();
		}
		this.getServer().getServicesManager().unregisterAll(this);

		if (isHologramsEnabled() && hologramInteraction != null) {
			hologramInteraction.unloadHolograms();
		}
	}

	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}

		econ = rsp.getProvider();
		return econ != null;
	}

	@Override
	public List<misat11.bw.api.Game> getGames() {
		return new ArrayList<>(games.values());
	}

	@Override
	public misat11.bw.api.Game getGameOfPlayer(Player player) {
		return isPlayerInGame(player) ? getPlayerGameProfile(player).getGame() : null;
	}

	@Override
	public boolean isGameWithNameExists(String name) {
		return games.containsKey(name);
	}

	@Override
	public misat11.bw.api.Game getGameByName(String name) {
		return games.get(name);
	}

	@Override
	public List<misat11.bw.api.ItemSpawnerType> getItemSpawnerTypes() {
		return new ArrayList<>(spawnerTypes.values());
	}

	@Override
	public misat11.bw.api.ItemSpawnerType getItemSpawnerTypeByName(String name) {
		return spawnerTypes.get(name);
	}

	@Override
	public boolean isItemSpawnerTypeRegistered(String name) {
		return spawnerTypes.containsKey(name);
	}

	@Override
	public boolean isEntityInGame(Entity entity) {
		return entitiesInGame.containsKey(entity);
	}

	@Override
	public misat11.bw.api.Game getGameOfEntity(Entity entity) {
		return entitiesInGame.get(entity);
	}

	@Override
	public void registerEntityToGame(Entity entity, misat11.bw.api.Game game) {
		if (!(game instanceof Game)) {
			return;
		}
		entitiesInGame.put(entity, (Game) game);
	}

	@Override
	public void unregisterEntityFromGame(Entity entity) {
		if (entitiesInGame.containsKey(entity)) {
			entitiesInGame.remove(entity);
		}
	}

	@Override
	public boolean isPlayerPlayingAnyGame(Player player) {
		return isPlayerInGame(player);
	}

	@Override
	public String getBedwarsVersion() {
		return version;
	}

	@Override
	public ColorChanger getColorChanger() {
		return colorChanger;
	}

	public static ItemStack applyColor(TeamColor color, ItemStack itemStack) {
		misat11.bw.api.TeamColor teamColor = color.toApiColor();
		return instance.getColorChanger().applyColor(teamColor, itemStack);
	}

	public static ItemStack applyColor(misat11.bw.api.TeamColor teamColor, ItemStack itemStack) {
		return instance.getColorChanger().applyColor(teamColor, itemStack);
	}

	@Override
	public misat11.bw.api.Game getFirstWaitingGame() {
		for (Game game : games.values()) {
			if (game.getStatus() == GameStatus.WAITING) {
				return game;
			}
		}
		return null;
	}
}

package misat11.bw.utils;

import misat11.bw.Main;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Configurator {

	public File configf, shopconfigf, signconfigf, recordconfigf;
	public FileConfiguration config, shopconfig, signconfig, recordconfig;

	public final File datafolder;
	public final Main main;

	public Configurator(Main main) {
		this.datafolder = main.getDataFolder();
		this.main = main;
	}

	private void configMigration() {
		if (config.getInt("version") < 2) {
			main.getLogger().info("Migrating config from version 1 to version 2 ...");
			if (Main.isLegacy()) {
				config.set("resources.bronze.material", "CLAY_BRICK");
			} else {
				config.set("resources.bronze.material", "BRICK");
			}
			config.set("resources.bronze.interval", config.getInt("spawners.bronze", 1));
			config.set("resources.bronze.name", "Bronze");
			config.set("resources.bronze.translate", "resource_bronze");
			config.set("resources.bronze.color", "DARK_RED");
			config.set("resources.bronze.spread", 1.0);

			config.set("resources.iron.material", "IRON_INGOT");
			config.set("resources.iron.interval", config.getInt("spawners.iron", 10));
			config.set("resources.iron.name", "Iron");
			config.set("resources.iron.translate", "resource_iron");
			config.set("resources.iron.color", "GRAY");
			config.set("resources.iron.spread", 1.0);

			config.set("resources.gold.material", "GOLD_INGOT");
			config.set("resources.gold.interval", config.getInt("spawners.gold", 20));
			config.set("resources.gold.name", "Gold");
			config.set("resources.gold.translate", "resource_gold");
			config.set("resources.gold.color", "GOLD");
			config.set("resources.gold.spread", 1.0);

			config.set("version", 2);

			config.set("spawners", null);

			try {
				config.save(configf);
			} catch (IOException e) {
				e.printStackTrace();
			}
			main.getLogger().info("Config successfully migrated from version 1 to version 2!");
		}

		if (shopconfig.getInt("version") < 2) {
			shopconfig.set("version", 2);
			if (shopconfig.isSet("shop-items")) {
				main.getLogger().info("Migrating shop.yml from version 1 to version 2 ...");

				List<Map<String, Object>> newData = new ArrayList<>();

				Set<String> s = Main.getConfigurator().shopconfig.getConfigurationSection("shop-items").getKeys(false);

				for (String i : s) {
					ConfigurationSection category = Main.getConfigurator().shopconfig
							.getConfigurationSection("shop-items." + i);
					ItemStack categoryItem = new ItemStack(Material.valueOf(category.getString("item")), 1,
							(short) category.getInt("damage", 0));
					ItemMeta categoryItemMeta = categoryItem.getItemMeta();
					categoryItemMeta.setLore(category.getStringList("lore"));
					categoryItemMeta.setDisplayName(category.getString("name"));
					categoryItem.setItemMeta(categoryItemMeta);

					Map<String, Object> map = new HashMap<>();

					map.put("stack", categoryItem);
					map.put("items", category.getList("items"));

					newData.add(map);
				}

				shopconfig.set("data", newData);

				shopconfig.set("shop-items", null);
			}

			try {
				shopconfig.save(shopconfigf);
			} catch (IOException e) {
				e.printStackTrace();
			}
			main.getLogger().info("Shop.yml successfully migrated from version 1 to version 2!");
		}
	}

	public void createFiles() {

		configf = new File(datafolder, "config.yml");
		shopconfigf = new File(datafolder, "shop.yml");
		signconfigf = new File(datafolder, "sign.yml");
		recordconfigf = new File(datafolder, "record.yml");

		if (!configf.exists()) {
			// Think about config.yml and config_legacy.yml
			if (Main.isLegacy()) {
				main.saveResource("config_legacy.yml", false);
				new File(datafolder, "config_legacy.yml").renameTo(configf);
			} else {
				main.saveResource("config.yml", false);
			}
		}
		if (!shopconfigf.exists()) {
			if (Main.isLegacy()) {
				main.saveResource("shop_legacy.yml", false);
				new File(datafolder, "shop_legacy.yml").renameTo(shopconfigf);
			} else {
				main.saveResource("shop.yml", false);
			}
		}
		if (!signconfigf.exists()) {
			main.saveResource("sign.yml", false);
		}
		if (!recordconfigf.exists()) {
			main.saveResource("record.yml", false);
		}
		config = new YamlConfiguration();
		shopconfig = new YamlConfiguration();
		signconfig = new YamlConfiguration();
		recordconfig = new YamlConfiguration();
		try {
			config.load(configf);
			shopconfig.load(shopconfigf);
			signconfig.load(signconfigf);
			recordconfig.load(recordconfigf);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}

		configMigration();

		AtomicBoolean modify = new AtomicBoolean(false);
		checkOrSetConfig(modify, "locale", "en");
		checkOrSetConfig(modify, "allow-crafting", false);
		checkOrSetConfig(modify, "keep-inventory-on-death", false);
		checkOrSetConfig(modify, "in-lobby-colored-leather-by-team", true);
		checkOrSetConfig(modify, "jointeam-entity-show-name", true);
		checkOrSetConfig(modify, "friendlyfire", false);
		checkOrSetConfig(modify, "player-drops", true);
		checkOrSetConfig(modify, "join-randomly-after-lobby-timeout", false);
		checkOrSetConfig(modify, "spectator-gm3", false);
		checkOrSetConfig(modify, "prevent-killing-villagers", true);
		checkOrSetConfig(modify, "compass-enabled", true);
		checkOrSetConfig(modify, "join-randomly-on-lobby-join", false);
		checkOrSetConfig(modify, "add-wool-to-inventory-on-join", true);
		checkOrSetConfig(modify, "prevent-spawning-mobs", true);
		checkOrSetConfig(modify, "spawner-holograms", true);
		checkOrSetConfig(modify, "spawner-disable-merge", true);
		checkOrSetConfig(modify, "prevent-lobby-spawn-mobs-in-radius", 16);
		checkOrSetConfig(modify, "spawner-holo-height", 0.25);
		checkOrSetConfig(modify, "spawner-holograms-countdown", true);
		checkOrSetConfig(modify, "damage-when-player-is-not-in-arena", false);
		checkOrSetConfig(modify, "remove-unused-target-blocks", true);
		checkOrSetConfig(modify, "allow-block-falling", true);
		checkOrSetConfig(modify, "game-start-items", false);
		checkOrSetConfig(modify, "player-respawn-items", false);
		checkOrSetConfig(modify, "gived-game-start-items", new ArrayList<>());
		checkOrSetConfig(modify, "gived-player-respawn-items", new ArrayList<>());
		checkOrSetConfig(modify, "allowed-commands", new ArrayList<>());
		checkOrSetConfig(modify, "disable-hunger", false);
		checkOrSetConfig(modify, "automatic-coloring-in-shop", true);
		checkOrSetConfig(modify, "destroy-placed-blocks-by-explosion", true);

		checkOrSetConfig(modify, "bungee.enabled", false);
		checkOrSetConfig(modify, "bungee.serverRestart", true);
		checkOrSetConfig(modify, "bungee.serverStop", false);
		checkOrSetConfig(modify, "bungee.server", "hub");
		checkOrSetConfig(modify, "bungee.auto-game-connect", false);

		checkOrSetConfig(modify, "farmBlocks.enable", true);
		checkOrSetConfig(modify, "farmBlocks.blocks", new ArrayList<>());

		checkOrSetConfig(modify, "scoreboard.enable", true);
		checkOrSetConfig(modify, "scoreboard.title", "§a%game%§r - %time%");
		checkOrSetConfig(modify, "scoreboard.bedLost", "§c\u2718");
		checkOrSetConfig(modify, "scoreboard.bedExists", "§a\u2714");
		checkOrSetConfig(modify, "scoreboard.teamTitle", "%bed%%color%%team%");

		checkOrSetConfig(modify, "title.fadeIn", 0);
		checkOrSetConfig(modify, "title.stay", 20);
		checkOrSetConfig(modify, "title.fadeOut", 0);

		checkOrSetConfig(modify, "items.jointeam", "COMPASS");
		checkOrSetConfig(modify, "items.leavegame", "SLIME_BALL");
		checkOrSetConfig(modify, "items.startgame", "DIAMOND");
		checkOrSetConfig(modify, "items.shopback", "BARRIER");
		checkOrSetConfig(modify, "items.shopcosmetic", "AIR");
		checkOrSetConfig(modify, "items.pageback", "ARROW");
		checkOrSetConfig(modify, "items.pageforward", "ARROW");

		checkOrSetConfig(modify, "vault.enable", true);
		checkOrSetConfig(modify, "vault.reward.kill", 5);
		checkOrSetConfig(modify, "vault.reward.win", 20);
		checkOrSetConfig(modify, "resources", new ArrayList<>());

		checkOrSetConfig(modify, "respawn.protection-enabled", true);
		checkOrSetConfig(modify, "respawn.protection-time",10);

		checkOrSetConfig(modify, "specials.action-bar-messages", true);
		checkOrSetConfig(modify, "specials.rescue-platform.is-breakable", false);
		checkOrSetConfig(modify, "specials.rescue-platform.delay", 0);
		checkOrSetConfig(modify, "specials.rescue-platform.break-time", 10);
		checkOrSetConfig(modify, "specials.rescue-platform.distance", 1);
		checkOrSetConfig(modify, "specials.rescue-platform.material", "GLASS");
		checkOrSetConfig(modify, "specials.protection-wall.is-breakable", false);
		checkOrSetConfig(modify, "specials.protection-wall.delay", 20);
		checkOrSetConfig(modify, "specials.protection-wall.break-time", 0);
		checkOrSetConfig(modify, "specials.protection-wall.width", 5);
		checkOrSetConfig(modify, "specials.protection-wall.height", 3);
		checkOrSetConfig(modify, "specials.protection-wall.distance", 2);
		checkOrSetConfig(modify, "specials.protection-wall.material", "CUT_SANDSTONE");
		checkOrSetConfig(modify, "specials.tntsheep.speed", 4.0);
		checkOrSetConfig(modify, "specials.tntsheep.follow-range", 10.0);
		checkOrSetConfig(modify, "specials.tntsheep.max-target-distance", 32);
		checkOrSetConfig(modify, "specials.tntsheep.explosion-time", 8);
		checkOrSetConfig(modify, "specials.arrow-blocker.protection-time", 10);
		checkOrSetConfig(modify, "specials.arrow-blocker.delay", 5);
		checkOrSetConfig(modify, "specials.warp-powder.teleport-time", 6);
		checkOrSetConfig(modify, "specials.warp-powder.delay", 0);
		checkOrSetConfig(modify, "specials.magnet-shoes.probability", 75);
		checkOrSetConfig(modify, "specials.golem.speed", 0.25);
		checkOrSetConfig(modify, "specials.golem.follow-range", 10);
		checkOrSetConfig(modify, "specials.golem.health", 20);
		checkOrSetConfig(modify, "specials.golem.name-format", "%teamcolor%%team% Golem");
		checkOrSetConfig(modify, "specials.golem.show-name", true);
		checkOrSetConfig(modify, "specials.golem.delay", 0);

		checkOrSetConfig(modify, "tnt.auto-ignite", false);
		checkOrSetConfig(modify, "tnt.explosion-time", 8);

		checkOrSetConfig(modify, "sounds.on_bed_destroyed", "ENTITY_ENDER_DRAGON_GROWL");
		checkOrSetConfig(modify, "sounds.on_countdown", "UI_BUTTON_CLICK");
		checkOrSetConfig(modify, "sounds.on_game_start", "ENTITY_PLAYER_LEVELUP");
		checkOrSetConfig(modify, "sounds.on_team_kill", "ENTITY_PLAYER_LEVELUP");
		checkOrSetConfig(modify, "sounds.on_player_kill", "ENTITY_PLAYER_BIG_FALL");
		checkOrSetConfig(modify, "sounds.on_item_buy", "ENTITY_ITEM_PICKUP");
		checkOrSetConfig(modify, "sounds.on_upgrade_buy", "ENTITY_EXPERIENCE_ORB_PICKUP");

		checkOrSetConfig(modify, "game-effects.end", new HashMap<String, Object>());
		checkOrSetConfig(modify, "game-effects.start", new HashMap<String, Object>());
		checkOrSetConfig(modify, "game-effects.kill", new HashMap<String, Object>());
		checkOrSetConfig(modify, "game-effects.teamkill", new HashMap<String, Object>());
		checkOrSetConfig(modify, "game-effects.lobbyjoin", new HashMap<String, Object>());
		checkOrSetConfig(modify, "game-effects.lobbyleave", new HashMap<String, Object>());
		checkOrSetConfig(modify, "game-effects.respawn", new HashMap<String, Object>());
		checkOrSetConfig(modify, "game-effects.beddestroy", new HashMap<String, Object>());
		checkOrSetConfig(modify, "game-effects.warppowdertick", new HashMap<String, Object>());

		checkOrSetConfig(modify, "lobby-scoreboard.enabled", true);
		checkOrSetConfig(modify, "lobby-scoreboard.title", "§eBEDWARS");
		checkOrSetConfig(modify, "lobby-scoreboard.content", Arrays.asList(" ", "§fMap: §2%arena%",
				"§fPlayers: §2%players%§f/§2%maxplayers%", " ", "§fWaiting ...", " "));

		checkOrSetConfig(modify, "statistics.enabled", true);
		checkOrSetConfig(modify, "statistics.type", "yaml");
		checkOrSetConfig(modify, "statistics.show-on-game-end", false);
		checkOrSetConfig(modify, "statistics.bed-destroyed-kills", false);
		checkOrSetConfig(modify, "statistics.scores.kill", 10);
		checkOrSetConfig(modify, "statistics.scores.die", 0);
		checkOrSetConfig(modify, "statistics.scores.win", 50);
		checkOrSetConfig(modify, "statistics.scores.bed-destroy", 25);
		checkOrSetConfig(modify, "statistics.scores.lose", 0);
		checkOrSetConfig(modify, "statistics.scores.record", 100);

		checkOrSetConfig(modify, "database.host", "localhost");
		checkOrSetConfig(modify, "database.port", 3306);
		checkOrSetConfig(modify, "database.db", "databse");
		checkOrSetConfig(modify, "database.user", "root");
		checkOrSetConfig(modify, "database.password", "secret");
		checkOrSetConfig(modify, "database.table-prefix", "bw_");

		checkOrSetConfig(modify, "bossbar.use-xp-bar", false);
		checkOrSetConfig(modify, "bossbar.lobby.enable", true);
		checkOrSetConfig(modify, "bossbar.lobby.color", "YELLOW");
		checkOrSetConfig(modify, "bossbar.lobby.style", "SEGMENTED_20");
		checkOrSetConfig(modify, "bossbar.game.enable", true);
		checkOrSetConfig(modify, "bossbar.game.color", "GREEN");
		checkOrSetConfig(modify, "bossbar.game.style", "SEGMENTED_20");

		checkOrSetConfig(modify, "holograms.enabled", true);
		checkOrSetConfig(modify, "holograms.headline", "Your §eBEDWARS§f stats");

		checkOrSetConfig(modify, "chat.override", true);
		checkOrSetConfig(modify, "chat.format", "<%teamcolor%%name%§r> ");
		checkOrSetConfig(modify, "chat.separate-game-chat", false);
		checkOrSetConfig(modify, "chat.send-death-messages-just-in-game", true);
		checkOrSetConfig(modify, "chat.send-custom-death-messages", true);
		checkOrSetConfig(modify, "chat.default-team-chat-while-running", true);
		checkOrSetConfig(modify, "chat.all-chat-prefix", "@a");
		checkOrSetConfig(modify, "chat.team-chat-prefix", "@t");
		checkOrSetConfig(modify, "chat.all-chat", "[ALL] ");
		checkOrSetConfig(modify, "chat.team-chat", "[TEAM] ");
		checkOrSetConfig(modify, "chat.death-chat", "[DEATH] ");

		checkOrSetConfig(modify, "rewards.enabled", false);
		checkOrSetConfig(modify, "rewards.player-win", new ArrayList<>());
		checkOrSetConfig(modify, "rewards.player-end-game", new ArrayList<>());
		checkOrSetConfig(modify, "rewards.player-destroy-bed", new ArrayList<>());
		checkOrSetConfig(modify, "rewards.player-kill", new ArrayList<>());

		checkOrSetConfig(modify, "lore.generate-automatically", true);
		checkOrSetConfig(modify, "lore.text",
				Arrays.asList("§7Price:", "§7%price% %resource%", "§7Amount:", "§7%amount%"));
		checkOrSetConfig(modify, "sign", Arrays.asList("§c§l[BedWars]", "%arena%", "%status%", "%players%"));

		checkOrSetConfig(modify, "hotbar.selector", 0);
		checkOrSetConfig(modify, "hotbar.color", 1);
		checkOrSetConfig(modify, "hotbar.start", 2);
		checkOrSetConfig(modify, "hotbar.leave", 8);

		checkOrSetConfig(modify, "breakable.enabled", false);
		checkOrSetConfig(modify, "breakable.blocks", new ArrayList<>());

		checkOrSetConfig(modify, "version", 2);
		if (modify.get()) {
			try {
				config.save(configf);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void checkOrSetConfig(AtomicBoolean modify, String path, Object value) {
		checkOrSet(modify, this.config, path, value);
	}

	private static void checkOrSet(AtomicBoolean modify, FileConfiguration config, String path, Object value) {
		if (!config.isSet(path)) {
			config.set(path, value);
			modify.set(true);
		}
	}

	public ItemStack readDefinedItem(String item, String def) {
		ItemStack material = new ItemStack(Material.valueOf(def));

		if (config.isSet("items." + item)) {
			Object obj = config.get("items." + item);
			if (obj instanceof ItemStack) {
				material = (ItemStack) obj;
			} else {
				material.setType(Material.valueOf((String) obj));
			}
		}

		return material;
	}
}

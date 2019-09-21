package misat11.bw.special;

import misat11.bw.Main;
import misat11.bw.api.Game;
import misat11.bw.api.Team;
import misat11.bw.game.TeamColor;
import misat11.bw.utils.ColorChanger;
import misat11.bw.utils.MiscUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

import static misat11.lib.lang.I18n.i18nonly;

public class RescuePlatform extends SpecialItem implements misat11.bw.api.special.RescuePlatform {
	private Game game;
	private Player player;
	private Team team;
	private List<Block> platformBlocks;

	private Material buildingMaterial;
	private ItemStack item;

	private boolean canBreak;
	private int breakingTime;
	private int livingTime;

	public RescuePlatform(Game game, Player player, Team team, ItemStack item) {
		super(game, player, team);
		this.game = game;
		this.player = player;
		this.team = team;
		this.item = item;
	}

	@Override
	public int getBreakingTime() {
		return breakingTime;
	}

	@Override
	public boolean canBreak() {
		return canBreak;
	}

	@Override
	public Material getMaterial() {
		return buildingMaterial;
	}

	@Override
	public ItemStack getStack() {
		return item;
	}

	@Override
	public void runTask() {
		new BukkitRunnable() {

			@Override
			public void run() {
				livingTime++;
				int time = breakingTime - livingTime;

				if (time < 6 && time > 0) {
					MiscUtils.sendActionBarMessage(
							player, i18nonly("specials_rescue_platform_destroy").replace("%time%", Integer.toString(time)));
				}

				if (livingTime == breakingTime) {
					for (Block block : RescuePlatform.this.platformBlocks) {
						block.getChunk().load(true);
						block.setType(Material.AIR);

						removeBlockFromList(block);
						game.getRegion().removeBlockBuiltDuringGame(block.getLocation());

					}
                    game.unregisterSpecialItem(RescuePlatform.this);
                    this.cancel();
				}
			}
		}.runTaskTimer(Main.getInstance(), 20L, 20L);
	}

	@Override
	public List<Block> getPlatformBlocks() {
		return platformBlocks;
	}

	private void addBlockToList(Block block)  {
		platformBlocks.add(block);
		game.getRegion().addBuiltDuringGame(block.getLocation());
	}

	private void removeBlockFromList(Block block) {
		game.getRegion().removeBlockBuiltDuringGame(block.getLocation());
	}

	public void createPlatform(boolean bre, int time, int dist, Material bMat) {
		canBreak = bre;
		breakingTime = time;
		buildingMaterial = bMat;
		platformBlocks = new ArrayList<>();

		Location center = player.getLocation().clone();
		center.setY(center.getY() - dist);

		for (BlockFace blockFace : BlockFace.values()) {
			if (blockFace.equals(BlockFace.DOWN) || blockFace.equals(BlockFace.UP)) {
				continue;
			}

			Block placedBlock = center.getBlock().getRelative(blockFace);
			if (placedBlock.getType() != Material.AIR) {
				continue;
			}

			ItemStack coloredStack = Main.applyColor(
					TeamColor.fromApiColor(team.getColor()), new ItemStack(buildingMaterial));
			if (Main.isLegacy()) {
				placedBlock.setType(coloredStack.getType());
				try {
					// The method is no longer in API, but in legacy versions exists
					Block.class.getMethod("setData", byte.class).invoke(placedBlock, (byte) coloredStack.getDurability());
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				placedBlock.setType(coloredStack.getType());
			}
			addBlockToList(placedBlock);
		}

		if (breakingTime > 0) {
			game.registerSpecialItem(this);
			runTask();

			MiscUtils.sendActionBarMessage(player, i18nonly("specials_rescue_platform_created").replace("%time%", Integer.toString(breakingTime)));

			item.setAmount(item.getAmount() - 1);
			player.updateInventory();
		} else {
			game.registerSpecialItem(this);

			MiscUtils.sendActionBarMessage(player, i18nonly("specials_rescue_platform_created_unbreakable"));
			item.setAmount(item.getAmount() - 1);
			player.updateInventory();
		}
	}
}

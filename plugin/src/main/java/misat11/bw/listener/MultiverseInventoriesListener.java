package misat11.bw.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.onarandombox.multiverseinventories.event.MVInventoryHandlingEvent;

import misat11.bw.Main;
import misat11.bw.game.GamePlayer;

public class MultiverseInventoriesListener implements Listener {
	@EventHandler
	public void onInventoryChange(MVInventoryHandlingEvent event) {
		Player player = event.getPlayer();
		/*if (Main.isPlayerGameProfileRegistered(player)) {
			GamePlayer gPlayer = Main.getPlayerGameProfile(player);
			if (gPlayer.getGame() != null || gPlayer.isTeleportingFromGame_justForInventoryPlugins) {
				gPlayer.isTeleportingFromGame_justForInventoryPlugins = false;
				event.setCancelled(true);
			}
		}*/
	}
}

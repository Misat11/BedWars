package org.screamingsandals.bedwars.game;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.lib.debug.Debug;
import org.screamingsandals.bedwars.utils.BungeeUtils;
import org.screamingsandals.bedwars.lib.nms.entity.PlayerUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class GamePlayer {
    public final Player player;
    private Game game = null;
    private String latestGame = null;
    private StoredInventory oldInventory = new StoredInventory();
    private List<ItemStack> permaItemsPurchased = new ArrayList<>();
    private List<Player> hiddenPlayers = new ArrayList<>();
    private ItemStack[] armorContents = null;

    public boolean isSpectator = false;
    public boolean isTeleportingFromGame_justForInventoryPlugins = false;
    public boolean mainLobbyUsed = false;

    public GamePlayer(Player player) {
        this.player = player;
    }

    public void changeGame(Game game) {
        if (this.game != null && game == null) {
            this.game.internalLeavePlayer(this);
            this.game = null;
            this.isSpectator = false;
            this.clean();
            if (Game.isBungeeEnabled()) {
                BungeeUtils.movePlayerToBungeeServer(player, Main.isDisabling());
            } else {
                this.restoreInv();
            }
        } else if (this.game == null && game != null) {
            this.storeInv();
            this.clean();
            this.game = game;
            this.isSpectator = false;
            this.mainLobbyUsed = false;
            this.game.internalJoinPlayer(this);
            if (this.game != null) {
                this.latestGame = this.game.getName();
            }
        } else if (this.game != null) {
            this.game.internalLeavePlayer(this);
            this.game = game;
            this.isSpectator = false;
            this.clean();
            this.mainLobbyUsed = false;
            this.game.internalJoinPlayer(this);
            if (this.game != null) {
                this.latestGame = this.game.getName();
            }
        }
    }

    public Game getGame() {
        return game;
    }

    public String getLatestGameName() {
        return this.latestGame;
    }

    public boolean isInGame() {
        return game != null;
    }

    public boolean canJoinFullGame() {
        return player.hasPermission("bw.vip.forcejoin");
    }

    public List<ItemStack> getPermaItemsPurchased() {
        return permaItemsPurchased;
    }

    private void resetPermaItems() {
        this.permaItemsPurchased.clear();
    }

    public void addPermaItem(ItemStack stack) {
        this.permaItemsPurchased.add(stack);
    }

    public void storeInv() {
        oldInventory.inventory = player.getInventory().getContents();
        oldInventory.armor = player.getInventory().getArmorContents();
        oldInventory.xp = player.getExp();
        oldInventory.effects = player.getActivePotionEffects();
        oldInventory.mode = player.getGameMode();
        oldInventory.leftLocation = player.getLocation();
        oldInventory.level = player.getLevel();
        oldInventory.listName = player.getPlayerListName();
        oldInventory.displayName = player.getDisplayName();
        oldInventory.foodLevel = player.getFoodLevel();
    }

    public void restoreInv() {
        isTeleportingFromGame_justForInventoryPlugins = true;
        if (!mainLobbyUsed) {
            teleport(oldInventory.leftLocation);
        }
        mainLobbyUsed = false;

        player.getInventory().setContents(oldInventory.inventory);
        player.getInventory().setArmorContents(oldInventory.armor);

        player.addPotionEffects(oldInventory.effects);
        player.setLevel(oldInventory.level);
        player.setExp(oldInventory.xp);
        player.setFoodLevel(oldInventory.foodLevel);

        for (PotionEffect e : player.getActivePotionEffects())
            player.removePotionEffect(e.getType());

        player.addPotionEffects(oldInventory.effects);

        player.setPlayerListName(oldInventory.listName);
        player.setDisplayName(oldInventory.displayName);

        player.setGameMode(oldInventory.mode);

        if (oldInventory.mode == GameMode.CREATIVE)
            player.setAllowFlight(true);
        else
            player.setAllowFlight(false);

        player.updateInventory();
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    public void resetLife() {
        this.player.setAllowFlight(false);
        this.player.setFlying(false);
        this.player.setExp(0.0F);
        this.player.setLevel(0);
        this.player.setSneaking(false);
        this.player.setSprinting(false);
        this.player.setFoodLevel(20);
        this.player.setSaturation(10);
        this.player.setExhaustion(0);
        this.player.setMaxHealth(20D);
        this.player.setHealth(this.player.getMaxHealth());
        this.player.setFireTicks(0);
        this.player.setFallDistance(0);
        this.player.setGameMode(GameMode.SURVIVAL);

        if (this.player.isInsideVehicle()) {
            this.player.leaveVehicle();
        }

        for (PotionEffect e : this.player.getActivePotionEffects()) {
            this.player.removePotionEffect(e.getType());
        }
    }

    public void invClean() {
        PlayerInventory inv = this.player.getInventory();
        inv.setArmorContents(new ItemStack[4]);
        inv.setContents(new ItemStack[]{});

        this.armorContents = null;
        this.player.updateInventory();
    }

    public void clean() {
        invClean();
        resetLife();
        resetPermaItems();
        new ArrayList<>(this.hiddenPlayers).forEach(this::showPlayer);
    }

    public boolean teleport(Location location) {
    	return PlayerUtils.teleportPlayer(player, location);
    }

    public boolean teleport(Location location, Runnable runnable) {
        return PlayerUtils.teleportPlayer(player, location, runnable);
    }

    public void hidePlayer(Player player) {
        if (!hiddenPlayers.contains(player) && !player.equals(this.player)) {
            hiddenPlayers.add(player);
            try {
                this.player.hidePlayer(Main.getInstance(), player);
            } catch (Throwable t) {
                this.player.hidePlayer(player);
            }
        }
    }

    public void showPlayer(Player player) {
        if (hiddenPlayers.contains(player) && !player.equals(this.player)) {
            hiddenPlayers.remove(player);
            try {
                this.player.showPlayer(Main.getInstance(), player);
            } catch (Throwable t) {
                this.player.showPlayer(player);
            }
        }

    }

    public void setGameArmorContents(ItemStack[] armorContents) {
        this.armorContents = armorContents;
    }

    public ItemStack[] getGameArmorContents() {
        return armorContents;
    }

}

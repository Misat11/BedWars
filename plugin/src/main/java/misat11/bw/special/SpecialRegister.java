package misat11.bw.special;

import misat11.bw.special.listener.*;
import org.bukkit.plugin.Plugin;

public class SpecialRegister {

    public static void onEnable(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new ArrowBlockerListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new GolemListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new LuckyBlockAddonListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MagnetShoesListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new ProtectionWallListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new RescuePlatformListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new TeamChestListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new TNTSheepListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new TrackerListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new TrapListener(), plugin);
        plugin.getServer().getPluginManager().registerEvents(new WarpPowderListener(), plugin);
    }

}

package org.screamingsandals.bedwars.api.special;

import org.bukkit.inventory.ItemStack;

/**
 * @author Bedwars Team
 */
public interface WarpPowder extends SpecialItem {
    /**
     * @param unregisterSpecial
     * @param showMessage
     */
    public void cancelTeleport(boolean unregisterSpecial, boolean showMessage);

    /**
     * @return
     */
    public ItemStack getStack();

    /**
     *
     */
    public void runTask();
}

package misat11.bw.utils;

import misat11.bw.Main;
import misat11.bw.game.TeamColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

public class ColorChanger implements misat11.bw.api.utils.ColorChanger {
    public static ItemStack changeLegacyStackColor(ItemStack itemStack, TeamColor teamColor) {
        Material material = itemStack.getType();
        String materialName = material.name();

        if (Main.autoColoredMaterials.contains(materialName)) {
            itemStack.setDurability((short) teamColor.woolData);
        } else if (material.toString().contains("GLASS")) {
            itemStack.setType(Material.getMaterial("STAINED_GLASS"));
            itemStack.setDurability((short) teamColor.woolData);
        } else if (material.toString().contains("GLASS_PANE")) {
            itemStack.setType(Material.getMaterial("STAINED_GLASS_PANE"));
            itemStack.setDurability((short) teamColor.woolData);
        }
        return itemStack;
    }

    public static Material changeMaterialColor(Material material, TeamColor teamColor) {
        String materialName = material.name();

        try {
            materialName = material.toString().substring(material.toString().indexOf("_") + 1);
        } catch (StringIndexOutOfBoundsException ignored) {
        }

        String teamMaterialColor = teamColor.material1_13;

        if (Main.autoColoredMaterials.contains(materialName)) {
            return Material.getMaterial(teamMaterialColor + "_" + materialName);
        } else if (material.toString().contains("GLASS")) {
            return Material.getMaterial(teamMaterialColor + "_STAINED_GLASS");
        } else if (material.toString().contains("GLASS_PANE")) {
            return Material.getMaterial(teamMaterialColor + "_STAINED_GLASS_PANE");
        }
        return material;

    }

    public static ItemStack changeLeatherArmorColor(ItemStack itemStack, TeamColor color) {
        Material material = itemStack.getType();

        if (material.toString().contains("LEATHER_") && !material.toString().contains("LEATHER_HORSE_")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) itemStack.getItemMeta();

            meta.setColor(color.leatherColor);
            itemStack.setItemMeta(meta);

            return itemStack;
        }
        return itemStack;
    }

	@Override
	public ItemStack applyColor(misat11.bw.api.TeamColor apiColor, ItemStack stack) {
		TeamColor color = TeamColor.fromApiColor(apiColor);
		Material material = stack.getType();
		if (Main.isLegacy()) {
			stack = changeLegacyStackColor(stack, color);
		} else {
			stack.setType(changeMaterialColor(material, color));
		}
		stack = changeLeatherArmorColor(stack, color);
		return stack;
	}
}

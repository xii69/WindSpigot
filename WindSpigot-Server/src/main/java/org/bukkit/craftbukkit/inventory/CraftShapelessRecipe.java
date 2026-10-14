package org.bukkit.craftbukkit.inventory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

import net.minecraft.server.CraftingManager;
import net.minecraft.server.InventoryCrafting;
import net.minecraft.server.ShapelessRecipes;

public class CraftShapelessRecipe extends ShapelessRecipe implements CraftRecipe {
	private ShapelessRecipes recipe;

	public CraftShapelessRecipe(ItemStack result) {
		super(result);
	}

	public CraftShapelessRecipe(ItemStack result, ShapelessRecipes recipe) {
		this(result);
		this.recipe = recipe;
	}

	public static CraftShapelessRecipe fromBukkitRecipe(ShapelessRecipe recipe) {
		if (recipe instanceof CraftShapelessRecipe) {
			return (CraftShapelessRecipe) recipe;
		}
		CraftShapelessRecipe ret = new CraftShapelessRecipe(recipe.getResult());
		for (ItemStack ingred : recipe.getIngredientList()) {
			ret.addIngredient(ingred.getType(), ingred.getDurability());
		}
		return ret;
	}

	// WindSpigot - matches() against crafting inventory / matrix
	public boolean matches(CraftingInventory inventory) {
		if (inventory == null) {
			return false;
		}
		if (this.recipe != null && inventory instanceof CraftInventoryCrafting) {
			InventoryCrafting matrix = (InventoryCrafting) ((CraftInventoryCrafting) inventory).getMatrixInventory();
			return this.recipe.a(matrix, null);
		}
		return matches(inventory.getMatrix());
	}

	public boolean matches(ItemStack[] matrix) {
		if (matrix == null) {
			return false;
		}
		List<ItemStack> remaining = new ArrayList<ItemStack>(getIngredientList());
		for (ItemStack slot : matrix) {
			if (slot == null || slot.getTypeId() == 0) {
				continue;
			}
			boolean found = false;
			Iterator<ItemStack> it = remaining.iterator();
			while (it.hasNext()) {
				ItemStack ingredient = it.next();
				if (ingredient.getTypeId() == slot.getTypeId() && (ingredient.getDurability() == Short.MAX_VALUE
						|| ingredient.getDurability() == slot.getDurability())) {
					it.remove();
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return remaining.isEmpty();
	}

	@Override
	public void addToCraftingManager() {
		List<ItemStack> ingred = this.getIngredientList();
		List<net.minecraft.server.ItemStack> nmsIngredients = new ArrayList<net.minecraft.server.ItemStack>(
				ingred.size());
		for (ItemStack mdata : ingred) {
			int id = mdata.getTypeId();
			short dmg = mdata.getDurability();
			nmsIngredients.add(new net.minecraft.server.ItemStack(CraftMagicNumbers.getItem(id), 1, dmg));
		}
		this.recipe = new ShapelessRecipes(CraftItemStack.asNMSCopy(this.getResult()), nmsIngredients);
		CraftingManager.getInstance().a(this.recipe);
	}
}

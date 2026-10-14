package org.bukkit.craftbukkit.inventory;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import net.minecraft.server.CraftingManager;
import net.minecraft.server.InventoryCrafting;
import net.minecraft.server.ShapedRecipes;

public class CraftShapedRecipe extends ShapedRecipe implements CraftRecipe {
	// WindSpigot - backing NMS recipe when wrapped via ShapedRecipes#toBukkitRecipe()
	private ShapedRecipes recipe;

	public CraftShapedRecipe(ItemStack result) {
		super(result);
	}

	public CraftShapedRecipe(ItemStack result, ShapedRecipes recipe) {
		this(result);
		this.recipe = recipe;
	}

	public static CraftShapedRecipe fromBukkitRecipe(ShapedRecipe recipe) {
		if (recipe instanceof CraftShapedRecipe) {
			return (CraftShapedRecipe) recipe;
		}
		CraftShapedRecipe ret = new CraftShapedRecipe(recipe.getResult());
		String[] shape = recipe.getShape();
		ret.shape(shape);
		Map<Character, ItemStack> ingredientMap = recipe.getIngredientMap();
		for (char c : ingredientMap.keySet()) {
			ItemStack stack = ingredientMap.get(c);
			if (stack != null) {
				ret.setIngredient(c, stack.getType(), stack.getDurability());
			}
		}
		return ret;
	}

	// WindSpigot - TODO #16: matches() against crafting inventory / 3×3 matrix
	public boolean matches(InventoryCrafting inventory) {
		if (inventory == null) {
			return false;
		}
		if (this.recipe != null) {
			return this.recipe.a(inventory, null);
		}
		return matches(toBukkitMatrix(inventory));
	}

	public boolean matches(ItemStack[] matrix) {
		return matches(getShape(), getIngredientMap(), matrix);
	}

	private static final int GRID = 3;
	private static final short WILDCARD_DATA = Short.MAX_VALUE;

	private static boolean matches(String[] shape, Map<Character, ItemStack> ingredients, ItemStack[] matrix) {
		if (shape == null || shape.length == 0 || matrix == null || matrix.length < GRID * GRID) {
			return false;
		}
		int height = shape.length;
		int width = shape[0].length();
		if (width == 0 || height > GRID || width > GRID) {
			return false;
		}
		for (int row = 1; row < height; row++) {
			if (shape[row] == null || shape[row].length() != width) {
				return false;
			}
		}

		for (int offX = 0; offX <= GRID - width; offX++) {
			for (int offY = 0; offY <= GRID - height; offY++) {
				if (matchesAt(shape, ingredients, matrix, width, height, offX, offY, true)
						|| matchesAt(shape, ingredients, matrix, width, height, offX, offY, false)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean matchesAt(String[] shape, Map<Character, ItemStack> ingredients, ItemStack[] matrix,
			int width, int height, int offX, int offY, boolean mirror) {
		for (int gridX = 0; gridX < GRID; gridX++) {
			for (int gridY = 0; gridY < GRID; gridY++) {
				int recipeX = gridX - offX;
				int recipeY = gridY - offY;
				ItemStack expected = null;

				if (recipeX >= 0 && recipeY >= 0 && recipeX < width && recipeY < height) {
					int shapeX = mirror ? (width - recipeX - 1) : recipeX;
					char key = shape[recipeY].charAt(shapeX);
					if (key != ' ' && ingredients != null) {
						expected = ingredients.get(key);
					}
				}

				ItemStack actual = matrix[gridY * GRID + gridX];
				if (!slotMatches(expected, actual)) {
					return false;
				}
			}
		}
		return true;
	}

	private static boolean slotMatches(ItemStack expected, ItemStack actual) {
		boolean expectEmpty = isEmpty(expected);
		boolean actualEmpty = isEmpty(actual);
		if (expectEmpty && actualEmpty) {
			return true;
		}
		if (expectEmpty || actualEmpty) {
			return false;
		}
		if (expected.getTypeId() != actual.getTypeId()) {
			return false;
		}
		short data = expected.getDurability();
		return data == WILDCARD_DATA || data == actual.getDurability();
	}

	private static boolean isEmpty(ItemStack stack) {
		return stack == null || stack.getTypeId() <= 0 || stack.getType() == Material.AIR;
	}

	private static ItemStack[] toBukkitMatrix(InventoryCrafting inventory) {
		ItemStack[] matrix = new ItemStack[9];
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				net.minecraft.server.ItemStack nms = inventory.c(x, y);
				matrix[y * 3 + x] = nms == null ? null : CraftItemStack.asCraftMirror(nms);
			}
		}
		return matrix;
	}

	@Override
	public void addToCraftingManager() {
		Object[] data;
		String[] shape = this.getShape();
		Map<Character, ItemStack> ingred = this.getIngredientMap();
		int datalen = shape.length;
		datalen += ingred.size() * 2;
		int i = 0;
		data = new Object[datalen];
		for (; i < shape.length; i++) {
			data[i] = shape[i];
		}
		for (char c : ingred.keySet()) {
			ItemStack mdata = ingred.get(c);
			if (mdata == null) {
				continue;
			}
			data[i] = c;
			i++;
			int id = mdata.getTypeId();
			short dmg = mdata.getDurability();
			data[i] = new net.minecraft.server.ItemStack(CraftMagicNumbers.getItem(id), 1, dmg);
			i++;
		}
		CraftingManager.getInstance().registerShapedRecipe(CraftItemStack.asNMSCopy(this.getResult()), data);
	}
}

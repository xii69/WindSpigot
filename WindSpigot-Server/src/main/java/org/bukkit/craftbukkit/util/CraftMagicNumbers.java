package org.bukkit.craftbukkit.util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Achievement;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.UnsafeValues;
import org.bukkit.craftbukkit.CraftStatistic;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import com.google.common.collect.Lists;

import net.minecraft.server.Block;
import net.minecraft.server.Blocks;
import net.minecraft.server.Item;
import net.minecraft.server.MinecraftKey;
import net.minecraft.server.MojangsonParseException;
import net.minecraft.server.MojangsonParser;
import net.minecraft.server.StatisticList;

@SuppressWarnings("deprecation")
public final class CraftMagicNumbers implements UnsafeValues {
	public static final UnsafeValues INSTANCE = new CraftMagicNumbers();

	// WindSpigot start - Material↔NMS maps (bootstrap from IDs once)
	private static final class MaterialMaps {
		static final Map<Material, Item> MATERIAL_ITEM = new EnumMap<Material, Item>(Material.class);
		static final Map<Material, Block> MATERIAL_BLOCK = new EnumMap<Material, Block>(Material.class);
		static final Map<Item, Material> ITEM_MATERIAL = new IdentityHashMap<Item, Material>();

		static {
			for (Material material : Material.values()) {
				Item item = Item.getById(material.getId());
				if (item != null) {
					MATERIAL_ITEM.put(material, item);
					ITEM_MATERIAL.put(item, material);
				}

				Block block = Block.getById(material.getId());
				if (block != null) {
					MATERIAL_BLOCK.put(material, block);
				}
			}
		}

		private MaterialMaps() {
		}
	}
	// WindSpigot end

	private CraftMagicNumbers() {
	}

	public static Block getBlock(org.bukkit.block.Block block) {
		return getBlock(block.getType());
	}

	@Deprecated
	// A bad method for bad magic.
	public static Block getBlock(int id) {
		return getBlock(Material.getMaterial(id));
	}

	@Deprecated
	// A bad method for bad magic.
	public static int getId(Block block) {
		return Block.getId(block);
	}

	public static Material getMaterial(Block block) {
		return Material.getMaterial(Block.getId(block));
	}

	public static Item getItem(Material material) {
		return MaterialMaps.MATERIAL_ITEM.get(material);
	}

	@Deprecated
	// A bad method for bad magic.
	public static Item getItem(int id) {
		return Item.getById(id);
	}

	@Deprecated
	// A bad method for bad magic.
	public static int getId(Item item) {
		return Item.getId(item);
	}

	public static Material getMaterial(Item item) {
		Material material = MaterialMaps.ITEM_MATERIAL.get(item);

		if (material == null) {
			return Material.AIR;
		}

		return material;
	}

	public static Block getBlock(Material material) {
		Block block = MaterialMaps.MATERIAL_BLOCK.get(material);

		if (block == null) {
			return Blocks.AIR;
		}

		return block;
	}

	@Override
	public Material getMaterialFromInternalName(String name) {
		return getMaterial(Item.REGISTRY.get(new MinecraftKey(name)));
	}

	@Override
	public List<String> tabCompleteInternalMaterialName(String token, List<String> completions) {
		ArrayList<String> results = Lists.newArrayList();
		for (MinecraftKey key : Item.REGISTRY.keySet()) {
			results.add(key.toString());
		}
		return StringUtil.copyPartialMatches(token, results, completions);
	}

	@Override
	public ItemStack modifyItemStack(ItemStack stack, String arguments) {
		net.minecraft.server.ItemStack nmsStack = CraftItemStack.asNMSCopy(stack);

		try {
			nmsStack.setTag(MojangsonParser.parse(arguments));
		} catch (MojangsonParseException ex) {
			Logger.getLogger(CraftMagicNumbers.class.getName()).log(Level.SEVERE, null, ex);
		}

		stack.setItemMeta(CraftItemStack.getItemMeta(nmsStack));

		return stack;
	}

	@Override
	public Statistic getStatisticFromInternalName(String name) {
		return CraftStatistic.getBukkitStatisticByName(name);
	}

	@Override
	public Achievement getAchievementFromInternalName(String name) {
		return CraftStatistic.getBukkitAchievementByName(name);
	}

	@Override
	public List<String> tabCompleteInternalStatisticOrAchievementName(String token, List<String> completions) {
		List<String> matches = new ArrayList<String>();
		Iterator iterator = StatisticList.stats.iterator();
		while (iterator.hasNext()) {
			String statistic = ((net.minecraft.server.Statistic) iterator.next()).name;
			if (statistic.startsWith(token)) {
				matches.add(statistic);
			}
		}
		return matches;
	}
}

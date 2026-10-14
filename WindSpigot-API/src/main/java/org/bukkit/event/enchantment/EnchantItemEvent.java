package org.bukkit.event.enchantment;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Called when an ItemStack is successfully enchanted (currently at enchantment
 * table)
 */
public class EnchantItemEvent extends InventoryEvent implements Cancellable {
	private static final HandlerList handlers = new HandlerList();
	private final Block table;
	private final ItemStack item;
	private int level;
	private boolean cancelled;
	private final Map<Enchantment, Integer> enchants;
	private final Player enchanter;
	private int button;
	private int itemCost;

	public EnchantItemEvent(final Player enchanter, final InventoryView view, final Block table, final ItemStack item,
			final int level, final Map<Enchantment, Integer> enchants, final int i) {
		super(view);
		this.enchanter = enchanter;
		this.table = table;
		this.item = item;
		this.level = level;
		this.enchants = new HashMap<Enchantment, Integer>(enchants);
		this.cancelled = false;
		this.button = i;
		// Vanilla: lapis consumed equals button index + 1 (1, 2, or 3)
		this.itemCost = i + 1;
	}

	/**
	 * Gets the player enchanting the item
	 *
	 * @return enchanting player
	 */
	public Player getEnchanter() {
		return enchanter;
	}

	/**
	 * Gets the block being used to enchant the item
	 *
	 * @return the block used for enchanting
	 */
	public Block getEnchantBlock() {
		return table;
	}

	/**
	 * Gets the item to be enchanted (can be modified)
	 *
	 * @return ItemStack of item
	 */
	public ItemStack getItem() {
		return item;
	}

	/**
	 * Get cost in exp levels of the enchantment
	 *
	 * @return experience level cost
	 */
	public int getExpLevelCost() {
		return level;
	}

	/**
	 * Set cost in exp levels of the enchantment
	 *
	 * @param level - cost in levels
	 */
	public void setExpLevelCost(int level) {
		this.level = level;
	}

	/**
	 * Get map of enchantment (levels, keyed by type) to be added to item (modify
	 * map returned to change values). Note: Any enchantments not allowed for the
	 * item will be ignored
	 *
	 * @return map of enchantment levels, keyed by enchantment
	 */
	public Map<Enchantment, Integer> getEnchantsToAdd() {
		return enchants;
	}

	/**
	 * Which button was pressed to initiate the enchanting.
	 *
	 * @return The button index (0, 1, or 2).
	 */
	public int whichButton() {
		return button;
	}

	/**
	 * Gets the number of cost items (lapis lazuli) that will be consumed.
	 * Defaults to {@link #whichButton()}{@code + 1} (vanilla 1, 2, or 3).
	 *
	 * @return cost item count to consume from the enchanting table's lapis slot
	 */
	public int getItemCost() {
		return itemCost;
	}

	/**
	 * Sets the number of cost items (lapis lazuli) that will be consumed.
	 * Use {@code 0} for a free enchant (no lapis). Does not change experience
	 * level cost; see {@link #setExpLevelCost(int)}.
	 *
	 * @param itemCost cost item count; must be {@code >= 0}
	 * @throws IllegalArgumentException if {@code itemCost} is negative
	 */
	public void setItemCost(int itemCost) {
		if (itemCost < 0) {
			throw new IllegalArgumentException("Item cost cannot be negative");
		}
		this.itemCost = itemCost;
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}

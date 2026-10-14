package org.bukkit.inventory.meta;

/**
 * Represents an item that can be repaired at an anvil.
 */
public interface Repairable {

	/**
	 * Checks to see if this has a repair penalty
	 *
	 * @return true if this has a repair penalty
	 */
	boolean hasRepairCost();

	/**
	 * Gets the repair penalty
	 *
	 * @return the repair penalty
	 */
	int getRepairCost();

	/**
	 * Sets the repair penalty.
	 * <p>
	 * There is no Bukkit upper bound: the value is stored as a vanilla NBT
	 * {@code int}. The anvil “too expensive” cap (40 levels in 1.8) applies to
	 * the XP cost of the current anvil operation, not this field. Non-positive
	 * values clear {@link #hasRepairCost()}.
	 *
	 * @param cost repair penalty
	 */
	void setRepairCost(int cost);

	@SuppressWarnings("javadoc")
	Repairable clone();
}

package org.bukkit.craftbukkit.inventory.util;

import net.minecraft.server.ITileInventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TileEntityBeacon;
import net.minecraft.server.TileEntityBrewingStand;
import net.minecraft.server.TileEntityDispenser;
import net.minecraft.server.TileEntityDropper;
import net.minecraft.server.TileEntityFurnace;
import net.minecraft.server.TileEntityHopper;

import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftInventoryBeacon;
import org.bukkit.craftbukkit.inventory.CraftInventoryBrewer;
import org.bukkit.craftbukkit.inventory.CraftInventoryFurnace;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class CraftTileInventoryConverter implements CraftInventoryCreator.InventoryConverter {

	public abstract ITileInventory getTileEntity();

	@Override
	public Inventory createInventory(InventoryHolder holder, InventoryType type) {
		return getInventory(getTileEntity());
	}

	@Override
	public Inventory createInventory(InventoryHolder holder, InventoryType type, String title) {
		ITileInventory tileEntity = getTileEntity();
		setCustomName(tileEntity, title);
		return getInventory(tileEntity);
	}

	public Inventory getInventory(ITileInventory tileEntity) {
		return new CraftInventory(tileEntity);
	}

	protected static void setCustomName(ITileInventory tileEntity, String title) {
		if (title == null) {
			return;
		}
		if (tileEntity instanceof TileEntityFurnace) {
			((TileEntityFurnace) tileEntity).a(title);
		} else if (tileEntity instanceof TileEntityBrewingStand) {
			((TileEntityBrewingStand) tileEntity).a(title);
		} else if (tileEntity instanceof TileEntityBeacon) {
			((TileEntityBeacon) tileEntity).a(title);
		} else if (tileEntity instanceof TileEntityDispenser) {
			// Dropper extends Dispenser
			((TileEntityDispenser) tileEntity).a(title);
		} else if (tileEntity instanceof TileEntityHopper) {
			((TileEntityHopper) tileEntity).a(title);
		}
	}

	public static class Furnace extends CraftTileInventoryConverter {

		@Override
		public ITileInventory getTileEntity() {
			TileEntityFurnace furnace = new TileEntityFurnace();
			// WindSpigot - furnace needs a world
			MinecraftServer server = MinecraftServer.getServer();
			if (server != null) {
				furnace.a(server.getWorldServer(0));
			}
			return furnace;
		}

		@Override
		public Inventory getInventory(ITileInventory tileEntity) {
			return new CraftInventoryFurnace((TileEntityFurnace) tileEntity);
		}
	}

	public static class BrewingStand extends CraftTileInventoryConverter {

		@Override
		public ITileInventory getTileEntity() {
			return new TileEntityBrewingStand();
		}

		@Override
		public Inventory getInventory(ITileInventory tileEntity) {
			return new CraftInventoryBrewer(tileEntity);
		}
	}

	public static class Beacon extends CraftTileInventoryConverter {

		@Override
		public ITileInventory getTileEntity() {
			return new TileEntityBeacon();
		}

		@Override
		public Inventory getInventory(ITileInventory tileInventory) {
			return new CraftInventoryBeacon((TileEntityBeacon) tileInventory);
		}
	}

	public static class Dispenser extends CraftTileInventoryConverter {

		@Override
		public ITileInventory getTileEntity() {
			return new TileEntityDispenser();
		}
	}

	public static class Dropper extends CraftTileInventoryConverter {

		@Override
		public ITileInventory getTileEntity() {
			return new TileEntityDropper();
		}
	}

	public static class Hopper extends CraftTileInventoryConverter {

		@Override
		public ITileInventory getTileEntity() {
			return new TileEntityHopper();
		}
	}
}

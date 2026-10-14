package org.bukkit.craftbukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

public class CraftWorldBorder implements WorldBorder {

	// WindSpigot - seconds*1000 must fit in long
	private static final long MAX_SIZE_TRANSITION_SECONDS = Long.MAX_VALUE / 1000L;

	private final World world;
	private final net.minecraft.server.WorldBorder handle;

	public CraftWorldBorder(CraftWorld world) {
		this.world = world;
		this.handle = world.getHandle().getWorldBorder();
	}

	@Override
	public void reset() {
		this.setSize(net.minecraft.server.WorldBorder.MAX_SIZE);
		this.setDamageAmount(0.2D);
		this.setDamageBuffer(5.0D);
		this.setWarningDistance(5);
		this.setWarningTime(15);
		this.setCenter(0, 0);
	}

	@Override
	public double getSize() {
		return this.handle.getSize();
	}

	@Override
	public void setSize(double newSize) {
		this.setSize(newSize, 0L);
	}

	@Override
	public void setSize(double newSize, long time) {
		newSize = Math.min(this.getMaxSize(), Math.max(net.minecraft.server.WorldBorder.MIN_SIZE, newSize));
		time = Math.min(MAX_SIZE_TRANSITION_SECONDS, Math.max(0L, time));

		if (time > 0L) {
			this.handle.transitionSizeBetween(this.handle.getSize(), newSize, time * 1000L);
		} else {
			this.handle.setSize(newSize);
		}
	}

	@Override
	public Location getCenter() {
		double x = this.handle.getCenterX();
		double z = this.handle.getCenterZ();

		return new Location(this.world, x, 0, z);
	}

	@Override
	public void setCenter(double x, double z) {
		double max = this.getMaxCenterCoordinate();
		x = Math.min(max, Math.max(-max, x));
		z = Math.min(max, Math.max(-max, z));

		this.handle.setCenter(x, z);
	}

	@Override
	public void setCenter(Location location) {
		this.setCenter(location.getX(), location.getZ());
	}

	@Override
	public double getDamageBuffer() {
		return this.handle.getDamageBuffer();
	}

	@Override
	public void setDamageBuffer(double blocks) {
		this.handle.setDamageBuffer(blocks);
	}

	@Override
	public double getDamageAmount() {
		return this.handle.getDamageAmount();
	}

	@Override
	public void setDamageAmount(double damage) {
		this.handle.setDamageAmount(damage);
	}

	@Override
	public int getWarningTime() {
		return this.handle.getWarningTime();
	}

	@Override
	public void setWarningTime(int time) {
		this.handle.setWarningTime(time);
	}

	@Override
	public int getWarningDistance() {
		return this.handle.getWarningDistance();
	}

	@Override
	public void setWarningDistance(int distance) {
		this.handle.setWarningDistance(distance);
	}

	@Override
	public double getMaxSize() {
		return net.minecraft.server.WorldBorder.MAX_SIZE;
	}

	@Override
	public double getMaxCenterCoordinate() {
		return net.minecraft.server.WorldBorder.MAX_CENTER_COORDINATE;
	}
}

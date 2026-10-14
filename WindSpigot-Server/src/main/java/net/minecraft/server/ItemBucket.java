package net.minecraft.server;

import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
// CraftBukkit end

import com.windpvp.windspigot.cache.Constants;

public class ItemBucket extends Item {

	private Block a;

	public ItemBucket(Block block) {
		this.maxStackSize = 1;
		this.a = block;
		this.a(CreativeModeTab.f);
	}

	@Override
	public ItemStack a(ItemStack itemstack, World world, EntityHuman entityhuman) {
		boolean flag = this.a == Blocks.AIR;
		MovingObjectPosition movingobjectposition = this.a(world, entityhuman, flag);

		if (movingobjectposition == null) {
			return itemstack;
		} else {
			if (movingobjectposition.type == MovingObjectPosition.EnumMovingObjectType.BLOCK) {
				BlockPosition blockposition = movingobjectposition.a();

				if (!world.a(entityhuman, blockposition)) {
					return itemstack;
				}

				if (flag) {
					if (!entityhuman.a(blockposition.shift(movingobjectposition.direction),
							movingobjectposition.direction, itemstack)) {
						return itemstack;
					}

					IBlockData iblockdata = world.getType(blockposition);
					Material material = iblockdata.getBlock().getMaterial();

					if (material == Material.WATER && iblockdata.get(BlockFluids.LEVEL).intValue() == 0) {
						// CraftBukkit start
						PlayerBucketFillEvent event = CraftEventFactory.callPlayerBucketFillEvent(entityhuman,
								blockposition.getX(), blockposition.getY(), blockposition.getZ(), null, itemstack,
								Items.WATER_BUCKET);

						if (event.isCancelled()) {
							resyncBucketCancel(entityhuman);
							return itemstack;
						}
						// CraftBukkit end
						world.setAir(blockposition);
						entityhuman.b(StatisticList.USE_ITEM_COUNT[Item.getId(this)]);
						return applyBucketEventResult(itemstack, entityhuman, event.getItemStack());
					}

					if (material == Material.LAVA && iblockdata.get(BlockFluids.LEVEL).intValue() == 0) {
						// CraftBukkit start
						PlayerBucketFillEvent event = CraftEventFactory.callPlayerBucketFillEvent(entityhuman,
								blockposition.getX(), blockposition.getY(), blockposition.getZ(), null, itemstack,
								Items.LAVA_BUCKET);

						if (event.isCancelled()) {
							resyncBucketCancel(entityhuman);
							return itemstack;
						}
						// CraftBukkit end
						world.setAir(blockposition);
						entityhuman.b(StatisticList.USE_ITEM_COUNT[Item.getId(this)]);
						return applyBucketEventResult(itemstack, entityhuman, event.getItemStack());
					}
				} else {
					if (this.a == Blocks.AIR) {
						// CraftBukkit start
						PlayerBucketEmptyEvent event = CraftEventFactory.callPlayerBucketEmptyEvent(entityhuman,
								blockposition.getX(), blockposition.getY(), blockposition.getZ(),
								movingobjectposition.direction, itemstack);

						if (event.isCancelled()) {
							resyncBucketCancel(entityhuman);
							return itemstack;
						}

						return applyBucketEventResult(itemstack, entityhuman, event.getItemStack());
						// CraftBukkit end
					}

					BlockPosition blockposition1 = blockposition.shift(movingobjectposition.direction);

					if (!entityhuman.a(blockposition1, movingobjectposition.direction, itemstack)) {
						return itemstack;
					}

					// CraftBukkit start
					PlayerBucketEmptyEvent event = CraftEventFactory.callPlayerBucketEmptyEvent(entityhuman,
							blockposition.getX(), blockposition.getY(), blockposition.getZ(),
							movingobjectposition.direction, itemstack);

					if (event.isCancelled()) {
						resyncBucketCancel(entityhuman);
						return itemstack;
					}
					// CraftBukkit end

					if (this.a(world, blockposition1) && !entityhuman.abilities.canInstantlyBuild) {
						entityhuman.b(StatisticList.USE_ITEM_COUNT[Item.getId(this)]);
						// CraftBukkit / PaperSpigot: honor event result for empty + stackable buckets
						return applyBucketEventResult(itemstack, entityhuman, event.getItemStack());
					}
				}
			}

			return itemstack;
		}
	}

	// CraftBukkit: honor PlayerBucketEvent#getItemStack() (EntityCow milking pattern).
	// Creative keeps the used bucket; survival consumes one and replaces hand or
	// pickups/drops the result. Null event result → empty hand / no overflow item.
	private static ItemStack applyBucketEventResult(ItemStack itemstack, EntityHuman entityhuman,
			org.bukkit.inventory.ItemStack result) {
		if (entityhuman.abilities.canInstantlyBuild) {
			return itemstack;
		}

		ItemStack nmsResult = CraftItemStack.asNMSCopy(result);
		if (--itemstack.count <= 0) {
			return nmsResult;
		}
		if (nmsResult != null && !entityhuman.inventory.pickup(nmsResult)) {
			entityhuman.drop(nmsResult, false);
		}
		return itemstack;
	}

	/** SPIGOT-4541: client predicts bucket use; cancelled events need an inventory resync. */
	private static void resyncBucketCancel(EntityHuman entityhuman) {
		if (entityhuman instanceof EntityPlayer) {
			((EntityPlayer) entityhuman).getBukkitEntity().updateInventory();
		}
	}

	public boolean a(World world, BlockPosition blockposition) {
		if (this.a == Blocks.AIR) {
			return false;
		} else {
			Material material = world.getType(blockposition).getBlock().getMaterial();
			boolean flag = !material.isBuildable();

			if (!world.isEmpty(blockposition) && !flag) {
				return false;
			} else {
				if (world.worldProvider.n() && this.a == Blocks.FLOWING_WATER) {
					int i = blockposition.getX();
					int j = blockposition.getY();
					int k = blockposition.getZ();

					world.makeSound(i + 0.5F, j + 0.5F, k + 0.5F, "random.fizz", 0.5F,
							2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);

					for (int l = 0; l < 8; ++l) {
						world.addParticle(EnumParticle.SMOKE_LARGE, i + Math.random(), j + Math.random(),
								k + Math.random(), 0.0D, 0.0D, 0.0D, Constants.EMPTY_ARRAY);
					}
				} else {
					if (!world.isClientSide && flag && !material.isLiquid()) {
						world.setAir(blockposition, true);
					}

					world.setTypeAndData(blockposition, this.a.getBlockData(), 3);
				}

				return true;
			}
		}
	}
}

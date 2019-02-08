package cazador.furnaceoverhaul.tile;

import java.util.Map.Entry;

import cazador.furnaceoverhaul.blocks.BlockIronFurnace;
import cazador.furnaceoverhaul.upgrade.Upgrade;
import cazador.furnaceoverhaul.upgrade.Upgrades;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fml.common.registry.GameRegistry.ItemStackHolder;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.oredict.OreDictionary;

public class TileEntityIronFurnace extends TileEntity implements ITickable {

	public static final int SLOT_INPUT = 0;
	public static final int SLOT_FUEL = 1;
	public static final int SLOT_OUTPUT = 2;
	public static final int[] SLOT_UPGRADE = { 3, 4, 5 };
	public static final int MAX_FE_TRANSFER = 1200;
	public static final int ENERGY_PER_TICK = 600;

	protected final ItemStackHandler inv = new ItemStackHandler(6);
	private final RangedWrapper TOP = new RangedWrapper(inv, SLOT_INPUT, SLOT_INPUT + 1);
	private final RangedWrapper SIDES = new RangedWrapper(inv, SLOT_FUEL, SLOT_FUEL + 1);
	private final RangedWrapper BOTTOM = new RangedWrapper(inv, SLOT_OUTPUT, SLOT_OUTPUT + 1);

	protected EnergyStorage energy = new EnergyStorage(50000, MAX_FE_TRANSFER, ENERGY_PER_TICK);
	protected ItemStack recipeKey = ItemStack.EMPTY;
	protected ItemStack recipeOutput = ItemStack.EMPTY;
	protected ItemStack failedMatch = ItemStack.EMPTY;
	protected int cookTime = 200;
	protected int burnTime = 0;
	protected int currentCookTime = 0;

	@ItemStackHolder(value = "minecraft:sponge", meta = 1)
	public static final ItemStack WET_SPONGE = ItemStack.EMPTY;

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		inv.deserializeNBT(tag.getCompoundTag("inv"));
		energy = new EnergyStorage(50000, MAX_FE_TRANSFER, ENERGY_PER_TICK, tag.getInteger("energy"));
		burnTime = tag.getInteger("burn_time");
		cookTime = tag.getInteger("cook_time");
		currentCookTime = tag.getInteger("current_cook_time");
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		compound = super.writeToNBT(compound);
		compound.setTag("inv", inv.serializeNBT());
		compound.setInteger("energy", energy.getEnergyStored());
		compound.setInteger("burn_time", burnTime);
		compound.setInteger("cook_time", cookTime);
		compound.setInteger("current_cook_time", currentCookTime);
		return compound;
	}

	@Override
	public void update() {
		if (world.isRemote && isBurning()) {
			burnTime--;
			return;
		} else if (world.isRemote) return;

		ItemStack fuel = ItemStack.EMPTY;
		boolean canSmelt = canSmelt();

		if (!this.isBurning() && !(fuel = inv.getStackInSlot(SLOT_FUEL)).isEmpty()) {
			if (canSmelt) burnFuel(fuel, false);
		}

		boolean wasBurning = isBurning();

		if (this.isBurning()) {
			burnTime--;
			if (canSmelt) smelt();
			else currentCookTime = 0;
		}

		if (!this.isBurning() && !(fuel = inv.getStackInSlot(SLOT_FUEL)).isEmpty()) {
			if (canSmelt()) burnFuel(fuel, wasBurning);
		}

		if (wasBurning && !isBurning()) world.setBlockState(pos, getDimState());
	}

	protected boolean isBurning() {
		return burnTime > 0;
	}

	protected void smelt() {
		currentCookTime++;
		if (this.currentCookTime == this.cookTime) {
			this.currentCookTime = 0;
			this.smeltItem();
		}
	}

	protected void burnFuel(ItemStack fuel, boolean burnedThisTick) {
		if (isElectric()) {
			burnTime = energy.getEnergyStored() >= ENERGY_PER_TICK ? 1 : 0;
			if (this.isBurning()) energy.extractEnergy(ENERGY_PER_TICK, false);
		} else {
			burnTime = getItemBurnTime(fuel);
			if (this.isBurning()) {
				Item item = fuel.getItem();
				fuel.shrink(1);
				if (fuel.isEmpty()) inv.setStackInSlot(SLOT_FUEL, item.getContainerItem(fuel));
				if (!burnedThisTick) world.setBlockState(pos, getLitState());
			}
		}
	}

	protected boolean canSmelt() {
		ItemStack input = inv.getStackInSlot(SLOT_INPUT);
		ItemStack output = inv.getStackInSlot(SLOT_OUTPUT);
		if (input.isEmpty() || input == failedMatch) return false;

		if (recipeKey.isEmpty() || !OreDictionary.itemMatches(recipeKey, input, false)) {
			boolean matched = false;
			for (Entry<ItemStack, ItemStack> e : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
				if (OreDictionary.itemMatches(e.getKey(), input, false)) {
					recipeKey = e.getKey();
					recipeOutput = e.getValue();
					matched = true;
					failedMatch = ItemStack.EMPTY;
					break;
				}
			}
			if (!matched) {
				recipeKey = ItemStack.EMPTY;
				recipeOutput = ItemStack.EMPTY;
				failedMatch = input;
				return false;
			}
		}

		return !recipeOutput.isEmpty() && (output.isEmpty() || (ItemHandlerHelper.canItemStacksStack(recipeOutput, output) && (recipeOutput.getCount() + output.getCount() <= output.getMaxStackSize())));
	}

	public void smeltItem() {
		ItemStack input = inv.getStackInSlot(SLOT_INPUT);
		ItemStack recipeOutput = FurnaceRecipes.instance().getSmeltingList().get(recipeKey);
		ItemStack output = inv.getStackInSlot(SLOT_OUTPUT);

		if (output.isEmpty()) inv.setStackInSlot(SLOT_OUTPUT, recipeOutput.copy());
		else if (ItemHandlerHelper.canItemStacksStack(output, recipeOutput)) output.grow(recipeOutput.getCount());

		if (input.isItemEqual(WET_SPONGE) && inv.getStackInSlot(SLOT_FUEL).getItem() == Items.BUCKET) inv.setStackInSlot(SLOT_FUEL, new ItemStack(Items.WATER_BUCKET));

		input.shrink(1);
	}

	@Override
	public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
		return oldState.getBlock() != newState.getBlock();
	}

	public boolean hasUpgrade(Upgrade upg) {
		for (int slot : SLOT_UPGRADE)
			if (upg.matches(inv.getStackInSlot(slot))) return true;
		return false;
	}

	public int getItemBurnTime(ItemStack stack) {
		if (isElectric()) return 0;
		return TileEntityFurnace.getItemBurnTime(stack) * (hasUpgrade(Upgrades.EFFICIENCY) ? 2 : 1);
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && capability == CapabilityEnergy.ENERGY) return true;
		return super.hasCapability(capability, facing);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if (capability == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(this.energy);
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			IItemHandler h;
			if (facing == null) h = inv;
			else if (facing == EnumFacing.DOWN) h = BOTTOM;
			else if (facing == EnumFacing.UP) h = TOP;
			else h = SIDES;
			return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(h);
		}
		return super.getCapability(capability, facing);
	}

	protected IBlockState getDimState() {
		return world.getBlockState(pos).withProperty(BlockIronFurnace.BURNING, false);
	}

	protected IBlockState getLitState() {
		return world.getBlockState(pos).withProperty(BlockIronFurnace.BURNING, true);
	}

	protected boolean isElectric() {
		return hasUpgrade(Upgrades.ELECTRIC_FUEL);
	}

}
package cazador.furnaceoverhaul.blocks;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import cazador.furnaceoverhaul.FurnaceOverhaul;
import cazador.furnaceoverhaul.handler.GuiHandler;
import cazador.furnaceoverhaul.tile.TileEntityIronFurnace;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemStackHandler;

public class BlockIronFurnace extends Block {

	public static final PropertyDirection FACING = BlockHorizontal.FACING;
	public static final PropertyBool BURNING = PropertyBool.create("burning");

	protected final TextFormatting infoColor;
	protected final int cookTime;
	protected final Supplier<TileEntity> teFunc;

	/**
	 * Make a new Iron Furnace.
	 * @param name The registry name.
	 * @param infoColor The color of the tooltip.
	 * @param cookTime The default cook time of this furnace.
	 * @param teFunc A supplier for the TE of this furnace.
	 */
	public BlockIronFurnace(String name, TextFormatting infoColor, int cookTime, Supplier<TileEntity> teFunc) {
		super(Material.IRON);
		this.setTranslationKey(FurnaceOverhaul.MODID + "." + name);
		this.setRegistryName(FurnaceOverhaul.MODID, name);
		this.setCreativeTab(FurnaceOverhaul.FO_TAB);
		this.setHardness(2.0F);
		this.setResistance(9.0F);
		this.setHarvestLevel("pickaxe", 1);
		this.setLightOpacity(0);
		this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(BURNING, false));
		this.infoColor = infoColor;
		this.cookTime = cookTime;
		this.teFunc = teFunc;
	}

	@Override
	public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
		return state.getValue(BURNING) ? 14 : 0;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, World player, List<String> tooltip, ITooltipFlag advanced) {
		tooltip.add(infoColor + I18n.format("info.furnaceoverhaul.cooktime", cookTime));
	}

	@Override
	public boolean hasTileEntity(IBlockState state) {
		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, IBlockState state) {
		return teFunc.get();
	}

	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, FACING, BURNING);
	}

	@Override
	public IBlockState getStateFromMeta(int meta) {
		return getDefaultState().withProperty(FACING, EnumFacing.HORIZONTALS[(meta & 0b1100) >> 2]).withProperty(BURNING, (meta & 1) == 1);
	}

	@Override
	public int getMetaFromState(IBlockState state) {
		return (state.getValue(BURNING) ? 1 : 0) | (state.getValue(FACING).getHorizontalIndex() << 2);
	}

	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
		return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
	}

	@Override
	public IBlockState withRotation(IBlockState state, Rotation rot) {
		return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	public IBlockState withMirror(IBlockState state, Mirror mirror) {
		return state.withRotation(mirror.toRotation(state.getValue(FACING)));
	}

	@Override
	public boolean hasComparatorInputOverride(IBlockState state) {
		return true;
	}

	@Override
	public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityIronFurnace) {
			ItemStackHandler inv = ((TileEntityIronFurnace) te).getInventory();
			int i = 0;
			float f = 0.0F;
			for (int j = 0; j < 3; ++j) {
				ItemStack itemstack = inv.getStackInSlot(j);

				if (!itemstack.isEmpty()) {
					f += (float) itemstack.getCount() / (float) Math.min(64, itemstack.getMaxStackSize());
					++i;
				}
			}
			f = f / 3;
			return MathHelper.floor(f * 14.0F) + (i > 0 ? 1 : 0);
		}
		return 0;
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
		TileEntity te = world.getTileEntity(pos);
		if (!world.isRemote && te instanceof TileEntityIronFurnace && ((TileEntityIronFurnace) te).isFluid()) {
			ItemStack stack = player.getHeldItem(hand);
			FluidStack fs = FluidUtil.getFluidContained(stack);
			if (fs != null && TileEntityIronFurnace.getFluidBurnTime(fs) > 0) {
				FluidActionResult res = FluidUtil.tryEmptyContainer(stack, FluidUtil.getFluidHandler(world, pos, null), 1000, player, true);
				if (res.isSuccess()) {
					if (!player.capabilities.isCreativeMode) player.setHeldItem(hand, res.result);
					return true;
				}
			}
		}

		if (!player.isSneaking() && !world.isRemote) {
			player.openGui(FurnaceOverhaul.INSTANCE, GuiHandler.GUI_FURNACE, world, pos.getX(), pos.getY(), pos.getZ());
		}
		return true;
	}

	@Override
	public void breakBlock(World world, BlockPos pos, IBlockState state) {
		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityIronFurnace && world.getBlockState(pos).getBlock() != state.getBlock()) {
			ItemStackHandler inv = ((TileEntityIronFurnace) te).getInventory();
			for (int i = 0; i < inv.getSlots(); i++)
				Block.spawnAsEntity(world, pos, inv.getStackInSlot(i));
			world.updateComparatorOutputLevel(pos, this);
		}
		super.breakBlock(world, pos, state);
	}

	@Override
	public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
		if (state.getValue(BURNING)) {
			EnumFacing facing = state.getValue(FACING);
			double d0 = pos.getX() + 0.5D;
			double d1 = pos.getY() + rand.nextDouble() * 6.0D / 16.0D;
			double d2 = pos.getZ() + 0.5D;
			double d4 = rand.nextDouble() * 0.6D - 0.3D;
			if (rand.nextDouble() < 0.1D) world.playSound(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0F, 1.0F, false);
			Vec3d offset = new Vec3d(facing.getDirectionVec()).scale(0.52D);
			world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0 + offset.x, d1, d2 + d4 + offset.z, 0.0D, 0.0D, 0.0D);
			world.spawnParticle(EnumParticleTypes.FLAME, d0 + offset.x, d1, d2 + d4 + offset.z, 0.0D, 0.0D, 0.0D);
		}
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {
		return false;
	}

}
